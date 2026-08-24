package com.weacsoft.jaravel.vendor.core.registrar;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SingletonRegistrar} 单元测试：验证「注册式但只允许一个」的语义。
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

    /** 收集最终生效实例的测试注册器。 */
    static class ThingRegistrar extends SingletonRegistrar<RegisterThing, Thing> {

        Thing applied;
        boolean fallbackUsed;

        ThingRegistrar(AnnotationConfigApplicationContext context) {
            super(context, RegisterThing.class, Thing.class);
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

    /** 在给定配置类下运行注册器。 */
    private ThingRegistrar run(Class<?>... configs) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            if (configs.length > 0) {
                context.register(configs);
            }
            context.refresh();
            ThingRegistrar registrar = new ThingRegistrar(context);
            registrar.afterSingletonsInstantiated();
            return registrar;
        }
    }

    // ==================== 测试配置类 ====================

    @Configuration
    static class SingleConfig {
        @Bean
        Object holder() {
            return new Object();
        }

        @RegisterThing
        public Thing one() {
            return new NamedThing("one");
        }
    }

    @Configuration
    static class DuplicateConfig {
        @RegisterThing
        public Thing one() {
            return new NamedThing("one");
        }

        @RegisterThing
        public Thing two() {
            return new NamedThing("two");
        }
    }

    @Configuration
    static class OverrideConfig {
        @RegisterThing
        public Thing normal() {
            return new NamedThing("normal");
        }

        @RegisterThing(override = true)
        public Thing overriding() {
            return new NamedThing("overriding");
        }
    }

    @Configuration
    static class DoubleOverrideConfig {
        @RegisterThing(override = true)
        public Thing first() {
            return new NamedThing("first");
        }

        @RegisterThing(override = true)
        public Thing second() {
            return new NamedThing("second");
        }
    }

    @Configuration
    static class EmptyConfig {
    }

    // ==================== 测试用例 ====================

    @Test
    void testSingleRegistrationApplies() {
        ThingRegistrar registrar = run(SingleConfig.class);

        assertInstanceOf(NamedThing.class, registrar.applied);
        assertEquals("one", registrar.applied.name());
        assertTrue(!registrar.fallbackUsed, "存在注册项时不应触发回退");
    }

    @Test
    void testDuplicateRegistrationThrows() {
        RegistrarException ex = assertThrows(RegistrarException.class,
                () -> run(DuplicateConfig.class));

        assertTrue(ex.getMessage().contains("只允许注册一个"),
                "重复注册应给出明确提示，实际: " + ex.getMessage());
    }

    @Test
    void testOverrideWins() {
        ThingRegistrar registrar = run(OverrideConfig.class);

        assertEquals("overriding", registrar.applied.name(),
                "override = true 的注册项应优先生效");
    }

    @Test
    void testMultipleOverridesThrow() {
        RegistrarException ex = assertThrows(RegistrarException.class,
                () -> run(DoubleOverrideConfig.class));

        assertTrue(ex.getMessage().contains("多个 override"),
                "多个 override 应报错，实际: " + ex.getMessage());
    }

    @Test
    void testFallbackWhenNoRegistration() {
        ThingRegistrar registrar = run(EmptyConfig.class);

        assertTrue(registrar.fallbackUsed, "无注册项时应触发回退");
        assertEquals("default", registrar.applied.name());
    }
}
