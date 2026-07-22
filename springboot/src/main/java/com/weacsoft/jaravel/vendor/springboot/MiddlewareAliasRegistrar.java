package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.http.middleware.Middleware;
import com.weacsoft.jaravel.vendor.http.middleware.MiddlewareAliasRegistry;
import com.weacsoft.jaravel.vendor.springboot.annotation.MiddlewareAlias;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 中间件别名注册器，对齐 Laravel {@code App\Http\Kernel::$routeMiddleware} 别名注册机制。
 * <p>
 * SpringBoot 启动时自动扫描 {@link MiddlewareAlias @MiddlewareAlias} 注解的 Bean，
 * 并注册到 {@link MiddlewareAliasRegistry} 全局注册表，使路由可通过字符串别名引用中间件。
 * <p>
 * 全局中间件（对所有路由生效的中间件）无需单独的注册器，直接在根 {@link com.weacsoft.jaravel.vendor.route.Router Router}
 * 上声明即可，路由会通过 {@code Router.getAllMiddlewares()} 继承。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 1. 声明中间件并注册别名
 * @MiddlewareAlias("auth")
 * public class AuthMiddleware implements Middleware {
 *     @Override
 *     public Response handle(Request request, NextFunction next, String... params) {
 *         String guard = params.length > 0 ? params[0] : "web";
 *         // 认证逻辑...
 *         return next.apply(request);
 *     }
 * }
 *
 * // 2. 路由中使用别名（支持参数）
 * router.get("/api/users", action).middleware("auth:api");
 *
 * // 3. 全局中间件直接在根 Router 上声明
 * router.middleware(trimStrings, convertEmptyStringsToNull);
 * }</pre>
 */
@Component
public class MiddlewareAliasRegistrar {

    private static final Logger log = LoggerFactory.getLogger(MiddlewareAliasRegistrar.class);

    private final ApplicationContext applicationContext;

    @Autowired
    public MiddlewareAliasRegistrar(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 启动时自动扫描 {@link MiddlewareAlias @MiddlewareAlias} 注解的 Bean，
     * 并注册到 {@link MiddlewareAliasRegistry} 全局注册表。
     */
    @PostConstruct
    public void registerAliases() {
        if (applicationContext == null) {
            return;
        }
        Map<String, Object> beansWithAnnotation = applicationContext.getBeansWithAnnotation(MiddlewareAlias.class);
        MiddlewareAliasRegistry registry = MiddlewareAliasRegistry.getGlobal();

        beansWithAnnotation.forEach((beanName, bean) -> {
            MiddlewareAlias annotation = bean.getClass().getAnnotation(MiddlewareAlias.class);
            if (annotation == null) {
                return;
            }
            String alias = annotation.value();

            if (bean instanceof Middleware) {
                registry.register(alias, (Middleware) bean);
                log.info("注册中间件别名: '{}' -> {}", alias, bean.getClass().getName());
            } else {
                log.warn("@MiddlewareAlias(\"{}\") 标注的 Bean '{}' 未实现 Middleware 接口，跳过注册",
                        alias, bean.getClass().getName());
            }
        });
    }
}
