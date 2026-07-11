package com.weacsoft.jaravel.vendor.plugin.java.executor;

import com.weacsoft.jaravel.vendor.plugin.java.classloader.DynamicClassLoader;
import com.weacsoft.jaravel.vendor.plugin.java.compiler.DynamicJavaCompiler;
import com.weacsoft.jaravel.vendor.plugin.java.compiler.DynamicJavaCompiler.JavaSourceFile;
import com.weacsoft.jaravel.vendor.plugin.jar.executor.PluginExecutionHelper;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java 源码编译执行器，支持两种编译方式。
 * <p>
 * 提供一站式「源码字符串 → 编译 → 加载 → 反射调用」能力。
 * <ul>
 *   <li>{@code inMemory=true}（默认）：纯内存编译，使用 {@link DynamicJavaCompiler}（MemoryJavaFileManager）
 *       + {@link DynamicClassLoader}，全程不落盘（无临时文件 I/O）</li>
 *   <li>{@code inMemory=false}：文件编译，将源码写入临时目录，用标准 {@code javac} 编译为 .class 文件，
 *       再通过 {@link URLClassLoader} 加载，保留传统的文件方式</li>
 * </ul>
 * <p>
 * 调用约定：优先反射调用 {@code run()} 方法（静态或实例均可），其次调用 {@code main(String[])} 方法。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 纯内存编译（默认）
 * Map<String, Object> result = JavaSourceExecutor.compileAndRun(sourceCode);
 *
 * // 文件编译
 * Map<String, Object> result = JavaSourceExecutor.compileAndRun(sourceCode, false);
 * }</pre>
 */
public final class JavaSourceExecutor {

    private JavaSourceExecutor() {
    }

    /** 从源码中提取 package 声明 */
    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("package\\s+([\\w.]+)\\s*;");

    /** 从源码中提取 class/interface/enum/record 声明 */
    private static final Pattern CLASS_PATTERN =
            Pattern.compile("(?:class|interface|enum|record)\\s+(\\w+)");

    /** 从源码中提取 public class 声明（兼容旧逻辑） */
    private static final Pattern PUBLIC_CLASS_PATTERN =
            Pattern.compile("public\\s+(?:class|interface|enum|record)\\s+(\\w+)");

    // ==================== 入口方法 ====================

    /**
     * 编译并执行 Java 源码（纯内存，不落盘）。
     *
     * @param sourceCode Java 源代码字符串
     * @return 执行结果 Map
     */
    public static Map<String, Object> compileAndRun(String sourceCode) {
        return compileAndRun(sourceCode, true);
    }

    /**
     * 编译并执行 Java 源码。
     *
     * @param sourceCode Java 源代码字符串
     * @param inMemory   true=纯内存编译（不落盘），false=文件编译（落盘到临时目录）
     * @return 执行结果 Map，包含以下字段：
     *         <ul>
     *           <li>success: 是否成功</li>
     *           <li>compile_success: 编译是否成功</li>
     *           <li>output: 执行输出（成功时）</li>
     *           <li>error: 错误信息（失败时）</li>
     *           <li>class_name: 解析出的类全限定名</li>
     *           <li>in_memory: 使用的编译方式</li>
     *         </ul>
     */
    public static Map<String, Object> compileAndRun(String sourceCode, boolean inMemory) {
        return inMemory
                ? compileAndRunInMemory(sourceCode)
                : compileAndRunToFile(sourceCode);
    }

    /** 检查 JDK 编译器是否可用 */
    public static boolean isCompilerAvailable() {
        return ToolProvider.getSystemJavaCompiler() != null;
    }

    // ==================== 纯内存编译 ====================

    /**
     * 纯内存编译执行（不落盘）。
     * 底层使用 DynamicJavaCompiler（MemoryJavaFileManager）+ DynamicClassLoader。
     */
    private static Map<String, Object> compileAndRunInMemory(String sourceCode) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. 检查编译器是否可用
        if (ToolProvider.getSystemJavaCompiler() == null) {
            result.put("success", false);
            result.put("compile_success", false);
            result.put("error", "JDK 编译器不可用，请在 JDK 环境下运行（JRE 不包含 javax.tools.JavaCompiler）");
            return result;
        }

        // 2. 解析类全限定名
        String className = parseFullClassName(sourceCode);
        if (className == null) {
            result.put("success", false);
            result.put("compile_success", false);
            result.put("error", "无法解析类名，请确保源码包含 class/interface/enum/record 声明");
            return result;
        }
        result.put("class_name", className);
        result.put("in_memory", true);

