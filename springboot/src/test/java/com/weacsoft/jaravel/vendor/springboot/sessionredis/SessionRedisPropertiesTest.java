package com.weacsoft.jaravel.vendor.springboot.sessionredis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SessionRedisProperties 配置属性测试（随装配类迁入 springboot 模块）。
 */
class SessionRedisPropertiesTest {

    @Test
    void testDefaultValues() {
        SessionRedisProperties props = new SessionRedisProperties();
        assertEquals("session", props.getConnection(), "默认连接名应为 session");
        assertEquals("laravel_session", props.getPrefix(), "默认前缀应为 laravel_session");
        assertEquals(30, props.getLifetime(), "默认生命周期应为 30 分钟");
        assertEquals("manage_session", props.getCookie(), "默认 Cookie 名应为 manage_session");
        assertNull(props.getAutoRegister(), "默认不设开关，由 jaravel.session.driver 自动判定");
    }

    @Test
    void testSetConnection() {
        SessionRedisProperties props = new SessionRedisProperties();
        props.setConnection("custom-session");
        assertEquals("custom-session", props.getConnection());
    }

    @Test
    void testSetPrefix() {
        SessionRedisProperties props = new SessionRedisProperties();
        props.setPrefix("my_session");
        assertEquals("my_session", props.getPrefix());
    }

    @Test
    void testSetLifetime() {
        SessionRedisProperties props = new SessionRedisProperties();
        props.setLifetime(120);
        assertEquals(120, props.getLifetime());
    }

    @Test
    void testSetCookie() {
        SessionRedisProperties props = new SessionRedisProperties();
        props.setCookie("custom_cookie");
        assertEquals("custom_cookie", props.getCookie());
    }

    @Test
    void testSetAutoRegister() {
        SessionRedisProperties props = new SessionRedisProperties();
        props.setAutoRegister(false);
        assertFalse(props.getAutoRegister());
        props.setAutoRegister(true);
        assertTrue(props.getAutoRegister());
    }
}
