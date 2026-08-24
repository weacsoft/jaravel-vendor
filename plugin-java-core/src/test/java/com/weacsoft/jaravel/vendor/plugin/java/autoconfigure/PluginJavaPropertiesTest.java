package com.weacsoft.jaravel.vendor.plugin.java.autoconfigure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PluginJavaProperties} Java 文件插件配置默认值单元测试。
 */
class PluginJavaPropertiesTest {

    @Test
    void defaultsMatchDocumentation() {
        PluginJavaProperties props = new PluginJavaProperties();
        assertTrue(props.isEnabled());
        assertEquals("plugins-java", props.getSourceDir());
        assertTrue(props.isAutoScan());
        assertTrue(props.isAutoRegister());
    }

    @Test
    void allFieldsAreMutable() {
        PluginJavaProperties props = new PluginJavaProperties();
        props.setEnabled(false);
        props.setSourceDir("/opt/java-plugins");
        props.setAutoScan(false);
        props.setAutoRegister(false);

        assertFalse(props.isEnabled());
        assertEquals("/opt/java-plugins", props.getSourceDir());
        assertFalse(props.isAutoScan());
        assertFalse(props.isAutoRegister());
    }
}
