package com.weacsoft.jaravel.vendor.plugin.java.manager;

import com.weacsoft.jaravel.vendor.plugin.java.model.JavaFilePluginInfo;
import com.weacsoft.jaravel.vendor.plugin.jar.integration.DefaultPluginIntegration;
import com.weacsoft.jaravel.vendor.plugin.jar.registrar.PluginBeanRegistrar;
import com.weacsoft.jaravel.vendor.plugin.jar.registrar.PluginRouteRegistrar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import javax.tools.ToolProvider;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@link JavaFilePluginManager} 字符串源码模式单元测试。
 * <p>
 * 覆盖 {@code registerPluginFromSource} / {@code reloadPluginFromSource} 的核心场景：
 * 单源码注册、多源码注册、热重载、{@link JavaFilePluginInfo.SourceMode#STRING} 标记、
 * 以及空/null 源码的异常处理。
 * <p>
 * 需 JDK 环境提供 {@code javax.tools.JavaCompiler}；在纯 JRE 上涉及编译的用例会被跳过
 * （与 {@link com.weacsoft.jaravel.vendor.plugin.java.compiler.DynamicJavaCompilerTest} 一致）。
 */
class StringSourcePluginTest {

    /** 单组件测试源码：com.example.test.TestService */
    private static final String TEST_SERVICE_SOURCE =
            "package com.example.test;\n"
                    + "import com.weacsoft.jaravel.vendor.plugin.jar.annotation.PluginComponent;\n"
                    + "\n"
                    + "@PluginComponent\n"
                    + "public class TestService {\n"
                    + "    public String hello() {\n"
                    + "        return \"Hello from string source!\";\n"
                    + "    }\n"
                    + "}\n";

    /** 多源码场景：ServiceA */
    private static final String SERVICE_A_SOURCE =
            "package com.example.test;\n"
                    + "import com.weacsoft.jaravel.vendor.plugin.jar.annotation.PluginComponent;\n"
                    + "\n"
                    + "@PluginComponent\n"
                    + "public class ServiceA {\n"
                    + "    public String name() { return \"A\"; }\n"
                    + "}\n";

    /** 多源码场景：ServiceB */
    private static final String SERVICE_B_SOURCE =
            "package com.example.test;\n"
                    + "import com.weacsoft.jaravel.vendor.plugin.jar.annotation.PluginComponent;\n"
                    + "\n"
                    + "@PluginComponent\n"
                    + "public class ServiceB {\n"
                    + "    public String name() { return \"B\"; }\n"
                    + "}\n";

    /** 热重载新增组件：HelperService */
    private static final String HELPER_SERVICE_SOURCE =
            "package com.example.test;\n"
                    + "import com.weacsoft.jaravel.vendor.plugin.jar.annotation.PluginComponent;\n"
                    + "\n"
                    + "@PluginComponent\n"
                    + "public class HelperService {\n"
                    + "    public String help() { return \"help\"; }\n"
                    + "}\n";

    private JavaFilePluginManager manager;

    /**
     * 每个用例构造一个全新的管理器。
     * <p>
     * 字符串模式不依赖文件系统，因此 {@code sourceDir} 传 {@code null}。
     * 注册器使用轻量的 {@link GenericApplicationContext} 与库自带的 {@link DefaultPluginIntegration}
     * （空操作实现）构造，避免引入完整 Spring Boot 测试上下文；注册路径本身不会调用注册器，
     * 此处仅保证管理器可被正常实例化。
     */
    @BeforeEach
    void setUp() {
        ConfigurableApplicationContext context = new GenericApplicationContext();
        PluginBeanRegistrar beanRegistrar = new PluginBeanRegistrar(context);
        PluginRouteRegistrar routeRegistrar = new PluginRouteRegistrar(
                null, beanRegistrar, new DefaultPluginIntegration());
        // autoRegister=true，但本测试不启用插件，路由注册器不会被实际调用
        manager = new JavaFilePluginManager(null, beanRegistrar, routeRegistrar, true);
    }

    private static boolean hasJdk() {
        return ToolProvider.getSystemJavaCompiler() != null;
    }

    /**
     * 从单个源码字符串注册插件：状态应为 LOADED，组件类数量为 1。
     */
    @Test
    void testRegisterFromSourceString() {
        assumeTrue(hasJdk(), "需要 JDK 提供 Java 编译器");

        String pluginId = manager.registerPluginFromSource("string-plugin", TEST_SERVICE_SOURCE);
        assertEquals("string-plugin", pluginId);

        JavaFilePluginInfo info = manager.getPlugin(pluginId);
        assertNotNull(info, "插件信息不应为 null");
        assertEquals(JavaFilePluginInfo.State.LOADED, info.getState(),
                "注册后状态应为 LOADED");
        assertEquals(1, info.getComponentClasses().size(),
                "应扫描到 1 个 @PluginComponent 组件");
        assertTrue(info.getComponentClasses().contains("com.example.test.TestService"),
                "组件集合应包含 TestService 的全限定名");
    }

    /**
     * 从多个源码字符串注册插件：编译成功，组件类数量为 2。
     */
    @Test
    void testRegisterFromSourceMap() {
        assumeTrue(hasJdk(), "需要 JDK 提供 Java 编译器");

        Map<String, String> sources = Map.of(
                "ServiceA.java", SERVICE_A_SOURCE,
                "ServiceB.java", SERVICE_B_SOURCE);

        String pluginId = manager.registerPluginFromSource("multi-plugin", sources);
        assertEquals("multi-plugin", pluginId);

        JavaFilePluginInfo info = manager.getPlugin(pluginId);
        assertNotNull(info);
        assertEquals(JavaFilePluginInfo.State.LOADED, info.getState(),
                "多源码注册后状态应为 LOADED");
        assertEquals(2, info.getComponentClasses().size(),
                "应扫描到 2 个 @PluginComponent 组件");
        assertTrue(info.getComponentClasses().contains("com.example.test.ServiceA"));
        assertTrue(info.getComponentClasses().contains("com.example.test.ServiceB"));
    }

    /**
     * 从新源码热重载插件：重载后应使用新源码（组件数由 1 变为 2）。
     */
    @Test
    void testReloadFromSource() {
        assumeTrue(hasJdk(), "需要 JDK 提供 Java 编译器");

        // v1：单个组件
        manager.registerPluginFromSource("reload-plugin", TEST_SERVICE_SOURCE);
        JavaFilePluginInfo v1 = manager.getPlugin("reload-plugin");
        assertNotNull(v1);
        assertEquals(1, v1.getComponentClasses().size(), "初始版本应有 1 个组件");

        // v2：新增 HelperService 组件，验证重载确实使用了新源码
        Map<String, String> v2Sources = Map.of(
                "TestService.java", TEST_SERVICE_SOURCE,
                "HelperService.java", HELPER_SERVICE_SOURCE);
        boolean ok = manager.reloadPluginFromSource("reload-plugin", v2Sources);

        assertTrue(ok, "热重载应返回 true");
        JavaFilePluginInfo v2 = manager.getPlugin("reload-plugin");
        assertNotNull(v2);
        assertEquals(JavaFilePluginInfo.State.LOADED, v2.getState(),
                "重载后状态应恢复为 LOADED");
        assertEquals(2, v2.getComponentClasses().size(),
                "重载后应扫描到 2 个组件，证明使用了新源码");
        assertTrue(v2.getComponentClasses().contains("com.example.test.HelperService"));
    }

    /**
     * 字符串模式注册后，{@code sourceMode} 应为 {@link JavaFilePluginInfo.SourceMode#STRING}。
     */
    @Test
    void testSourceMode() {
        assumeTrue(hasJdk(), "需要 JDK 提供 Java 编译器");

        manager.registerPluginFromSource("mode-plugin", TEST_SERVICE_SOURCE);

        JavaFilePluginInfo info = manager.getPlugin("mode-plugin");
        assertNotNull(info);
        assertEquals(JavaFilePluginInfo.SourceMode.STRING, info.getSourceMode(),
                "字符串模式注册的插件 sourceMode 应为 STRING");
    }

    /**
     * 空源码应抛出 RuntimeException，且插件被标记为 DISABLED。
     */
    @Test
    void testEmptySource() {
        // 空源码在编译前即被拦截（parseSourceStrings 跳过空白源码 -> 无有效源码），无需 JDK
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> manager.registerPluginFromSource("empty-plugin", ""));
        assertTrue(ex.getMessage().contains("插件注册失败"),
                "异常信息应表明注册失败: " + ex.getMessage());

        JavaFilePluginInfo info = manager.getPlugin("empty-plugin");
        assertNotNull(info, "失败也应记录插件信息");
        assertEquals(JavaFilePluginInfo.State.DISABLED, info.getState(),
                "注册失败的插件状态应为 DISABLED");
        assertNotNull(info.getErrorMessage(), "应记录错误信息");
    }

    /**
     * null 源码应抛出 RuntimeException（manager 的空值处理），且插件被标记为 DISABLED。
     * <p>
     * 通过 {@link HashMap} 传入 null 值以绕过 {@code Map.of} 对 null 的限制，
     * 从而验证 {@link JavaFilePluginManager} 自身的 null 安全处理。
     */
    @Test
    void testNullSource() {
        Map<String, String> sources = new HashMap<>();
        sources.put("Source.java", null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> manager.registerPluginFromSource("null-plugin", sources));
        assertTrue(ex.getMessage().contains("插件注册失败"),
                "异常信息应表明注册失败: " + ex.getMessage());

        JavaFilePluginInfo info = manager.getPlugin("null-plugin");
        assertNotNull(info, "失败也应记录插件信息");
        assertEquals(JavaFilePluginInfo.State.DISABLED, info.getState(),
                "null 源码注册失败的插件状态应为 DISABLED");
    }
}
