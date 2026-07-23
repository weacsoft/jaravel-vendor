package com.weacsoft.jaravel.vendor.migration.artisan;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;
import com.weacsoft.jaravel.vendor.migration.engine.MigrationExecutor;

/**
 * Artisan 命令：{@code migrate:rollback}，回滚最近一批迁移。
 * <p>
 * 对齐 Laravel {@code php artisan migrate:rollback}，委托给 {@link MigrationExecutor} 执行。
 * 支持通过 {@code --step=N} 选项指定回滚批数。
 */
public class MigrateRollbackCommand extends ArtisanCommand {

    private final MigrationExecutor executor;

    public MigrateRollbackCommand(MigrationExecutor executor) {
        this.executor = executor;
    }

    @Override
    public String signature() {
        return "migrate:rollback {--step=1}";
    }

    @Override
    public String description() {
        return "回滚最近一批（或指定批数）迁移";
    }

    @Override
    public int handle() {
        try {
            String stepStr = option("step", "1");
            executor.execute("rollback=" + stepStr);
            return 0;
        } catch (Exception e) {
            error("回滚失败: " + e.getMessage());
            return 1;
        }
    }
}
