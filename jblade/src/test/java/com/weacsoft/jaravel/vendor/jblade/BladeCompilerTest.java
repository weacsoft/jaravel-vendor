package com.weacsoft.jaravel.vendor.jblade;

import com.weacsoft.jaravel.vendor.utils.memory.MemoryClassLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.io.Writer;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BladeCompiler 模板编译测试。
 * 补充 BladeEngineTest 未覆盖的编译器层面测试：
 * 后缀处理、类名生成、编译产物验证、@extends/@yield/@section/@if/@foreach 解析。
 */
class BladeCompilerTest {

    private MemoryClassLoader classLoader;

    @BeforeEach
    void setUp() {
        classLoader = new MemoryClassLoader();
    }

    // ===== 后缀处理测试 =====

    @Test
    void testGetSuffixDefault() {
        BladeCompiler compiler = new BladeCompiler("templates", classLoader);
        assertEquals(".blade.java", compiler.getSuffix(), "默认后缀应为 .blade.java");
    }

    @Test
    void testGetSuffixCustom() {
        BladeCompiler compiler = new BladeCompiler("templates", classLoader, ".tpl");
        assertEquals(".tpl", compiler.getSuffix(), "自定义后缀应正确返回");
    }

    @Test
    void testGetSuffixNullFallback() {
        BladeCompiler compiler = new BladeCompiler("templates", classLoader, null);
        assertEquals(".blade.java", compiler.getSuffix(), "null 后缀应回退到默认");
    }

    @Test
    void testGetSuffixEmptyFallback() {
        BladeCompiler compiler = new BladeCompiler("templates", classLoader, "");
        assertEquals(".blade.java", compiler.getSuffix(), "空后缀应回退到默认");
    }

    // ===== 类名生成测试 =====

    @Test
    void testCompileHelloReturnsClassName() throws Exception {
        BladeCompiler compiler = new BladeCompiler("templates", classLoader);
        String className = compiler.compile("hello");
        assertEquals("Blade_hello", className, "编译 hello 应返回类名 Blade_hello");
    }

    @Test
    void testCompilePageReturnsClassName() throws Exception {
        BladeCompiler compiler = new BladeCompiler("templates", classLoader);
        String className = compiler.compile("page");
        assertEquals("Blade_page", className, "编译 page 应返回类名 Blade_page");
    }

    // ===== 编译产物验证测试 =====

    @Test
    void testCompiledClassExtendsBladeTemplate() throws Exception {
        BladeCompiler compiler = new BladeCompiler("templates", classLoader);
        String className = compiler.compile("hello");
        Class<?> clazz = classLoader.loadClass(className);
        assertNotNull(clazz, "编译后的类应可加载");
        assertTrue(BladeTemplate.class.isAssignableFrom(clazz),
                "编译产物应是 BladeTemplate 的子类");
    }

    // ===== {{ }} Echo 渲染测试 =====

    @Test
    void testCompileEchoRendering() throws Exception {
        BladeCompiler compiler = new BladeCompiler("templates", classLoader);
        String className = compiler.compile("hello");
        BladeTemplate template = (BladeTemplate) classLoader.loadClass(className)
                .getDeclaredConstructor().newInstance();
        template.getContext().setVariable("name", "Alice");
        template.getContext().setVariable("count", 5);
        template.init();
        String result = template.render();
        assertTrue(result.contains("Hello, Alice!"), "应渲染 {{ $name }} 为 Alice");
        assertTrue(result.contains("You have 5 messages."), "应渲染 {{ $count }} 为 5");
    }

    // ===== @extends 解析测试 =====

    @Test
    void testCompileExtendsSetsParentTemplate() throws Exception {
        BladeCompiler compiler = new BladeCompiler("templates", classLoader);
        String className = compiler.compile("page");
        BladeTemplate template = (BladeTemplate) classLoader.loadClass(className)
                .getDeclaredConstructor().newInstance();
        template.init();
        assertEquals("layout", template.getContext().getParentTemplate(),
                "@extends('layout') 应设置父模板为 layout");
    }

    // ===== @section 解析测试 =====

    @Test
    void testCompileSectionInlineValue() throws Exception {
        BladeCompiler compiler = new BladeCompiler("templates", classLoader);
        String className = compiler.compile("page");
        BladeTemplate template = (BladeTemplate) classLoader.loadClass(className)
                .getDeclaredConstructor().newInstance();
        template.init();
        assertEquals("My Page", template.getContext().getSection("title"),
                "@section('title', 'My Page') 应设置 section title 为 My Page");
    }

    // ===== @yield 解析测试 =====

    @Test
    void testCompileYieldDefaultValue() throws Exception {
        BladeCompiler compiler = new BladeCompiler("templates", classLoader);
        compiler.compile("layout");
        BladeTemplate template = (BladeTemplate) classLoader.loadClass("Blade_layout")
                .getDeclaredConstructor().newInstance();
        template.init();
        String result = template.render();
        assertTrue(result.contains("Default Title"),
                "@yield('title', 'Default Title') 在无 section 时应输出默认值");
        assertTrue(result.contains("Navigation"), "应包含父模板的静态内容");
    }

    // ===== @if / @else 解析测试 =====

    @Test
    void testCompileIfElseTrueBranch() throws Exception {
        BladeCompiler compiler = new BladeCompiler("templates", classLoader);
        String className = compiler.compile("condition");
        BladeTemplate template = (BladeTemplate) classLoader.loadClass(className)
                .getDeclaredConstructor().newInstance();
        template.getContext().setVariable("show", true);
        template.init();
        Consumer<Writer> renderer = template.getContext().getSectionRenderer("content");
        assertNotNull(renderer, "应注册 content section renderer");
        StringWriter writer = new StringWriter();
        renderer.accept(writer);
        String output = writer.toString();
        assertTrue(output.contains("Show"), "@if($show) 为 true 时应显示 Show");
        assertFalse(output.contains("Hide"), "true 分支不应包含 Hide");
    }

    @Test
    void testCompileIfElseFalseBranch() throws Exception {
        BladeCompiler compiler = new BladeCompiler("templates", classLoader);
        String className = compiler.compile("condition");
        BladeTemplate template = (BladeTemplate) classLoader.loadClass(className)
                .getDeclaredConstructor().newInstance();
        template.getContext().setVariable("show", false);
        template.init();
        Consumer<Writer> renderer = template.getContext().getSectionRenderer("content");
        StringWriter writer = new StringWriter();
        renderer.accept(writer);
        String output = writer.toString();
        assertTrue(output.contains("Hide"), "@if($show) 为 false 时应显示 Hide");
        assertFalse(output.contains("Show"), "false 分支不应包含 Show");
    }

    // ===== @foreach 解析测试 =====

    @Test
    void testCompileForEachLoop() throws Exception {
        BladeCompiler compiler = new BladeCompiler("templates", classLoader);
        String className = compiler.compile("loop");
        BladeTemplate template = (BladeTemplate) classLoader.loadClass(className)
                .getDeclaredConstructor().newInstance();
        template.getContext().setVariable("items", new String[]{"Alice", "Bob", "Charlie"});
        template.init();
        String result = template.render();
        assertTrue(result.contains("Alice"), "@foreach 应迭代渲染 Alice");
        assertTrue(result.contains("Bob"), "@foreach 应迭代渲染 Bob");
        assertTrue(result.contains("Charlie"), "@foreach 应迭代渲染 Charlie");
    }
}
