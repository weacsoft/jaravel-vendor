package com.weacsoft.jaravel.vendor.springboot.storage;

import com.weacsoft.jaravel.vendor.core.condition.OnDriverInUseCondition;

/**
 * 仅当<b>显式选用</b> database 磁盘驱动时才装配 {@code DatabaseFilesystemDriver}。
 *
 * <h3>命中条件</h3>
 * 仅当 {@code jaravel.storage.disks.*.driver} 取值为 {@code database} 时装配。
 *
 * <h3>为什么严格按需（不认缺省）</h3>
 * database 磁盘依赖数据库，不在兜底列表；缺省应回退到 {@code local}。因此本条件<b>不认缺省</b>。
 *
 * @see OnDriverInUseCondition
 */
public class OnDatabaseDiskDriverCondition extends OnDriverInUseCondition {

    public OnDatabaseDiskDriverCondition() {
        super("database", "jaravel.storage.disks.", ".driver");
    }
}
