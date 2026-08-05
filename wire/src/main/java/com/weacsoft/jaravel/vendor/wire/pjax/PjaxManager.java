package com.weacsoft.jaravel.vendor.wire.pjax;

import com.weacsoft.jaravel.vendor.jblade.BladeEngine;
import com.weacsoft.jaravel.vendor.jblade.BladeTemplate;
import com.weacsoft.jaravel.vendor.json.Json;
import com.weacsoft.jaravel.vendor.wire.WireManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PJAX 核心管理器：整页渲染、局部区域差分、资源注入。
 *
 * <p><b>零侵入原理</b>：模板编译期 jblade 已自动分析出继承链与全部
 * {@code @yield} 区域，渲染时在每个区域外包裹 HTML 注释锚点
 * {@code <!--pjax:start:NAME-->...<!--pjax:end:NAME-->}。
 * 因此模板作者无需书写任何额外标记，也无需声明「哪些区域可切换」。</p>
 *
 * <h3>切换判定</h3>
 * <ol>
 *   <li>客户端请求时通过 {@code X-Pjax-Regions} 上报各区域当前指纹。</li>
 *   <li>服务端渲染目标页面，对每个区域计算指纹。</li>
 *   <li>指纹相同的区域视为未变化，<b>不下发内容也不触碰 DOM</b>，
 *       从而天然保住这些区域内的滚动位置、输入状态、已绑定事件。</li>
 *   <li>布局模板或区域集合不同 → 骨架不兼容，回退整页跳转，保证正确性。</li>
 * </ol>
 */
public final class PjaxManager {

    /** PJAX 前端资源的默认引用路径 */
    private static String jsPath = "/static/pjax.js";

    /** 是否自动注入 pjax.js 的 script 标签 */
    private static boolean autoInjectJs = true;

    /** 不参与区域切换的区域名（这些区域始终保留在页面上，不做 DOM 替换） */
    private static final Set<String> excludedRegions = new LinkedHashSet<>();

    /** 标题区域名：在 head 的 {@code <title>} 内，需特殊处理为文本更新 */
    private static final String TITLE_REGION = "title";

    private PjaxManager() {
    }

    // ===== 配置 =====

    public static void setJsPath(String path) {
        jsPath = (path == null || path.isEmpty()) ? "/static/pjax.js" : path;
    }

    public static String getJsPath() {
        return jsPath;
    }

    public static void setAutoInjectJs(boolean autoInject) {
        autoInjectJs = autoInject;
    }

    public static boolean isAutoInjectJs() {
        return autoInjectJs;
    }

    public static void addExcludedRegions(String... names) {
        if (names == null) {
            return;
        }
        for (String name : names) {
            if (name != null && !name.isEmpty()) {
                excludedRegions.add(name);
            }
        }
    }

    public static Set<String> getExcludedRegions() {
        return java.util.Collections.unmodifiableSet(excludedRegions);
    }

    public static void clearExcludedRegions() {
        excludedRegions.clear();
    }

    // ===== 渲染入口 =====

    /**
     * 整页渲染（首次直接访问页面时使用）。
     *
     * <p>输出完整 HTML，其中：</p>
     * <ul>
     *   <li>每个区域包裹注释锚点，供前端后续定位替换；</li>
     *   <li>{@code <title>} 内的锚点被剥离（避免污染标题文本）；</li>
     *   <li>{@code </body>} 前注入 pjax 配置块与 pjax.js。</li>
     * </ul>
     *
     * @param templateName 模板名
     * @param data         模板数据
     * @param url          当前页面 URL（写入配置块供 popstate 比对）
     * @return 完整 HTML
     */
    public static String renderPage(String templateName, Map<String, Object> data, String url) {
        Rendered rendered = render(templateName, data);
        String html = rendered.html;
        Map<String, String> hashes = new LinkedHashMap<>();
        for (String name : rendered.regionNames) {
            String content = BladeEngine.extractPjaxRegion(html, name);
            hashes.put(name, hash(content));
        }
        hashes.put(TITLE_REGION, hash(rendered.title));
        return injectAssets(html, buildConfig(templateName, rendered.layout, url, hashes));
    }

