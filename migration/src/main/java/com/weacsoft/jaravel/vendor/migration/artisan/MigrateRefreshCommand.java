package com.weacsoft.jaravel.vendor.migration.artisan;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;
import com.weacsoft.jaravel.vendor.migration.engine.MigrationExecutor;

/**
 * Artisan 命令：{@code migrate:refresh}，回滚所有迁移并重新执行。
 * <p>
 * 对齐 Laravel {@code php artisan migrate:refresh}，委托给 {@link MigrationExecutor} 执行。
 */
public class MigrateRefreshCommand extends ArtisanCommand {

    private final MigrationExecutor executor;

    public MigrateRefreshCommand(MigrationExecutor executor) {
        this.executor = executor;
    }

    @Override
    public String signature() {
        return "migrate:refresh";
    }

    @Override
    public String description() {
        return "回滚所有迁移并重新执行";
    }

    @Override
    public int handle() {
        try {
            executor.execute("refresh");
            return 0;
        } catch (Exception e) {
            error("刷新失败: " + e.getMessage());
            return 1;
        }
    }
}
