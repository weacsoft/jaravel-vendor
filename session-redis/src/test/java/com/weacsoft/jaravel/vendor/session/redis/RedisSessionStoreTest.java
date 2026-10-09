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

    // ==================== Session ID 白名单（Cookie 值直接拼进 Redis 键）====================

    @Test
    void generatedStyleSessionIdIsAccepted() {
        // UUID(32 位十六进制) 是 generateSessionId() 的实际形状
        assertTrue(RedisSessionStore.isValidSessionId("0123456789abcdef0123456789abcdef"));
        assertTrue(RedisSessionStore.isValidSessionId("abc-DEF_1234567890123456"));
    }

    @Test
    void maliciousOrMalformedSessionIdIsRejected() {
        String[] rejected = {
                null, "", "   ", "short", "0123456789abcde",          // 长度不足
                "abc/../../etc/passwd1234",                            // 路径穿越字符
                "abc def ghijklmnopqrst",                              // 空白
                "0123456789abcdef\n0123456789abcdef",                  // 换行
                "会话标识会话标识会话标识会话标识会话标识",                  // 非 ASCII
                "0123456789abcdef0123456789abcdef"
                        + "0123456789abcdef0123456789abcdef"
                        + "0123456789abcdef0123456789abcdef"
                        + "0123456789abcdef0123456789abcdef"
                        + "0123456789abcdef0123456789abcdef"           // 超长（160 > 128）
        };
        for (String candidate : rejected) {
            assertFalse(RedisSessionStore.isValidSessionId(candidate),
                    "非法 Session ID 必须被拒绝: " + candidate);
        }
    }
}
