package com.weacsoft.jaravel.vendor.session.redis;

import com.weacsoft.jaravel.vendor.redis.RedisManager;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RedisSessionStore 配置和 key 生成逻辑测试。
 * 不测试实际 Redis 连接，仅测试 Session ID 生成、生命周期转换、空值处理等纯逻辑。
 */
class RedisSessionStoreTest {

    /**
     * 构造 RedisSessionStore，RedisManager 传 null。
     * 仅测试不触发 Redis 命令的纯逻辑路径。
     */
    private RedisSessionStore createStore(String prefix, long lifetimeMinutes, String cookieName) {
        return new RedisSessionStore(null, "session", prefix, lifetimeMinutes, cookieName);
    }

    @Test
    void testGenerateSessionIdFormat() {
        RedisSessionStore store = createStore("laravel_session", 30, "manage_session");
        String sessionId = store.generateSessionId();
        assertNotNull(sessionId);
        assertEquals(32, sessionId.length(), "Session ID 应为 32 位无横线 UUID");
        assertFalse(sessionId.contains("-"), "Session ID 不应包含横线");
        // 验证两次生成结果不同
        assertNotEquals(sessionId, store.generateSessionId());
    }

    @Test
    void testGetLifetimeSecondsConversion() {
        RedisSessionStore store = createStore("laravel_session", 30, "manage_session");
        assertEquals(1800, store.getLifetimeSeconds(), "30 分钟应转换为 1800 秒");
    }

    @Test
    void testCustomLifetimeConversion() {
        RedisSessionStore store = createStore("prefix", 60, "cookie");
        assertEquals(3600, store.getLifetimeSeconds(), "60 分钟应转换为 3600 秒");
    }

    @Test
    void testGetCookieName() {
        RedisSessionStore store = createStore("prefix", 30, "my_session_cookie");
        assertEquals("my_session_cookie", store.getCookieName());
    }

    @Test
    void testGetAllWithNullSessionIdReturnsEmpty() {
        RedisSessionStore store = createStore("prefix", 30, "cookie");
        Map<String, Object> result = store.getAll(null);
        assertNotNull(result);
        assertTrue(result.isEmpty(), "null sessionId 应返回空 Map");
    }

    @Test
    void testGetAllWithEmptySessionIdReturnsEmpty() {
        RedisSessionStore store = createStore("prefix", 30, "cookie");
        Map<String, Object> result = store.getAll("");
        assertNotNull(result);
        assertTrue(result.isEmpty(), "空 sessionId 应返回空 Map");
    }

    @Test
    void testGetWithNullSessionIdReturnsNull() {
        RedisSessionStore store = createStore("prefix", 30, "cookie");
        assertNull(store.get(null, "key"), "null sessionId 应返回 null");
    }

    @Test
    void testGetWithEmptySessionIdReturnsNull() {
        RedisSessionStore store = createStore("prefix", 30, "cookie");
        assertNull(store.get("", "key"), "空 sessionId 应返回 null");
    }

    @Test
    void testExistsWithNullSessionIdReturnsFalse() {
        RedisSessionStore store = createStore("prefix", 30, "cookie");
        assertFalse(store.exists(null), "null sessionId 应返回 false");
    }

    @Test
    void testPutWithNullSessionIdIsNoOp() {
        RedisSessionStore store = createStore("prefix", 30, "cookie");
        // 不应抛出异常
        assertDoesNotThrow(() -> store.put(null, "key", "value"));
    }
}
