package com.weacsoft.jaravel.vendor.plugin.jar.classloader;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Set;

/**
 * 插件 ClassLoader。
 * <p>
 * 三级 ClassLoader 隔离架构中的第三级（最底层），每个插件一个实例。
 * <p>
 * 类加载顺序：
 * <ol>
 *   <li><b>Application 代理类</b>：包名以 {@code com.weacsoft.jaravel.vendor.plugin.jar.annotation.} 开头的
 *       代理类委托给主程序 ClassLoader（即 {@code PluginClassLoader.class.getClassLoader()}），
 *       保证全进程唯一实例，使插件互调生效。</li>
 *   <li><b>共享包前缀</b>：匹配 {@code sharedPackagePrefixes} 的类委托给 {@link SharedClassLoader}，
 *       保证共享 API / 注解 / 共享服务在全插件范围内一致。</li>
 *   <li><b>自身加载</b>：其余类由本 ClassLoader 自行加载（插件私有类）。</li>
 *   <li><b>父类委托</b>：自身加载失败时回退到父 ClassLoader（即共享 ClassLoader）。</li>
 * </ol>
 * <p>
 * 热更新共享 JAR 时，通过 {@link #updateSharedClassLoader(SharedClassLoader)} 切换共享 ClassLoader，
 * 无需重建插件 ClassLoader。
 */
public class PluginClassLoader extends URLClassLoader implements Closeable {

    /** 默认共享包前缀：插件注解包必须由共享 ClassLoader 加载 */
    public static final String DEFAULT_SHARED_PREFIX = "com.weacsoft.jaravel.vendor.plugin.jar.annotation.";

    private final String pluginId;
    private volatile SharedClassLoader sharedClassLoader;
    private final Set<String> sharedPackagePrefixes;

    /**
     * 构造插件 ClassLoader。
     *
     * @param pluginId               插件 ID
     * @param jarPath                插件 JAR 路径
     * @param sharedClassLoader      共享 ClassLoader
     * @param sharedPackagePrefixes  共享包前缀集合（为空时使用默认值）
     */
    public PluginClassLoader(String pluginId, Path jarPath, SharedClassLoader sharedClassLoader,
                             Set<String> sharedPackagePrefixes) {
        super(toUrls(jarPath), sharedClassLoader);
        this.pluginId = pluginId;
        this.sharedClassLoader = sharedClassLoader;
        this.sharedPackagePrefixes = sharedPackagePrefixes == null || sharedPackagePrefixes.isEmpty()
                ? Set.of(DEFAULT_SHARED_PREFIX)
                : Set.copyOf(sharedPackagePrefixes);
    }

    private static URL[] toUrls(Path jarPath) {
        try {
            return new URL[]{jarPath.toUri().toURL()};
        } catch (Exception e) {
            throw new RuntimeException("构造插件 ClassLoader URL 失败: " + jarPath, e);
        }
    }

    /**
     * 返回插件 ID。
     *
     * @return 插件 ID
     */
    public String getPluginId() {
        return pluginId;
    }

    /**
     * 返回当前共享 ClassLoader。
     *
     * @return 共享 ClassLoader
     */
    public SharedClassLoader getSharedClassLoader() {
        return sharedClassLoader;
    }

    /**
     * 热更新共享 ClassLoader。
     * <p>
     * 切换后，后续加载的共享包前缀类将从新的共享 ClassLoader 加载；
     * 已加载的类不受影响（Class 对象一旦创建即绑定定义 ClassLoader）。
     *
     * @param newSharedClassLoader 新的共享 ClassLoader
     */
    public void updateSharedClassLoader(SharedClassLoader newSharedClassLoader) {
        this.sharedClassLoader = newSharedClassLoader;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            // 1. 已加载过的类直接返回
            Class<?> loaded = findLoadedClass(name);
            if (loaded != null) {
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }

            // 2. Application 代理类委托给主程序 ClassLoader，保证全进程唯一
            //    （Application、HttpMethod 等代理类必须由主程序加载，否则 volatile 静态字段不共享）
            if (isApplicationProxyClass(name)) {
                try {
                    Class<?> cls = PluginClassLoader.class.getClassLoader().loadClass(name);
                    if (resolve) {
                        resolveClass(cls);
                    }
                    return cls;
                } catch (ClassNotFoundException e) {
                    // 主程序未加载则继续尝试其他途径
                }
            }

            // 3. 共享包前缀委托给共享 ClassLoader
            if (isSharedPackage(name) && sharedClassLoader != null) {
                try {
                    Class<?> cls = sharedClassLoader.loadClass(name);
                    if (resolve) {
                        resolveClass(cls);
                    }
                    return cls;
                } catch (ClassNotFoundException e) {
                    // 共享 ClassLoader 未找到则继续尝试自身加载
                }
            }

            // 4. 自身加载（插件私有类）
            try {
                Class<?> cls = findClass(name);
                if (resolve) {
                    resolveClass(cls);
                }
                return cls;
            } catch (ClassNotFoundException e) {
                // 自身未找到则回退到父（共享 ClassLoader）
            }

            // 5. 父委托
            return super.loadClass(name, resolve);
        }
    }

    /**
     * 判断是否为 Application 代理类。
     * <p>
     * 代理类包括 {@code Application} 本身及其内部接口 {@code HotPluginManagerRef}，
     * 它们必须由主程序 ClassLoader 加载以保证静态 {@code managerRef} 字段全进程唯一。
     */
    private boolean isApplicationProxyClass(String name) {
        return name != null
                && (name.equals("com.weacsoft.jaravel.vendor.plugin.jar.annotation.Application")
                || name.startsWith("com.weacsoft.jaravel.vendor.plugin.jar.annotation.Application$"));
    }

    /**
     * 判断类名是否匹配共享包前缀。
     */
    private boolean isSharedPackage(String name) {
        if (name == null || sharedPackagePrefixes == null) {
            return false;
        }
        for (String prefix : sharedPackagePrefixes) {
            if (prefix != null && name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 关闭 ClassLoader，忽略 IO 异常。
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
