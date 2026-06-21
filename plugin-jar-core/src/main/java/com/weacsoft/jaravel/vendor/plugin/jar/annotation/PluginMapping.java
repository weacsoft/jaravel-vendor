package com.weacsoft.jaravel.vendor.plugin.jar.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记插件组件中的方法为路由处理方法。
 * <p>
 * 被此注解标记的方法将在插件启用时被注册为 HTTP 路由，
 * 当请求匹配 {@link #path()} 和 {@link #method()} 时，调用该方法处理请求。
 * <p>
 * 使用示例：
 * <pre>
 * {@literal @PluginComponent}
 * public class UserController {
 *     {@literal @PluginMapping(path = "/users/{id}", method = HttpMethod.GET)}
 *     public Response show(Request request) {
 *         String id = request.routeParam("id");
 *         return ResponseBuilder.json(Map.of("id", id));
 *     }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PluginMapping {

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
