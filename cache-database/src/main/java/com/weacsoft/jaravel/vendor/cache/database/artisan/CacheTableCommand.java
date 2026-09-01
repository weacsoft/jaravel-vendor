package com.weacsoft.jaravel.vendor.cache.database.artisan;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;
import com.weacsoft.jaravel.vendor.artisan.make.MakeCodeProperties;
import com.weacsoft.jaravel.vendor.migration.MigrationGenerator;

import java.io.IOException;

/**
 * Artisan 命令：{@code cache:table}，生成数据库缓存表的迁移文件。
 * <p>
 * 对齐 Laravel {@code php artisan cache:table}，但<b>不直接建表</b>，
 * 而是生成一个迁移 Java 文件到业务工程的<b>迁移源代码目录</b>
 *（默认 {@code src/main/java/<basePackage 路径>/database/migrations}，
 * 由 {@link MakeCodeProperties} 决定；未注入时退化为历史默认 {@code database/migrations}）。
 * 用户随后执行 {@code artisan migrate} 即可创建表。
 * <p>
 * 提示：若使用默认表名 {@code jaravel_cache}，可直接改用
 * {@code artisan vendor:publish --tag=migrations} 发布本模块内置迁移，免去生成步骤。
 * <p>
 * 仅当使用 {@code database} 缓存驱动时需要执行此命令。
 * 使用 {@code array} 或 {@code file} 驱动时无需执行。
 */
public class CacheTableCommand extends ArtisanCommand {

    private static final String DEFAULT_OUTPUT_DIR = "database/migrations";
    private static final String DEFAULT_PACKAGE = "database.migrations";

    private final String table;
    private final MakeCodeProperties makeProps;

    public CacheTableCommand(String table) {
        this(table, null);
    }

    /**
     * @param table      缓存表名
     * @param makeProps  artisan 代码生成配置（决定迁移文件落盘目录与包名，可为 null）
     */
    public CacheTableCommand(String table, MakeCodeProperties makeProps) {
        this.table = (table == null || table.isEmpty()) ? "jaravel_cache" : table;
        this.makeProps = makeProps;
    }

    @Override
    public String signature() {
        return "cache:table";
    }

    @Override
    public String description() {
        return "生成数据库缓存表迁移文件（仅 database 驱动需要）";
    }

    @Override
    public int handle() {
        info("正在生成缓存表迁移文件...");
        info("  表名: " + table);

        String upBody = "        schema.create(\"" + table + "\", table -> {\n" +
                "            table.string(\"cache_key\", 255).primary();\n" +
                "            table.text(\"cache_value\").nullable();\n" +
                "            table.bigInteger(\"expires_at\").defaultValue(0);\n" +
                "        });";

        String downBody = "        schema.dropIfExists(\"" + table + "\");";

        try {
            String outputDir = makeProps != null ? makeProps.getMigrationSourceDir() : DEFAULT_OUTPUT_DIR;
            String packageName = makeProps != null ? makeProps.getMigrationPackage() : DEFAULT_PACKAGE;
            String path = MigrationGenerator.generate(
                    outputDir, packageName,
                    "create cache table", upBody, downBody);
            info("迁移文件已生成: " + path);
            info("请执行 artisan migrate 以创建表");
            return 0;
        } catch (IOException e) {
            error("生成迁移文件失败: " + e.getMessage());
            return 1;
        }
    }
}
