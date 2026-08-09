package com.weacsoft.jaravel.vendor.wire.navigation;

import com.weacsoft.jaravel.vendor.jblade.WireAnchorRewriter;
import com.weacsoft.jaravel.vendor.json.Json;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wire 导航渲染器：将渲染好的 HTML 提取 section、计算 diff、生成 JSON 响应。
 *
 * <h3>工作流程</h3>
 * <ol>
 *   <li>从 HTML 中按 {@code <!--wire:section-start:NAME-->...<!--wire:section-end:NAME-->} 提取每个 section；</li>
 *   <li>计算每个 section 的 FNV-1a 32-bit hash —— 对父 section 计算 hash 时会剥离其内嵌的
 *       子 section（含起止注释），因此父 section 不会因为子 section 内容不同而被误判为变更；</li>
 *   <li>对比客户端上报的 hash（来自 WireContext），只保留变化过的 section；</li>
 *   <li>抽取锚点值：{@code <title>} 文本、{@code class} 等<b>注释非法位置</b>由标记属性定位，
 *       服务端直接下发渲染后的完整新值（见 {@link WireAnchorRewriter}）；</li>
 *   <li>生成 JSON 响应：{@code {"sections":{...},"hashes":{...},"anchors":{...},"title":"...","url":"..."}}</li>
 * </ol>
 *
 * <p>Wire 发送的是 <b>diff</b>（只含变化的 section），
 * 不是全量 section 信封。未变化的 section 完全不传输，前端也不触碰对应 DOM。
 */
public class WireRenderer {

    /** Wire section 标记正则（不 trim 内容，与服务端/前端 hash 计算保持一致） */
    private static final Pattern SECTION_PATTERN = Pattern.compile(
            "<!--wire:section-start:([a-zA-Z0-9_-]+)-->(.*?)<!--wire:section-end:\\1-->",
            Pattern.DOTALL);

    /** 用于剥离所有嵌套的 section 标记（起止注释本身），保留非嵌套的文本/HTML 结构。 */
    private static final Pattern NESTED_SECTION_STRIP = Pattern.compile(
            "<!--wire:section-start:[a-zA-Z0-9_-]+-->.*?<!--wire:section-end:[a-zA-Z0-9_-]+-->",
            Pattern.DOTALL);

    /** title 提取正则 */
    private static final Pattern TITLE_PATTERN = Pattern.compile(
            "<title>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * 从渲染后的 HTML 中提取 section diff。
     *
     * @param html 带 wire:section 标记的渲染后 HTML
     * @param url  当前页面 URL
     * @return JSON 字符串，格式见类 javadoc
     */
    public static String renderDiff(String html, String url) {
        if (html == null || html.isEmpty()) {
            return "{}";
        }

        // 提取 title（剥掉 title yield 包裹的 wire 标记，避免 JS 用 payload.title 赋值时把注释带进标签页）
        String title = null;
        Matcher tm = TITLE_PATTERN.matcher(html);
        if (tm.find()) {
            title = tm.group(1)
                    .replaceAll("<!--wire:section-start:title-->", "")
                    .replaceAll("<!--wire:section-end:title-->", "")
                    .trim();
        }

        // 提取所有 section 并计算 hash（含嵌套子 section 时，父 section 的 hash 基于剥离了子 section 后的内容）
        Map<String, String> allSections = new LinkedHashMap<>();
        Map<String, String> allHashes = new LinkedHashMap<>();
        Set<String> sectionNames = new LinkedHashSet<>();
        Matcher sm = SECTION_PATTERN.matcher(html);
        while (sm.find()) {
            String name = sm.group(1);
            String content = sm.group(2);
            allSections.put(name, content);
            sectionNames.add(name);
            allHashes.put(name, hashNormalized(content, sectionNames, name));
        }

        // 对比客户端 hash，只保留变化的 section
        Map<String, String> incomingHashes = WireContext.getIncomingHashes();
        Map<String, String> changed = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : allSections.entrySet()) {
            String name = e.getKey();
            String newHash = allHashes.get(name);
            String oldHash = incomingHashes.get(name);
            if (newHash != null && !newHash.equals(oldHash)) {
                changed.put(name, e.getValue());
            }
        }

        // 抽取「注释非法位置」的锚点当前值（<title> 文本、class 等属性值）。
        // 这些位置无法用注释定位，改由标记属性 + 服务端下发完整新值的方式更新。
        // 数据量极小（通常只有标题与少量 class），故不做 diff，整体下发由前端幂等应用。
        Map<String, String> anchors = WireAnchorRewriter.extract(html);

        // 构建 JSON 响应
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sections", changed);
        result.put("hashes", allHashes);
        if (!anchors.isEmpty()) {
            result.put("anchors", anchors);
        }
        if (title != null && !title.isEmpty()) {
            result.put("title", title);
        }
        if (url != null && !url.isEmpty()) {
            result.put("url", url);
        }

        return Json.stringify(result);
    }

