package com.weacsoft.jaravel.vendor.plugin.java.compiler;

import org.junit.jupiter.api.Test;

import javax.tools.ToolProvider;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@link DynamicJavaCompiler} 动态编译单元测试。
 * <p>
 * 需 JDK 环境提供 {@code javax.tools.JavaCompiler}；在纯 JRE 上相关用例会被跳过。
 */
class DynamicJavaCompilerTest {

    private final DynamicJavaCompiler compiler = new DynamicJavaCompiler();

    private static boolean hasJdk() {
        return ToolProvider.getSystemJavaCompiler() != null;
    }

    @Test
    void compileEmptyOrNullReturnsEmptyMap() {
        assertTrue(compiler.compile(Collections.emptyList(), getClass().getClassLoader()).isEmpty());
        // null 入参返回空 map（非 NPE）
        assertNotNull(compiler.compile(null, getClass().getClassLoader()));
    }

    @Test
    void compilesValidSourceIntoBytecode() {
        assumeTrue(hasJdk(), "需要 JDK 提供 Java 编译器");

        String src = "package demo;\n"
                + "public class Hello {\n"
                + "  public String greet(String name) { return \"Hello, \" + name + \"!\"; }\n"
                + "}\n";
        Map<String, byte[]> result = compiler.compile(
                List.of(new DynamicJavaCompiler.JavaSourceFile("demo.Hello", src, "Hello.java")),
                getClass().getClassLoader());

        assertEquals(1, result.size());
        assertNotNull(result.get("demo.Hello"));
        assertTrue(result.get("demo.Hello").length > 0, "应产生非空字节码");
    }

    @Test
    void compiledClassIsLoadableAndExecutable() throws Exception {
        assumeTrue(hasJdk(), "需要 JDK 提供 Java 编译器");

        String src = "package demo;\n"
                + "public class Greeter {\n"
                + "  public String hi(String n) { return \"hi \" + n; }\n"
                + "}\n";
        Map<String, byte[]> result = compiler.compile(
                List.of(new DynamicJavaCompiler.JavaSourceFile("demo.Greeter", src, "Greeter.java")),
                getClass().getClassLoader());

        com.weacsoft.jaravel.vendor.plugin.java.classloader.DynamicClassLoader cl =
                new com.weacsoft.jaravel.vendor.plugin.java.classloader.DynamicClassLoader(
                        getClass().getClassLoader(), result);
        Class<?> clazz = cl.loadClass("demo.Greeter");
        Object instance = clazz.getDeclaredConstructor().newInstance();
        Object out = clazz.getMethod("hi", String.class).invoke(instance, "java");
        assertEquals("hi java", out);
        cl.close();
    }

    @Test
    void invalidSourceThrowsWithDiagnostics() {
        assumeTrue(hasJdk(), "需要 JDK 提供 Java 编译器");

        String badSrc = "package demo;\npublic class Broken {\n  // 缺少闭合大括号";
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> compiler.compile(
                        List.of(new DynamicJavaCompiler.JavaSourceFile("demo.Broken", badSrc, "Broken.java")),
                        getClass().getClassLoader()));
        assertTrue(ex.getMessage().contains("编译失败"));
    }
}
