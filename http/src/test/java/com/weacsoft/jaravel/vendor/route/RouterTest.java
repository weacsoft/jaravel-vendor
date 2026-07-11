package com.weacsoft.jaravel.vendor.route;

import com.weacsoft.jaravel.vendor.http.controller.Controllers;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import com.weacsoft.jaravel.vendor.http.controller.response.ResponseBuilder;
import com.weacsoft.jaravel.vendor.http.middleware.Middleware;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Router} 路由注册、分组与中间件链测试。
 * <p>
 * 覆盖 GET / POST / PUT / DELETE / PATCH 注册、group 分组（prefix/namespace/name）、
 * 中间件挂载与链式合并、getAllRoutes 递归收集。
 */
class RouterTest {

    /** 空动作占位 */
    private static final Controllers.Runner NOOP = request -> ResponseBuilder.ok();

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
        Middleware m1 = (request, next) -> ResponseBuilder.ok();
        Middleware m2 = (request, next) -> ResponseBuilder.ok();
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
        Middleware parentMw = (request, next) -> ResponseBuilder.ok();
        router.middleware(parentMw);

        Middleware childMw = (request, next) -> ResponseBuilder.ok();
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
