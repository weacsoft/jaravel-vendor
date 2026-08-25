package com.weacsoft.jaravel.vendor.core;

import com.weacsoft.jaravel.vendor.core.lookup.GlobalBeanProvider;
import com.weacsoft.jaravel.vendor.core.lookup.GlobalLookup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>P3 验收冒烟：非 Spring 宿主可用性</b>（{@code Spring优化方案.md} §5.4 新增门禁）。
 * <p>
 * 本类<b>不 import 任何 Spring 类型</b>（编译期即证明 core 链路零 Spring）：
 * 手动安装一个 Map 版 {@link GlobalBeanProvider}，验证
 * {@link Facade} / {@link App} / {@link Application} 全链路在纯 JVM 环境下可用，
 * 以及移除提供者后的空安全降级行为。
 * <br>
 * （Spring 宿主侧的等价链路由 springboot 模块的自动化测试覆盖。）
 */
@DisplayName("非 Spring 可用性冒烟（core 零 Spring 全链路）")
class NonSpringAvailabilitySmokeTest {

    /** 测试用服务。 */
    public static class ScoreService {
        public String hello() {
            return "hello-non-spring";
        }
    }

    /** 应用容器子类（对齐 publish 模板 AppConfig 的形态，去 Spring 化）。 */
    public static class TestApp extends Application {
        static {
            registerDefaultBinding("score", ScoreService.class);
        }
    }

    /** Map 版 GlobalBeanProvider（非 Spring 宿主的最小实现）。 */
    private static class MapProvider implements GlobalBeanProvider {
        final Map<String, Object> registry = new LinkedHashMap<>();

        @Override
        public Object bean(Class<?> type) {
            for (Object bean : registry.values()) {
                if (type.isInstance(bean)) {
                    return bean;
                }
            }
            throw new IllegalStateException("No bean of type " + type.getName());
        }

        @Override
        public Object bean(String name) {
            if (!registry.containsKey(name)) {
                throw new IllegalStateException("No bean named " + name);
            }
            return registry.get(name);
        }

        @Override
        public Object bean(String name, Class<?> type) {
            Object bean = bean(name);
            if (!type.isInstance(bean)) {
                throw new IllegalStateException("Bean '" + name + "' 类型不符");
            }
            return bean;
        }

        @Override
        public boolean contains(String name) {
            return registry.containsKey(name);
        }

        @Override
        public List<String> beanNames() {
            return new ArrayList<>(registry.keySet());
        }

        @Override
        public void registerSingleton(String name, Object instance) {
            registry.put(name, instance);
        }
    }

    private MapProvider provider;
    private ScoreService service;
    private TestApp app;

    @BeforeEach
    void setUp() {
        provider = new MapProvider();
        service = new ScoreService();
        app = new TestApp();
        provider.registry.put("scoreService", service);
        provider.registry.put("app", app);
        GlobalLookup.install(provider);
    }

    @AfterEach
    void tearDown() {
        GlobalLookup.uninstall();
    }

    @Test
    @DisplayName("Facade 全链路：resolve(type) / resolve(name,type) / App.app()")
    void facadeAndAppResolveThroughProvider() {
        // Facade.resolve — 类型解析
        assertSame(service, Facade.resolve(ScoreService.class));
        // Facade.resolve — 名称 + 类型解析
        assertSame(service, Facade.resolve("scoreService", ScoreService.class));
        // App.app() — SpringContext.bean(Application.class) 链路
        assertSame(app, App.app());
        // typed make — SpringContext 链路
        assertSame(service, app.make(ScoreService.class));
    }

    @Test
    @DisplayName("Application 三种注册方式免容器全链路可用")
    void applicationRegisterPathsWorkWithoutContainer() {
        // 1. bind（工厂，每次 make 新实例）
        app.bind("fresh", () -> new ScoreService());
        ScoreService a = app.make("fresh");
        ScoreService b = app.make("fresh");
        assertNotNull(a);
        assertNotNull(b);
        assertTrue(a != b, "bind 工厂应每次创建新实例");

        // 2. singleton（首次 make 后缓存）
        ScoreService singletonInstance = new ScoreService();
        app.register("one", singletonInstance);
        assertSame(singletonInstance, app.make("one"));

        // 3. defaultBinding（别名 → 类型经提供者解析）
        assertSame(service, app.<ScoreService>make("score"));
    }

    @Test
    @DisplayName("registerSingleton 发布到宿主 provider（更新语义）")
    void registerSingletonPublishesToProvider() {
        // Application 本地注册表 → publishToSpring（SpringContext.registerSingleton 链路）
        app.register("scoreService", service);
        assertTrue(app.publishToSpring("scoreService"), "已注册的服务应能发布到宿主");
        assertSame(service, SpringContext.bean("scoreService", ScoreService.class));

        // 直接经门面注册新名称 + 覆盖既有条目（更新语义）
        ScoreService replacement = new ScoreService();
        SpringContext.registerSingleton("published", replacement);
        assertSame(replacement, SpringContext.bean("published", ScoreService.class));
        SpringContext.registerSingleton("published", service);
        assertSame(service, SpringContext.bean("published", ScoreService.class));
    }

    @Test
    @DisplayName("空安全降级：未安装提供者时 beanOrNull=null、bind/singleton 仍可用")
    void gracefulDegradingWhenProviderUninstalled() {
        GlobalLookup.uninstall();

        // 空安全路径返回 null（不抛异常）
        assertNull(SpringContext.beanOrNull(ScoreService.class));
        assertNull(SpringContext.beanOrNull("scoreService", ScoreService.class));

        // 本地注册表路径不依赖容器，仍然可用
        app.register("local", service);
        assertSame(service, app.make("local"));
        app.bind("localFactory", (Supplier<Object>) ScoreService::new);
        assertNotNull(app.make("localFactory"));

        // 未注册名称：make(name) 返回 null（不经过容器）
        assertNull(app.<Object>make("neverRegistered"));
    }

    @Test
    @DisplayName("强依赖路径在提供者缺失时给出可操作提示")
    void strongPathFailsWithActionableMessage() {
        GlobalLookup.uninstall();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> Facade.resolve(ScoreService.class));
        assertTrue(ex.getMessage().contains("GlobalBeanProvider"),
                "提示应指向安装点，实际: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("install"),
                "提示应包含 install 指引，实际: " + ex.getMessage());
    }
}