    /**
     * 计算整页所有 section 的 hash（仅提取与哈希，不做 diff 对比）。
     * 用于在【首屏整页】响应中注入 {@code window.__wireHashes}，
     * 让前端首屏直接使用服务端口径的 hash，从而保证导航时
     * 服务端能精确判断哪些 section 未变化（最小 diff）。
     */
    public static Map<String, String> computeHashes(String html) {
        Map<String, String> hashes = new LinkedHashMap<>();
        if (html == null || html.isEmpty()) return hashes;
        Set<String> sectionNames = new LinkedHashSet<>();
        Matcher sm = SECTION_PATTERN.matcher(html);
        while (sm.find()) sectionNames.add(sm.group(1));
        sm = SECTION_PATTERN.matcher(html);
        while (sm.find()) {
            hashes.put(sm.group(1), hashNormalized(sm.group(2), sectionNames, sm.group(1)));
        }
        return hashes;
    }

    /**
     * 规范化 hash 输入：把 name 内的嵌套子 section（含其起止注释）替换为一个恒定占位符
     * {@code WIRE_SECTION_PLACEHOLDER}，使父 section 的 hash 与嵌套子 section 的具体内容
     * 解耦。嵌套子 section 各自有独立的 hash 键，客户端/服务端都会根据子 section 的 hash 决定是否下发；
     * 父 section 只在它自己独有内容（不含子 section）真正变化时才被认为变更。
     *
     * <p>对于叶子 section（其内部不再嵌套其它 section），本方法退化为直接返回原 content。
     *
     * @param content    section 原始内容
     * @param allNames   整页所有已知 section 名（用于判断哪些是 name 的直接子 section）
     * @param name       当前正在计算的 section 名（把自己排除，避免误替换同名注释）
     * @return 规范化后的字符串
     */
    static String normalizeNestedSections(String content, Set<String> allNames, String name) {
        if (content == null || content.isEmpty()) return content;
        // 只有当 content 里含有其它 section 的起始注释时才需要做替换（否则直接返回原引用）
        for (String s : allNames) {
            if (!s.equals(name) && content.indexOf("<!--wire:section-start:" + s + "-->") >= 0) {
                return doNormalize(content, allNames, name);
            }
        }
        return content;
    }

    private static String doNormalize(String content, Set<String> allNames, String name) {
        StringBuilder out = new StringBuilder(content.length());
        Matcher m = SECTION_PATTERN.matcher(content);
        int last = 0;
        while (m.find()) {
            String childName = m.group(1);
            if (childName.equals(name)) continue; // 不替换自身
            if (!allNames.contains(childName)) continue; // 保守：仅在已登记的 section 集里替换
            out.append(content, last, m.start());
            out.append("WIRE_SECTION_PLACEHOLDER:").append(childName);
            last = m.end();
        }
        out.append(content, last, content.length());
        return out.length() == content.length() ? content : out.toString();
    }

    private static String hashNormalized(String content, Set<String> allNames, String name) {
        return hash(normalizeNestedSections(content, allNames, name));
    }

    /** FNV-1a 32-bit hash（与前端 wire-navigate.js 完全相同的算法）。 */
    static String hash(String content) {
        if (content == null || content.isEmpty()) {
            return "00000000";
        }
        int h = 0x811c9dc5;
        for (int i = 0; i < content.length(); i++) {
            h ^= content.charAt(i);
            h *= 0x01000193;
        }
        return String.format("%08x", h);
    }
}