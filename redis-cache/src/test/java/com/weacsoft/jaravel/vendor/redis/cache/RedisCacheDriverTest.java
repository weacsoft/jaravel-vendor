package com.weacsoft.jaravel.vendor.redis.cache;

import com.weacsoft.jaravel.vendor.redis.RedisManager;
import io.lettuce.core.KeyScanCursor;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * RedisCacheDriver 序列化、键前缀与扫描范围测试。
 * <p>
 * 使用 Mockito mock RedisManager，不依赖真实 Redis。
 * <p>
 * <b>键前缀是安全要求</b>：驱动给所有键加 {@code <prefix>}，SCAN 用 MATCH 限定范围。
 * 历史缺陷是 SCAN 不带 MATCH，导致 {@code Cache::flush()} 清空整个 Redis 库。
 */
class RedisCacheDriverTest {

    /** 测试用前缀（覆盖 redisManager.getPrefix() 的配置来源） */
    private static final String PREFIX = "test:";

    @SuppressWarnings("unchecked")
    private RedisCommands<String, String> mockCmd;
    private RedisManager mockManager;
    private RedisCacheDriver driver;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mockManager = mock(RedisManager.class);
        mockCmd = (RedisCommands<String, String>) mock(RedisCommands.class);
        when(mockManager.sync(any())).thenReturn(mockCmd);
        when(mockManager.getPrefix()).thenReturn(PREFIX);
        driver = new RedisCacheDriver(mockManager, "cache");
    }

    /** 逻辑键 → 期望物理键 */
    private static String physical(String key) {
        return PREFIX + key;
    }

    @Test
    void testPutWithTtlCallsSetex() {
        driver.put("user:1", "hello", 60);
        // 验证 setex 被调用，key 带命名空间前缀，ttl 与 JSON 值正确
        verify(mockCmd).setex(physical("user:1"), 60L, "\"hello\"");
    }

    @Test
    void testPutWithoutTtlCallsSet() {
        driver.put("config", "value", 0);
        verify(mockCmd).set(physical("config"), "\"value\"");
    }

    @Test
    void testPutWithNegativeTtlCallsSet() {
        driver.put("key", "val", -1);
        verify(mockCmd).set(physical("key"), "\"val\"");
    }

    @Test
    void testPutMapSerialization() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "Alice");
        data.put("age", 30);
        driver.put("user:1", data, 120);
        verify(mockCmd).setex(eq(physical("user:1")), eq(120L), contains("Alice"));
    }

    @Test
    void testGetDeserializesJson() {
        when(mockCmd.get(physical("key"))).thenReturn("\"hello world\"");
        Object result = driver.get("key");
        assertEquals("hello world", result, "应反序列化 JSON 字符串为 Java String");
    }

    @Test
    void testGetReturnsNullWhenKeyMissing() {
        when(mockCmd.get(physical("missing"))).thenReturn(null);
        assertNull(driver.get("missing"), "key 不存在时应返回 null");
    }

    @Test
    void testGetDeserializesMap() {
        when(mockCmd.get(physical("key"))).thenReturn("{\"name\":\"Bob\",\"age\":25}");
        Object result = driver.get("key");
        assertInstanceOf(Map.class, result, "应反序列化 JSON 对象为 Map");
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertEquals("Bob", map.get("name"));
        assertEquals(25, map.get("age"));
    }

    @Test
    void testExistsReturnsTrue() {
        when(mockCmd.exists(physical("key"))).thenReturn(1L);
        assertTrue(driver.exists("key"));
    }

    @Test
    void testExistsReturnsFalse() {
        when(mockCmd.exists(physical("key"))).thenReturn(0L);
        assertFalse(driver.exists("key"));
    }

    @Test
    void testRemoveReturnsTrue() {
        when(mockCmd.del(physical("key"))).thenReturn(1L);
        assertTrue(driver.remove("key"));
    }

    @Test
    void testRemoveReturnsFalse() {
        when(mockCmd.del(physical("key"))).thenReturn(0L);
        assertFalse(driver.remove("key"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void allKeysScansOnlyOwnNamespaceAndStripsPrefix() {
        KeyScanCursor<String> cursor = mock(KeyScanCursor.class);
        when(cursor.getKeys()).thenReturn(List.of(physical("a"), physical("b")));
        when(cursor.isFinished()).thenReturn(true);
        when(mockCmd.scan(any(ScanCursor.class), any(ScanArgs.class))).thenReturn(cursor);

        assertEquals(List.of("a", "b"), List.copyOf(driver.allKeys()),
                "allKeys 契约是「本驱动的逻辑键」，必须去掉命名空间前缀");
    }

    @Test
    @SuppressWarnings("unchecked")
    void removeAllDeletesOnlyPrefixedKeys() {
        KeyScanCursor<String> cursor = mock(KeyScanCursor.class);
        when(cursor.getKeys()).thenReturn(List.of(physical("x"), physical("y")));
        when(cursor.isFinished()).thenReturn(true);
        when(mockCmd.scan(any(ScanCursor.class), any(ScanArgs.class))).thenReturn(cursor);

        driver.removeAll();

        verify(mockCmd).del(physical("x"), physical("y"));
    }

    // ==================== AtomicCacheDriver：原子原语 ====================

    @Test
    void addIfAbsentUsesSetNx() {
        when(mockCmd.set(eq(physical("once")), any(String.class), any(io.lettuce.core.SetArgs.class)))
                .thenReturn("OK");

        assertTrue(driver.addIfAbsent("once", "v", 30), "SET NX 返回 OK 时应为写入成功");

        // 代理证据：必须走「带 SetArgs 的 set 重载」（put() 用的是两参 set），
        // 这条重载就是 SET NX [EX ttl] 的原子写法
        verify(mockCmd).set(eq(physical("once")), any(String.class), any(io.lettuce.core.SetArgs.class));
        verify(mockCmd, never()).set(eq(physical("once")), any(String.class));
    }

    @Test
    void addIfAbsentReturnsFalseWhenKeyExists() {
        when(mockCmd.set(eq(physical("dup")), any(String.class), any(io.lettuce.core.SetArgs.class)))
                .thenReturn(null);
        assertFalse(driver.addIfAbsent("dup", "v", 30));
    }

    @Test
    void pullValueUsesAtomicGetDelScript() {
        when(mockCmd.eval(any(String.class), any(io.lettuce.core.ScriptOutputType.class),
                any(String[].class))).thenReturn("\"token\"");
        assertEquals("token", driver.pullValue("one-shot"));

        var scriptCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mockCmd).eval(scriptCaptor.capture(), any(io.lettuce.core.ScriptOutputType.class),
                any(String[].class));
        assertTrue(scriptCaptor.getValue().contains("GET") && scriptCaptor.getValue().contains("DEL"),
                "取走必须是 GET+DEL 单脚本（原子），实际=" + scriptCaptor.getValue());
    }

    @Test
    void incrementAndGetUsesAtomicIncrby() {
        when(mockCmd.incrby(physical("counter"), 5L)).thenReturn(7L);
        assertEquals(7L, driver.incrementAndGet("counter", 5L, 0L));
        verify(mockCmd).incrby(physical("counter"), 5L);
    }

    @Test
    void incrementAndGetSeedsTtlWhenRequested() {
        when(mockCmd.incrby(physical("rate"), 1L)).thenReturn(1L);
        driver.incrementAndGet("rate", 1L, 60L);
        // 键不存在且要求 TTL 时，应先用 SET NX EX 建 0，避免 INCRBY 建出永久的键
        verify(mockCmd).set(eq(physical("rate")), eq("0"), any(io.lettuce.core.SetArgs.class));
        verify(mockCmd).incrby(physical("rate"), 1L);
    }
}