    /**
     * 局部渲染（从已加载页面切换过来时使用）。
     *
     * @param templateName 模板名
     * @param data         模板数据
     * @param state        客户端上报的 PJAX 状态
     * @return JSON 信封字符串
     */
    public static String renderPartial(String templateName, Map<String, Object> data, PjaxContext.State state) {
        Rendered rendered = render(templateName, data);
        String html = rendered.html;

        Map<String, String> hashes = new LinkedHashMap<>();
        Map<String, String> contents = new LinkedHashMap<>();
        for (String name : rendered.regionNames) {
            String content = BladeEngine.extractPjaxRegion(html, name);
            hashes.put(name, hash(content));
            contents.put(name, content == null ? "" : content);
        }
        hashes.put(TITLE_REGION, hash(rendered.title));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pjax", true);
        payload.put("url", state != null && state.url != null ? state.url : "");
        payload.put("template", templateName);
        payload.put("layout", rendered.layout == null ? "" : rendered.layout);
        payload.put("title", rendered.title == null ? "" : rendered.title);

        if (!isCompatible(rendered, state)) {
            // 骨架不兼容：告知前端做一次真实跳转，保证渲染结果绝对正确
            payload.put("reload", true);
            payload.put("regions", new LinkedHashMap<String, String>());
            payload.put("unchanged", new ArrayList<String>());
            payload.put("hashes", hashes);
            return stringify(payload);
        }

        Map<String, String> clientHashes = state.regionHashes;
        Map<String, String> changed = new LinkedHashMap<>();
        List<String> unchanged = new ArrayList<>();
        for (Map.Entry<String, String> entry : contents.entrySet()) {
            String name = entry.getKey();
            String newHash = hashes.get(name);
            String oldHash = clientHashes.get(name);
            if (newHash != null && newHash.equals(oldHash)) {
                unchanged.add(name);
            } else {
                changed.put(name, entry.getValue());
            }
        }

        payload.put("reload", false);
        payload.put("regions", changed);
        payload.put("unchanged", unchanged);
        payload.put("hashes", hashes);
        return stringify(payload);
    }

    // ===== 内部实现 =====

    /** 一次 PJAX 渲染的中间结果 */
    private static final class Rendered {
        String html;
        String title;
        String layout;
        List<String> regionNames;
    }

    /**
     * 执行 PJAX 模式渲染，并做标题剥离与排除区域清理。
     */
    private static Rendered render(String templateName, Map<String, Object> data) {
        try {
            BladeEngine engine = WireManager.getEngine();
            Map<String, Object> vars = new LinkedHashMap<>();
            if (data != null) {
                vars.putAll(data);
            }
            BladeEngine.PjaxRenderResult result = engine.renderPjax(templateName, vars);

            Rendered rendered = new Rendered();
            rendered.title = result.title == null ? "" : result.title.trim();
            rendered.layout = result.meta != null ? result.meta.parentTemplate : null;

            // <title> 内的注释锚点会被浏览器当作标题文本显示，必须剥离
            String html = stripRegionMarkers(result.html, TITLE_REGION);
            // 排除区域不参与切换，直接去掉锚点
            for (String excluded : excludedRegions) {
                html = stripRegionMarkers(html, excluded);
            }
            rendered.html = html;
            rendered.regionNames = scanRegionNames(html);
            return rendered;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("PJAX 渲染失败: " + templateName, e);
        }
    }

