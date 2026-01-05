package com.weacsoft.jaravel.route;

import com.weacsoft.jaravel.controller.Controller;
import com.weacsoft.jaravel.middleware.Middleware;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static com.weacsoft.jaravel.route.RouteService.*;

public class Router {
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

    private final List<Route> routes = new CopyOnWriteArrayList<>();
    private final List<Router> routers = new CopyOnWriteArrayList<>();
    private final List<Middleware> middlewares = new CopyOnWriteArrayList<>();

    public Router middleware(Middleware... middleware) {
        middlewares.addAll(Arrays.asList(middleware));
        return this;
    }

    public Route get(String uri, Controller.Runner action) {
        return addRoute("GET", uri, action);
    }

    public Route post(String uri, Controller.Runner action) {
        return addRoute("POST", uri, action);
    }

    public Route put(String uri, Controller.Runner action) {
        return addRoute("PUT", uri, action);
    }

    public Route delete(String uri, Controller.Runner action) {
        return addRoute("DELETE", uri, action);
    }

    public Route patch(String uri, Controller.Runner action) {
        return addRoute("PATCH", uri, action);
    }

    public Router all(String uri, Controller.Runner action) {
        return addMultiRoute(new String[]{"GET", "POST", "PUT", "DELETE", "PATCH"}, uri, action);
    }

    public Route addRoute(String method, String uri, Controller.Runner action) {
        Route route = new Route(method, uri, action);
        route.setRouter(this);
        routes.add(route);
        return route;
    }

    public Router addMultiRoute(String[] method, String uri, Controller.Runner action) {
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
        return this;
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
