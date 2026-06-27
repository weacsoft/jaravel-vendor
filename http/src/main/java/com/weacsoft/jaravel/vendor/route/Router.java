package com.weacsoft.jaravel.vendor.route;

import com.weacsoft.jaravel.vendor.controller.Controllers;
import com.weacsoft.jaravel.vendor.http.staticresource.StaticResourceRoute;
import com.weacsoft.jaravel.vendor.middleware.Middleware;
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
    private final List<Route> routes = new CopyOnWriteArrayList<>();
    private final List<Router> routers = new CopyOnWriteArrayList<>();
    private final List<Middleware> middlewares = new CopyOnWriteArrayList<>();
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

    public Router middleware(Middleware... middleware) {
        middlewares.addAll(Arrays.asList(middleware));
        return this;
    }

    public Route get(String uri, Controllers.Runner action) {
        return addRoute("GET", uri, action);
    }

    public Route post(String uri, Controllers.Runner action) {
        return addRoute("POST", uri, action);
    }

    public Route put(String uri, Controllers.Runner action) {
        return addRoute("PUT", uri, action);
    }

    public Route delete(String uri, Controllers.Runner action) {
        return addRoute("DELETE", uri, action);
    }

    public Route patch(String uri, Controllers.Runner action) {
        return addRoute("PATCH", uri, action);
    }

    public Router all(String uri, Controllers.Runner action) {
        return addMultiRoute(new String[]{"GET", "POST", "PUT", "DELETE", "PATCH"}, uri, action);
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
        Route r = addRoute("GET", urlPrefix + "/{path}", route);
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
        Route r = addRoute("GET", urlPrefix + "/{path}", route);
        r.name("static:" + urlPrefix);
        return route;
    }

    public Route addRoute(String method, String uri, Controllers.Runner action) {
        Route route = new Route(method, uri, action);
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

    public Router group(Map<Route.Group, String> params, Consumer<Router> router) {
        Router groupRouter = new Router();
        groupRouter.setParentRouter(this);
        params.forEach((key, value) -> {
            if (key.equals(Route.Group.NAMESPACE)) {
                groupRouter.setNamespace(value);
            } else if (key.equals(Route.Group.PREFIX)) {
                groupRouter.setPrefix(value);
            } else if (key.equals(Route.Group.NAME)) {
                groupRouter.setName(value);
            }
        });
        router.accept(groupRouter);
        routers.add(groupRouter);
        return groupRouter;
    }

    public List<Route> getAllRoutes() {
        List<Route> routes = new ArrayList<>();
        routers.forEach(router -> routes.addAll(router.getAllRoutes()));
        routes.addAll(this.routes);
        return routes;
    }

    public List<Middleware> getAllMiddlewares() {
        List<Middleware> middlewares = new ArrayList<>();
        middlewares.addAll(this.middlewares);
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