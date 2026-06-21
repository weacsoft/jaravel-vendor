package com.weacsoft.jaravel.vendor.plugin.java.compiler;

import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 动态 Java 编译器。
 * <p>
 * 使用 JDK 内置的 {@link javax.tools.JavaCompiler}（通过 {@link ToolProvider#getSystemJavaCompiler()} 获取），
 * 将 .java 源文件编译为内存中的字节码，无需落盘。
 * <p>
 * 编译过程：
 * <ol>
 *   <li>将源代码包装为 {@link JavaSourceFile}（内部类，继承 {@link SimpleJavaFileObject}）。</li>
 *   <li>使用 {@link MemoryJavaFileManager} 拦截编译器输出的 .class 文件，存入内存。</li>
 *   <li>通过 {@code -classpath} 选项传入当前 classpath，使源文件可引用主程序依赖。</li>
 *   <li>使用 {@link DiagnosticCollector} 捕获编译诊断信息，失败时抛出包含详细错误的 RuntimeException。</li>
 * </ol>
 * <p>
 * 编译结果为 {@code Map<String, byte[]>}（类全限定名 -> 字节码），
 * 供 {@link com.weacsoft.jaravel.vendor.plugin.java.classloader.DynamicClassLoader} 加载。
 */
public class DynamicJavaCompiler {

    /**
     * 编译多个 Java 源文件。
     *
     * @param sourceFiles       源文件列表
     * @param parentClassLoader 父 ClassLoader（用于解析依赖 classpath）
     * @return 编译结果：类全限定名 -> 字节码
     * @throws RuntimeException 编译失败时抛出，包含诊断信息
     */
    public Map<String, byte[]> compile(List<JavaSourceFile> sourceFiles, ClassLoader parentClassLoader) {
        if (sourceFiles == null || sourceFiles.isEmpty()) {
            return new HashMap<>();
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("无法获取 Java 编译器，请确保使用 JDK 而非 JRE 运行程序");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        // 构造编译单元
        List<JavaFileObject> compilationUnits = new ArrayList<>();
        for (JavaSourceFile sourceFile : sourceFiles) {
            compilationUnits.add(new SourceCodeJavaFileObject(
                    sourceFile.getClassName(), sourceFile.getSourceCode()));
        }

        // 构造编译选项：传入 classpath 以解析依赖
        List<String> options = new ArrayList<>();
        options.add("-classpath");
        options.add(buildClasspath(parentClassLoader));

        Map<String, byte[]> compiledClasses = new HashMap<>();

        try (MemoryJavaFileManager fileManager = new MemoryJavaFileManager(
                compiler.getStandardFileManager(diagnostics, null, null))) {

            JavaCompiler.CompilationTask task = compiler.getTask(
                    null, fileManager, diagnostics, options, null, compilationUnits);

            Boolean success = task.call();

            if (success == null || !success) {
                String errorMsg = buildErrorMessage(diagnostics);
                throw new RuntimeException("Java 文件编译失败:\n" + errorMsg);
            }

            // 从内存文件管理器中提取编译后的字节码
            for (String className : fileManager.getGeneratedClassNames()) {
                compiledClasses.put(className, fileManager.getGeneratedClass(className));
            }
        } catch (IOException e) {
            throw new RuntimeException("编译器文件管理器关闭失败", e);
        }

        return compiledClasses;
    }

    /**
     * 构建 classpath 字符串。
     * <p>
     * 使用 {@code System.getProperty("java.class.path")} 获取当前 classpath，
     * 使源文件可引用主程序的所有依赖。
     *
     * @param parentClassLoader 父 ClassLoader（保留参数，当前使用系统 classpath）
     * @return classpath 字符串
     */
    private String buildClasspath(ClassLoader parentClassLoader) {
        String classpath = System.getProperty("java.class.path");
        return classpath != null ? classpath : "";
    }

    /**
     * 从诊断信息构建错误消息。
     */
    private String buildErrorMessage(DiagnosticCollector<JavaFileObject> diagnostics) {
        return diagnostics.getDiagnostics().stream()
                .map(d -> {
                    String source = d.getSource() != null ? d.getSource().getName() : "<unknown>";
                    return String.format("  [%s] %s 第%d行 第%d列: %s",
                            d.getKind(), source, d.getLineNumber(), d.getColumnNumber(),
                            d.getMessage(null));
                })
                .collect(Collectors.joining("\n"));
    }

    // ==================== 内部类 ====================

    /**
     * Java 源文件描述。
     * <p>
     * 包含类全限定名、源代码和文件名，用于传递给编译器。
     */
    public static class JavaSourceFile {

        private final String className;
        private final String sourceCode;
        private final String fileName;

        public JavaSourceFile(String className, String sourceCode, String fileName) {
            this.className = className;
            this.sourceCode = sourceCode;
            this.fileName = fileName;
        }

        public String getClassName() {
            return className;
        }

        public String getSourceCode() {
            return sourceCode;
        }

        public String getFileName() {
            return fileName;
        }
    }

    /**
     * 内存中的源代码文件对象。
     * <p>
     * 继承 {@link SimpleJavaFileObject}，通过 URI {@code string:///类名.java} 标识，
     * {@link #getCharContent} 返回源代码内容。
     */
    private static class SourceCodeJavaFileObject extends SimpleJavaFileObject {

        private final String sourceCode;

        SourceCodeJavaFileObject(String className, String sourceCode) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension),
                    Kind.SOURCE);
            this.sourceCode = sourceCode;
        }

        @Override
        public CharBuffer getCharContent(boolean ignoreEncodingErrors) {
            return CharBuffer.wrap(sourceCode);
        }
    }

    /**
     * 内存中的 class 文件对象。
     * <p>
     * 继承 {@link SimpleJavaFileObject}，通过 {@link ByteArrayOutputStream} 接收编译器输出的字节码。
     */
    private static class ClassFileJavaFileObject extends SimpleJavaFileObject {

        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        ClassFileJavaFileObject(String className) {
            super(URI.create("bytes:///" + className.replace('.', '/') + Kind.CLASS.extension),
                    Kind.CLASS);
        }

        @Override
        public OutputStream openOutputStream() {
            return outputStream;
        }

        byte[] getBytes() {
            return outputStream.toByteArray();
        }
    }

    /**
     * 内存文件管理器。
     * <p>
     * 继承 {@link ForwardingJavaFileManager}，拦截编译器的 .class 文件输出，
     * 将字节码存入内部的 {@link ClassFileJavaFileObject} Map，编译完成后统一提取。
     */
    private static class MemoryJavaFileManager extends ForwardingJavaFileManager<JavaFileManager> {

        private final Map<String, ClassFileJavaFileObject> generatedClasses = new HashMap<>();

        MemoryJavaFileManager(JavaFileManager fileManager) {
            super(fileManager);
        }

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
         * 获取已编译的类名列表。
         */
        List<String> getGeneratedClassNames() {
            return new ArrayList<>(generatedClasses.keySet());
        }

        /**
         * 获取指定类的字节码。
         */
        byte[] getGeneratedClass(String className) {
            ClassFileJavaFileObject classFile = generatedClasses.get(className);
            return classFile != null ? classFile.getBytes() : null;
        }
    }
}
