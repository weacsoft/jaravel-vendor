package com.weacsoft.jaravel.vendor.jblade.view;

import com.weacsoft.jaravel.vendor.core.view.View;
import com.weacsoft.jaravel.vendor.jblade.BladeAssetHelper;
import com.weacsoft.jaravel.vendor.jblade.BladeEngine;
import com.weacsoft.jaravel.vendor.jblade.PrecompiledTemplateLoader;
import com.weacsoft.jaravel.vendor.utils.memory.MemoryClassLoader;

import java.io.InputStream;
import java.util.Map;

/**
 * Blade 模板引擎的 {@link View} 实现（core 标准层）。
 * <p>
 * 包装 {@link BladeEngine}，使框架只依赖 {@link View} 抽象即可渲染 Blade 模板，
 * 而无需让非模板模块直接耦合 jblade。
 * 同时保留 {@link #getEngine()} 以便 {@code wire} 模块在组件渲染等场景直接使用底层引擎能力。
 * </p>
 */
public class BladeView implements View {

    private final String name;
    private final BladeEngine engine;

    public BladeView(String name, BladeEngine engine) {
        this.name = name;
        this.engine = engine;
    }

    @Override
    public String render(String templateName, Map<String, Object> data) throws Exception {
        return engine.render(templateName, data);
    }

    @Override
    public boolean exists(String templateName) {
        return engine.templateExists(templateName);
    }

    @Override
    public String name() {
        return name;
    }

    /**
     * 暴露底层 Blade 引擎，供需要引擎级能力（如组件渲染、section 解析）的模块使用。
     *
     * @return 底层 BladeEngine
     */
    public BladeEngine getEngine() {
        return engine;
    }

    /**
     * 构建 BladeView（预编译包模式，仅需 JRE）。
     * <p>
     * 从 classpath 资源加载预编译的 {@code .jblade.zip}，模板字节码已打包进 JAR，无需 JDK。
     * </p>
     *
     * @param name         实现名
     * @param templateDir  模板目录（classpath 下）
     * @param suffix       模板后缀
     * @param engine       已填充预编译数据的 BladeEngine
     * @param urlPrefix    静态资源前缀（写入 BladeAssetHelper）
     * @return BladeView
     */
    public static BladeView precompiledPackage(String name, String templateDir, String suffix,
                                               BladeEngine engine, String urlPrefix) {
        BladeAssetHelper.setUrlPrefix(urlPrefix);
        return new BladeView(name, engine);
    }

    /**
     * 构建 BladeView（预编译包模式，从 classpath zip 文件加载）。
     * <p>
     * 直接从 classpath 读取 {@code zipPath} 并填充到引擎，适用于编程式初始化。
     * </p>
     *
     * @param name        实现名
     * @param templateDir 模板目录
     * @param suffix      模板后缀
     * @param zipPath     classpath 下的 zip 路径（如 {@code classpath:templates.jblade.zip}）
     * @param urlPrefix   静态资源前缀
     * @return BladeView
     * @throws Exception 加载失败时抛出
     */
    public static BladeView precompiledPackageFromResource(String name, String templateDir, String suffix,
                                                            String zipPath, String urlPrefix) throws Exception {
        BladeAssetHelper.setUrlPrefix(urlPrefix);
        MemoryClassLoader loader = new MemoryClassLoader();
        BladeEngine engine = new BladeEngine(templateDir, suffix, null, loader);
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(
                zipPath.replace("classpath:", ""))) {
            if (is == null) {
                throw new IllegalArgumentException("找不到预编译包资源: " + zipPath);
            }
            PrecompiledTemplateLoader.PrecompiledBundle bundle =
                    PrecompiledTemplateLoader.loadBundleFromPackage(is);
            engine.populatePrecompiledBundle(bundle);
        }
        return new BladeView(name, engine);
    }

    /**
     * 构建 BladeView（运行时编译模式）。
     * <p>需要完整 JDK 才能编译模板源码。</p>
     *
     * @param name         实现名
     * @param templateDir  模板目录（classpath 下）
     * @param suffix       模板后缀
     * @param cacheStore   编译缓存（可空，空则仅内存缓存）
     * @param urlPrefix    静态资源前缀（写入 BladeAssetHelper）
     * @return BladeView
     */
    public static BladeView runtime(String name, String templateDir, String suffix,
                                    com.weacsoft.jaravel.vendor.cache.CacheStore cacheStore,
                                    String urlPrefix) {
        BladeAssetHelper.setUrlPrefix(urlPrefix);
        BladeEngine engine = new BladeEngine(templateDir, suffix, cacheStore);
        return new BladeView(name, engine);
    }
}
