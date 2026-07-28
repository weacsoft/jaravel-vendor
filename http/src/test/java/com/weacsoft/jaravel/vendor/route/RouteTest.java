package com.weacsoft.jaravel.vendor.route;

import com.weacsoft.jaravel.vendor.http.controller.Controllers;
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
 * {@link Route} 静态路由门面测试。
 * <p>
 * 覆盖静态路由注册、无参闭包分组、流式构建器、嵌套分组中间件、ThreadLocal 上下文管理。
 */
class RouteTest {

    private static final Controllers.Runner NOOP = request -> ResponseBuilder.ok();

    @BeforeEach
    void setUp() {
        MiddlewareAliasRegistry.getGlobal().clear();
        Router rootRouter = new Router();
        Route.setRootRouter(rootRouter);
    }

    @AfterEach
    void tearDown() {
        MiddlewareAliasRegistry.getGlobal().clear();
        Route.clearContext();
    }

    @Test
    void testStaticGetRegistration() {
        Route.get("/users", "UserController::list");

        Router root = Route.currentRouter();
        List<RouteDefinition> routes = root.getAllRoutes();
        assertEquals(1, routes.size());
        assertEquals("GET", routes.get(0).getMethod());
        assertEquals("/users", routes.get(0).getUri());
    }

    @Test
    void testStaticMultipleHttpMethods() {
        Route.get("/users", "UserController::list");
        Route.post("/users", "UserController::create");
        Route.put("/users/1", "UserController::update");
        Route.delete("/users/1", "UserController::delete");
        Route.patch("/users/1", "UserController::patch");

        Router root = Route.currentRouter();
        List<RouteDefinition> routes = root.getAllRoutes();
        assertEquals(5, routes.size());
    }

    @Test
    void testStaticGroupWithRunnable() {
        Route.group(Map.of(Route.Group.PREFIX, "api"), () -> {
            Route.get("/users", "UserController::list");
            Route.post("/users", "UserController::create");
        });

        Router root = Route.currentRouter();
        List<RouteDefinition> routes = root.getAllRoutes();
        assertEquals(2, routes.size());
        for (RouteDefinition route : routes) {
            assertTrue(route.getFullUri().startsWith("/api/"),
                    "分组前缀应合并, 实际: " + route.getFullUri());
        }
    }

    @Test
    void testStaticNestedGroupsWithRunnable() {
        Route.group(Map.of(Route.Group.PREFIX, "api"), () -> {
            Route.group(Map.of(Route.Group.PREFIX, "v1"), () -> {
                Route.get("/users", "UserController::list");
            });
        });

        Router root = Route.currentRouter();
        List<RouteDefinition> routes = root.getAllRoutes();
        assertEquals(1, routes.size());
        assertEquals("/api/v1/users", routes.get(0).getFullUri());
    }

    @Test
    void testStaticGroupWithMiddleware() {
        Middleware authMw = (request, next, params) -> ResponseBuilder.ok();
        MiddlewareAliasRegistry.getGlobal().register("auth", authMw);

        Route.group(Map.of(
                Route.Group.PREFIX, "admin",
                Route.Group.MIDDLEWARE, new String[]{"auth:admin"}
        ), () -> {
            Route.get("/home", "HomeController::index");
            Route.get("/logout", "LoginController::logout");
        });

        Router root = Route.currentRouter();
        List<RouteDefinition> routes = root.getAllRoutes();
        assertEquals(2, routes.size());
        for (RouteDefinition route : routes) {
            List<Middleware> mws = route.getMiddlewares();
            assertEquals(1, mws.size(), "每个路由应有分组中间件");
        }
    }

    @Test
    void testFluentBuilderMiddlewarePrefixGroup() {
        Middleware authMw = (request, next, params) -> ResponseBuilder.ok();
        MiddlewareAliasRegistry.getGlobal().register("auth", authMw);

        Route.middleware("auth:admin").prefix("admin").group(() -> {
            Route.get("/home", "HomeController::index");
            Route.get("/logout", "LoginController::logout");
        });

        Router root = Route.currentRouter();
        List<RouteDefinition> routes = root.getAllRoutes();
        assertEquals(2, routes.size());
        for (RouteDefinition route : routes) {
            assertTrue(route.getFullUri().startsWith("/admin/"));
            List<Middleware> mws = route.getMiddlewares();
            assertEquals(1, mws.size(), "流式构建器的中间件应生效");
        }
    }

