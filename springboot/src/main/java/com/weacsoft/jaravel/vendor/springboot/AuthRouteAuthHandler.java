package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.auth.AuthContext;
import com.weacsoft.jaravel.vendor.auth.AuthManager;
import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

/**
 * 基于 auth 模块的路由认证处理器。
 * <p>
 * 当 {@link AuthManager} 存在于 classpath 时启用，在请求处理前设置
 * {@link AuthContext}，请求结束后清理 {@link AuthContext} 和 {@link AuthManager}
 * 的 ThreadLocal 状态。
 * <p>
 * {@code @ConditionalOnClass} 通过 ASM 读取字节码注解，不会触发类加载。
 * 当 auth 模块不在 classpath 时，Spring 不会加载此类，从而避免
 * {@code NoClassDefFoundError}。
 */
@ConditionalOnClass(AuthManager.class)
public class AuthRouteAuthHandler implements RouteAuthHandler {

    private final AuthManager authManager;

    public AuthRouteAuthHandler(AuthManager authManager) {
        this.authManager = authManager;
    }

    @Override
    public void setupAuth(Request request) {
        AuthContext.set(request);
    }

    @Override
    public void clearAuth() {
        AuthContext.clear();
        authManager.clear();
    }
}
