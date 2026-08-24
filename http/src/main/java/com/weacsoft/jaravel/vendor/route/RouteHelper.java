package com.weacsoft.jaravel.vendor.route;

import java.util.Map;

/**
 * 路由 / URL 辅助门面，对齐 Laravel 全局辅助函数 {@code route()} 与 {@code url()}。
 *
 * <h3>按路由别名生成 URL（对齐 Laravel {@code route('name')}）</h3>
 * <pre>
 * // 静态（对齐 Laravel 全局辅助函数，可 import static）
 * String url = RouteHelper.route("admin.login");
 * String url2 = RouteHelper.route("user.show", Map.of("id", 1));
 *
 * // 流式（对齐 AppConfig.app().route().route("admin.login")）
 * String url = AppConfig.app().route().route("admin.login");
 * </pre>
 *
 * <h3>按路径生成 URL（对齐 Laravel {@code url('/path')}，不校验是否存在）</h3>
 * <pre>
 * String url = RouteHelper.url("/admin/login");        // "/admin/login"
 * String url = AppConfig.app().route().url("admin/login"); // "/admin/login"
 * </pre>
 *
 * <p>底层按名解析复用 {@link Router#url(String)}，与模板辅助函数 {@code route()} 行为完全一致；
 * 根路由器由 {@link #setRouter(Router)} 在启动期注入（见 RouteServiceProvider）。</p>
 */
public final class RouteHelper {

    /** 根路由器，由 RouteServiceProvider 在启动时通过 {@link #setRouter(Router)} 注入一次 */
    private static volatile Router rootRouter;

    /** 共享实例，供 {@code AppConfig.app().route()} 流式调用 */
    private static final RouteHelper INSTANCE = new RouteHelper();

    private RouteHelper() {
    }

    /**
     * 注入根路由器（框架启动期调用一次，幂等）。
     *
     * @param router 根 {@link Router}
     */
    public static void setRouter(Router router) {
        rootRouter = router;
    }

    /**
     * 返回共享实例，供 {@code AppConfig.app().route().route(...)} / {@code .url(...)} 流式调用。
     */
    public static RouteHelper instance() {
        return INSTANCE;
    }

    // ===== 静态 API（对齐 Laravel 全局辅助函数 route() / url()） =====

    /**
     * 按路由别名解析为 URL（对齐 Laravel {@code route('name')}）。
     *
     * @param name 路由别名（如 {@code "admin.login"}）
     * @return 完整 URL 路径
     */
    public static String route(String name) {
        return route(name, null);
    }

    /**
     * 按路由别名解析为 URL，并替换路径参数（对齐 Laravel {@code route('name', [...])}）。
     *
     * @param name   路由别名（如 {@code "admin.user.show"}）
     * @param params 路径参数（Map 或单值）
     * @return 完整 URL 路径（已替换参数）
     */
    public static String route(String name, Object params) {
        if (rootRouter == null) {
            throw new IllegalStateException(
                    "RouteHelper 尚未初始化，请先由 RouteServiceProvider 调用 RouteHelper.setRouter(router)");
        }
        return rootRouter.url(name, params);
    }

    /**
     * 按路径生成 URL（对齐 Laravel {@code url('/path')}），<b>不校验路由是否存在</b>。
     * <p>
     * 已含协议/域名的绝对地址（含 {@code "://"}）或已以 {@code /} 开头的路径原样返回；
     * 其余情况自动补前导 {@code /}。
     *
     * @param path 路径（如 {@code "admin/login"} 或 {@code "/admin/login"}）
     * @return 标准化后的 URL 路径
     */
    public static String url(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        if (path.startsWith("/") || path.contains("://")) {
            return path;
        }
        return "/" + path;
    }

    // ===== 流式调用说明 =====
    // 本类方法均为静态（对齐 Laravel 全局辅助函数）。由于 Java 不允许同类中静态方法与实例方法
    // 签名相同，流式调用 AppConfig.app().route().route("admin.login") 通过「实例引用调用静态方法」
    // 实现：AppConfig.app().route() 返回 RouteHelper 单例，.route(...) 即解析到上面的静态方法。
}
