package com.weacsoft.jaravel.vendor.migration.artisan;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;
import com.weacsoft.jaravel.vendor.artisan.make.MakeCodeProperties;
import com.weacsoft.jaravel.vendor.migration.ReverseModelGenerator;

/**
 * Artisan 命令：{@code make:model-from-table}，从数据库表反向生成 Model 类。
 * <p>
 * 对齐 Laravel 的反向工程能力，通过 JDBC 读取数据库表结构，
 * 自动生成包含字段注解、主键标注、软删除检测的 Model Java 源文件。
 * <p>
 * 使用方式：
 * <pre>
 * java -jar app.jar artisan make:model-from-table users
 * java -jar app.jar artisan make:model-from-table users --force
 * </pre>
 * 当 migration 和 artisan 模块同时存在于 classpath 时，由
 * {@code MigrationArtisanAutoConfiguration} 自动注册为 Spring Bean。
 */
public class MakeModelFromTableCommand extends ArtisanCommand {

    private final ReverseModelGenerator generator;
    private final MakeCodeProperties properties;

    public MakeModelFromTableCommand(ReverseModelGenerator generator, MakeCodeProperties properties) {
        this.generator = generator;
        this.properties = properties;
    }

    @Override
    public String signature() {
        return "make:model-from-table {table} {--force}";
    }

    @Override
    public String description() {
        return "从数据库表反向生成 Model 类（含字段、软删除检测）";
    }

    @Override
    public int handle() {
        String table = argument("table");
        if (table == null || table.isEmpty()) {
            error("请指定表名: make:model-from-table {table}");
            return 1;
        }
        boolean force = hasOption("force");
        try {
            String path = generator.generate(table, properties.getBasePackage(), properties.getOutputDir(), force);
            info("Model 生成成功: " + path);
            return 0;
        } catch (Exception e) {
            error("生成失败: " + e.getMessage());
            return 1;
        }
    }
}
