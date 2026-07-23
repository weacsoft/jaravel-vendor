package com.weacsoft.jaravel.vendor.cache.artisan;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;
import com.weacsoft.jaravel.vendor.cache.driver.DatabaseCacheDriver;

/**
 * Artisan 命令：{@code cache:table}，创建数据库缓存表。
 * <p>
 * 对齐 Laravel {@code php artisan cache:table}，在数据库中创建缓存表
 * （默认 {@code jaravel_cache}，可通过 {@code jaravel.cache.database-table} 配置）。
 * <p>
 * 仅当使用 {@code database} 缓存驱动时需要执行此命令。
 * 使用 {@code array} 或 {@code file} 驱动时无需执行。
 * <p>
 * 当 cache 和 artisan 模块同时存在于 classpath 时，由
 * {@code CacheArtisanAutoConfiguration} 自动注册为 Spring Bean。
 */
public class CacheTableCommand extends ArtisanCommand {

    private final DatabaseCacheDriver databaseCacheDriver;

    public CacheTableCommand(DatabaseCacheDriver databaseCacheDriver) {
        this.databaseCacheDriver = databaseCacheDriver;
    }

    @Override
    public String signature() {
        return "cache:table";
    }

    @Override
    public String description() {
        return "创建数据库缓存表（仅 database 驱动需要）";
    }

    @Override
    public int handle() {
        String table = databaseCacheDriver.getTable();
        info("正在创建缓存表: " + table);
        boolean success = databaseCacheDriver.createTable();
        if (success) {
            info("缓存表创建成功: " + table);
            return 0;
        } else {
            error("缓存表创建失败，请检查数据库权限或表是否已存在");
            return 1;
        }
    }
}
