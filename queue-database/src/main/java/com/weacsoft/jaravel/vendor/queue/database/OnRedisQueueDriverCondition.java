package com.weacsoft.jaravel.vendor.queue.database;

import com.weacsoft.jaravel.vendor.core.condition.OnDriverInUseCondition;

/**
 * 仅当<b>显式选用</b> redis 作为队列驱动时才装配 Redis 队列相关 Bean。
 *
 * <h3>命中条件</h3>
 * 仅当 {@code jaravel.queue.driver} 取值为 {@code redis} 时装配。
 *
 * <h3>为什么严格按需</h3>
 * redis 队列依赖 RedisManager，不在兜底列表；缺省应回退到 {@code sync}（内存队列）。
 * 因此本条件<b>不认缺省</b>。此前使用 {@code @ConditionalOnProperty(..., havingValue = "redis")}，
 * 现统一到 vendor 模块组的 {@link OnDriverInUseCondition} 原则：<b>安装 ≠ 启用，用上了才注册</b>。
 *
 * @see OnDriverInUseCondition
 */
public class OnRedisQueueDriverCondition extends OnDriverInUseCondition {

    public OnRedisQueueDriverCondition() {
        super("redis", "jaravel.queue.driver");
    }
}
