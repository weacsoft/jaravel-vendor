package com.weacsoft.jaravel.vendor.artisan.make;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;

/**
 * make:all 命令 — 一键生成 Controller + Middleware + Model + Migration + Command + Event + Listener。
 * <p>
 * 使用方式：{@code java -jar app.jar artisan make:all User}
 * <p>
 * 生成的文件（以 User 为例）：
 * <ul>
 *   <li>UserController.java         → {@code base-package}.controller</li>
 *   <li>UserMiddleware.java         → {@code base-package}.middleware</li>
 *   <li>UserModel.java              → {@code base-package}.model</li>
 *   <li>Migration_YYYY_MM_DD_User.java → {@code base-package}.migration</li>
 *   <li>UserCommand.java            → {@code base-package}.command</li>
 *   <li>UserEvent.java              → {@code base-package}.event</li>
 *   <li>UserListener.java           → {@code base-package}.listener</li>
 * </ul>
 */
public class MakeAllCommand extends ArtisanCommand {

    /** 可抛出 IOException 的生成器接口 */
    @FunctionalInterface
    private interface GeneratorSupplier {
        String get() throws Exception;
    }

    private MakeCodeProperties properties;

    @Override
    public String signature() {
        return "make:all {name} {--force}";
    }

    @Override
    public String description() {
        return "一键生成全部（Controller+Middleware+Model+Migration+Command+Event+Listener）";
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
        int successCount = 0;
        int failCount = 0;

        info("");
        info("===== make:all " + name + " =====");
        info("");

        // 1. Controller
        if (tryGenerate("Controller", () -> MakeGenerator.generateController(properties, name, force))) {
            successCount++;
        } else {
            failCount++;
        }

        // 2. Middleware
        if (tryGenerate("Middleware", () -> MakeGenerator.generateMiddleware(properties, name, force))) {
            successCount++;
        } else {
            failCount++;
        }

        // 3. Model
        if (tryGenerate("Model", () -> MakeGenerator.generateModel(properties, name, force))) {
            successCount++;
        } else {
            failCount++;
        }

        // 4. Migration
        if (tryGenerate("Migration", () -> MakeGenerator.generateMigration(properties, name, force))) {
            successCount++;
        } else {
            failCount++;
        }

        // 5. Command
        if (tryGenerate("Command", () -> MakeGenerator.generateCommand(properties, name, force))) {
            successCount++;
        } else {
            failCount++;
        }

        // 6. Event
        String eventName = MakeGenerator.ensureSuffix(name, "Event");
        if (tryGenerate("Event", () -> MakeGenerator.generateEvent(properties, name, force))) {
            successCount++;
        } else {
            failCount++;
        }

        // 7. Listener (关联到刚生成的 Event)
        if (tryGenerate("Listener", () -> MakeGenerator.generateListener(properties, name, eventName, force))) {
            successCount++;
        } else {
            failCount++;
        }

        info("");
        info("===== 完成: " + successCount + " 成功, " + failCount + " 跳过 =====");
        return failCount > 0 ? 1 : 0;
    }

    /**
     * 尝试生成文件，捕获异常并输出结果。
     *
     * @return true=成功, false=跳过/失败
     */
    private boolean tryGenerate(String type, GeneratorSupplier generator) {
        try {
            String path = generator.get();
            info("  [+] " + type + " created: " + shortenPath(path));
            return true;
        } catch (IllegalStateException e) {
            info("  [-] " + type + " skipped: " + e.getMessage());
            return false;
        } catch (Exception e) {
            error("  [!] " + type + " failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * 缩短路径显示（只显示包名+类名）。
     */
    private String shortenPath(String fullPath) {
        int srcIndex = fullPath.replace('\\', '/').indexOf("src/main/java/");
        if (srcIndex >= 0) {
            return fullPath.substring(srcIndex + 14);
        }
        return fullPath;
    }
}
