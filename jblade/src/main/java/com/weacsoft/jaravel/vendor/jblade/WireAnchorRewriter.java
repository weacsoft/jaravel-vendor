package com.weacsoft.jaravel.vendor.jblade;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wire section 锚点改写器：把「HTML 注释非法位置」的 section 锚点改写为标记属性。
 *
 * <h3>为什么需要它</h3>
 * <p>
 * {@link BladeTemplate#yieldSection} 会为每个 {@code @yield} 输出
 * {@code <!--wire:section-start:NAME-->…<!--wire:section-end:NAME-->} 注释锚点，
 * 前端据此定位并替换 DOM。但 HTML 规范里有两类位置<b>不解析注释</b>：
 * <ol>
 *   <li><b>原始文本元素</b>（{@code <title> <textarea> <script> <style>}）：
 *       内部不存在注释节点，{@code <!--…-->} 会被当成纯文本原样显示 —— 浏览器标签页会出现
 *       {@code <!--wire:section-start:title-->我的页面} 这种脏内容；</li>
 *   <li><b>标签属性值内部</b>（如 {@code <body class="@yield('bodyClass')">}）：
 *       注释同样退化成属性字符串的一部分，直接污染 class / content 等属性值。</li>
 * </ol>
 *
 * <h3>解决办法</h3>
 * <p>
 * 在渲染出口对整段 HTML 做一次线性扫描（不引入任何 HTML 解析库）：
 * 处于上述两类上下文中的锚点注释被<b>剥离</b>，改为在<b>所属元素的开始标签</b>上追加标记属性：
 * <pre>
 *   &lt;title&gt;&lt;!--wire:section-start:title--&gt;订单列表&lt;!--wire:section-end:title--&gt;&lt;/title&gt;
 *   ↓
 *   &lt;title wire:section-text="title"&gt;订单列表&lt;/title&gt;
 *
 *   &lt;body class="&lt;!--wire:section-start:bodyClass--&gt;page-order&lt;!--wire:section-end:bodyClass--&gt;"&gt;
 *   ↓
 *   &lt;body class="page-order" wire:section-attr="class:bodyClass"&gt;
 * </pre>
 * 属性是任何位置都合法的，因此锚点不再破坏页面。位于正常元素内容区（注释合法）的锚点<b>保持原样</b>，
 * 既有的 TreeWalker diff 机制完全不受影响。
 *
 * <h3>前后端如何协作</h3>
 * <p>
 * 标记属性只回答「这个元素的哪一部分由哪个 section 驱动」，具体新值由服务端在导航 diff 里
 * 通过 {@link #extract(String)} 抽取后整体下发（见 {@code WireRenderer}），
 * 前端按标记直接赋值。因此形如 {@code class="base @yield('x') tail"} 的
 * 「部分插值」也能正确更新 —— 服务端给的是渲染完成后的完整值，前端无需理解静态片段。
 *
 * <p>本类线程安全（无状态），且对不含锚点的 HTML 走快速返回，正常页面几乎零开销。
 */
public final class WireAnchorRewriter {

    /** 标记属性：元素的文本内容由这些 section 驱动（空格分隔，可多个） */
    public static final String ATTR_SECTION_TEXT = "wire:section-text";
    /** 标记属性：元素的某些属性由 section 驱动，token 形如 {@code 属性名:section名}（空格分隔，可多个） */
    public static final String ATTR_SECTION_ATTR = "wire:section-attr";

    /** 锚点探针（快速判断整段 HTML 是否需要处理） */
    private static final String MARKER_PROBE = "<!--wire:section-";

    /** 锚点注释（start / end 通用） */
    private static final Pattern MARKER = Pattern.compile("<!--wire:section-(?:start|end):([a-zA-Z0-9_-]+)-->");

    /** HTML 原始文本元素：内部不解析注释 */
    private static final Set<String> RAW_TEXT_TAGS = Set.of("title", "textarea", "script", "style");

    private WireAnchorRewriter() {
    }

    /**
     * 改写非法位置的 section 锚点。
     *
     * @param html 渲染后的 HTML（可为 null）
     * @return 改写后的 HTML；不含锚点时原样返回同一个引用
     */
    public static String rewrite(String html) {
        if (html == null || html.isEmpty() || html.indexOf(MARKER_PROBE) < 0) {
            return html;
        }
        return process(html, null);
    }

    /**
     * 从<b>已改写</b>的 HTML 中抽取锚点当前值，用于随导航 diff 下发给前端。
     *
     * <p>返回键的格式：
     * <ul>
     *   <li>{@code text:SECTION} → 该元素完整的文本内容；</li>
     *   <li>{@code attr:属性名:SECTION} → 该元素该属性完整的值。</li>
     * </ul>
     *
     * @param html 已改写的 HTML
     * @return 锚点键 → 当前值；无锚点时返回空 Map
     */
    public static Map<String, String> extract(String html) {
        Map<String, String> anchors = new LinkedHashMap<>();
        if (html == null || html.isEmpty()) {
            return anchors;
        }
        if (!html.contains(ATTR_SECTION_TEXT) && !html.contains(ATTR_SECTION_ATTR)) {
            return anchors;
        }
        process(html, anchors);
        return anchors;
    }

    // ===================== 内部实现 =====================

    /**
     * 线性扫描 HTML。
     *
     * @param html    输入
     * @param anchors 非 null 时进入「抽取模式」，把锚点当前值写入其中
     * @return 改写后的 HTML
     */
    private static String process(String html, Map<String, String> anchors) {
        int n = html.length();
        StringBuilder out = new StringBuilder(n + 64);
        int i = 0;
        while (i < n) {
            char c = html.charAt(i);
            if (c != '<') {
                out.append(c);
                i++;
                continue;
            }
            // 注释 / DOCTYPE / CDATA：元素内容区的注释是合法的，原样保留
            if (html.startsWith("<!", i)) {
                int close = html.indexOf('>', i);
                if (html.startsWith("<!--", i)) {
                    close = html.indexOf("-->", i + 4);
                    close = close < 0 ? -1 : close + 2;
                }
                if (close < 0) {
                    out.append(html, i, n);
                    break;
                }
                out.append(html, i, close + 1);
                i = close + 1;
                continue;
            }
            // 结束标签
            if (html.startsWith("</", i)) {
                int gt = html.indexOf('>', i);
                if (gt < 0) {
                    out.append(html, i, n);
                    break;
                }
                out.append(html, i, gt + 1);
                i = gt + 1;
                continue;
            }
            // 开始标签
            if (i + 1 < n && isNameStart(html.charAt(i + 1))) {
                i = handleStartTag(html, i, out, anchors);
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /**
     * 处理一个开始标签（含其属性；若是原始文本元素，连同其文本内容一并处理）。
     *
     * @return 下一个待扫描位置
     */
    private static int handleStartTag(String html, int start, StringBuilder out, Map<String, String> anchors) {
        int n = html.length();
        int p = start + 1;
        int nameStart = p;
        while (p < n && isNameChar(html.charAt(p))) {
            p++;
        }
        String rawTagName = html.substring(nameStart, p);
        String tagName = rawTagName.toLowerCase();

        StringBuilder attrs = new StringBuilder();
        // 属性值中发现的锚点：token 形如 class:bodyClass
        List<String> attrTokens = new ArrayList<>();
        // 已解析出的属性（小写名 → 清洗后的值），抽取模式下用于读取现值
        Map<String, String> attrValues = new LinkedHashMap<>();
        boolean selfClosing = false;

        while (p < n) {
            char ch = html.charAt(p);
            if (ch == '>') {
                p++;
                break;
            }
            if (ch == '/' && p + 1 < n && html.charAt(p + 1) == '>') {
                selfClosing = true;
                p += 2;
                break;
            }
            if (Character.isWhitespace(ch)) {
                attrs.append(ch);
                p++;
                continue;
            }
            // 兜底：锚点注释被误写在属性区（如 <div @yield('attrs')>），整段跳过。
            // 注意必须在属性名解析之前处理 —— 注释以 "-->" 结尾，若按属性名扫描会在 '>' 处
            // 提前截断标签，导致后续 HTML 结构被吞掉。
            if (html.startsWith("<!--", p)) {
                int commentEnd = html.indexOf("-->", p + 4);
                if (commentEnd < 0) {
                    p = n;
                    break;
                }
                String comment = html.substring(p, commentEnd + 3);
                Matcher mk = MARKER.matcher(comment);
                if (mk.matches()) {
                    // 非锚点注释保留在属性区（极少见，交给浏览器容错）
                    p = commentEnd + 3;
                    continue;
                }
                attrs.append(comment);
                p = commentEnd + 3;
                continue;
            }

            // ---- 属性名 ----
            int an = p;
            while (p < n) {
                char a = html.charAt(p);
                if (Character.isWhitespace(a) || a == '=' || a == '>'
                        || (a == '/' && p + 1 < n && html.charAt(p + 1) == '>')) {
                    break;
                }
                p++;
            }
            String attrName = html.substring(an, p);
            if (attrName.isEmpty()) {
                attrs.append(html.charAt(p));
                p++;
                continue;
            }
            // ---- 可选的 =值 ----
            int q = p;
            while (q < n && Character.isWhitespace(html.charAt(q))) {
                q++;
            }
            if (q < n && html.charAt(q) == '=') {
                q++;
                while (q < n && Character.isWhitespace(html.charAt(q))) {
                    q++;
                }
                char quote = 0;
                String value;
                int valueEnd;
                if (q < n && (html.charAt(q) == '"' || html.charAt(q) == '\'')) {
                    quote = html.charAt(q);
                    int vs = q + 1;
                    int ve = html.indexOf(quote, vs);
                    if (ve < 0) {
                        ve = n;
                    }
                    value = html.substring(vs, ve);
                    valueEnd = Math.min(ve + 1, n);
                } else {
                    int vs = q;
                    while (q < n && !Character.isWhitespace(html.charAt(q)) && html.charAt(q) != '>') {
                        q++;
                    }
                    value = html.substring(vs, q);
                    valueEnd = q;
                }

                String cleaned = value;
                if (value.indexOf(MARKER_PROBE) >= 0) {
                    Set<String> names = new LinkedHashSet<>();
                    cleaned = stripMarkers(value, names);
                    for (String nm : names) {
                        attrTokens.add(attrName + ":" + nm);
                    }
                }
                char qc = quote != 0 ? quote : '"';
                attrs.append(attrName).append('=').append(qc).append(cleaned).append(qc);
                attrValues.put(attrName.toLowerCase(), cleaned);
                p = valueEnd;
            } else {
                attrs.append(attrName);
                attrValues.put(attrName.toLowerCase(), "");
            }
        }

        // ---- 原始文本元素：内部注释非法，剥离并记到开始标签上 ----
        boolean rawText = RAW_TEXT_TAGS.contains(tagName) && !selfClosing;
        String textContent = null;
        int nextIndex = p;
        List<String> textNames = new ArrayList<>();
        if (rawText) {
            int endTag = indexOfEndTag(html, p, tagName);
            String raw = html.substring(p, endTag);
            if (raw.indexOf(MARKER_PROBE) >= 0) {
                Set<String> names = new LinkedHashSet<>();
                textContent = stripMarkers(raw, names);
                textNames.addAll(names);
            } else {
                textContent = raw;
            }
            nextIndex = endTag;
        }

        // ---- 抽取模式：读取已有标记属性的当前值 ----
        if (anchors != null) {
            String attrSpec = attrValues.get(ATTR_SECTION_ATTR);
            if (attrSpec != null && !attrSpec.trim().isEmpty()) {
                for (String token : attrSpec.trim().split("\\s+")) {
                    int colon = token.indexOf(':');
                    if (colon <= 0) {
                        continue;
                    }
                    String target = token.substring(0, colon).toLowerCase();
                    String value = attrValues.get(target);
                    if (value != null) {
                        anchors.put("attr:" + token, value);
                    }
                }
            }
            String textSpec = attrValues.get(ATTR_SECTION_TEXT);
            if (textSpec != null && !textSpec.trim().isEmpty() && textContent != null) {
                for (String name : textSpec.trim().split("\\s+")) {
                    anchors.put("text:" + name, textContent);
                }
            }
        }

        // ---- 输出开始标签（追加标记属性） ----
        out.append('<').append(rawTagName).append(attrs);
        if (!attrTokens.isEmpty()) {
            out.append(' ').append(ATTR_SECTION_ATTR).append("=\"").append(String.join(" ", attrTokens)).append('"');
        }
        if (!textNames.isEmpty()) {
            out.append(' ').append(ATTR_SECTION_TEXT).append("=\"").append(String.join(" ", textNames)).append('"');
        }
        if (selfClosing) {
            out.append(attrs.length() > 0 && Character.isWhitespace(attrs.charAt(attrs.length() - 1)) ? "/" : " /");
        }
        out.append('>');
        if (rawText) {
            out.append(textContent);
        }
        return nextIndex;
    }

    /** 剥离字符串中的全部锚点注释，并收集 section 名。 */
    private static String stripMarkers(String value, Set<String> names) {
        Matcher m = MARKER.matcher(value);
        StringBuilder sb = new StringBuilder(value.length());
        while (m.find()) {
            names.add(m.group(1));
            m.appendReplacement(sb, "");
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** 从 {@code from} 起查找 {@code </tagName} 的 '<' 下标；找不到返回字符串长度。 */
    private static int indexOfEndTag(String html, int from, String tagName) {
        int n = html.length();
        int idx = from;
        while (true) {
            idx = html.indexOf("</", idx);
            if (idx < 0) {
                return n;
            }
            int k = idx + 2;
            int t = 0;
            while (t < tagName.length() && k < n && Character.toLowerCase(html.charAt(k)) == tagName.charAt(t)) {
                k++;
                t++;
            }
            if (t == tagName.length() && (k >= n || html.charAt(k) == '>' || html.charAt(k) == '/'
                    || Character.isWhitespace(html.charAt(k)))) {
                return idx;
            }
            idx += 2;
        }
    }

    private static boolean isNameStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isNameChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == ':' || c == '_';
    }
}
