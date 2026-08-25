package com.weacsoft.jaravel.vendor.springboot.queuedatabase;

import com.weacsoft.jaravel.vendor.springboot.condition.OnDriverInUseCondition;

/**
 * 仅当<b>显式选用</b> database 作为队列驱动时才装配 {@link DatabaseQueueDriver} 相关 Bean。
 *
 * <h3>命中条件</h3>
 * 仅当 {@code jaravel.queue.driver} 取值为 {@code database} 时装配。
 *
 * <h3>为什么不兜底</h3>
 * database 队列依赖 DataSource，不在兜底列表；缺失应回退到 {@code sync}（内存队列）。
 * 因此本条件<b>不认缺省</b>。
 *
 * <h3>P3 解耦说明</h3>
 * P3 前位于 queue-database 模块（{@code vendor.queue.database}）；基类
 * {@link OnDriverInUseCondition} 是纯 Spring Condition 已迁入 springboot，
 * 本类随之迁入以消除循环依赖（queue-database 不能依赖 springboot）。语义不变。
 *
 * @see OnDriverInUseCondition
 */
public class OnDatabaseQueueDriverCondition extends OnDriverInUseCondition {

    public OnDatabaseQueueDriverCondition() {
        super("database", "jaravel.queue.driver");
    }
}
