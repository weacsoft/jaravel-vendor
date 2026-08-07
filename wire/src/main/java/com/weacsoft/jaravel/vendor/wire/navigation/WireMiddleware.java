package com.weacsoft.jaravel.vendor.wire.navigation;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.json.Json;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import com.weacsoft.jaravel.vendor.http.middleware.Middleware;
import com.weacsoft.jaravel.vendor.utils.WireMode;
import com.weacsoft.jaravel.vendor.wire.WireManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wire 全局中间件：透明拦截标准 {@code ResponseBuilder.view()} 响应并转换为 Wire diff JSON。
 */
public class WireMiddleware implements Middleware {

    private static final Logger log = LoggerFactory.getLogger(WireMiddleware.class);

    private static final String HEADER_NAVIGATE = "X-Wire-Navigate";
    private static final String HEADER_HASHES = "X-Wire-Hashes";
    /** 请求属性键：标记当前请求为 Wire 导航，ResponseBuilder.view() 读取此属性以启用 __wire_mode */
    public static final String ATTR_WIRE_NAVIGATE = "__wire_navigate";

    @Override
    public Response handle(Request request, NextFunction next, String... params) {
        // 检查是否为 Wire 导航请求（仅 GET）
        String navigate = headerIgnoreCase(request, HEADER_NAVIGATE);
        if (!"GET".equalsIgnoreCase(method(request)) || navigate == null || !"true".equals(navigate)) {
            // 普通整页请求：若页面包含 wire:section 标记，注入服务端算好的初始 hash，
            // 让前端首屏直接使用同口径 hash，导航时即可实现「只换差异 section」的最小 diff。
            return injectInitialHashes(next.apply(request));
        }

        // Wire 导航请求 → 写入上下文（ThreadLocal，供 ResponseBuilder 读取）
        Map<String, String> hashes = parseHashes(headerIgnoreCase(request, HEADER_HASHES));
        WireContext.begin(hashes);
        WireMode.begin();

        try {
            Response response = next.apply(request);
            String contentType = response.getContentType();
            if (contentType != null && contentType.toLowerCase().contains("text/html")) {
                String html = response.getContent();
                if (html != null && !html.isEmpty()) {
                    String url = fullUrl(request);
                    String diffJson = WireRenderer.renderDiff(html, url);
                    return new WireDiffResponse(diffJson, response.getStatus());
                }
            }
            return response;
        } finally {
            WireMode.clear();
            WireContext.clear();
        }
    }

    /**
     * 普通整页响应：若含 wire:section 标记，注入 {@code window.__wireHashes} 脚本。
     * 不改变页面任何可见内容，仅追加一段 JS 变量，供前端 wire-navigate.js 首屏直接使用。
     */
    private Response injectInitialHashes(Response response) {
        String contentType = response.getContentType();
        if (contentType == null || !contentType.toLowerCase().contains("text/html")) {
            return response;
        }
        String html = response.getContent();
        if (html == null || !html.contains("<!--wire:section-start:")) {
            return response;
        }
        Map<String, String> hashes = WireRenderer.computeHashes(html);
        String script = "<script>window.__wireHashes=" + Json.stringify(hashes) + ";</script>";
        String newHtml = html.contains("</body>")
                ? html.replace("</body>", script + "</body>")
                : html + script;
        return new HtmlContentWrapper(response, newHtml);
    }

    private Map<String, String> parseHashes(String headerValue) {
        Map<String, String> result = new LinkedHashMap<>();
        if (headerValue == null || headerValue.isEmpty()) return result;
        for (String pair : headerValue.split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String key = pair.substring(0, eq).trim();
                String val = pair.substring(eq + 1).trim();
                if (!key.isEmpty() && !val.isEmpty()) result.put(key, val);
            }
        }
        return result;
    }

    private String method(Request request) {
        try { return (String) request.getClass().getMethod("method").invoke(request); }
        catch (Exception e) { return "GET"; }
    }

    private String fullUrl(Request request) {
        String uri = request.uri();
        if (uri == null || uri.isEmpty()) uri = "/";
        String query = queryString(request);
        return query != null && !query.isEmpty() ? uri + "?" + query : uri;
    }

    private String queryString(Request request) {
        try { return (String) request.getClass().getMethod("queryString").invoke(request); }
        catch (Exception e) { return null; }
    }

    private String getHeader(Response response, String name) {
        try {
            Map<String, List<String>> headers = response.getHeaders();
            if (headers != null) {
                for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                    if (e.getKey().equalsIgnoreCase(name) && e.getValue() != null && !e.getValue().isEmpty()) {
                        return e.getValue().get(0);
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Map<String, Object> extractData(Response response) {
        // 尝试从 response body 获取数据（如果 response 存储了 data map）
        try {
            Object body = response.getBody();
            if (body instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) body;
                return data;
            }
        } catch (Exception ignored) {}
        return java.util.Collections.emptyMap();
    }

    static String headerIgnoreCase(Request request, String name) {
        if (request == null || name == null) return null;
        String direct = request.header(name);
        if (direct != null && !direct.isEmpty()) return direct;
        // 遍历查找（兼容大小写不敏感）
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) request.getClass()
                    .getMethod("getHeaders").invoke(request);
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    if (e.getKey().equalsIgnoreCase(name)) return e.getValue();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static WireMiddleware instance() {
        return new WireMiddleware();
    }

    /** 委托型响应：保持原响应的状态码/头/Cookie 等，仅替换 body 内容（用于注入初始 hash）。 */
    private static class HtmlContentWrapper implements Response {
        private final Response delegate;
        private final String content;

        HtmlContentWrapper(Response delegate, String content) {
            this.delegate = delegate;
            this.content = content;
        }

        @Override public String getContent() { return content; }
        @Override public int getStatus() { return delegate.getStatus(); }
        @Override public String getContentType() { return delegate.getContentType(); }
        @Override public Map<String, List<String>> getHeaders() { return delegate.getHeaders(); }
        @Override public void addHeader(String name, String value) { delegate.addHeader(name, value); }
        @Override public jakarta.servlet.http.Cookie[] getCookies() { return delegate.getCookies(); }
        @Override public void addCookie(jakarta.servlet.http.Cookie cookie) { delegate.addCookie(cookie); }
        @Override public void addCookie(String name, String value) { delegate.addCookie(name, value); }
        @Override public byte[] getBytes() { return content != null ? content.getBytes(java.nio.charset.StandardCharsets.UTF_8) : new byte[0]; }
        @Override public Object getBody() { return content; }
    }

    private static class WireDiffResponse implements Response {
        private final String json;
        private final int status;

        WireDiffResponse(String json, int status) { this.json = json; this.status = status; }

        @Override public String getContent() { return json; }
        @Override public int getStatus() { return status; }
        @Override public String getContentType() { return "application/json; charset=UTF-8"; }
        @Override public Map<String, List<String>> getHeaders() {
            Map<String, List<String>> h = new LinkedHashMap<>();
            h.put("Content-Type", List.of(getContentType()));
            return h;
        }
        @Override public void addHeader(String name, String value) {}
        @Override public jakarta.servlet.http.Cookie[] getCookies() { return new jakarta.servlet.http.Cookie[0]; }
        @Override public void addCookie(jakarta.servlet.http.Cookie cookie) {}
        @Override public void addCookie(String name, String value) {}
        @Override public byte[] getBytes() { return json != null ? json.getBytes(java.nio.charset.StandardCharsets.UTF_8) : new byte[0]; }
        @Override public Object getBody() { return json; }
    }
}
