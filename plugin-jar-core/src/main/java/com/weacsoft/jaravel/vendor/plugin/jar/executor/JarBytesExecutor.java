package com.weacsoft.jaravel.vendor.plugin.jar.executor;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * Jar 执行器，支持两种加载方式。
 * <p>
 * 提供一站式「jar → 加载 → 反射调用」能力。
 * <ul>
 *   <li>{@code inMemory=true}（默认）：从 byte[] 加载，使用 {@link InMemoryJarClassLoader}
 *       （JarInputStream + ByteArrayInputStream），不落盘</li>
 *   <li>{@code inMemory=false}：从文件路径加载，使用 {@link URLClassLoader}，
 *       保留传统的文件方式</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 纯内存加载
 * byte[] jarBytes = Files.readAllBytes(Path.of("my-plugin.jar"));
 * Map<String, Object> result = JarBytesExecutor.execute(jarBytes, "com.example.MyPlugin", "run");
 *
 * // 文件加载
 * Map<String, Object> result = JarBytesExecutor.execute(Path.of("my-plugin.jar"), "com.example.MyPlugin", "run");
 * }</pre>
 */
public final class JarBytesExecutor {

    private JarBytesExecutor() {
    }

    // ==================== 纯内存加载入口 ====================

    /**
     * 从内存中的 jar 字节数组加载并执行指定方法（纯内存，不落盘）。
     *
     * @param jarBytes  jar 文件的字节数组
     * @param mainClass 要调用的主类全限定名
     * @param method    要调用的方法名（null 或空则默认 "run"）
     * @return 执行结果 Map
     */
    public static Map<String, Object> execute(byte[] jarBytes, String mainClass, String method) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (jarBytes == null || jarBytes.length == 0) {
            result.put("success", false);
            result.put("error", "jar 字节数组为空");
            return result;
        }
        if (mainClass == null || mainClass.isEmpty()) {
            result.put("success", false);
            result.put("error", "缺少 main_class 参数");
            return result;
        }
        if (method == null || method.isEmpty()) {
            method = "run";
        }
        result.put("main_class", mainClass);
        result.put("method", method);
        result.put("in_memory", true);

        InMemoryJarClassLoader classLoader = null;
        try {
            classLoader = new InMemoryJarClassLoader(
                    jarBytes, JarBytesExecutor.class.getClassLoader());
            Class<?> clazz = classLoader.loadClass(mainClass);
            PluginExecutionHelper.invokeAndSetResult(result, clazz);
        } catch (ClassNotFoundException e) {
            result.put("success", false);
            result.put("error", "类不存在: " + mainClass);
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

    // ==================== 文件加载入口 ====================

    /**
     * 从文件路径加载 jar 并执行指定方法（文件加载方式）。
     *
     * @param jarPath   jar 文件路径
     * @param mainClass 要调用的主类全限定名
     * @param method    要调用的方法名（null 或空则默认 "run"）
     * @return 执行结果 Map
     */
    public static Map<String, Object> execute(Path jarPath, String mainClass, String method) {
        return execute(jarPath.toFile(), mainClass, method);
    }

    /**
     * 从文件加载 jar 并执行指定方法（文件加载方式）。
     * 使用 URLClassLoader 基于文件 URL 加载。
     *
     * @param jarFile   jar 文件
     * @param mainClass 要调用的主类全限定名
     * @param method    要调用的方法名（null 或空则默认 "run"）
     * @return 执行结果 Map
     */
    public static Map<String, Object> execute(File jarFile, String mainClass, String method) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (jarFile == null || !jarFile.exists()) {
            result.put("success", false);
            result.put("error", "Jar 文件不存在");
            return result;
        }
        if (mainClass == null || mainClass.isEmpty()) {
            result.put("success", false);
            result.put("error", "缺少 main_class 参数");
            return result;
        }
        if (method == null || method.isEmpty()) {
            method = "run";
        }
        result.put("main_class", mainClass);
        result.put("method", method);
        result.put("in_memory", false);

        URLClassLoader classLoader = null;
        try {
            classLoader = new URLClassLoader(new URL[]{jarFile.toURI().toURL()},
                    JarBytesExecutor.class.getClassLoader());
            Class<?> clazz = classLoader.loadClass(mainClass);
            PluginExecutionHelper.invokeAndSetResult(result, clazz);
        } catch (ClassNotFoundException e) {
            result.put("success", false);
            result.put("error", "类不存在: " + mainClass);
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

    /**
     * 统一入口：根据 inMemory 参数选择加载方式。
     *
     * @param jarFile   jar 文件
     * @param mainClass 要调用的主类全限定名
     * @param method    要调用的方法名
     * @param inMemory  true=纯内存加载，false=文件加载
     * @return 执行结果 Map
     */
    public static Map<String, Object> execute(File jarFile, String mainClass, String method, boolean inMemory) {
        if (inMemory) {
            try {
                byte[] jarBytes = Files.readAllBytes(jarFile.toPath());
                return execute(jarBytes, mainClass, method);
            } catch (IOException e) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", false);
                result.put("error", "读取 Jar 文件失败: " + e.getMessage());
                return result;
            }
        } else {
            return execute(jarFile, mainClass, method);
        }
    }

    // ==================== 扫描工具 ====================

    /**
     * 从 jar 字节数组中提取所有 class 的全限定名（不加载，仅扫描）。
     *
     * @param jarBytes jar 字节数组
     * @return class 全限定名列表
     */
    public static java.util.List<String> listClasses(byte[] jarBytes) {
        java.util.List<String> classNames = new java.util.ArrayList<>();
        if (jarBytes == null || jarBytes.length == 0) {
            return classNames;
        }
        try (JarInputStream jis = new JarInputStream(new ByteArrayInputStream(jarBytes))) {
            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                String name = entry.getName();
                if (name.endsWith(".class")) {
                    classNames.add(name.replace('/', '.').replace(".class", ""));
                }
            }
        } catch (IOException e) {
            // 忽略
        }
        return classNames;
    }

