package com.weacsoft.jaravel.vendor.artisan.make;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;

/**
 * make:event 命令 — 生成 Event 类。
 * <p>
 * 使用方式：{@code java -jar app.jar artisan make:event UserRegisteredEvent}
 */
public class MakeEventCommand extends ArtisanCommand {

    private MakeCodeProperties properties;

    @Override
    public String signature() {
        return "make:event {name} {--force}";
    }

    @Override
    public String description() {
        return "生成 Event 类（对齐 Laravel php artisan make:event）";
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
            String path = MakeGenerator.generateEvent(properties, name, force);
            info("Event created: " + path);
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
