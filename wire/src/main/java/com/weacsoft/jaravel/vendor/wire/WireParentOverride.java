package com.weacsoft.jaravel.vendor.wire;

import java.util.HashMap;
import java.util.Map;

/**
 * Wire 运行时 @extends 父模板覆盖。
 * <p>
 * Wire 模块提供的全局、线程安全的父模板名覆盖机制：当 Wire 请求需要切换子模板的父布局
 * 时(如直访走 layouts.mdui.form、wire 走 layouts.mdui.form.dialog),在渲染前注册覆盖,
 * 渲染完成后清除。
 * <p>
 * 该覆盖在 BladeEngine.initInheritanceChain 中被读取,优先于模板中 @extends 的字面量。
 */
public final class WireParentOverride {

    private WireParentOverride() {
    }

    private static final ThreadLocal<Map<String, String>> OVERRIDES = ThreadLocal.withInitial(HashMap::new);

    /**
     * 注册模板的父模板覆盖。
     *
     * @param templateName 子模板名(如 "mdui.admin.admin.item")
     * @param newParent    新的父模板名(如 "layouts.mdui.form.dialog")
     */
    public static void register(String templateName, String newParent) {
        if (templateName == null || templateName.isEmpty()) return;
        if (newParent == null || newParent.isEmpty()) return;
        OVERRIDES.get().put(templateName, newParent);
    }

    /**
     * 查询指定模板的父模板覆盖(可为 null)。
     */
    public static String get(String templateName) {
        Map<String, String> overrides = OVERRIDES.get();
        if (overrides == null) return null;
        return overrides.get(templateName);
    }

    /**
     * 清除当前线程的所有覆盖注册(渲染完成后调用)。
     */
    public static void clear() {
        OVERRIDES.get().clear();
    }
}