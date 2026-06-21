package com.weacsoft.jaravel.vendor.schedule;

/**
 * Redis 分布式锁提供者接口。
 * <p>
 * 抽象分布式锁实现，使 schedule 模块不直接依赖 redis-config 模块。
 * 当 redis-config 模块存在时，由其自动装配提供实现；否则分布式锁功能不可用。
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
