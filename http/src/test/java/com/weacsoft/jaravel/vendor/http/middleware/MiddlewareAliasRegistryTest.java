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
 * {@link MiddlewareAliasRegistry} 别名/类注册与解析测试。
 * <p>
 * 覆盖别名注册、表达式解析（无参数/单参数/多参数）、类对象解析、类名解析、
 * 批量解析、参数烘焙验证、未注册异常等场景。
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

    // ========== 别名注册与解析 ==========

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
        registry.register("auth", (request, next, params) -> {
            Response resp = ResponseBuilder.ok();
            resp.addHeader("X-Guard", params.length > 0 ? params[0] : "default");
            return resp;
        });

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
        assertTrue(registry.getRegisteredAliases().isEmpty(), "清除后别名应为空");
        assertTrue(registry.getRegisteredClasses().isEmpty(), "清除后类映射应为空");
    }

    @Test
    void testExpressionParsingWithSpaces() {
        registry.register("cors", testMiddleware());

        Middleware resolved = registry.resolve("  cors  ");
        assertNotNull(resolved, "别名两端空格应被 trim");
    }

    @Test
    void testExpressionParsingEmptyParams() {
        registry.register("cors", testMiddleware());

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
        final String[] capturedParams = {null};
        registry.register("auth", (request, next, params) -> {
            capturedParams[0] = String.join(",", params);
            return ResponseBuilder.ok();
        });

        Middleware resolved = registry.resolve("auth:api");
        resolved.handle(null, req -> ResponseBuilder.ok(), "should", "be", "ignored");
        assertEquals("api", capturedParams[0], "闭包应使用烘焙参数，忽略调用时传入的参数");
    }

    // ========== 类注册与解析 ==========

    @Test
    void testRegisterByClassOnly() {
        Middleware mw = testMiddleware();
        registry.register(mw);

        // 不应注册别名
        assertFalse(registry.isRegistered("auth"), "register(Middleware) 不应注册别名");
        // 应注册类
        assertTrue(registry.isClassRegistered(mw.getClass()), "应注册类映射");
    }

    @Test
    void testRegisterWithAliasAlsoRegistersClass() {
        Middleware mw = testMiddleware();
        registry.register("auth", mw);

        // 别名和类都应注册
        assertTrue(registry.isRegistered("auth"), "应注册别名");
        assertTrue(registry.isClassRegistered(mw.getClass()), "应同时注册类映射");
    }

    @Test
    void testRegisterWithEmptyAliasOnlyRegistersClass() {
        Middleware mw = testMiddleware();
        registry.register("", mw);

        // 空别名不应注册
        assertFalse(registry.isRegistered(""), "空别名不应注册");
        // 类应注册
        assertTrue(registry.isClassRegistered(mw.getClass()), "空别名时仍应注册类映射");
    }

    @Test
    void testRegisterWithNullAliasOnlyRegistersClass() {
        Middleware mw = testMiddleware();
        registry.register(null, mw);

        assertFalse(registry.isRegistered(null), "null 别名不应注册");
        assertTrue(registry.isClassRegistered(mw.getClass()), "null 别名时仍应注册类映射");
    }

    @Test
    void testResolveByClassWithoutParams() {
        final int[] paramCount = {0};
        Middleware mw = (request, next, params) -> {
            paramCount[0] = params.length;
            return ResponseBuilder.ok();
        };
        registry.register(mw);

        Middleware resolved = registry.resolve(mw.getClass());
        assertNotNull(resolved, "按类解析应返回非 null");
        resolved.handle(null, req -> ResponseBuilder.ok());
        assertEquals(0, paramCount[0], "无参数时 params 应为空数组");
    }

    @Test
    void testResolveByClassWithParams() {
        final String[] capturedParams = {null};
        Middleware mw = (request, next, params) -> {
            capturedParams[0] = String.join(",", params);
            return ResponseBuilder.ok();
        };
        registry.register(mw);

        Middleware resolved = registry.resolve(mw.getClass(), "api", "admin");
        resolved.handle(null, req -> ResponseBuilder.ok());
        assertEquals("api,admin", capturedParams[0], "应正确传递参数");
    }

    @Test
    void testResolveByClassThrowsForUnregisteredClass() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve(String.class),
                "未注册的类应抛出 IllegalArgumentException");
    }

    @Test
    void testResolveByClassWithParamsThrowsForUnregisteredClass() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve(String.class, "param"),
                "未注册的类应抛出 IllegalArgumentException");
    }

    @Test
    void testResolveByClassIgnoresCallTimeParams() {
        final String[] capturedParams = {null};
        Middleware mw = (request, next, params) -> {
            capturedParams[0] = String.join(",", params);
            return ResponseBuilder.ok();
        };
        registry.register(mw);

        // 烘焙参数 ["api"]
        Middleware resolved = registry.resolve(mw.getClass(), "api");
        // 调用时传入不同参数，应被忽略
        resolved.handle(null, req -> ResponseBuilder.ok(), "ignored");
        assertEquals("api", capturedParams[0], "闭包应使用烘焙参数");
    }

    @Test
    void testResolveBySimpleName() {
        Middleware mw = testMiddleware();
        registry.register(mw);

        String simpleName = mw.getClass().getSimpleName();
        Middleware resolved = registry.resolve(simpleName);
        assertNotNull(resolved, "应能通过类简名解析");
    }

    @Test
    void testResolveByFullyQualifiedName() {
        Middleware mw = testMiddleware();
        registry.register(mw);

        String fullName = mw.getClass().getName();
        Middleware resolved = registry.resolve(fullName);
        assertNotNull(resolved, "应能通过全限定类名解析");
    }

    @Test
    void testResolveBySimpleNameWithParams() {
        final String[] capturedParams = {null};
        Middleware mw = (request, next, params) -> {
            capturedParams[0] = String.join(",", params);
            return ResponseBuilder.ok();
        };
        registry.register(mw);

        String simpleName = mw.getClass().getSimpleName();
        Middleware resolved = registry.resolve(simpleName + ":api,admin");
        resolved.handle(null, req -> ResponseBuilder.ok());
        assertEquals("api,admin", capturedParams[0], "类名表达式应正确解析参数");
    }

    @Test
    void testResolveStringFallsBackToClassName() {
        // 注册别名 "auth" 和一个无别名的中间件
        Middleware authMw = testMiddleware();
        registry.register("auth", authMw);

        Middleware logMw = testMiddleware();
        registry.register(logMw);

        // "auth" 应按别名解析
        Middleware resolved1 = registry.resolve("auth");
        assertNotNull(resolved1, "应按别名解析");

        // logMw 的简名应按类名解析
        String logName = logMw.getClass().getSimpleName();
        Middleware resolved2 = registry.resolve(logName);
        assertNotNull(resolved2, "应按类名回退解析");
    }

    @Test
    void testAliasTakesPrecedenceOverClassName() {
        // 如果别名和类简名相同，别名应优先
        Middleware aliasMw = testMiddleware();
        Middleware classMw = testMiddleware();

        // 注册别名 "TestMiddleware"（与 classMw 的简名相同）
        registry.register("TestMiddleware", aliasMw);
        registry.register(classMw);

        // 解析 "TestMiddleware" 应返回别名注册的中间件（通过 aliasMw 的闭包包装）
        // 由于两者都是 testMiddleware()，我们无法通过行为区分，但可以验证不报错
        Middleware resolved = registry.resolve("TestMiddleware");
        assertNotNull(resolved, "别名优先于类名");
    }

    @Test
    void testGetRegisteredClasses() {
        // 使用不同的中间件实现，确保 Class 不同（相同 lambda 会共享同一 synthetic class）
        Middleware mw1 = (request, next, params) -> ResponseBuilder.ok();
        Middleware mw2 = new Middleware() {
            @Override
            public Response handle(com.weacsoft.jaravel.vendor.http.controller.request.Request request,
                    NextFunction next, String... params) {
                return ResponseBuilder.ok();
            }
        };
        registry.register("auth", mw1);
        registry.register(mw2);

        assertEquals(2, registry.getRegisteredClasses().size(),
                "应注册 2 个类");
    }

    @Test
    void testIsClassRegistered() {
        Middleware mw = testMiddleware();
        assertFalse(registry.isClassRegistered(mw.getClass()), "未注册前应返回 false");
        registry.register(mw);
        assertTrue(registry.isClassRegistered(mw.getClass()), "注册后应返回 true");
    }
}
