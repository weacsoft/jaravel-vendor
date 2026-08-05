package com.weacsoft.jaravel.vendor.utils.runtime;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * 运行时编译 classpath 解析工具（兼容 Spring Boot 可执行 fat-jar）。
 * <p>
 * 模板引擎、动态插件编译器等需要在运行时调用 {@link javax.tools.JavaCompiler}（javac）
 * 把源码编译成字节码。以 {@code java -jar app.jar} 方式运行时：
 * <ul>
 *   <li>{@code java.class.path} 只包含最外层 fat-jar；</li>
 *   <li>真正的依赖位于 {@code BOOT-INF/lib/*.jar}（嵌套 jar），业务类位于
 *       {@code BOOT-INF/classes/}；</li>
 *   <li>Spring Boot 3.2+ 用 {@code nested:} 协议加载它们，javac 的标准文件管理器读不到，
 *       会导致「程序包 xxx 不存在 / 找不到符号」之类的编译错误。</li>
 * </ul>
 * 因此本工具在检测到 fat-jar 时，会把 {@code BOOT-INF/lib} 与 {@code BOOT-INF/classes}
 * 一次性展开到临时目录后再拼进 classpath（每个 JVM 只做一次，退出时清理）。
 * <p>
 * 普通 {@code -cp} / IDE / {@code spring-boot:run} 启动方式下不会触发展开，
 * 直接复用 {@code java.class.path} 与类加载器 URL 即可。
 * <p>
 * jblade 与 plugin-java 共用本工具，避免各模块重复实现 fat-jar 探测逻辑。
 */
public final class RuntimeClasspath {

    /**
     * 手动指定编译 classpath 的系统属性。
     * <p>
     * 设置后完全跳过自动探测，适用于自动探测不适用的特殊部署形态。
     */
    public static final String CLASSPATH_PROPERTY = "jblade.compiler.classpath";

    /** 解析结果缓存：整个 JVM 生命周期内只计算一次 */
    private static volatile String cachedClasspath;

    /** fat-jar 展开目录（懒创建，JVM 退出时删除） */
    private static File extractedBootDir;

    private RuntimeClasspath() {
    }

    /**
     * 构造供 javac 使用的 classpath。
     *
     * @return 以 {@link File#pathSeparator} 分隔的 classpath，可能为空字符串
     */
    public static String resolve() {
        String cached = cachedClasspath;
        if (cached != null) {
            return cached;
        }
        synchronized (RuntimeClasspath.class) {
            if (cachedClasspath != null) {
                return cachedClasspath;
            }

            String override = System.getProperty(CLASSPATH_PROPERTY);
            if (override != null && !override.trim().isEmpty()) {
                cachedClasspath = override.trim();
                return cachedClasspath;
            }

            Set<String> entries = new LinkedHashSet<>();

            // 1) 系统 classpath（普通启动方式下这一项就够了）
            String systemCp = System.getProperty("java.class.path");
            if (systemCp != null && !systemCp.isEmpty()) {
                for (String part : systemCp.split(Pattern.quote(File.pathSeparator))) {
                    if (!part.isEmpty()) {
                        entries.add(part);
                    }
                }
            }

            // 2) 类加载器链上的 file: URL（覆盖 IDE、spring-boot:run、插件类加载器等）
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = RuntimeClasspath.class.getClassLoader();
            }
            while (cl != null) {
                if (cl instanceof URLClassLoader) {
                    for (URL url : ((URLClassLoader) cl).getURLs()) {
                        if ("file".equals(url.getProtocol())) {
                            try {
                                entries.add(new File(url.toURI()).getAbsolutePath());
                            } catch (Exception ignored) {
                                // 单个条目解析失败不影响整体
                            }
                        }
                    }
                }
                cl = cl.getParent();
            }

            // 3) fat-jar：展开 BOOT-INF/lib 与 BOOT-INF/classes
            File bootJar = detectBootJar();
            if (bootJar != null) {
                entries.addAll(extractBootJar(bootJar));
            }

            cachedClasspath = String.join(File.pathSeparator, entries);
            return cachedClasspath;
        }
    }

    /**
     * 探测当前是否运行在 Spring Boot 可执行 fat-jar 中，并返回该 jar 文件。
     * <p>
     * 依次尝试两种来源：本类的 CodeSource 位置、{@code java.class.path} 单条目。
     * 任一路径指向的 jar 若包含 {@code BOOT-INF/} 目录即判定为 fat-jar。
     *
     * @return fat-jar 文件；非 fat-jar 运行方式返回 {@code null}
     */
    private static File detectBootJar() {
        List<String> candidates = new ArrayList<>();

        try {
            java.security.CodeSource cs = RuntimeClasspath.class.getProtectionDomain().getCodeSource();
            if (cs != null && cs.getLocation() != null) {
                candidates.add(cs.getLocation().toString());
            }
        } catch (Exception ignored) {
            // 安全管理器可能拒绝访问，忽略
        }

        String systemCp = System.getProperty("java.class.path");
        if (systemCp != null && !systemCp.contains(File.pathSeparator) && systemCp.endsWith(".jar")) {
            candidates.add(systemCp);
        }

        for (String candidate : candidates) {
            File jar = toOuterJarFile(candidate);
            if (jar != null && isBootJar(jar)) {
                return jar;
            }
        }
        return null;
    }

    /**
     * 从各种形态的位置串中解析出最外层 jar 文件。
     * <p>
     * 支持：{@code nested:/path/app.jar/!BOOT-INF/lib/x.jar}（Spring Boot 3.2+）、
     * {@code jar:file:/path/app.jar!/BOOT-INF/lib/x.jar!/}（旧版）、
     * {@code file:/path/app.jar} 以及普通文件路径。
     *
     * @param location 位置串
     * @return jar 文件；无法解析或文件不存在返回 {@code null}
     */
    private static File toOuterJarFile(String location) {
        if (location == null || location.isEmpty()) {
            return null;
        }
        try {
            String path = location;
            if (path.startsWith("nested:")) {
                path = path.substring("nested:".length());
                int sep = path.indexOf("/!");
                if (sep >= 0) {
                    path = path.substring(0, sep);
                }
                return existingJar(new File(URLDecoder.decode(path, "UTF-8")));
            }
            if (path.startsWith("jar:")) {
                path = path.substring("jar:".length());
                int sep = path.indexOf("!/");
                if (sep >= 0) {
                    path = path.substring(0, sep);
                }
            }
            if (path.startsWith("file:")) {
                return existingJar(new File(java.net.URI.create(path)));
            }
            return existingJar(new File(path));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 返回存在且为 {@code .jar} 的文件，否则返回 {@code null}。
     */
    private static File existingJar(File file) {
        if (file != null && file.isFile() && file.getName().endsWith(".jar")) {
            return file;
        }
        return null;
    }

    /**
     * 判断 jar 是否为 Spring Boot 可执行包（含 {@code BOOT-INF/} 目录）。
     */
    private static boolean isBootJar(File jar) {
        try (JarFile jarFile = new JarFile(jar)) {
            return jarFile.getEntry("BOOT-INF/") != null
                    || jarFile.getEntry("BOOT-INF/classes/") != null;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 将 fat-jar 中的 {@code BOOT-INF/lib/*.jar} 与 {@code BOOT-INF/classes/**}
     * 展开到临时目录，返回可直接用于 classpath 的条目列表。
     * <p>
     * 展开只在首次编译模板时发生一次，结果随 {@link #cachedClasspath} 缓存；
     * 临时目录在 JVM 退出时递归删除。
     *
     * @param bootJar fat-jar 文件
     * @return classpath 条目列表；展开失败返回空列表
     */
    private static List<String> extractBootJar(File bootJar) {
        List<String> result = new ArrayList<>();
        try {
            File dir = extractedBootDir();
            File classesDir = new File(dir, "classes");
            File libDir = new File(dir, "lib");
            //noinspection ResultOfMethodCallIgnored
            classesDir.mkdirs();
            //noinspection ResultOfMethodCallIgnored
            libDir.mkdirs();

            try (JarFile jarFile = new JarFile(bootJar)) {
                Enumeration<JarEntry> it = jarFile.entries();
                while (it.hasMoreElements()) {
                    JarEntry entry = it.nextElement();
                    String name = entry.getName();
                    if (entry.isDirectory()) {
                        continue;
                    }
                    File target = null;
                    if (name.startsWith("BOOT-INF/lib/") && name.endsWith(".jar")) {
                        target = new File(libDir, name.substring(name.lastIndexOf('/') + 1));
                    } else if (name.startsWith("BOOT-INF/classes/")) {
                        String relative = name.substring("BOOT-INF/classes/".length());
                        // 只需要 .class 文件即可满足编译；资源文件不参与编译，跳过以减少 IO
                        if (!relative.endsWith(".class")) {
                            continue;
                        }
                        target = new File(classesDir, relative);
                    }
                    if (target == null) {
                        continue;
                    }

                    // 目录穿越防护：展开路径必须落在临时目录内
                    if (!target.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator)) {
                        continue;
                    }
                    File parent = target.getParentFile();
                    if (parent != null && !parent.isDirectory()) {
                        //noinspection ResultOfMethodCallIgnored
                        parent.mkdirs();
                    }
                    if (target.isFile() && target.length() == entry.getSize()) {
                        continue;
                    }
                    try (InputStream in = jarFile.getInputStream(entry);
                         OutputStream out = new FileOutputStream(target)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            out.write(buf, 0, n);
                        }
                    }
                }
            }

            result.add(classesDir.getAbsolutePath());
            File[] libs = libDir.listFiles((d, n) -> n.endsWith(".jar"));
            if (libs != null) {
                Arrays.sort(libs);
                for (File lib : libs) {
                    result.add(lib.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            // 展开失败时退回到已收集的其它条目，编译错误信息足以提示用户
            return Collections.emptyList();
        }
        return result;
    }

    /**
     * 获取（并懒创建）fat-jar 展开目录，JVM 退出时递归删除。
     *
     * @return 临时目录
     * @throws IOException 目录创建失败
     */
    private static synchronized File extractedBootDir() throws IOException {
        if (extractedBootDir != null && extractedBootDir.isDirectory()) {
            return extractedBootDir;
        }
        File dir = java.nio.file.Files.createTempDirectory("jaravel-cp-").toFile();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteRecursively(dir)));
        extractedBootDir = dir;
        return dir;
    }

    /**
     * 递归删除目录，失败时静默忽略（仅用于临时目录清理）。
     *
     * @param file 待删除文件或目录
     */
    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
