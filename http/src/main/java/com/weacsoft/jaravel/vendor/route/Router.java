package com.weacsoft.jaravel.vendor.route;

import com.weacsoft.jaravel.vendor.http.controller.ControllerActionResolver;
import com.weacsoft.jaravel.vendor.http.controller.Controllers;
import com.weacsoft.jaravel.vendor.route.staticresource.StaticResourceRoute;
import com.weacsoft.jaravel.vendor.http.middleware.ClassMiddlewareSpec;
import com.weacsoft.jaravel.vendor.http.middleware.Middleware;
import com.weacsoft.jaravel.vendor.http.middleware.MiddlewareAliasRegistry;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static com.weacsoft.jaravel.vendor.route.RouteService.*;

public class Router {
    private final List<RouteDefinition> routes = new CopyOnWriteArrayList<>();
    private final List<Router> routers = new CopyOnWriteArrayList<>();
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
    @Setter
    private Router parentRouter;

    /**
     * 添加路由器级中间件（直接传入中间件实例）。
     * <p>
     * 路由器级中间件会被该路由器下所有路由继承。
     *
     * @param middleware 中间件实例
     * @return this（链式调用）
     */
    public Router middleware(Middleware... middleware) {
        middlewareSpecs.addAll(Arrays.asList(middleware));
        return this;
    }

    /**
     * 添加路由器级中间件（通过别名表达式引用，对齐 Laravel {@code Route::middleware('auth:api')}）。
     * <p>
     * 路由器级中间件会被该路由器下所有路由继承，适用于分组批量添加中间件。
     * <p>
     * 别名表达式语法：
     * <ul>
     *   <li>{@code "auth"} — 别名 "auth"，无参数</li>
     *   <li>{@code "auth:api"} — 别名 "auth"，参数 ["api"]</li>
     *   <li>{@code "auth:api,admin"} — 别名 "auth"，参数 ["api", "admin"]</li>
     * </ul>
     *
     * @param aliases 别名表达式
     * @return this（链式调用）
     * @see MiddlewareAliasRegistry
     */
    public Router middleware(String... aliases) {
        middlewareSpecs.addAll(Arrays.asList(aliases));
        return this;
    }

    /**
     * 添加路由器级中间件（通过类对象引用，支持可选参数）。
     * <p>
     * 路由器级中间件会被该路由器下所有路由继承，适用于分组批量添加中间件。
     * 适用于标注了 {@code @MiddlewareAlias} 但未填别名的中间件，或需要类型安全引用的场景。
     * <p>
     * 使用示例：
     * <pre>
     * // 无参数
     * router.middleware(LogMiddleware.class);
     * // 带参数
     * router.middleware(AuthMiddleware.class, "api", "admin");
     * </pre>
     *
     * @param clazz  中间件类（必须实现 {@link Middleware}）
     * @param params 中间件参数（可选）
     * @return this（链式调用）
     * @see MiddlewareAliasRegistry#resolve(Class, String...)
     */
    public Router middleware(Class<?> clazz, String... params) {
        middlewareSpecs.add(new ClassMiddlewareSpec(clazz, params));
        return this;
    }

    public RouteDefinition get(String uri, Controllers.Runner action) {
        return addRoute("GET", uri, action);
    }

    public RouteDefinition post(String uri, Controllers.Runner action) {
        return addRoute("POST", uri, action);
    }

    public RouteDefinition put(String uri, Controllers.Runner action) {
        return addRoute("PUT", uri, action);
    }

    public RouteDefinition delete(String uri, Controllers.Runner action) {
        return addRoute("DELETE", uri, action);
    }

    public RouteDefinition patch(String uri, Controllers.Runner action) {
        return addRoute("PATCH", uri, action);
    }

    public Router all(String uri, Controllers.Runner action) {
        return addMultiRoute(new String[]{"GET", "POST", "PUT", "DELETE", "PATCH"}, uri, action);
    }

    // ===== 控制器引用重载（对齐 Laravel Route::get('/users', 'UserController@index')） =====

