package com.weacsoft.jaravel.vendor.auth;

import com.weacsoft.jaravel.vendor.http.request.Request;

/**
 * 认证上下文：以 ThreadLocal 持有当前请求，供 Guard 读取 session / token。
 * <p>
 * 由 {@code AuthLifecycleFilter} 在请求开始时设置、结束时清理。
 */
public final class AuthContext {

    private static final ThreadLocal<Request> CURRENT = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(Request request) {
        CURRENT.set(request);
    }

    public static Request get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
