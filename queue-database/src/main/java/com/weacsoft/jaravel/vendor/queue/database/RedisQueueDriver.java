package com.weacsoft.jaravel.vendor.queue.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.weacsoft.jaravel.vendor.redis.RedisManager;
import io.lettuce.core.MapScanCursor;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis 队列驱动，对齐 Laravel {@code Illuminate\Queue\RedisQueue}。
 * <p>
 * 基于 Redis List / ZSET 实现队列存储，支持多实例消费、延迟执行、重试与失败归档。
 * 通过 {@link RedisManager} 获取指定命名连接的 {@link RedisCommands}，所有操作均为线程安全。
 *
 * <h3>数据结构</h3>
 * <ul>
 *   <li><b>就绪队列</b>：{@code jaravel:queue:{queueName}}（List），LPUSH 入队，RPOP 出队（FIFO）</li>
 *   <li><b>延迟队列</b>：{@code jaravel:queue:{queueName}:delayed}（ZSET，score=到期时间戳）</li>
 *   <li><b>预约队列</b>：{@code jaravel:queue:{queueName}:reserved}（ZSET，score=预约超时时间戳）</li>
 *   <li><b>失败队列</b>：{@code jaravel:queue:failed}（List），LPUSH 入队，最新失败在前</li>
 *   <li><b>任务 ID 序列</b>：{@code jaravel:queue:seq}（INCR 生成全局唯一 ID）</li>
 *   <li><b>失败 ID 序列</b>：{@code jaravel:queue:failed:seq}（INCR）</li>
 *   <li><b>队列索引</b>：{@code jaravel:queue:index}（Hash，jobId -> queueName，用于按 jobId 定位队列）</li>
 * </ul>
 * Job 以 JSON 字符串存储，包含 id / queue / payload / attempts / reservedAt / availableAt / createdAt。
 *
 * <h3>多实例消费</h3>
 * RPOP 是原子操作，确保同一任务只被一个实例获取。延迟 / 预约任务的迁移通过 {@code ZREM} 返回值
 * 抢占（返回 1 的实例执行迁移，返回 0 的跳过），避免重复入队。
 *
 * <h3>重试机制</h3>
 * pop 时将任务 ZADD 到预约队列（score=now+retryAfterSeconds*1000），超时未被 delete/release/fail
 * 的任务会在下次 pop 时被迁移回就绪队列重新消费，attempts 递增。
 *
 * <h3>依赖</h3>
 * 必须依赖 {@code redis-config} 模块提供 {@link RedisManager}。构造时 redisManager 为 null 将抛出异常。
 * 该驱动通过条件装配按需启用（{@link RedisQueueAutoConfiguration}），未引入 redis-config 时自动回退到 database 驱动。
 */
public class RedisQueueDriver implements QueueDriver {

    private static final Logger logger = LoggerFactory.getLogger(RedisQueueDriver.class);

    /** Redis 键前缀 */
    private static final String DEFAULT_PREFIX = "jaravel:queue";

    /** Redis 管理器，提供命名连接 */
    private final RedisManager redisManager;

    /** Redis 连接名，null / 空使用默认连接 */
    private final String connectionName;

    /** 重试超时秒数，超过此时间未被确认的任务会被重新预约 */
    private final long retryAfterSeconds;

    /** 失败任务保留天数 */
    private final int failedJobRetentionDays;

    /** Redis 键前缀 */
    private final String prefix;

    /** JSON 序列化器 */
    private final ObjectMapper objectMapper;

    /**
     * 构造 Redis 队列驱动。
     *
     * @param redisManager             Redis 管理器（不可为 null）
     * @param connectionName           Redis 连接名，null / 空使用默认连接
     * @param retryAfterSeconds        重试超时秒数
     * @param failedJobRetentionDays   失败任务保留天数
     */
    public RedisQueueDriver(RedisManager redisManager, String connectionName,
                            long retryAfterSeconds, int failedJobRetentionDays) {
        if (redisManager == null) {
            throw new IllegalArgumentException("RedisQueueDriver 需要 RedisManager，请引入 redis-config 模块");
        }
        this.redisManager = redisManager;
        this.connectionName = connectionName;
        this.retryAfterSeconds = retryAfterSeconds;
        this.failedJobRetentionDays = failedJobRetentionDays;
        this.prefix = DEFAULT_PREFIX;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        logger.info("[queue-redis] 初始化: connection={}, retryAfter={}s, retention={}d",
                connectionName == null || connectionName.isEmpty() ? "default" : connectionName,
                retryAfterSeconds, failedJobRetentionDays);
    }

