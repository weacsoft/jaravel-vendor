package com.weacsoft.jaravel.vendor.cache.database;

import com.weacsoft.jaravel.vendor.core.publish.PublishableMigration;

import java.util.List;
import java.util.Map;

/**
 * cache-database 模块的迁移文件发布声明。
 * <p>
 * 由 {@code artisan vendor:publish --tag=cache-database}（单模块）或
 * {@code artisan vendor:publish --tag=migrations}（全部模块）发布。
 * <p>
 * 发布内容：{@code jaravel_cache} 缓存表的建表迁移
 * （classpath 资源 {@code jaravel/migrations/Migration_20240101_CreateJaravelCacheTable.java}）
 * 落到业务工程迁移源代码目录，随后执行 {@code artisan migrate} 建表。
 * <p>
 * 发布后的迁移文件包名由命令层按工程基包名重写（{@code <basePackage>.database.migrations}），
 * 与本文件的内置包名无关。
 */
public class CacheDatabaseMigrationPublishable implements PublishableMigration {

    /** 内置迁移文件 classpath 路径（jar 内资源） */
    public static final String CLASSPATH_RESOURCE =
            "jaravel/migrations/Migration_20240101_CreateJaravelCacheTable.java";

    /** 发布文件名（遵循 Migration_YYYY_MM_DD_ 约定） */
    public static final String FILE_NAME = "Migration_20240101_CreateJaravelCacheTable.java";

    @Override
    public String tag() {
        return "cache-database";
    }

    @Override
    public List<Map.Entry<String, String>> migrationFiles() {
        return List.of(Map.entry(CLASSPATH_RESOURCE, FILE_NAME));
    }

    @Override
    public String description() {
        return "数据库缓存表 jaravel_cache 建表迁移";
    }
}
