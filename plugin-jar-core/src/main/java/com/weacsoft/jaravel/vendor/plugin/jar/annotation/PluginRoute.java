package com.weacsoft.jaravel.vendor.plugin.jar.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记插件方法为"可注册路由"。
 * <p>
 * 与 {@link PluginMapping} 的区别：
 * <ul>
 *   <li>{@code @PluginMapping}：插件启用时自动注册路由（auto-register 模式）。</li>
 *   <li>{@code @PluginRoute}：仅标记方法为可注册路由，不自动注册。
 *       需由宿主手动调用 {@code registerRoute()} 注册（manual-register 模式）。</li>
 * </ul>
 * <p>
 * 在 auto-register=false 模式下，{@code @PluginMapping} 方法也会被扫描为 availableRoutes，
 * 但不会自动注册。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PluginRoute {

    /**
     * 路由路径，支持路径参数，如 {@code /users/{id}}。
     */
    String path();

    /**
     * HTTP 方法，默认为 {@link HttpMethod#GET}。
     */
    HttpMethod method() default HttpMethod.GET;

    /**
     * 响应内容类型（Content-Type），默认为 {@code application/json}。
     */
    String produces() default "application/json";
}
