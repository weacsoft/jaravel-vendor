package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;

/**
 * 路由认证处理器接口，用于解耦 springboot 模块对 auth 模块的 optional 依赖。
 * <p>
 * 当 auth 模块存在于 classpath 时，由 {@link AuthRouteAuthHandler} 提供实现，
 * 在请求处理前设置 {@code AuthContext}，请求结束后清理认证状态。
 * <p>
 * 当 auth 模块不存在时，由 {@link DefaultRouteAuthHandler} 提供 no-op 实现，
 * 所有操作变为空操作，避免 {@code NoClassDefFoundError}。
 */
public interface RouteAuthHandler {

    /**
     * 设置当前请求的认证上下文。
     *
     * @param request 当前请求
     */
    void setupAuth(Request request);

    /**
     * 清理认证上下文（请求结束后调用）。
     */
    void clearAuth();
}
