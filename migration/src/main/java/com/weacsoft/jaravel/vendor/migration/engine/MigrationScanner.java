package com.weacsoft.jaravel.vendor.migration.engine;


import com.weacsoft.jaravel.vendor.migration.Migration;
import com.weacsoft.jaravel.vendor.migration.MigrationAnnotation;
import com.weacsoft.jaravel.vendor.utils.memory.MemoryClassLoader;
import com.weacsoft.jaravel.vendor.utils.memory.MemoryFileManager;
import com.weacsoft.jaravel.vendor.utils.memory.SourceCodeJavaFileObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 迁移源扫描器与加载器，支持四种迁移来源模式。
 * <p>
 * <b>四种模式</b>：
 * <ul>
 *   <li><b>DIRECTORY</b>（目录模式）：扫描目录下的 {@code .java} 文件，运行时内存编译（需要 JDK）。
 *       通过 {@link #compileFromDirectory(String)} 加载。</li>
 *   <li><b>DIRECTORY_CLASSES</b>（预编译目录模式）：从目录加载预编译的 {@code .class} 文件（只需 JRE）。
 *       通过 {@link #loadFromDirectoryClasses(String)} 加载，使用 {@link URLClassLoader}。</li>
 *   <li><b>JAR</b>（JAR 模式）：从 {@code .jar} 文件加载预编译的迁移类（只需要 JRE）。
 *       通过 {@link #loadFromJar(File)} 加载，使用 {@link URLClassLoader}。</li>
 *   <li><b>CLASSPATH</b>（Classpath 模式）：从当前 classpath 扫描迁移类（内置迁移）。
 *       通过 {@link #loadFromClasspath()} 加载，使用当前 {@link ClassLoader}。</li>
 * </ul>
 * <p>
 * 三种模式均通过 {@link MigrationAnnotation} 注解自动识别迁移类，无需手动指定包名。
 * <p>
 * <b>核心数据结构</b>：
 * <ul>
 *   <li>{@code compiledClasses}（{@code Map<String, byte[]>}）：DIRECTORY 模式编译后的字节码</li>
 *   <li>{@code loadedClasses}（{@code Map<String, Class<?>>}）：JAR / CLASSPATH 模式加载的类</li>
 *   <li>{@code jarClassLoader}（{@link URLClassLoader}）：JAR 模式的类加载器，用于资源释放</li>
 * </ul>
 * <p>
 * 对齐 <a href="https://github.com/weacsoft/database-all-support">weacsoft/database-all-support</a>
 * 中的 {@code MigrationScanner} 设计。
 *
 * @see MemoryClassLoader
 * @see MemoryFileManager
 * @see SourceCodeJavaFileObject
 * @see MigrationAnnotation
 * @see MigrationSource
 */
public class MigrationScanner {

    private static final Logger log = LoggerFactory.getLogger(MigrationScanner.class);

    /** 类名 -> 字节码，存储 DIRECTORY 模式编译后的类 */
    private final Map<String, byte[]> compiledClasses = new ConcurrentHashMap<>();

    /** 类名 -> Class 对象，存储 JAR / CLASSPATH 模式加载的类 */
    private final Map<String, Class<?>> loadedClasses = new ConcurrentHashMap<>();

    /** 内存类加载器，懒加载（DIRECTORY 模式使用） */
    private MemoryClassLoader memoryClassLoader;

    /** JAR 模式的 URLClassLoader，用于资源释放 */
    private URLClassLoader jarClassLoader;

    /**
     * 获取内存类加载器（懒加载）。
     * <p>
     * 每次调用返回同一个实例，直到 {@link #removeMemoryClassLoader()} 被调用。
     *
     * @return MemoryClassLoader 实例
     */
    public MemoryClassLoader getMemoryClassLoader() {
        if (memoryClassLoader == null) {
            memoryClassLoader = new MemoryClassLoader(compiledClasses, getClass().getClassLoader());
        }
        return memoryClassLoader;
    }

    /**
     * 释放内存类加载器。
     * <p>
     * 将 {@link #memoryClassLoader} 置为 null，使已加载的类可被 GC 回收。
     */
    public void removeMemoryClassLoader() {
        memoryClassLoader = null;
    }

    /**
     * 获取所有迁移类全限定名列表（包含 DIRECTORY 编译的和 JAR/CLASSPATH 加载的）。
     *
     * @return 类全限定名列表
     */
    public List<String> getAllMigrationClassNames() {
        List<String> names = new ArrayList<>(compiledClasses.keySet());
        names.addAll(loadedClasses.keySet());
        return names;
    }

    /**
     * 获取已编译或已加载的类。
     * <p>
     * 优先从 {@code compiledClasses}（DIRECTORY 模式）中查找，
     * 若不存在则从 {@code loadedClasses}（JAR / CLASSPATH 模式）中返回。
     *
     * @param className 类全限定名
     * @return Class 对象
     * @throws ClassNotFoundException 如果类未找到
     */
    public Class<?> getCompiledClass(String className) throws ClassNotFoundException {
        // DIRECTORY 模式：通过 MemoryClassLoader 加载
        if (compiledClasses.containsKey(className)) {
            return getMemoryClassLoader().loadClass(className);
        }
        // JAR / CLASSPATH 模式：直接返回已加载的 Class
        Class<?> clazz = loadedClasses.get(className);
        if (clazz != null) {
            return clazz;
        }
        throw new ClassNotFoundException("迁移类未找到: " + className);
    }

    // ==================== DIRECTORY 模式 ====================

    /**
     * 编译指定目录下的所有 {@code .java} 文件（DIRECTORY 模式）。
     *
     * @param directory 目录路径
     * @throws Exception 编译或读取异常
     */
    public void compileFromDirectory(String directory) throws Exception {
        compileFromDirectory(new File(directory));
    }

    /**
     * 编译指定目录下的所有 {@code .java} 文件。
     * <p>
     * 扫描目录（仅一级，不递归）下所有以 {@code .java} 结尾的文件，
     * 逐个调用 {@link #compileFromFile(File)} 进行内存编译。
     *
     * @param dir 目录
     * @throws Exception 编译或读取异常
     */
    public void compileFromDirectory(File dir) throws Exception {
        if (dir.exists() && dir.isDirectory()) {
            List<File> resultFiles = new ArrayList<>();
            try (Stream<Path> pathStream = Files.list(dir.toPath())) {
                pathStream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .forEach(path -> resultFiles.add(path.toFile()));
            }
            log.info("[migration] 扫描到 {} 个 .java 迁移文件: {}", resultFiles.size(), dir.getAbsolutePath());
            for (File file : resultFiles) {
                compileFromFile(file);
            }
        } else {
            throw new RuntimeException("目录不存在或不是目录：" + dir.getAbsolutePath());
        }
    }

    /**
     * 编译单个 {@code .java} 文件。
     * <p>
     * 流程：
     * <ol>
     *   <li>读取源码</li>
     *   <li>移除注释与字符串（用于提取包名）</li>
     *   <li>提取包名与简单类名，拼接全限定名</li>
     *   <li>移除 {@code @Component} 注解与 import（兼容旧迁移文件）</li>
     *   <li>使用 {@link JavaCompiler} + {@link MemoryFileManager} 内存编译</li>
     *   <li>将编译后的字节码存入 {@link #compiledClasses}</li>
     * </ol>
     *
     * @param file .java 文件
     * @throws Exception 编译或读取异常
     */
    public void compileFromFile(File file) throws Exception {
        if (file.exists() && file.isFile()) {
            String sourceCode = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            if (sourceCode.isEmpty()) {
                throw new IOException("无法读取 Java 文件或文件内容为空: " + file.getAbsolutePath());
            }

            // 处理源代码，提取包名
            String codeWithoutCommentsAndStrings = removeCommentsAndStrings(sourceCode);
            String packageName = extractPackageName(codeWithoutCommentsAndStrings);
            String simpleClassName = extractClassName(file.getAbsolutePath());
            String fullClassName = packageName.isEmpty() ? simpleClassName : packageName + "." + simpleClassName;

            // 移除 @Component 注解与 import（兼容旧迁移文件）
            String processedSourceCode = removeComponentAnnotation(sourceCode);

            // 获取系统编译器
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                throw new IllegalStateException(
                    "无法获取 Java 编译器，当前运行环境为 JRE 而非 JDK。\n" +
                    "迁移文件源码模式(DIRECTORY)需要 JDK 运行环境。\n" +
                    "解决方案：\n" +
                    "1. 使用 JDK 运行程序\n" +
                    "2. 使用 JAR 模式：将迁移文件预编译为 JAR，通过 loadFromJar() 加载\n" +
                    "3. 使用 CLASSPATH 模式：将迁移类编译到 classpath 中，通过 loadFromClasspath() 加载\n" +
                    "4. 使用预编译目录模式：将迁移 .java 文件编译为 .class 文件放到目录，通过 loadFromDirectoryClasses() 加载"
                );
            }

            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

            try (MemoryFileManager fileManager = new MemoryFileManager(
                compiler.getStandardFileManager(diagnostics, null, null))) {

                List<JavaFileObject> compilationUnits = new ArrayList<>();
                compilationUnits.add(new SourceCodeJavaFileObject(fullClassName, processedSourceCode));

                // 添加 classpath 选项，确保编译器能找到迁移模块的类
                List<String> options = new ArrayList<>();
                options.add("-classpath");
                options.add(System.getProperty("java.class.path"));

                JavaCompiler.CompilationTask task = compiler.getTask(
                    null, fileManager, diagnostics, options, null, compilationUnits);
                Boolean success = task.call();

                if (success == null || !success) {
                    StringBuilder errorMsg = new StringBuilder("编译错误: ");
                    for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                        errorMsg.append(String.format("\n第%d行: %s",
                            diagnostic.getLineNumber(), diagnostic.getMessage(null)));
                    }
                    throw new RuntimeException(errorMsg.toString());
                }

                // 从内存文件管理器中获取编译后的类字节码
                for (String name : fileManager.getGeneratedClassNames()) {
                    compiledClasses.put(name, fileManager.getGeneratedClass(name));
                }
                log.info("[migration] 编译成功: {}", fullClassName);
            }
        } else {
            throw new RuntimeException("文件不存在或不是文件：" + file.getAbsolutePath());
        }
    }

    /**
     * 编译单个 {@code .java} 文件。
     *
     * @param filePath 文件路径
     * @throws Exception 编译或读取异常
     */
    public void compileFromFile(String filePath) throws Exception {
        compileFromFile(new File(filePath));
    }

    // ==================== 预编译目录模式 ====================

    /**
     * 从目录加载预编译的 .class 文件（JRE 模式，无需 JDK）。
     * <p>
     * 递归扫描目录下所有 {@code .class} 文件，通过 {@link URLClassLoader} 加载，
     * 检查 {@link MigrationAnnotation} 标记，自动识别迁移类。
     * <p>
     * 适用于：开发阶段用 JDK 编译迁移 .java 文件为 .class，运行时只需 JRE。
     *
     * @param dirPath 目录路径
     * @throws Exception 加载异常
     */
    public void loadFromDirectoryClasses(String dirPath) throws Exception {
        loadFromDirectoryClasses(new File(dirPath));
    }

    /**
     * 从目录加载预编译的 .class 文件。
     *
     * @param dir 目录
     * @throws Exception 加载异常
     */
    public void loadFromDirectoryClasses(File dir) throws Exception {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            throw new RuntimeException("目录不存在或不是目录: " + (dir != null ? dir.getAbsolutePath() : "null"));
        }

        URL dirUrl = dir.toURI().toURL();
        jarClassLoader = new URLClassLoader(new URL[]{dirUrl}, getClass().getClassLoader());

        int count = 0;
        List<File> classFiles = new ArrayList<>();
        collectClassFiles(dir, classFiles);

        log.info("[migration] 扫描到 {} 个 .class 文件: {}", classFiles.size(), dir.getAbsolutePath());

        for (File classFile : classFiles) {
            // 将文件路径转换为类全限定名
            String relativePath = dir.toURI().relativize(classFile.toURI()).getPath();
            String className = relativePath
                .replace('/', '.')
                .replace('\\', '.')
                .substring(0, relativePath.length() - ".class".length());

            // 跳过内部类
            if (className.contains("$")) {
                continue;
            }

            try {
                Class<?> clazz = jarClassLoader.loadClass(className);
                if (isMigrationClass(clazz)) {
                    loadedClasses.put(className, clazz);
                    count++;
                    log.info("[migration] 从目录加载迁移类: {}", className);
                }
            } catch (Throwable e) {
                log.debug("[migration] 跳过无法加载的类: {} - {}", className, e.getMessage());
            }
        }
        log.info("[migration] 从目录加载了 {} 个迁移类: {}", count, dir.getAbsolutePath());
    }

    /**
     * 递归收集目录下的所有 .class 文件。
     */
    private void collectClassFiles(File dir, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                collectClassFiles(file, result);
            } else if (file.getName().endsWith(".class")) {
                result.add(file);
            }
        }
    }

    // ==================== JAR 模式 ====================

    /**
     * 从 JAR 文件加载迁移类（JRE 模式，无需 JDK）。
     * <p>
     * 扫描 JAR 中所有 {@code .class} 文件（跳过 {@code META-INF} 目录），
     * 通过 {@link URLClassLoader} 加载，检查 {@link MigrationAnnotation} 标记，
     * 自动识别任意包名。
     * <p>
     * 加载的类存入 {@link #loadedClasses}，{@link URLClassLoader} 存入
     * {@link #jarClassLoader} 供后续使用与 {@link #finish()} 释放。
     *
     * @param jarFile JAR 文件路径
     * @throws Exception 加载异常
     */
    public void loadFromJar(File jarFile) throws Exception {
        if (jarFile == null || !jarFile.exists() || !jarFile.isFile()) {
            throw new RuntimeException("JAR 文件不存在或不是文件: " + (jarFile != null ? jarFile.getAbsolutePath() : "null"));
        }

        // 创建 URLClassLoader 加载 jar
        URL jarUrl = jarFile.toURI().toURL();
        jarClassLoader = new URLClassLoader(new URL[]{jarUrl}, getClass().getClassLoader());

        int count = 0;
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();

                // 只处理 .class 文件，跳过 META-INF 和内部类
                if (!entryName.endsWith(".class")) {
                    continue;
                }
                if (entryName.startsWith("META-INF/")) {
                    continue;
                }

                // 转换路径为类全限定名
                String className = entryName
                    .replace('/', '.')
                    .replace('\\', '.')
                    .substring(0, entryName.length() - ".class".length());

                // 跳过内部类（如 Migration$1.class）
                if (className.contains("$")) {
                    continue;
                }

                try {
                    Class<?> clazz = jarClassLoader.loadClass(className);
                    if (isMigrationClass(clazz)) {
                        loadedClasses.put(className, clazz);
                        count++;
                        log.info("[migration] 从 JAR 加载迁移类: {}", className);
                    }
                } catch (Throwable e) {
                    // 跳过无法加载的类（可能是依赖缺失等）
                    log.debug("[migration] 跳过无法加载的类: {} - {}", className, e.getMessage());
                }
            }
        }
        log.info("[migration] 从 JAR 加载了 {} 个迁移类: {}", count, jarFile.getAbsolutePath());
    }

    // ==================== CLASSPATH 模式 ====================

    /**
     * 从 classpath 扫描迁移类（内置迁移模式）。
     * <p>
     * 扫描当前 classloader 的所有 classpath 条目，查找标注 {@link MigrationAnnotation}
     * 的类。支持自动识别任意包名。
     * <p>
     * classpath 条目可以是：
     * <ul>
     *   <li>目录：递归扫描 {@code .class} 文件</li>
     *   <li>JAR 文件：扫描 jar 内的 {@code .class} 条目</li>
     * </ul>
     * 加载的类存入 {@link #loadedClasses}，使用当前 {@link ClassLoader}。
     *
     * @throws Exception 扫描异常
     */
    public void loadFromClasspath() throws Exception {
        String classpath = System.getProperty("java.class.path");
        if (classpath == null || classpath.isEmpty()) {
            log.warn("[migration] classpath 为空，无法扫描迁移类");
            return;
        }

        String separator = System.getProperty("path.separator");
        String[] paths = classpath.split(separator);
        ClassLoader classLoader = getClass().getClassLoader();
        int count = 0;

        for (String pathStr : paths) {
            File pathFile = new File(pathStr);
            if (!pathFile.exists()) {
                continue;
            }
            if (pathFile.isDirectory()) {
                count += scanDirectoryForMigrations(pathFile, classLoader);
            } else if (pathFile.getName().endsWith(".jar")) {
                count += scanJarForMigrations(pathFile, classLoader);
            }
        }
        log.info("[migration] 从 classpath 扫描到 {} 个迁移类", count);
    }

    /**
     * 递归扫描目录下的 {@code .class} 文件，查找迁移类。
     *
     * @param dir        目录
     * @param classLoader 类加载器
     * @return 加载的迁移类数量
     */
    private int scanDirectoryForMigrations(File dir, ClassLoader classLoader) {
        int count = 0;
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                count += scanDirectoryForMigrations(file, classLoader);
            } else if (file.getName().endsWith(".class")) {
                String className = pathToClassName(dir, file);
                if (className == null || className.contains("$")) {
                    continue;
                }
                count += tryLoadMigrationClass(className, classLoader);
            }
        }
        return count;
    }

    /**
     * 扫描 JAR 文件中的 {@code .class} 条目，查找迁移类（CLASSPATH 模式）。
     *
     * @param jarFile     JAR 文件
     * @param classLoader 类加载器
     * @return 加载的迁移类数量
     */
    private int scanJarForMigrations(File jarFile, ClassLoader classLoader) {
        int count = 0;
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (!entryName.endsWith(".class") || entryName.startsWith("META-INF/")) {
                    continue;
                }
                String className = entryName
                    .replace('/', '.')
                    .replace('\\', '.')
                    .substring(0, entryName.length() - ".class".length());
                if (className.contains("$")) {
                    continue;
                }
                count += tryLoadMigrationClass(className, classLoader);
            }
        } catch (IOException e) {
            log.debug("[migration] 无法读取 JAR 文件: {} - {}", jarFile.getAbsolutePath(), e.getMessage());
        }
        return count;
    }

    /**
     * 尝试加载类并检查是否为迁移类，若是则注册到 {@link #loadedClasses}。
     *
     * @param className   类全限定名
     * @param classLoader 类加载器
     * @return 1 表示成功加载并注册，0 表示不是迁移类或加载失败
     */
    private int tryLoadMigrationClass(String className, ClassLoader classLoader) {
        try {
            Class<?> clazz = classLoader.loadClass(className);
            if (isMigrationClass(clazz)) {
                loadedClasses.put(className, clazz);
                log.info("[migration] 从 classpath 加载迁移类: {}", className);
                return 1;
            }
        } catch (Throwable e) {
            // 跳过无法加载的类
            log.debug("[migration] 跳过无法加载的类: {} - {}", className, e.getMessage());
        }
        return 0;
    }

    /**
     * 检查类是否为迁移类：标注 {@link MigrationAnnotation} 且实现 {@link Migration}。
     *
     * @param clazz 待检查的类
     * @return true 表示是迁移类
     */
    private boolean isMigrationClass(Class<?> clazz) {
        // 跳过接口、抽象类、注解、枚举
        if (clazz.isInterface() || java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
            return false;
        }
        // 检查 @MigrationAnnotation
        if (!clazz.isAnnotationPresent(MigrationAnnotation.class)) {
            return false;
        }
        // 检查是否实现 Migration 接口
        return Migration.class.isAssignableFrom(clazz);
    }

    /**
     * 根据基准目录和 .class 文件路径，计算类全限定名。
     * <p>
     * 此方法用于 CLASSPATH 模式下扫描目录时的类名推导。
     * 由于无法确定 classpath 根目录，这里使用文件路径中从 {@code com} 目录开始的部分。
     *
     * @param parentDir .class 文件所在目录
     * @param classFile .class 文件
     * @return 类全限定名，无法推导时返回 null
     */
    private String pathToClassName(File parentDir, File classFile) {
        String fileName = classFile.getName();
        if (!fileName.endsWith(".class")) {
            return null;
        }
        String simpleName = fileName.substring(0, fileName.length() - ".class".length());

        // 从文件路径向上回溯，拼接包名
        List<String> parts = new ArrayList<>();
        parts.add(simpleName);
        File current = parentDir;
        File root = parentDir;
        while (current != null) {
            String dirName = current.getName();
            // 常见包名根目录：com, org, cn, io, net, app 等
            if (isPackageRoot(dirName)) {
                parts.add(dirName);
                root = current;
                break;
            }
            parts.add(dirName);
            current = current.getParentFile();
        }
        // 反转并拼接
        java.util.Collections.reverse(parts);
        return String.join(".", parts);
    }

    /**
     * 判断目录名是否为常见 Java 包根目录。
     *
     * @param dirName 目录名
     * @return true 表示是包根目录
     */
    private boolean isPackageRoot(String dirName) {
        return "com".equals(dirName) || "org".equals(dirName) || "cn".equals(dirName)
            || "io".equals(dirName) || "net".equals(dirName) || "app".equals(dirName)
            || "src".equals(dirName);
    }

    // ==================== 源码处理工具方法 ====================

    /**
     * 从处理后的源代码中提取包名（已移除注释和字符串）。
     *
     * @param processedSourceCode 已移除注释和字符串的源代码
     * @return 包名，无包名时返回空字符串
     */
    private String extractPackageName(String processedSourceCode) {
        Pattern pattern = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;");
        Matcher matcher = pattern.matcher(processedSourceCode);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    /**
     * 移除源代码中的注释和字符串常量，避免干扰包名解析。
     * <p>
     * 处理以下内容：
     * <ul>
     *   <li>单行注释 {@code // ...}</li>
     *   <li>多行注释 {@code /* ... *\/}</li>
     *   <li>字符串常量 {@code "..."} 与字符常量 {@code '.'}</li>
     * </ul>
     *
     * @param sourceCode 原始源代码
     * @return 移除注释和字符串后的源代码
     */
    private String removeCommentsAndStrings(String sourceCode) {
        StringBuilder result = new StringBuilder();
        int length = sourceCode.length();
        int i = 0;
        boolean inSingleLineComment = false;
        boolean inMultiLineComment = false;
        boolean inString = false;
        char stringDelimiter = '"';
        while (i < length) {
            char c = sourceCode.charAt(i);
            if (!inSingleLineComment && !inMultiLineComment && !inString) {
                if (c == '/' && i + 1 < length && sourceCode.charAt(i + 1) == '/') {
                    inSingleLineComment = true;
                    i += 2;
                    continue;
                } else if (c == '/' && i + 1 < length && sourceCode.charAt(i + 1) == '*') {
                    inMultiLineComment = true;
                    i += 2;
                    continue;
                } else if (c == '"' || c == '\'') {
                    inString = true;
                    stringDelimiter = c;
                    i++;
                    continue;
                }
            } else if (inSingleLineComment) {
                if (c == '\n' || c == '\r') {
                    inSingleLineComment = false;
                }
                i++;
                continue;
            } else if (inMultiLineComment) {
                if (c == '*' && i + 1 < length && sourceCode.charAt(i + 1) == '/') {
                    inMultiLineComment = false;
                    i += 2;
                    continue;
                }
                i++;
                continue;
            } else if (inString) {
                if (c == stringDelimiter) {
                    if (i > 0 && sourceCode.charAt(i - 1) != '\\') {
                        inString = false;
                    }
                }
                i++;
                continue;
            }
            result.append(c);
            i++;
        }
        return result.toString();
    }

    /**
     * 移除源代码中的 {@code @Component} 注解及其 import。
     * <p>
     * 用于兼容旧迁移文件（可能仍带有 {@code @Component} 注解），
     * 移除后使迁移文件成为纯 Java 文件，不依赖 Spring。
     *
     * @param sourceCode 原始源代码
     * @return 移除 @Component 后的源代码
     */
    private String removeComponentAnnotation(String sourceCode) {
        // 移除 import org.springframework.stereotype.Component;
        sourceCode = sourceCode.replaceAll(
            "import\\s+org\\.springframework\\.stereotype\\.Component\\s*;", "");
        // 移除 @Component 或 @Component(...) 注解（使用 \b 避免 @ComponentScan 误匹配）
        sourceCode = sourceCode.replaceAll("@Component\\b\\s*(\\([^)]*\\))?", "");
        return sourceCode;
    }

    /**
     * 从文件路径中提取简单类名（不含包名，不含扩展名）。
     *
     * @param filePath 文件路径
     * @return 简单类名
     */
    private String extractClassName(String filePath) {
        Path path = Paths.get(filePath);
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1) {
            return fileName;
        }
        return fileName.substring(0, dotIndex);
    }

    // ==================== 资源释放 ====================

    /**
     * 清除所有已编译的类字节码。
     */
    public void removeAll() {
        compiledClasses.clear();
        loadedClasses.clear();
    }

    /**
     * 释放所有资源：清除已编译类 + 清除已加载类 + 释放类加载器。
     * <p>
     * 在所有迁移操作完成后调用，确保：
     * <ul>
     *   <li>DIRECTORY 模式：清除 {@code compiledClasses} 与 {@link MemoryClassLoader}</li>
     *   <li>JAR 模式：清除 {@code loadedClasses} 并关闭 {@link URLClassLoader}</li>
     *   <li>CLASSPATH 模式：清除 {@code loadedClasses}</li>
     * </ul>
     */
    public void finish() {
        removeAll();
        removeMemoryClassLoader();
        // 关闭 JAR 模式的 URLClassLoader
        if (jarClassLoader != null) {
            try {
                jarClassLoader.close();
            } catch (IOException e) {
                log.warn("[migration] 关闭 URLClassLoader 失败: {}", e.getMessage());
            }
            jarClassLoader = null;
        }
        log.info("[migration] MigrationScanner 资源已释放");
    }
}