    /**
     * 注册 GET 路由（字符串形式控制器引用，对齐 Laravel {@code Route::get('/users', 'UserController@index')}）。
     * <p>
     * 控制器引用格式：{@code "ControllerName::methodName"} 或 {@code "com.example.ControllerName::methodName"}。
     * 解析延迟到首次请求时执行，保证路由注册顺序与控制器扫描顺序无关。
     *
     * @param uri              URI
     * @param controllerAction 控制器引用（如 {@code "UserController::list"}）
     * @return 路由实例
     * @see ControllerActionResolver#resolve(String)
     */
    public RouteDefinition get(String uri, String controllerAction) {
        return addRoute("GET", uri, lazyResolve(controllerAction));
    }

    /**
     * 注册 POST 路由（字符串形式控制器引用）。
     *
     * @param uri              URI
     * @param controllerAction 控制器引用（如 {@code "AuthController::login"}）
     * @return 路由实例
     * @see ControllerActionResolver#resolve(String)
     */
    public RouteDefinition post(String uri, String controllerAction) {
        return addRoute("POST", uri, lazyResolve(controllerAction));
    }

    /**
     * 注册 PUT 路由（字符串形式控制器引用）。
     *
     * @param uri              URI
     * @param controllerAction 控制器引用
     * @return 路由实例
     * @see ControllerActionResolver#resolve(String)
     */
    public RouteDefinition put(String uri, String controllerAction) {
        return addRoute("PUT", uri, lazyResolve(controllerAction));
    }

    /**
     * 注册 DELETE 路由（字符串形式控制器引用）。
     *
     * @param uri              URI
     * @param controllerAction 控制器引用
     * @return 路由实例
     * @see ControllerActionResolver#resolve(String)
     */
    public RouteDefinition delete(String uri, String controllerAction) {
        return addRoute("DELETE", uri, lazyResolve(controllerAction));
    }

    /**
     * 注册 PATCH 路由（字符串形式控制器引用）。
     *
     * @param uri              URI
     * @param controllerAction 控制器引用
     * @return 路由实例
     * @see ControllerActionResolver#resolve(String)
     */
    public RouteDefinition patch(String uri, String controllerAction) {
        return addRoute("PATCH", uri, lazyResolve(controllerAction));
    }

    /**
     * 注册多方法路由（字符串形式控制器引用）。
     *
     * @param uri              URI
     * @param controllerAction 控制器引用
     * @return 路由组实例
     * @see ControllerActionResolver#resolve(String)
     */
    public Router all(String uri, String controllerAction) {
        return addMultiRoute(new String[]{"GET", "POST", "PUT", "DELETE", "PATCH"}, uri, lazyResolve(controllerAction));
    }

    /**
     * 注册 GET 路由（类对象形式控制器引用，忽略包名）。
     * <p>
     * 通过类对象和方法名引用控制器，类型安全且无需输入完整类名。
     * 解析延迟到首次请求时执行。
     *
     * @param uri             URI
     * @param controllerClass 控制器类
     * @param methodName      方法名
     * @return 路由实例
     * @see ControllerActionResolver#resolve(Class, String)
     */
    public RouteDefinition get(String uri, Class<?> controllerClass, String methodName) {
        return addRoute("GET", uri, lazyResolve(controllerClass, methodName));
    }

    /**
     * 注册 POST 路由（类对象形式控制器引用）。
     *
     * @param uri             URI
     * @param controllerClass 控制器类
     * @param methodName      方法名
     * @return 路由实例
     * @see ControllerActionResolver#resolve(Class, String)
     */
    public RouteDefinition post(String uri, Class<?> controllerClass, String methodName) {
        return addRoute("POST", uri, lazyResolve(controllerClass, methodName));
    }

    /**
     * 注册 PUT 路由（类对象形式控制器引用）。
     *
     * @param uri             URI
     * @param controllerClass 控制器类
     * @param methodName      方法名
     * @return 路由实例
     * @see ControllerActionResolver#resolve(Class, String)
     */
    public RouteDefinition put(String uri, Class<?> controllerClass, String methodName) {
        return addRoute("PUT", uri, lazyResolve(controllerClass, methodName));
    }

