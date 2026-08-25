package com.weacsoft.jaravel.vendor.springboot.rediscache;

import com.weacsoft.jaravel.vendor.core.condition.OnDriverInUseCondition;

/**
 * 仅当确实声明了 {@code driver: redis} 的缓存 store 时才装配 Redis 缓存驱动。
 *
 * <pre>
 * jaravel:
 *   cache:
 *     stores:
 *       redis:
 *         driver: redis     # ← 命中，装配
 *         connection: cache
 * </pre>
 *
 * 也可用 {@code jaravel.cache.redis.auto-register} 强制启用/关闭（优先级最高）。
 * <p>
 * 未选用 redis 缓存驱动时，本模块完全不装配，应用无需 Redis 即可启动。
 *
 * @see OnDriverInUseCondition
 */
public class OnRedisCacheStoreCondition extends OnDriverInUseCondition {

    public OnRedisCacheStoreCondition() {
        super("redis", "jaravel.cache.stores.", ".driver", "jaravel.cache.driver");
        enableKey("jaravel.cache.redis.auto-register");
    }
}
