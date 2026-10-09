package com.weacsoft.jaravel.vendor.queue.database;

import com.weacsoft.jaravel.vendor.core.queue.QueuedJob;
import com.weacsoft.jaravel.vendor.redis.RedisManager;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RedisQueueDriver} 的键空间与原子性回归测试（此驱动此前<b>零测试覆盖</b>）。
 * <p>
 * 锁定四件事：
 * <ol>
 *   <li><b>索引带成员</b>：{@code jobId -> queue<US>member}，使 delete/release 变成 O(1)、
 *       不再 {@code ZRANGE 0 -1} 全量扫描并逐条反序列化；</li>
 *   <li><b>push 先写索引再入队</b>：避免「已入队但没有索引」导致 delete/release 定位不到队列；</li>
 *   <li><b>迁移/领取/释放走 Lua</b>：{@code ZREM+LPUSH}、{@code RPOP+ZADD}、{@code ZREM+入队}
 *       各自在同一脚本内完成 —— 原实现是两步命令，进程死在中间会丢任务或重复投递；</li>
 *   <li><b>索引缺失不再静默</b>：改为告警，并说明任务会由超时迁移按「至少一次」重新投递。</li>
 * </ol>
 * 说明：Lua 的<b>原子语义本身</b>需要真实 Redis 才能验证（当前测试只覆盖调用的脚本、键与参数），
 * 已作为未验证项记录在 CHANGELOG。
 */
class RedisQueueDriverTest {

    private static final long JOB_ID = 7L;
    private static final String QUEUE = "default";

    /** 预约集合里的成员 JSON（字段与 serializeJob 对齐） */
    private static final String MEMBER =
            "{\"id\":7,\"queue\":\"default\",\"payload\":\"hello\",\"attempts\":0,"
                    + "\"reservedAt\":0,\"availableAt\":0,\"createdAt\":0}";

    /** 新格式索引值：队列名 + US + 成员 */
    private static final String INDEX_VALUE = QUEUE + '\u0001' + MEMBER;

    @SuppressWarnings("unchecked")
    private RedisCommands<String, String> cmd;
    private RedisQueueDriver driver;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        RedisManager manager = mock(RedisManager.class);
        cmd = (RedisCommands<String, String>) mock(RedisCommands.class);
        when(manager.sync(any())).thenReturn(cmd);
        driver = new RedisQueueDriver(manager, null, 60, 7);
    }

    @Test
    void pushWritesIndexWithMemberBeforeEnqueue() {
        driver.push(QUEUE, "hello");

        // 索引值必须带成员（分隔符 + JSON），否则 delete/release 只能退化为扫描
        org.mockito.ArgumentCaptor<String> valueCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(cmd).hset(anyString(), anyString(), valueCaptor.capture());
        String indexValue = valueCaptor.getValue();
        int separator = indexValue.indexOf('\u0001');
        assertEquals(QUEUE, indexValue.substring(0, separator), "索引值首段应是队列名");
        org.junit.jupiter.api.Assertions.assertTrue(
                indexValue.substring(separator + 1).contains("\"payload\":\"hello\""),
                "索引值第二段应是任务成员 JSON");

        // 顺序：先 hset 索引，再入队
        InOrder order = inOrder(cmd);
        order.verify(cmd).hset(anyString(), anyString(), anyString());
        order.verify(cmd).lpush(anyString(), anyString());
    }

    @Test
    void deleteUsesIndexMemberWithoutScanning() {
        when(cmd.hget(anyString(), eq(Long.toString(JOB_ID)))).thenReturn(INDEX_VALUE);

        driver.delete(JOB_ID);

        verify(cmd).zrem(contains(":reserved"), eq(MEMBER));
        verify(cmd).hdel(anyString(), eq(Long.toString(JOB_ID)));
        // 关键：新格式索引下不得再扫描预约 ZSET
        verify(cmd, never()).zrange(anyString(), anyLong(), anyLong());
    }

    @Test
    void deleteFallsBackToScanForLegacyIndexValue() {
        // 旧格式：索引里只有队列名
        when(cmd.hget(anyString(), eq(Long.toString(JOB_ID)))).thenReturn(QUEUE);
        when(cmd.zrange(contains(":reserved"), anyLong(), anyLong())).thenReturn(List.of(MEMBER));

        driver.delete(JOB_ID);

        verify(cmd).zrange(contains(":reserved"), anyLong(), anyLong());
        verify(cmd).zrem(contains(":reserved"), eq(MEMBER));
    }

    @Test
    void deleteWithMissingIndexDoesNotTouchReservedSet() {
        when(cmd.hget(anyString(), eq(Long.toString(JOB_ID)))).thenReturn(null);

        driver.delete(JOB_ID);

        verify(cmd, never()).zrem(anyString(), anyString());
        verify(cmd, never()).hdel(anyString(), anyString());
    }

    @Test
    void popMigratesAndClaimsViaLuaScripts() {
        // 领取脚本（含 RPOP）返回成员；迁移脚本（含 ZRANGEBYSCORE）返回条数
        doReturn(MEMBER).when(cmd).eval(contains("RPOP"), any(ScriptOutputType.class),
                any(String[].class), anyString());
        doReturn(1L).when(cmd).eval(contains("ZRANGEBYSCORE"), any(ScriptOutputType.class),
                any(String[].class), anyString());

        QueuedJob job = driver.pop(QUEUE);

        assertNotNull(job, "应取到任务");
        assertEquals(JOB_ID, job.getId());
        assertEquals(1, job.getAttempts(), "attempts 应在领取时递增");
        // 迁移与领取都必须走 eval（原子），不能再直接用两步命令
        verify(cmd, org.mockito.Mockito.atLeastOnce()).eval(contains("ZRANGEBYSCORE"),
                any(ScriptOutputType.class), any(String[].class), anyString());
        verify(cmd, org.mockito.Mockito.atLeastOnce()).eval(contains("RPOP"),
                any(ScriptOutputType.class), any(String[].class), anyString());
        verify(cmd, never()).rpop(anyString());
        verify(cmd, never()).zrangebyscore(anyString(), anyDouble(), anyDouble());
    }

    @Test
    void popReturnsNullWhenClaimScriptFindsNothing() {
        doReturn(null).when(cmd).eval(contains("RPOP"), any(ScriptOutputType.class),
                any(String[].class), anyString());
        doReturn(0L).when(cmd).eval(contains("ZRANGEBYSCORE"), any(ScriptOutputType.class),
                any(String[].class), anyString());

        assertEquals(null, driver.pop(QUEUE));
    }

    @Test
    void releaseUsesAtomicScriptInsteadOfTwoStepCommands() {
        when(cmd.hget(anyString(), eq(Long.toString(JOB_ID)))).thenReturn(INDEX_VALUE);

        driver.release(JOB_ID, 0);

        // 释放必须是一个脚本（ZREM + 入队 + 刷新索引），不能再分别 zrem/lpush
        verify(cmd).eval(contains("ZREM"), any(ScriptOutputType.class), any(String[].class),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(cmd, never()).lpush(anyString(), anyString());
    }

    @Test
    void releaseWithoutIndexWarnsAndDoesNotThrow() {
        when(cmd.hget(anyString(), eq(Long.toString(JOB_ID)))).thenReturn(null);

        driver.release(JOB_ID, 0);

        verify(cmd, never()).lpush(anyString(), anyString());
    }

    private static double anyDouble() {
        return org.mockito.ArgumentMatchers.anyDouble();
    }
}