    /** 获取 Redis 同步命令接口 */
    private RedisCommands<String, String> cmd() {
        return redisManager.sync(connectionName);
    }

    private String readyKey(String queue) {
        return prefix + ":" + queue;
    }

    private String delayedKey(String queue) {
        return prefix + ":" + queue + ":delayed";
    }

    private String reservedKey(String queue) {
        return prefix + ":" + queue + ":reserved";
    }

    private String failedKey() {
        return prefix + ":failed";
    }

    private String seqKey() {
        return prefix + ":seq";
    }

    private String failedSeqKey() {
        return prefix + ":failed:seq";
    }

    private String indexKey() {
        return prefix + ":index";
    }

    // ==================== 队列操作 ====================

    @Override
    public long push(String queueName, String payload) {
        return push(queueName, payload, 0);
    }

    @Override
    public long push(String queueName, String payload, long delayMs) {
        long now = System.currentTimeMillis();
        long id = cmd().incr(seqKey());
        long availableAt = delayMs > 0 ? now + delayMs : now;
        String json = serializeJob(id, queueName, payload, 0, 0L, availableAt, now);
        RedisCommands<String, String> cmd = cmd();
        if (delayMs > 0) {
            cmd.zadd(delayedKey(queueName), availableAt, json);
        } else {
            cmd.lpush(readyKey(queueName), json);
        }
        cmd.hset(indexKey(), Long.toString(id), queueName);
        logger.debug("[queue-redis] 推送任务: queue={}, jobId={}, delayMs={}", queueName, id, delayMs);
        return id;
    }

    @Override
    public QueuedJob pop(String queueName) {
        long now = System.currentTimeMillis();
        RedisCommands<String, String> cmd = cmd();
        String ready = readyKey(queueName);
        String delayed = delayedKey(queueName);
        String reserved = reservedKey(queueName);

        // 1. 迁移到期延迟任务到就绪队列
        List<String> delayedMembers = cmd.zrangebyscore(delayed, 0.0, (double) now);
        for (String member : delayedMembers) {
            if (cmd.zrem(delayed, member) > 0) {
                cmd.lpush(ready, member);
            }
        }

        // 2. 迁移超时预约任务到就绪队列（worker 崩溃 / 超时未确认）
        List<String> reservedMembers = cmd.zrangebyscore(reserved, 0.0, (double) now);
        for (String member : reservedMembers) {
            if (cmd.zrem(reserved, member) > 0) {
                cmd.lpush(ready, member);
            }
        }

        // 3. 弹出就绪任务
        String json = cmd.rpop(ready);
        if (json == null) {
            return null;
        }

        Map<String, Object> job = deserialize(json);
        if (job == null) {
            logger.warn("[queue-redis] 弹出任务反序列化失败，丢弃: {}", json);
            return null;
        }
        long id = asLong(job.get("id"));
        String payload = asString(job.get("payload"));
        int attempts = (int) asLong(job.get("attempts")) + 1;
        long availableAt = asLong(job.get("availableAt"));
        long createdAt = asLong(job.get("createdAt"));
        long reservedAt = now + retryAfterSeconds * 1000;

        // 更新 attempts / reservedAt 后重新写入预约队列
        String updated = serializeJob(id, queueName, payload, attempts, reservedAt, availableAt, createdAt);
        cmd.zadd(reserved, reservedAt, updated);

        return new QueuedJob(id, queueName, payload, attempts, reservedAt, availableAt, createdAt);
    }

