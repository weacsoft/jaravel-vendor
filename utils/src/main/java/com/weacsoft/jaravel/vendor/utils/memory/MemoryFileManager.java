package com.weacsoft.jaravel.vendor.utils.memory;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存文件管理器，收集编译后的类字节码。
 * <p>
 * 继承 {@link ForwardingJavaFileManager}，在 {@link #getJavaFileForOutput} 时将编译器输出的
 * class 字节码拦截到内存中的 {@link ClassFileJavaFileObject}，而非写入磁盘。
 * <p>
 * 编译完成后可通过 {@link #getGeneratedClassNames()} 和 {@link #getGeneratedClass(String)}
 * 获取所有生成的类名与字节码。
 * <p>
 * 对齐 <a href="https://github.com/weacsoft/database-all-support">weacsoft/database-all-support</a>
 * 中的 MemoryFileManager 设计。
 *
 * @see MemoryClassLoader
 * @see SourceCodeJavaFileObject
 */
public class MemoryFileManager extends ForwardingJavaFileManager<JavaFileManager> {

    /** 存储生成的类文件（类名 -> ClassFileJavaFileObject） */
    private final Map<String, ClassFileJavaFileObject> generatedClasses = new ConcurrentHashMap<>();

    /**
     * 构造内存文件管理器。
     *
     * @param fileManager 被委托的标准文件管理器（通常由 compiler.getStandardFileManager 创建）
     */
    public MemoryFileManager(JavaFileManager fileManager) {
        super(fileManager);
    }

    /**
     * 拦截编译器输出：当编译器需要输出 class 文件时，返回内存中的
     * {@link ClassFileJavaFileObject}，使字节码写入内存而非磁盘。
     *
     * @param location  输出位置
     * @param className 类全限定名
     * @param kind      文件类型
     * @param sibling   兄弟文件对象
     * @return JavaFileObject
     * @throws IOException IO 异常
     */
    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className,
                                               JavaFileObject.Kind kind, FileObject sibling) throws IOException {
        if (kind == JavaFileObject.Kind.CLASS) {
            ClassFileJavaFileObject classFile = new ClassFileJavaFileObject(className);
            generatedClasses.put(className, classFile);
            return classFile;
        }
        return super.getJavaFileForOutput(location, className, kind, sibling);
    }

    /**
     * 获取所有生成的类名列表。
     *
     * @return 类全限定名列表
     */
    public List<String> getGeneratedClassNames() {
        return new ArrayList<>(generatedClasses.keySet());
    }

    /**
     * 获取指定类的字节码。
     *
     * @param className 类全限定名
     * @return 字节码数组，不存在时返回 null
     */
    public byte[] getGeneratedClass(String className) {
        ClassFileJavaFileObject classFile = generatedClasses.get(className);
        return classFile != null ? classFile.getBytes() : null;
    }

    /**
     * 内存中的 class 文件对象（存储编译后的字节码）。
     * <p>
     * 编译器通过 {@link #openOutputStream()} 获取输出流，将字节码写入
     * {@link ByteArrayOutputStream}，随后通过 {@link #getBytes()} 读取。
     */
    public static class ClassFileJavaFileObject extends SimpleJavaFileObject {

        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        /**
         * 构造内存 class 文件对象。
         *
         * @param className 类全限定名
         */
        public ClassFileJavaFileObject(String className) {
            super(URI.create("bytes:///" + className.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
        }

        /**
         * 返回字节码输出流，供编译器写入。
         *
         * @return 输出流
         */
        @Override
        public OutputStream openOutputStream() {
            return outputStream;
        }

        /**
         * 获取已写入的字节码。
         *
         * @return 字节码数组
         */
        public byte[] getBytes() {
            return outputStream.toByteArray();
        }
    }
}
