package com.weacsoft.jaravel.vendor.wire.pjax;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import com.weacsoft.jaravel.vendor.http.middleware.Middleware;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PJAX 全局中间件：判定请求是否纳入 PJAX 管辖并写入线程上下文。
 *
 * <p>注册为全局中间件后，所有页面路由自动获得无感切换能力，
 * 控制器与路由定义<b>无需任何改动</b>：</p>
 *
 * <pre>{@code
 * // RouteServiceProvider#configureRoutes
 * baseRouter.middleware(new PjaxMiddleware());
 * }</pre>
 *
 * <p>本中间件只负责识别与清理，真正的渲染切换发生在
 * {@code ResponseBuilder.view()} 内部的 {@link PjaxViewRenderer}。</p>
 *
 * <h3>纳入管辖的条件</h3>
 * <ul>
 *   <li>请求方法为 GET（写操作不做局部切换，避免与表单提交语义冲突）；</li>
 *   <li>不是 Wire 请求（{@code X-Wire-Request} 走原有链路）；</li>
 *   <li>不是纯接口调用（{@code X-Requested-With: XMLHttpRequest} 且未带 PJAX 头）；</li>
 *   <li>路径未被 {@link #setExcludedPrefixes(List)} 排除。</li>
 * </ul>
 */
public class PjaxMiddleware implements Middleware {

    /** 排除的路径前缀（如 /api、/static），命中则完全不介入 */
    private static final List<String> excludedPrefixes = new ArrayList<>();

    /**
     * 设置排除的路径前缀。命中前缀的请求完全不进入 PJAX 管辖。
     */
    public static void setExcludedPrefixes(List<String> prefixes) {
        excludedPrefixes.clear();
        if (prefixes != null) {
            for (String p : prefixes) {
                if (p != null && !p.isEmpty()) {
                    excludedPrefixes.add(p);
                }
            }
        }
    }

    public static List<String> getExcludedPrefixes() {
        return new ArrayList<>(excludedPrefixes);
    }

    @Override
    public Response handle(Request request, NextFunction next, String... params) {
        boolean marked = false;
        try {
            if (inScope(request)) {
                boolean partial = isPartialRequest(request);
                PjaxContext.begin(
                        fullUrl(request),
                        partial,
                        headerIgnoreCase(request, PjaxContext.HEADER_LAYOUT),
                        headerIgnoreCase(request, PjaxContext.HEADER_REGIONS));
                marked = true;
            }
            return next.apply(request);
        } finally {
            if (marked) {
                PjaxContext.clear();
            }
        }
    }

    /**
     * 判断请求是否纳入 PJAX 管辖。
     */
    private boolean inScope(Request request) {
        if (!"GET".equalsIgnoreCase(method(request))) {
            return false;
        }
        // Wire 请求走原有链路，不被 PJAX 接管
        if (headerIgnoreCase(request, "X-Wire-Request") != null) {
            return false;
        }
        String uri = request.uri();
        if (uri != null) {
            for (String prefix : excludedPrefixes) {
                if (uri.startsWith(prefix)) {
                    return false;
                }
            }
        }
        // 非 PJAX 的 XHR 通常是接口调用，不应被包装成页面
        if (!isPartialRequest(request)
                && "XMLHttpRequest".equalsIgnoreCase(
                        String.valueOf(headerIgnoreCase(request, "X-Requested-With")))) {
            return false;
        }
        return true;
    }

    /**
     * 是否为局部切换请求（携带 {@code X-Pjax: true}）。
     */
    private boolean isPartialRequest(Request request) {
        String flag = headerIgnoreCase(request, PjaxContext.HEADER_PJAX);
        return flag != null && "true".equalsIgnoreCase(flag.trim());
    }

    private String method(Request request) {
        try {
            String m = request.method();
            return m == null ? "GET" : m;
        } catch (Throwable ignored) {
            return "GET";
        }
    }

    /**
     * 大小写不敏感地读取请求头。
     * <p>HTTP 头名本身大小写不敏感，而浏览器 {@code fetch} 通常会将其小写化，
     * 因此不能依赖精确 key 匹配。</p>
     *
     * @return 头值；不存在或为空时返回 {@code null}
     */
    static String headerIgnoreCase(Request request, String name) {
        if (request == null || name == null) {
            return null;
        }
        String direct = request.header(name);
        if (direct != null && !direct.isEmpty()) {
            return direct;
        }
        for (String key : request.headerNames()) {
            if (name.equalsIgnoreCase(key)) {
                String value = request.header(key);
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }

    /**
     * 组装完整请求 URL（路径 + 查询串），用于前端 pushState 回填地址栏。
     * <p>查询串由 {@code Request#query()} 重建，保持与实际请求参数一致。</p>
     */
    private String fullUrl(Request request) {
        String uri = request.uri();
        if (uri == null || uri.isEmpty()) {
            uri = "/";
        }
        StringBuilder query = new StringBuilder();
        try {
            for (Map.Entry<String, Object> entry : request.query().entrySet()) {
                if (entry.getKey() == null || entry.getKey().isEmpty()) {
                    continue;
                }
                if (query.length() > 0) {
                    query.append('&');
                }
                query.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                        .append('=')
                        .append(URLEncoder.encode(
                                entry.getValue() == null ? "" : String.valueOf(entry.getValue()),
                                StandardCharsets.UTF_8));
            }
        } catch (RuntimeException ignored) {
            // 查询参数不可用时仅使用 URI
        }
        return query.length() == 0 ? uri : uri + "?" + query;
    }
}
