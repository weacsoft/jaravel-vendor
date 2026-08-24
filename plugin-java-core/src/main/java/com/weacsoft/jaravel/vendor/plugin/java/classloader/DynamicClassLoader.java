package com.weacsoft.jaravel.vendor.plugin.java.classloader;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态类加载器。
 * <p>
 * 继承 {@link URLClassLoader}，持有编译后的字节码（{@code Map<String, byte[]>}），
 * 在 {@link #findClass(String)} 中优先从内存字节码加载，找不到时委托父类加载。
 * <p>
 * 支持热重载：重新编译后创建新的 DynamicClassLoader 实例，旧实例通过 {@link #close()} 释放资源。
 * <p>
 * 线程安全：使用 {@link ConcurrentHashMap} 存储字节码，支持并发读。
 *
 * @see com.weacsoft.jaravel.vendor.plugin.java.compiler.DynamicJavaCompiler
 */
public class DynamicClassLoader extends URLClassLoader implements Closeable {

    /** 编译后的字节码：类全限定名 -> 字节码 */
    private final Map<String, byte[]> compiledClasses;

    /**
     * 构造动态类加载器。
     *
     * @param parent           父 ClassLoader
     * @param compiledClasses  编译后的字节码 Map
     */
    public DynamicClassLoader(ClassLoader parent, Map<String, byte[]> compiledClasses) {
        super(new URL[0], parent);
        this.compiledClasses = new ConcurrentHashMap<>(compiledClasses);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        // 优先从内存字节码加载
        byte[] bytecode = compiledClasses.get(name);
        if (bytecode != null) {
            return defineClass(name, bytecode, 0, bytecode.length);
        }
        // 委托父类加载
        return super.findClass(name);
    }

    /**
     * 获取指定类的字节码。
     *
     * @param className 类全限定名
     * @return 字节码数组，不存在返回 null
     */
    public byte[] getBytecode(String className) {
        return compiledClasses.get(className);
    }

    /**
     * 获取所有已编译的类名。
     *
     * @return 类名集合
     */
    public Set<String> getCompiledClassNames() {
        return compiledClasses.keySet();
    }

    @Override
    public void close() throws IOException {
        super.close();
    }
}
