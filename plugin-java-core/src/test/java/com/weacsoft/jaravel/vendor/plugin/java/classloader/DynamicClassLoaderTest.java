package com.weacsoft.jaravel.vendor.plugin.java.classloader;

import com.weacsoft.jaravel.vendor.plugin.java.compiler.DynamicJavaCompiler;
import org.junit.jupiter.api.Test;

import javax.tools.ToolProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@link DynamicClassLoader} 动态类加载器单元测试。
 */
class DynamicClassLoaderTest {

    @Test
    void getBytecodeAndCompiledClassNamesExposeBackedMap() {
        Map<String, byte[]> map = new HashMap<>();
        byte[] bytes = {1, 2, 3};
        map.put("demo.A", bytes);
        DynamicClassLoader cl = new DynamicClassLoader(getClass().getClassLoader(), map);

        assertArrayEquals(bytes, cl.getBytecode("demo.A"));
        assertNull(cl.getBytecode("demo.Missing"));
        assertTrue(cl.getCompiledClassNames().contains("demo.A"));
        assertEquals(1, cl.getCompiledClassNames().size());
    }

    @Test
    void loadingUnknownClassThrowsClassNotFoundException() {
        DynamicClassLoader cl = new DynamicClassLoader(getClass().getClassLoader(), new HashMap<>());
        assertThrows(ClassNotFoundException.class, () -> cl.loadClass("totally.nonexistent.Class"));
    }

    @Test
    void loadsCompiledClassFromMemoryBytecode() throws Exception {
        assumeTrue(ToolProvider.getSystemJavaCompiler() != null, "需要 JDK");

        DynamicJavaCompiler compiler = new DynamicJavaCompiler();
        String src = "package demo;\n"
                + "public class Calc {\n"
                + "  public int add(int a, int b) { return a + b; }\n"
                + "}\n";
        Map<String, byte[]> result = compiler.compile(
                List.of(new DynamicJavaCompiler.JavaSourceFile("demo.Calc", src, "Calc.java")),
                getClass().getClassLoader());

        DynamicClassLoader cl = new DynamicClassLoader(getClass().getClassLoader(), result);
        Class<?> clazz = cl.loadClass("demo.Calc");
        Object inst = clazz.getDeclaredConstructor().newInstance();
        Object r = clazz.getMethod("add", int.class, int.class).invoke(inst, 3, 4);
        assertEquals(7, r);
        cl.close();
    }

    @Test
    void closeDoesNotThrow() {
        DynamicClassLoader cl = new DynamicClassLoader(getClass().getClassLoader(), new HashMap<>());
        assertDoesNotThrow(cl::close);
    }

    @Test
    void emptyMapClassLoaderHasNoCompiledClasses() {
        DynamicClassLoader cl = new DynamicClassLoader(getClass().getClassLoader(), new HashMap<>());
        assertTrue(cl.getCompiledClassNames().isEmpty());
    }
}
