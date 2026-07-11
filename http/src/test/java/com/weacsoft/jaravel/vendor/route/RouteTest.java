package com.weacsoft.jaravel.vendor.route;

import com.weacsoft.jaravel.vendor.http.controller.Controllers;
import com.weacsoft.jaravel.vendor.http.controller.response.ResponseBuilder;
import com.weacsoft.jaravel.vendor.http.middleware.Middleware;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Route} URI 生成、fullUri 合并、命名空间与中间件测试。
 */
class RouteTest {

    private static final Controllers.Runner NOOP = request -> ResponseBuilder.ok();

    @Test
    void testBasicRouteProperties() {
        Router router = new Router();
        Route route = router.get("/users/{id}", NOOP);

        assertEquals("GET", route.getMethod());
        assertEquals("/users/{id}", route.getUri());
        assertEquals("/users/{id}", route.getFullUri(), "无前缀时 fullUri 应等于 uri");
        assertNotNull(route.getAction());
    }

    @Test
    void testFullUriWithRouterPrefix() {
        Router router = new Router();
        router.setPrefix("api");
        Route route = router.get("/users", NOOP);

        assertEquals("/api/users", route.getFullUri());
    }

    @Test
    void testFullUriWithGroupPrefix() {
        Router router = new Router();
        router.group(Map.of(Route.Group.PREFIX, "api/v1"), r -> {
            r.get("/users", NOOP);
            r.get("/posts/{id}", NOOP);
        });

        List<Route> routes = router.getAllRoutes();
        assertEquals("/api/v1/users", routes.get(0).getFullUri());
        assertEquals("/api/v1/posts/{id}", routes.get(1).getFullUri());
    }

    @Test
    void testRouteNameAndFullName() {
        Router router = new Router();
        Route route = router.get("/users", NOOP).name("users.index");

        assertEquals("users.index", route.getName());
        // normalizeName 实现会给 fullName 补前导 "."
        assertEquals(".users.index", route.getFullName(), "fullName 由 normalizeName 处理，带前导点");
    }

    @Test
    void testFullNameWithGroupNamePrefix() {
        Router router = new Router();
        router.group(Map.of(Route.Group.NAME, "admin"), r ->
                r.get("/users", NOOP).name("users.list"));

        Route route = router.getAllRoutes().get(0);
        assertEquals(".admin.users.list", route.getFullName());
    }

    @Test
    void testRouteMiddleware() {
        Middleware m1 = (request, next) -> ResponseBuilder.ok();
        Middleware m2 = (request, next) -> ResponseBuilder.ok();

        Router router = new Router();
        Route route = router.get("/secret", NOOP).middleware(m1, m2);

        List<Middleware> middlewares = route.getMiddlewares();
        assertEquals(2, middlewares.size());
        assertSame(m1, middlewares.get(0));
        assertSame(m2, middlewares.get(1));
    }

    @Test
    void testFullNamespaceWithGroup() {
        Router router = new Router();
        router.group(Map.of(Route.Group.NAMESPACE, "app.http.controller"), r ->
                r.get("/demo", NOOP));

        Route route = router.getAllRoutes().get(0);
        assertEquals("app.http.controller", route.getFullNamespace());
    }

    @Test
    void testFullUriNormalization() {
        // 带多余斜杠的 URI 应被规范化
        Router router = new Router();
        Route route = router.get("users//info", NOOP);
        assertEquals("/users/info", route.getFullUri());
    }
}
