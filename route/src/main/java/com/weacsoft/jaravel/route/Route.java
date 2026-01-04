package com.weacsoft.jaravel.route;

import com.weacsoft.jaravel.controller.Controller;
import com.weacsoft.jaravel.middleware.Middleware;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.weacsoft.jaravel.route.RouteService.*;

public class Route {
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
    private Controller.Runner action;
    @Setter
    private Router router;
    private final List<Middleware> middlewares = new CopyOnWriteArrayList<>();

    public Route(String method, String uri, Controller.Runner action) {
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

    @Getter
    private String uri;

    public Route middleware(Middleware... middleware) {
        middlewares.addAll(List.of(middleware));
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

    public String generateFullNamespace() {
        return normalizeNamesapce(router.generateFullNamespace() + "." + namespace);
    }

    protected String generateFullName() {
        return normalizeName(router.generateFullName() + "." + name);
    }

    public List<Middleware> getMiddlewares() {
        List<Middleware> middlewares=new CopyOnWriteArrayList<>();
        middlewares.addAll(router.getAllMiddlewares());
        middlewares.addAll(this.middlewares);
        return middlewares;
    }
}
