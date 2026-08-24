package com.weacsoft.jaravel.vendor.cache.artisan;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;
import com.weacsoft.jaravel.vendor.migration.MigrationGenerator;

import java.io.IOException;

/**
 * Artisan 命令：{@code cache:table}，生成数据库缓存表的迁移文件。
 * <p>
 * 对齐 Laravel {@code php artisan cache:table}，但<b>不直接建表</b>，
 * 而是生成一个迁移 Java 文件到项目的 {@code database/migrations/} 目录。
 * 用户随后执行 {@code artisan migrate} 即可创建表。
 * <p>
 * 仅当使用 {@code database} 缓存驱动时需要执行此命令。
 * 使用 {@code array} 或 {@code file} 驱动时无需执行。
 */
public class CacheTableCommand extends ArtisanCommand {

    private static final String DEFAULT_OUTPUT_DIR = "database/migrations";
    private static final String DEFAULT_PACKAGE = "database.migrations";

    private final String table;

    public CacheTableCommand(String table) {
        this.table = (table == null || table.isEmpty()) ? "jaravel_cache" : table;
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
            String path = MigrationGenerator.generate(
                    DEFAULT_OUTPUT_DIR, DEFAULT_PACKAGE,
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
