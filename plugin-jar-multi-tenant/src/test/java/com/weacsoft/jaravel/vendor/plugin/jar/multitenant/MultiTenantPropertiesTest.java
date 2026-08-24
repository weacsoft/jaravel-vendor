package com.weacsoft.jaravel.vendor.plugin.jar.multitenant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MultiTenantProperties 配置属性测试。
 */
class MultiTenantPropertiesTest {

    @Test
    void testDefaultEnabled() {
        MultiTenantProperties props = new MultiTenantProperties();
        assertTrue(props.isEnabled(), "默认应启用多租户插件模式");
    }

    @Test
    void testDefaultSeparator() {
        MultiTenantProperties props = new MultiTenantProperties();
        assertEquals("@", props.getSeparator(), "默认分隔符应为 @");
    }

    @Test
    void testSetEnabled() {
        MultiTenantProperties props = new MultiTenantProperties();
        props.setEnabled(false);
        assertFalse(props.isEnabled());
    }

    @Test
    void testSetSeparator() {
        MultiTenantProperties props = new MultiTenantProperties();
        props.setSeparator("::");
        assertEquals("::", props.getSeparator());
    }
}
