package com.weacsoft.jaravel.utils.memory;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryFileManager extends ForwardingJavaFileManager<JavaFileManager> {
    // 存储生成的类文件
    private final Map<String, ClassFileJavaFileObject> generatedClasses = new ConcurrentHashMap<>();

    public MemoryFileManager(JavaFileManager fileManager) {
        super(fileManager);
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
        if (kind == JavaFileObject.Kind.CLASS) {
            ClassFileJavaFileObject classFile = new ClassFileJavaFileObject(className);
            generatedClasses.put(className, classFile);
            return classFile;
        }
        return super.getJavaFileForOutput(location, className, kind, sibling);
    }

    // 获取生成的类名列表
    public List<String> getGeneratedClassNames() {
        return new ArrayList<>(generatedClasses.keySet());
    }

    // 获取生成的类字节码
    public byte[] getGeneratedClass(String className) {
        ClassFileJavaFileObject classFile = generatedClasses.get(className);
        if (classFile != null) {
            return classFile.getBytes();
        }
        return null;
    }
}
