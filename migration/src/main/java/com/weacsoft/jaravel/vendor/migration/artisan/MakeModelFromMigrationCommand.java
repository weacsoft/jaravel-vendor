package com.weacsoft.jaravel.vendor.migration.artisan;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;
import com.weacsoft.jaravel.vendor.artisan.make.MakeCodeProperties;
import com.weacsoft.jaravel.vendor.migration.MigrationFileParser;
import com.weacsoft.jaravel.vendor.migration.ParsedTable;
import com.weacsoft.jaravel.vendor.migration.ReverseModelGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Artisan 命令：{@code make:model-from-migration}，从迁移 Java 文件生成 Model 类。
 * <p>
 * 通过 {@link MigrationFileParser} 解析迁移文件中的表结构定义（无需连接数据库），
 * 再由 {@link ReverseModelGenerator} 生成包含字段注解、主键标注、软删除检测的
 * Model Java 源文件。
 * <p>
 * 与 {@code make:model-from-table}（从数据库表反向生成）互补，此命令直接从
 * 迁移源码中提取表结构，适用于表结构尚未迁移到数据库或无数据库环境的场景。
 * <p>
 * 使用方式：
 * <pre>
 * # 为指定表生成 Model
 * java -jar app.jar artisan make:model-from-migration users
 *
 * # 为所有迁移中定义的表生成 Model
 * java -jar app.jar artisan make:model-from-migration --all
 *
 * # 强制覆盖已存在的文件
 * java -jar app.jar artisan make:model-from-migration users --force
 * java -jar app.jar artisan make:model-from-migration --all --force
 * </pre>
 * 当 migration 和 artisan 模块同时存在于 classpath 时，由
 * {@code MigrationArtisanAutoConfiguration} 自动注册为 Spring Bean。
 *
 * @see MigrationFileParser
 * @see ReverseModelGenerator#generateFromParsedTable
 * @see MakeModelFromTableCommand
 */
public class MakeModelFromMigrationCommand extends ArtisanCommand {

    private final ReverseModelGenerator generator;
    private final MakeCodeProperties properties;
    private final MigrationFileParser parser;

    public MakeModelFromMigrationCommand(ReverseModelGenerator generator, MakeCodeProperties properties) {
        this.generator = generator;
        this.properties = properties;
        this.parser = new MigrationFileParser();
    }

    @Override
    public String signature() {
        return "make:model-from-migration {table?} {--all} {--force}";
    }

    @Override
    public String description() {
        return "从迁移 Java 文件生成 Model 类（无需连接数据库）";
    }

    @Override
    public int handle() {
        boolean force = hasOption("force");
        boolean all = hasOption("all");
        String table = argument("table");

        if (!all && (table == null || table.isEmpty())) {
            error("请指定表名或使用 --all 选项:");
            error("  make:model-from-migration {table}");
            error("  make:model-from-migration --all");
            return 1;
        }

        String migrationDir = properties.getMigrationDir();
        info("解析迁移文件目录: " + migrationDir);

        Map<String, ParsedTable> tables = parser.parseAll(migrationDir);
        if (tables.isEmpty()) {
            error("未在迁移目录中找到任何表定义: " + migrationDir);
            error("请检查 jaravel.artisan.make.migration-dir 配置");
            return 1;
        }

        if (all) {
            // 为所有表生成 Model
            List<String> generated = new ArrayList<>();
            List<String> failed = new ArrayList<>();
            for (Map.Entry<String, ParsedTable> entry : tables.entrySet()) {
                try {
                    String path = generator.generateFromParsedTable(
                        entry.getValue(), properties.getBasePackage(), properties.getOutputDir(), force);
                    info("Model 生成成功: " + path);
                    generated.add(entry.getKey());
                } catch (Exception e) {
                    error("生成失败 [" + entry.getKey() + "]: " + e.getMessage());
                    failed.add(entry.getKey());
                }
            }
            info(String.format("完成: %d 个成功, %d 个失败", generated.size(), failed.size()));
            return failed.isEmpty() ? 0 : 1;
        } else {
            // 为指定表生成 Model
            ParsedTable parsedTable = tables.get(table);
            if (parsedTable == null) {
                error("在迁移文件中未找到表定义: " + table);
                error("可用的表: " + String.join(", ", tables.keySet()));
                return 1;
            }
            try {
                String path = generator.generateFromParsedTable(
                    parsedTable, properties.getBasePackage(), properties.getOutputDir(), force);
                info("Model 生成成功: " + path);
                return 0;
            } catch (Exception e) {
                error("生成失败: " + e.getMessage());
                return 1;
            }
        }
    }
}
