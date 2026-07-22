package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.http.middleware.Middleware;
import com.weacsoft.jaravel.vendor.http.middleware.MiddlewareAliasRegistry;
import com.weacsoft.jaravel.vendor.http.middleware.MiddlewareResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GlobalMiddlewareRegistry 全局中间件注册器测试。
 * <p>
 * 测试中间件添加、批量添加、不可变列表返回等纯逻辑，
 * 以及 {@code @Middleware} 注解 Bean 的别名自动注册功能。
 */
class GlobalMiddlewareRegistryTest {

    @BeforeEach
    void setUp() {
        MiddlewareAliasRegistry.getGlobal().clear();
    }

    @AfterEach
    void tearDown() {
        MiddlewareAliasRegistry.getGlobal().clear();
    }

    /** 创建一个空的测试中间件 */
    private Middleware testMiddleware() {
        return (request, next) -> null;
    }

    @Test
    void testGetMiddlewaresReturnsEmptyInitially() {
        GlobalMiddlewareRegistry registry = new GlobalMiddlewareRegistry(null);
        assertNotNull(registry.getMiddlewares());
        assertTrue(registry.getMiddlewares().isEmpty(), "初始状态中间件列表应为空");
    }

    @Test
    void testAddMiddleware() {
        GlobalMiddlewareRegistry registry = new GlobalMiddlewareRegistry(null);
        Middleware m = testMiddleware();
        registry.add(m);
        assertEquals(1, registry.getMiddlewares().size(), "添加后应有 1 个中间件");
        assertSame(m, registry.getMiddlewares().get(0), "应返回添加的中间件实例");
    }

    @Test
    void testAddMultipleMiddlewares() {
        GlobalMiddlewareRegistry registry = new GlobalMiddlewareRegistry(null);
        registry.add(testMiddleware());
        registry.add(testMiddleware());
        registry.add(testMiddleware());
        assertEquals(3, registry.getMiddlewares().size(), "应添加 3 个中间件");
    }

    @Test
    void testAddAllMiddlewares() {
        GlobalMiddlewareRegistry registry = new GlobalMiddlewareRegistry(null);
        List<Middleware> list = Arrays.asList(testMiddleware(), testMiddleware());
        registry.addAll(list);
        assertEquals(2, registry.getMiddlewares().size(), "addAll 后应有 2 个中间件");
    }

    @Test
    void testGetMiddlewaresReturnsUnmodifiableList() {
        GlobalMiddlewareRegistry registry = new GlobalMiddlewareRegistry(null);
        registry.add(testMiddleware());
        assertThrows(UnsupportedOperationException.class,
                () -> registry.getMiddlewares().add(testMiddleware()),
                "返回的列表应不可修改");
    }

    // ========== 别名中间件自动注册测试 ==========

    @Test
    void testRegisterAliasMiddlewaresWithNullContext() {
        // applicationContext 为 null 时不应抛出异常
        GlobalMiddlewareRegistry registry = new GlobalMiddlewareRegistry(null);
        assertDoesNotThrow(() -> registry.registerAliasMiddlewares(),
                "applicationContext 为 null 时应安全跳过");
    }

    @Test
    void testRegisterSimpleMiddlewareAlias() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("authMiddleware", TestAuthMiddleware.class);
        context.refresh();

        GlobalMiddlewareRegistry registry = new GlobalMiddlewareRegistry(context);
        registry.registerAliasMiddlewares();

        assertTrue(MiddlewareAliasRegistry.getGlobal().isRegistered("auth"),
                "应注册别名 'auth'");

        Middleware resolved = MiddlewareAliasRegistry.getGlobal().resolve("auth");
        assertNotNull(resolved, "别名 'auth' 应解析为非 null 中间件");
    }

    @Test
    void testRegisterResolverMiddlewareAlias() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("logMiddleware", TestLogMiddlewareResolver.class);
        context.refresh();

        GlobalMiddlewareRegistry registry = new GlobalMiddlewareRegistry(context);
        registry.registerAliasMiddlewares();

        assertTrue(MiddlewareAliasRegistry.getGlobal().isRegistered("log"),
                "应注册别名 'log'");

        // 参数化中间件：log:api → resolve("api")
        Middleware resolved = MiddlewareAliasRegistry.getGlobal().resolve("log:api");
        assertNotNull(resolved, "别名 'log:api' 应解析为非 null 中间件");
    }

    @Test
    void testRegisterMultipleAliasMiddlewares() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("authMiddleware", TestAuthMiddleware.class);
        context.registerSingleton("logMiddleware", TestLogMiddlewareResolver.class);
        context.refresh();

        GlobalMiddlewareRegistry registry = new GlobalMiddlewareRegistry(context);
        registry.registerAliasMiddlewares();

        assertTrue(MiddlewareAliasRegistry.getGlobal().isRegistered("auth"));
        assertTrue(MiddlewareAliasRegistry.getGlobal().isRegistered("log"));
        assertEquals(2, MiddlewareAliasRegistry.getGlobal().getRegisteredAliases().size(),
                "应注册 2 个别名");
    }

    @Test
    void testResolverMiddlewareWithMultipleParameters() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("logMiddleware", TestLogMiddlewareResolver.class);
        context.refresh();

        GlobalMiddlewareRegistry registry = new GlobalMiddlewareRegistry(context);
        registry.registerAliasMiddlewares();

        // log:api,admin → resolve("api", "admin")
        Middleware resolved = MiddlewareAliasRegistry.getGlobal().resolve("log:api,admin");
        assertNotNull(resolved, "多参数别名应正确解析");
    }
}
