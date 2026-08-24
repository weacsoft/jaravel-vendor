package com.weacsoft.jaravel.vendor.migration.artisan;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;
import com.weacsoft.jaravel.vendor.migration.engine.MigrationExecutor;

/**
 * Artisan 命令：{@code migrate:reset}，回滚所有迁移。
 * <p>
 * 对齐 Laravel {@code php artisan migrate:reset}，委托给 {@link MigrationExecutor} 执行。
 */
public class MigrateResetCommand extends ArtisanCommand {

    private final MigrationExecutor executor;

    public MigrateResetCommand(MigrationExecutor executor) {
        this.executor = executor;
    }

    @Override
    public String signature() {
        return "migrate:reset";
    }

    @Override
    public String description() {
        return "回滚所有迁移";
    }

    @Override
    public int handle() {
        try {
            executor.execute("reset");
            return 0;
        } catch (Exception e) {
            error("重置失败: " + e.getMessage());
            return 1;
        }
    }
}