    @Test
    void testFluentBuilderNamespaceAndName() {
        Route.namespace("Admin").prefix("admin").name("adm").group(() -> {
            Route.get("/dashboard", "HomeController::index");
        });

        Router root = Route.currentRouter();
        List<RouteDefinition> routes = root.getAllRoutes();
        assertEquals(1, routes.size());
        RouteDefinition route = routes.get(0);
        assertEquals("/admin/dashboard", route.getFullUri());
        assertEquals("Admin", route.getFullNamespace());
    }

    @Test
    void testFluentBuilderChainingAllAttributes() {
        Middleware authMw = (request, next, params) -> ResponseBuilder.ok();
        Middleware logMw = (request, next, params) -> ResponseBuilder.ok();
        MiddlewareAliasRegistry.getGlobal().register("auth", authMw);
        MiddlewareAliasRegistry.getGlobal().register("log", logMw);

        Route.middleware("auth:api", "log")
                .prefix("api")
                .namespace("Api")
                .group(() -> {
                    Route.get("/data", "DataController::list");
                });

        Router root = Route.currentRouter();
        List<RouteDefinition> routes = root.getAllRoutes();
        assertEquals(1, routes.size());
        RouteDefinition route = routes.get(0);
        assertEquals("/api/data", route.getFullUri());
        assertEquals("Api", route.getFullNamespace());
        List<Middleware> mws = route.getMiddlewares();
        assertEquals(2, mws.size(), "应有 auth + log 两个中间件");
    }

    @Test
    void testStaticGroupMiddlewareInheritedByNestedGroup() {
        Middleware authMw = (request, next, params) -> ResponseBuilder.ok();
        Middleware logMw = (request, next, params) -> ResponseBuilder.ok();
        MiddlewareAliasRegistry.getGlobal().register("auth", authMw);
        MiddlewareAliasRegistry.getGlobal().register("log", logMw);

        Route.group(Map.of(
                Route.Group.PREFIX, "api",
                Route.Group.MIDDLEWARE, "auth:api"
        ), () -> {
            Route.group(Map.of(
                    Route.Group.PREFIX, "v1",
                    Route.Group.MIDDLEWARE, "log"
            ), () -> {
                Route.get("/users", "UserController::list");
            });
        });

        Router root = Route.currentRouter();
        List<RouteDefinition> routes = root.getAllRoutes();
        assertEquals(1, routes.size());
        assertEquals("/api/v1/users", routes.get(0).getFullUri());
        List<Middleware> mws = routes.get(0).getMiddlewares();
        assertEquals(2, mws.size(), "外层 auth + 内层 log = 2 个中间件");
    }

    @Test
    void testContextRestoredAfterGroup() {
        // group 前后，currentRouter 应为同一个根 Router
        Router before = Route.currentRouter();
        Route.group(Map.of(Route.Group.PREFIX, "api"), () -> {
            Route.get("/test", "TestController::test");
        });
        Router after = Route.currentRouter();

        assertSame(before, after, "group 执行后 ThreadLocal 上下文应恢复");
    }

    @Test
    void testRouteNameChainableAfterStaticGet() {
        Route.get("/users", "UserController::list").name("users.index");

        Router root = Route.currentRouter();
        List<RouteDefinition> routes = root.getAllRoutes();
        assertEquals(1, routes.size());
        assertEquals(".users.index", routes.get(0).getFullName());
    }

    @Test
    void testFluentBuilderWithMethodReference() {
        Middleware authMw = (request, next, params) -> ResponseBuilder.ok();
        MiddlewareAliasRegistry.getGlobal().register("auth", authMw);

        // 模拟 Laravel ->group(base_path('routes/api.php')) 的方法引用形式
        Route.middleware("auth").prefix("api").group(this::registerApiRoutes);

        Router root = Route.currentRouter();
        List<RouteDefinition> routes = root.getAllRoutes();
        assertEquals(2, routes.size());
    }

    /**
     * 模拟独立的路由注册方法（对齐 Laravel routes/api.php 文件）。
     */
    private void registerApiRoutes() {
        Route.get("/users", "UserController::list");
        Route.get("/posts", "PostController::list");
    }
}
