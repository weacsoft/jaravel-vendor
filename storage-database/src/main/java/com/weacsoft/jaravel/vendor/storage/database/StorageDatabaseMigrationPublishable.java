package com.weacsoft.jaravel.vendor.storage.database;

import com.weacsoft.jaravel.vendor.core.publish.PublishableMigration;

import java.util.List;
import java.util.Map;

/**
 * storage-database 模块的迁移文件发布声明。
 * <p>
 * 由 {@code artisan vendor:publish --tag=storage-database}（单模块）或
 * {@code artisan vendor:publish --tag=migrations}（全部模块）发布。
 * <p>
 * 发布内容：{@code storage_file} / {@code storage_file_chunk} 两张表的建表迁移
 * （默认 binary 内容列 {@code content}；自定义前缀/列名的磁盘请用
 * {@code artisan storage:table} 生成迁移并按需修改表结构）
 * 落到业务工程迁移源代码目录，随后执行 {@code artisan migrate} 建表。
 */
public class StorageDatabaseMigrationPublishable implements PublishableMigration {

    /** 内置迁移文件 classpath 路径（jar 内资源） */
    public static final String CLASSPATH_RESOURCE =
            "jaravel/migrations/Migration_20240101_CreateStorageTables.java";

    /** 发布文件名（遵循 Migration_YYYY_MM_DD_ 约定） */
    public static final String FILE_NAME = "Migration_20240101_CreateStorageTables.java";

    @Override
    public String tag() {
        return "storage-database";
    }

    @Override
    public List<Map.Entry<String, String>> migrationFiles() {
        return List.of(Map.entry(CLASSPATH_RESOURCE, FILE_NAME));
    }

    @Override
    public String description() {
        return "数据库文件存储表 storage_file / storage_file_chunk 建表迁移";
    }
}
