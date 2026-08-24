package com.weacsoft.jaravel.vendor.route;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * RouteHelper 单元测试：
 * - route(别名) 按路由名解析 URL（对齐 Laravel route('name')）
 * - url(路径) 单纯生成 URL，不校验是否存在（对齐 Laravel url('/path')）
 * - 静态 API 与 AppConfig.app().route() 流式 API 行为一致
 */
class RouteHelperTest {

    private Router sampleRouter() {
        Router router = new Router();
        router.group(Map.of(
                Route.Group.PREFIX, "admin",
                Route.Group.NAME, "admin"
        ), r -> r.get("/login", "LoginController::login").name("login"));
        router.get("/users/{id}", "UserController::show").name("user.show");
        return router;
    }

    @Test
    void routeResolvesByName() {
        RouteHelper.setRouter(sampleRouter());

        // 静态调用
        assertEquals("/admin/login", RouteHelper.route("admin.login"));
        assertEquals("/users/5", RouteHelper.route("user.show", Map.of("id", 5)));

        // 流式调用 AppConfig.app().route().route(...)
        assertEquals("/admin/login", RouteHelper.instance().route("admin.login"));
        assertEquals("/users/5", RouteHelper.instance().route("user.show", Map.of("id", 5)));
    }

    @Test
    void routeWithUnmatchedParamsAppendsQueryString() {
        RouteHelper.setRouter(sampleRouter());

        // 无占位符路由 + Map 参数 → 未匹配参数追加为查询串（对齐 Laravel route('name', [...])）
        assertEquals("/admin/login?id=1", RouteHelper.route("admin.login", Map.of("id", "1")));
        // 流式调用行为一致
        assertEquals("/admin/login?id=1", RouteHelper.instance().route("admin.login", Map.of("id", "1")));
    }

    @Test
    void urlNormalizesPath() {
        // 静态调用
        assertEquals("/admin/login", RouteHelper.url("admin/login"));
        assertEquals("/admin/login", RouteHelper.url("/admin/login"));
        assertEquals("https://example.com/a", RouteHelper.url("https://example.com/a"));
        assertEquals("/", RouteHelper.url(""));
        assertEquals("/", RouteHelper.url(null));

        // 流式调用 AppConfig.app().route().url(...)
        assertEquals("/admin/login", RouteHelper.instance().url("admin/login"));
    }

    @Test
    void routeThrowsWhenNotInitialized() {
        RouteHelper.setRouter(null);
        assertThrows(IllegalStateException.class, () -> RouteHelper.route("admin.login"));
    }
}
