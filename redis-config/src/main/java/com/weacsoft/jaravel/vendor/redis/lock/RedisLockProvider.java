package com.weacsoft.jaravel.vendor.redis.lock;

/**
 * Redis 分布式锁提供者接口。
 * <p>
 * 抽象分布式锁实现，定义于 redis-config 模块中。
 * 当 Redis 配置可用时，由 {@link RedisLockProviderImpl} 提供实现；
 * schedule 模块通过依赖注入可选使用此接口，实现定时任务的分布式锁防重复执行。
 */
public interface RedisLockProvider {

    /**
     * 尝试获取分布式锁。
     *
     * @param key         锁键
     * @param ttlSeconds  锁持有时间（秒）
     * @return 是否成功获取锁
     */
    boolean tryLock(String key, long ttlSeconds);

    /**
     * 释放分布式锁。
     *
     * @param key 锁键
     */
    void unlock(String key);
}
