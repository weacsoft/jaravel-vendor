package com.weacsoft.jaravel.vendor.storage.autoconfigure;

import com.weacsoft.jaravel.vendor.core.condition.OnDriverInUseCondition;

/**
 * 仅当<b>显式（或缺省）选用</b> local 磁盘驱动时才装配 {@code LocalFilesystemDriver}。
 *
 * <h3>命中条件</h3>
 * 以下任一情况装配：
 * <ul>
 *   <li>{@code jaravel.storage.disks.*.driver} 取值为 {@code local} / {@code public}；</li>
 *   <li>配置了某个 disk 但<b>未写 driver</b>，或缺省未配置任何 disk（回退到 local 兜底）。</li>
 * </ul>
 *
 * <h3>为什么 local 认缺省</h3>
 * 遵循 vendor 模块组的统一兜底原则：用户写了 {@code jaravel.storage.disks} 但没写具体 driver、
 * 或完全没写 disks 时，用最基础的 {@code local} 磁盘保证功能基本可用。因此本条件对缺省视为命中。
 *
 * @see OnDriverInUseCondition
 */
public class OnLocalDiskDriverCondition extends OnDriverInUseCondition {

    public OnLocalDiskDriverCondition() {
        super("local", "jaravel.storage.disks.", ".driver",
                "public", "jaravel.storage.disks.", ".driver");
        matchIfAbsent();
    }
}
