package com.weacsoft.jaravel.vendor.queue.database;

import com.weacsoft.jaravel.vendor.core.publish.PublishableMigration;

import java.util.List;
import java.util.Map;

/**
 * queue-database 模块的迁移文件发布声明。
 * <p>
 * 由 {@code artisan vendor:publish --tag=queue-database}（单模块）或
 * {@code artisan vendor:publish --tag=migrations}（全部模块）发布。
 * <p>
 * 发布内容：{@code jobs} / {@code failed_jobs} 两张队列表的建表迁移
 * 落到业务工程迁移源代码目录，随后执行 {@code artisan migrate} 建表。
 * 自定义任务表名的部署请用 {@code artisan queue:table} 生成迁移并修改表名。
 */
public class QueueDatabaseMigrationPublishable implements PublishableMigration {

    /** 内置迁移文件 classpath 路径（jar 内资源） */
    public static final String CLASSPATH_RESOURCE =
            "jaravel/migrations/Migration_20240101_CreateQueueTables.java";

    /** 发布文件名（遵循 Migration_YYYY_MM_DD_ 约定） */
    public static final String FILE_NAME = "Migration_20240101_CreateQueueTables.java";

    @Override
    public String tag() {
        return "queue-database";
    }

    @Override
    public List<Map.Entry<String, String>> migrationFiles() {
        return List.of(Map.entry(CLASSPATH_RESOURCE, FILE_NAME));
    }

    @Override
    public String description() {
        return "数据库队列表 jobs / failed_jobs 建表迁移";
    }
}
