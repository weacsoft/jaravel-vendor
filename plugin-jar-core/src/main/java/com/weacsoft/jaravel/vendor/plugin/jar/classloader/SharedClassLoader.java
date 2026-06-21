package com.weacsoft.jaravel.vendor.plugin.jar.classloader;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 共享 ClassLoader。
 * <p>
 * 三级 ClassLoader 隔离架构中的第二级：
 * <ul>
 *   <li>父 ClassLoader：JDK 平台 ClassLoader（{@code SharedClassLoader.class.getClassLoader().getParent()}），
 *       避免插件意外访问主程序的类。</li>
 *   <li>自身：加载共享 JAR（如 plugin-jar-core 自身、共享 API JAR、jaravel vendor JAR），
 *       以及插件中标注 {@code @SharedService} 的类。</li>
 *   <li>子 ClassLoader：{@link PluginClassLoader}，每个插件一个，加载插件私有类。</li>
 * </ul>
 * <p>
 * 共享 ClassLoader 可在运行时被替换（热更新共享 JAR 时），各插件 ClassLoader 通过
 * {@link PluginClassLoader#updateSharedClassLoader(SharedClassLoader)} 切换到新的共享 ClassLoader。
 */
public class SharedClassLoader extends URLClassLoader implements Closeable {

    private final String version;

    /**
     * 创建共享 ClassLoader，加载单个 JAR。
     *
     * @param jarPath JAR 路径
     * @param version 版本号
     * @return 共享 ClassLoader 实例
     */
    public static SharedClassLoader create(Path jarPath, String version) {
        List<Path> paths = new ArrayList<>();
        paths.add(jarPath);
        return create(paths, version);
    }

    /**
     * 创建共享 ClassLoader，加载多个 JAR。
     *
     * @param jarPaths JAR 路径列表
     * @param version  版本号
     * @return 共享 ClassLoader 实例
     */
    public static SharedClassLoader create(List<Path> jarPaths, String version) {
        URL[] urls = new URL[jarPaths.size()];
        try {
            for (int i = 0; i < jarPaths.size(); i++) {
                urls[i] = jarPaths.get(i).toUri().toURL();
            }
        } catch (Exception e) {
            throw new RuntimeException("构造共享 ClassLoader URL 失败", e);
        }
        // 父 ClassLoader 取当前 ClassLoader 的父，避免插件直接访问主程序类
        ClassLoader parent = SharedClassLoader.class.getClassLoader().getParent();
        return new SharedClassLoader(urls, parent, version);
    }

    private SharedClassLoader(URL[] urls, ClassLoader parent, String version) {
        super(urls, parent);
        this.version = version;
    }

    /**
     * 返回共享 ClassLoader 的版本号。
     *
     * @return 版本号
     */
    public String getVersion() {
        return version;
    }

    /**
     * 关闭 ClassLoader，忽略 IO 异常。
     * <p>
     * 共享 ClassLoader 在热更新时会被关闭，但关闭失败不应影响主流程。
     */
    @Override
    public void close() {
        try {
            super.close();
        } catch (IOException ignored) {
            // 忽略关闭异常
        }
    }
}
