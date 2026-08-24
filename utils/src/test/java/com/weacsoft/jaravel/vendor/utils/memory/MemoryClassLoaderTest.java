package com.weacsoft.jaravel.vendor.utils.memory;

import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MemoryClassLoader} / {@link MemoryFileManager} / {@link SourceCodeJavaFileObject}
 * 内存编译三件套的单元测试。
 * <p>
 * 通过 {@code javax.tools.JavaCompiler} 将一段源码字符串编译为字节码，
 * 拦截到 {@link MemoryFileManager} 内存中，再用 {@link MemoryClassLoader} 加载并反射调用，
 * 验证 loadClass / findClass / defineClass 的核心行为。
 */
class MemoryClassLoaderTest {

    /**
     * 端到端：源码字符串 -> 内存编译 -> MemoryClassLoader 加载 -> 反射调用方法。
     * 验证 findClass 从内存字节码 defineClass 的核心能力。
     */
    @Test
    void testLoadClassFromMemoryAndInvoke() throws Exception {
        String className = "com.example.calc.HelloMemory";
        String source = "package com.example.calc; "
                + "public class HelloMemory { "
                + "  public String greet(String name) { return \"hello, \" + name; } "
                + "  public int add(int a, int b) { return a + b; } "
                + "}";

        Map<String, byte[]> compiled = compile(className, source);
        MemoryClassLoader loader = new MemoryClassLoader(compiled, getClass().getClassLoader());

        Class<?> clazz = loader.loadClass(className);
        assertNotNull(clazz, "loadClass 应返回非 null Class 对象");
        assertEquals("HelloMemory", clazz.getSimpleName());

        Object instance = clazz.getDeclaredConstructor().newInstance();
        Method greet = clazz.getMethod("greet", String.class);
        assertEquals("hello, jaravel", greet.invoke(instance, "jaravel"));

        Method add = clazz.getMethod("add", int.class, int.class);
        assertEquals(42, add.invoke(instance, 19, 23));
    }

    /**
     * 验证 loadClass 对父类加载器已加载的类（如 JDK 类）走双亲委派，能正常加载。
     */
    @Test
    void testLoadClassDelegatesToParentForJdkClasses() throws Exception {
        MemoryClassLoader loader = new MemoryClassLoader(new HashMap<>(), getClass().getClassLoader());
        Class<?> stringClass = loader.loadClass("java.lang.String");
        assertNotNull(stringClass);
        assertEquals("java.lang.String", stringClass.getName());
        // 确实由父加载器（bootstrap）加载，而非本加载器定义
        assertNotSame(loader, stringClass.getClassLoader());
    }

    /**
     * 验证 findClass 对不在内存映射中的类会抛出 ClassNotFoundException。
     */
    @Test
    void testFindClassThrowsForUnknownClass() {
        MemoryClassLoader loader = new MemoryClassLoader(new HashMap<>(), getClass().getClassLoader());
        assertThrows(ClassNotFoundException.class,
                () -> loader.findClass("com.example.notexist.Missing"));
    }

    /**
     * 验证 findClass 能直接加载内存中的类（不经 loadClass 的双亲委派）。
     */
    @Test
    void testFindClassLoadsFromMemoryDirectly() throws Exception {
        String className = "com.example.direct.Plain";
        String source = "package com.example.direct; public class Plain {}";
        Map<String, byte[]> compiled = compile(className, source);

        MemoryClassLoader loader = new MemoryClassLoader(compiled, getClass().getClassLoader());
        Class<?> clazz = loader.findClass(className);
        assertNotNull(clazz);
        assertEquals(className, clazz.getName());
        // 该类由 MemoryClassLoader 自身定义
        assertSame(loader, clazz.getClassLoader());
    }

    /**
     * 验证空映射的 MemoryClassLoader 仍可正常构造并委托父加载器。
     */
    @Test
    void testEmptyMapStillWorksForParentClasses() throws Exception {
        MemoryClassLoader loader = new MemoryClassLoader(null, getClass().getClassLoader());
        // loadClass 走双亲委派，能加载 Object
        Class<?> objectClass = loader.loadClass("java.lang.Object");
        assertNotNull(objectClass);
    }

    // ==================== MemoryFileManager / SourceCodeJavaFileObject ====================

    /**
     * 验证 MemoryFileManager 收集生成的类名与字节码。
     */
    @Test
    void testMemoryFileManagerCollectsGeneratedClass() throws Exception {
        String className = "com.example.fm.Standalone";
        String source = "package com.example.fm; public class Standalone {}";

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "当前环境需提供 JDK 以获取 JavaCompiler");
        MemoryFileManager fileManager = new MemoryFileManager(
                compiler.getStandardFileManager(null, null, null));
        SourceCodeJavaFileObject sourceObject = new SourceCodeJavaFileObject(className, source);

        boolean ok = compiler.getTask(null, fileManager, null, null, null, List.of(sourceObject)).call();
        assertTrue(ok, "编译应成功");

        assertTrue(fileManager.getGeneratedClassNames().contains(className),
                "生成的类名列表应包含编译产物");
        byte[] bytes = fileManager.getGeneratedClass(className);
        assertNotNull(bytes, "字节码不应为 null");
        assertTrue(bytes.length > 4, "class 字节码长度应大于 0");

        // magic number: 0xCAFEBABE
        assertEquals(0xCA, bytes[0] & 0xFF);
        assertEquals(0xFE, bytes[1] & 0xFF);
        assertEquals(0xBA, bytes[2] & 0xFF);
        assertEquals(0xBE, bytes[3] & 0xFF);

        // 不存在的类返回 null
        assertNull(fileManager.getGeneratedClass("com.example.notexist"));
    }

    /**
     * 验证 SourceCodeJavaFileObject.getCharContent 返回包装的源码字符串。
     */
    @Test
    void testSourceCodeJavaFileObjectCharContent() {
        String className = "com.example.sc.Greet";
        String sourceCode = "package com.example.sc; public class Greet { }";
        SourceCodeJavaFileObject sourceObject = new SourceCodeJavaFileObject(className, sourceCode);

        String content = sourceObject.getCharContent(true).toString();
        assertEquals(sourceCode, content);
    }

    /**
     * 编译一段源码，返回 类名 -> 字节码 的映射。
     */
    private Map<String, byte[]> compile(String className, String source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "当前环境需提供 JDK 以获取 JavaCompiler");
        MemoryFileManager fileManager = new MemoryFileManager(
                compiler.getStandardFileManager(null, null, null));
        SourceCodeJavaFileObject sourceObject = new SourceCodeJavaFileObject(className, source);
        boolean ok = compiler.getTask(null, fileManager, null, null, null, List.of(sourceObject)).call();
        assertTrue(ok, "源码编译应成功: " + className);

        Map<String, byte[]> compiled = new HashMap<>();
        for (String name : fileManager.getGeneratedClassNames()) {
            compiled.put(name, fileManager.getGeneratedClass(name));
        }
        return compiled;
    }
}