        // 3. 内存编译
        DynamicJavaCompiler compiler = new DynamicJavaCompiler();
        Map<String, byte[]> compiledClasses;
        try {
            compiledClasses = compiler.compile(
                    Collections.singletonList(new JavaSourceFile(className, sourceCode, className + ".java")),
                    JavaSourceExecutor.class.getClassLoader()
            );
        } catch (RuntimeException e) {
            result.put("success", false);
            result.put("compile_success", false);
            result.put("error", e.getMessage());
            return result;
        }
        result.put("compile_success", true);

        // 4. 内存加载并反射调用
        DynamicClassLoader classLoader = null;
        try {
            classLoader = new DynamicClassLoader(
                    JavaSourceExecutor.class.getClassLoader(), compiledClasses);
            Class<?> clazz = classLoader.loadClass(className);
            PluginExecutionHelper.invokeAndSetResult(result, clazz);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "执行异常: " + e.getMessage());
        } finally {
            if (classLoader != null) {
                try { classLoader.close(); } catch (Exception ignored) { }
            }
        }

        return result;
    }

    // ==================== 文件编译 ====================

    /**
     * 文件编译执行（落盘到临时目录）。
     * 将源码写入临时 .java 文件，用标准 javac 编译为 .class 文件，再通过 URLClassLoader 加载。
     */
    private static Map<String, Object> compileAndRunToFile(String sourceCode) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. 检查编译器是否可用
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            result.put("success", false);
            result.put("compile_success", false);
            result.put("error", "JDK 编译器不可用，请在 JDK 环境下运行（JRE 不包含 javax.tools.JavaCompiler）");
            return result;
        }

        // 2. 解析类全限定名和简单类名
        String className = parseFullClassName(sourceCode);
        if (className == null) {
            result.put("success", false);
            result.put("compile_success", false);
            result.put("error", "无法解析类名，请确保源码包含 class/interface/enum/record 声明");
            return result;
        }
        String simpleName = className.contains(".")
                ? className.substring(className.lastIndexOf('.') + 1) : className;
        result.put("class_name", className);
        result.put("in_memory", false);

        // 3. 写入临时文件并编译
        Path tempDir = null;
        URLClassLoader classLoader = null;
        try {
            tempDir = Files.createTempDirectory("java-run-");
            Path sourceFile = tempDir.resolve(simpleName + ".java");
            Files.writeString(sourceFile, sourceCode);

            // 使用标准 javac 编译
            int exitCode = compiler.run(null, null, null,
                    "-classpath", System.getProperty("java.class.path"),
                    sourceFile.toString());

            if (exitCode != 0) {
                result.put("success", false);
                result.put("compile_success", false);
                result.put("error", "编译失败，退出码: " + exitCode);
                return result;
            }
            result.put("compile_success", true);

            // 4. 通过 URLClassLoader 加载
            classLoader = new URLClassLoader(new URL[]{tempDir.toUri().toURL()},
                    JavaSourceExecutor.class.getClassLoader());
            Class<?> clazz = classLoader.loadClass(className);
            PluginExecutionHelper.invokeAndSetResult(result, clazz);
        } catch (Exception e) {
            if (!result.containsKey("compile_success")) {
                result.put("compile_success", false);
            }
            result.put("success", false);
            result.put("error", "执行异常: " + e.getMessage());
        } finally {
            if (classLoader != null) {
                try { classLoader.close(); } catch (Exception ignored) { }
            }
            // 清理临时文件
            if (tempDir != null) {
                cleanupTempDir(tempDir);
            }
        }

        return result;
    }

    // ==================== 公共工具方法 ====================

    /**
     * 从源码中解析类全限定名（package + class）。
     *
     * @param sourceCode 源代码字符串
     * @return 类全限定名，解析失败返回 null
     */
    public static String parseFullClassName(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return null;
        }

        // 解析 package
        String packageName = null;
        Matcher pkgMatcher = PACKAGE_PATTERN.matcher(sourceCode);
        if (pkgMatcher.find()) {
            packageName = pkgMatcher.group(1);
        }

        // 解析 class/interface/enum/record 名称
        String simpleClassName = null;
        Matcher pubMatcher = PUBLIC_CLASS_PATTERN.matcher(sourceCode);
        if (pubMatcher.find()) {
            simpleClassName = pubMatcher.group(1);
        } else {
            Matcher clsMatcher = CLASS_PATTERN.matcher(sourceCode);
            if (clsMatcher.find()) {
                simpleClassName = clsMatcher.group(1);
            }
        }

        if (simpleClassName == null) {
            return null;
        }

        return packageName != null ? packageName + "." + simpleClassName : simpleClassName;
    }

    /** 清理临时目录 */
    private static void cleanupTempDir(Path tempDir) {
        try {
            Files.walk(tempDir)
                    .sorted(java.util.Collections.reverseOrder())
                    .map(java.nio.file.Path::toFile)
                    .forEach(File::delete);
        } catch (Exception ignored) {
        }
    }
}
