package com.weacsoft.jaravel.vendor.springboot.rediscache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * RedisCacheProperties 配置属性测试（随装配类迁入 springboot 模块）。
 */
class RedisCachePropertiesTest {

    @Test
    void testDefaultConnection() {
        RedisCacheProperties props = new RedisCacheProperties();
        assertEquals("cache", props.getConnection(), "默认连接名应为 cache");
        assertNull(props.getAutoRegister(), "autoRegister 默认应为 null（由 store driver 自动判定）");
    }

    @Test
    void testSetters() {
        RedisCacheProperties props = new RedisCacheProperties();
        props.setConnection("session");
        props.setAutoRegister(true);
        assertEquals("session", props.getConnection());
        assertEquals(Boolean.TRUE, props.getAutoRegister());

        props.setAutoRegister(false);
        assertEquals(Boolean.FALSE, props.getAutoRegister(), "autoRegister 应支持显式 false 覆盖开关");
    }
}
