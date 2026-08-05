package com.weacsoft.jaravel.vendor.wire.component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wire 命名组件注册表 + 请求级下发队列。
 *
 * <h3>1. 注册（启动期，一次性）</h3>
 * 与 Blade 自定义指令的注册方式对齐——在 Wire 初始化时把「名称 → 模板」登记进来：
 * <pre>{@code
 * WireComponents.register("toast",   "components.toast");
 * WireComponents.register("confirm", "components.confirm", Map.of("okText", "确定"));
 * }</pre>
 * 也可以在 {@code application.yml} 里声明，由自动装配完成注册：
 * <pre>{@code
 * jaravel:
 *   wire:
 *     components:
 *       toast: components.toast
 *       confirm: components.confirm
 * }</pre>
 *
 * <h3>2. 下发（请求期）</h3>
 * 后端任意位置把组件"推"给当前请求，前端无感挂载：
 * <pre>{@code
 * // Wire 更新链路：写入 effects.components
 * return WireService.from(request, "wire-demo", "/api/wire/demo")
 *         .action("save", c -> { ...; c.responseComponent("toast", Map.of("message", "已保存")); })
 *         .responseUpdate();
 *
 * // 普通页面链路：由 WireOutlet 中间件写入首屏 bootstrap
 * WireComponents.push("toast", Map.of("message", "欢迎回来"));
 * return ResponseBuilder.view("pjax.home", data);
 * }</pre>
 *
 * <p><b>队列是请求级的</b>（{@link ThreadLocal}）：由 {@link WireOutlet} 中间件在请求结束时
 * 兜底清理，避免线程池复用造成串号；{@link #drain()} 取走后即清空。</p>
 */
public final class WireComponents {

    private static final Logger log = LoggerFactory.getLogger(WireComponents.class);

    /** 名称 → 定义。启动期写入，运行期只读。 */
    private static final Map<String, WireComponentDefinition> REGISTRY = new ConcurrentHashMap<>();

    /** 请求级待下发队列（尚未渲染）。 */
    private static final ThreadLocal<List<Pending>> PENDING = new ThreadLocal<>();

    private WireComponents() {
    }

    // ===== 注册表 =====

    /**
     * 注册命名组件。
     *
     * @param name     组件名（前后端共用的唯一标识）
     * @param template 普通 Blade 片段模板名（如 {@code components.toast}）
     */
    public static void register(String name, String template) {
        register(new WireComponentDefinition(name, template));
    }

    /**
     * 注册命名组件并指定默认参数。
     */
    public static void register(String name, String template, Map<String, Object> defaults) {
        register(new WireComponentDefinition(name, template, defaults));
    }

    /**
     * 注册命名组件（完整定义）。同名重复注册会覆盖并打 WARN，便于发现命名冲突。
     */
    public static void register(WireComponentDefinition definition) {
        if (definition == null) {
            return;
        }
        WireComponentDefinition old = REGISTRY.put(definition.name(), definition);
        if (old != null && !old.template().equals(definition.template())) {
            log.warn("[wire-component] 组件名 {} 被重复注册：{} -> {}（后者生效）",
                    definition.name(), old.template(), definition.template());
        }
    }

    /** 批量注册（名称 → 模板名）。 */
    public static void registerAll(Map<String, String> nameToTemplate) {
        if (nameToTemplate == null) {
            return;
        }
        for (Map.Entry<String, String> e : nameToTemplate.entrySet()) {
            register(e.getKey(), e.getValue());
        }
    }

    public static boolean has(String name) {
        return name != null && REGISTRY.containsKey(name);
    }

    public static WireComponentDefinition get(String name) {
        return name == null ? null : REGISTRY.get(name);
    }

    /** 已注册的组件名集合（不可修改视图）。 */
    public static Set<String> names() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }

    public static void unregister(String name) {
        if (name != null) {
            REGISTRY.remove(name);
        }
    }

    /** 清空注册表（主要用于测试）。 */
    public static void clear() {
        REGISTRY.clear();
    }

    // ===== 请求级队列 =====

    /**
     * 把一个命名组件下发给当前请求。
     * <p>
     * 未注册的名称<b>立即抛异常</b>（而非静默丢弃），使拼错的组件名在开发期就暴露。
     *
     * @param name   组件名
     * @param params 本次参数（会与定义的默认参数合并，本次值优先）
     */
    public static void push(String name, Map<String, Object> params) {
        WireComponentDefinition def = get(name);
        if (def == null) {
            throw new IllegalArgumentException(
                    "未注册的 Wire 命名组件: " + name + "。已注册: " + names()
                            + "。请在启动期调用 WireComponents.register(\"" + name + "\", \"模板名\") "
                            + "或在 application.yml 的 jaravel.wire.components 下声明。");
        }
        List<Pending> list = PENDING.get();
        if (list == null) {
            list = new ArrayList<>(2);
            PENDING.set(list);
        }
        list.add(new Pending(def, params));
    }

    /** 下发一个无参数的命名组件。 */
    public static void push(String name) {
        push(name, null);
    }

    /** 当前请求是否有待下发的组件。 */
    public static boolean hasPending() {
        List<Pending> list = PENDING.get();
        return list != null && !list.isEmpty();
    }

    /**
     * 取走并渲染当前请求的全部待下发组件，同时清空队列。
     * <p>
     * 单个组件渲染失败时记录 ERROR 并跳过该组件——一条提示的模板错误不应让整个页面 500，
     * 但错误会完整出现在日志里，不会被静默吞掉。
     *
     * @return 可直接序列化的载荷列表；无待下发组件时返回空列表
     */
    public static List<Map<String, Object>> drain() {
        List<Pending> list = PENDING.get();
        PENDING.remove();
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (Pending pending : list) {
            try {
                result.add(WireComponentRenderer.render(pending.definition, pending.params).toMap());
            } catch (RuntimeException e) {
                log.error("[wire-component] 组件 {}（模板 {}）渲染失败，本次已跳过",
                        pending.definition.name(), pending.definition.template(), e);
            }
        }
        return result;
    }

    /** 丢弃当前请求的待下发组件（中间件在请求结束时兜底清理，防止线程复用串号）。 */
    public static void clearPending() {
        PENDING.remove();
    }

    // ===== 内部 =====

    private static final class Pending {
        final WireComponentDefinition definition;
        final Map<String, Object> params;

        Pending(WireComponentDefinition definition, Map<String, Object> params) {
            this.definition = definition;
            this.params = params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
        }
    }
}