    /**
     * 注册 DELETE 路由（类对象形式控制器引用）。
     *
     * @param uri             URI
     * @param controllerClass 控制器类
     * @param methodName      方法名
     * @return 路由实例
     * @see ControllerActionResolver#resolve(Class, String)
     */
    public RouteDefinition delete(String uri, Class<?> controllerClass, String methodName) {
        return addRoute("DELETE", uri, lazyResolve(controllerClass, methodName));
    }

    /**
     * 注册 PATCH 路由（类对象形式控制器引用）。
     *
     * @param uri             URI
     * @param controllerClass 控制器类
     * @param methodName      方法名
     * @return 路由实例
     * @see ControllerActionResolver#resolve(Class, String)
     */
    public RouteDefinition patch(String uri, Class<?> controllerClass, String methodName) {
        return addRoute("PATCH", uri, lazyResolve(controllerClass, methodName));
    }

    /**
     * 注册多方法路由（类对象形式控制器引用）。
     *
     * @param uri             URI
     * @param controllerClass 控制器类
     * @param methodName      方法名
     * @return 路由组实例
     * @see ControllerActionResolver#resolve(Class, String)
     */
    public Router all(String uri, Class<?> controllerClass, String methodName) {
        return addMultiRoute(new String[]{"GET", "POST", "PUT", "DELETE", "PATCH"}, uri, lazyResolve(controllerClass, methodName));
    }

    /**
     * 创建延迟解析的 Runner（字符串形式）。
     * <p>
     * 控制器引用在首次请求时才解析，保证路由注册时控制器尚未注册也能正常工作。
     * {@link ControllerActionResolver} 内部缓存解析结果，后续请求无额外开销。
     */
    private Controllers.Runner lazyResolve(String controllerAction) {
        return request -> ControllerActionResolver.resolve(controllerAction).handle(request);
    }

    /**
     * 创建延迟解析的 Runner（类对象形式）。
     */
    private Controllers.Runner lazyResolve(Class<?> controllerClass, String methodName) {
        return request -> ControllerActionResolver.resolve(controllerClass, methodName).handle(request);
    }

    /**
     * 注册静态资源目录。
     * <p>
     * 匹配 {@code urlPrefix} 前缀的 GET 请求会从指定目录加载静态文件。
     * <p>
     * 示例：
     * <pre>
     * router.serveStatic("/static", "classpath:/static/", 3600);
     * // 访问 /static/css/app.css → classpath:/static/css/app.css
     * </pre>
     *
     * @param urlPrefix   URL 前缀（如 {@code /static}）
     * @param location    资源目录（如 {@code classpath:/static/} 或 {@code file:./public/}）
     * @param cacheMaxAge 缓存时间（秒）
     * @return 静态资源路由实例
     */
    public StaticResourceRoute serveStatic(String urlPrefix, String location, int cacheMaxAge) {
        StaticResourceRoute route = new StaticResourceRoute(urlPrefix, location, cacheMaxAge);
        RouteDefinition r = addRoute("GET", urlPrefix + "/{path}", route);
        r.name("static:" + urlPrefix);
        return route;
    }

    /**
     * 注册静态资源目录（默认缓存 1 小时）。
     *
     * @param urlPrefix URL 前缀
     * @param location  资源目录
     * @return 静态资源路由实例
     */
    public StaticResourceRoute serveStatic(String urlPrefix, String location) {
        return serveStatic(urlPrefix, location, 3600);
    }

    /**
     * 注册多目录静态资源（按顺序回退查找）。
     *
     * @param urlPrefix   URL 前缀
     * @param locations   资源目录列表
     * @param cacheMaxAge 缓存时间（秒）
     * @return 静态资源路由实例
     */
    public StaticResourceRoute serveStatic(String urlPrefix, java.util.List<String> locations, int cacheMaxAge) {
        StaticResourceRoute route = new StaticResourceRoute(urlPrefix, locations, cacheMaxAge);
        RouteDefinition r = addRoute("GET", urlPrefix + "/{path}", route);
        r.name("static:" + urlPrefix);
        return route;
    }

    public RouteDefinition addRoute(String method, String uri, Controllers.Runner action) {
        RouteDefinition route = new RouteDefinition(method, uri, action);
        route.setRouter(this);
        routes.add(route);
        return route;
    }

