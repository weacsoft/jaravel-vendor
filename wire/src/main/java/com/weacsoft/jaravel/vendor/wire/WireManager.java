package com.weacsoft.jaravel.vendor.wire;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weacsoft.jaravel.vendor.jblade.BladeEngine;

import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/**
 * Wire 管理器：核心工具类，负责 Wire 模式的渲染、section 提取和快照编解码。
 * <p>
 * <b>设计理念</b>：WireManager 是无状态的工具类，所有状态通过 snapshot 在客户端流转。
 * 服务端不需要维护组件实例，天然支持水平扩展。
 * <p>
 * <b>两种使用模式</b>：
 * <ol>
 *   <li><b>无感模式</b>（推荐）：使用 {@code Response.wire(template, data, updateUrl)} 渲染页面，
 *       {@code @section} 自动成为可更新区域，无需编写 WireComponent 子类。</li>
 *   <li><b>显式模式</b>：在控制器中调用 {@code WireManager.renderSections()} 手动渲染指定 section，
 *       配合 {@code Response.wireUpdate()} 返回更新响应。</li>
 * </ol>
 */
public class WireManager {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** Wire 模式标记，设置到 BladeContext 中触发 @yield 的 section 包装 */
    public static final String WIRE_MODE_KEY = "__wire_mode";

    /** Wire 更新 URL 标记，设置到 BladeContext 中供模板使用 */
    public static final String WIRE_UPDATE_URL_KEY = "__wire_update_url";

    private static BladeEngine engine;

    /**
     * 设置 BladeEngine 实例（由 ServiceProvider 或配置类调用）。
     */
    public static void setEngine(BladeEngine engine) {
        WireManager.engine = engine;
    }

    /**
     * 获取 BladeEngine 实例。
     */
    public static BladeEngine getEngine() {
        if (engine == null) {
            throw new RuntimeException("Wire 模块未初始化：BladeEngine 未设置");
        }
        return engine;
    }

    // ===== 渲染方法 =====

    /**
     * 以 Wire 模式渲染模板（完整页面）。
     * <p>
     * 设置 {@code __wire_mode = true} 到模板上下文，使 {@code @yield} 输出被
     * {@code <div wire:section="name">} 包裹，从而前端可以定位并局部替换。
     *
     * @param templateName 模板名
     * @param data         模板数据
     * @return 渲染后的完整 HTML（含 wire:section 包装）
     */
    public static String renderForWire(String templateName, Map<String, Object> data) {
        try {
            Map<String, Object> wireData = new LinkedHashMap<>();
            if (data != null) {
                wireData.putAll(data);
            }
            wireData.put(WIRE_MODE_KEY, true);
            return getEngine().render(templateName, wireData);
        } catch (Exception e) {
            throw new RuntimeException("Wire 渲染失败: " + templateName, e);
        }
    }

    /**
     * 渲染指定 section 的内容（不含布局）。
     * <p>
     * 加载子模板并调用 init() 注册 section renderer，
     * 然后只执行指定 section 的 renderer，不渲染完整页面。
     *
     * @param templateName 模板名
     * @param sectionName  section 名
     * @param data         模板数据
     * @return section 的 HTML 内容
     */
    public static String renderSection(String templateName, String sectionName, Map<String, Object> data) {
        try {
            return getEngine().renderSection(templateName, sectionName, data);
        } catch (Exception e) {
            throw new RuntimeException("Wire section 渲染失败: " + templateName + "::" + sectionName, e);
        }
    }

    /**
     * 批量渲染多个 section（高效：只加载和初始化模板一次）。
     *
     * @param templateName 模板名
     * @param sectionNames 需要渲染的 section 名列表
     * @param data         模板数据
     * @return section 名 → HTML 内容
     */
    public static Map<String, String> renderSections(String templateName, List<String> sectionNames, Map<String, Object> data) {
        try {
            return getEngine().renderSections(templateName, sectionNames, data);
        } catch (Exception e) {
            throw new RuntimeException("Wire sections 渲染失败: " + templateName, e);
        }
    }

    /**
     * 获取模板中所有已注册的 section 名。
     *
     * @param templateName 模板名
     * @return section 名列表
     */
    public static List<String> getSectionNames(String templateName) {
        try {
            return getEngine().getSectionNames(templateName);
        } catch (Exception e) {
            throw new RuntimeException("获取 section 名列表失败: " + templateName, e);
        }
    }

    // ===== 快照编解码 =====

    /**
     * 将数据 Map 编码为 Base64 JSON 快照。
     *
     * @param data 组件状态数据
     * @return Base64 编码的 JSON 字符串
     */
    public static String encodeSnapshot(Map<String, Object> data) {
        try {
            Map<String, Object> cleanData = new LinkedHashMap<>();
            if (data != null) {
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    if (!entry.getKey().startsWith("__wire")) {
                        cleanData.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            String json = objectMapper.writeValueAsString(cleanData);
            return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("编码快照失败", e);
        }
    }

    /**
     * 从 Base64 JSON 快照解码出数据 Map。
     *
     * @param base64 Base64 编码的 JSON 字符串
     * @return 组件状态数据
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> decodeSnapshot(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            String json = new String(bytes, StandardCharsets.UTF_8);
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("解码快照失败", e);
        }
    }

    // ===== HTML 注入 =====

    /**
     * 将 Wire 资源（snapshot + updateUrl + wire.js）注入到 HTML 中。
     * <p>
     * 在 {@code </body>} 标签前插入：
     * <pre>{@code
     * <script type="application/json" wire:config data-wire-update="/api/wire/admin">base64snapshot</script>
     * <script src="/static/wire.js"></script>
     * }</pre>
     *
     * @param html      渲染后的 HTML
     * @param updateUrl Wire 更新 URL
     * @param snapshot  Base64 编码的快照
     * @return 注入 Wire 资源后的 HTML
     */
    public static String injectWireAssets(String html, String updateUrl, String snapshot) {
        String wireConfig = "<script type=\"application/json\" wire:config" +
                " data-wire-update=\"" + escapeHtml(updateUrl) + "\"" +
                " wire:snapshot=\"" + escapeHtml(snapshot) + "\"></script>\n" +
                "<script src=\"/static/wire.js\"></script>";

        String lowerHtml = html.toLowerCase();
        int bodyCloseIndex = lowerHtml.lastIndexOf("</body>");
        if (bodyCloseIndex >= 0) {
            return html.substring(0, bodyCloseIndex) + wireConfig + "\n" + html.substring(bodyCloseIndex);
        }
        return html + "\n" + wireConfig;
    }

    /**
     * 完整的 Wire 初始渲染：渲染模板 + 注入 Wire 资源。
     *
     * @param templateName 模板名
     * @param data         模板数据
     * @param updateUrl    Wire 更新 URL
     * @return 完整的 HTML（含 wire:section 包装 + Wire 资源注入）
     */
    public static String renderWirePage(String templateName, Map<String, Object> data, String updateUrl) {
        String html = renderForWire(templateName, data);
        String snapshot = encodeSnapshot(data);
        return injectWireAssets(html, updateUrl, snapshot);
    }

    private static String escapeHtml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
