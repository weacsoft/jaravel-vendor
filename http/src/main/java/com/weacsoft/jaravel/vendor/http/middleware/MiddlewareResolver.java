package com.weacsoft.jaravel.vendor.http.middleware;

/**
 * 中间件解析器（函数式接口），对齐 Laravel 中间件别名 + 参数机制。
 * <p>
 * 在 Laravel 中，路由可以通过字符串别名引用中间件，并传递参数：
 * <pre>
 * Route::middleware('auth:api');        // auth 中间件，参数为 "api"
 * Route::middleware('auth:api,admin');  // auth 中间件，参数为 "api" 和 "admin"
 * </pre>
 * <p>
 * 本接口将别名映射到实际的 {@link Middleware} 实例。对于无参数的中间件，
 * 直接返回固定实例即可；对于需要参数的中间件（如根据 guard 类型构造不同的认证逻辑），
 * 可根据 parameters 动态创建中间件实例。
 *
 * <h3>示例：无参数中间件</h3>
 * <pre>{@code
 * MiddlewareResolver resolver = params -> logMiddleware;  // 忽略参数
 * }</pre>
 *
 * <h3>示例：参数化中间件</h3>
 * <pre>{@code
 * MiddlewareResolver resolver = params -> {
 *     String guard = params.length > 0 ? params[0] : "web";
 *     return new AuthMiddleware(guard);
 * };
 * }</pre>
 *
 * @see MiddlewareAliasRegistry
 */
@FunctionalInterface
public interface MiddlewareResolver {

    /**
     * 根据参数解析出中间件实例。
     *
     * @param parameters 中间件参数（来自别名表达式冒号后的逗号分隔值，如 "auth:api,admin" → ["api", "admin"]）
     * @return 中间件实例
     */
    Middleware resolve(String... parameters);
}
