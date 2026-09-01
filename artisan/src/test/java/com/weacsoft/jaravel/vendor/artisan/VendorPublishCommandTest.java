package com.weacsoft.jaravel.vendor.artisan;

import com.weacsoft.jaravel.vendor.artisan.make.MakeCodeProperties;
import com.weacsoft.jaravel.vendor.artisan.vendor.VendorPublishCommand;
import com.weacsoft.jaravel.vendor.core.publish.Publishable;
import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;
import com.weacsoft.jaravel.vendor.core.publish.PublishableMigration;
import com.weacsoft.jaravel.vendor.core.publish.PublishableRegistry;
import com.weacsoft.jaravel.vendor.core.publish.PublishableStatic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link VendorPublishCommand} 单元测试（统一处理配置类 + 静态资源 + 迁移文件）。
 * <p>
 * 测试覆盖：
 * <ul>
 *   <li>{@code --all} 同时发布配置类、静态资源与迁移文件</li>
 *   <li>{@code --tag=<模块>} 只发布该标签（含其配置与资源）</li>
 *   <li>{@code --tag=resources} 只发布静态资源；{@code --tag=config} 只发布配置类；
 *       {@code --tag=migrations} 只发布所有模块的建表迁移</li>
 *   <li>迁移发布产物的包名被重写为工程迁移包（与目标目录一致，直接可编译）</li>
 *   <li>{@code --force} 覆盖语义与默认跳过语义</li>
 *   <li>{@code --list} 只列出不写文件</li>
 *   <li>未知 tag 返回失败码</li>
 *   <li>无可发布项时优雅退出（可选依赖回退）</li>
 * </ul>
 */
class VendorPublishCommandTest {

    @TempDir
    Path tempDir;

