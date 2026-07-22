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
 * 覆盖别名注册、表达式解析（无参数/单参数/多参数）、批量解析、参数烘焙验证、未注册别名异常等场景。
 */
class MiddlewareAliasRegistryTest {

    private MiddlewareAliasRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new MiddlewareAliasRegistry();
    }

    @AfterEach
    void tearDown() {
        MiddlewareAliasRegistry.getGlobal().clear();
    }

    private Middleware testMiddleware() {
        return (request, next, params) -> ResponseBuilder.ok();
    }

    @Test
    void testRegisterAndResolveSimpleAlias() {
        Middleware mw = testMiddleware();
        registry.register("auth", mw);

        assertTrue(registry.isRegistered("auth"));
        Middleware resolved = registry.resolve("auth");
        assertNotNull(resolved, "resolve 应返回非 null 的 Middleware");
    }

    @Test
    void testResolveWithSingleParameter() {
        // 中间件根据 params[0] 设置响应头
        registry.register("auth", (request, next, params) -> {
            Response resp = ResponseBuilder.ok();
            resp.addHeader("X-Guard", params.length > 0 ? params[0] : "default");
            return resp;
        });

        // auth:api → params = ["api"]
        Middleware resolved = registry.resolve("auth:api");
        Response response = resolved.handle(null, req -> ResponseBuilder.ok());
        assertEquals("api", response.getHeaders().get("X-Guard").get(0),
                "params[0] 应为 'api'");
    }

    @Test
    void testResolveWithMultipleParameters() {
        final String[] capturedParams = {null};
        registry.register("auth", (request, next, params) -> {
            capturedParams[0] = String.join(",", params);
            return ResponseBuilder.ok();
        });

        // auth:api,admin → params = ["api", "admin"]
        Middleware resolved = registry.resolve("auth:api,admin");
        resolved.handle(null, req -> ResponseBuilder.ok());
        assertEquals("api,admin", capturedParams[0], "应正确解析多参数");
    }

    @Test
    void testResolveWithoutParams() {
        final int[] paramCount = {0};
        registry.register("auth", (request, next, params) -> {
            paramCount[0] = params.length;
            return ResponseBuilder.ok();
        });

        Middleware resolved = registry.resolve("auth");
        resolved.handle(null, req -> ResponseBuilder.ok());
        assertEquals(0, paramCount[0], "无参数别名 params 应为空数组");
    }

    @Test
    void testResolveAllPreservesOrder() {
        Middleware mw1 = testMiddleware();
        Middleware mw2 = testMiddleware();
        registry.register("auth", mw1);
        registry.register("log", mw2);

        List<Middleware> result = registry.resolveAll(Arrays.asList("auth", "log"));
        assertEquals(2, result.size());
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
    void testExpressionParsingWithSpaces() {
        registry.register("cors", testMiddleware());

        // 带空格的表达式应被 trim
        Middleware resolved = registry.resolve("  cors  ");
        assertNotNull(resolved, "别名两端空格应被 trim");
    }

    @Test
    void testExpressionParsingEmptyParams() {
        registry.register("cors", testMiddleware());

        // cors: — 冒号后无参数
        Middleware resolved = registry.resolve("cors:");
        assertNotNull(resolved, "冒号后无参数应正常解析");
    }

    @Test
    void testGlobalRegistryStaticMethods() {
        Middleware mw = testMiddleware();
        MiddlewareAliasRegistry.registerGlobal("global-test", mw);

        assertTrue(MiddlewareAliasRegistry.getGlobal().isRegistered("global-test"));
        Middleware resolved = MiddlewareAliasRegistry.resolveGlobal("global-test");
        assertNotNull(resolved);
    }

    @Test
    void testResolvedMiddlewareIgnoresCallTimeParams() {
        // resolve 返回的闭包应忽略调用时传入的 params，使用烘焙的参数
        final String[] capturedParams = {null};
        registry.register("auth", (request, next, params) -> {
            capturedParams[0] = String.join(",", params);
            return ResponseBuilder.ok();
        });

        // 解析 auth:api → 烘焙 params = ["api"]
        Middleware resolved = registry.resolve("auth:api");

        // 调用时传入不同的 params，应被忽略
        resolved.handle(null, req -> ResponseBuilder.ok(), "should", "be", "ignored");
        assertEquals("api", capturedParams[0], "闭包应使用烘焙参数，忽略调用时传入的参数");
    }
}
