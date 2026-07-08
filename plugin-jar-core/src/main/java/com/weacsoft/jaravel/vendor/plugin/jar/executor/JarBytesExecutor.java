package com.weacsoft.jaravel.vendor.plugin.jar.executor;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * Jar 字节码内存执行器。
 * <p>
 * 提供一站式「jar 字节数组 → 内存加载 → 反射调用」能力，
 * 全程不落盘（无临时文件 I/O）。
 * <p>
 * 内部使用自定义 {@link InMemoryJarClassLoader}，通过 {@link JarInputStream} +
 * {@link ByteArrayInputStream} 从 byte[] 逐个读取 .class entry 并 {@code defineClass}，
 * 不依赖 {@code URLClassLoader} 的文件 URL 约束。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * byte[] jarBytes = Files.readAllBytes(Path.of("my-plugin.jar"));
 * Map<String, Object> result = JarBytesExecutor.execute(jarBytes, "com.example.MyPlugin", "run");
 * System.out.println(result.get("output"));
 * }</pre>
 */
public final class JarBytesExecutor {

    private JarBytesExecutor() {
    }

    /**
     * 从内存中的 jar 字节数组加载并执行指定方法（纯内存，不落盘）。
     *
     * @param jarBytes  jar 文件的字节数组
     * @param mainClass 要调用的主类全限定名
     * @param method    要调用的方法名（null 或空则默认 "run"）
     * @return 执行结果 Map，包含以下字段：
     *         <ul>
     *           <li>success: 是否成功</li>
     *           <li>output: 执行输出（成功时）</li>
     *           <li>error: 错误信息（失败时）</li>
     *           <li>main_class: 主类名</li>
     *           <li>method: 方法名</li>
     *         </ul>
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

        InMemoryJarClassLoader classLoader = null;
        try {
            // 1. 从内存加载 jar 中的所有 class
            classLoader = new InMemoryJarClassLoader(
                    jarBytes, JarBytesExecutor.class.getClassLoader());

            // 2. 加载目标类
            Class<?> clazz = classLoader.loadClass(mainClass);

            // 3. 反射调用方法
            Method m = clazz.getMethod(method);
            Object output = Modifier.isStatic(m.getModifiers())
                    ? m.invoke(null)
                    : m.invoke(clazz.getDeclaredConstructor().newInstance());

            result.put("success", true);
            result.put("output", output != null ? output.toString() : "null");
        } catch (ClassNotFoundException e) {
            result.put("success", false);
            result.put("error", "类不存在: " + mainClass);
        } catch (NoSuchMethodException e) {
            result.put("success", false);
            result.put("error", "方法不存在: " + method);
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
                    // 读取 entry 的全部字节
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
