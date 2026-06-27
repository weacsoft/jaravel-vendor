package com.weacsoft.jaravel.vendor.plugin.jar.autoconfigure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PluginJarProperties} 配置默认值单元测试。
 */
class PluginJarPropertiesTest {

    @Test
    void defaultsMatchDocumentation() {
        PluginJarProperties props = new PluginJarProperties();
        assertTrue(props.isEnabled());
        assertEquals("plugins", props.getPluginsDir());
        assertTrue(props.isAutoRestore());
        assertTrue(props.isAutoRegister());
    }

    @Test
    void allFieldsAreMutable() {
        PluginJarProperties props = new PluginJarProperties();
        props.setEnabled(false);
        props.setPluginsDir("/data/plugins");
        props.setAutoRestore(false);
        props.setAutoRegister(false);

        org.junit.jupiter.api.Assertions.assertFalse(props.isEnabled());
        assertEquals("/data/plugins", props.getPluginsDir());
        org.junit.jupiter.api.Assertions.assertFalse(props.isAutoRestore());
        org.junit.jupiter.api.Assertions.assertFalse(props.isAutoRegister());
    }
}
