package com.weacsoft.jaravel.vendor.artisan;

import com.weacsoft.jaravel.vendor.artisan.make.MakeCodeProperties;
import com.weacsoft.jaravel.vendor.artisan.vendor.VendorPublishCommand;
import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link VendorPublishCommand} 单元测试。
 * <p>
 * 测试覆盖：
 * <ul>
 *   <li>{@code --all} / {@code --tag} 发布到 {@code <基础包>/config/} 目录</li>
 *   <li>{@code --force} 覆盖语义与默认跳过语义</li>
 *   <li>{@code --list} 只列出不写文件</li>
 *   <li>未知 tag 返回失败码</li>
 *   <li>无可发布项时优雅退出（可选依赖回退）</li>
 * </ul>
 * <p>
 * 测试类与 {@link ArtisanCommand} 同包，以便调用包级可见的 {@code setParsed}。
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
    }

    /** 配置类应发布到 {@code <outputDir>/com/example/test/config/}。 */
    private Path configDir() {
        return tempDir.resolve("com/example/test/config");
    }

    /**
     * 构造命令并注入解析后的选项。
     * <p>
     * {@code setParsed} 是 {@code ArtisanCommand} 的包级方法，而被测命令位于
     * {@code .vendor} 子包，无法直接调用，故通过反射注入（等价于
     * {@code ArtisanApplication} 在调度前所做的事）。
     */
    private VendorPublishCommand command(List<PublishableConfig> configs, String... optionKeys) {
        VendorPublishCommand cmd = new VendorPublishCommand(configs, properties);
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

    @Test
    void testPublishAll() throws IOException {
        int code = command(List.of(stub("cache", "CacheConfig"), stub("storage", "StorageConfig")),
                "all").handle();

        assertEquals(0, code);
        Path cache = configDir().resolve("CacheConfig.java");
        assertTrue(Files.exists(cache), "CacheConfig.java 应被发布");
        assertTrue(Files.exists(configDir().resolve("StorageConfig.java")), "StorageConfig.java 应被发布");

        // 发布内容应使用业务工程的基础包名
        String content = Files.readString(cache, StandardCharsets.UTF_8);
        assertTrue(content.startsWith("package com.example.test.config;"),
                "发布产物的包名应为业务工程基础包 + .config，实际: " + content);
    }

    @Test
    void testPublishByTag() {
        int code = command(List.of(stub("cache", "CacheConfig"), stub("storage", "StorageConfig")),
                "tag=cache").handle();

        assertEquals(0, code);
        assertTrue(Files.exists(configDir().resolve("CacheConfig.java")));
        assertFalse(Files.exists(configDir().resolve("StorageConfig.java")),
                "未指定的 tag 不应被发布");
    }

    @Test
    void testUnknownTagFails() {
        int code = command(List.of(stub("cache", "CacheConfig")), "tag=nope").handle();
        assertEquals(1, code, "未知 tag 应返回失败码");
    }

    @Test
    void testSkipExistingWithoutForce() throws IOException {
        Files.createDirectories(configDir());
        Path target = configDir().resolve("CacheConfig.java");
        Files.writeString(target, "// 用户已修改的内容", StandardCharsets.UTF_8);

        int code = command(List.of(stub("cache", "CacheConfig")), "all").handle();

        assertEquals(0, code);
        assertEquals("// 用户已修改的内容", Files.readString(target, StandardCharsets.UTF_8),
                "默认不应覆盖用户已存在的文件");
    }

    @Test
    void testForceOverwrites() throws IOException {
        Files.createDirectories(configDir());
        Path target = configDir().resolve("CacheConfig.java");
        Files.writeString(target, "// 旧内容", StandardCharsets.UTF_8);

        int code = command(List.of(stub("cache", "CacheConfig")), "all", "force").handle();

        assertEquals(0, code);
        assertTrue(Files.readString(target, StandardCharsets.UTF_8).contains("class CacheConfig"),
                "--force 应覆盖已存在文件");
    }

    @Test
    void testListDoesNotWriteFiles() {
        int code = command(List.of(stub("cache", "CacheConfig")), "list").handle();

        assertEquals(0, code);
        assertFalse(Files.exists(configDir().resolve("CacheConfig.java")),
                "--list 只列出，不应写文件");
    }

    @Test
    void testNoOptionsDoesNotWriteFiles() {
        int code = command(List.of(stub("cache", "CacheConfig"))).handle();

        assertEquals(0, code);
        assertFalse(Files.exists(configDir().resolve("CacheConfig.java")),
                "未指定 --all/--tag 时不应误覆盖文件");
    }

    @Test
    void testEmptyPublishablesExitsGracefully() {
        // 模拟「未引入任何声明可发布配置的模块」，应优雅退出而非报错
        assertEquals(0, command(List.of(), "all").handle());
    }
}
