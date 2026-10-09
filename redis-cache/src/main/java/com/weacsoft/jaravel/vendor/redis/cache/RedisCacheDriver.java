package com.weacsoft.jaravel.vendor.redis.cache;

import com.weacsoft.jaravel.vendor.cache.CacheDriver;
import com.weacsoft.jaravel.vendor.json.Json;
import com.weacsoft.jaravel.vendor.redis.RedisManager;
import io.lettuce.core.KeyScanCursor;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Redis 缓存驱动，对齐 Laravel {@code RedisStore}（{@code Illuminate\Cache\RedisStore}）。
 * <p>
 * 实现 {@link CacheDriver} 接口，底层通过 {@link RedisManager} 获取指定命名连接的 Redis 命令接口，
 * 所有缓存键值均以 JSON 序列化存储，TTL 通过 Redis EXPIRE/SETEX 实现。
 * <p>
 * <b>多机同步</b>：由于所有实例共享同一 Redis 实例（或集群），写入的缓存对所有实例立即可见，
 * 天然实现多机缓存同步，无需额外的广播或失效机制。
 *
 * <h3>键命名空间（重要）</h3>
 * 本驱动写入 Redis 的键统一加前缀 {@code <keyPrefix><逻辑键>}。前缀取自
 * {@link RedisManager#getPrefix()}（配置键为 {@code jaravel.redis.options.prefix}，
 * 注意不是 {@code jaravel.redis.prefix}），未配置时回退为 {@link #DEFAULT_KEY_PREFIX}。
 * <p>
 * <b>为什么必须有前缀</b>：{@link #allKeys()} 用 SCAN + MATCH 前缀遍历，{@link #removeAll()}
 * 只删除这些键。历史缺陷是 SCAN 时<b>没带 MATCH</b>，于是 {@code Cache::flush()} 会把整个
 * Redis 库的键全部删掉 —— 连同会话、队列、限流计数、同实例其它应用的数据，且不可恢复。
 *
 * <h3>序列化策略</h3>
 * <ul>
 *   <li>值通过 {@link Json} 序列化为 JSON 字符串存储</li>
 *   <li>读取时返回反序列化后的 Java 对象（Map / List / String / Number 等）</li>
 *   <li>TTL {@code <= 0} 表示永不过期，使用 SET 而非 SETEX</li>
 * </ul>
 */
public class RedisCacheDriver implements CacheDriver, com.weacsoft.jaravel.vendor.cache.TtlAwareCacheDriver {

    private static final Logger logger = LoggerFactory.getLogger(RedisCacheDriver.class);

    /** 未配置 {@code jaravel.redis.options.prefix} 时使用的默认键前缀（保证 flushed 范围可控） */
    public static final String DEFAULT_KEY_PREFIX = "jaravel:cache:";

    /** SCAN 每批数量 */
    private static final int SCAN_BATCH = 100;

    /** Redis 管理器，提供命名连接 */
    private final RedisManager redisManager;

    /** Redis 连接名（如 cache / model-cache），对应 jaravel.redis.connections 中的配置 */
    private final String connectionName;

    /** 实际使用的键前缀（非空，见类注释） */
    private final String keyPrefix;

    /**
     * 构造 Redis 缓存驱动。
     *
     * @param redisManager    Redis 管理器
     * @param connectionName  Redis 连接名，null 使用默认连接
     */
    public RedisCacheDriver(RedisManager redisManager, String connectionName) {
        this.redisManager = redisManager;
        this.connectionName = connectionName;
        String configured = redisManager != null ? redisManager.getPrefix() : null;
        this.keyPrefix = (configured == null || configured.isBlank()) ? DEFAULT_KEY_PREFIX : configured;
    }

    /**
     * 使用默认连接构造 Redis 缓存驱动。
     *
     * @param redisManager Redis 管理器
     */
    public RedisCacheDriver(RedisManager redisManager) {
        this(redisManager, null);
    }

    /**
     * @return 实际使用的键前缀
     */
    public String getKeyPrefix() {
        return keyPrefix;
    }

    /** 获取 Redis 同步命令接口 */
    private RedisCommands<String, String> commands() {
        return redisManager.sync(connectionName);
    }

    /** 逻辑键 → Redis 实际键 */
    private String physicalKey(String key) {
        return keyPrefix + key;
    }

    /** Redis 实际键 → 逻辑键（用于 allKeys 返回契约值） */
    private String logicalKey(String physicalKey) {
        return physicalKey.startsWith(keyPrefix) ? physicalKey.substring(keyPrefix.length()) : physicalKey;
    }

    @Override
    public boolean put(String key, Object value, long ttlSeconds) {
        try {
            String json = Json.stringify(value);
            RedisCommands<String, String> cmd = commands();
            if (ttlSeconds > 0) {
                cmd.setex(physicalKey(key), ttlSeconds, json);
            } else {
                cmd.set(physicalKey(key), json);
            }
            return true;
        } catch (Exception e) {
            logger.error("[redis-cache] 写入缓存失败 key={}: {}", key, e.getMessage());
            return false;
        }
    }

    @Override
    public Object get(String key) {
        try {
            String json = commands().get(physicalKey(key));
            if (json == null) {
                return null;
            }
            return Json.parse(json, Object.class);
        } catch (Exception e) {
            logger.error("[redis-cache] 读取缓存失败 key={}: {}", key, e.getMessage());
            return null;
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            return commands().exists(physicalKey(key)) > 0;
        } catch (Exception e) {
            logger.error("[redis-cache] 检查缓存存在失败 key={}: {}", key, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean remove(String key) {
        try {
            return commands().del(physicalKey(key)) > 0;
        } catch (Exception e) {
            logger.error("[redis-cache] 移除缓存失败 key={}: {}", key, e.getMessage());
            return false;
        }
    }

    @Override
    public void removeAll() {
        try {
            RedisCommands<String, String> cmd = commands();
            // 只删除本驱动命名空间下的键：SCAN + MATCH <prefix>*（绝不 FLUSHDB / 全库 DEL）
            List<String> physicalKeys = scanPhysicalKeys();
            if (!physicalKeys.isEmpty()) {
                cmd.del(physicalKeys.toArray(new String[0]));
                logger.info("[redis-cache] 已清空本命名空间缓存: prefix={}, keys={}", keyPrefix, physicalKeys.size());
            }
        } catch (Exception e) {
            logger.error("[redis-cache] 清空缓存失败: {}", e.getMessage());
        }
    }

    @Override
    public Collection<String> allKeys() {
        List<String> logical = new ArrayList<>();
        try {
            for (String physical : scanPhysicalKeys()) {
                logical.add(logicalKey(physical));
            }
        } catch (Exception e) {
            logger.error("[redis-cache] 扫描缓存键失败: {}", e.getMessage());
        }
        return logical;
    }

    /**
     * 报告剩余 TTL（供 {@code CacheStore.increment/decrement} 保留原 TTL）。
     * <p>
     * Redis 的 {@code TTL} 语义：{@code -2} 键不存在、{@code -1} 存在但无过期时间、
     * 其他为剩余秒数。这里按能力接口的三态转换（empty / 0 / 正数）。
     */
    @Override
    public java.util.OptionalLong remainingTtlSeconds(String key) {
        try {
            Long ttl = commands().ttl(physicalKey(key));
            if (ttl == null) {
                return java.util.OptionalLong.empty();
            }
            if (ttl >= 0) {
                return java.util.OptionalLong.of(ttl);
            }
            // ttl == -1：键存在且永不过期；ttl == -2：键不存在
            return ttl == -1L ? java.util.OptionalLong.of(0L) : java.util.OptionalLong.empty();
        } catch (Exception e) {
            logger.error("[redis-cache] 读取 TTL 失败 key={}: {}", key, e.getMessage());
            return java.util.OptionalLong.empty();
        }
    }

    /**
     * SCAN 遍历本驱动命名空间下的所有物理键（带 MATCH，避免扫到别人的键）。
     *
     * @return 物理键列表
     */
    private List<String> scanPhysicalKeys() {
        List<String> keys = new ArrayList<>();
        RedisCommands<String, String> cmd = commands();
        ScanCursor cursor = ScanCursor.INITIAL;
        ScanArgs args = ScanArgs.Builder.limit(SCAN_BATCH).match(keyPrefix + "*");
        do {
            KeyScanCursor<String> scanResult = cmd.scan(cursor, args);
            keys.addAll(scanResult.getKeys());
            cursor = scanResult;
        } while (!cursor.isFinished());
        return keys;
    }
}