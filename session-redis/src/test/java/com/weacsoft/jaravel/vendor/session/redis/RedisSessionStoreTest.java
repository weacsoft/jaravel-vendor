package com.weacsoft.jaravel.vendor.session.redis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RedisSessionStore 测试。
 * <p>
 * 仅测试 {@link SessionStore} 接口实现逻辑（support 方法），不测试实际 Redis 连接。
 * 实际的 get/put/remove/destroy 操作需要 AuthContext 上下文，在集成测试中验证。
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
    void testSupportRedis() {
        RedisSessionStore store = createStore("laravel_session", 30, "manage_session");
        assertTrue(store.support("redis"), "应支持 redis");
        assertTrue(store.support("REDIS"), "应不区分大小写");
        assertTrue(store.support("Redis"));
    }

    @Test
    void testDoesNotSupportOtherStores() {
        RedisSessionStore store = createStore("prefix", 30, "cookie");
        assertFalse(store.support("cookie"), "不应支持 cookie");
        assertFalse(store.support("file"), "不应支持 file");
        assertFalse(store.support("database"), "不应支持 database");
    }

    @Test
    void testConstructionDoesNotThrow() {
        assertDoesNotThrow(() -> createStore("prefix", 60, "my_cookie"));
    }
}
