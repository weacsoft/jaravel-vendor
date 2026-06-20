package com.weacsoft.jaravel.vendor.auth.middleware;

import com.weacsoft.jaravel.vendor.auth.facade.Auth;
import com.weacsoft.jaravel.vendor.middleware.Middleware;
import com.weacsoft.jaravel.vendor.http.request.Request;
import com.weacsoft.jaravel.vendor.http.response.Response;
import com.weacsoft.jaravel.vendor.http.response.ResponseBuilder;

/**
 * 认证中间件，对齐 Laravel 的 {@code auth} 中间件。
 * <p>
 * 支持指定守卫名称，对齐 Laravel 的 {@code auth:api}、{@code auth:web} 语法：
 * <pre>
 * router.get("/admin", handler).middleware(new Authenticate("web"))
 * router.get("/api/profile", handler).middleware(new Authenticate("api"))
 * </pre>
 * 未登录时：API 请求返回 401 JSON，其它请求重定向到登录页。
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
    public Response handle(Request request, NextFunction next) {
        boolean authenticated;
        if (guard != null && !guard.isEmpty()) {
            // 指定守卫：对齐 Laravel auth:guard 语法
            authenticated = Auth.guard(guard).check();
        } else {
            // 默认守卫
            authenticated = Auth.check();
        }
        if (authenticated) {
            return next.apply(request);
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
