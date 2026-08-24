package com.weacsoft.jaravel.vendor.plugin.java.model;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JavaFilePluginInfo} Java 文件插件信息模型单元测试。
 */
class JavaFilePluginInfoTest {

    @Test
    void defaultsAreLoadedStateAndNonEmptySets() {
        JavaFilePluginInfo info = new JavaFilePluginInfo();
        assertEquals(JavaFilePluginInfo.State.LOADED, info.getState());
        assertNotNull(info.getSourceFiles());
        assertNotNull(info.getComponentClasses());
        assertNotNull(info.getRouteMappings());
        assertNotNull(info.getAvailableRoutes());
        assertNotNull(info.getRegisteredBeanNames());
        assertTrue(info.getSourceFiles().isEmpty());
    }

    @Test
    void twoArgConstructorSetsIdentityFields() {
        JavaFilePluginInfo info = new JavaFilePluginInfo("hello-plugin", "/src/plugins/hello");
        assertEquals("hello-plugin", info.getPluginId());
        assertEquals("/src/plugins/hello", info.getSourceDir());
        assertEquals(JavaFilePluginInfo.State.LOADED, info.getState());
    }

    @Test
    void settersAreNullSafe() {
        JavaFilePluginInfo info = new JavaFilePluginInfo("p", "/d");
        info.setSourceFiles(null);
        info.setComponentClasses(null);
        info.setRouteMappings(null);
        info.setAvailableRoutes(null);
        info.setRegisteredBeanNames(null);

        // null 入参应回退为空集合，而非保持 null
        assertNotNull(info.getSourceFiles());
        assertTrue(info.getSourceFiles().isEmpty());
        assertNotNull(info.getRegisteredBeanNames());
    }

    @Test
    void stateTransitionsAndErrorMessage() {
        JavaFilePluginInfo info = new JavaFilePluginInfo("p", "/d");
        info.setState(JavaFilePluginInfo.State.ENABLED);
        assertEquals(JavaFilePluginInfo.State.ENABLED, info.getState());

        info.setState(JavaFilePluginInfo.State.DISABLED);
        info.setErrorMessage("compile error");
        info.setLastModified(12345L);
        assertEquals(JavaFilePluginInfo.State.DISABLED, info.getState());
        assertEquals("compile error", info.getErrorMessage());
        assertEquals(12345L, info.getLastModified());
    }

    @Test
    void toStringSummarizesCountsAndState() {
        JavaFilePluginInfo info = new JavaFilePluginInfo("p", "/d");
        info.setSourceFiles(Set.of("A.java", "B.java"));
        info.setComponentClasses(Set.of("com.x.A"));
        info.setState(JavaFilePluginInfo.State.ENABLED);

        String s = info.toString();
        assertTrue(s.contains("pluginId='p'"));
        assertTrue(s.contains("state=ENABLED"));
        assertTrue(s.contains("sourceFiles=2"));
        assertTrue(s.contains("componentClasses=1"));
    }

    @Test
    void errorMessageDefaultsNull() {
        JavaFilePluginInfo info = new JavaFilePluginInfo();
        // 未设置错误信息时为 null（非抛异常）
        assertFalse(info.getErrorMessage() != null);
    }
}
