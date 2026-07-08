package com.weacsoft.jaravel.vendor.plugin.java.executor;

import com.weacsoft.jaravel.vendor.plugin.java.classloader.DynamicClassLoader;
import com.weacsoft.jaravel.vendor.plugin.java.compiler.DynamicJavaCompiler;
import com.weacsoft.jaravel.vendor.plugin.java.compiler.DynamicJavaCompiler.JavaSourceFile;

import javax.tools.ToolProvider;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java 源码内存编译执行器。
 * <p>
 * 提供一站式「源码字符串 → 内存编译 → 内存加载 → 反射调用」能力，
 * 全程不落盘（无临时文件 I/O），底层复用 {@link DynamicJavaCompiler} + {@link DynamicClassLoader}。
 * <p>
 * 调用约定：优先反射调用 {@code run()} 方法（静态或实例均可），其次调用 {@code main(String[])} 方法。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * Map<String, Object> result = JavaSourceExecutor.compileAndRun(
 *     "public class Hello {\n" +
 *     "    public String run() {\n" +
 *     "        return \"Hello, World!\";\n" +
 *     "    }\n" +
 *     "}"
 * );
 * System.out.println(result.get("output")); // Hello, World!
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

    /**
     * 编译并执行 Java 源码（纯内存，不落盘）。
     *
     * @param sourceCode Java 源代码字符串
     * @return 执行结果 Map，包含以下字段：
     *         <ul>
     *           <li>success: 是否成功</li>
     *           <li>compile_success: 编译是否成功</li>
     *           <li>output: 执行输出（成功时）</li>
     *           <li>error: 错误信息（失败时）</li>
     *           <li>class_name: 解析出的类全限定名</li>
     *         </ul>
     */
    public static Map<String, Object> compileAndRun(String sourceCode) {
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

            // 优先调用 run()，其次调用 main()
            Object output = null;
            boolean invoked = false;

            try {
                Method runMethod = clazz.getMethod("run");
                output = Modifier.isStatic(runMethod.getModifiers())
                        ? runMethod.invoke(null)
                        : runMethod.invoke(clazz.getDeclaredConstructor().newInstance());
                invoked = true;
            } catch (NoSuchMethodException e) {
                // 没有 run() 方法，尝试 main()
            }

            if (!invoked) {
                try {
                    Method mainMethod = clazz.getMethod("main", String[].class);
                    if (Modifier.isStatic(mainMethod.getModifiers())) {
                        mainMethod.invoke(null, (Object) new String[]{});
                        output = "main() 方法已执行";
                        invoked = true;
                    }
                } catch (NoSuchMethodException e) {
                    // 没有 main() 方法
                }
            }

            if (!invoked) {
                result.put("success", false);
                result.put("error", "未找到 run() 或 main() 方法");
                return result;
            }

            result.put("success", true);
            result.put("output", output != null ? output.toString() : "null");
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

    /** 检查 JDK 编译器是否可用 */
    public static boolean isCompilerAvailable() {
        return ToolProvider.getSystemJavaCompiler() != null;
    }

    /**
     * 从源码中解析类全限定名（package + class）。
     * <p>
     * 先匹配 package 声明，再匹配 class/interface/enum/record 声明，
     * 拼接为全限定名。若源码无 package 声明，则返回简单类名。
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
        // 优先匹配 public 声明，其次匹配任意声明
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
}
