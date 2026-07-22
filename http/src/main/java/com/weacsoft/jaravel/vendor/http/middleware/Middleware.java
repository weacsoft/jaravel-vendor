package com.weacsoft.jaravel.vendor.http.middleware;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;

/**
 * 中间件函数式接口，对齐 Laravel 中间件机制。
 * <p>
 * 采用洋葱模型，通过 {@code next.apply(request)} 将请求传递给下一层。
 * <p>
 * {@code params} 参数来自路由别名表达式（如 {@code auth:api} → params = ["api"]），
 * 对齐 Laravel 的 {@code auth:api}、{@code auth:api,admin} 语法。
 * 无别名场景下 params 为空数组。
 *
 * <h3>示例：无参数中间件</h3>
 * <pre>{@code
 * Middleware logMiddleware = (request, next, params) -> {
 *     System.out.println("Request: " + request.getRequest().getRequestURI());
 *     return next.apply(request);
 * };
 * }</pre>
 *
 * <h3>示例：参数化中间件</h3>
 * <pre>{@code
 * @MiddlewareAlias("auth")
 * public class AuthMiddleware implements Middleware {
 *     @Override
 *     public Response handle(Request request, NextFunction next, String... params) {
 *         String guard = params.length > 0 ? params[0] : "web";
 *         if (!Auth.guard(guard).check()) {
 *             return ResponseBuilder.error(401, "Unauthorized");
 *         }
 *         return next.apply(request);
 *     }
 * }
 *
 * // 路由中使用：auth:api → params = ["api"]
 * router.get("/api/users", action).middleware("auth:api");
 * }</pre>
 *
 * @see MiddlewareAliasRegistry
 */
@FunctionalInterface
public interface Middleware {

    /**
     * 处理请求。
     *
     * @param request HTTP 请求
     * @param next    下一层处理函数，调用 {@code next.apply(request)} 传递请求
     * @param params  中间件参数（来自别名表达式冒号后的逗号分隔值，如 "auth:api,admin" → ["api", "admin"]）
     * @return HTTP 响应
     */
    Response handle(Request request, NextFunction next, String... params);

    @FunctionalInterface
    interface NextFunction {
        Response apply(Request request);
    }
}
