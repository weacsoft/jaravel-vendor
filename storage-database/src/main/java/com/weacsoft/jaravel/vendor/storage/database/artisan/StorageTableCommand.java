package com.weacsoft.jaravel.vendor.storage.database.artisan;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;
import com.weacsoft.jaravel.vendor.artisan.make.MakeCodeProperties;
import com.weacsoft.jaravel.vendor.migration.MigrationGenerator;

import java.io.IOException;

/**
 * Artisan 命令：{@code storage:table}，生成 storage 数据库表的迁移文件。
 * <p>
 * 对齐 Laravel {@code php artisan storage:table}，但<b>不直接建表</b>，
 * 而是生成一个迁移 Java 文件到项目的 {@code database/migrations/} 目录。
 * 用户随后执行 {@code artisan migrate} 即可创建表。
 * <p>
 * 生成的迁移文件使用 {@code schema.create()} + Blueprint 流式 API 声明表结构，
 * 由 migration 模块在执行迁移时自动适配数据库方言（MySQL/SQLite/H2/PostgreSQL/SQL Server/Oracle）。
 * <p>
 * 仅当使用 {@code database} 磁盘驱动时需要此迁移。
 * 使用 {@code local}（默认）时不需要任何数据库表。
 */
public class StorageTableCommand extends ArtisanCommand {

    /** 默认输出目录（相对于项目根目录） */
    private static final String DEFAULT_OUTPUT_DIR = "database/migrations";

    /** 默认包名 */
    private static final String DEFAULT_PACKAGE = "database.migrations";

    private final String tablePrefix;
    private final String contentColumn;
    private final boolean binary;
    private final MakeCodeProperties makeProps;

    /**
     * 使用默认配置创建命令。
     * tablePrefix="storage_", contentColumn="content", binary=true
     */
    public StorageTableCommand() {
        this("storage_", "content", true, null);
    }

    /**
     * @param tablePrefix   表名前缀（默认 storage_）
     * @param contentColumn 内容列名（默认 content）
     * @param binary        是否使用二进制列（true=LONGBLOB, false=LONGTEXT）
     */
    public StorageTableCommand(String tablePrefix, String contentColumn, boolean binary) {
        this(tablePrefix, contentColumn, binary, null);
    }

    /**
     * @param tablePrefix   表名前缀（默认 storage_）
     * @param contentColumn 内容列名（默认 content）
     * @param binary        是否使用二进制列（true=LONGBLOB, false=LONGTEXT）
     * @param makeProps     artisan 代码生成配置（决定迁移文件落盘目录与包名，可为 null）
     */
    public StorageTableCommand(String tablePrefix, String contentColumn, boolean binary,
                               MakeCodeProperties makeProps) {
        this.tablePrefix = (tablePrefix == null || tablePrefix.isBlank()) ? "storage_" : tablePrefix;
        this.contentColumn = (contentColumn == null || contentColumn.isBlank()) ? "content" : contentColumn.trim();
        this.binary = binary;
        this.makeProps = makeProps;
    }

    @Override
    public String signature() {
        return "storage:table";
    }

    @Override
    public String description() {
        return "生成 storage 数据库表迁移文件（仅 database 磁盘驱动需要）";
    }

    @Override
    public int handle() {
        String filesTable = tablePrefix + "file";
        String chunksTable = tablePrefix + "file_chunk";
        String contentType = binary ? "LONGBLOB" : "LONGTEXT";

        info("正在生成 storage 表迁移文件...");
        info("  文件表: " + filesTable);
        info("  分片表: " + chunksTable);
        info("  内容列: " + contentColumn + " (" + contentType + ")");

        String upBody = buildUpBody(filesTable, chunksTable, contentType);
        String downBody = buildDownBody(filesTable, chunksTable);

        try {
            String outputDir = makeProps != null ? makeProps.getMigrationSourceDir() : DEFAULT_OUTPUT_DIR;
            String packageName = makeProps != null ? makeProps.getMigrationPackage() : DEFAULT_PACKAGE;
            String path = MigrationGenerator.generate(
                    outputDir, packageName,
                    "create storage tables", upBody, downBody);
            info("迁移文件已生成: " + path);
            info("请执行 artisan migrate 以创建表");
            return 0;
        } catch (IOException e) {
            error("生成迁移文件失败: " + e.getMessage());
            return 1;
        }
    }

    private String buildUpBody(String filesTable, String chunksTable, String contentType) {
        return "        schema.create(\"" + filesTable + "\", table -> {\n" +
                "            table.string(\"disk\", 64).primary();\n" +
                "            table.string(\"path\", 1024).primary();\n" +
                "            table.string(\"visibility\", 16).defaultValue(\"private\");\n" +
                "            table.string(\"mime_type\", 255).nullable();\n" +
                "            table.bigInteger(\"size\").defaultValue(0);\n" +
                "            table.integer(\"chunk_count\").defaultValue(0);\n" +
                "            table.bigInteger(\"created_at\").nullable();\n" +
                "            table.bigInteger(\"updated_at\").nullable();\n" +
                "        });\n" +
                "        schema.create(\"" + chunksTable + "\", table -> {\n" +
                "            table.string(\"disk\", 64).primary();\n" +
                "            table.string(\"path\", 1024).primary();\n" +
                "            table.integer(\"chunk_index\").primary();\n" +
                "            table.binary(\"" + contentColumn + "\").nullable();\n" +
                "            table.integer(\"size\").defaultValue(0);\n" +
                "            table.bigInteger(\"created_at\").nullable();\n" +
                "            table.bigInteger(\"updated_at\").nullable();\n" +
                "        });";
    }

    private String buildDownBody(String filesTable, String chunksTable) {
        return "        schema.dropIfExists(\"" + chunksTable + "\");\n" +
                "        schema.dropIfExists(\"" + filesTable + "\");";
    }
}
