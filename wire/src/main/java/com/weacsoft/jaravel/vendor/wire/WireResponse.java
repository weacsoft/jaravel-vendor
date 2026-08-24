package com.weacsoft.jaravel.vendor.wire;

import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import com.weacsoft.jaravel.vendor.http.controller.response.ResponseBuilder;

import java.util.*;

/**
 * Wire 响应构建器（兼容层）—— 为旧 API 提供向后兼容。
 * <p>
 * <b>推荐使用</b>：继承 {@link WireController} 并实现 {@code render()} 方法，
 * 由框架自动处理 index/update 流程。
 * <p>
 * 本类仅为兼容旧代码而保留，内部委托 {@link WireManager} 和 {@link WireEffects}。
 *
 * @deprecated 推荐使用 {@link WireController} 替代
 */
@Deprecated
public class WireResponse {

    private final Map<String, String> sections = new LinkedHashMap<>();
    private String snapshot = "";
    private final List<Map<String, Object>> components = new ArrayList<>();

    private WireResponse() {}

    /**
     * 创建一个新的 WireResponse 构建器。
     */
    public static WireResponse of() {
        return new WireResponse();
    }

    /**
     * 快捷方法：渲染指定 section 并返回 JSON 响应。
     *
     * @param templateName 模板名
     * @param data         模板数据
     * @param sectionNames 需要渲染的 section 列表
     * @return JSON 响应
     */
    public static Response update(String templateName, Map<String, Object> data, List<String> sectionNames) {
        Map<String, String> sectionHtmls = WireManager.renderSections(templateName, sectionNames, data);
        String snapshot = WireManager.encodeSnapshot(data);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sections", sectionHtmls);
        result.put("snapshot", snapshot);
        result.put("effects", new LinkedHashMap<>());
        return ResponseBuilder.json(result);
    }

    /**
     * 添加一个临时组件到当前响应。
     *
     * @param name   组件名（如 "toast"）
     * @param params 组件参数
     * @return this（链式调用）
     */
    public WireResponse withComponent(String name, Map<String, Object> params) {
        WireEffects.push(name, params);
        return this;
    }

    /**
     * 设置快照。
     *
     * @param snapshot Base64 编码的快照
     * @return this
     */
    public WireResponse withSnapshot(String snapshot) {
        this.snapshot = snapshot;
        return this;
    }

    /**
     * 添加要刷新的 section。
     *
     * @param name section 名
     * @param html section HTML 内容
     * @return this
     */
    public WireResponse withSection(String name, String html) {
        sections.put(name, html);
        return this;
    }

    /**
     * 构建 JSON 响应。
     *
     * @return JSON 响应
     */
    public Response build() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sections", sections);
        result.put("snapshot", snapshot);

        Map<String, Object> effects = new LinkedHashMap<>();
        // 渲染临时组件
        List<Map<String, Object>> rawComponents = WireEffects.drain();
        if (!rawComponents.isEmpty()) {
            List<Map<String, Object>> rendered = new ArrayList<>();
            for (Map<String, Object> raw : rawComponents) {
                String name = (String) raw.get("name");
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (Map<String, Object>) raw.get("params");
                String templateName = WireManager.resolveComponentTemplate(name);
                if (templateName == null) templateName = name;
                try {
                    String compId = "wc-" + name + "-" + System.nanoTime();
                    Map<String, Object> renderParams = new LinkedHashMap<>();
                    if (params != null) renderParams.putAll(params);
                    renderParams.put("id", compId);
                    String html = WireManager.renderForWire(templateName, renderParams);
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("name", name);
                    entry.put("html", html);
                    entry.put("id", compId);
                    rendered.add(entry);
                } catch (Exception ignored) {}
            }
            if (!rendered.isEmpty()) {
                effects.put("components", rendered);
            }
        }
        result.put("effects", effects);

        return ResponseBuilder.json(result);
    }
}
