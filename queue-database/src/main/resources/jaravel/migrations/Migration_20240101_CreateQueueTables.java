package com.weacsoft.jaravel.database.migrations;

import com.weacsoft.jaravel.vendor.migration.Migration;
import com.weacsoft.jaravel.vendor.migration.Schema;
import com.weacsoft.jaravel.vendor.migration.MigrationAnnotation;

/**
 * 迁移：创建 jaravel 数据库队列表（queue-database 模块内置迁移）。
 * <p>
 * 本文件打包在 queue-database 模块 jar 中，由
 * {@code artisan vendor:publish --tag=migrations} 发布到业务工程迁移目录，
 * 再执行 {@code artisan migrate} 完成建表（对齐 Laravel vendor:publish --tag=migrations）。
 * <p>
 * 默认表结构：{@code jobs}（任务表）与 {@code failed_jobs}（失败任务表），
 * 与 {@code DatabaseQueueDriver#defineJobsTable} / {@code #defineFailedJobsTable} 一致。
 * 自定义任务表名（{@code jaravel.queue.table}）的部署请用 {@code artisan queue:table}
 * 生成迁移并修改表名后再执行 {@code artisan migrate}。
 */
@MigrationAnnotation
public class Migration_20240101_CreateQueueTables implements Migration {

    @Override
    public void up(Schema schema) {
        schema.create("jobs", table -> {
            table.id();
            table.string("queue", 255);
            table.text("payload");
            table.integer("attempts").defaultValue(0);
            table.bigInteger("reserved_at").nullable();
            table.bigInteger("available_at");
            table.bigInteger("created_at");
            table.index("queue");
        });
        schema.create("failed_jobs", table -> {
            table.id();
            table.string("queue", 255);
            table.text("payload");
            table.text("exception").nullable();
            table.integer("attempts").defaultValue(0);
            table.bigInteger("failed_at");
            table.index("queue");
        });
    }

    @Override
    public void down(Schema schema) {
        schema.dropIfExists("failed_jobs");
        schema.dropIfExists("jobs");
    }
}
