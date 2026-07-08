package com.weacsoft.jaravel.vendor.wire;

import com.weacsoft.jaravel.vendor.http.response.Response;
import com.weacsoft.jaravel.vendor.http.response.ResponseBuilder;

import java.util.*;

/**
 * Wire 响应构建器，Wire 控制器的统一响应入口。
 * <p>
 * 提供语义化的静态方法，覆盖 Wire 的全部响应场景：
 * <ul>
 *   <li>{@link #wire} — 初始页面渲染（完整 HTML + Wire 资源注入）</li>
 *   <li>{@link #update} — 部分更新（仅返回变化的 section HTML + 新 snapshot）</li>
 *   <li>{@link #redirect} — Wire 重定向（返回 JSON，前端自动跳转，无感）</li>
 *   <li>{@link #error} — Wire 错误（返回 JSON，前端可处理）</li>
 *   <li>{@link #of} — 全能构建器（通过参数组合以上能力）</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 初始页面
 * public Response page(Request request) {
 *     Map<String, Object> data = new LinkedHashMap<>();
 *     data.put("count", 0);
 *     return WireResponse.wire("counter", data, "/api/wire/counter");
 * }
 *
 * // 部分更新
 * public Response update(Request request) {
 *     WireRequest wireReq = WireRequest.from(request);
 *     Map<String, Object> data = wireReq.getMergedData();
 *     data.put("count", toInt(data.get("count")) + 1);
 *     return WireResponse.update("counter", data, wireReq.getSections());
 * }
 *
 * // 处理中触发跳转（如创建完成后跳到详情页）
 * public Response save(Request request) {
 *     int newId = service.create(...);
 *     return WireResponse.redirect("/items/" + newId);
 * }
 *
 * // 错误响应
 * public Response update(Request request) {
 *     if (!Auth.check()) {
 *         return WireResponse.error(401, "Unauthorized", "/login");
 *     }
 *     if (!hasPermission()) {
 *         return WireResponse.error(403, "无权限执行此操作");
 *     }
 *     ...
 * }
 *
 * // 全能模式：更新 + 跳转 + 自定义效果
 * public Response update(Request request) {
 *     Map<String, Object> data = ...;
 *     List<String> sections = ...;
 *     return WireResponse.of("counter", data, sections)
     *         .withRedirect("/dashboard")
     *         .withDispatch("item-updated", Map.of("id", 42))
     *         .build();
 * }
 * }</pre>
 *
 * <h3>响应格式</h3>
 * <p>所有 Wire JSON 响应（update/redirect/error/of）统一格式：
 * <pre>{@code
 * {
 *   "sections": {"content": "<div>...</div>"},   // 可选
 *   "snapshot": "base64编码状态",                  // 可选
 *   "effects": {                                  // 可选
 *     "redirect": "/login",
 *     "dispatch": [{"name": "event-name", "data": {...}}]
 *   },
 *   "error": {"status": 401, "message": "..."}     // 可选,仅 error 响应
 * }
 * }</pre>
 */
public class WireResponse {

    private final Map<String, String> sections = new LinkedHashMap<>();
    private String snapshot;
    private final Map<String, Object> effects = new LinkedHashMap<>();
    private Integer errorStatus;
    private String errorMessage;

    // ===== 构造方法 =====

    private WireResponse() {
    }

    // ===== 静态工厂方法 =====

    /**
     * 初始页面渲染：渲染模板 + 注入 Wire 资源（wire.js + snapshot + updateUrl）。
     * <p>
     * 返回完整的 HTML 页面，包含：
     * <ul>
     *   <li>模板渲染结果（带 wire:section 标记）</li>
     *   <li>{@code <script wire:config>} 配置（含 snapshot 和 updateUrl）</li>
     *   <li>{@code <script src="/static/wire.js">} 前端运行时</li>
     * </ul>
     *
     * @param templateName 模板名
     * @param data         模板数据
     * @param updateUrl    Wire 更新 URL（如 "/api/wire/counter"）
     * @return HTML 响应
     */
    public static Response wire(String templateName, Map<String, Object> data, String updateUrl) {
        String html = WireManager.renderWirePage(templateName, data, updateUrl);
        return ResponseBuilder.html(html);
    }

