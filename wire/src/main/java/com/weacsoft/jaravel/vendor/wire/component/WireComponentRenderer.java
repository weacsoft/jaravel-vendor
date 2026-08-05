package com.weacsoft.jaravel.vendor.wire.component;

import com.weacsoft.jaravel.vendor.wire.WireManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 命名组件渲染器：把「名称 + 参数」渲染成可下发给前端的 {@link WireComponentPayload}。
 *
 * <p>渲染走的是<b>普通 Blade 渲染</b>（{@code engine.render}），
 * 不设置 {@code __wire_mode}、不产生 {@code wire:section} 标记——命名组件不参与局部刷新。</p>
 *
 * <h3>生命周期脚本的拆分</h3>
 * 模板里的 {@code <script wire:lifecycle>...</script>} 会被整段取出并从 HTML 中移除，
 * 单独放进 payload 的 {@code script} 字段。原因有二：
 * <ol>
 *   <li>通过 {@code innerHTML} 插入的 {@code <script>} 浏览器<b>不会执行</b>；</li>
 *   <li>即便执行也是全局作用域，同一页面挂载多个同名组件时函数定义会互相覆盖。
 *       拆出来后由前端逐实例用 {@code new Function} 求值，每个实例一份独立闭包，实现隔离。</li>
 * </ol>
 */
public final class WireComponentRenderer {

    /** 实例序号，保证同名组件多开时 id 唯一。 */
    private static final AtomicLong SEQ = new AtomicLong();

    /** 匹配 {@code <script wire:lifecycle> ... </script>}（大小写不敏感、跨行）。 */
    private static final Pattern LIFECYCLE_SCRIPT = Pattern.compile(
            "(?is)<script\\b[^>]*\\bwire:lifecycle\\b[^>]*>(.*?)</script\\s*>");

    /** id 中的非法字符（组件名可能含点号等）。 */
    private static final Pattern UNSAFE_ID_CHARS = Pattern.compile("[^a-zA-Z0-9_-]");

    private WireComponentRenderer() {
    }

    /**
     * 按组件名渲染。
     *
     * @param name   已注册的组件名
     * @param params 本次参数
     * @return 下发载荷
     * @throws IllegalArgumentException 名称未注册
     */
    public static WireComponentPayload render(String name, Map<String, Object> params) {
        WireComponentDefinition def = WireComponents.get(name);
        if (def == null) {
            throw new IllegalArgumentException("未注册的 Wire 命名组件: " + name
                    + "。已注册: " + WireComponents.names());
        }
        return render(def, params);
    }

    /**
     * 按定义渲染。
     *
     * @param definition 组件定义
     * @param params     本次参数（与默认参数合并，本次值优先）
     * @return 下发载荷
     */
    public static WireComponentPayload render(WireComponentDefinition definition, Map<String, Object> params) {
        String id = nextId(definition.name());

        Map<String, Object> merged = new LinkedHashMap<>(definition.defaults());
        if (params != null) {
            merged.putAll(params);
        }

        // 模板可用变量：$wireId / $wireName（用于作用域化 DOM id 与 CSS）
        Map<String, Object> data = new LinkedHashMap<>(merged);
        data.put("wireId", id);
        data.put("wireName", definition.name());

        String rendered;
        try {
            rendered = WireManager.getEngine().render(definition.template(), data);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Wire 命名组件渲染失败: " + definition.name()
                    + "（模板 " + definition.template() + "）", e);
        }

        StringBuilder script = new StringBuilder();
        String html = extractLifecycleScripts(rendered, script);

        return new WireComponentPayload(id, definition.name(), html.trim(), script.toString(), merged);
    }

    /**
     * 生成实例唯一 id，形如 {@code wc-toast-7}。
     */
    static String nextId(String name) {
        String safe = UNSAFE_ID_CHARS.matcher(name == null ? "c" : name).replaceAll("-");
        return "wc-" + safe + "-" + SEQ.incrementAndGet();
    }

    /**
     * 取出全部 {@code <script wire:lifecycle>} 的脚本体，并返回移除这些标签后的 HTML。
     *
     * @param html      渲染结果
     * @param scriptOut 脚本体输出（多段按出现顺序拼接）
     * @return 剥离脚本后的 HTML
     */
    static String extractLifecycleScripts(String html, StringBuilder scriptOut) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        Matcher m = LIFECYCLE_SCRIPT.matcher(html);
        StringBuilder cleaned = new StringBuilder();
        int last = 0;
        while (m.find()) {
            cleaned.append(html, last, m.start());
            String body = m.group(1);
            if (body != null && !body.trim().isEmpty()) {
                if (scriptOut.length() > 0) {
                    scriptOut.append('\n');
                }
                scriptOut.append(body);
            }
            last = m.end();
        }
        if (last == 0) {
            return html;
        }
        cleaned.append(html, last, html.length());
        return cleaned.toString();
    }
}
