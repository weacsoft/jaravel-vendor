package com.weacsoft.jaravel.vendor.http.middleware;

import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import com.weacsoft.jaravel.vendor.http.controller.response.ResponseBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MiddlewareAliasRegistry} 别名注册与解析测试。
 * <p>
 * 覆盖别名注册、表达式解析（无参数/单参数/多参数）、批量解析、未注册别名异常等场景。
 */
class MiddlewareAliasRegistryTest {

    private MiddlewareAliasRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new MiddlewareAliasRegistry();
    }

    @AfterEach
    void tearDown() {
        // 清理全局注册表，避免影响其他测试
        MiddlewareAliasRegistry.getGlobal().clear();
    }

    private Middleware testMiddleware() {
        return (request, next) -> ResponseBuilder.ok();
    }

    @Test
    void testRegisterAndResolveSimpleAlias() {
        Middleware mw = testMiddleware();
        registry.register("auth", mw);

        assertTrue(registry.isRegistered("auth"));
        Middleware resolved = registry.resolve("auth");
        assertSame(mw, resolved, "无参数别名应返回注册的中间件实例");
    }

    @Test
    void testResolveWithSingleParameter() {
        Middleware mw = testMiddleware();
        registry.register("auth", mw);

        // auth:api — 参数被忽略（简单中间件）
        Middleware resolved = registry.resolve("auth:api");
        assertSame(mw, resolved, "简单中间件应忽略参数，返回同一实例");
    }

    @Test
    void testResolveWithMultipleParameters() {
        Middleware mw = testMiddleware();
        registry.register("auth", mw);

        // auth:api,admin — 多参数被忽略（简单中间件）
        Middleware resolved = registry.resolve("auth:api,admin");
        assertSame(mw, resolved, "简单中间件应忽略多参数，返回同一实例");
    }

    @Test
    void testRegisterResolverWithParameters() {
        // 参数化中间件：根据参数创建不同实例
        registry.register("auth", params -> (request, next) -> {
            Response resp = ResponseBuilder.ok();
            resp.addHeader("X-Guard", params.length > 0 ? params[0] : "default");
            return resp;
        });

        Middleware resolved = registry.resolve("auth:api");
        assertNotNull(resolved);
        assertEquals("api", resolved.handle(null, req -> ResponseBuilder.ok()).getHeaders().get("X-Guard").get(0));

        Middleware resolved2 = registry.resolve("auth:admin");
        assertNotNull(resolved2);
        assertEquals("admin", resolved2.handle(null, req -> ResponseBuilder.ok()).getHeaders().get("X-Guard").get(0));
    }

    @Test
    void testRegisterResolverWithMultipleParameters() {
        final String[] capturedParams = {null};
        registry.register("auth", params -> {
            capturedParams[0] = String.join(",", params);
            return testMiddleware();
        });

        registry.resolve("auth:api,admin");
        assertEquals("api,admin", capturedParams[0], "应正确解析多参数");
    }

    @Test
    void testResolveAllPreservesOrder() {
        Middleware mw1 = testMiddleware();
        Middleware mw2 = testMiddleware();
        registry.register("auth", mw1);
        registry.register("log", mw2);

        List<Middleware> result = registry.resolveAll(Arrays.asList("auth", "log"));
        assertEquals(2, result.size());
        assertSame(mw1, result.get(0), "第一个应是 auth");
        assertSame(mw2, result.get(1), "第二个应是 log");
    }

    @Test
    void testResolveUnregisteredAliasThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve("nonexistent"),
                "未注册的别名应抛出 IllegalArgumentException");
    }

    @Test
    void testIsRegistered() {
        assertFalse(registry.isRegistered("auth"), "未注册前应返回 false");
        registry.register("auth", testMiddleware());
        assertTrue(registry.isRegistered("auth"), "注册后应返回 true");
    }

    @Test
    void testClear() {
        registry.register("auth", testMiddleware());
        registry.register("log", testMiddleware());
        assertEquals(2, registry.getRegisteredAliases().size());

        registry.clear();
        assertTrue(registry.getRegisteredAliases().isEmpty(), "清除后应为空");
    }

    @Test
    void testExpressionParsingNoColon() {
        Middleware mw = testMiddleware();
        registry.register("cors", mw);

        // 无冒号 → 无参数
        Middleware resolved = registry.resolve("cors");
        assertSame(mw, resolved);
    }

    @Test
    void testExpressionParsingWithSpaces() {
        Middleware mw = testMiddleware();
        registry.register("cors", mw);

        // 带空格的表达式应被 trim
        Middleware resolved = registry.resolve("  cors  ");
        assertSame(mw, resolved, "别名两端空格应被 trim");
    }

    @Test
    void testExpressionParsingEmptyParams() {
        Middleware mw = testMiddleware();
        registry.register("cors", mw);

        // cors: — 冒号后无参数
        Middleware resolved = registry.resolve("cors:");
        assertSame(mw, resolved, "冒号后无参数应正常解析");
    }

    @Test
    void testGlobalRegistryStaticMethods() {
        Middleware mw = testMiddleware();
        MiddlewareAliasRegistry.registerGlobal("global-test", mw);

        assertTrue(MiddlewareAliasRegistry.getGlobal().isRegistered("global-test"));
        Middleware resolved = MiddlewareAliasRegistry.resolveGlobal("global-test");
        assertSame(mw, resolved);
    }
}
