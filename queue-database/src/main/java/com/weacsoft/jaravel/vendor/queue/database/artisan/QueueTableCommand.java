package com.weacsoft.jaravel.vendor.queue.database.artisan;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;
import com.weacsoft.jaravel.vendor.artisan.make.MakeCodeProperties;
import com.weacsoft.jaravel.vendor.migration.MigrationGenerator;
import com.weacsoft.jaravel.vendor.queue.database.QueueDatabaseProperties;

import java.io.IOException;

/**
 * Artisan 命令：{@code queue:table}，生成队列任务表的迁移文件。
 * <p>
 * 对齐 Laravel {@code php artisan queue:table}，但<b>不直接建表</b>，
 * 而是生成一个迁移 Java 文件到项目的 {@code database/migrations/} 目录。
 * 用户随后执行 {@code artisan migrate} 即可创建表。
 * <p>
 * 仅当使用 {@code database} 队列驱动时需要执行此命令。
 * 使用 {@code sync}（默认）或 {@code redis} 驱动时无需执行。
 */
public class QueueTableCommand extends ArtisanCommand {

    private static final String DEFAULT_OUTPUT_DIR = "database/migrations";
    private static final String DEFAULT_PACKAGE = "database.migrations";

    private final QueueDatabaseProperties dbProps;
    private final MakeCodeProperties makeProps;

    public QueueTableCommand(QueueDatabaseProperties dbProps) {
        this(dbProps, null);
    }

    /**
     * @param dbProps   数据库队列配置（含任务表名）
     * @param makeProps artisan 代码生成配置（决定迁移文件落盘目录与包名，可为 null）
     */
    public QueueTableCommand(QueueDatabaseProperties dbProps, MakeCodeProperties makeProps) {
        this.dbProps = dbProps;
        this.makeProps = makeProps;
    }

    @Override
    public String signature() {
        return "queue:table";
    }

    @Override
    public String description() {
        return "生成队列任务表迁移文件 jobs/failed_jobs（仅 database 驱动需要）";
    }

    @Override
    public int handle() {
        String table = dbProps.getTable();
        String failedTable = "failed_jobs";

        info("正在生成队列任务表迁移文件...");
        info("  任务表: " + table);
        info("  失败任务表: " + failedTable);

        String upBody = "        schema.create(\"" + table + "\", table -> {\n" +
                "            table.id();\n" +
                "            table.string(\"queue\", 255);\n" +
                "            table.text(\"payload\");\n" +
                "            table.integer(\"attempts\").defaultValue(0);\n" +
                "            table.bigInteger(\"reserved_at\").nullable();\n" +
                "            table.bigInteger(\"available_at\");\n" +
                "            table.bigInteger(\"created_at\");\n" +
                "        });\n" +
                "        schema.create(\"" + failedTable + "\", table -> {\n" +
                "            table.id();\n" +
                "            table.string(\"queue\", 255);\n" +
                "            table.text(\"payload\");\n" +
                "            table.text(\"exception\").nullable();\n" +
                "            table.integer(\"attempts\").defaultValue(0);\n" +
                "            table.bigInteger(\"failed_at\");\n" +
                "        });";

        String downBody = "        schema.dropIfExists(\"" + failedTable + "\");\n" +
                "        schema.dropIfExists(\"" + table + "\");";

        try {
            String outputDir = makeProps != null ? makeProps.getMigrationSourceDir() : DEFAULT_OUTPUT_DIR;
            String packageName = makeProps != null ? makeProps.getMigrationPackage() : DEFAULT_PACKAGE;
            String path = MigrationGenerator.generate(
                    outputDir, packageName,
                    "create queue tables", upBody, downBody);
            info("迁移文件已生成: " + path);
            info("请执行 artisan migrate 以创建表");
            info("提示：请将 jaravel.queue.driver 设置为 database 以使用数据库队列");
            return 0;
        } catch (IOException e) {
            error("生成迁移文件失败: " + e.getMessage());
            return 1;
        }
    }
}
