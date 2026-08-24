package com.weacsoft.jaravel.vendor.wire;

import com.weacsoft.jaravel.vendor.core.view.View;
import com.weacsoft.jaravel.vendor.json.Json;
import com.weacsoft.jaravel.vendor.jblade.BladeEngine;
import com.weacsoft.jaravel.vendor.jblade.view.BladeView;
import com.weacsoft.jaravel.vendor.jblade.view.ViewFacade;

import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** Wire 模式标记，设置到 BladeContext 中触发 @yield 的 section 包装 */
    public static final String WIRE_MODE_KEY = "__wire_mode";

    /** Wire 更新 URL 标记，设置到 BladeContext 中供模板使用 */
    public static final String WIRE_UPDATE_URL_KEY = "__wire_update_url";

    /** 组件名 → 模板名 注册表。由 WireAutoConfiguration 从配置初始化。 */
    private static final Map<String, String> COMPONENT_TEMPLATES = new java.util.concurrent.ConcurrentHashMap<>();

    /** 注册组件名到模板名的映射。 */
    public static void registerComponentTemplate(String name, String template) {
        if (name != null && template != null) COMPONENT_TEMPLATES.put(name, template);
    }

    /**
     * 解析组件名对应的模板名。未注册时返回 null。
     *
     * @param name 组件名(如 "toast")
     * @return 模板名(如 "components.toast"),未注册时返回 null
     */
    public static String resolveComponentTemplate(String name) {
        return COMPONENT_TEMPLATES.get(name);
    }

    /** wire.js 的外部引用路径（注入到 HTML 中的 script src），默认 /static/wire.js */
    private static String jsPath = "/static/wire.js";

    /** wire.js 在 classpath 中的路径（用于读取内容） */
    private static final String WIRE_JS_CLASSPATH = "/static/wire.js";

    /** 是否自动注入 wire.js 的 script 标签，默认 true（向后兼容） */
    private static boolean autoInjectJs = true;

    /** Wire section 排除列表：在这些列表中的 section 不会被 wire:section 标记包裹 */
    private static final Set<String> excludedSections = new LinkedHashSet<>();

    private static BladeEngine engine;

    /**
     * 设置 BladeEngine 实例（由 ServiceProvider 或配置类调用）。
     */
    public static void setEngine(BladeEngine engine) {
        WireManager.engine = engine;
    }

    /**
     * 获取 BladeEngine 实例。
     * <p>
     * 优先使用 {@link #setEngine(BladeEngine)} 显式设置的实例；
     * 若未设置，则从 {@link ViewFacade} 取当前激活的 {@link BladeView} 并复用其底层引擎。
     * 这样无需手动把引擎塞进 WireManager，对齐"从 Facade 直接拿"的设计。
     */
    public static BladeEngine getEngine() {
        if (engine == null) {
            try {
                View view = ViewFacade.getView();
                if (view instanceof BladeView) {
                    engine = ((BladeView) view).getEngine();
                } else {
                    throw new RuntimeException(
                            "当前激活的 View 实现不是 BladeView，Wire 模块无法使用（需 Blade 引擎）");
                }
            } catch (RuntimeException e) {
                throw new RuntimeException("Wire 模块未初始化，且无法从 ViewFacade 获取 BladeEngine：" + e.getMessage());
            }
        }
        return engine;
    }

    /**
     * 设置 wire.js 的外部引用路径（注入到 HTML 中的 script src）。
     * <p>
     * 用于自定义静态资源服务路径，例如 CDN 或自定义路由前缀。
     *
     * @param path JS 引用路径，如 "/assets/wire.js"
     */
    public static void setJsPath(String path) {
        WireManager.jsPath = path != null ? path : "/static/wire.js";
    }

    /**
     * 获取 wire.js 的外部引用路径。
     */
    public static String getJsPath() {
        return jsPath;
    }

    /**
     * 设置是否自动注入 wire.js 的 script 标签。
     * <p>
     * 设为 false 后，{@link #injectWireAssets} 只注入 wire:config 配置标签，
     * 开发者需自行在页面中引入 wire.js（可使用 {@link #getWireJsContent()} 获取 JS 内容）。
     *
     * @param autoInject true=自动注入（默认），false=手动控制
     */
    public static void setAutoInjectJs(boolean autoInject) {
        WireManager.autoInjectJs = autoInject;
    }

    /**
     * 是否自动注入 wire.js。
     */
    public static boolean isAutoInjectJs() {
        return autoInjectJs;
    }

    // ===== Section 排除列表 =====

    /**
     * 添加要排除的 section 名称。
     * <p>
     * 排除列表中的 section 在 Wire 模式渲染时不会被 {@code <!--wire:section-start/end-->} 标记包裹，
     * 因此前端 wire.js 不会将其作为可更新区域。
     * <p>
     * 适用于：某些 section/slot 虽然使用了 Blade 的 @section/@yield，但实际上不需要 Wire 更新能力。
     * 不影响 jblade 本身的渲染逻辑。
     *
     * @param sectionNames 要排除的 section 名称
     */
    public static void addExcludedSections(String... sectionNames) {
        if (sectionNames != null) {
            for (String name : sectionNames) {
                if (name != null && !name.isEmpty()) {
                    excludedSections.add(name);
                }
            }
        }
    }

    /**
     * 移除排除的 section 名称。
     *
     * @param sectionName 要移除的 section 名称
     */
    public static void removeExcludedSection(String sectionName) {
        excludedSections.remove(sectionName);
    }

    /**
     * 获取排除列表（不可修改视图）。
     */
    public static Set<String> getExcludedSections() {
        return Collections.unmodifiableSet(excludedSections);
    }

    /**
     * 清空排除列表。
     */
    public static void clearExcludedSections() {
        excludedSections.clear();
    }

    /**
     * 检查指定 section 是否被排除。
     *
     * @param sectionName section 名称
     * @return true=被排除（不生成 wire 标记）
     */
    public static boolean isExcluded(String sectionName) {
        return excludedSections.contains(sectionName);
    }

    /**
     * 从已渲染的 HTML 中移除排除 section 的 wire 标记。
     * <p>
     * 由于不修改 jblade 本身，Wire 模式渲染时所有 @yield 都会生成 wire:section 标记。
     * 此方法在后处理阶段移除排除 section 的标记，使其不被前端 wire.js 识别为可更新区域。
     *
     * @param html 已渲染的 HTML（含 wire:section 标记）
     * @return 移除排除标记后的 HTML
     */
    static String stripExcludedSectionMarkers(String html) {
        if (excludedSections.isEmpty() || html == null) {
            return html;
        }
        String result = html;
        for (String name : excludedSections) {
            // 移除注释标记: <!--wire:section-start:name--> 和 <!--wire:section-end:name-->
            result = result.replace("<!--wire:section-start:" + name + "-->", "");
            result = result.replace("<!--wire:section-end:" + name + "-->", "");
        }
        return result;
    }

    /**
     * 获取 wire.js 的完整 JavaScript 内容。
     * <p>
     * 从 classpath 读取 {@code /static/wire.js} 并返回字符串。开发者可以：
     * <ul>
     *   <li>修改其中的静态资源请求路径后内联到页面</li>
     *   <li>通过自定义路由提供修改后的 JS 内容</li>
     *   <li>配合 {@link #setAutoInjectJs(false)} 实现完全手动控制</li>
     * </ul>
     *
     * @return wire.js 的完整内容
     */
    public static String getWireJsContent() {
        try (InputStream is = WireManager.class.getResourceAsStream(WIRE_JS_CLASSPATH)) {
            if (is == null) {
                throw new RuntimeException("wire.js 未找到: " + WIRE_JS_CLASSPATH);
            }
            byte[] bytes = new byte[is.available()];
            int offset = 0;
            int remaining = bytes.length;
            while (remaining > 0) {
                int read = is.read(bytes, offset, remaining);
                if (read == -1) break;
                offset += read;
                remaining -= read;
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("读取 wire.js 失败: " + WIRE_JS_CLASSPATH, e);
        }
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
            String html = getEngine().render(templateName, wireData);
            // 后处理：移除排除 section 的 wire 标记（不修改 jblade 本身）
            return stripExcludedSectionMarkers(html);
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
     * <p>
     * 支持两种 section 来源，<b>不改动 jblade 的 @section 语义</b>：
     * <ol>
     *   <li>已注册的 {@code @section} 名（如 {@code content}）：直接渲染对应区块。</li>
     *   <li>非 {@code @section} 名（如内联在 content 中的 {@code wire:section="list"}）：
     *       回退渲染 {@code content}，再按 {@code wire:section} 属性截取子区域返回。</li>
     * </ol>
     * 这样前端 {@code Wire.refresh(['list'])} 能精准刷新内联区块，而无需模板额外声明 {@code @section('list')}。
     *
     * @param templateName 模板名
     * @param sectionNames 需要渲染的 section 名列表（可混用 @section 名与 wire:section 属性名）
     * @param data         模板数据
     * @return section 名 → HTML 内容
     */
    public static Map<String, String> renderSections(String templateName, List<String> sectionNames, Map<String, Object> data) {
        try {
            List<String> registered = getEngine().getSectionNames(templateName);
            Map<String, String> result = new LinkedHashMap<>();
            for (String name : sectionNames) {
                if (registered.contains(name)) {
                    result.put(name, getEngine().renderSection(templateName, name, data));
                } else {
                    // 非 @section：渲染 content 后按 wire:section 属性截取
                    String contentHtml = getEngine().renderSection(templateName, "content", data);
                    result.put(name, extractWireSection(contentHtml, name));
                }
            }
            return result;
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
            String json = Json.stringify(cleanData);
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
            return Json.parseToMap(json);
        } catch (Exception e) {
            throw new RuntimeException("解码快照失败", e);
        }
    }

    // ===== HTML 注入 =====

    /**
     * 将 Wire 资源（snapshot + updateUrl + wire.js）注入到 HTML 中。
     * <p>
     * 默认行为受 {@link #isAutoInjectJs()} 控制：为 true 时注入 wire.js script 标签，
     * 为 false 时只注入 wire:config 配置标签（开发者需自行引入 wire.js）。
     *
     * @param html      渲染后的 HTML
     * @param updateUrl Wire 更新 URL
     * @param snapshot  Base64 编码的快照
     * @return 注入 Wire 资源后的 HTML
     */
    public static String injectWireAssets(String html, String updateUrl, String snapshot) {
        return injectWireAssets(html, updateUrl, snapshot, autoInjectJs);
    }

    /**
     * 将 Wire 资源注入到 HTML 中，显式指定是否注入 wire.js。
     * <p>
     * 在 {@code </body>} 标签前插入：
     * <pre>{@code
     * <script type="application/json" wire:config data-wire-update="/api/wire/admin">base64snapshot</script>
     * <script src="/static/wire.js"></script>   <!-- 仅当 injectJs=true 时注入 -->
     * }</pre>
     *
     * @param html      渲染后的 HTML
     * @param updateUrl Wire 更新 URL
     * @param snapshot  Base64 编码的快照
     * @param injectJs  是否注入 wire.js 的 script 标签
     * @return 注入 Wire 资源后的 HTML
     */
    public static String injectWireAssets(String html, String updateUrl, String snapshot, boolean injectJs) {
        StringBuilder sb = new StringBuilder();
        sb.append("<script type=\"application/json\" wire:config")
                .append(" data-wire-update=\"").append(escapeHtml(updateUrl)).append("\"")
                .append(" wire:snapshot=\"").append(escapeHtml(snapshot)).append("\"></script>\n");
        if (injectJs) {
            sb.append("<script src=\"").append(escapeHtml(jsPath)).append("\"></script>");
        }

        String lowerHtml = html.toLowerCase();
        int bodyCloseIndex = lowerHtml.lastIndexOf("</body>");
        if (bodyCloseIndex >= 0) {
            return html.substring(0, bodyCloseIndex) + sb + "\n" + html.substring(bodyCloseIndex);
        }
        return html + "\n" + sb;
    }

    /**
     * 完整的 Wire 初始渲染：渲染模板 + 注入 Wire 资源。
     * <p>
     * 默认行为受 {@link #isAutoInjectJs()} 控制。
     *
     * @param templateName 模板名
     * @param data         模板数据
     * @param updateUrl    Wire 更新 URL
     * @return 完整的 HTML（含 wire:section 包装 + Wire 资源注入）
     */
    public static String renderWirePage(String templateName, Map<String, Object> data, String updateUrl) {
        return renderWirePage(templateName, data, updateUrl, autoInjectJs);
    }

    /**
     * 完整的 Wire 初始渲染：渲染模板 + 注入 Wire 资源，显式指定是否注入 wire.js。
     *
     * @param templateName 模板名
     * @param data         模板数据
     * @param updateUrl    Wire 更新 URL
     * @param injectJs     是否注入 wire.js 的 script 标签
     * @return 完整的 HTML（含 wire:section 包装 + Wire 资源注入）
     */
    public static String renderWirePage(String templateName, Map<String, Object> data, String updateUrl, boolean injectJs) {
        String html = renderForWire(templateName, data);
        String snapshot = encodeSnapshot(data);
        return injectWireAssets(html, updateUrl, snapshot, injectJs);
    }

    private static String escapeHtml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * 从已渲染的 HTML 中按 {@code wire:section="name"} 属性提取对应块的内部 HTML。
     * <p>
     * 用于支持「不引入第二个 @section」的场景：列表等内容通过
     * {@code <div wire:section="list">...</div>} 直接内联在 content 里，
     * 前端 {@code Wire.refresh(['list'])} 只请求这一块。后端渲染 content 后，
     * 用本方法按属性截取该块返回，<b>全程不改动 jblade 的 @section 语义</b>。
     *
     * @param html 已渲染（含 wire:section 标记）的 HTML
     * @param name wire:section 属性值
     * @return 该 wire:section 块的内部 HTML；若找不到返回空串
     */
    public static String extractWireSection(String html, String name) {
        if (html == null || name == null || name.isEmpty()) {
            return "";
        }
        // 1) 先定位带 wire:section="name" 的开始标签，拿到标签名与标签结束位置。
        //    注意：这里只匹配开始标签，绝不能用 (.*?)</tag> 去「顺手」匹配结束标签，
        //    因为 section 内部几乎必然存在同名嵌套标签（如 div 里套很多 div），
        //    非贪婪匹配会停在第一个 </div>，导致返回的 HTML 被截断（分页器、列表行全部丢失）。
        String openRegex = "(?s)<([a-zA-Z][a-zA-Z0-9]*)\\b[^>]*?\\bwire:section\\s*=\\s*[\"']"
                + Pattern.quote(name) + "[\"'][^>]*?>";
        Matcher m = Pattern.compile(openRegex).matcher(html);
        if (!m.find()) {
            return "";
        }

        String tag = m.group(1);
        int contentStart = m.end();

        // 自闭合标签（<div ... />）没有内容
        String openTag = html.substring(m.start(), m.end());
        if (openTag.endsWith("/>")) {
            return "";
        }

        // 2) 从内容起点开始做深度计数，找到与开始标签配对的结束标签。
        Pattern tagPattern = Pattern.compile(
                "(?s)<(/?)" + Pattern.quote(tag) + "\\b[^>]*?>",
                Pattern.CASE_INSENSITIVE);
        Matcher tm = tagPattern.matcher(html);

        int depth = 1;
        int pos = contentStart;
        while (tm.find(pos)) {
            boolean isClosing = "/".equals(tm.group(1));
            String full = tm.group();

            if (isClosing) {
                depth--;
                if (depth == 0) {
                    return html.substring(contentStart, tm.start());
                }
            } else if (!full.endsWith("/>")) {
                // 自闭合的同名标签不增加深度
                depth++;
            }
            pos = tm.end();
        }

        // 3) 结构不闭合（模板异常）时，退化为返回剩余全部内容，
        //    这比返回被截断的片段或空串更安全。
        return html.substring(contentStart);
    }
}
