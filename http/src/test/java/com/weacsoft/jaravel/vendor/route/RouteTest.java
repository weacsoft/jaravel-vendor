package com.weacsoft.jaravel.vendor.route;

import com.weacsoft.jaravel.vendor.http.controller.Controllers;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import com.weacsoft.jaravel.vendor.http.controller.response.ResponseBuilder;
import com.weacsoft.jaravel.vendor.http.middleware.Middleware;
import com.weacsoft.jaravel.vendor.http.middleware.MiddlewareAliasRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Route} URI 生成、fullUri 合并、命名空间与中间件测试。
 * 包含直接中间件和别名中间件两种模式。
 */
class RouteTest {

    private static final Controllers.Runner NOOP = request -> ResponseBuilder.ok();

    @BeforeEach
    void setUp() {
        MiddlewareAliasRegistry.getGlobal().clear();
    }

    @AfterEach
    void tearDown() {
        MiddlewareAliasRegistry.getGlobal().clear();
    }

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
        Middleware m1 = (request, next, params) -> ResponseBuilder.ok();
        Middleware m2 = (request, next, params) -> ResponseBuilder.ok();

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
        Router router = new Router();
        Route route = router.get("users//info", NOOP);
        assertEquals("/users/info", route.getFullUri());
    }

    // ========== 别名中间件测试 ==========

    @Test
    void testRouteMiddlewareByAlias() {
        Middleware authMw = (request, next, params) -> ResponseBuilder.ok();
        MiddlewareAliasRegistry.getGlobal().register("auth", authMw);

        Router router = new Router();
        Route route = router.get("/secret", NOOP).middleware("auth");

        List<Middleware> middlewares = route.getMiddlewares();
        assertEquals(1, middlewares.size());
        assertNotNull(middlewares.get(0), "别名应解析为非 null 的 Middleware 包装闭包");
    }

    @Test
    void testRouteMiddlewareByAliasWithParameter() {
        Middleware logMw = (request, next, params) -> ResponseBuilder.ok();
        MiddlewareAliasRegistry.getGlobal().register("log", logMw);

        Router router = new Router();
        Route route = router.get("/api", NOOP).middleware("log:api");

        List<Middleware> middlewares = route.getMiddlewares();
        assertEquals(1, middlewares.size());
        assertNotNull(middlewares.get(0));
    }

    @Test
    void testRouteMiddlewareMultipleAliasesInOrder() {
        Middleware authMw = (request, next, params) -> ResponseBuilder.ok();
        Middleware logMw = (request, next, params) -> ResponseBuilder.ok();
        Middleware corsMw = (request, next, params) -> ResponseBuilder.ok();
        MiddlewareAliasRegistry.getGlobal().register("auth", authMw);
        MiddlewareAliasRegistry.getGlobal().register("log", logMw);
        MiddlewareAliasRegistry.getGlobal().register("cors", corsMw);

        Router router = new Router();
        Route route = router.get("/api", NOOP).middleware("auth:api", "log", "cors");

        List<Middleware> middlewares = route.getMiddlewares();
        assertEquals(3, middlewares.size());
        assertNotNull(middlewares.get(0), "第一个应是 auth");
        assertNotNull(middlewares.get(1), "第二个应是 log");
        assertNotNull(middlewares.get(2), "第三个应是 cors");
    }

    @Test
    void testRouteMiddlewareMixedDirectAndAlias() {
        Middleware directMw = (request, next, params) -> ResponseBuilder.ok();
        Middleware aliasMw = (request, next, params) -> ResponseBuilder.ok();
        MiddlewareAliasRegistry.getGlobal().register("auth", aliasMw);

        Router router = new Router();
        Route route = router.get("/mixed", NOOP)
                .middleware(directMw)
                .middleware("auth")
                .middleware(directMw);

        List<Middleware> middlewares = route.getMiddlewares();
        assertEquals(3, middlewares.size());
        assertSame(directMw, middlewares.get(0), "第一个应是直接中间件（同实例）");
        assertNotNull(middlewares.get(1), "第二个应是别名解析的中间件包装闭包");
        assertSame(directMw, middlewares.get(2), "第三个应是直接中间件（同实例）");
    }

    @Test
    void testRouteMiddlewareAliasWithMultipleParameters() {
        final String[] capturedParams = {null};
        MiddlewareAliasRegistry.getGlobal().register("auth", (request, next, params) -> {
            capturedParams[0] = String.join(",", params);
            return ResponseBuilder.ok();
        });

        Router router = new Router();
        router.get("/api", NOOP).middleware("auth:api,admin");

        // 触发别名解析并执行中间件
        List<Middleware> middlewares = router.getAllRoutes().get(0).getMiddlewares();
        assertEquals(1, middlewares.size());
        middlewares.get(0).handle(null, req -> ResponseBuilder.ok());
        assertEquals("api,admin", capturedParams[0], "应正确解析多参数");
    }
}
