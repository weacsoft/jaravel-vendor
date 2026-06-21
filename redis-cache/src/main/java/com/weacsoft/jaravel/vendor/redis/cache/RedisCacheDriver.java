package com.weacsoft.jaravel.vendor.redis.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.weacsoft.jaravel.vendor.cache.CacheDriver;
import com.weacsoft.jaravel.vendor.redis.RedisManager;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

/**
 * Redis 缓存驱动，对齐 Laravel {@code RedisStore}（{@code Illuminate\Cache\RedisStore}）。
 * <p>
 * 实现 {@link CacheDriver} 接口，底层通过 {@link RedisManager} 获取指定命名连接的 Redis 命令接口，
 * 所有缓存键值均以 JSON 序列化存储，TTL 通过 Redis EXPIRE/SETEX 实现。
 * <p>
 * <b>多机同步</b>：由于所有实例共享同一 Redis 实例（或集群），写入的缓存对所有实例立即可见，
 * 天然实现多机缓存同步，无需额外的广播或失效机制。
 *
 * <h3>序列化策略</h3>
 * <ul>
 *   <li>值通过 Jackson {@link ObjectMapper} 序列化为 JSON 字符串存储</li>
 *   <li>读取时返回反序列化后的 Java 对象（Map / List / String / Number 等）</li>
 *   <li>TTL {@code <= 0} 表示永不过期，使用 SET 而非 SETEX</li>
 * </ul>
 *
 * <h3>键扫描</h3>
 * {@link #allKeys()} 使用 SCAN 命令遍历键空间（非 KEYS，避免阻塞），
 * 匹配模式为 {@code prefix + *}。
 */
public class RedisCacheDriver implements CacheDriver {

    private static final Logger logger = LoggerFactory.getLogger(RedisCacheDriver.class);

    /** Redis 管理器，提供命名连接 */
    private final RedisManager redisManager;

    /** Redis 连接名（如 cache / model-cache），对应 jaravel.redis.connections 中的配置 */
    private final String connectionName;

    /** JSON 序列化器 */
    private final ObjectMapper objectMapper;

    /**
     * 构造 Redis 缓存驱动。
     *
     * @param redisManager    Redis 管理器
     * @param connectionName  Redis 连接名，null 使用默认连接
     */
    public RedisCacheDriver(RedisManager redisManager, String connectionName) {
        this.redisManager = redisManager;
        this.connectionName = connectionName;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    /**
     * 使用默认连接构造 Redis 缓存驱动。
     *
     * @param redisManager Redis 管理器
     */
    public RedisCacheDriver(RedisManager redisManager) {
        this(redisManager, null);
    }

    /** 获取 Redis 同步命令接口 */
    private RedisCommands<String, String> commands() {
        return redisManager.sync(connectionName);
    }

    @Override
    public boolean put(String key, Object value, long ttlSeconds) {
        try {
            String json = objectMapper.writeValueAsString(value);
            RedisCommands<String, String> cmd = commands();
            if (ttlSeconds > 0) {
                cmd.setex(key, ttlSeconds, json);
            } else {
                cmd.set(key, json);
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
            String json = commands().get(key);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            logger.error("[redis-cache] 读取缓存失败 key={}: {}", key, e.getMessage());
            return null;
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            return commands().exists(key) > 0;
        } catch (Exception e) {
            logger.error("[redis-cache] 检查缓存存在失败 key={}: {}", key, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean remove(String key) {
        try {
            return commands().del(key) > 0;
        } catch (Exception e) {
            logger.error("[redis-cache] 移除缓存失败 key={}: {}", key, e.getMessage());
            return false;
        }
    }

    @Override
    public void removeAll() {
        try {
            RedisCommands<String, String> cmd = commands();
            // 使用 SCAN 遍历并删除所有键，避免 FLUSHDB 影响其他用途
            Collection<String> keys = allKeys();
            if (!keys.isEmpty()) {
                cmd.del(keys.toArray(new String[0]));
            }
        } catch (Exception e) {
            logger.error("[redis-cache] 清空缓存失败: {}", e.getMessage());
        }
    }

    @Override
    public Collection<String> allKeys() {
        Collection<String> all = new ArrayList<>();
        try {
            RedisCommands<String, String> cmd = commands();
            // SCAN 遍历，避免 KEYS 阻塞
            io.lettuce.core.ScanCursor cursor = io.lettuce.core.ScanCursor.INITIAL;
            do {
                io.lettuce.core.ScanArgs args = io.lettuce.core.ScanArgs.Builder.limit(100);
                io.lettuce.core.KeyScanCursor<String> scanResult = cmd.scan(cursor, args);
                all.addAll(scanResult.getKeys());
                cursor = scanResult;
            } while (!cursor.isFinished());
        } catch (Exception e) {
            logger.error("[redis-cache] 扫描缓存键失败: {}", e.getMessage());
        }
        return all;
    }
}