    private MakeCodeProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MakeCodeProperties();
        properties.setBasePackage("com.example.test");
        properties.setOutputDir(tempDir.toString());
        properties.setResourcesDir(tempDir.resolve("resources").toString());
        PublishableRegistry.clearForTest();
    }

    /** 配置类应发布到 {@code <outputDir>/com/example/test/config/}。 */
    private Path configDir() {
        return tempDir.resolve("com/example/test/config");
    }

    /** 静态资源应发布到 {@code <resourcesDir>/static/}。 */
    private Path resourcesDir() {
        return tempDir.resolve("resources");
    }

    /** 迁移文件应发布到 {@code <outputDir>/com/example/test/database/migrations/}（MakeCodeProperties 约定）。 */
    private Path migrationDir() {
        return tempDir.resolve("com/example/test/database/migrations");
    }

    /**
     * 构造命令并注入解析后的选项。
     */
    private VendorPublishCommand command(String... optionKeys) {
        VendorPublishCommand cmd = new VendorPublishCommand(properties);
        Map<String, String> options = new LinkedHashMap<>();
        for (String key : optionKeys) {
            int eq = key.indexOf('=');
            if (eq > 0) {
                options.put(key.substring(0, eq), key.substring(eq + 1));
            } else {
                options.put(key, "true");
            }
        }
        try {
            Method setParsed = ArtisanCommand.class
                    .getDeclaredMethod("setParsed", Map.class, Map.class);
            setParsed.setAccessible(true);
            setParsed.invoke(cmd, new LinkedHashMap<String, String>(), options);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("注入命令选项失败", e);
        }
        return cmd;
    }

    /** 简单的可发布配置桩。 */
    private PublishableConfig stub(String tag, String className) {
        return new PublishableConfig() {
            @Override
            public String tag() {
                return tag;
            }

            @Override
            public String className() {
                return className;
            }

            @Override
            public String source(String basePackage) {
                return "package " + basePackage + ".config;\n\npublic class " + className + " {}\n";
            }
        };
    }

    /** 简单的可发布静态资源桩（用内存 ClassLoader 提供字节）。 */
    private PublishableStatic staticStub(String tag, String cp, String target, byte[] bytes) {
        return new PublishableStatic() {
            @Override
            public String tag() {
                return tag;
            }

            @Override
            public Map<String, String> resources() {
                return Collections.singletonMap(cp, target);
            }

            @Override
            public ClassLoader resourceClassLoader() {
                return new ClassLoader() {
                    @Override
                    public InputStream getResourceAsStream(String name) {
                        return name.equals(cp) ? new ByteArrayInputStream(bytes) : null;
                    }
                };
            }
        };
    }

    /** 简单的可发布迁移桩（用内存 ClassLoader 提供模块 jar 内置迁移源码）。 */
    private PublishableMigration migrationStub(String tag, String cp, String target, byte[] bytes) {
        return new PublishableMigration() {
            @Override
            public String tag() {
                return tag;
            }

            @Override
            public List<Map.Entry<String, String>> migrationFiles() {
                return List.of(Map.entry(cp, target));
            }

            @Override
            public String description() {
                return "测试迁移桩";
            }

            @Override
            public ClassLoader sourceClassLoader() {
                return new ClassLoader() {
                    @Override
                    public InputStream getResourceAsStream(String name) {
                        return name.equals(cp) ? new ByteArrayInputStream(bytes) : null;
                    }
                };
            }
        };
    }

    @Test
    void testPublishAll() throws IOException {
        PublishableRegistry.register(stub("cache", "CacheConfig"));
        PublishableRegistry.register(staticStub("captcha", "static/x.js", "static/x.js", "console.log(1)".getBytes(StandardCharsets.UTF_8)));

        int code = command("all").handle();

        assertEquals(0, code);
        assertTrue(Files.exists(configDir().resolve("CacheConfig.java")), "配置类应被发布");
        assertTrue(Files.exists(resourcesDir().resolve("static/x.js")), "静态资源应被发布");

        String content = Files.readString(configDir().resolve("CacheConfig.java"), StandardCharsets.UTF_8);
        assertTrue(content.startsWith("package com.example.test.config;"),
                "发布产物的包名应为业务工程基础包 + .config，实际: " + content);
    }

    @Test
    void testPublishByTag() {
        PublishableRegistry.register(stub("cache", "CacheConfig"));
        PublishableRegistry.register(stub("storage", "StorageConfig"));

        int code = command("tag=cache").handle();

        assertEquals(0, code);
        assertTrue(Files.exists(configDir().resolve("CacheConfig.java")));
        assertFalse(Files.exists(configDir().resolve("StorageConfig.java")),
                "未指定的 tag 不应被发布");
    }

    @Test
    void testPublishResourcesTag() throws IOException {
        PublishableRegistry.register(stub("cache", "CacheConfig"));
        PublishableRegistry.register(staticStub("captcha", "static/x.js", "static/x.js", "A".getBytes(StandardCharsets.UTF_8)));
        PublishableRegistry.register(staticStub("wire", "static/wire.js", "static/wire.js", "B".getBytes(StandardCharsets.UTF_8)));

        int code = command("tag=resources").handle();

        assertEquals(0, code);
        assertFalse(Files.exists(configDir().resolve("CacheConfig.java")), "--tag=resources 不应发布配置类");
        assertTrue(Files.exists(resourcesDir().resolve("static/x.js")), "captcha 静态资源应被发布");
        assertTrue(Files.exists(resourcesDir().resolve("static/wire.js")), "wire 静态资源应被发布");
    }

    @Test
    void testPublishConfigTag() throws IOException {
        PublishableRegistry.register(stub("cache", "CacheConfig"));
        PublishableRegistry.register(staticStub("captcha", "static/x.js", "static/x.js", "A".getBytes(StandardCharsets.UTF_8)));

        int code = command("tag=config").handle();

        assertEquals(0, code);
        assertTrue(Files.exists(configDir().resolve("CacheConfig.java")), "--tag=config 应发布配置类");
        assertFalse(Files.exists(resourcesDir().resolve("static/x.js")), "--tag=config 不应发布静态资源");
    }

    @Test
    void testPublishMigrationsTag() throws IOException {
        // 内置迁移源码带模块自己的包名（模拟模块 jar 内资源），发布后必须被重写为工程迁移包
        byte[] cacheMigration = ("package com.weacsoft.vendor.internal;\n"
                + "@MigrationAnnotation\npublic class Migration_20240101_CreateJaravelCacheTable {}\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] queueMigration = ("package com.weacsoft.vendor.internal;\n"
                + "@MigrationAnnotation\npublic class Migration_20240101_CreateQueueTables {}\n")
                .getBytes(StandardCharsets.UTF_8);
        PublishableRegistry.register(stub("cache", "CacheConfig"));
        PublishableRegistry.register(migrationStub("cache-database",
                "jaravel/migrations/Migration_20240101_CreateJaravelCacheTable.java",
                "Migration_20240101_CreateJaravelCacheTable.java", cacheMigration));
        PublishableRegistry.register(migrationStub("queue-database",
                "jaravel/migrations/Migration_20240101_CreateQueueTables.java",
                "Migration_20240101_CreateQueueTables.java", queueMigration));

        int code = command("tag=migrations").handle();

        assertEquals(0, code);
        Path cacheTarget = migrationDir().resolve("Migration_20240101_CreateJaravelCacheTable.java");
        Path queueTarget = migrationDir().resolve("Migration_20240101_CreateQueueTables.java");
        assertTrue(Files.exists(cacheTarget), "cache-database 内置迁移应被发布");
        assertTrue(Files.exists(queueTarget), "queue-database 内置迁移应被发布");
        assertFalse(Files.exists(configDir().resolve("CacheConfig.java")),
                "--tag=migrations 不应发布配置类");

        String content = Files.readString(cacheTarget, StandardCharsets.UTF_8);
        assertTrue(content.startsWith("package com.example.test.database.migrations;"),
                "迁移文件包名应重写为工程迁移包，实际: " + content);
        assertTrue(content.contains("Migration_20240101_CreateJaravelCacheTable"),
                "迁移类内容应保留");
    }

    @Test
    void testPublishMigrationByModuleTag() throws IOException {
        byte[] migrationBytes = ("package com.weacsoft.vendor.internal;\n"
                + "public class Migration_20240101_CreateStorageTables {}\n")
                .getBytes(StandardCharsets.UTF_8);
        PublishableRegistry.register(migrationStub("cache-database", "a.java", "Migration_A.java", migrationBytes));
        PublishableRegistry.register(migrationStub("storage-database", "b.java", "Migration_B.java", migrationBytes));

        int code = command("tag=storage-database").handle();

        assertEquals(0, code, "应按模块 tag 发布对应迁移");
        assertTrue(Files.exists(migrationDir().resolve("Migration_B.java")));
        assertFalse(Files.exists(migrationDir().resolve("Migration_A.java")),
                "非本 tag 的迁移不应被发布");
    }

    @Test
    void testMigrationSkipExistingAndForce() throws IOException {
        byte[] migrationBytes = ("package com.weacsoft.vendor.internal;\n"
                + "public class Migration_20240101_CreateJaravelCacheTable {}\n")
                .getBytes(StandardCharsets.UTF_8);
        PublishableRegistry.register(migrationStub("cache-database",
                "m.java", "Migration_20240101_CreateJaravelCacheTable.java", migrationBytes));
        Path target = migrationDir().resolve("Migration_20240101_CreateJaravelCacheTable.java");

        // 首次：发布成功
        assertEquals(0, command("tag=migrations").handle());
        assertTrue(Files.exists(target));
        String first = Files.readString(target, StandardCharsets.UTF_8);

        // 用户修改后重跑：默认跳过，不覆盖
        Files.writeString(target, "// 用户已修改", StandardCharsets.UTF_8);
        PublishableRegistry.clearForTest();
        PublishableRegistry.register(migrationStub("cache-database",
                "m.java", "Migration_20240101_CreateJaravelCacheTable.java", migrationBytes));
        assertEquals(0, command("tag=migrations").handle());
        assertEquals("// 用户已修改", Files.readString(target, StandardCharsets.UTF_8),
                "默认不应覆盖业务工程已存在的迁移文件");

        // --force：覆盖
        PublishableRegistry.clearForTest();
        PublishableRegistry.register(migrationStub("cache-database",
                "m.java", "Migration_20240101_CreateJaravelCacheTable.java", migrationBytes));
        assertEquals(0, command("tag=migrations", "force").handle());
        assertTrue(Files.readString(target, StandardCharsets.UTF_8).contains("Migration_20240101_CreateJaravelCacheTable"),
                "--force 应覆盖已存在的迁移文件");
    }

    @Test
    void testAllIncludesMigrations() throws IOException {
        byte[] migrationBytes = ("package com.weacsoft.vendor.internal;\n"
                + "public class Migration_20240101_CreateJaravelCacheTable {}\n")
                .getBytes(StandardCharsets.UTF_8);
        PublishableRegistry.register(stub("cache", "CacheConfig"));
        PublishableRegistry.register(migrationStub("cache-database", "m.java",
                "Migration_20240101_CreateJaravelCacheTable.java", migrationBytes));

        int code = command("all").handle();

        assertEquals(0, code);
        assertTrue(Files.exists(configDir().resolve("CacheConfig.java")));
        assertTrue(Files.exists(migrationDir().resolve("Migration_20240101_CreateJaravelCacheTable.java")),
                "--all 应同时发布迁移文件");
    }

    @Test
    void testUnknownTagFails() {
        PublishableRegistry.register(stub("cache", "CacheConfig"));
        int code = command("tag=nope").handle();
        assertEquals(1, code, "未知 tag 应返回失败码");
    }

    @Test
    void testSkipExistingWithoutForce() throws IOException {
        Files.createDirectories(configDir());
        Path target = configDir().resolve("CacheConfig.java");
        Files.writeString(target, "// 用户已修改的内容", StandardCharsets.UTF_8);

        PublishableRegistry.register(stub("cache", "CacheConfig"));
        int code = command("all").handle();

        assertEquals(0, code);
        assertEquals("// 用户已修改的内容", Files.readString(target, StandardCharsets.UTF_8),
                "默认不应覆盖用户已存在的文件");
    }

    @Test
    void testForceOverwrites() throws IOException {
        Files.createDirectories(configDir());
        Path target = configDir().resolve("CacheConfig.java");
        Files.writeString(target, "// 旧内容", StandardCharsets.UTF_8);

        PublishableRegistry.register(stub("cache", "CacheConfig"));
        int code = command("all", "force").handle();

        assertEquals(0, code);
        assertTrue(Files.readString(target, StandardCharsets.UTF_8).contains("class CacheConfig"),
                "--force 应覆盖已存在文件");
    }

    @Test
    void testListDoesNotWriteFiles() {
        PublishableRegistry.register(stub("cache", "CacheConfig"));
        int code = command("list").handle();

        assertEquals(0, code);
        assertFalse(Files.exists(configDir().resolve("CacheConfig.java")),
                "--list 只列出，不应写文件");
    }

    @Test
    void testNoOptionsDoesNotWriteFiles() {
        PublishableRegistry.register(stub("cache", "CacheConfig"));
        int code = command().handle();

        assertEquals(0, code);
        assertFalse(Files.exists(configDir().resolve("CacheConfig.java")),
                "未指定 --all/--tag 时不应误覆盖文件");
    }

    @Test
    void testEmptyPublishablesExitsGracefully() {
        assertEquals(0, command("all").handle());
    }
}
