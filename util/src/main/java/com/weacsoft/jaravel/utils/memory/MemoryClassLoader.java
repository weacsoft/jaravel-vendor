package com.weacsoft.jaravel.utils.memory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义类加载器，从内存读取class字节码
 */
public class MemoryClassLoader extends ClassLoader {
    //类存储器，存储类名和源代码
    private final Map<String, byte[]> compiledClasses = new ConcurrentHashMap<>();

    public Map<String, byte[]> getCompiledClasses() {
        return compiledClasses;
    }

    //搜索类
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] classData = getCompiledClasses().get(name);
        if (classData == null) {
            return super.findClass(name);
        }
        return defineClass(name, classData, 0, classData.length);
    }
}
