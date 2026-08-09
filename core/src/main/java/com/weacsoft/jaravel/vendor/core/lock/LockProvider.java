package com.weacsoft.jaravel.vendor.core.lock;

/**
 * 分布式锁提供者接口。
 * <p>
 * 定义于 core 模块，使 schedule 等模块无需强依赖 redis。
 * 当 Redis 可用时，由 redis 模块的 {@code RedisLockProviderImpl} 提供实现；
 * 当 Redis 不可用时，schedule 的分布式锁降级为单机执行（跳过锁）。
 * <p>
 * 业务方也可自定义实现（如基于 ZooKeeper、etcd 等），通过 Spring bean 注入自动生效。
 */
public interface LockProvider {

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