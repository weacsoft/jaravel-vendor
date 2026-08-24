package com.weacsoft.jaravel.vendor.jblade.view;

import com.weacsoft.jaravel.vendor.core.registrar.AnnotationDrivenRegistrar;

import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式注册一个 {@link View} 实现（对齐 auth/cache 模块的 @RegisterXxx 风格）。
 * <p>
 * 用法：在提供 {@link View} Bean 的方法上标注，或在外层 @Configuration 上标注：
 * <pre>{@code
 * @RegisterView(name = "blade", defaultView = true)
 * @Bean
 * public View bladeView(ObjectProvider<CacheManager> cacheManagerProvider,
 *                       @Value("${jaravel.view.template-dir:templates}") String templateDir,
 *                       @Value("${jaravel.view.suffix:.blade.java}") String suffix) {
 *     CacheStore cacheStore = cacheManagerProvider.getIfAvailable() != null
 *             ? cacheManagerProvider.getIfAvailable().store() : null;
 *     return BladeView.runtime("blade", templateDir, suffix, cacheStore, "/static");
 * }
 * }</pre>
 * 优先级（声明 → 配置 → 默认）：
 * <ul>
 *   <li>声明层：本注解提供 View 实例，{@code name} 为其实例名；</li>
 *   <li>配置层：{@code jaravel.view.default=xxx} 指定默认激活哪一个声明；</li>
 *   <li>默认层：无任何声明时由 {@code ViewAutoConfiguration} 兜底注册 Blade 实现。</li>
 * </ul>
 *
 * @see ViewManager
 * @see ViewRegistrar
 */
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(ViewRegistrar.class)
public @interface RegisterView {

    /**
     * 视图实现名（唯一标识）。配置层通过 {@code jaravel.view.default} 引用它。
     */
    String name() default "blade";

    /**
     * 标记此实现是否为默认（当配置未指定 {@code jaravel.view.default} 时生效）。
     * 多个声明只允许一个为 true。
     */
    boolean defaultView() default false;
}
