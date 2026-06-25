package com.weacsoft.jaravel.vendor.plugin.jar.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 为 {@link PluginMapping} 方法声明额外的别名路由。
 * <p>
 * 当同一个方法需要挂载到多个路由路径时（如兼容旧路径 {@code /a/list} 和新路径 {@code /b/list}），
 * 在方法上添加 {@code @PluginMapping} 作为主路由，再添加一个或多个 {@code @PluginAlias} 作为别名路由。
 * <p>
 * 扫描器会为每个 {@code @PluginAlias} 生成独立的 {@code RouteInfo}，
 * 指向同一个 {@code beanName.methodName}，实现"一方法多路由"。
 * <p>
 * 使用示例：
 * <pre>
 * {@literal @PluginComponent}
 * public class BlogController {
 *     {@literal @PluginMapping(path = "/blog/list", method = HttpMethod.GET)}
 *     {@literal @PluginAlias(path = "/a/list")}            // 兼容旧路径，HTTP 方法继承主路由
 *     {@literal @PluginAlias(path = "/b/list", method = HttpMethod.POST)}  // 兼容旧路径，指定不同 HTTP 方法
 *     public Response list(Request request) {
 *         // ...
 *     }
 * }
 * </pre>
 * <p>
 * 注意：{@code @PluginAlias} 必须与 {@code @PluginMapping} 或 {@code @PluginRoute} 配合使用，
 * 单独使用无效（没有主路由则无法确定 Bean 和方法名）。
 *
 * @see PluginMapping
 * @see PluginRoute
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(PluginAliases.class)
public @interface PluginAlias {

    /**
     * 别名路由路径，支持路径参数，如 {@code /users/{id}}。
     */
    String path();

    /**
     * HTTP 方法。
     * <p>
     * 默认为 {@link HttpMethod#GET}。
     * 若别名路由的 HTTP 方法与主路由不同，需显式指定。
     *
     * @return HTTP 方法
     */
    HttpMethod method() default HttpMethod.GET;

    /**
     * 响应内容类型（Content-Type），默认为 {@code application/json}。
     *
     * @return 内容类型
     */
    String produces() default "application/json";
}
