package com.weacsoft.jaravel.vendor.artisan.make;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MakeGenerator} 单元测试。
 * <p>
 * 测试覆盖：
 * <ul>
 *   <li>工具方法：{@code ensureSuffix} / {@code toPascalCase} / {@code toSnakeCase}</li>
 *   <li>7 个生成方法：Controller / Middleware / Model / Migration / Command / Event / Listener</li>
 *   <li>{@code --force} 覆盖逻辑</li>
 *   <li>一键生成全部</li>
 * </ul>
 * <p>
 * 使用 JUnit 5 {@code @TempDir} 创建临时输出目录，测试类与 {@link MakeGenerator} 同包，
 * 以访问包级可见的静态工具方法。
 */
class MakeGeneratorTest {

    @TempDir
    Path tempDir;

    private MakeCodeProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MakeCodeProperties();
        properties.setBasePackage("com.example.test");
        properties.setOutputDir(tempDir.toString());
        properties.setMigrationDir(tempDir.resolve("database/migrations").toString());
    }

    // ==================== 工具方法测试 ====================

    @Test
    void testEnsureSuffix() {
        // "User" + "Controller" = "UserController"
        assertEquals("UserController", MakeGenerator.ensureSuffix("User", "Controller"));
        // "UserController" + "Controller" = "UserController"（已有后缀，不重复添加）
        assertEquals("UserController", MakeGenerator.ensureSuffix("UserController", "Controller"));
        // snake_case 输入应先转为 PascalCase 再补后缀
        assertEquals("UserProfileController", MakeGenerator.ensureSuffix("user_profile", "Controller"));
    }

    @Test
    void testToPascalCase() {
        assertEquals("UserProfile", MakeGenerator.toPascalCase("user_profile"));
        assertEquals("User", MakeGenerator.toPascalCase("user"));
        assertEquals("UserController", MakeGenerator.toPascalCase("user_controller"));
    }

    @Test
    void testToSnakeCase() {
        assertEquals("user_controller", MakeGenerator.toSnakeCase("UserController"));
        assertEquals("user", MakeGenerator.toSnakeCase("User"));
    }

    // ==================== 生成方法测试 ====================

    @Test
    void testGenerateController() throws IOException {
        String path = MakeGenerator.generateController(properties, "User", false);

        // 验证文件存在
        Path file = Paths.get(path);
        assertTrue(Files.exists(file), "Controller 文件应存在");

        // 验证内容包含 package / class / implements / @Controller
        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(content.contains("package com.example.test.http.controllers;"), "应包含 package 声明");
        assertTrue(content.contains("public class UserController"), "应包含 class 声明");
        assertTrue(content.contains("implements Controllers"), "应 implements Controllers");
        assertTrue(content.contains("@Controller"), "应包含 @Controller 注解");
        assertTrue(content.contains("public Response index(Request request)"), "应包含 index 方法");
    }

    @Test
    void testGenerateMiddleware() throws IOException {
        String path = MakeGenerator.generateMiddleware(properties, "Auth", false);

        Path file = Paths.get(path);
        assertTrue(Files.exists(file), "Middleware 文件应存在");

        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(content.contains("package com.example.test.http.middleware;"), "应包含 package 声明");
        assertTrue(content.contains("public class AuthMiddleware"), "应包含 class 声明");
        assertTrue(content.contains("implements Middleware"), "应 implements Middleware");
        assertTrue(content.contains("@MiddlewareAlias"), "应包含 @MiddlewareAlias 注解");
        assertTrue(content.contains("String... params"), "handle 方法应包含 String... params 参数");
    }

    @Test
    void testGenerateModel() throws IOException {
        String path = MakeGenerator.generateModel(properties, "User", false);

        Path file = Paths.get(path);
        assertTrue(Files.exists(file), "Model 文件应存在");

        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(content.contains("package com.example.test.models;"), "应包含 package 声明");
        assertTrue(content.contains("public class User"), "应包含 class 声明");
        assertTrue(content.contains("extends BaseModel<User, Long>"), "应继承 BaseModel");
        assertTrue(content.contains("@Repository"), "应包含 @Repository");
        assertTrue(content.contains("@Table(name = \"users\")"), "应包含 @Table");
        assertTrue(content.contains("@Primary"), "应包含 @Primary");
        assertTrue(content.contains("@Column(name = \"id\")"), "应包含 @Column");
        assertTrue(content.contains("public static User find(Long id)"), "应包含静态 find 方法");
        assertTrue(content.contains("public static List<User> all()"), "应包含静态 all 方法");
    }

    @Test
    void testGenerateMigration() throws IOException {
        String path = MakeGenerator.generateMigration(properties, "create_users_table", false);

        Path file = Paths.get(path);
        assertTrue(Files.exists(file), "Migration 文件应存在");

        // 验证文件名包含日期前缀 Migration_YYYY_MM_DD_
        String fileName = file.getFileName().toString();
        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd"));
        String expectedPrefix = "Migration_" + datePrefix + "_";
        assertTrue(fileName.startsWith(expectedPrefix),
                "Migration 文件名应以日期前缀 '" + expectedPrefix + "' 开头，实际: " + fileName);

        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(content.contains("package com.example.test.database.migrations;"), "应包含 package 声明");
        assertTrue(content.contains("implements Migration"), "应 implements Migration");
    }

    @Test
    void testGenerateCommand() throws IOException {
        String path = MakeGenerator.generateCommand(properties, "SyncData", false);

        Path file = Paths.get(path);
        assertTrue(Files.exists(file), "Command 文件应存在");

        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(content.contains("package com.example.test.console.commands;"), "应包含 package 声明");
        assertTrue(content.contains("public class SyncDataCommand"), "应包含 class 声明");
        assertTrue(content.contains("extends ArtisanCommand"), "应 extends ArtisanCommand");
    }

    @Test
    void testGenerateEvent() throws IOException {
        String path = MakeGenerator.generateEvent(properties, "UserRegistered", false);

        Path file = Paths.get(path);
        assertTrue(Files.exists(file), "Event 文件应存在");

        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(content.contains("package com.example.test.events;"), "应包含 package 声明");
        assertTrue(content.contains("public class UserRegisteredEvent"), "应包含 class 声明");
        assertTrue(content.contains("implements Event"), "应 implements Event");
    }

    @Test
    void testGenerateListener() throws IOException {
        String path = MakeGenerator.generateListener(properties, "SendWelcomeEmail", "UserRegisteredEvent", false);

        Path file = Paths.get(path);
        assertTrue(Files.exists(file), "Listener 文件应存在");

        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(content.contains("package com.example.test.listeners;"), "应包含 package 声明");
        assertTrue(content.contains("public class SendWelcomeEmailListener"), "应包含 class 声明");
        assertTrue(content.contains("implements Listener<UserRegisteredEvent>"), "应 implements Listener<UserRegisteredEvent>");
        assertTrue(content.contains("@Component"), "应包含 @Component 注解");
        assertTrue(content.contains("@ListensTo(UserRegisteredEvent.class)"), "应包含 @ListensTo 注解");
    }

    // ==================== force 覆盖测试 ====================

    @Test
    void testForceOverwrite() throws IOException {
        // 第一次生成成功
        String path = MakeGenerator.generateController(properties, "User", false);
        assertTrue(Files.exists(Paths.get(path)), "首次生成后文件应存在");

        // 第二次生成（force=false）应抛出 IllegalStateException
        assertThrows(IllegalStateException.class, () -> MakeGenerator.generateController(properties, "User", false),
                "文件已存在且 force=false 时应抛出 IllegalStateException");

        // 第三次生成（force=true）应成功覆盖
        assertDoesNotThrow(() -> MakeGenerator.generateController(properties, "User", true),
                "force=true 时应成功覆盖已存在文件");
    }

    // ==================== 一键生成全部测试 ====================

    @Test
    void testGenerateAll() throws IOException {
        // 调用所有 7 个生成方法
        String controllerPath = MakeGenerator.generateController(properties, "User", false);
        String middlewarePath = MakeGenerator.generateMiddleware(properties, "User", false);
        String modelPath = MakeGenerator.generateModel(properties, "User", false);
        String migrationPath = MakeGenerator.generateMigration(properties, "User", false);
        String commandPath = MakeGenerator.generateCommand(properties, "User", false);
        String eventPath = MakeGenerator.generateEvent(properties, "User", false);
        String listenerPath = MakeGenerator.generateListener(properties, "User", "UserEvent", false);

        // 验证所有文件均存在
        assertTrue(Files.exists(Paths.get(controllerPath)), "Controller 文件应存在");
        assertTrue(Files.exists(Paths.get(middlewarePath)), "Middleware 文件应存在");
        assertTrue(Files.exists(Paths.get(modelPath)), "Model 文件应存在");
        assertTrue(Files.exists(Paths.get(migrationPath)), "Migration 文件应存在");
        assertTrue(Files.exists(Paths.get(commandPath)), "Command 文件应存在");
        assertTrue(Files.exists(Paths.get(eventPath)), "Event 文件应存在");
        assertTrue(Files.exists(Paths.get(listenerPath)), "Listener 文件应存在");
    }

    // ==================== 导入正确性验证测试 ====================
    // 以下测试验证生成的代码包含所有必要的 import 语句，确保生成的代码可以直接编译。

    @Test
    void testControllerImportsComplete() throws IOException {
        String path = MakeGenerator.generateController(properties, "User", true);
        String content = Files.readString(Paths.get(path), StandardCharsets.UTF_8);

        // 必须包含的导入
        assertTrue(content.contains("import com.weacsoft.jaravel.vendor.http.controller.Controllers;"),
                "Controller 应导入 Controllers 接口");
        assertTrue(content.contains("import com.weacsoft.jaravel.vendor.http.controller.request.Request;"),
                "Controller 应导入 Request");
        assertTrue(content.contains("import com.weacsoft.jaravel.vendor.http.controller.response.Response;"),
                "Controller 应导入 Response");
        assertTrue(content.contains("import com.weacsoft.jaravel.vendor.http.controller.response.ResponseBuilder;"),
                "Controller 应导入 ResponseBuilder");
        assertTrue(content.contains("import org.springframework.stereotype.Controller;"),
                "Controller 应导入 @Controller 注解");
        assertTrue(content.contains("import java.util.HashMap;"), "Controller 应导入 HashMap");
        assertTrue(content.contains("import java.util.Map;"), "Controller 应导入 Map");

        // 必须包含的注解和接口实现
        assertTrue(content.contains("@Controller"), "Controller 应标注 @Controller");
        assertTrue(content.contains("implements Controllers"), "Controller 应实现 Controllers 接口");
    }

    @Test
    void testMiddlewareImportsComplete() throws IOException {
        String path = MakeGenerator.generateMiddleware(properties, "Auth", true);
        String content = Files.readString(Paths.get(path), StandardCharsets.UTF_8);

        // 必须包含的导入
        assertTrue(content.contains("import com.weacsoft.jaravel.vendor.http.middleware.Middleware;"),
                "Middleware 应导入 Middleware 接口");
        assertTrue(content.contains("import com.weacsoft.jaravel.vendor.http.middleware.Middleware.NextFunction;"),
                "Middleware 应导入 NextFunction（嵌套类型，不导入会导致编译错误）");
        assertTrue(content.contains("import com.weacsoft.jaravel.vendor.http.controller.request.Request;"),
                "Middleware 应导入 Request");
        assertTrue(content.contains("import com.weacsoft.jaravel.vendor.http.controller.response.Response;"),
                "Middleware 应导入 Response");
        assertTrue(content.contains("import com.weacsoft.jaravel.vendor.springboot.annotation.MiddlewareAlias;"),
                "Middleware 应导入 @MiddlewareAlias 注解");

        // 必须包含的注解和接口实现
        assertTrue(content.contains("@MiddlewareAlias"), "Middleware 应标注 @MiddlewareAlias");
        assertTrue(content.contains("implements Middleware"), "Middleware 应实现 Middleware 接口");

        // handle 方法签名必须包含 NextFunction 参数
        assertTrue(content.contains("NextFunction next"), "handle 方法应包含 NextFunction next 参数");
    }

    @Test
    void testModelImportsComplete() throws IOException {
        String path = MakeGenerator.generateModel(properties, "Setting", true);
        String content = Files.readString(Paths.get(path), StandardCharsets.UTF_8);

        // 必须包含的导入
        assertTrue(content.contains("import com.weacsoft.jaravel.vendor.database.BaseModel;"),
                "Model 应导入 BaseModel");
        assertTrue(content.contains("import gaarason.database.annotation.Column;"),
                "Model 应导入 @Column 注解");
        assertTrue(content.contains("import gaarason.database.annotation.Primary;"),
                "Model 应导入 @Primary 注解");
        assertTrue(content.contains("import gaarason.database.annotation.Table;"),
                "Model 应导入 @Table 注解");
        assertTrue(content.contains("import gaarason.database.query.QueryBuilder;"),
                "Model 应导入 QueryBuilder");
        assertTrue(content.contains("import lombok.Data;"), "Model 应导入 @Data");
        assertTrue(content.contains("import lombok.EqualsAndHashCode;"),
                "Model 应导入 @EqualsAndHashCode");
        assertTrue(content.contains("import org.springframework.stereotype.Repository;"),
                "Model 应导入 @Repository");
        assertTrue(content.contains("import java.util.List;"), "Model 应导入 List");

        // 不应包含不存在的 gaarason.database.record.Record 导入
        assertFalse(content.contains("import gaarason.database.record.Record;"),
                "Model 不应导入 gaarason.database.record.Record（包不存在，会导致编译错误）");

        // 必须包含的注解和继承
        assertTrue(content.contains("@Repository"), "Model 应标注 @Repository");
        assertTrue(content.contains("@Table(name = \"settings\")"), "Model 应标注 @Table(name = \"settings\")");
        assertTrue(content.contains("@Primary"), "Model 应标注 @Primary");
        assertTrue(content.contains("@Column(name = \"id\")"), "Model 应标注 @Column(name = \"id\")");
        assertTrue(content.contains("extends BaseModel<Setting, Long>"), "Model 应继承 BaseModel<Setting, Long>");

        // 类名不应包含 Model 后缀
        assertTrue(content.contains("public class Setting extends"),
                "Model 类名应为 Setting，不应包含 Model 后缀");
        assertFalse(content.contains("public class SettingModel"),
                "Model 类名不应为 SettingModel");
    }

    @Test
    void testModelSnakeCaseInput() throws IOException {
        // snake_case 输入应正确转为 PascalCase，不加 Model 后缀
        String path = MakeGenerator.generateModel(properties, "user_profile", true);
        String content = Files.readString(Paths.get(path), StandardCharsets.UTF_8);

        assertTrue(content.contains("public class UserProfile extends"),
                "snake_case 输入应转为 PascalCase 类名 UserProfile");
        assertTrue(content.contains("@Table(name = \"user_profiles\")"),
                "表名应为 user_profiles");
    }

    @Test
    void testMigrationImportsComplete() throws IOException {
        String path = MakeGenerator.generateMigration(properties, "create_settings_table", true);
        String content = Files.readString(Paths.get(path), StandardCharsets.UTF_8);

        // 必须包含的导入
        assertTrue(content.contains("import com.weacsoft.jaravel.vendor.migration.Migration;"),
                "Migration 应导入 Migration 接口");
        assertTrue(content.contains("import com.weacsoft.jaravel.vendor.migration.Schema;"),
                "Migration 应导入 Schema");
        assertTrue(content.contains("import com.weacsoft.jaravel.vendor.migration.MigrationAnnotation;"),
                "Migration 应导入 @MigrationAnnotation 注解");

        // 必须包含的注解和接口实现
        assertTrue(content.contains("@MigrationAnnotation"), "Migration 应标注 @MigrationAnnotation");
        assertTrue(content.contains("implements Migration"), "Migration 应实现 Migration 接口");

        // 必须包含 up/down 方法
        assertTrue(content.contains("public void up(Schema schema)"), "Migration 应包含 up 方法");
        assertTrue(content.contains("public void down(Schema schema)"), "Migration 应包含 down 方法");
    }

    @Test
    void testCommandImportsComplete() throws IOException {
        String path = MakeGenerator.generateCommand(properties, "SyncData", true);
        String content = Files.readString(Paths.get(path), StandardCharsets.UTF_8);

        // 必须包含的导入
        assertTrue(content.contains("import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;"),
                "Command 应导入 ArtisanCommand");
        assertTrue(content.contains("import org.springframework.stereotype.Component;"),
                "Command 应导入 @Component 注解");

        // 必须包含的注解和继承
        assertTrue(content.contains("@Component"), "Command 应标注 @Component");
        assertTrue(content.contains("extends ArtisanCommand"), "Command 应继承 ArtisanCommand");

        // 必须包含 signature/description/handle 方法
        assertTrue(content.contains("public String signature()"), "Command 应包含 signature 方法");
        assertTrue(content.contains("public String description()"), "Command 应包含 description 方法");
        assertTrue(content.contains("public int handle()"), "Command 应包含 handle 方法");

        // 注释中的使用方式应为 artisan（不是 --artisan）
        assertFalse(content.contains("--artisan"),
                "Command 注释不应包含 --artisan，正确用法是 artisan");
        assertTrue(content.contains("artisan "),
                "Command 注释应包含 artisan 用法说明");
    }

    @Test
    void testEventImportsComplete() throws IOException {
        String path = MakeGenerator.generateEvent(properties, "UserRegistered", true);
        String content = Files.readString(Paths.get(path), StandardCharsets.UTF_8);

        // 必须包含的导入
        assertTrue(content.contains("import com.weacsoft.jaravel.vendor.event.Event;"),
                "Event 应导入 Event 接口");

        // 必须实现 Event 接口
        assertTrue(content.contains("implements Event"), "Event 应实现 Event 接口");
    }

    @Test
    void testListenerImportsComplete() throws IOException {
        String path = MakeGenerator.generateListener(properties, "SendWelcomeEmail", "UserRegisteredEvent", true);
        String content = Files.readString(Paths.get(path), StandardCharsets.UTF_8);

        // 必须包含的导入
        assertTrue(content.contains("import com.weacsoft.jaravel.vendor.event.Listener;"),
                "Listener 应导入 Listener 接口");
        assertTrue(content.contains("import com.weacsoft.jaravel.vendor.event.ListensTo;"),
                "Listener 应导入 @ListensTo 注解");
        assertTrue(content.contains("import org.springframework.stereotype.Component;"),
                "Listener 应导入 @Component 注解");
        assertTrue(content.contains("import com.example.test.events.UserRegisteredEvent;"),
                "Listener 应导入事件类");

        // 必须包含的注解和接口实现
        assertTrue(content.contains("@Component"), "Listener 应标注 @Component");
        assertTrue(content.contains("@ListensTo(UserRegisteredEvent.class)"),
                "Listener 应标注 @ListensTo");
        assertTrue(content.contains("implements Listener<UserRegisteredEvent>"),
                "Listener 应实现 Listener<UserRegisteredEvent>");

        // 必须包含 handle 方法
        assertTrue(content.contains("public void handle(UserRegisteredEvent event)"),
                "Listener 应包含 handle 方法");
    }

    @Test
    void testListenerWithoutEvent() throws IOException {
        // 不指定事件类型时应使用占位类型 YourEvent
        String path = MakeGenerator.generateListener(properties, "GenericListener", null, true);
        String content = Files.readString(Paths.get(path), StandardCharsets.UTF_8);

        assertTrue(content.contains("implements Listener<YourEvent>"),
                "未指定事件时应使用 YourEvent 占位类型");
        assertTrue(content.contains("@ListensTo(YourEvent.class)"),
                "未指定事件时应标注 @ListensTo(YourEvent.class)");
    }
}