    public Router addMultiRoute(String[] method, String uri, Controllers.Runner action) {
        Router groupRouter = new Router();
        groupRouter.setParentRouter(this);
        for (String m : method) {
            groupRouter.addRoute(m, uri, action);
        }
        routers.add(groupRouter);
        return groupRouter;
    }

    /**
     * 创建路由分组，支持 prefix / namespace / name / middleware 参数。
     * <p>
     * 对齐 Laravel {@code Route::group(['prefix' => 'admin', 'middleware' => ['auth:admin']], ...)}。
     * <p>
     * {@link Route.Group#MIDDLEWARE} 的值支持以下类型：
     * <ul>
     *   <li>{@code String} — 单个别名（如 {@code "auth:api"}）</li>
     *   <li>{@code String[]} — 多个别名（如 {@code new String[]{"auth:admin", "permission:admin"}}）</li>
     *   <li>{@code List<String>} — 别名列表</li>
     * </ul>
     *
     * <h3>示例</h3>
     * <pre>
     * router.group(Map.of(
     *     Route.Group.PREFIX, "admin",
     *     Route.Group.MIDDLEWARE, new String[]{"auth:admin", "permission:admin"}
     * ), admin -> {
     *     admin.get("/home", "HomeController::index");
     * });
     * </pre>
     *
     * @param params 分组参数（prefix / namespace / name / middleware）
     * @param router 回调（接收子 Router）
     * @return 子 Router
     */
    public Router group(Map<Route.Group, ?> params, Consumer<Router> router) {
        Router groupRouter = new Router();
        groupRouter.setParentRouter(this);
        params.forEach((key, value) -> {
            if (key.equals(Route.Group.NAMESPACE)) {
                groupRouter.setNamespace((String) value);
            } else if (key.equals(Route.Group.PREFIX)) {
                groupRouter.setPrefix((String) value);
            } else if (key.equals(Route.Group.NAME)) {
                groupRouter.setName((String) value);
            } else if (key.equals(Route.Group.MIDDLEWARE)) {
                applyGroupMiddleware(groupRouter, value);
            }
        });
        router.accept(groupRouter);
        routers.add(groupRouter);
        return groupRouter;
    }

    /**
     * 将中间件参数应用到分组 Router。
     */
    @SuppressWarnings("unchecked")
    private void applyGroupMiddleware(Router groupRouter, Object value) {
        if (value instanceof String) {
            groupRouter.middleware((String) value);
        } else if (value instanceof String[]) {
            groupRouter.middleware((String[]) value);
        } else if (value instanceof List) {
            List<String> list = (List<String>) value;
            groupRouter.middleware(list.toArray(new String[0]));
        }
    }

    /**
     * 添加子路由器（供静态门面 {@link Route#group(Map, Runnable)} 使用）。
     * <p>
     * 通常不需要手动调用，路由组通过 {@link #group(Map, Consumer)} 自动添加。
     *
     * @param router 子路由器
     */
    public void addGroupRouter(Router router) {
        router.setParentRouter(this);
        routers.add(router);
    }

    public List<RouteDefinition> getAllRoutes() {
        List<RouteDefinition> routes = new ArrayList<>();
        routers.forEach(router -> routes.addAll(router.getAllRoutes()));
        routes.addAll(this.routes);
        return routes;
    }

    public List<Middleware> getAllMiddlewares() {
        List<Middleware> middlewares = new ArrayList<>();
        // 解析本路由器的中间件规格（直接中间件 + 别名/类名表达式 + 类对象 + 类+参数）
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
        // 递归添加父路由器中间件
        if (parentRouter != null)
            middlewares.addAll(parentRouter.getAllMiddlewares());
        return middlewares;
    }

    protected String generateFullUri() {
        if (parentRouter == null)
            return normalizeUri(prefix);
        return normalizeUri(parentRouter.generateFullUri() + "/" + prefix);
    }

    protected String generateFullNamespace() {
        if (parentRouter == null)
            return normalizeNamesapce(namespace);
        return normalizeNamesapce(parentRouter.generateFullNamespace() + "." + namespace);
    }

    protected String generateFullName() {
        if (parentRouter == null)
            return normalizeName(name);
        return normalizeName(parentRouter.generateFullName() + "." + name);
    }
}