    /**
     * 判断客户端当前骨架能否承接目标页面的局部替换。
     * <p>布局不同或区域集合不同，说明 DOM 锚点对不上，必须整页跳转。</p>
     */
    private static boolean isCompatible(Rendered rendered, PjaxContext.State state) {
        if (state == null || state.regionHashes.isEmpty()) {
            return false;
        }
        String clientLayout = state.layout;
        String serverLayout = rendered.layout;
        if (serverLayout == null || serverLayout.isEmpty()) {
            return false;
        }
        if (!serverLayout.equals(clientLayout)) {
            return false;
        }
        Set<String> clientRegions = new LinkedHashSet<>(state.regionHashes.keySet());
        clientRegions.remove(TITLE_REGION);
        Set<String> serverRegions = new LinkedHashSet<>(rendered.regionNames);
        return clientRegions.equals(serverRegions);
    }

    /**
     * 扫描 HTML 中出现的全部 PJAX 区域名（按出现顺序，去重）。
     * <p>以「实际渲染输出」为准而非编译期元数据，避免条件分支导致的区域集合漂移。</p>
     */
    static List<String> scanRegionNames(String html) {
        List<String> names = new ArrayList<>();
        if (html == null) {
            return names;
        }
        String prefix = BladeTemplate.PJAX_SECTION_START_PREFIX;
        Set<String> seen = new LinkedHashSet<>();
        int idx = 0;
        while ((idx = html.indexOf(prefix, idx)) >= 0) {
            int nameStart = idx + prefix.length();
            int nameEnd = html.indexOf("-->", nameStart);
            if (nameEnd < 0) {
                break;
            }
            String name = html.substring(nameStart, nameEnd);
            if (!name.isEmpty() && seen.add(name)) {
                names.add(name);
            }
            idx = nameEnd + 3;
        }
        return names;
    }

    /**
     * 移除指定区域的注释锚点（保留区域内容本身）。
     */
    static String stripRegionMarkers(String html, String name) {
        if (html == null || name == null || name.isEmpty()) {
            return html;
        }
        return html
                .replace(BladeTemplate.PJAX_SECTION_START_PREFIX + name + "-->", "")
                .replace(BladeTemplate.PJAX_SECTION_END_PREFIX + name + "-->", "");
    }

    /**
     * 计算区域内容指纹：FNV-1a 32 位，输出 8 位十六进制。
     * <p>选用 FNV-1a 而非 MD5：无需加密强度，只需稳定、快速、碰撞率可接受。
     * 前端不需要实现该算法——指纹始终由服务端下发。</p>
     */
    static String hash(String content) {
        if (content == null) {
            return "0";
        }
        int h = 0x811c9dc5;
        for (int i = 0; i < content.length(); i++) {
            h ^= content.charAt(i);
            h *= 0x01000193;
        }
        return Integer.toHexString(h);
    }

    /**
     * 构建注入页面的 PJAX 配置 JSON。
     */
    private static String buildConfig(String template, String layout, String url, Map<String, String> hashes) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("template", template == null ? "" : template);
        config.put("layout", layout == null ? "" : layout);
        config.put("url", url == null ? "" : url);
        config.put("hashes", hashes);
        return stringify(config);
    }

    /**
     * 在 {@code </body>} 前注入 PJAX 配置块与 pjax.js。
     */
    static String injectAssets(String html, String configJson) {
        StringBuilder sb = new StringBuilder();
        sb.append("<script type=\"application/json\" id=\"pjax-config\">")
                .append(escapeForScript(configJson))
                .append("</script>\n");
        if (autoInjectJs) {
            sb.append("<script src=\"").append(escapeHtml(jsPath)).append("\" defer></script>");
        }
        String lower = html.toLowerCase();
        int bodyClose = lower.lastIndexOf("</body>");
        if (bodyClose >= 0) {
            return html.substring(0, bodyClose) + sb + "\n" + html.substring(bodyClose);
        }
        return html + "\n" + sb;
    }

    /**
     * 转义 JSON 中可能提前闭合 script 标签的序列。
     */
    private static String escapeForScript(String json) {
        if (json == null) {
            return "{}";
        }
        return json.replace("</", "<\\/");
    }

    private static String escapeHtml(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String stringify(Object value) {
        try {
            return Json.stringify(value);
        } catch (Exception e) {
            throw new RuntimeException("PJAX 响应序列化失败", e);
        }
    }
}
