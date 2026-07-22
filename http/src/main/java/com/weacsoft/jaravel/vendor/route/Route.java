package com.weacsoft.jaravel.vendor.route;

import com.weacsoft.jaravel.vendor.http.controller.Controllers;
import com.weacsoft.jaravel.vendor.http.middleware.ClassMiddlewareSpec;
import com.weacsoft.jaravel.vendor.http.middleware.Middleware;
import com.weacsoft.jaravel.vendor.http.middleware.MiddlewareAliasRegistry;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.weacsoft.jaravel.vendor.route.RouteService.*;

public class Route {
    /**
     * 中间件规格列表，元素类型为：
     * <ul>
     *   <li>{@link Middleware} — 直接中间件实例</li>
     *   <li>{@link String} — 别名/类名表达式（如 "auth:api"、"LogMiddleware:debug"）</li>
     *   <li>{@link Class} — 类对象引用（无参数，如 AuthMiddleware.class）</li>
     *   <li>{@link ClassMiddlewareSpec} — 类对象 + 参数（如 AuthMiddleware.class + ["api"]）</li>
     * </ul>
     * 保持插入顺序，支持混合使用。
     */
    private final List<Object> middlewareSpecs = new CopyOnWriteArrayList<>();
    @Setter
    @Getter
    private String name = "";
    @Setter
    @Getter
    private String namespace = "";
    @Setter
    @Getter
    private String prefix = "";
    @Getter
    private String method;
    @Getter
    @Setter
    private Controllers.Runner action;
    @Setter
    private Router router;
    @Getter
    private String uri;

    public Route(String method, String uri, Controllers.Runner action) {
        setMethod(method);
        setUri(uri);
        setAction(action);
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    /**
     * 添加路由级中间件（直接传入中间件实例）。
     *
     * @param middleware 中间件实例
     * @return this（链式调用）
     */
    public Route middleware(Middleware... middleware) {
        middlewareSpecs.addAll(Arrays.asList(middleware));
        return this;
    }

    /**
     * 添加路由级中间件（通过别名表达式引用，对齐 Laravel {@code Route::middleware('auth:api')}）。
     * <p>
     * 别名表达式语法：
     * <ul>
     *   <li>{@code "auth"} — 别名 "auth"，无参数</li>
     *   <li>{@code "auth:api"} — 别名 "auth"，参数 ["api"]</li>
     *   <li>{@code "auth:api,admin"} — 别名 "auth"，参数 ["api", "admin"]</li>
     * </ul>
     * 别名需提前通过 {@link MiddlewareAliasRegistry} 注册。
     *
     * @param aliases 别名表达式
     * @return this（链式调用）
     * @see MiddlewareAliasRegistry
     */
    public Route middleware(String... aliases) {
        middlewareSpecs.addAll(Arrays.asList(aliases));
        return this;
    }

    /**
     * 添加路由级中间件（通过类对象引用，支持可选参数）。
     * <p>
     * 适用于标注了 {@code @MiddlewareAlias} 但未填别名的中间件，或需要类型安全引用的场景。
     * 中间件类必须已通过 {@link MiddlewareAliasRegistry} 注册（有注解的会自动注册）。
     * <p>
     * 使用示例：
     * <pre>
     * // 无参数
     * router.get("/log", action).middleware(LogMiddleware.class);
     * // 带参数
     * router.get("/api", action).middleware(AuthMiddleware.class, "api", "admin");
     * </pre>
     *
     * @param clazz  中间件类（必须实现 {@link Middleware}）
     * @param params 中间件参数（可选）
     * @return this（链式调用）
     * @see MiddlewareAliasRegistry#resolve(Class, String...)
     */
    public Route middleware(Class<?> clazz, String... params) {
        middlewareSpecs.add(new ClassMiddlewareSpec(clazz, params));
        return this;
    }

    public Route name(String name) {
        setName(name);
        return this;
    }

    public Route prefix(String prefix) {
        this.prefix = prefix;
        return this;
    }

    public String generateFullUri() {
        return normalizeUri(router.generateFullUri() + "/" + prefix + "/" + uri);
    }

    public String getFullUri() {
        return generateFullUri();
    }

    public String generateFullNamespace() {
        return normalizeNamesapce(router.generateFullNamespace() + "." + namespace);
    }

    protected String generateFullName() {
        return normalizeName(router.generateFullName() + "." + name);
    }

    public String getFullName() {
        return generateFullName();
    }

    public String getFullNamespace() {
        return generateFullNamespace();
    }

    public List<Middleware> getMiddlewares() {
        List<Middleware> middlewares = new ArrayList<>();
        // 先加父路由器中间件（含别名/类解析）
        middlewares.addAll(router.getAllMiddlewares());
        // 再加本路由中间件（解析别名表达式 / 类对象 / 类+参数）
        MiddlewareAliasRegistry registry = MiddlewareAliasRegistry.getGlobal();
        for (Object spec : middlewareSpecs) {
            if (spec instanceof Middleware) {
                middlewares.add((Middleware) spec);
            } else if (spec instanceof String) {
                middlewares.add(registry.resolve((String) spec));
            } else if (spec instanceof ClassMiddlewareSpec) {
                ClassMiddlewareSpec cms = (ClassMiddlewareSpec) spec;
                middlewares.add(registry.resolve(cms.getClazz(), cms.getParams()));
            } else if (spec instanceof Class<?>) {
                middlewares.add(registry.resolve((Class<?>) spec));
            }
        }
        return middlewares;
    }

    public enum Group {
        NAMESPACE,
        PREFIX,
        NAME
    }
}