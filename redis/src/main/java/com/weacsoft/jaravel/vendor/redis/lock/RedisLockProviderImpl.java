package com.weacsoft.jaravel.vendor.redis.lock;

import com.weacsoft.jaravel.vendor.redis.RedisManager;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Redis 的分布式锁实现，对齐 Laravel {@code Illuminate\Cache\RedisLock}。
 * <p>
 * 加锁：{@code SET key token NX EX ttl}（原子）；解锁：<b>Lua compare-and-delete</b>，
 * 只有当锁的持有者令牌与本实例记录的一致时才删除。
 *
 * <h3>为什么解锁必须校验属主</h3>
 * 直接 {@code DEL key} 会在锁过期后被其他实例抢走时误删他人的锁，破坏互斥；
 * 因此本实例只删除「自己加过、且值未被替换」的锁。注意：若业务执行时间可能超过
 * TTL，仍应调大 TTL 或续租——「锁过期后被他人接管」的窗口对 CAS 删除同样不可见。
 */
public class RedisLockProviderImpl implements RedisLockProvider {

    private static final Logger logger = LoggerFactory.getLogger(RedisLockProviderImpl.class);

    /** Lua：值匹配才删除（常量时间语义由 Redis 单线程执行保证原子性） */
    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    /** Redis 管理器 */
    private final RedisManager redisManager;

    /** Redis 连接名，null 使用默认连接 */
    private final String connectionName;

    /** 锁值前缀，用于标识锁持有者（本实例 + 线程） */
    private final String lockValuePrefix;

    /** 本实例成功加锁的 key → 令牌，用于解锁时校验属主 */
    private final Map<String, String> ownedTokens = new ConcurrentHashMap<>();

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
                ownedTokens.put(key, value);
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
        String token = ownedTokens.get(key);
        if (token == null) {
            // 本实例没加过该锁（或已释放）：绝不能 DEL —— 那会删掉别人的锁
            logger.debug("[redis-lock] 跳过解锁（本实例未持有该锁）: key={}", key);
            return;
        }
        try {
            Long deleted = commands().eval(UNLOCK_SCRIPT, ScriptOutputType.INTEGER,
                    new String[]{key}, token);
            if (deleted != null && deleted > 0) {
                logger.debug("[redis-lock] 释放锁: key={}", key);
            } else {
                // 值已被替换（锁过期后他人接管）或已过期：不要动别人的锁
                logger.warn("[redis-lock] 锁已不属于本实例（可能已超时被接管），未删除: key={}", key);
            }
        } catch (Exception e) {
            logger.error("[redis-lock] 释放锁失败: key={} - {}", key, e.getMessage());
        } finally {
            ownedTokens.remove(key);
        }
    }
}