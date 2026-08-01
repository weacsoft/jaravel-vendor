package com.weacsoft.jaravel.vendor.queue.database;

import com.weacsoft.jaravel.vendor.core.condition.OnDriverInUseCondition;

/**
 * 仅当<b>显式选用</b> database 作为队列驱动时才装配 {@link DatabaseQueueDriver} 相关 Bean。
 *
 * <h3>命中条件</h3>
 * 仅当 {@code jaravel.queue.driver} 取值为 {@code database} 时装配。
 *
 * <h3>为什么严格按需</h3>
 * database 队列依赖 DataSource，不在兜底列表；缺省应回退到 {@code sync}（内存队列）。
 * 因此本条件<b>不认缺省</b>。
 *
 * @see OnDriverInUseCondition
 */
public class OnDatabaseQueueDriverCondition extends OnDriverInUseCondition {

    public OnDatabaseQueueDriverCondition() {
        super("database", "jaravel.queue.driver");
    }
}
