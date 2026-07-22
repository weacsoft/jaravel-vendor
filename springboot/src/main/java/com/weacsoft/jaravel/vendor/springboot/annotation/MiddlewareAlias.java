package com.weacsoft.jaravel.vendor.springboot.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 中间件别名注解，对齐 Laravel {@code App\Http\Kernel::$routeMiddleware} 别名注册机制。
 * <p>
 * 标注在实现了 {@code com.weacsoft.jaravel.vendor.http.middleware.Middleware} 的类上，
 * SpringBoot 启动时由 {@code SpringBootRouteAutoConfiguration} 通过 classpath 扫描
 * 自动发现并实例化（非 Spring Bean），注册到
 * {@code com.weacsoft.jaravel.vendor.http.middleware.MiddlewareAliasRegistry} 全局注册表。
 * <p>
 * <b>中间件不是 Spring Bean</b>——本注解不组合 {@code @Component}，标注后类不会被注册为 Spring Bean。
 * 中间件由框架通过反射实例化（要求有无参构造器），适合无状态或通过继承覆盖方法配置参数的场景。
 * <p>
 * 注册后，路由可通过三种方式引用该中间件：
 * <ul>
 *   <li><b>别名</b>（填写了 value 时）：{@code middleware("auth:api")} — 字符串别名 + 参数</li>
 *   <li><b>类对象</b>：{@code middleware(AuthMiddleware.class)} 或 {@code middleware(AuthMiddleware.class, "api")} — Class + 可选参数</li>
 *   <li><b>类名</b>：{@code middleware("AuthMiddleware:api")} — 类名字符串，语法与别名一致</li>
 * </ul>
 * 别名表达式或类名表达式中的参数会传入 {@code handle} 方法的 {@code params}。
 * <p>
 * 三种注册场景：
 * <ul>
 *   <li>未标注 {@code @MiddlewareAlias}：视为用户自建中间件，模块和 SpringBoot 均不扫描注册</li>
 *   <li>标注但未填别名（{@code @MiddlewareAlias} 或 {@code @MiddlewareAlias("")}）：按类对象或类名识别</li>
 *   <li>标注且填了别名（{@code @MiddlewareAlias("auth")}）：按别名识别，也可按类对象/类名识别</li>
 * </ul>
 * <p>
 * 注：本注解命名为 {@code MiddlewareAlias} 而非 {@code Middleware}，
 * 以避免与 {@code com.weacsoft.jaravel.vendor.http.middleware.Middleware} 接口同名冲突。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 1. 填写别名 — 路由中用别名引用
 * @MiddlewareAlias("auth")
 * public class AuthMiddleware implements Middleware {
 *     @Override
 *     public Response handle(Request request, NextFunction next, String... params) {
 *         String guard = params.length > 0 ? params[0] : "web";
 *         return next.apply(request);
 *     }
 * }
 * router.get("/api/users", action).middleware("auth:api");
 *
 * // 2. 不填别名 — 路由中用类对象或类名引用
 * @MiddlewareAlias
 * public class LogMiddleware implements Middleware { ... }
 * router.get("/log", action).middleware(LogMiddleware.class);
 * router.get("/log", action).middleware("LogMiddleware");
 *
 * // 3. 继承预定义中间件并自定义参数
 * @MiddlewareAlias
 * public class AppTrimStrings extends TrimStrings {
 *     @Override
 *     protected String[] except() {
 *         return new String[]{"password", "password_confirmation"};
 *     }
 * }
 * }</pre>
 *
 * @see com.weacsoft.jaravel.vendor.http.middleware.MiddlewareAliasRegistry
 * @see com.weacsoft.jaravel.vendor.springboot.SpringBootRouteAutoConfiguration
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MiddlewareAlias {

    /**
     * 中间件别名，用于在路由中通过字符串引用。
     * <p>
     * 例如值为 {@code "auth"} 时，路由中可使用 {@code middleware("auth")} 或 {@code middleware("auth:api")} 引用。
     * <p>
     * 留空时（默认），中间件仅按类对象或类名识别，不注册别名。
     *
     * @return 别名，默认空字符串
     */
    String value() default "";
}
