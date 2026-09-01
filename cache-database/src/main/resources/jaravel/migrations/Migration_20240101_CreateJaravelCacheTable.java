package com.weacsoft.jaravel.database.migrations;

import com.weacsoft.jaravel.vendor.migration.Migration;
import com.weacsoft.jaravel.vendor.migration.Schema;
import com.weacsoft.jaravel.vendor.migration.MigrationAnnotation;

/**
 * 迁移：创建 jaravel 数据库缓存表（cache-database 模块内置迁移）。
 * <p>
 * 本文件打包在 cache-database 模块 jar 中，由
 * {@code artisan vendor:publish --tag=migrations} 发布到业务工程迁移目录，
 * 再执行 {@code artisan migrate} 完成建表（对齐 Laravel vendor:publish --tag=migrations）。
 * <p>
 * 类名采用 {@code Migration_YYYY_MM_DD_PascalCaseDescription} 约定；
 * 使用 {@code @MigrationAnnotation} 标记，由 {@code MigrationScanner} 运行时识别。
 * <p>
 * 表结构与 {@code DatabaseCacheDriver#createTable()} 的 Blueprint 定义保持一致。
 */
@MigrationAnnotation
public class Migration_20240101_CreateJaravelCacheTable implements Migration {

    @Override
    public void up(Schema schema) {
        schema.create("jaravel_cache", table -> {
            table.string("cache_key", 255).primary();
            table.text("cache_value").nullable();
            table.bigInteger("expires_at").defaultValue(0);
        });
    }

    @Override
    public void down(Schema schema) {
        schema.dropIfExists("jaravel_cache");
    }
}
