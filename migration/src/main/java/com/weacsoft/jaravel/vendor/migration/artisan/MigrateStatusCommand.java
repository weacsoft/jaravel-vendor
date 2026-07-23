package com.weacsoft.jaravel.vendor.migration.artisan;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;
import com.weacsoft.jaravel.vendor.migration.engine.MigrationExecutor;

/**
 * Artisan 命令：{@code migrate:status}，查看迁移状态。
 * <p>
 * 对齐 Laravel {@code php artisan migrate:status}，委托给 {@link MigrationExecutor} 执行。
 */
public class MigrateStatusCommand extends ArtisanCommand {

    private final MigrationExecutor executor;

    public MigrateStatusCommand(MigrationExecutor executor) {
        this.executor = executor;
    }

    @Override
    public String signature() {
        return "migrate:status";
    }

    @Override
    public String description() {
        return "查看迁移状态";
    }

    @Override
    public int handle() {
        try {
            executor.execute("status");
            return 0;
        } catch (Exception e) {
            error("查看状态失败: " + e.getMessage());
            return 1;
        }
    }
}
