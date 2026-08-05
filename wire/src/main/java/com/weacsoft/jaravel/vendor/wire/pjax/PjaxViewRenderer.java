package com.weacsoft.jaravel.vendor.wire.pjax;

import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import com.weacsoft.jaravel.vendor.http.controller.response.ResponseBuilder;
import com.weacsoft.jaravel.vendor.http.pjax.PjaxRenderer;

import java.util.Map;

/**
 * PJAX 渲染策略实现：把 {@code ResponseBuilder.view()} 透明改造为无感切换。
 *
 * <p>注册后，控制器仍然照常书写：</p>
 * <pre>{@code
 * public Response index(Request request) {
 *     return ResponseBuilder.view("pages.list", ResponseBuilder.map("items", items));
 * }
 * }</pre>
 *
 * <p>而实际输出会根据请求形态自动分流：</p>
 * <table border="1">
 *   <caption>输出分流</caption>
 *   <tr><th>场景</th><th>输出</th><th>Content-Type</th></tr>
 *   <tr><td>浏览器直接访问 /list</td><td>完整 HTML（含区域锚点 + pjax.js）</td><td>text/html</td></tr>
 *   <tr><td>从 /home 点击链接切到 /list</td><td>仅变化区域的 JSON 信封</td><td>application/json</td></tr>
 * </table>
 */
public class PjaxViewRenderer implements PjaxRenderer {

    @Override
    public boolean shouldIntercept() {
        try {
            return PjaxContext.isActive();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public Response render(String templateName, Map<String, Object> data) {
        PjaxContext.State state = PjaxContext.current();
        if (state == null) {
            return null;
        }
        if (state.partial) {
            return jsonResponse(PjaxManager.renderPartial(templateName, data, state));
        }
        String html = PjaxManager.renderPage(templateName, data, state.url);
        if (html == null || !html.toLowerCase().contains("</body>")) {
            // 不是完整 HTML 文档（如邮件模板、局部组件片段），放弃接管走原渲染链路
            return null;
        }
        return htmlResponse(html);
    }

    /**
     * 构造 PJAX 局部响应：JSON 内容 + 禁用缓存 + Vary 头。
     * <p>{@code Vary: X-Pjax} 至关重要——同一 URL 在带/不带 PJAX 头时返回不同内容，
     * 缺少该头会导致 CDN 或浏览器把 JSON 信封缓存成整页响应。</p>
     */
    private Response jsonResponse(String json) {
        return ResponseBuilder.raw()
                .status(200)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Cache-Control", "no-store")
                .header("Vary", "X-Pjax")
                .body(json);
    }

    /**
     * 构造 PJAX 首屏响应：完整 HTML + Vary 头。
     */
    private Response htmlResponse(String html) {
        return ResponseBuilder.raw()
                .status(200)
                .header("Content-Type", "text/html; charset=utf-8")
                .header("Vary", "X-Pjax")
                .body(html);
    }
}
