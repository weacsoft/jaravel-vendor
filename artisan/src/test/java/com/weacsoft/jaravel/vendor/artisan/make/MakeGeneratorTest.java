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
        assertTrue(content.contains("package com.example.test.controller;"), "应包含 package 声明");
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
        assertTrue(content.contains("package com.example.test.middleware;"), "应包含 package 声明");
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
        assertTrue(content.contains("package com.example.test.model;"), "应包含 package 声明");
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
        assertTrue(content.contains("package com.example.test.migration;"), "应包含 package 声明");
        assertTrue(content.contains("implements Migration"), "应 implements Migration");
    }

    @Test
    void testGenerateCommand() throws IOException {
        String path = MakeGenerator.generateCommand(properties, "SyncData", false);

        Path file = Paths.get(path);
        assertTrue(Files.exists(file), "Command 文件应存在");

        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(content.contains("package com.example.test.command;"), "应包含 package 声明");
        assertTrue(content.contains("public class SyncDataCommand"), "应包含 class 声明");
        assertTrue(content.contains("extends ArtisanCommand"), "应 extends ArtisanCommand");
    }

    @Test
    void testGenerateEvent() throws IOException {
        String path = MakeGenerator.generateEvent(properties, "UserRegistered", false);

        Path file = Paths.get(path);
        assertTrue(Files.exists(file), "Event 文件应存在");

        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(content.contains("package com.example.test.event;"), "应包含 package 声明");
        assertTrue(content.contains("public class UserRegisteredEvent"), "应包含 class 声明");
        assertTrue(content.contains("implements Event"), "应 implements Event");
    }

    @Test
    void testGenerateListener() throws IOException {
        String path = MakeGenerator.generateListener(properties, "SendWelcomeEmail", "UserRegisteredEvent", false);

        Path file = Paths.get(path);
        assertTrue(Files.exists(file), "Listener 文件应存在");

        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(content.contains("package com.example.test.listener;"), "应包含 package 声明");
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
}
