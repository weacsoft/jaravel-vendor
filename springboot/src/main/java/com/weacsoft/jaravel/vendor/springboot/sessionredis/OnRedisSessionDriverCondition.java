package com.weacsoft.jaravel.vendor.springboot.sessionredis;

import com.weacsoft.jaravel.vendor.core.condition.OnDriverInUseCondition;

/**
 * 仅当<b>显式选用</b> Redis 作为 Session 驱动时才装配本模块。
 *
 * <h3>命中条件</h3>
 * 以下任一配置为 {@code redis} 即装配：
 * <pre>
 * jaravel:
 *   session:
 *     driver: redis        # ← 推荐写法，对齐 Laravel config/session.php 的 driver
 * </pre>
 * 或显式打开开关：
 * <pre>
 * jaravel:
 *   session:
 *     redis:
 *       auto-register: true
 * </pre>
 *
 * <h3>为什么改成显式选用</h3>
 * 此前使用 {@code @ConditionalOnProperty(..., matchIfMissing = true)}，
 * 只要 {@code session-redis} 出现在 classpath 就会自动装配并注入 {@code RedisManager}，
 * 导致「依赖装了但没启用」的项目在没有 Redis 时直接启动失败。
 * 现在遵循 vendor 模块组的统一原则：<b>安装 ≠ 启用，用上了才注册和配置</b>。
 *
 * @see OnDriverInUseCondition
 */
public class OnRedisSessionDriverCondition extends OnDriverInUseCondition {

    public OnRedisSessionDriverCondition() {
        super("redis", "jaravel.session.stores.", ".driver",
                "jaravel.session.driver", "jaravel.session.store");
        enableKey("jaravel.session.redis.auto-register");
    }
}
