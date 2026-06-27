package com.weacsoft.jaravel.vendor.plugin.jar.manager;

import com.weacsoft.jaravel.vendor.plugin.jar.annotation.HttpMethod;
import com.weacsoft.jaravel.vendor.plugin.jar.integration.PluginIntegration;
import com.weacsoft.jaravel.vendor.plugin.jar.model.PluginInfo;
import com.weacsoft.jaravel.vendor.plugin.jar.model.RouteInfo;
import com.weacsoft.jaravel.vendor.plugin.jar.persistence.MetadataPersistence;
import com.weacsoft.jaravel.vendor.plugin.jar.registrar.PluginBeanRegistrar;
import com.weacsoft.jaravel.vendor.plugin.jar.registrar.PluginRouteRegistrar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link HotPluginManager} 插件注册 / 查询 / 生命周期管理单元测试。
 * <p>
 * 协作组件（Bean/Route 注册器、持久化、集成回调）以 Mockito 模拟，
 * 聚焦于 HotPluginManager 自身的状态机与注册表逻辑。
 */
@ExtendWith(MockitoExtension.class)
class HotPluginManagerTest {

    @Mock
    private PluginBeanRegistrar beanRegistrar;
    @Mock
    private PluginRouteRegistrar routeRegistrar;
    @Mock
    private MetadataPersistence persistence;
    @Mock
    private PluginIntegration integration;

    @TempDir
    Path pluginsDir;

    private HotPluginManager manager;

    @BeforeEach
    void setUp() {
        manager = new HotPluginManager(pluginsDir, beanRegistrar, routeRegistrar,
                persistence, integration, true);
    }

    @Test
    void registerPluginFromBytesCreatesMemoryPlugin() {
        String id = manager.registerPluginFromBytes(new byte[]{1, 2, 3}, "mem-1");
        assertEquals("mem-1", id);

        PluginInfo info = manager.getPlugin("mem-1");
        assertNotNull(info);
        assertEquals(PluginInfo.State.UPLOADED, info.getState());
        assertFalse(info.isPersisted(), "内存插件不持久化");
        assertNotNull(info.getJarPath());

        assertEquals(1, manager.getAllPlugins().size());
    }

    @Test
    void registerPluginFromPathPersistFalseRecordsOriginalPath() throws Exception {
        Path jar = Files.createTempFile("demo-", ".jar");
        Files.write(jar, new byte[]{0});

        String id = manager.registerPluginFromPath(jar, "demo", false);
        assertEquals("demo", id);

        PluginInfo info = manager.getPlugin("demo");
        assertNotNull(info);
        assertEquals(jar.toString(), info.getJarPath(), "persist=false 时记录原始路径");
        assertFalse(info.isPersisted());
    }

    @Test
    void registerPluginFromPathDerivesIdFromFilenameWhenAbsent() throws Exception {
        Path jar = Files.createTempFile("auto-id-", ".jar");
        Files.write(jar, new byte[]{0});

        String id = manager.registerPluginFromPath(jar, null, false);
        // 由文件名推导（去掉 .jar 后缀）
        assertTrue(id.startsWith("auto-id-"));
        assertNotNull(manager.getPlugin(id));
    }

    @Test
    void enablePluginReturnsFalseForNonExistent() {
        assertFalse(manager.enablePlugin("does-not-exist"));
    }

    @Test
    void enablePluginFailsWithoutSharedClassLoader() {
        // 注册一个内存插件（无效 JAR 字节），未初始化共享 ClassLoader 时启用必失败
        String id = manager.registerPluginFromBytes(new byte[]{}, "mem-bad");
        assertFalse(manager.enablePlugin(id));

        PluginInfo info = manager.getPlugin("mem-bad");
        assertEquals(PluginInfo.State.DISABLED, info.getState());
        assertNotNull(info.getErrorMessage(), "失败时应记录错误信息");
    }

    @Test
    void disablePluginReturnsFalseForNonExistent() {
        assertFalse(manager.disablePlugin("nope"));
    }

    @Test
    void uninstallPluginRemovesFromRegistry() {
        String id = manager.registerPluginFromBytes(new byte[]{1}, "mem-uninstall");
        assertNotNull(manager.getPlugin(id));

        assertTrue(manager.uninstallPlugin(id));
        assertNull(manager.getPlugin(id));
        // 卸载应触发持久化删除
        verify(persistence).delete(id);
    }

    @Test
    void getServiceFromPluginReturnsNullWhenNotEnabledOrMissing() {
        assertNull(manager.getServiceFromPlugin("missing", "bean"));

        String id = manager.registerPluginFromBytes(new byte[]{}, "mem-svc");
        // 仅 UPLOADED（未启用），获取服务返回 null
        assertNull(manager.getServiceFromPlugin(id, "bean"));
    }

    @Test
    void registerRouteReturnsFalseForNonExistentPluginWithoutTouchingRegistrar() {
        RouteInfo route = new RouteInfo("/x", HttpMethod.GET, "c", "m", null);
        assertFalse(manager.registerRoute("missing", route));
        verify(routeRegistrar, never()).registerRoute(any(), any());
    }

    @Test
    void getAvailableRoutesReturnsEmptyForMissingPlugin() {
        assertTrue(manager.getAvailableRoutes("missing").isEmpty());
    }

    @Test
    void getAllPluginsReturnsDefensiveCopy() {
        manager.registerPluginFromBytes(new byte[]{1}, "a");
        manager.registerPluginFromBytes(new byte[]{2}, "b");

        var list = manager.getAllPlugins();
        assertEquals(2, list.size());
        // 修改返回列表不影响内部注册表
        list.clear();
        assertEquals(2, manager.getAllPlugins().size());
    }
}
