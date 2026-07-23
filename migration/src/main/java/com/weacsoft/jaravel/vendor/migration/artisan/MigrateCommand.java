package com.weacsoft.jaravel.vendor.migration.artisan;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;
import com.weacsoft.jaravel.vendor.migration.engine.MigrationExecutor;

/**
 * Artisan 命令：{@code migrate}，执行数据库迁移。
 * <p>
 * 对齐 Laravel {@code php artisan migrate}，委托给 {@link MigrationExecutor} 执行。
 * 当 migration 和 artisan 模块同时存在于 classpath 时，由
 * {@code MigrationArtisanAutoConfiguration} 自动注册为 Spring Bean。
 */
public class MigrateCommand extends ArtisanCommand {

    private final MigrationExecutor executor;

    public MigrateCommand(MigrationExecutor executor) {
        this.executor = executor;
    }

    @Override
    public String signature() {
        return "migrate {--force}";
    }

    @Override
    public String description() {
        return "执行数据库迁移";
    }

    @Override
    public int handle() {
        try {
            executor.execute("migrate");
            return 0;
        } catch (Exception e) {
            error("迁移执行失败: " + e.getMessage());
            return 1;
        }
    }
}
