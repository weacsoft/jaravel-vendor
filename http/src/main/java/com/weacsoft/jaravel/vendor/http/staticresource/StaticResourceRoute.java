package com.weacsoft.jaravel.vendor.http.staticresource;

import com.weacsoft.jaravel.vendor.controller.Controllers;
import com.weacsoft.jaravel.vendor.http.request.Request;
import com.weacsoft.jaravel.vendor.http.response.Response;
import com.weacsoft.jaravel.vendor.http.response.ResponseBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 静态资源路由处理器，实现 {@link Controllers.Runner}。
 * <p>
 * 注册到 Router 后，匹配 URL 前缀的 GET 请求会被此处理器拦截，
 * 从配置的资源目录中加载文件并返回。
 * <p>
 * <h3>使用方式</h3>
 * <pre>
 * // 在路由中注册静态资源目录
 * router.serveStatic("/static", "classpath:/static/", 3600);
 *
 * // 或通过配置自动注册
 * jaravel:
 *   http:
 *     static-resource:
 *       enabled: true
 *       url-prefix: /static
 *       default-location: classpath:/static/
 *       cache-max-age: 3600
 * </pre>
 * <p>
 * <h3>多目录回退</h3>
 * 支持注册多个资源目录，按顺序查找，第一个找到的返回：
 * <pre>
 * router.serveStatic("/static", List.of("file:./public/", "classpath:/static/"), 3600);
 * </pre>
 */
public class StaticResourceRoute implements Controllers.Runner {

    private static final Logger log = LoggerFactory.getLogger(StaticResourceRoute.class);

    private final String urlPrefix;
    private final List<StaticResourceHandler> handlers;

    /**
     * 构造单目录静态资源路由。
     *
     * @param urlPrefix    URL 前缀（如 {@code /static}）
     * @param location     资源目录（如 {@code classpath:/static/}）
     * @param cacheMaxAge  缓存时间（秒）
     */
    public StaticResourceRoute(String urlPrefix, String location, int cacheMaxAge) {
        this.urlPrefix = normalizePrefix(urlPrefix);
        this.handlers = new ArrayList<>();
        this.handlers.add(new StaticResourceHandler(location, cacheMaxAge));
    }

    /**
     * 构造多目录静态资源路由（按顺序回退查找）。
     *
     * @param urlPrefix    URL 前缀
     * @param locations    资源目录列表
     * @param cacheMaxAge  缓存时间（秒）
     */
    public StaticResourceRoute(String urlPrefix, List<String> locations, int cacheMaxAge) {
        this.urlPrefix = normalizePrefix(urlPrefix);
        this.handlers = new ArrayList<>();
        for (String loc : locations) {
            this.handlers.add(new StaticResourceHandler(loc, cacheMaxAge));
        }
    }

    @Override
    public Response handle(Request request) {
        // 从 routeParams 中提取文件路径（路由注册为 urlPrefix/{path}）
        // Spring RouterFunction 会将 {path} 解析为 routeParam
        String relativePath = request.routeParam("path");
        if (relativePath == null || relativePath.isEmpty()) {
            return ResponseBuilder.error(404, "Not Found");
        }

        // 按顺序在多个目录中查找
        for (StaticResourceHandler handler : handlers) {
            StaticResourceHandler.ResourceResult result = handler.load(relativePath);
            if (result != null) {
                log.debug("[static] 命中: {} -> {}{}", relativePath, handler.getLocation(), relativePath);
                return ResponseBuilder.staticFile(
                        result.getContent(),
                        result.getMimeType(),
                        result.getCacheMaxAge()
                );
            }
        }

        log.debug("[static] 未找到: {}", relativePath);
        return ResponseBuilder.error(404, "Not Found: " + relativePath);
    }

    /**
     * 返回 URL 前缀。
     */
    public String getUrlPrefix() {
        return urlPrefix;
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return "/static";
        }
        if (!prefix.startsWith("/")) {
            prefix = "/" + prefix;
        }
        // 去除末尾的 /
        while (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix;
    }
}
