package com.weacsoft.jaravel.database.migrations;

import com.weacsoft.jaravel.vendor.migration.Migration;
import com.weacsoft.jaravel.vendor.migration.Schema;
import com.weacsoft.jaravel.vendor.migration.MigrationAnnotation;

/**
 * 迁移：创建 jaravel 数据库文件存储表（storage-database 模块内置迁移）。
 * <p>
 * 本文件打包在 storage-database 模块 jar 中，由
 * {@code artisan vendor:publish --tag=migrations} 发布到业务工程迁移目录，
 * 再执行 {@code artisan migrate} 完成建表（对齐 Laravel vendor:publish --tag=migrations）。
 * <p>
 * 默认表结构（binary=true / content-column=content 的磁盘配置）：
 * {@code storage_file}（元信息）与 {@code storage_file_chunk}（内容分片）。
 * 自定义表前缀/列名的磁盘请用 {@code artisan storage:table} 生成对应迁移并修改表结构，
 * 再执行 {@code artisan migrate}。
 */
@MigrationAnnotation
public class Migration_20240101_CreateStorageTables implements Migration {

    @Override
    public void up(Schema schema) {
        schema.create("storage_file", table -> {
            table.string("disk", 64).primary();
            table.string("path", 1024).primary();
            table.string("visibility", 16).defaultValue("private");
            table.string("mime_type", 255).nullable();
            table.bigInteger("size").defaultValue(0);
            table.integer("chunk_count").defaultValue(0);
            table.bigInteger("created_at").nullable();
            table.bigInteger("updated_at").nullable();
        });
        schema.create("storage_file_chunk", table -> {
            table.string("disk", 64).primary();
            table.string("path", 1024).primary();
            table.integer("chunk_index").primary();
            table.binary("content").nullable();
            table.integer("size").defaultValue(0);
            table.bigInteger("created_at").nullable();
            table.bigInteger("updated_at").nullable();
        });
    }

    @Override
    public void down(Schema schema) {
        schema.dropIfExists("storage_file_chunk");
        schema.dropIfExists("storage_file");
    }
}
