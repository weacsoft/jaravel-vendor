package com.weacsoft.jaravel.vendor.core.lock;

/**
 * 分布式锁提供者接口。
 * <p>
 * 定义于 core 模块，使 schedule 等模块无需强依赖 redis。
 * 通过 {@link RegisterLockProvider @RegisterLockProvider} 注解注册到
 * {@link LockProviderManager}，不进入 Spring 容器。
 * <p>
 * 当 Redis 可用时，由 redis 模块的 {@code RedisLockProviderImpl} 提供实现；
 * 当 Redis 不可用时，{@link LockProviderManager} 自动兜底为同步锁（单机执行）。
 * <p>
 * 业务方也可自定义实现（如基于 ZooKeeper、etcd 等），通过 {@code @RegisterLockProvider} 注册自动生效。
 *
 * @see RegisterLockProvider
 * @see LockProviderManager
 * @see LockProviderRegistrar
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