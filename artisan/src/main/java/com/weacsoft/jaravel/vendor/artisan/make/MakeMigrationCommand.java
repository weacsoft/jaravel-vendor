package com.weacsoft.jaravel.vendor.artisan.make;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;

/**
 * make:migration 命令 — 生成 Migration 类。
 * <p>
 * 使用方式：{@code java -jar app.jar artisan make:migration create_users_table}
 */
public class MakeMigrationCommand extends ArtisanCommand {

    private MakeCodeProperties properties;

    @Override
    public String signature() {
        return "make:migration {name} {--force}";
    }

    @Override
    public String description() {
        return "生成 Migration 类（对齐 Laravel php artisan make:migration）";
    }

    public void setProperties(MakeCodeProperties properties) {
        this.properties = properties;
    }

    @Override
    public int handle() {
        String name = argument("name");
        if (name == null || name.isEmpty()) {
            error("缺少参数: name");
            return 1;
        }
        boolean force = hasOption("force");
        try {
            String path = MakeGenerator.generateMigration(properties, name, force);
            info("Migration created: " + path);
            return 0;
        } catch (IllegalStateException e) {
            error(e.getMessage());
            return 1;
        } catch (Exception e) {
            error("生成失败: " + e.getMessage());
            return 1;
        }
    }
}
