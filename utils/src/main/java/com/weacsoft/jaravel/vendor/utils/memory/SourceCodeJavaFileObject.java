package com.weacsoft.jaravel.vendor.utils.memory;

import javax.tools.SimpleJavaFileObject;
import java.net.URI;
import java.nio.CharBuffer;

/**
 * 内存中的源代码 JavaFileObject。
 * <p>
 * 将 Java 源代码字符串包装为 {@link javax.tools.JavaFileObject}，供
 * {@link javax.tools.JavaCompiler} 编译时读取源码内容。
 * <p>
 * 对齐 <a href="https://github.com/weacsoft/database-all-support">weacsoft/database-all-support</a>
 * 中的 SourceCodeJavaFileObject 设计。
 * <pre>
 * String sourceCode = "package com.example; public class Hello {}";
 * SourceCodeJavaFileObject source = new SourceCodeJavaFileObject("com.example.Hello", sourceCode);
 * // 加入编译单元列表后交给 JavaCompiler 编译
 * </pre>
 *
 * @see MemoryFileManager
 * @see MemoryClassLoader
 */
public class SourceCodeJavaFileObject extends SimpleJavaFileObject {

    private final String sourceCode;

    /**
     * 构造内存源代码文件对象。
     *
     * @param className  类全限定名（用于构造虚拟 URI）
     * @param sourceCode Java 源代码字符串
     */
    public SourceCodeJavaFileObject(String className, String sourceCode) {
        super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
        this.sourceCode = sourceCode;
    }

    /**
     * 返回源代码内容，供编译器读取。
     *
     * @param ignoreEncodingErrors 是否忽略编码错误
     * @return 源代码 CharBuffer
     */
    @Override
    public CharBuffer getCharContent(boolean ignoreEncodingErrors) {
        return CharBuffer.wrap(sourceCode);
    }
}
