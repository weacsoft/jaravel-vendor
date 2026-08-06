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

/**
 * 单条路由定义，包含 HTTP 方法、URI、控制器动作、中间件链及命名空间/前缀等元数据。
 * <p>
 * 由 {@link Router} 的 {@code get()}/{@code post()} 等方法创建并返回，
 * 支持链式调用 {@code .name()} / {@code .middleware()} 设置路由级属性。
 * <p>
 * 路由的完整 URI、命名空间、名称由所属 {@link Router} 层级自动合并计算
 * （{@link #getFullUri()} / {@link #getFullNamespace()} / {@link #getFullName()}）。
 *
 * @see Router
 * @see Route（静态门面）
 */
public class RouteDefinition {
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
    @Getter
    private String name = "";
    @Getter
    private String namespace = "";
    @Getter
    private String prefix = "";
    @Getter
    private String method;
    @Getter
    private Controllers.Runner action;
    private Router router;
    @Getter
    private String uri;

    // ===== 派生结果缓存 =====
    //
    // 完整 URI / 名称 / 命名空间与中间件链都需沿父级 Router 递归合并，且中间件别名
    // 表达式每次解析都会重新拆串并分配闭包。这些结果在路由注册完成后完全不变，
    // 却处在每请求的必经路径上（中间件链）与模板 route() 反查路径上（名称/URI），
    // 因此在此就地缓存，用 RouteService 的全局结构版本号统一失效。

    private volatile int cacheVersion = -1;
    private volatile String cachedFullUri;
    private volatile String cachedFullName;
    private volatile String cachedFullNamespace;
    private volatile List<Middleware> cachedMiddlewares;
    private volatile Middleware.NextFunction cachedChain;

    public RouteDefinition(String method, String uri, Controllers.Runner action) {
        setMethod(method);
        setUri(uri);
        setAction(action);
    }

    /**
     * 结构版本号变化时丢弃全部派生缓存。
     * <p>缓存内容均为纯函数结果，竞态下最坏只是重复计算一次，无需加锁。</p>
     */
    private void refreshCache() {
        int version = RouteService.structureVersion();
        if (cacheVersion != version) {
            cachedFullUri = null;
            cachedFullName = null;
            cachedFullNamespace = null;
            cachedMiddlewares = null;
            cachedChain = null;
            cacheVersion = version;
        }
    }

    public void setMethod(String method) {
        this.method = method;
        RouteService.invalidateStructure();
    }

    public void setUri(String uri) {
        this.uri = uri;
        RouteService.invalidateStructure();
    }

    public void setName(String name) {
        this.name = name;
        RouteService.invalidateStructure();
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
        RouteService.invalidateStructure();
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
        RouteService.invalidateStructure();
    }

    public void setAction(Controllers.Runner action) {
        this.action = action;
        RouteService.invalidateStructure();
    }

    public void setRouter(Router router) {
        this.router = router;
        RouteService.invalidateStructure();
    }

    /**
     * 添加路由级中间件（直接传入中间件实例）。
     *
     * @param middleware 中间件实例
     * @return this（链式调用）
     */
    public RouteDefinition middleware(Middleware... middleware) {
        middlewareSpecs.addAll(Arrays.asList(middleware));
        RouteService.invalidateStructure();
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
    public RouteDefinition middleware(String... aliases) {
        middlewareSpecs.addAll(Arrays.asList(aliases));
        RouteService.invalidateStructure();
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
    public RouteDefinition middleware(Class<?> clazz, String... params) {
        middlewareSpecs.add(new ClassMiddlewareSpec(clazz, params));
        RouteService.invalidateStructure();
        return this;
    }

    public RouteDefinition name(String name) {
        setName(name);
        return this;
    }

    public RouteDefinition prefix(String prefix) {
        setPrefix(prefix);
        return this;
    }

    public String generateFullUri() {
        return normalizeUri(router.generateFullUri() + "/" + prefix + "/" + uri);
    }

    public String getFullUri() {
        refreshCache();
        String cached = cachedFullUri;
        if (cached == null) {
            cached = generateFullUri();
            cachedFullUri = cached;
        }
        return cached;
    }

    public String generateFullNamespace() {
        return normalizeNamesapce(router.generateFullNamespace() + "." + namespace);
    }

    protected String generateFullName() {
        return normalizeName(router.generateFullName() + "." + name);
    }

    public String getFullName() {
        refreshCache();
        String cached = cachedFullName;
        if (cached == null) {
            cached = generateFullName();
            cachedFullName = cached;
        }
        return cached;
    }

    public String getFullNamespace() {
        refreshCache();
        String cached = cachedFullNamespace;
        if (cached == null) {
            cached = generateFullNamespace();
            cachedFullNamespace = cached;
        }
        return cached;
    }

    public List<Middleware> getMiddlewares() {
        refreshCache();
        List<Middleware> cached = cachedMiddlewares;
        if (cached == null) {
            cached = java.util.Collections.unmodifiableList(resolveMiddlewares());
            cachedMiddlewares = cached;
        }
        return cached;
    }

    private List<Middleware> resolveMiddlewares() {
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

    /**
     * 取得「中间件链 + 控制器动作」折叠后的最终处理函数。
     *
     * <p>此前该折叠在每个请求的 HandlerFunction 内部重做一遍：为每条路由重新解析
     * 一次中间件别名（拆串 + 分配参数数组 + 分配闭包），再自尾向头分配 N 个链式闭包。
     * 中间件配置在注册完成后不再变化，因此结果可直接缓存，命中率 100%。</p>
     *
     * @return 可直接 {@code apply(request)} 的处理链
     */
    public Middleware.NextFunction getHandlerChain() {
        refreshCache();
        Middleware.NextFunction cached = cachedChain;
        if (cached == null) {
            List<Middleware> all = getMiddlewares();
            final Controllers.Runner target = action;
            Middleware.NextFunction handler = target::handle;
            for (int i = all.size() - 1; i >= 0; i--) {
                final Middleware middleware = all.get(i);
                final Middleware.NextFunction next = handler;
                handler = request -> middleware.handle(request, next);
            }
            cached = handler;
            cachedChain = cached;
        }
        return cached;
    }
}
