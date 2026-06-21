package com.weacsoft.jaravel.vendor.redis.lock;

import com.weacsoft.jaravel.vendor.redis.RedisManager;
import com.weacsoft.jaravel.vendor.schedule.RedisLockProvider;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Redis 的分布式锁实现，对齐 Laravel {@code Illuminate\Cache\RedisLock}。
 * <p>
 * 使用 Redis {@code SET key value NX EX seconds} 实现原子性加锁，
 * 通过 {@code DEL key} 释放锁。适用于多机环境下的定时任务防重复执行。
 *
 * <h3>实现细节</h3>
 * <ul>
 *   <li>加锁：{@code SET <key> <token> NX EX <ttl>}，仅当 key 不存在时设置，并带 TTL</li>
 *   <li>解锁：直接 {@code DEL <key>}（简化实现，不验证 token）</li>
 *   <li>TTL 防死锁：锁自动过期，避免持有者崩溃导致锁永久占用</li>
 * </ul>
 */
public class RedisLockProviderImpl implements RedisLockProvider {

    private static final Logger logger = LoggerFactory.getLogger(RedisLockProviderImpl.class);

    /** Redis 管理器 */
    private final RedisManager redisManager;

    /** Redis 连接名，null 使用默认连接 */
    private final String connectionName;

    /** 锁值前缀，用于标识锁持有者 */
    private final String lockValuePrefix;

    public RedisLockProviderImpl(RedisManager redisManager, String connectionName) {
        this.redisManager = redisManager;
        this.connectionName = connectionName;
        this.lockValuePrefix = "lock:" + Thread.currentThread().getId() + ":";
    }

    /** 获取 Redis 同步命令接口 */
    private RedisCommands<String, String> commands() {
        return redisManager.sync(connectionName);
    }

    @Override
    public boolean tryLock(String key, long ttlSeconds) {
        try {
            String value = lockValuePrefix + System.nanoTime();
            // SET key value NX EX seconds
            String result = commands().set(key, value, io.lettuce.core.SetArgs.Builder.nx().ex(ttlSeconds));
            boolean locked = "OK".equals(result);
            if (locked) {
                logger.debug("[redis-lock] 加锁成功: key={}, ttl={}s", key, ttlSeconds);
            }
            return locked;
        } catch (Exception e) {
            logger.error("[redis-lock] 加锁失败: key={} - {}", key, e.getMessage());
            return false;
        }
    }

    @Override
    public void unlock(String key) {
        try {
            commands().del(key);
            logger.debug("[redis-lock] 释放锁: key={}", key);
        } catch (Exception e) {
            logger.error("[redis-lock] 释放锁失败: key={} - {}", key, e.getMessage());
        }
    }
}
