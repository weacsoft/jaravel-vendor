package com.weacsoft.jaravel.vendor.schedule;

import com.weacsoft.jaravel.vendor.core.lock.LockProvider;

/**
 * 默认同步锁提供者（单机模式）。
 * <p>
 * 当未引入 Redis 等分布式锁模块时，使用此实现作为兜底。
 * 所有 {@code tryLock} 调用都会成功——单机模式下不存在并发冲突。
 * <p>
 * 业务方引入 Redis 模块后，{@code RedisLockProviderImpl} 会自动覆盖本实现。
 *
 * @see LockProvider
 * @see ScheduleAutoConfiguration
 */
public class SyncLockProvider implements LockProvider {

    @Override
    public boolean tryLock(String key, long ttlSeconds) {
        // 单机模式：永远成功
        return true;
    }

    @Override
    public void unlock(String key) {
        // 单机模式：无锁可释放
    }
}