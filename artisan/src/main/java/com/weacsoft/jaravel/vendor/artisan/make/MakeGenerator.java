package com.weacsoft.jaravel.vendor.artisan.make;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 代码生成器，对齐 Laravel {@code php artisan make:xxx} 系列命令。
 * <p>
 * 根据 {@link MakeCodeProperties} 配置的基包和输出目录，生成 Controller、Middleware、
 * Model、Migration、Command、Event、Listener 的 Java 源文件，并自动放到对应包目录下。
 * <p>
 * <h3>支持的生成类型</h3>
 * <ul>
 *   <li>{@link #generateController}  — 生成 Controller 类</li>
 *   <li>{@link #generateMiddleware}  — 生成 Middleware 类</li>
 *   <li>{@link #generateModel}       — 生成 Model 类</li>
 *   <li>{@link #generateMigration}   — 生成 Migration 类</li>
 *   <li>{@link #generateCommand}     — 生成 ArtisanCommand 类</li>
 *   <li>{@link #generateEvent}       — 生成 Event 类</li>
 *   <li>{@link #generateListener}    — 生成 Listener 类</li>
 * </ul>
 * <p>
 * <h3>命名约定</h3>
 * <ul>
 *   <li>类名自动转为 PascalCase（如 {@code user_profile} → {@code UserProfile}）</li>
 *   <li>若名称不含 {@code Controller}/{@code Middleware} 等后缀，自动补全</li>
 *   <li>文件已存在时抛出 {@link IllegalStateException}，除非传入 {@code force=true}</li>
 * </ul>
 */
public final class MakeGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy_MM_dd");

    private MakeGenerator() {
    }

    // ==================== Controller ====================

    /**
     * 生成 Controller 类。
     *
     * @param properties 配置
     * @param name       控制器名称（如 {@code UserController} 或 {@code user}）
     * @param force      是否覆盖已存在文件
     * @return 生成的文件绝对路径
     */
    public static String generateController(MakeCodeProperties properties, String name, boolean force) throws IOException {
        String className = ensureSuffix(name, "Controller");
        String packageName = properties.getBasePackage() + ".app.http.controllers";
        String content = buildControllerSource(packageName, className);
        return writeJavaFile(properties.getOutputDir(), packageName, className, content, force);
    }

    private static String buildControllerSource(String packageName, String className) {
        return "package " + packageName + ";\n\n" +
                "import com.weacsoft.jaravel.vendor.http.controller.Controllers;\n" +
                "import com.weacsoft.jaravel.vendor.http.controller.request.Request;\n" +
                "import com.weacsoft.jaravel.vendor.http.controller.response.Response;\n" +
                "import com.weacsoft.jaravel.vendor.http.controller.response.ResponseBuilder;\n" +
                "import org.springframework.stereotype.Controller;\n\n" +
                "import java.util.HashMap;\n" +
                "import java.util.Map;\n\n" +
                "/**\n" +
                " * " + className + " 控制器，对齐 Laravel Controller。\n" +
                " * <p>\n" +
                " * 在路由中通过字符串引用注册：\n" +
                " * <pre>\n" +
                " * router.get(\"/users\", \"" + className + "::index\");\n" +
                " * router.post(\"/users\", \"" + className + "::store\");\n" +
                " * </pre>\n" +
                " */\n" +
                "@Controller\n" +
                "public class " + className + " implements Controllers {\n\n" +
                "    public Response index(Request request) {\n" +
                "        Map<String, Object> data = new HashMap<>();\n" +
                "        data.put(\"message\", \"" + className + " ready\");\n" +
                "        return ResponseBuilder.json(data);\n" +
                "    }\n" +
                "}\n";
    }

    // ==================== Middleware ====================

    /**
     * 生成 Middleware 类。
     */
    public static String generateMiddleware(MakeCodeProperties properties, String name, boolean force) throws IOException {
        String className = ensureSuffix(name, "Middleware");
        String packageName = properties.getBasePackage() + ".app.http.middleware";
        String content = buildMiddlewareSource(packageName, className);
        return writeJavaFile(properties.getOutputDir(), packageName, className, content, force);
    }

    private static String buildMiddlewareSource(String packageName, String className) {
        String aliasName = toSnakeCase(className).replaceAll("_middleware$", "");
        return "package " + packageName + ";\n\n" +
                "import com.weacsoft.jaravel.vendor.http.middleware.Middleware;\n" +
                "import com.weacsoft.jaravel.vendor.http.middleware.Middleware.NextFunction;\n" +
                "import com.weacsoft.jaravel.vendor.http.controller.request.Request;\n" +
                "import com.weacsoft.jaravel.vendor.http.controller.response.Response;\n" +
                "import com.weacsoft.jaravel.vendor.springboot.annotation.MiddlewareAlias;\n\n" +
                "/**\n" +
                " * " + className + " 中间件，对齐 Laravel Middleware。\n" +
                " * <p>\n" +
                " * 标注 {@code @MiddlewareAlias} 后由 SpringBoot classpath 扫描自动注册，\n" +
                " * 路由中通过字符串别名引用：\n" +
                " * <pre>\n" +
                " * router.group(Map.of(), g -> { ... }).middleware(\"" + aliasName + "\");\n" +
                " * </pre>\n" +
                " */\n" +
                "@MiddlewareAlias(\"" + aliasName + "\")\n" +
                "public class " + className + " implements Middleware {\n\n" +
                "    @Override\n" +
                "    public Response handle(Request request, NextFunction next, String... params) {\n" +
                "        // 前置处理：在请求到达控制器前执行\n" +
                "        // TODO: 添加中间件逻辑\n\n" +
                "        Response response = next.apply(request);\n\n" +
                "        // 后置处理：在响应返回前执行\n" +
                "        // TODO: 添加后置逻辑\n\n" +
                "        return response;\n" +
                "    }\n" +
                "}\n";
    }

    // ==================== Model ====================

    /**
     * 生成 Model 类。
     * <p>
     * 生成的 Model 继承 {@code BaseModel}，自带 {@code @Repository}、{@code @Table}、
     * {@code @Primary}、{@code @Column} 注解及静态查询方法，对齐 Laravel Eloquent 用法。
     */
    public static String generateModel(MakeCodeProperties properties, String name, boolean force) throws IOException {
        String className = toPascalCase(name);
        String packageName = properties.getBasePackage() + ".app.models";
        String content = buildModelSource(packageName, className);
        return writeJavaFile(properties.getOutputDir(), packageName, className, content, force);
    }

    private static String buildModelSource(String packageName, String className) {
        String tableName = toSnakeCase(className) + "s";
        return "package " + packageName + ";\n\n" +
                "import com.weacsoft.jaravel.vendor.database.BaseModel;\n" +
                "import com.weacsoft.jaravel.vendor.database.TimestampFill;\n" +
                "import gaarason.database.annotation.Column;\n" +
                "import gaarason.database.annotation.Primary;\n" +
                "import gaarason.database.annotation.Table;\n" +
                "import gaarason.database.query.QueryBuilder;\n" +
                "import lombok.Data;\n" +
                "import lombok.EqualsAndHashCode;\n" +
                "import org.springframework.stereotype.Repository;\n\n" +
                "import java.util.List;\n\n" +
                "/**\n" +
                " * " + className + " 模型，对齐 Laravel Eloquent。\n" +
                " * <p>\n" +
                " * 对应数据库表：{@code " + tableName + "}\n" +
                " */\n" +
                "@Data\n" +
                "@EqualsAndHashCode(callSuper = false)\n" +
                "@Repository\n" +
                "@Table(name = \"" + tableName + "\")\n" +
                "public class " + className + " extends BaseModel<" + className + ", Long> {\n\n" +
                "    @Primary\n" +
                "    @Column(name = \"id\")\n" +
                "    private Long id;\n\n" +
                "    // TODO: 添加业务字段\n\n" +
                "    // 创建时间：仅插入时自动填充，格式 yyyy-MM-dd HH:mm:ss（本地时间）\n" +
                "    @Column(name = \"created_at\", fill = TimestampFill.CreatedTimeStringFill.class)\n" +
                "    private String createdAt;\n\n" +
                "    // 更新时间：插入和更新时自动填充，格式 yyyy-MM-dd HH:mm:ss（本地时间）\n" +
                "    @Column(name = \"updated_at\", fill = TimestampFill.UpdatedTimeStringFill.class)\n" +
                "    private String updatedAt;\n\n" +
                "    // ==================== 静态查询方法 ====================\n\n" +
                "    public static " + className + " find(Long id) {\n" +
                "        return BaseModel.find(" + className + ".class, id);\n" +
                "    }\n\n" +
                "    public static List<" + className + "> all() {\n" +
                "        return BaseModel.all(" + className + ".class);\n" +
                "    }\n\n" +
                "    public static QueryBuilder<" + className + ", Long> query() {\n" +
                "        return BaseModel.query(" + className + ".class);\n" +
                "    }\n" +
                "}\n";
    }

    // ==================== Migration ====================

    /**
     * 生成 Migration 类。
     * <p>
     * 迁移文件生成到 Java 源码树中（{@code output-dir/基包/database/migration/}），
     * 与 Controller、Model 等保持一致的生成方式，确保能被编译器编译并由 CLASSPATH 模式加载。
     */
    public static String generateMigration(MakeCodeProperties properties, String name, boolean force) throws IOException {
        String datePrefix = LocalDate.now().format(DATE_FORMATTER);
        String pascalName = toPascalCase(name);
        String className = "Migration_" + datePrefix + "_" + pascalName;
        String packageName = properties.getBasePackage() + ".database.migration";
        String content = buildMigrationSource(packageName, className, name);
        return writeJavaFile(properties.getOutputDir(), packageName, className, content, force);
    }

    private static String buildMigrationSource(String packageName, String className, String description) {
        String tableName = toSnakeCase(description).replaceAll("^(create|add|drop|alter)_", "");
        return "package " + packageName + ";\n\n" +
                "import com.weacsoft.jaravel.vendor.migration.Migration;\n" +
                "import com.weacsoft.jaravel.vendor.migration.Schema;\n" +
                "import com.weacsoft.jaravel.vendor.migration.MigrationAnnotation;\n\n" +
                "/**\n" +
                " * 迁移：" + description + "。\n" +
                " */\n" +
                "@MigrationAnnotation\n" +
                "public class " + className + " implements Migration {\n\n" +
                "    @Override\n" +
                "    public void up(Schema schema) {\n" +
                "        // TODO: 在此编写正向迁移逻辑\n" +
                "        // schema.create(\"" + tableName + "\", table -> {\n" +
                "        //     table.id();\n" +
                "        //     table.timestamps();\n" +
                "        // });\n" +
                "    }\n\n" +
                "    @Override\n" +
                "    public void down(Schema schema) {\n" +
                "        // TODO: 在此编写回滚逻辑\n" +
                "        // schema.dropIfExists(\"" + tableName + "\");\n" +
                "    }\n" +
                "}\n";
    }

    // ==================== Command ====================

    /**
     * 生成 ArtisanCommand 类。
     */
    public static String generateCommand(MakeCodeProperties properties, String name, boolean force) throws IOException {
        String className = ensureSuffix(name, "Command");
        String packageName = properties.getBasePackage() + ".app.console.commands";
        String content = buildCommandSource(packageName, className);
        return writeJavaFile(properties.getOutputDir(), packageName, className, content, force);
    }

    private static String buildCommandSource(String packageName, String className) {
        String commandName = toSnakeCase(className).replaceAll("_command$", "");
        return "package " + packageName + ";\n\n" +
                "import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;\n" +
                "import org.springframework.stereotype.Component;\n\n" +
                "/**\n" +
                " * " + className + " 自定义 Artisan 命令。\n" +
                " * <p>\n" +
                " * 使用方式：{@code java -jar app.jar artisan " + commandName + " [args]}\n" +
                " */\n" +
                "@Component\n" +
                "public class " + className + " extends ArtisanCommand {\n\n" +
                "    @Override\n" +
                "    public String signature() {\n" +
                "        return \"" + commandName + " {--force}\";\n" +
                "    }\n\n" +
                "    @Override\n" +
                "    public String description() {\n" +
                "        return \"自定义命令: " + className + "\";\n" +
                "    }\n\n" +
                "    @Override\n" +
                "    public int handle() {\n" +
                "        info(\"" + className + " executed\");\n" +
                "        // TODO: 实现命令逻辑\n" +
                "        return 0;\n" +
                "    }\n" +
                "}\n";
    }

    // ==================== Event ====================

    /**
     * 生成 Event 类。
     */
    public static String generateEvent(MakeCodeProperties properties, String name, boolean force) throws IOException {
        String className = ensureSuffix(name, "Event");
        String packageName = properties.getBasePackage() + ".app.events";
        String content = buildEventSource(packageName, className);
        return writeJavaFile(properties.getOutputDir(), packageName, className, content, force);
    }

    private static String buildEventSource(String packageName, String className) {
        return "package " + packageName + ";\n\n" +
                "import com.weacsoft.jaravel.vendor.event.Event;\n\n" +
                "/**\n" +
                " * " + className + " 事件。\n" +
                " * <p>\n" +
                " * 触发事件：\n" +
                " * <pre>\n" +
                " * eventDispatcher.dispatch(new " + className + "(...));\n" +
                " * </pre>\n" +
                " */\n" +
                "public class " + className + " implements Event {\n\n" +
                "    // TODO: 添加事件数据字段\n\n" +
                "    public " + className + "() {\n" +
                "    }\n" +
                "}\n";
    }

    // ==================== Listener ====================

    /**
     * 生成 Listener 类。
     */
    public static String generateListener(MakeCodeProperties properties, String name, String eventName, boolean force) throws IOException {
        String className = ensureSuffix(name, "Listener");
        String packageName = properties.getBasePackage() + ".app.listeners";
        String eventType = eventName != null && !eventName.isEmpty()
                ? ensureSuffix(eventName, "Event")
                : "YourEvent";
        String eventImport = eventName != null && !eventName.isEmpty()
                ? "import " + properties.getBasePackage() + ".app.events." + eventType + ";\n"
                : "// import " + properties.getBasePackage() + ".app.events.YourEvent;\n";
        String content = buildListenerSource(packageName, className, eventType, eventImport);
        return writeJavaFile(properties.getOutputDir(), packageName, className, content, force);
    }

    private static String buildListenerSource(String packageName, String className, String eventType, String eventImport) {
        return "package " + packageName + ";\n\n" +
                "import com.weacsoft.jaravel.vendor.event.Listener;\n" +
                "import com.weacsoft.jaravel.vendor.event.ListensTo;\n" +
                "import org.springframework.stereotype.Component;\n" +
                eventImport +
                "\n" +
                "/**\n" +
                " * " + className + " 监听器，对齐 Laravel Listener。\n" +
                " * <p>\n" +
                " * 标注 {@code @Component} + {@code @ListensTo} 后由 SpringBoot 自动扫描注册，\n" +
                " * 监听 {@link " + eventType + "} 事件。\n" +
                " * <p>\n" +
                " * 如需异步队列执行，实现 {@code ShouldQueue} 接口：\n" +
                " * <pre>\n" +
                " * public class " + className + " implements Listener<" + eventType + ">, ShouldQueue {\n" +
                " *     &#64;Override public String queue() { return \"emails\"; }\n" +
                " * }\n" +
                " * </pre>\n" +
                " */\n" +
                "@Component\n" +
                "@ListensTo(" + eventType + ".class)\n" +
                "public class " + className + " implements Listener<" + eventType + "> {\n\n" +
                "    @Override\n" +
                "    public void handle(" + eventType + " event) {\n" +
                "        // TODO: 实现事件处理逻辑\n" +
                "    }\n" +
                "}\n";
    }

    // ==================== 工具方法 ====================

    /**
     * 写入 Java 源文件到指定包目录下。
     */
    private static String writeJavaFile(String outputDir, String packageName, String className,
                                         String content, boolean force) throws IOException {
        String packagePath = packageName.replace('.', '/');
        Path dir = Paths.get(outputDir, packagePath);
        Files.createDirectories(dir);

        Path file = dir.resolve(className + ".java");
        if (Files.exists(file) && !force) {
            throw new IllegalStateException("文件已存在，拒绝覆盖: " + file.toAbsolutePath()
                    + "（使用 --force 强制覆盖）");
        }

        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file.toAbsolutePath().toString();
    }

    /**
     * 确保类名以指定后缀结尾。
     */
    static String ensureSuffix(String name, String suffix) {
        String pascal = toPascalCase(name);
        if (pascal.endsWith(suffix)) {
            return pascal;
        }
        return pascal + suffix;
    }

    /**
     * 将任意字符串转换为 PascalCase。
     */
    static String toPascalCase(String input) {
        if (input == null || input.isEmpty()) {
            return "Generated";
        }
        String[] words = input.split("[^a-zA-Z0-9]+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                sb.append(word.substring(1));
            }
        }
        return sb.length() > 0 ? sb.toString() : "Generated";
    }

    /**
     * 将 PascalCase 转换为 snake_case。
     */
    static String toSnakeCase(String input) {
        if (input == null || input.isEmpty()) {
            return "generated";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('_');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }
}
