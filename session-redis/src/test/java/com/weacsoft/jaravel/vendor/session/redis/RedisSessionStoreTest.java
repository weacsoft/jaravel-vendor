package com.weacsoft.jaravel.vendor.session.redis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RedisSessionStore 测试。
 * <p>
 * 仅测试构造逻辑，不测试实际 Redis 连接。
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
    void testConstructionDoesNotThrow() {
        assertDoesNotThrow(() -> createStore("prefix", 60, "my_cookie"));
    }
}
