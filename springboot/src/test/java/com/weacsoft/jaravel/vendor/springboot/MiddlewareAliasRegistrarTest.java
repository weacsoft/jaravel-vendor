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
 * {@link MiddlewareAliasRegistrar} 别名自动注册测试。
 */
class MiddlewareAliasRegistrarTest {

    @BeforeEach
    void setUp() {
        MiddlewareAliasRegistry.getGlobal().clear();
    }

    @AfterEach
    void tearDown() {
        MiddlewareAliasRegistry.getGlobal().clear();
    }

    @Test
    void testRegisterAliasesWithNullContext() {
        MiddlewareAliasRegistrar registrar = new MiddlewareAliasRegistrar(null);
        assertDoesNotThrow(() -> registrar.registerAliases(),
                "applicationContext 为 null 时应安全跳过");
    }

    @Test
    void testRegisterSingleAlias() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("authMiddleware", TestAuthMiddleware.class);
        context.refresh();

        MiddlewareAliasRegistrar registrar = new MiddlewareAliasRegistrar(context);
        registrar.registerAliases();

        assertTrue(MiddlewareAliasRegistry.getGlobal().isRegistered("auth"),
                "应注册别名 'auth'");
    }

    @Test
    void testRegisterMultipleAliases() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("authMiddleware", TestAuthMiddleware.class);
        context.registerSingleton("logMiddleware", TestLogMiddleware.class);
        context.refresh();

        MiddlewareAliasRegistrar registrar = new MiddlewareAliasRegistrar(context);
        registrar.registerAliases();

        assertTrue(MiddlewareAliasRegistry.getGlobal().isRegistered("auth"));
        assertTrue(MiddlewareAliasRegistry.getGlobal().isRegistered("log"));
        assertEquals(2, MiddlewareAliasRegistry.getGlobal().getRegisteredAliases().size());
    }

    @Test
    void testAliasMiddlewareReceivesParams() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("authMiddleware", TestAuthMiddleware.class);
        context.refresh();

        MiddlewareAliasRegistrar registrar = new MiddlewareAliasRegistrar(context);
        registrar.registerAliases();

        // 解析别名表达式 auth:api
        Middleware resolved = MiddlewareAliasRegistry.getGlobal().resolve("auth:api");
        assertNotNull(resolved);

        // 执行中间件，验证 params 被正确传递
        Middleware.NextFunction finalHandler = req -> ResponseBuilder.ok();
        Response response = resolved.handle(null, finalHandler);
        assertEquals("api", response.getHeaders().get("X-Auth-Guard").get(0),
                "params[0] 应为 'api'");
    }

    @Test
    void testAliasMiddlewareWithMultipleParams() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("logMiddleware", TestLogMiddleware.class);
        context.refresh();

        MiddlewareAliasRegistrar registrar = new MiddlewareAliasRegistrar(context);
        registrar.registerAliases();

        // 解析别名表达式 log:api,admin
        Middleware resolved = MiddlewareAliasRegistry.getGlobal().resolve("log:api,admin");
        assertNotNull(resolved);

        // 执行中间件，验证 params[0] 被正确传递
        Middleware.NextFunction finalHandler = req -> ResponseBuilder.ok();
        Response response = resolved.handle(null, finalHandler);
        assertEquals("api", response.getHeaders().get("X-Log-Channel").get(0),
                "params[0] 应为 'api'");
    }

    @Test
    void testAliasMiddlewareWithoutParams() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("authMiddleware", TestAuthMiddleware.class);
        context.refresh();

        MiddlewareAliasRegistrar registrar = new MiddlewareAliasRegistrar(context);
        registrar.registerAliases();

        // 解析无参数别名 auth
        Middleware resolved = MiddlewareAliasRegistry.getGlobal().resolve("auth");
        assertNotNull(resolved);

        Middleware.NextFunction finalHandler = req -> ResponseBuilder.ok();
        Response response = resolved.handle(null, finalHandler);
        assertEquals("default", response.getHeaders().get("X-Auth-Guard").get(0),
                "无参数时应使用默认值");
    }
}
