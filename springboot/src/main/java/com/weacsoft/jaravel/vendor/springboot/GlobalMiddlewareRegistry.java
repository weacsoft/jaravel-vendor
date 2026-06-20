package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.middleware.Middleware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 全局中间件注册器，对齐 Laravel {@code App\Http\Kernel::$middleware} 全局中间件栈。
 * <p>
 * 作为 Spring {@code @Component} 管理，全局中间件对所有路由生效，
 * 由 {@code SpringBootRouteAutoConfiguration} 合并到每条路由的中间件链最外层。
 * <p>
 * 支持两种注册方式：
 * <ul>
 *   <li>直接传入中间件实例：{@link #add(Middleware)} / {@link #addAll(List)}（向后兼容）</li>
 *   <li>按 Spring Bean 类型注册：{@link #addByType(Class)}，从容器中获取无状态的单例 Bean</li>
 * </ul>
 */
@Component
public class GlobalMiddlewareRegistry {

    private final List<Middleware> middlewares = new ArrayList<>();

    private final ApplicationContext applicationContext;

    @Autowired
    public GlobalMiddlewareRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 直接添加中间件实例（向后兼容）。
     * <p>
     * 适用于需要构造参数的不可变中间件（如 {@code new Authenticate("api")}）。
     *
     * @param middleware 中间件实例
     */
    public void add(Middleware middleware) {
        middlewares.add(middleware);
    }

    /**
     * 批量添加中间件实例（向后兼容）。
     *
     * @param middlewares 中间件实例列表
     */
    public void addAll(List<Middleware> middlewares) {
        this.middlewares.addAll(middlewares);
    }

    /**
     * 按 Spring Bean 类型注册中间件。
     * <p>
     * 从 Spring 容器中获取指定类型的中间件 Bean（须为无状态、可复用的单例），
     * 对齐 Laravel 在 RouteServiceProvider 中统一注册系统中间件的做法。
     *
     * @param middlewareClass 中间件的 Spring Bean 类型
     */
    public void addByType(Class<? extends Middleware> middlewareClass) {
        Middleware middleware = applicationContext.getBean(middlewareClass);
        middlewares.add(middleware);
    }

    /**
     * 批量按 Spring Bean 类型注册中间件。
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
