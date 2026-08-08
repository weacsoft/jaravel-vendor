package com.weacsoft.jaravel.vendor.artisan;

import com.weacsoft.jaravel.vendor.artisan.make.MakeCodeProperties;
import com.weacsoft.jaravel.vendor.artisan.vendor.VendorPublishCommand;
import com.weacsoft.jaravel.vendor.core.publish.Publishable;
import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;
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
 * {@link VendorPublishCommand} 单元测试（统一处理配置类 + 静态资源）。
 * <p>
 * 测试覆盖：
 * <ul>
 *   <li>{@code --all} 同时发布配置类与静态资源</li>
 *   <li>{@code --tag=<模块>} 只发布该标签（含其配置与资源）</li>
 *   <li>{@code --tag=resources} 只发布静态资源；{@code --tag=config} 只发布配置类</li>
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
    }

    /** 配置类应发布到 {@code <outputDir>/com/example/test/config/}。 */
    private Path configDir() {
        return tempDir.resolve("com/example/test/config");
    }

    /** 静态资源应发布到 {@code <resourcesDir>/static/}。 */
    private Path resourcesDir() {
        return tempDir.resolve("resources");
    }

    /**
     * 构造命令并注入解析后的选项。
     */
    private VendorPublishCommand command(List<Publishable> items, String... optionKeys) {
        VendorPublishCommand cmd = new VendorPublishCommand(items, properties);
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

    @Test
    void testPublishAll() throws IOException {
        int code = command(List.of(
                stub("cache", "CacheConfig"),
                staticStub("captcha", "static/x.js", "static/x.js", "console.log(1)".getBytes(StandardCharsets.UTF_8))
        ), "all").handle();

        assertEquals(0, code);
        assertTrue(Files.exists(configDir().resolve("CacheConfig.java")), "配置类应被发布");
        assertTrue(Files.exists(resourcesDir().resolve("static/x.js")), "静态资源应被发布");

        String content = Files.readString(configDir().resolve("CacheConfig.java"), StandardCharsets.UTF_8);
        assertTrue(content.startsWith("package com.example.test.config;"),
                "发布产物的包名应为业务工程基础包 + .config，实际: " + content);
    }

    @Test
    void testPublishByTag() {
        int code = command(List.of(
                stub("cache", "CacheConfig"),
                stub("storage", "StorageConfig")
        ), "tag=cache").handle();

        assertEquals(0, code);
        assertTrue(Files.exists(configDir().resolve("CacheConfig.java")));
        assertFalse(Files.exists(configDir().resolve("StorageConfig.java")),
                "未指定的 tag 不应被发布");
    }

    @Test
    void testPublishResourcesTag() throws IOException {
        int code = command(List.of(
                stub("cache", "CacheConfig"),
                staticStub("captcha", "static/x.js", "static/x.js", "A".getBytes(StandardCharsets.UTF_8)),
                staticStub("wire", "static/wire.js", "static/wire.js", "B".getBytes(StandardCharsets.UTF_8))
        ), "tag=resources").handle();

        assertEquals(0, code);
        assertFalse(Files.exists(configDir().resolve("CacheConfig.java")), "--tag=resources 不应发布配置类");
        assertTrue(Files.exists(resourcesDir().resolve("static/x.js")), "captcha 静态资源应被发布");
        assertTrue(Files.exists(resourcesDir().resolve("static/wire.js")), "wire 静态资源应被发布");
    }

    @Test
    void testPublishConfigTag() throws IOException {
        int code = command(List.of(
                stub("cache", "CacheConfig"),
                staticStub("captcha", "static/x.js", "static/x.js", "A".getBytes(StandardCharsets.UTF_8))
        ), "tag=config").handle();

        assertEquals(0, code);
        assertTrue(Files.exists(configDir().resolve("CacheConfig.java")), "--tag=config 应发布配置类");
        assertFalse(Files.exists(resourcesDir().resolve("static/x.js")), "--tag=config 不应发布静态资源");
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
        assertEquals(0, command(List.of(), "all").handle());
    }
}
