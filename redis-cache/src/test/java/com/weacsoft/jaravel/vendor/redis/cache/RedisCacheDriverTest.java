package com.weacsoft.jaravel.vendor.redis.cache;

import com.weacsoft.jaravel.vendor.redis.RedisManager;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RedisCacheDriver 序列化与 key 构建逻辑测试。
 * 使用 Mockito mock RedisManager，不测试实际 Redis 连接。
 */
class RedisCacheDriverTest {

    @SuppressWarnings("unchecked")
    private RedisCommands<String, String> mockCmd;
    private RedisManager mockManager;
    private RedisCacheDriver driver;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mockManager = mock(RedisManager.class);
        mockCmd = (RedisCommands<String, String>) mock(RedisCommands.class);
        when(mockManager.sync(any())).thenReturn(mockCmd);
        driver = new RedisCacheDriver(mockManager, "cache");
    }

    @Test
    void testPutWithTtlCallsSetex() {
        driver.put("user:1", "hello", 60);
        // 验证 setex 被调用，key 和 ttl 正确，value 为 JSON 序列化结果
        verify(mockCmd).setex("user:1", 60L, "\"hello\"");
    }

    @Test
    void testPutWithoutTtlCallsSet() {
        driver.put("config", "value", 0);
        // TTL <= 0 时应调用 set 而非 setex
        verify(mockCmd).set("config", "\"value\"");
    }

    @Test
    void testPutWithNegativeTtlCallsSet() {
        driver.put("key", "val", -1);
        verify(mockCmd).set("key", "\"val\"");
    }

    @Test
    void testPutMapSerialization() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "Alice");
        data.put("age", 30);
        driver.put("user:1", data, 120);
        // 验证 setex 被调用，value 包含 JSON 序列化的 Map
        verify(mockCmd).setex(eq("user:1"), eq(120L), contains("Alice"));
    }

    @Test
    void testGetDeserializesJson() {
        when(mockCmd.get("key")).thenReturn("\"hello world\"");
        Object result = driver.get("key");
        assertEquals("hello world", result, "应反序列化 JSON 字符串为 Java String");
    }

    @Test
    void testGetReturnsNullWhenKeyMissing() {
        when(mockCmd.get("missing")).thenReturn(null);
        assertNull(driver.get("missing"), "key 不存在时应返回 null");
    }

    @Test
    void testGetDeserializesMap() {
        when(mockCmd.get("key")).thenReturn("{\"name\":\"Bob\",\"age\":25}");
        Object result = driver.get("key");
        assertInstanceOf(Map.class, result, "应反序列化 JSON 对象为 Map");
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertEquals("Bob", map.get("name"));
        assertEquals(25, map.get("age"));
    }

    @Test
    void testExistsReturnsTrue() {
        when(mockCmd.exists("key")).thenReturn(1L);
        assertTrue(driver.exists("key"));
    }

    @Test
    void testExistsReturnsFalse() {
        when(mockCmd.exists("key")).thenReturn(0L);
        assertFalse(driver.exists("key"));
    }

    @Test
    void testRemoveReturnsTrue() {
        when(mockCmd.del("key")).thenReturn(1L);
        assertTrue(driver.remove("key"));
    }

    @Test
    void testRemoveReturnsFalse() {
        when(mockCmd.del("key")).thenReturn(0L);
        assertFalse(driver.remove("key"));
    }
}
