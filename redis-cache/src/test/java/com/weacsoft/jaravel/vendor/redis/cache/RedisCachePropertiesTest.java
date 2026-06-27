package com.weacsoft.jaravel.vendor.redis.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RedisCacheProperties 配置属性测试。
 */
class RedisCachePropertiesTest {

    @Test
    void testDefaultValues() {
        RedisCacheProperties props = new RedisCacheProperties();
        assertEquals("cache", props.getConnection(), "默认连接名应为 cache");
        assertTrue(props.isAutoRegister(), "默认应自动注册到 CacheManager");
    }

    @Test
    void testSetConnection() {
        RedisCacheProperties props = new RedisCacheProperties();
        props.setConnection("model-cache");
        assertEquals("model-cache", props.getConnection());
    }

    @Test
    void testSetAutoRegister() {
        RedisCacheProperties props = new RedisCacheProperties();
        props.setAutoRegister(false);
        assertFalse(props.isAutoRegister());
    }
}
