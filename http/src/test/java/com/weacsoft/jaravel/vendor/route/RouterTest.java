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
 * {@link Router} 路由注册、分组与中间件链测试。
 * <p>
 * 覆盖 GET / POST / PUT / DELETE / PATCH 注册、group 分组（prefix/namespace/name）、
 * 中间件挂载与链式合并、getAllRoutes 递归收集、别名中间件。
 */
class RouterTest {

    /** 空动作占位 */
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
    void testHttpMethodRegistration() {
        Router router = new Router();
        router.get("/users", NOOP);
        router.post("/users", NOOP);
        router.put("/users/1", NOOP);
        router.delete("/users/1", NOOP);
        router.patch("/users/1", NOOP);

        List<Route> routes = router.getAllRoutes();
        assertEquals(5, routes.size());

        // 验证每个路由的方法与 URI
        assertEquals("GET", routes.get(0).getMethod());
        assertEquals("POST", routes.get(1).getMethod());
        assertEquals("PUT", routes.get(2).getMethod());
        assertEquals("DELETE", routes.get(3).getMethod());
        assertEquals("PATCH", routes.get(4).getMethod());
    }

    @Test
    void testGetRouteReturnsRouteInstance() {
        Router router = new Router();
        Route route = router.get("/items", NOOP);

        assertNotNull(route);
        assertEquals("GET", route.getMethod());
        assertEquals("/items", route.getUri());
        assertSame(router, getRouter(route), "route 的 router 应为当前 router");
    }

    @Test
    void testGroupWithPrefix() {
        Router router = new Router();
        router.group(Map.of(Route.Group.PREFIX, "api"), r -> {
            r.get("/users", NOOP);
            r.post("/orders", NOOP);
        });

        List<Route> routes = router.getAllRoutes();
        assertEquals(2, routes.size());

        // 分组前缀应合并到 fullUri
        Route usersRoute = routes.stream()
                .filter(r -> r.getUri().equals("/users"))
                .findFirst().orElseThrow();
        assertEquals("/api/users", usersRoute.getFullUri());

        Route ordersRoute = routes.stream()
                .filter(r -> r.getUri().equals("/orders"))
                .findFirst().orElseThrow();
        assertEquals("/api/orders", ordersRoute.getFullUri());
    }

    @Test
    void testNestedGroupsMergePrefix() {
        Router router = new Router();
        router.group(Map.of(Route.Group.PREFIX, "api"), api ->
                api.group(Map.of(Route.Group.PREFIX, "v1"), v1 -> {
                    v1.get("/users", NOOP);
                    v1.get("/posts", NOOP);
                }));

        List<Route> routes = router.getAllRoutes();
        assertEquals(2, routes.size());
        for (Route route : routes) {
            assertTrue(route.getFullUri().startsWith("/api/v1/"),
                    "嵌套分组前缀应合并, 实际: " + route.getFullUri());
        }
    }

    @Test
    void testGroupWithNamespaceAndName() {
        Router router = new Router();
        router.group(Map.of(
                Route.Group.NAMESPACE, "admin",
                Route.Group.NAME, "adm"
        ), r -> r.get("/dashboard", NOOP).name("dashboard"));

        List<Route> routes = router.getAllRoutes();
        assertEquals(1, routes.size());
        Route route = routes.get(0);

        // 命名空间应合并
        assertEquals("admin", route.getFullNamespace());
        // 名称应合并：normalizeName 带前导点
        assertEquals(".adm.dashboard", route.getFullName());
    }

    @Test
    void testMiddlewareOnRouter() {
        Router router = new Router();
        Middleware m1 = (request, next, params) -> ResponseBuilder.ok();
        Middleware m2 = (request, next, params) -> ResponseBuilder.ok();
        router.middleware(m1, m2);
        router.get("/secured", NOOP);

        List<Middleware> middlewares = router.getAllMiddlewares();
        assertEquals(2, middlewares.size());
        assertSame(m1, middlewares.get(0));
        assertSame(m2, middlewares.get(1));
    }

    @Test
    void testMiddlewareInheritedFromParentGroup() {
        Router router = new Router();
        Middleware parentMw = (request, next, params) -> ResponseBuilder.ok();
        router.middleware(parentMw);

        Middleware childMw = (request, next, params) -> ResponseBuilder.ok();
        router.group(Map.of(Route.Group.PREFIX, "api"), r -> {
            r.middleware(childMw);
            r.get("/data", NOOP);
        });

        List<Route> routes = router.getAllRoutes();
        assertEquals(1, routes.size());
        List<Middleware> routeMiddlewares = routes.get(0).getMiddlewares();
        // 父级中间件 + 子级中间件
        assertEquals(2, routeMiddlewares.size());
    }

