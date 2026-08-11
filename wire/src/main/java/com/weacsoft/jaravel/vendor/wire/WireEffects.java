package com.weacsoft.jaravel.vendor.wire;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wire 请求级临时组件下发队列(ThreadLocal)。
 * <p>
 * 在任意 WireController.action 方法中通过 {@link #push(String, Map)} 添加待下发的临时组件,
 * update() 处理完毕后通过 {@link #drain()} 统一取走,放入响应 JSON 的 effects.components 字段。
 * <p>
 * 语义对应 Livewire 的 wire:push / wire:navigate 中的 component 下发机制,
 * 临时组件(toast/confirm/alert 等)不再依赖 WireComponents 注册表,
 * 而是像业务组件一样,通过自己的 WireController 子类 render() 返回 HTML 片段下发。
 */
public final class WireEffects {

    private WireEffects() {
    }

    private static final ThreadLocal<List<Map<String, Object>>> QUEUE = ThreadLocal.withInitial(ArrayList::new);

    /**
     * 向当前请求的临时组件队列添加一个组件。
     *
     * @param name   组件名(对应已注册的组件模板名,如 "toast")
     * @param params 组件参数
     */
    public static void push(String name, Map<String, Object> params) {
        if (name == null || name.isEmpty()) return;
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", name);
        entry.put("params", params != null ? params : new LinkedHashMap<>());
        QUEUE.get().add(entry);
    }

    /**
     * 取走当前请求的所有待下发临时组件,清空队列。
     *
     * @return 组件列表(每项含 name 和 params 字段)
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> drain() {
        List<Map<String, Object>> list = QUEUE.get();
        List<Map<String, Object>> result = new ArrayList<>(list);
        list.clear();
        return result;
    }

    /**
     * 清空当前请求的队列(不取走)。
     */
    public static void clear() {
        QUEUE.get().clear();
    }
}