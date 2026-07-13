package com.weacsoft.jaravel.vendor.jblade;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 预编译模式集成测试。
 * 验证预编译 -> 加载 -> 渲染的完整流程。
 */
class PrecompiledIntegrationTest {

    @TempDir
    Path tempDir;

    /**
     * 测试 CLASSES 模式：预编译到目录 -> 从目录加载 -> 渲染
     */
    @Test
    void testPrecompiledClassesMode() throws Exception {
        String templatesPath = "src/test/resources/templates";
        String outputDir = tempDir.resolve("precompiled_classes").toString();

        // 1. 预编译
        BladePrecompiler precompiler = new BladePrecompiler(templatesPath, ".blade.java");
        int count = precompiler.compileAllToClasses(outputDir);
        assertTrue(count > 0, "应至少编译一个模板");

        // 2. 从预编译目录加载
        BladeEngine engine = BladeEngine.fromPrecompiledClasses(outputDir);
        assertTrue(engine.isPrecompiledMode(), "应为预编译模式");

        // 3. 渲染 hello 模板
        Map<String, Object> vars = new HashMap<>();
        vars.put("name", "Precompiled");
        vars.put("count", 42);
        String html = engine.render("hello", vars);
        assertTrue(html.contains("Hello, Precompiled!"), "应包含 Hello, Precompiled!");
        assertTrue(html.contains("You have 42 messages."), "应包含 You have 42 messages.");
    }

    /**
     * 测试 PACKAGED 模式：预编译到 zip -> 从 zip 加载 -> 渲染
     */
    @Test
    void testPrecompiledPackageMode() throws Exception {
        String templatesPath = "src/test/resources/templates";
        String outputDir = tempDir.resolve("precompiled_pkg").toString();

        // 1. 预编译为 zip
        BladePrecompiler precompiler = new BladePrecompiler(templatesPath, ".blade.java");
        int count = precompiler.compileAllToZip(outputDir, "test_templates.jblade.zip");
        assertTrue(count > 0, "应至少编译一个模板");

        // 2. 从 zip 加载
        String zipPath = outputDir + "/test_templates.jblade.zip";
        BladeEngine engine = BladeEngine.fromPrecompiledPackage(zipPath);
        assertTrue(engine.isPrecompiledMode(), "应为预编译模式");

        // 3. 渲染 hello 模板
        Map<String, Object> vars = new HashMap<>();
        vars.put("name", "ZipMode");
        vars.put("count", 7);
        String html = engine.render("hello", vars);
        assertTrue(html.contains("Hello, ZipMode!"), "应包含 Hello, ZipMode!");
        assertTrue(html.contains("You have 7 messages."), "应包含 You have 7 messages.");
    }

    /**
     * 测试预编译模式下模板继承仍正常工作
     */
    @Test
    void testPrecompiledInheritance() throws Exception {
        String templatesPath = "src/test/resources/templates";
        String outputDir = tempDir.resolve("precompiled_inherit").toString();

        // 1. 预编译
        BladePrecompiler precompiler = new BladePrecompiler(templatesPath, ".blade.java");
        precompiler.compileAllToClasses(outputDir);

        // 2. 加载并渲染 page 模板（@extends layout）
        BladeEngine engine = BladeEngine.fromPrecompiledClasses(outputDir);
        Map<String, Object> vars = new HashMap<>();
        vars.put("name", "InheritTest");
        String html = engine.render("page", vars);

        assertTrue(html.contains("My Page"), "title 应为 My Page");
        assertTrue(html.contains("Welcome, InheritTest!"), "应包含 Welcome, InheritTest!");
        assertTrue(html.contains("Navigation"), "应包含父模板的 Navigation");
        assertTrue(html.contains("<html>"), "应包含父模板的 HTML 结构");
    }

    /**
     * 测试预编译产物中模板映射包含所有模板
     */
    @Test
    void testPrecompiledTemplateMapping() throws Exception {
        String templatesPath = "src/test/resources/templates";
        String outputDir = tempDir.resolve("precompiled_mapping").toString();

        BladePrecompiler precompiler = new BladePrecompiler(templatesPath, ".blade.java");
        precompiler.compileAllToClasses(outputDir);

        BladeEngine engine = BladeEngine.fromPrecompiledClasses(outputDir);
        Map<String, String> mapping = engine.getPrecompiledTemplateMapping();
        assertNotNull(mapping, "模板映射不应为 null");
        assertTrue(mapping.containsKey("hello"), "应包含 hello 模板");
        assertTrue(mapping.containsKey("page"), "应包含 page 模板");
        assertTrue(mapping.containsKey("layout"), "应包含 layout 模板");
    }
}