    /**
     * 从 jar 文件中提取所有 class 的全限定名（不加载，仅扫描）。
     *
     * @param jarFile jar 文件
     * @return class 全限定名列表
     */
    public static java.util.List<String> listClasses(File jarFile) {
        if (jarFile == null || !jarFile.exists()) {
            return new java.util.ArrayList<>();
        }
        try {
            return listClasses(Files.readAllBytes(jarFile.toPath()));
        } catch (IOException e) {
            return new java.util.ArrayList<>();
        }
    }

    // ==================== 内部类 ====================

    /**
     * 内存 Jar ClassLoader。
     * <p>
     * 从 jar 字节数组中读取所有 .class entry，存入内存 Map（类全限定名 -> byte[]），
     * {@link #findClass(String)} 时从内存 Map 中 defineClass，不落盘。
     * <p>
     * 继承 ClassLoader（非 URLClassLoader），避免对文件 URL 的依赖。
     */
    static class InMemoryJarClassLoader extends ClassLoader implements Closeable {

        private final Map<String, byte[]> classBytes = new java.util.concurrent.ConcurrentHashMap<>();

        InMemoryJarClassLoader(byte[] jarBytes, ClassLoader parent) {
            super(parent);
            try (JarInputStream jis = new JarInputStream(new ByteArrayInputStream(jarBytes))) {
                JarEntry entry;
                while ((entry = jis.getNextJarEntry()) != null) {
                    if (!entry.getName().endsWith(".class")) {
                        continue;
                    }
                    String className = entry.getName()
                            .replace('/', '.')
                            .replace(".class", "");
                    byte[] bytes = readAllBytes(jis);
                    classBytes.put(className, bytes);
                }
            } catch (IOException e) {
                throw new RuntimeException("从内存加载 jar 失败", e);
            }
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = classBytes.get(name);
            if (bytes != null) {
                return defineClass(name, bytes, 0, bytes.length);
            }
            return super.findClass(name);
        }

        @Override
        public void close() throws IOException {
            // ClassLoader 无需显式关闭资源
        }

        private static byte[] readAllBytes(JarInputStream jis) throws IOException {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int len;
            while ((len = jis.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        }
    }
}
