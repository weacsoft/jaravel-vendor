package com.weacsoft.jaravel.vendor.plugin.jar.annotation;

/**
 * HTTP 方法枚举，用于 {@link PluginMapping} 注解声明路由的请求方法。
 * <p>
 * 对齐 Spring MVC / Servlet 规范的标准 HTTP 方法。
 */
public enum HttpMethod {
    GET,
    POST,
    PUT,
    DELETE,
    PATCH,
    HEAD,
    OPTIONS
}
