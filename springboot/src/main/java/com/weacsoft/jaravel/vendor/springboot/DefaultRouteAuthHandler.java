package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;

/**
 * 默认路由认证处理器（no-op 实现）。
 * <p>
 * 当 auth 模块不在 classpath 时使用此实现，所有操作为空操作。
 * 由 {@code @ConditionalOnMissingBean(RouteAuthHandler.class)} 守卫，
 * 仅在没有其他 {@link RouteAuthHandler} 实现时创建。
 */
public class DefaultRouteAuthHandler implements RouteAuthHandler {

    @Override
    public void setupAuth(Request request) {
        // no-op: auth 模块不在 classpath
    }

    @Override
    public void clearAuth() {
        // no-op: auth 模块不在 classpath
    }
}
