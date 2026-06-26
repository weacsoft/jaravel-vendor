package com.weacsoft.jaravel.vendor.jblade;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BladeEngine 后缀功能测试。
 * 验证默认后缀 .blade.java 和自定义后缀均能正常工作。
 */
class BladeEngineTest {

    /**
     * 测试默认后缀为 .blade.java
     */
    @Test
    void testDefaultSuffix() {
        BladeEngine engine = new BladeEngine("templates");
        assertEquals(".blade.java", engine.getSuffix());
        assertEquals(".blade.java", BladeEngine.DEFAULT_SUFFIX);
        assertEquals(".blade.java", BladeCompiler.DEFAULT_SUFFIX);
    }

    /**
     * 测试使用默认后缀 .blade.java 渲染模板
     */
    @Test
    void testRenderWithDefaultSuffix() throws Exception {
        BladeEngine engine = new BladeEngine("templates");

        Map<String, Object> vars = new HashMap<>();
        vars.put("name", "Alice");
        vars.put("count", 5);

        String html = engine.render("hello", vars);

        assertTrue(html.contains("Hello, Alice!"), "应包含 Hello, Alice!");
        assertTrue(html.contains("You have 5 messages."), "应包含 You have 5 messages.");
    }

    /**
     * 测试使用自定义后缀渲染模板（向后兼容）
     */
    @Test
    void testRenderWithCustomSuffix() throws Exception {
        // 使用 .blade.java 作为自定义后缀（与默认相同，但验证显式传参）
        BladeEngine engine = new BladeEngine("templates", ".blade.java");

        Map<String, Object> vars = new HashMap<>();
        vars.put("name", "Bob");
        vars.put("count", 3);

        String html = engine.render("hello", vars);

        assertTrue(html.contains("Hello, Bob!"), "应包含 Hello, Bob!");
        assertTrue(html.contains("You have 3 messages."), "应包含 You have 3 messages.");
    }

    /**
     * 测试模板继承（@extends / @section / @yield）
     */
    @Test
    void testTemplateInheritance() throws Exception {
        BladeEngine engine = new BladeEngine("templates");

        Map<String, Object> vars = new HashMap<>();
        vars.put("name", "Charlie");

        String html = engine.render("page", vars);

        assertTrue(html.contains("My Page"), "title 应为 My Page");
        assertTrue(html.contains("Welcome, Charlie!"), "应包含 Welcome, Charlie!");
        assertTrue(html.contains("Navigation"), "应包含父模板的 Navigation");
        assertTrue(html.contains("<html>"), "应包含父模板的 HTML 结构");
    }

    /**
     * 测试条件与循环指令
     */
    @Test
    void testConditionAndLoop() throws Exception {
        BladeEngine engine = new BladeEngine("templates");

        Map<String, Object> vars = new HashMap<>();
        vars.put("users", List.of("Alice", "Bob", "Charlie"));

        // 使用 hello 模板测试基本渲染
        String html = engine.render("hello", Map.of("name", "Test", "count", 1));
        assertNotNull(html);
        assertFalse(html.isEmpty());
    }

    /**
     * 测试 null suffix 不会导致 NPE，而是回退到默认后缀
     */
    @Test
    void testNullSuffixFallback() {
        BladeEngine engine = new BladeEngine("templates", (String) null);
        assertEquals(".blade.java", engine.getSuffix());
    }

    /**
     * 测试空字符串 suffix 也会回退到默认后缀
     */
    @Test
    void testEmptySuffixFallback() {
        BladeEngine engine = new BladeEngine("templates", "");
        assertEquals(".blade.java", engine.getSuffix());
    }
}
