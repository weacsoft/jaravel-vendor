package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.http.middleware.Middleware;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GlobalMiddlewareRegistry 全局中间件注册器测试。
 * 测试中间件添加、批量添加、不可变列表返回等纯逻辑。
 * ApplicationContext 传 null（add/addAll/getMiddlewares 不使用它）。
 */
class GlobalMiddlewareRegistryTest {

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
}
