package com.weacsoft.jaravel.vendor.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SpringContext} 静态上下文持有器单元测试。
 */
class SpringContextTest {

    /** 测试用 Bean */
    public static class MyService {
    }

    private GenericApplicationContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new GenericApplicationContext();
        ctx.getBeanFactory().registerSingleton("myService", new MyService());
        ctx.refresh();
        // 通过实例方法注入静态上下文
        new SpringContext().setApplicationContext(ctx);
    }

    @AfterEach
    void tearDown() {
        // 复位静态状态，避免影响其它测试
        new SpringContext().setApplicationContext(null);
    }

    @Test
    void getReturnsInjectedContext() {
        assertSame(ctx, SpringContext.get());
    }

    @Test
    void getThrowsWhenNotInitialized() {
        new SpringContext().setApplicationContext(null);
        IllegalStateException ex = assertThrows(IllegalStateException.class, SpringContext::get);
        assertTrue(ex.getMessage().contains("SpringContext"));
    }

    @Test
    void beanByNameAndTypeIsResolved() {
        MyService service = SpringContext.bean("myService", MyService.class);
        assertNotNull(service);
        assertTrue(service instanceof MyService);
    }

    @Test
    void beanByTypeIsResolved() {
        MyService service = SpringContext.bean(MyService.class);
        assertNotNull(service);
    }

    @Test
    void containsChecksBeanPresence() {
        assertTrue(SpringContext.contains("myService"));
        assertFalse(SpringContext.contains("nonExistentBean"));
    }

    @Test
    void beanByNameReturnsRawInstance() {
        Object bean = SpringContext.bean("myService");
        assertNotNull(bean);
        assertEquals(MyService.class, bean.getClass());
    }
}
