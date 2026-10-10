package com.weacsoft.jaravel.vendor.route;

import com.weacsoft.jaravel.vendor.http.controller.response.ResponseBuilder;
import com.weacsoft.jaravel.vendor.http.middleware.Middleware;
import com.weacsoft.jaravel.vendor.http.middleware.MiddlewareAliasRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 路由中间件顺序与静态资源匹配回归测试（审计 M3）。
 * <p>
 * <ul>
 *   <li><b>顺序</b>：必须与 Laravel 的洋葱模型一致 —— 父级（全局/根）最外层、组次之、路由最内。
 *       旧实现先加自身、后加父级，折叠后全局中间件反而最内层（例如「根挂 EncryptCookies、
 *       组挂 VerifyCsrfToken」会变成先验 CSRF 再解密 Cookie，恒定 419）。</li>
 *   <li><b>静态资源</b>：必须用 {@code {*path}}（capture-the-rest）才能匹配多段路径；
 *       {@code {path}} 只匹配单段（嵌套资源恒 404），而 Spring PathPattern 下 {@code /**}
 *       不产生 pathVariables（同样 404）。</li>
 * </ul>
 * 本仓库此前没有任何中间件顺序断言（既有用例只断言数量），因此顺序修复必须由本类钉死。
 */
class RouterMiddlewareOrderTest {

    @BeforeEach
    @AfterEach
    void clearAliases() {
        MiddlewareAliasRegistry.getGlobal().clear();
    }

    private static Middleware named(String name, List<String> log) {
        return (request, next, params) -> {
            log.add("enter:" + name);
            try {
                return next.apply(request);
            } finally {
                log.add("exit:" + name);
            }
        };
    }

    @Test
    void parentMiddlewareComesBeforeChildInResolvedChain() {
        Router root = new Router();
        Middleware global = named("global", new java.util.ArrayList<>());
        root.middleware(global);

        Middleware groupMw = named("group", new java.util.ArrayList<>());
        Router group = new Router();
        group.middleware(groupMw);
        root.addGroupRouter(group);

        Middleware routeMw = named("route", new java.util.ArrayList<>());
        RouteDefinition route = group.get("/x", request -> ResponseBuilder.ok());
        route.middleware(routeMw);

        List<Middleware> chain = route.getMiddlewares();
        assertEquals(3, chain.size(), "三层中间件都应解析出来: " + chain.size());
        assertSame(global, chain.get(0), "全局（父级）必须最外层");
        assertSame(groupMw, chain.get(1), "组中间件次之");
        assertSame(routeMw, chain.get(2), "路由自身中间件最内层");
    }

    @Test
    void handlerChainRunsOnionOrderEnterAndExit() {
        List<String> log = new java.util.ArrayList<>();
        Router root = new Router();
        root.middleware(named("global", log));
        Router group = new Router();
        group.middleware(named("group", log));
        root.addGroupRouter(group);
        RouteDefinition route = group.get("/x", request -> {
            log.add("action");
            return ResponseBuilder.ok();
        });
        route.middleware(named("route", log));

        route.getHandlerChain().apply(new com.weacsoft.jaravel.vendor.http.controller.request.Request());

        assertEquals(List.of("enter:global", "enter:group", "enter:route", "action",
                "exit:route", "exit:group", "exit:global"), log,
                "必须是洋葱顺序：进入正序、退出逆序");
    }

    @Test
    void staticRouteUsesCaptureTheRestPattern() {
        Router router = new Router();
        router.serveStatic("/static", "classpath:/static/");

        List<RouteDefinition> routes = router.getAllRoutes();
        assertEquals(1, routes.size());
        String uri = routes.get(0).generateFullUri();
        assertTrue(uri.contains("{*path}"),
                "静态资源必须用 {*path} 才能匹配多段路径（{path} 只匹配单段、/** 不产生 pathVariables）: " + uri);
    }

    @Test
    void multiDirectoryStaticRouteAlsoUsesCaptureTheRest() {
        Router router = new Router();
        router.serveStatic("/assets", List.of("classpath:/static/", "file:./public/"), 60);

        String uri = router.getAllRoutes().get(0).generateFullUri();
        assertTrue(uri.contains("{*path}"), "多目录静态资源同样必须支持多段路径: " + uri);
    }

    @Test
    void groupPrefixStillAppliesToRoutes() {
        Router root = new Router();
        root.group(Map.of(Route.Group.PREFIX, "api"), r -> r.get("/data", request -> ResponseBuilder.ok()));
        String uri = root.getAllRoutes().get(0).generateFullUri();
        assertTrue(uri.contains("/api/data"), "组前缀应拼进路由: " + uri);
    }
}