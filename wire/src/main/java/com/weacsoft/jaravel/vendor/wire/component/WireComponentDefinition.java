package com.weacsoft.jaravel.vendor.wire.component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wire 命名组件定义：一个「名称 → 普通 Blade 模板」的绑定。
 *
 * <p>命名组件是<b>临时事务型</b>片段（消息提示、确认框、引导浮层等），
 * 与 Wire 的 section 局部刷新<b>无关</b>：它没有 snapshot、不参与 {@code wire:section} 更新、
 * 不需要 update URL，只是一段「后端下发 → 前端挂载 → 走完生命周期 → 自动移除」的普通模板。</p>
 *
 * <h3>模板契约</h3>
 * 模板是<b>无布局的片段</b>（不要 {@code @extends}），可选地包含一段生命周期脚本：
 * <pre>{@code
 * <div class="toast toast--{{ $level }}">{{ $message }}</div>
 * <script wire:lifecycle>
 *     function onCreate(el, wire) {}    // 内容已从服务端取回、尚未插入 DOM
 *     function onStart(el, wire) {}     // 已插入 DOM 且其余初始化完成
 *     function onStop(el, wire) {}      // 开始移除 DOM（可在此播放退场动画）
 *     function onDestroy(el, wire) {}   // DOM 已移除
 * </script>
 * }</pre>
 * 四个函数<b>全部可选</b>；模板内主动调用 {@code wire.stop()} 表示「展示完成，移除我」。
 *
 * @see WireComponents 注册表与请求级队列
 * @see WireComponentRenderer 渲染与生命周期脚本拆分
 */
public final class WireComponentDefinition {

    private final String name;
    private final String template;
    private final Map<String, Object> defaults;

    public WireComponentDefinition(String name, String template, Map<String, Object> defaults) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Wire 命名组件的 name 不能为空");
        }
        if (template == null || template.trim().isEmpty()) {
            throw new IllegalArgumentException("Wire 命名组件 [" + name + "] 的模板名不能为空");
        }
        this.name = name.trim();
        this.template = template.trim();
        this.defaults = defaults == null || defaults.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(defaults));
    }

    public WireComponentDefinition(String name, String template) {
        this(name, template, null);
    }

    /** 组件名（后端 {@code responseComponent(name, ...)} 与前端实例作用域共用）。 */
    public String name() {
        return name;
    }

    /** 模板名（如 {@code components.toast}）。 */
    public String template() {
        return template;
    }

    /** 默认参数：调用方未提供的键使用此处的值。 */
    public Map<String, Object> defaults() {
        return defaults;
    }

    @Override
    public String toString() {
        return "WireComponentDefinition{name=" + name + ", template=" + template + "}";
    }
}