    @Override
    public void delete(long jobId) {
        String queue = cmd().hget(indexKey(), Long.toString(jobId));
        if (queue == null) {
            logger.debug("[queue-redis] 删除任务：索引中不存在 jobId={}", jobId);
            return;
        }
        String member = findReservedMember(queue, jobId);
        if (member != null) {
            cmd().zrem(reservedKey(queue), member);
        }
        cmd().hdel(indexKey(), Long.toString(jobId));
        logger.debug("[queue-redis] 删除任务: jobId={}, queue={}", jobId, queue);
    }

    @Override
    public void release(long jobId) {
        release(jobId, 0);
    }

    @Override
    public void release(long jobId, long delayMs) {
        String queue = cmd().hget(indexKey(), Long.toString(jobId));
        if (queue == null) {
            logger.warn("[queue-redis] 释放任务：索引中不存在 jobId={}", jobId);
            return;
        }
        String member = findReservedMember(queue, jobId);
        if (member == null) {
            logger.warn("[queue-redis] 释放任务：预约队列中不存在 jobId={}", jobId);
            return;
        }
        Map<String, Object> job = deserialize(member);
        if (job == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long availableAt = delayMs > 0 ? now + delayMs : now;
        String updated = serializeJob(
                asLong(job.get("id")), queue, asString(job.get("payload")),
                (int) asLong(job.get("attempts")), 0L, availableAt, asLong(job.get("createdAt")));
        RedisCommands<String, String> cmd = cmd();
        cmd.zrem(reservedKey(queue), member);
        if (delayMs > 0) {
            cmd.zadd(delayedKey(queue), availableAt, updated);
        } else {
            cmd.lpush(readyKey(queue), updated);
        }
        logger.debug("[queue-redis] 释放任务: jobId={}, delayMs={}", jobId, delayMs);
    }

    @Override
    public int size(String queueName) {
        Long n = cmd().llen(readyKey(queueName));
        return n != null ? n.intValue() : 0;
    }

    @Override
    public void clear(String queueName) {
        RedisCommands<String, String> cmd = cmd();
        cmd.del(readyKey(queueName), delayedKey(queueName), reservedKey(queueName));
        // 清理该队列在索引中的残留
        ScanCursor cursor = ScanCursor.INITIAL;
        do {
            MapScanCursor<String, String> scan = cmd.hscan(indexKey(), cursor);
            List<String> toRemove = new ArrayList<>();
            for (Map.Entry<String, String> entry : scan.getMap().entrySet()) {
                if (queueName.equals(entry.getValue())) {
                    toRemove.add(entry.getKey());
                }
            }
            if (!toRemove.isEmpty()) {
                cmd.hdel(indexKey(), toRemove.toArray(new String[0]));
            }
            cursor = scan;
        } while (!cursor.isFinished());
        logger.info("[queue-redis] 清空队列: {}", queueName);
    }

    // ==================== 失败队列 ====================

    @Override
    public void fail(long jobId, String queue, String payload, int attempts, String exception) {
        long now = System.currentTimeMillis();
        long failedId = cmd().incr(failedSeqKey());
        String json = serializeFailedJob(failedId, jobId, queue, payload, attempts, exception, now);
        RedisCommands<String, String> cmd = cmd();
        cmd.lpush(failedKey(), json);
        // 从预约队列移除原任务
        String member = findReservedMember(queue, jobId);
        if (member != null) {
            cmd.zrem(reservedKey(queue), member);
        }
        cmd.hdel(indexKey(), Long.toString(jobId));
        logger.warn("[queue-redis] 任务归档到失败队列: jobId={}, queue={}, attempts={}", jobId, queue, attempts);
    }

    @Override
    public List<QueuedJob> getFailedJobs() {
        List<String> list = cmd().lrange(failedKey(), 0, -1);
        List<QueuedJob> result = new ArrayList<>(list.size());
        for (String json : list) {
            Map<String, Object> f = deserialize(json);
            if (f == null) {
                continue;
            }
            result.add(new QueuedJob(
                    asLong(f.get("id")),
                    asString(f.get("queue")),
                    asString(f.get("payload")),
                    (int) asLong(f.get("attempts")),
                    0L,
                    asLong(f.get("failedAt")),
                    asLong(f.get("failedAt")),
                    asString(f.get("exception"))));
        }
        return result;
    }

    @Override
    public void retryFailedJob(long failedJobId) {
        List<String> list = cmd().lrange(failedKey(), 0, -1);
        for (String json : list) {
            Map<String, Object> f = deserialize(json);
            if (f == null) {
                continue;
            }
            if (asLong(f.get("id")) == failedJobId) {
                String queue = asString(f.get("queue"));
                String payload = asString(f.get("payload"));
                push(queue, payload);
                cmd().lrem(failedKey(), 1, json);
                logger.info("[queue-redis] 重试失败任务: failedJobId={}, queue={}", failedJobId, queue);
                return;
            }
        }
        logger.warn("[queue-redis] 重试失败任务不存在: failedJobId={}", failedJobId);
    }

    @Override
    public void deleteFailedJob(long failedJobId) {
        List<String> list = cmd().lrange(failedKey(), 0, -1);
        for (String json : list) {
            Map<String, Object> f = deserialize(json);
            if (f == null) {
                continue;
            }
            if (asLong(f.get("id")) == failedJobId) {
                cmd().lrem(failedKey(), 1, json);
                logger.info("[queue-redis] 删除失败任务: failedJobId={}", failedJobId);
                return;
            }
        }
        logger.warn("[queue-redis] 删除失败任务不存在: failedJobId={}", failedJobId);
    }

    @Override
    public void clearFailedJobs() {
        cmd().del(failedKey());
        logger.info("[queue-redis] 清空所有失败任务");
    }

    /**
     * 清理超过保留天数的失败任务，对齐 Laravel {@code queue:prune-failed-jobs}。
     */
    public void purgeOldFailedJobs() {
        long threshold = System.currentTimeMillis() - (long) failedJobRetentionDays * 24 * 60 * 60 * 1000;
        List<String> list = cmd().lrange(failedKey(), 0, -1);
        int count = 0;
        for (String json : list) {
            Map<String, Object> f = deserialize(json);
            if (f == null) {
                continue;
            }
            if (asLong(f.get("failedAt")) < threshold) {
                cmd().lrem(failedKey(), 1, json);
                count++;
            }
        }
        if (count > 0) {
            logger.info("[queue-redis] 清理过期失败任务: count={}, retentionDays={}", count, failedJobRetentionDays);
        }
    }

    // ==================== 内部工具 ====================

    /** 在指定队列的预约 ZSET 中查找 jobId 对应的成员（JSON），未找到返回 null */
    private String findReservedMember(String queue, long jobId) {
        List<String> members = cmd().zrange(reservedKey(queue), 0, -1);
        for (String member : members) {
            Map<String, Object> job = deserialize(member);
            if (job != null && asLong(job.get("id")) == jobId) {
                return member;
            }
        }
        return null;
    }

    private String serializeJob(long id, String queue, String payload, int attempts,
                                long reservedAt, long availableAt, long createdAt) {
        Map<String, Object> map = new LinkedHashMap<>(7);
        map.put("id", id);
        map.put("queue", queue);
        map.put("payload", payload);
        map.put("attempts", attempts);
        map.put("reservedAt", reservedAt);
        map.put("availableAt", availableAt);
        map.put("createdAt", createdAt);
        return writeJson(map);
    }

    private String serializeFailedJob(long failedId, long jobId, String queue, String payload,
                                      int attempts, String exception, long failedAt) {
        Map<String, Object> map = new LinkedHashMap<>(7);
        map.put("id", failedId);
        map.put("jobId", jobId);
        map.put("queue", queue);
        map.put("payload", payload);
        map.put("attempts", attempts);
        map.put("exception", exception);
        map.put("failedAt", failedAt);
        return writeJson(map);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deserialize(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            logger.error("[queue-redis] JSON 反序列化失败: {}", e.getMessage());
            return null;
        }
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("[queue-redis] JSON 序列化失败: " + e.getMessage(), e);
        }
    }

    private long asLong(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
