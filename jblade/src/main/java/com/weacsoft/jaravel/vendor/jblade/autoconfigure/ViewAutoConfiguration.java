package com.weacsoft.jaravel.vendor.jblade.autoconfigure;

import com.weacsoft.jaravel.vendor.cache.CacheManager;
import com.weacsoft.jaravel.vendor.cache.CacheStore;
import com.weacsoft.jaravel.vendor.core.publish.PublishableRegistry;
import com.weacsoft.jaravel.vendor.core.view.View;
import com.weacsoft.jaravel.vendor.jblade.BladeFunctions;
import com.weacsoft.jaravel.vendor.jblade.view.BladeView;
import com.weacsoft.jaravel.vendor.jblade.view.RegisterView;
import com.weacsoft.jaravel.vendor.jblade.view.ViewFacade;
import com.weacsoft.jaravel.vendor.jblade.view.ViewManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationUtils;

/**
 * 视图模块自动装配。
 * <p>
 * 三层优先级（声明 → 配置 → 默认）：
 * <ol>
 *   <li><b>声明</b>：扫描所有 {@link RegisterView} 标注的 {@link View} Bean；</li>
 *   <li><b>配置</b>：{@code jaravel.view.default=xxx} 指定默认激活实现；</li>
 *   <li><b>默认</b>：无任何声明时，兜底注册一个 Blade 运行时实现（对齐 session 的兜底逻辑）。</li>
 * </ol>
 * 最终把解析出的默认 {@link View} 绑定到 {@link ViewFacade}，供 {@code ResponseBuilder}、
 * {@code WireManager} 直接取用，无需手动 set 引擎。
 * </p>
 */
@Configuration
public class ViewAutoConfiguration {
    static {
        PublishableRegistry.register(new JbladePublishableConfig());
    }

    private static final Logger log = LoggerFactory.getLogger(ViewAutoConfiguration.class);

    @Value("${jaravel.view.default:}")
    private String configuredDefault;

    @Value("${jaravel.view.template-dir:templates}")
    private String templateDir;

    @Value("${jaravel.view.suffix:.blade.java}")
    private String suffix;

    @Value("${jaravel.view.asset-url-prefix:/static}")
    private String assetUrlPrefix;

    @Bean
    public ViewManager viewManager(ConfigurableListableBeanFactory beanFactory,
                                   ObjectProvider<CacheManager> cacheManagerProvider) {
        ViewManager manager = new ViewManager();

        // 1. 声明层：收集所有 @RegisterView 标注的 View Bean
        boolean hasDeclared = false;
        String annotatedDefault = null;
        for (String beanName : beanFactory.getBeansWithAnnotation(RegisterView.class).keySet()) {
            Object bean = beanFactory.getBean(beanName);
            if (!(bean instanceof View)) {
                continue;
            }
            View view = (View) bean;
            hasDeclared = true;
            RegisterView merged = beanFactory.findAnnotationOnBean(beanName, RegisterView.class);
            String viewName = (merged != null && !merged.name().isEmpty()) ? merged.name() : view.name();
            if (merged != null && merged.defaultView()) {
                annotatedDefault = viewName;
            }
            manager.register(view);
        }

        // 2. 默认层：无任何声明时，兜底注册 Blade 运行时实现
        if (!hasDeclared) {
            log.warn("[view] 未声明任何 View 实现，兜底注册 Blade（运行时编译模式）");
            CacheStore cacheStore = resolveCacheStore(cacheManagerProvider);
            BladeView fallback = BladeView.runtime("blade", templateDir, suffix, cacheStore, assetUrlPrefix);
            manager.register(fallback);
        } else if (annotatedDefault != null) {
            manager.setAnnotatedDefault(annotatedDefault);
        }

        // 3. 配置层：jaravel.view.default 优先于注解 default
        if (configuredDefault != null && !configuredDefault.isEmpty()) {
            manager.setConfiguredDefault(configuredDefault);
        }

        // 绑定静态门面
        ViewFacade.bind(manager);
        // 注册模板可调用的全局函数:{{ View::preheat() }} 在任意模板里预热编译所有模板。
        // 由调用方主动触发(如应用启动完成后),框架不会自动执行。
        if (!BladeFunctions.has("View::preheat")) {
            BladeFunctions.register("View::preheat", args -> ViewFacade.preheat());
        }
        View defaultView = manager.defaultView();
        log.info("[view] 默认 View 实现: {}",
                defaultView == null ? "<none>" : defaultView.name());
        return manager;
    }

    private CacheStore resolveCacheStore(ObjectProvider<CacheManager> cacheManagerProvider) {
        CacheManager cm = cacheManagerProvider.getIfAvailable();
        if (cm == null) {
            return null;
        }
        try {
            return cm.store();
        } catch (Exception e) {
            log.warn("[view] 缓存模块未正确配置，编译缓存回退为内存");
            return null;
        }
    }
}
