package com.weacsoft.jaravel.vendor.auth.middleware;

import com.weacsoft.jaravel.vendor.auth.facade.Auth;
import com.weacsoft.jaravel.vendor.http.middleware.Middleware;
import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import com.weacsoft.jaravel.vendor.http.controller.response.ResponseBuilder;

/**
 * 认证中间件，对齐 Laravel 的 {@code auth} 中间件。
 * <p>
 * 支持指定守卫名称，对齐 Laravel 的 {@code auth:api}、{@code auth:web} 语法：
 * <pre>
 * router.get("/admin", handler).middleware(new Authenticate("web"))
 * router.get("/api/profile", handler).middleware(new Authenticate("api"))
 * </pre>
 * 未登录时分三种情况处理：
 * <ul>
 *   <li>Wire 请求（X-Wire-Request 头）：返回 401 JSON，包含 {@code redirect} 字段指向登录页，
 *       前端 wire.js 自动跳转，实现无感重定向</li>
 *   <li>API 请求：返回 401 JSON</li>
 *   <li>其它请求：302 重定向到登录页</li>
 * </ul>
 */
public class Authenticate implements Middleware {

    private final String loginPath;
    private final String guard;

    public Authenticate() {
        this(null, "/login");
    }

    public Authenticate(String guard) {
        this(guard, "/login");
    }

    public Authenticate(String guard, String loginPath) {
        this.guard = guard;
        this.loginPath = loginPath;
    }

    @Override
    public Response handle(Request request, NextFunction next, String... params) {
        // 优先使用 params 中的守卫（来自别名表达式如 auth:api），其次使用构造器指定的守卫
        String effectiveGuard = (params != null && params.length > 0) ? params[0] : this.guard;
        boolean authenticated;
        if (effectiveGuard != null && !effectiveGuard.isEmpty()) {
            // 指定守卫：对齐 Laravel auth:guard 语法
            authenticated = Auth.guard(effectiveGuard).check();
        } else {
            // 默认守卫
            authenticated = Auth.check();
        }
        if (authenticated) {
            return next.apply(request);
        }

        // 检测 Wire 请求：通过自定义头 X-Wire-Request 判断
        String wireHeader = request.header("X-Wire-Request");
        if ("true".equalsIgnoreCase(wireHeader)) {
            // Wire 请求未认证：返回 401 JSON，包含 redirect 字段
            // wire.js 收到后会自动重定向到登录页，用户无感知
            return ResponseBuilder.error(401, "Unauthorized", loginPath);
        }

        String accept = request.header("Accept");
        String contentType = request.getRequest() != null ? request.getRequest().getContentType() : null;
        String path = request.getRequest() != null ? request.getRequest().getRequestURI() : null;
        boolean isApi = (accept != null && accept.contains("application/json"))
                || (contentType != null && contentType.contains("application/json"))
                || (path != null && path.startsWith("/api"));
        if (isApi) {
            return ResponseBuilder.error(401, "Unauthorized");
        }
        return ResponseBuilder.redirect(loginPath);
    }
}
