package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.http.middleware.Middleware;
import com.weacsoft.jaravel.vendor.http.middleware.MiddlewareAliasRegistry;
import com.weacsoft.jaravel.vendor.http.middleware.MiddlewareResolver;
import com.weacsoft.jaravel.vendor.springboot.annotation.MiddlewareAlias;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 全局中间件注册器，对齐 Laravel {@code App\Http\Kernel} 中间件栈。
 * <p>
 * 承担两项职责：
 * <ol>
 *   <li><b>全局中间件</b>：通过 {@link #add(Middleware)} / {@link #addAll(List)} / {@link #addByType(Class)}
 *       注册的中间件对所有路由生效，由 {@code SpringBootRouteAutoConfiguration} 合并到每条路由的中间件链最外层。</li>
 *   <li><b>别名中间件自动注册</b>：启动时自动扫描 {@link MiddlewareAlias @MiddlewareAlias} 注解的 Bean，
 *       注册到 {@link MiddlewareAliasRegistry} 全局注册表，使路由可通过字符串别名引用中间件。</li>
 * </ol>
 * <p>
 * 别名中间件注册流程：
 * <ol>
 *   <li>用户在中间件类上标注 {@code @MiddlewareAlias("auth")}</li>
 *   <li>SpringBoot 启动时，本类的 {@link #registerAliasMiddlewares()} 方法自动扫描所有 {@code @MiddlewareAlias} Bean</li>
 *   <li>若 Bean 实现 {@link MiddlewareResolver}，注册为参数化中间件别名；否则注册为简单中间件别名</li>
 *   <li>路由中通过 {@code middleware("auth:api")} 字符串别名引用</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 1. 声明中间件并注册别名
 * @MiddlewareAlias("auth")
 * public class AuthMiddleware implements Middleware {
 *     public Response handle(Request request, NextFunction next) {
 *         // 认证逻辑...
 *         return next.apply(request);
 *     }
 * }
 *
 * // 2. 路由中使用别名
 * router.get("/api/users", action).middleware("auth:api");
 * }</pre>
 */
@Component
public class GlobalMiddlewareRegistry {

    private static final Logger log = LoggerFactory.getLogger(GlobalMiddlewareRegistry.class);

    private final List<Middleware> middlewares = new ArrayList<>();

    private final ApplicationContext applicationContext;

    @Autowired
    public GlobalMiddlewareRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 启动时自动扫描 {@link MiddlewareAlias @MiddlewareAlias} 注解的 Bean，
     * 并注册到 {@link MiddlewareAliasRegistry} 全局注册表。
     * <p>
     * 扫描规则：
     * <ul>
     *   <li>Bean 实现 {@link MiddlewareResolver} → 注册为参数化中间件别名（支持 {@code alias:param1,param2} 语法）</li>
     *   <li>Bean 实现 {@link Middleware} → 注册为简单中间件别名（忽略参数）</li>
     * </ul>
     */
    @PostConstruct
    public void registerAliasMiddlewares() {
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

            if (bean instanceof MiddlewareResolver) {
                registry.register(alias, (MiddlewareResolver) bean);
                log.info("注册参数化中间件别名: '{}' -> {}", alias, bean.getClass().getName());
            } else if (bean instanceof Middleware) {
                registry.register(alias, (Middleware) bean);
                log.info("注册中间件别名: '{}' -> {}", alias, bean.getClass().getName());
            } else {
                log.warn("@MiddlewareAlias(\"{}\") 标注的 Bean '{}' 既不实现 Middleware 也不实现 MiddlewareResolver，跳过注册",
                        alias, bean.getClass().getName());
            }
        });
    }

    /**
     * 直接添加全局中间件实例。
     * <p>
     * 全局中间件对所有路由生效，位于中间件链最外层。
     *
     * @param middleware 中间件实例
     */
    public void add(Middleware middleware) {
        middlewares.add(middleware);
    }

    /**
     * 批量添加全局中间件实例。
     *
     * @param middlewares 中间件实例列表
     */
    public void addAll(List<Middleware> middlewares) {
        this.middlewares.addAll(middlewares);
    }

    /**
     * 按 Spring Bean 类型注册全局中间件。
     * <p>
     * 从 Spring 容器中获取指定类型的中间件 Bean（须为无状态、可复用的单例）。
     *
     * @param middlewareClass 中间件的 Spring Bean 类型
     */
    public void addByType(Class<? extends Middleware> middlewareClass) {
        Middleware middleware = applicationContext.getBean(middlewareClass);
        middlewares.add(middleware);
    }

    /**
     * 批量按 Spring Bean 类型注册全局中间件。
     *
     * @param middlewareClasses 中间件的 Spring Bean 类型列表
     */
    public void addAllByType(List<Class<? extends Middleware>> middlewareClasses) {
        for (Class<? extends Middleware> middlewareClass : middlewareClasses) {
            addByType(middlewareClass);
        }
    }

    /**
     * 返回已注册的全局中间件列表（不可修改视图）。
     *
     * @return 全局中间件列表
     */
    public List<Middleware> getMiddlewares() {
        return Collections.unmodifiableList(middlewares);
    }
}
