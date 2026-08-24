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

    /** dispatch 事件队列:action 中通过 {@link #dispatch(String, Object)} 添加,随响应下发到前端触发 window 事件 */
    private static final ThreadLocal<List<Map<String, Object>>> DISPATCH_QUEUE = ThreadLocal.withInitial(ArrayList::new);

    /** 重定向地址队列:action 中通过 {@link #redirect(String)} 显式指定,使本次 update 响应携带 effects.redirect */
    private static final ThreadLocal<String> REDIRECT = ThreadLocal.withInitial(() -> null);

    /** URL 变更队列:action 中通过 {@link #pushUrl(String)} 指定,使本次 update 响应携带 effects.url
     *  (前端仅用 history.pushState 改变地址栏,不发起请求、不刷新页面)。对应「点击修改后 URL 变深链」场景。 */
    private static final ThreadLocal<String> PUSH_URL = ThreadLocal.withInitial(() -> null);

    /** 返回 URL 队列:action 中通过 {@link #backUrl(String)} 指定,
     * 使本次 update 响应携带 effects.backUrl(前端 dialog 取消时用于还原地址栏)。 */
    private static final ThreadLocal<String> BACK_URL = ThreadLocal.withInitial(() -> null);

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
     * 派发一个前端事件:更新响应会以 {@code effects.dispatch} 形式下发,
     * 前端 wire.js 收到后执行 {@code window.dispatchEvent(new CustomEvent(name, {detail:data}))}。
     * <p>
     * 典型用途:在 action 中打开/关闭对话框(如 {@code WireEffects.dispatch("admin-dialog-open", null)})。
     *
     * @param name 事件名(前端需自行监听)
     * @param data 事件负载(可为 null)
     */
    public static void dispatch(String name, Object data) {
        if (name == null || name.isEmpty()) return;
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", name);
        entry.put("data", data);
        DISPATCH_QUEUE.get().add(entry);
    }

    /**
     * 取走当前请求的所有待派发事件,清空队列。
     *
     * @return 事件列表(每项含 name 和 data 字段)
     */
    public static List<Map<String, Object>> drainDispatches() {
        List<Map<String, Object>> list = DISPATCH_QUEUE.get();
        List<Map<String, Object>> result = new ArrayList<>(list);
        list.clear();
        return result;
    }

    /**
     * 清空当前请求的队列(不取走)。
     */
    public static void clear() {
        QUEUE.get().clear();
        DISPATCH_QUEUE.get().clear();
        REDIRECT.remove();
        PUSH_URL.remove();
        BACK_URL.remove();
    }

    /**
     * 请求一次重定向:在 action 中调用,使本次 update 响应携带 {@code effects.redirect},
     * 前端据此整页跳转到指定 URL(常用于保存成功后返回列表、关闭对话框)。
     * <p>
     * 与 {@link #getRedirectUrl} 类回调的区别:本方法是「按 action 显式」的,
     * 不会作用于 {@code edit()} / {@code add()} 等只下发组件、不希望整页跳转的 action;
     * 若未调用本方法,则回退到 {@code getRedirectUrl(Request)}(通常为 null = 不跳转)。
     *
     * @param url 重定向目标 URL(空值忽略)
     */
    public static void redirect(String url) {
        if (url == null || url.isEmpty()) return;
        REDIRECT.set(url);
    }

    /**
     * 取走本次请求的重定向地址(无则 null),并清空。
     *
     * @return 重定向 URL 或 null
     */
    public static String drainRedirect() {
        String r = REDIRECT.get();
        REDIRECT.remove();
        return r;
    }

    /**
     * 请求一次 URL 变更:在 action 中调用,使本次 update 响应携带 {@code effects.url},
     * 前端据此仅用 {@code history.pushState} 改变地址栏(生成可分享深链),不发起请求、不刷新页面。
     * <p>
     * 典型场景:列表点击「修改」后,地址栏变为 {@code /admin/admin/change?id=5} 而不重新加载页面。
     * 与 {@link #redirect(String)} 的区别:redirect 会触发整页/透明导航跳转;pushUrl 只改地址栏文本。
     *
     * @param url 目标 URL(空值忽略)
     */
    public static void pushUrl(String url) {
        if (url == null || url.isEmpty()) return;
        PUSH_URL.set(url);
    }

    /**
     * 取走本次请求的 URL 变更地址(无则 null),并清空。
     *
     * @return 变更后的 URL 或 null
     */
    public static String drainPushUrl() {
        String u = PUSH_URL.get();
        PUSH_URL.remove();
        return u;
    }

    /**
     * 设置「返回 URL」:在 action 中调用,使本次 update 响应携带 {@code effects.backUrl},
     * 供前端 dialog 取消/关闭时用于还原地址栏(不发起请求、不刷新页面)。
     * <p>
     * 典型场景:点击「修改」后地址栏变为 /admin/admin/change?id=5,
     * 取消时回到 /admin/admin。backUrl 由后端决定,前端只是消费,不与 dialog 模板耦合。
     *
     * @param url 返回列表页 URL(空值忽略)
     */
    public static void backUrl(String url) {
        if (url == null || url.isEmpty()) return;
        BACK_URL.set(url);
    }

    /**
     * 取走本次请求的返回 URL(无则 null),并清空。
     *
     * @return 返回 URL 或 null
     */
    public static String drainBackUrl() {
        String u = BACK_URL.get();
        BACK_URL.remove();
        return u;
    }

    /**
     * 读取本次请求的返回 URL(不消耗,不清空)。
     * 用于需要在多个位置读取但不想重复 drain 的场景。
     *
     * @return 返回 URL 或 null
     */
    public static String getBackUrl() {
        return BACK_URL.get();
    }
}