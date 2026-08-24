package com.weacsoft.jaravel.vendor.artisan.make;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;

/**
 * make:listener 命令 — 生成 Listener 类。
 * <p>
 * 使用方式：{@code java -jar app.jar artisan make:listener SendWelcomeEmailListener --event=UserRegisteredEvent}
 */
public class MakeListenerCommand extends ArtisanCommand {

    private MakeCodeProperties properties;

    @Override
    public String signature() {
        return "make:listener {name} {--event=} {--force}";
    }

    @Override
    public String description() {
        return "生成 Listener 类（对齐 Laravel php artisan make:listener）";
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
        String eventName = option("event");
        boolean force = hasOption("force");
        try {
            String path = MakeGenerator.generateListener(properties, name, eventName, force);
            info("Listener created: " + path);
            if (eventName == null || eventName.isEmpty()) {
                info("提示: 未指定 --event，请手动修改监听器中的事件类型");
            }
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