    @Test
    void testAllMethodRegistersMultipleHttpMethods() {
        Router router = new Router();
        router.all("/anything", NOOP);

        List<Route> routes = router.getAllRoutes();
        assertEquals(5, routes.size(), "all 应注册 GET/POST/PUT/DELETE/PATCH 五种方法");
    }

    @Test
    void testGetAllRoutesCollectsFromRootAndGroups() {
        Router router = new Router();
        router.get("/root", NOOP);
        router.group(Map.of(Route.Group.PREFIX, "g"), r -> {
            r.get("/a", NOOP);
            r.get("/b", NOOP);
        });

        List<Route> routes = router.getAllRoutes();
        assertEquals(3, routes.size(), "应收集根路由与分组路由");
    }

    // ========== 别名中间件测试 ==========

    @Test
    void testRouterMiddlewareByAlias() {
        Middleware authMw = (request, next, params) -> ResponseBuilder.ok();
        MiddlewareAliasRegistry.getGlobal().register("auth", authMw);

        Router router = new Router();
        router.middleware("auth");
        router.get("/secured", NOOP);

        List<Middleware> middlewares = router.getAllMiddlewares();
        assertEquals(1, middlewares.size());
        assertNotNull(middlewares.get(0), "路由器级别名应解析为非 null 的 Middleware 包装闭包");
    }

    @Test
    void testRouterMiddlewareByAliasWithParameter() {
        Middleware logMw = (request, next, params) -> ResponseBuilder.ok();
        MiddlewareAliasRegistry.getGlobal().register("log", logMw);

        Router router = new Router();
        router.middleware("log:api");
        router.get("/api", NOOP);

        List<Middleware> middlewares = router.getAllMiddlewares();
        assertEquals(1, middlewares.size());
        assertNotNull(middlewares.get(0));
    }

    @Test
    void testRouterMiddlewareMultipleAliasesInOrder() {
        Middleware authMw = (request, next, params) -> ResponseBuilder.ok();
        Middleware logMw = (request, next, params) -> ResponseBuilder.ok();
        MiddlewareAliasRegistry.getGlobal().register("auth", authMw);
        MiddlewareAliasRegistry.getGlobal().register("log", logMw);

        Router router = new Router();
        router.middleware("auth:api", "log");
        router.get("/api", NOOP);

        List<Middleware> middlewares = router.getAllMiddlewares();
        assertEquals(2, middlewares.size());
        assertNotNull(middlewares.get(0), "第一个应是 auth");
        assertNotNull(middlewares.get(1), "第二个应是 log");
    }

    @Test
    void testAliasMiddlewareInheritedFromParentGroup() {
        Middleware parentMw = (request, next, params) -> ResponseBuilder.ok();
        Middleware childMw = (request, next, params) -> ResponseBuilder.ok();
        MiddlewareAliasRegistry.getGlobal().register("auth", parentMw);
        MiddlewareAliasRegistry.getGlobal().register("log", childMw);

        Router router = new Router();
        router.middleware("auth");

        router.group(Map.of(Route.Group.PREFIX, "api"), r -> {
            r.middleware("log");
            r.get("/data", NOOP);
        });

        List<Route> routes = router.getAllRoutes();
        assertEquals(1, routes.size());
        List<Middleware> routeMiddlewares = routes.get(0).getMiddlewares();
        // 父级别名中间件 + 子级别名中间件
        assertEquals(2, routeMiddlewares.size(), "父级和子级别名中间件都应被解析");
    }

    @Test
    void testMixedDirectAndAliasMiddlewareOnRouter() {
        Middleware directMw = (request, next, params) -> ResponseBuilder.ok();
        Middleware aliasMw = (request, next, params) -> ResponseBuilder.ok();
        MiddlewareAliasRegistry.getGlobal().register("auth", aliasMw);

        Router router = new Router();
        router.middleware(directMw).middleware("auth").middleware(directMw);
        router.get("/mixed", NOOP);

        List<Middleware> middlewares = router.getAllMiddlewares();
        assertEquals(3, middlewares.size());
        assertSame(directMw, middlewares.get(0), "第一个应是直接中间件（同实例）");
        assertNotNull(middlewares.get(1), "第二个应是别名解析的中间件包装闭包");
        assertSame(directMw, middlewares.get(2), "第三个应是直接中间件（同实例）");
    }