    /**
     * 初始页面渲染（使用默认 update URL: /wire/update）。
     */
    public static Response wire(String templateName, Map<String, Object> data) {
        return wire(templateName, data, "/wire/update");
    }

    /**
     * 部分更新响应：渲染指定 section 并返回 JSON。
     * <p>
     * 前端 wire.js 收到后，自动替换对应 section 的 DOM 内容，并更新 snapshot。
     *
     * @param templateName 模板名
     * @param data         更新后的模板数据
     * @param sections     需要渲染的 section 名列表（为空时使用模板默认 section）
     * @return JSON 响应（含 section HTML + 新 snapshot）
     */
    public static Response update(String templateName, Map<String, Object> data, List<String> sections) {
        if (sections == null || sections.isEmpty()) {
            sections = WireManager.getSectionNames(templateName);
        }
        Map<String, String> sectionHtml = WireManager.renderSections(templateName, sections, data);
        String snapshot = WireManager.encodeSnapshot(data);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sections", sectionHtml);
        result.put("snapshot", snapshot);
        result.put("effects", new LinkedHashMap<>());
        return ResponseBuilder.json(result);
    }

    /**
     * Wire 重定向：返回 JSON，前端 wire.js 自动执行 {@code window.location.href = url}。
     * <p>
     * 用于在 Wire 处理过程中触发跳转（如创建完成后跳到详情页）。
     * 默认立即跳转。如需延迟（如先显示"保存成功"提示再跳转），使用 {@link #redirect(String, int)}。
     *
     * @param url 目标 URL
     * @return JSON 响应（含 effects.redirect）
     */
    public static Response redirect(String url) {
        return redirect(url, 0);
    }

    /**
     * Wire 重定向（带延迟）：返回 JSON，前端 wire.js 在延迟指定毫秒后跳转。
     * <p>
     * 典型场景：保存成功后先显示提示消息，延迟 1~2 秒再跳转。
     * <pre>{@code
     * // 1.5 秒后跳转
     * return WireResponse.redirect("/items/" + newId, 1500);
     * }</pre>
     *
     * @param url     目标 URL
     * @param delayMs 延迟毫秒数（0 = 立即跳转）
     * @return JSON 响应（含 effects.redirect 对象）
     */
    public static Response redirect(String url, int delayMs) {
        Map<String, Object> redirectInfo = new LinkedHashMap<>();
        redirectInfo.put("url", url);
        redirectInfo.put("delay", delayMs);

        Map<String, Object> effects = new LinkedHashMap<>();
        effects.put("redirect", redirectInfo);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sections", new LinkedHashMap<>());
        result.put("snapshot", "");
        result.put("effects", effects);
        return ResponseBuilder.json(result);
    }

    /**
     * Wire 错误响应：返回指定状态码的 JSON。
     * <p>
     * 前端 wire.js 对 401 会自动读取 redirect 字段跳转登录页；
     * 其他状态码会打印错误到控制台。
     *
     * @param status  HTTP 状态码（401=未认证, 403=无权限, 500=服务器错误）
     * @param message 错误消息
     * @return JSON 响应
     */
    public static Response error(int status, String message) {
        return ResponseBuilder.error(status, message);
    }

    /**
     * Wire 错误响应（带重定向 URL）。
     * <p>
     * 用于认证过期场景：返回 401 + redirect URL，前端自动跳转登录页。
     *
     * @param status   HTTP 状态码
     * @param message  错误消息
     * @param redirect 重定向 URL（如 "/login"）
     * @return JSON 响应
     */
    public static Response error(int status, String message, String redirect) {
        return ResponseBuilder.error(status, message, redirect);
    }

    // ===== 全能构建器 =====

