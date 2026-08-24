package com.weacsoft.jaravel.vendor.plugin.jar.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PluginInfo} 插件元信息单元测试。
 */
class PluginInfoTest {

    @Test
    void defaultStateIsUploaded() {
        PluginInfo info = new PluginInfo();
        assertEquals(PluginInfo.State.UPLOADED, info.getState());
        // 集合字段默认非空
        assertNotNull(info.getSharedClassDependencies());
        assertNotNull(info.getRegisteredBeanNames());
        assertNotNull(info.getComponentClasses());
        assertNotNull(info.getRouteMappings());
        assertNotNull(info.getAvailableRoutes());
        assertTrue(info.getSharedClassDependencies().isEmpty());
        assertFalse(info.isPersisted());
    }

    @Test
    void threeArgConstructorSetsFields() {
        PluginInfo info = new PluginInfo("demo", "1.0.0", "/tmp/demo.jar");
        assertEquals("demo", info.getPluginId());
        assertEquals("1.0.0", info.getVersion());
        assertEquals("/tmp/demo.jar", info.getJarPath());
        assertEquals(PluginInfo.State.UPLOADED, info.getState());
    }

    @Test
    void stateTransitionsAreMutable() {
        PluginInfo info = new PluginInfo("p", "1", "/x.jar");
        info.setState(PluginInfo.State.ENABLED);
        assertEquals(PluginInfo.State.ENABLED, info.getState());

        info.setState(PluginInfo.State.DISABLED);
        info.setErrorMessage("boom");
        assertEquals(PluginInfo.State.DISABLED, info.getState());
        assertEquals("boom", info.getErrorMessage());
    }

    @Test
    void setsAreReplaceable() {
        PluginInfo info = new PluginInfo("p", "1", "/x.jar");
        Set<String> beans = new HashSet<>();
        beans.add("a");
        info.setRegisteredBeanNames(beans);
        info.setPersisted(true);

        assertEquals(beans, info.getRegisteredBeanNames());
        assertSame(beans, info.getRegisteredBeanNames());
        assertTrue(info.isPersisted());
    }
}
