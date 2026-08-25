package com.weacsoft.jaravel.vendor.core;

import com.weacsoft.jaravel.vendor.core.lookup.GlobalBeanProvider;
import com.weacsoft.jaravel.vendor.core.lookup.GlobalLookup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SpringContext} 静态门面单元测试（零 Spring，P3 起委托 {@link GlobalLookup} 安装的
 * {@link GlobalBeanProvider}；此处安装一个 Map 版实现验证全部能力）。
 */
class SpringContextTest {

    /** 测试用 Bean */
    public static class MyService {
    }

    /**
     * 测试用 Map 版 GlobalBeanProvider（非 Spring 宿主的最小实现）。
     */
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

    private MapProvider provider;

    @BeforeEach
    void setUp() {
        provider = new MapProvider();
        provider.registry.put("myService", new MyService());
        GlobalLookup.install(provider);
    }

    @AfterEach
    void tearDown() {
        GlobalLookup.uninstall();
    }

    @Test
    void beanByTypeIsResolved() {
        MyService service = SpringContext.bean(MyService.class);
        assertNotNull(service);
        assertSame(provider.registry.get("myService"), service);
    }

    @Test
    void beanByNameAndTypeIsResolved() {
        MyService service = SpringContext.bean("myService", MyService.class);
        assertNotNull(service);
        assertTrue(service instanceof MyService);
    }

    @Test
    void beanByNameReturnsRawInstance() {
        Object bean = SpringContext.bean("myService");
        assertNotNull(bean);
        assertEquals(MyService.class, bean.getClass());
    }

    @Test
    void containsChecksBeanPresence() {
        assertTrue(SpringContext.contains("myService"));
        assertFalse(SpringContext.contains("nonExistentBean"));
    }

    @Test
    void missingBeanThrowsIllegalState() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> SpringContext.bean("noSuchBean"));
        assertTrue(ex.getMessage().contains("noSuchBean"));
    }

    @Test
    void uninstalledProviderThrowsGuidance() {
        GlobalLookup.uninstall();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> SpringContext.bean(MyService.class));
        assertTrue(ex.getMessage().contains("GlobalBeanProvider"));
    }

    @Test
    void beanOrNullReturnsNullWhenUninstalledOrMissing() {
        GlobalLookup.uninstall();
        org.junit.jupiter.api.Assertions.assertNull(SpringContext.beanOrNull(MyService.class));
        org.junit.jupiter.api.Assertions.assertNull(SpringContext.beanOrNull("nope", MyService.class));

        GlobalLookup.install(provider);
        org.junit.jupiter.api.Assertions.assertNull(SpringContext.beanOrNull("nope", MyService.class));
        org.junit.jupiter.api.Assertions.assertSame(provider.registry.get("myService"),
                SpringContext.beanOrNull(MyService.class));
    }

    @Test
    void registerSingletonReplacesExisting() {
        MyService second = new MyService();
        SpringContext.registerSingleton("myService", second);
        assertSame(second, SpringContext.bean("myService", MyService.class));
    }
}
