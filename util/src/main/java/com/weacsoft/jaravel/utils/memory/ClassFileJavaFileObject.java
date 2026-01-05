package com.weacsoft.jaravel.utils.memory;

import javax.tools.SimpleJavaFileObject;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;

/**
 * 内存中的class文件对象（存储编译后的字节码）
 */
public class ClassFileJavaFileObject extends SimpleJavaFileObject {
    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    ClassFileJavaFileObject(String className) {
        super(URI.create("bytes:///" + className.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
    }

    @Override
    public OutputStream openOutputStream() {
        return outputStream;
    }

    // 获取字节码
    public byte[] getBytes() {
        return outputStream.toByteArray();
    }
}
