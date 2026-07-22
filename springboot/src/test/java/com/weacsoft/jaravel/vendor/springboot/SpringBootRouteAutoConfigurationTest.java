package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import com.weacsoft.jaravel.vendor.http.controller.response.ResponseBuilder;
import com.weacsoft.jaravel.vendor.http.middleware.Middleware;
import com.weacsoft.jaravel.vendor.http.middleware.MiddlewareAliasRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SpringBootRouteAutoConfiguration} 中间件别名自动扫描测试。
 * <p>
 * 验证 {@code scanMiddlewareAliases} 方法能正确扫描 {@code @MiddlewareAlias} 注解的 Bean，
 * 注册到 {@link MiddlewareAliasRegistry} 全局注册表。
 * 覆盖三种场景：有别名、无别名、null 上下文。
 */
class SpringBootRouteAutoConfigurationTest {

    private SpringBootRouteAutoConfiguration configuration;

    @BeforeEach
    void setUp() {
        MiddlewareAliasRegistry.getGlobal().clear();
        configuration = new SpringBootRouteAutoConfiguration();
    }

    @AfterEach
    void tearDown() {
        MiddlewareAliasRegistry.getGlobal().clear();
    }

    @Test
    void testScanWithNullContext() {
        assertDoesNotThrow(() -> configuration.scanMiddlewareAliases(null),
                "applicationContext 为 null 时应安全跳过");
    }

    @Test
    void testScanSingleAliasMiddleware() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("authMiddleware", TestAuthMiddleware.class);
        context.refresh();

        configuration.scanMiddlewareAliases(context);

        assertTrue(MiddlewareAliasRegistry.getGlobal().isRegistered("auth"),
                "应注册别名 'auth'");
        assertTrue(MiddlewareAliasRegistry.getGlobal().isClassRegistered(TestAuthMiddleware.class),
                "应同时注册类映射");
    }

    @Test
    void testScanMultipleAliasMiddlewares() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("authMiddleware", TestAuthMiddleware.class);
        context.registerSingleton("logMiddleware", TestLogMiddleware.class);
        context.refresh();

        configuration.scanMiddlewareAliases(context);

        assertTrue(MiddlewareAliasRegistry.getGlobal().isRegistered("auth"));
        assertTrue(MiddlewareAliasRegistry.getGlobal().isRegistered("log"));
        assertEquals(2, MiddlewareAliasRegistry.getGlobal().getRegisteredAliases().size());
        assertEquals(2, MiddlewareAliasRegistry.getGlobal().getRegisteredClasses().size());
    }

    @Test
    void testScanNoAliasMiddleware() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("noAliasMiddleware", TestNoAliasMiddleware.class);
        context.refresh();

        configuration.scanMiddlewareAliases(context);

        // 无别名的中间件不应注册别名
        assertEquals(0, MiddlewareAliasRegistry.getGlobal().getRegisteredAliases().size(),
                "无别名中间件不应注册别名");
        // 但应注册类映射
        assertTrue(MiddlewareAliasRegistry.getGlobal().isClassRegistered(TestNoAliasMiddleware.class),
                "无别名中间件应注册类映射");
    }

    @Test
    void testScanMixedAliasAndNoAliasMiddlewares() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("authMiddleware", TestAuthMiddleware.class);
        context.registerSingleton("noAliasMiddleware", TestNoAliasMiddleware.class);
        context.refresh();

        configuration.scanMiddlewareAliases(context);

        // 只有 auth 注册了别名
        assertEquals(1, MiddlewareAliasRegistry.getGlobal().getRegisteredAliases().size(),
                "只有 1 个别名");
        // 两个类都注册了
        assertEquals(2, MiddlewareAliasRegistry.getGlobal().getRegisteredClasses().size(),
                "应注册 2 个类映射");
    }

    @Test
    void testScannedAliasMiddlewareReceivesParams() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("authMiddleware", TestAuthMiddleware.class);
        context.refresh();

        configuration.scanMiddlewareAliases(context);

        Middleware resolved = MiddlewareAliasRegistry.getGlobal().resolve("auth:api");
        assertNotNull(resolved);

        Middleware.NextFunction finalHandler = req -> ResponseBuilder.ok();
        Response response = resolved.handle(null, finalHandler);
        assertEquals("api", response.getHeaders().get("X-Auth-Guard").get(0),
                "params[0] 应为 'api'");
    }

    @Test
    void testScannedNoAliasMiddlewareResolvedByClass() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("noAliasMiddleware", TestNoAliasMiddleware.class);
        context.refresh();

        configuration.scanMiddlewareAliases(context);

        // 通过类对象解析
        Middleware resolved = MiddlewareAliasRegistry.getGlobal().resolve(TestNoAliasMiddleware.class);
        assertNotNull(resolved);

        Middleware.NextFunction finalHandler = req -> ResponseBuilder.ok();
        Response response = resolved.handle(null, finalHandler);
        assertEquals("true", response.getHeaders().get("X-NoAlias-Called").get(0),
                "无别名中间件应能通过类对象解析并执行");
    }

    @Test
    void testScannedNoAliasMiddlewareResolvedByClassName() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("noAliasMiddleware", TestNoAliasMiddleware.class);
        context.refresh();

        configuration.scanMiddlewareAliases(context);

        // 通过类简名解析
        Middleware resolved = MiddlewareAliasRegistry.getGlobal().resolve("TestNoAliasMiddleware");
        assertNotNull(resolved, "应能通过类简名解析无别名中间件");
    }

    @Test
    void testScannedAliasMiddlewareAlsoResolvedByClass() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("authMiddleware", TestAuthMiddleware.class);
        context.refresh();

        configuration.scanMiddlewareAliases(context);

        // 有别名的中间件也能通过类对象解析
        Middleware resolved = MiddlewareAliasRegistry.getGlobal().resolve(TestAuthMiddleware.class, "admin");
        assertNotNull(resolved);

        Middleware.NextFunction finalHandler = req -> ResponseBuilder.ok();
        Response response = resolved.handle(null, finalHandler);
        assertEquals("admin", response.getHeaders().get("X-Auth-Guard").get(0),
                "有别名的中间件也应能通过类对象+参数解析");
    }
}