    /**
     * 全能构建器：通过链式调用组合 sections + snapshot + redirect + dispatch + error。
     * <p>
     * 示例：
     * <pre>{@code
     * return WireResponse.of("counter", data, sections)
     *     .redirect("/dashboard")
     *     .build();
     * }</pre>
     *
     * @param templateName 模板名（用于渲染 sections）
     * @param data         模板数据
     * @param sections     需要渲染的 section 名列表
     * @return 链式构建器
     */
    public static WireResponse of(String templateName, Map<String, Object> data, List<String> sections) {
        WireResponse resp = new WireResponse();
        if (sections == null || sections.isEmpty()) {
            sections = WireManager.getSectionNames(templateName);
        }
        resp.sections.putAll(WireManager.renderSections(templateName, sections, data));
        resp.snapshot = WireManager.encodeSnapshot(data);
        return resp;
    }

    /**
     * 空构建器：不渲染任何 section，仅用于纯 redirect / error 场景。
     */
    public static WireResponse of() {
        return new WireResponse();
    }

    // ===== 链式方法 =====

    /**
     * 添加重定向效果（立即跳转）。
     */
    public WireResponse withRedirect(String url) {
        return withRedirect(url, 0);
    }

    /**
     * 添加重定向效果（延迟跳转）。
     * <p>
     * 典型场景：保存成功后先显示提示，延迟 N 毫秒再跳转。
     * <pre>{@code
     * return ctx.responseOf()
     *     .withRedirect("/dashboard", 1500)  // 1.5 秒后跳转
     *     .build();
     * }</pre>
     *
     * @param url     目标 URL
     * @param delayMs 延迟毫秒数（0 = 立即跳转）
     */
    public WireResponse withRedirect(String url, int delayMs) {
        Map<String, Object> redirectInfo = new LinkedHashMap<>();
        redirectInfo.put("url", url);
        redirectInfo.put("delay", delayMs);
        effects.put("redirect", redirectInfo);
        return this;
    }

    /**
     * 添加 dispatch 事件效果（前端通过 window.addEventListener 监听）。
     */
    public WireResponse withDispatch(String eventName, Object eventData) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dispatchList = (List<Map<String, Object>>) effects.get("dispatch");
        if (dispatchList == null) {
            dispatchList = new ArrayList<>();
            effects.put("dispatch", dispatchList);
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("name", eventName);
        event.put("data", eventData);
        dispatchList.add(event);
        return this;
    }

    /**
     * 设置错误状态（构建器会返回非 200 的 JSON 响应）。
     */
    public WireResponse withError(int status, String message) {
        this.errorStatus = status;
        this.errorMessage = message;
        return this;
    }

    /**
     * 构建最终的 HTTP Response。
     */
    public Response build() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sections", sections);
        result.put("snapshot", snapshot != null ? snapshot : "");
        result.put("effects", effects);

        if (errorStatus != null) {
            // 带错误的响应：使用 errorStatus 作为 HTTP 状态码
            Map<String, Object> errorInfo = new LinkedHashMap<>();
            errorInfo.put("status", errorStatus);
            errorInfo.put("message", errorMessage);
            result.put("error", errorInfo);
            return ResponseBuilder.error(errorStatus, errorMessage != null ? errorMessage : "Error");
        }

        return ResponseBuilder.json(result);
    }

    // ===== 数据访问方法（向后兼容） =====

    /**
     * 创建一个只包含 section 更新的响应（传统方式，向后兼容）。
     */
    public static WireResponse of(Map<String, String> sections, Map<String, Object> data) {
        WireResponse resp = new WireResponse();
        if (sections != null) {
            resp.sections.putAll(sections);
        }
        resp.snapshot = WireManager.encodeSnapshot(data);
        return resp;
    }

    /**
     * 创建一个包含 section 更新和重定向效果的响应（传统方式，向后兼容）。
     */
    public static WireResponse of(Map<String, String> sections, Map<String, Object> data, String redirectUrl) {
        WireResponse resp = of(sections, data);
        if (redirectUrl != null) {
            resp.effects.put("redirect", redirectUrl);
        }
        return resp;
    }

    public Map<String, String> getSections() {
        return sections;
    }

    public String getSnapshot() {
        return snapshot;
    }

    public Map<String, Object> getEffects() {
        return effects;
    }

    /**
     * 转为 Map（用于 JSON 序列化，向后兼容）。
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sections", sections);
        map.put("snapshot", snapshot != null ? snapshot : "");
        map.put("effects", effects);
        return map;
    }
}