    @Test
    void testAliasMiddlewareWithMultipleParametersOnRouter() {
        final String[] capturedParams = {null};
        MiddlewareAliasRegistry.getGlobal().register("auth", (request, next, params) -> {
            capturedParams[0] = String.join(",", params);
            return ResponseBuilder.ok();
        });

        Router router = new Router();
        router.middleware("auth:api,admin");
        router.get("/api", NOOP);

        // 触发别名解析并执行中间件
        List<Middleware> middlewares = router.getAllMiddlewares();
        assertEquals(1, middlewares.size());
        middlewares.get(0).handle(null, req -> ResponseBuilder.ok());
        assertEquals("api,admin", capturedParams[0], "应正确解析多参数");
    }

    // ========== 类对象中间件测试 ==========

    @Test
    void testRouterMiddlewareByClass() {
        Middleware logMw = (request, next, params) -> ResponseBuilder.ok();
        MiddlewareAliasRegistry.getGlobal().register(logMw);

        Router router = new Router();
        router.middleware(logMw.getClass());
        router.get("/log", NOOP);

        List<Middleware> middlewares = router.getAllMiddlewares();
        assertEquals(1, middlewares.size());
        assertNotNull(middlewares.get(0), "按类对象应解析为非 null 的 Middleware");
    }

    @Test
    void testRouterMiddlewareByClassWithParams() {
        final String[] capturedParams = {null};
        Middleware authMw = (request, next, params) -> {
            capturedParams[0] = String.join(",", params);
            return ResponseBuilder.ok();
        };
        MiddlewareAliasRegistry.getGlobal().register(authMw);

        Router router = new Router();
        router.middleware(authMw.getClass(), "api", "admin");
        router.get("/api", NOOP);

        List<Middleware> middlewares = router.getAllMiddlewares();
        assertEquals(1, middlewares.size());
        middlewares.get(0).handle(null, req -> ResponseBuilder.ok());
        assertEquals("api,admin", capturedParams[0], "类对象+参数应正确传递");
    }

    @Test
    void testRouterMiddlewareByClassName() {
        Middleware logMw = (request, next, params) -> ResponseBuilder.ok();
        MiddlewareAliasRegistry.getGlobal().register(logMw);

        Router router = new Router();
        String className = logMw.getClass().getSimpleName();
        router.middleware(className);
        router.get("/log", NOOP);

        List<Middleware> middlewares = router.getAllMiddlewares();
        assertEquals(1, middlewares.size());
        assertNotNull(middlewares.get(0), "按类名应解析为非 null 的 Middleware");
    }

    @Test
    void testRouterMiddlewareByClassNameWithParams() {
        final String[] capturedParams = {null};
        Middleware authMw = (request, next, params) -> {
            capturedParams[0] = String.join(",", params);
            return ResponseBuilder.ok();
        };
        MiddlewareAliasRegistry.getGlobal().register(authMw);

        Router router = new Router();
        String className = authMw.getClass().getSimpleName();
        router.middleware(className + ":api,admin");
        router.get("/api", NOOP);

        List<Middleware> middlewares = router.getAllMiddlewares();
        assertEquals(1, middlewares.size());
        middlewares.get(0).handle(null, req -> ResponseBuilder.ok());
        assertEquals("api,admin", capturedParams[0], "类名表达式应正确解析参数");
    }

    @Test
    void testRouterMiddlewareMixedDirectAliasAndClass() {
        Middleware directMw = (request, next, params) -> ResponseBuilder.ok();
        Middleware aliasMw = (request, next, params) -> ResponseBuilder.ok();
        Middleware classMw = (request, next, params) -> ResponseBuilder.ok();
        MiddlewareAliasRegistry.getGlobal().register("auth", aliasMw);
        MiddlewareAliasRegistry.getGlobal().register(classMw);

        Router router = new Router();
        router.middleware(directMw).middleware("auth").middleware(classMw.getClass());
        router.get("/mixed", NOOP);

        List<Middleware> middlewares = router.getAllMiddlewares();
        assertEquals(3, middlewares.size());
        assertSame(directMw, middlewares.get(0), "第一个应是直接中间件");
        assertNotNull(middlewares.get(1), "第二个应是别名解析的闭包");
        assertNotNull(middlewares.get(2), "第三个应是类对象解析的闭包");
    }

    /** 通过反射获取 route 的 router 字段（无 public getter） */
    private Router getRouter(Route route) {
        try {
            var field = Route.class.getDeclaredField("router");
            field.setAccessible(true);
            return (Router) field.get(route);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
