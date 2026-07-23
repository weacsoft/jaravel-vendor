package com.weacsoft.jaravel.vendor.artisan.make;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;

/**
 * make:model 命令 — 生成 Model 类。
 * <p>
 * 使用方式：{@code java -jar app.jar artisan make:model User}
 */
public class MakeModelCommand extends ArtisanCommand {

    private MakeCodeProperties properties;

    @Override
    public String signature() {
        return "make:model {name} {--force}";
    }

    @Override
    public String description() {
        return "生成 Model 类（对齐 Laravel php artisan make:model）";
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
            String path = MakeGenerator.generateModel(properties, name, force);
            info("Model created: " + path);
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
