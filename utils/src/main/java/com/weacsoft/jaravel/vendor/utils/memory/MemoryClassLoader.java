package com.weacsoft.jaravel.vendor.utils.memory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义类加载器，从内存读取 class 字节码。
 * <p>
 * 对齐 <a href="https://github.com/weacsoft/database-all-support">weacsoft/database-all-support</a>
 * 中的 MemoryClassLoader 设计：接收一个 {@code Map<String, byte[]>}（类名 -> 字节码），
 * 在 {@link #findClass(String)} 时从内存中查找并 {@link #defineClass(String, byte[], int, int)}。
 * <p>
 * 典型用法：
 * <pre>
 * Map&lt;String, byte[]&gt; compiledClasses = new ConcurrentHashMap&lt;&gt;();
 * // ... 编译后填充 compiledClasses ...
 * MemoryClassLoader loader = new MemoryClassLoader(compiledClasses, getClass().getClassLoader());
 * Class&lt;?&gt; clazz = loader.loadClass("com.example.MyMigration");
 * </pre>
 *
 * @see MemoryFileManager
 * @see SourceCodeJavaFileObject
 */
public class MemoryClassLoader extends ClassLoader {

    /** 类名 -> 字节码 */
    private final Map<String, byte[]> classBytes;

    /**
     * 默认构造器，创建空的字节码映射。
     * 用于 jblade 等模块在编译后通过 {@link #getCompiledClasses()} 获取映射并填充。
     */
    public MemoryClassLoader() {
        super();
        this.classBytes = new ConcurrentHashMap<>();
    }

    /**
     * 构造内存类加载器。
     *
     * @param classBytes 编译后的类字节码映射（类名 -> 字节码）
     * @param parent     父类加载器，用于双亲委派查找未在内存中的类
     */
    public MemoryClassLoader(Map<String, byte[]> classBytes, ClassLoader parent) {
        super(parent);
        this.classBytes = classBytes;
    }

    /**
     * 从内存中查找并定义类。
     * <p>
     * 若内存中存在该类的字节码，则调用 {@link #defineClass(String, byte[], int, int)} 定义类；
     * 否则委托父类加载器查找。
     *
     * @param name 类全限定名
     * @return Class 对象
     * @throws ClassNotFoundException 如果内存和父加载器均找不到该类
     */
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = classBytes.get(name);
        if (bytes != null) {
            return defineClass(name, bytes, 0, bytes.length);
        }
        return super.findClass(name);
    }

    /**
     * 获取字节码映射表（类名 -> 字节码）。
     * <p>
     * jblade 的 BladeCompiler 在编译后通过此方法获取编译产物。
     *
     * @return 字节码映射表
     */
    public Map<String, byte[]> getCompiledClasses() {
        return classBytes;
    }
}
