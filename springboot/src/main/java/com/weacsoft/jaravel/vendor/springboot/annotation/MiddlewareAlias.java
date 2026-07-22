package com.weacsoft.jaravel.vendor.springboot.annotation;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 中间件别名注解，对齐 Laravel {@code App\Http\Kernel::$routeMiddleware} 别名注册机制。
 * <p>
 * 标注在实现了 {@code com.weacsoft.jaravel.vendor.http.middleware.Middleware} 的类上，
 * SpringBoot 启动时由 {@code MiddlewareAliasRegistrar} 自动扫描并注册到
 * {@code com.weacsoft.jaravel.vendor.http.middleware.MiddlewareAliasRegistry} 全局注册表。
 * <p>
 * 注册后，路由可通过字符串别名引用该中间件，别名表达式中的参数会传入 {@code handle} 方法的 {@code params}。
 * <p>
 * 注：本注解命名为 {@code MiddlewareAlias} 而非 {@code Middleware}，
 * 以避免与 {@code com.weacsoft.jaravel.vendor.http.middleware.Middleware} 接口同名冲突。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @MiddlewareAlias("auth")
 * public class AuthMiddleware implements Middleware {
 *     @Override
 *     public Response handle(Request request, NextFunction next, String... params) {
 *         // params 来自别名表达式：auth:api → params = ["api"]
 *         String guard = params.length > 0 ? params[0] : "web";
 *         // 认证逻辑...
 *         return next.apply(request);
 *     }
 * }
 *
 * // 路由中使用别名
 * router.get("/api/users", action).middleware("auth:api");
 * // auth:api,admin → params = ["api", "admin"]
 * router.get("/admin", action).middleware("auth:admin");
 * }</pre>
 * <p>
 * 本注解组合了 {@code @Component}，标注后类会自动注册为 Spring Bean。
 *
 * @see com.weacsoft.jaravel.vendor.http.middleware.MiddlewareAliasRegistry
 * @see com.weacsoft.jaravel.vendor.springboot.MiddlewareAliasRegistrar
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface MiddlewareAlias {

    /**
     * 中间件别名，用于在路由中通过字符串引用。
     * <p>
     * 例如值为 {@code "auth"} 时，路由中可使用 {@code middleware("auth")} 或 {@code middleware("auth:api")} 引用。
     *
     * @return 别名
     */
    @AliasFor(annotation = Component.class, attribute = "value")
    String value();
}
