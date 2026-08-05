package com.weacsoft.jaravel.vendor.wire.component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一个命名组件实例的下发载荷：前端据此在 outlet 中挂载一份<b>互相隔离</b>的实例。
 *
 * <p>序列化后形如：</p>
 * <pre>{@code
 * {
 *   "id":     "wc-toast-3",          // 实例唯一 id（同名组件多开时用于隔离）
 *   "name":   "toast",               // 组件名
 *   "html":   "<div ...>...</div>",  // 已剥离生命周期脚本的纯 HTML
 *   "script": "function onStart(...){...}", // 生命周期脚本源码（可为空串）
 *   "params": {"message":"已保存"}    // 合并后的参数，回传给前端 wire.params
 * }
 * }</pre>
 *
 * <p><b>为什么把脚本单独拆出来</b>：通过 {@code innerHTML} 插入的 {@code <script>} 不会被浏览器执行，
 * 且即便执行也共享全局作用域，多个同名实例会互相覆盖函数定义。拆出来后由前端用
 * {@code new Function} 逐实例求值，每个实例得到独立闭包，天然隔离。</p>
 */
public final class WireComponentPayload {

    private final String id;
    private final String name;
    private final String html;
    private final String script;
    private final Map<String, Object> params;

    public WireComponentPayload(String id, String name, String html, String script, Map<String, Object> params) {
        this.id = id;
        this.name = name;
        this.html = html != null ? html : "";
        this.script = script != null ? script : "";
        this.params = params != null ? params : new LinkedHashMap<>();
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String html() {
        return html;
    }

    public String script() {
        return script;
    }

    public Map<String, Object> params() {
        return params;
    }

    /**
     * 转为可直接 JSON 序列化的 Map（写入 {@code effects.components} 或首屏 bootstrap）。
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("html", html);
        map.put("script", script);
        map.put("params", params);
        return map;
    }

    @Override
    public String toString() {
        return "WireComponentPayload{id=" + id + ", name=" + name + ", htmlLength=" + html.length() + "}";
    }
}
