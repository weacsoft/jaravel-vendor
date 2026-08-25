package com.weacsoft.jaravel.vendor.core.registrar;

import com.weacsoft.jaravel.vendor.core.lookup.GlobalBeanProvider;
import com.weacsoft.jaravel.vendor.core.lookup.GlobalLookup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SingletonRegistrar} 单元测试（零 Spring）：验证「注册式但只允许一个」的语义。
 * <p>
 * P3 起扫描经 {@code GlobalLookup} 安装的 {@link GlobalBeanProvider} 驱动，
 * 本测试安装一个 Map 版实现，注解方法直接写在普通类上（无 @Bean）。
 * <p>
 * 覆盖：单个注册、重复注册报错、override 覆盖、多个 override 报错、无注册回退默认。
 */
class SingletonRegistrarTest {

    /** 测试用注解：模拟 @RegisterSessionStore / @RegisterQueueDriver。 */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface RegisterThing {
        boolean override() default false;
    }

    /** 测试用组件契约。 */
    interface Thing {
        String name();
    }

    record NamedThing(String name) implements Thing {
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
                throw new IllegalStateException("Bean '" + name + "' 不是 " + type.getName());
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

    /** 收集最终生效实例的测试注册器（P3：纯扫描器，构造不再需要容器）。 */
    static class ThingRegistrar extends SingletonRegistrar<RegisterThing, Thing> {

        Thing applied;
        boolean fallbackUsed;

        ThingRegistrar() {
            super(RegisterThing.class, Thing.class);
        }

        @Override
        protected boolean isOverride(RegisterThing annotation) {
            return annotation.override();
        }

        @Override
        protected void apply(Thing instance) {
            this.applied = instance;
        }

        @Override
        protected void applyFallback() {
            this.fallbackUsed = true;
            this.applied = new NamedThing("default");
        }
    }

    // ==================== 测试源（普通类 + 注解方法，零 Spring） ====================

    static class SingleSource {
        @RegisterThing
        public Thing one() {
            return new NamedThing("one");
        }
    }

    static class DuplicateSource {
        @RegisterThing
        public Thing one() {
            return new NamedThing("one");
        }

        @RegisterThing
        public Thing two() {
            return new NamedThing("two");
        }
    }

    static class OverrideSource {
        @RegisterThing
        public Thing normal() {
            return new NamedThing("normal");
        }

        @RegisterThing(override = true)
        public Thing overriding() {
            return new NamedThing("overriding");
        }
    }

    static class DoubleOverrideSource {
        @RegisterThing(override = true)
        public Thing first() {
            return new NamedThing("first");
        }

        @RegisterThing(override = true)
        public Thing second() {
            return new NamedThing("second");
        }
    }

    // ==================== 测试用例 ====================

    private MapProvider provider;

    @BeforeEach
    void setUp() {
        provider = new MapProvider();
        GlobalLookup.install(provider);
    }

    @AfterEach
    void tearDown() {
        GlobalLookup.uninstall();
    }

    /** 在给定源实例下运行注册器。 */
    private ThingRegistrar run(String name, Object source) {
        provider.registry.put(name, source);
        ThingRegistrar registrar = new ThingRegistrar();
        registrar.scan();
        return registrar;
    }

    @Test
    void testSingleRegistrationApplies() {
        ThingRegistrar registrar = run("source", new SingleSource());

        assertInstanceOf(NamedThing.class, registrar.applied);
        assertEquals("one", registrar.applied.name());
        assertTrue(!registrar.fallbackUsed, "存在注册项时不应触发回退");
    }

    @Test
    void testDuplicateRegistrationThrows() {
        RegistrarException ex = assertThrows(RegistrarException.class,
                () -> run("source", new DuplicateSource()));

        assertTrue(ex.getMessage().contains("只允许注册一个"),
                "重复注册应给出明确提示，实际: " + ex.getMessage());
    }

    @Test
    void testOverrideWins() {
        ThingRegistrar registrar = run("source", new OverrideSource());

        assertEquals("overriding", registrar.applied.name(),
                "override = true 的注册项应优先生效");
    }

    @Test
    void testMultipleOverridesThrow() {
        RegistrarException ex = assertThrows(RegistrarException.class,
                () -> run("source", new DoubleOverrideSource()));

        assertTrue(ex.getMessage().contains("多个 override"),
                "多个 override 应报错，实际: " + ex.getMessage());
    }

    @Test
    void testFallbackWhenNoRegistration() {
        provider.registry.put("unrelated", new Object());
        ThingRegistrar registrar = new ThingRegistrar();
        registrar.scan();

        assertTrue(registrar.fallbackUsed, "无注册项时应触发回退");
        assertEquals("default", registrar.applied.name());
    }

    @Test
    void testUninstalledProviderThrowsGuidance() {
        GlobalLookup.uninstall();
        ThingRegistrar registrar = new ThingRegistrar();
        RegistrarException ex = assertThrows(RegistrarException.class, registrar::scan);
        assertTrue(ex.getMessage().contains("GlobalBeanProvider"));
    }

    @Test
    void testScanIsIdempotent() {
        provider.registry.put("source", new SingleSource());
        ThingRegistrar registrar = new ThingRegistrar();
        registrar.scan();
        registrar.scan(); // 幂等：不重复登记
        assertInstanceOf(NamedThing.class, registrar.applied);
        assertEquals("one", registrar.applied.name());
    }
}
