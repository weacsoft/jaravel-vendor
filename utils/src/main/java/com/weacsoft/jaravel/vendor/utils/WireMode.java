package com.weacsoft.jaravel.vendor.utils;

/**
 * Wire 模式标记（ThreadLocal）。
 * <p>
 * 由 wire 模块的 WireMiddleware 在请求进入时设置，
 * 由各模块（http/jblade）在渲染时读取。
 * 因为 wire 模块依赖 utils，而 http 也依赖 utils，
 * 所以这是唯一可以双向访问的公共点。
 */
public class WireMode {

    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();

    public static void begin() {
        ACTIVE.set(Boolean.TRUE);
    }

    public static boolean isActive() {
        return Boolean.TRUE.equals(ACTIVE.get());
    }

    public static void clear() {
        ACTIVE.remove();
    }
}
