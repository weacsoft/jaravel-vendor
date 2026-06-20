package com.weacsoft.jaravel.vendor.core;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring 上下文持有器。
 * <p>
 * 供 {@link Facade} 门面静态访问容器中的 Bean，模仿 Laravel 的 Facade 机制：
 * 门面是一个静态代理，背后真正干活的是容器里解析出的实例。
 */
@Component
public class SpringContext implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringContext.context = applicationContext;
    }

    public static ApplicationContext get() {
        if (context == null) {
            throw new IllegalStateException("SpringContext 尚未初始化，ApplicationContext 未注入");
        }
        return context;
    }

    public static <T> T bean(Class<T> type) {
        return get().getBean(type);
    }

    public static <T> T bean(String name, Class<T> type) {
        return get().getBean(name, type);
    }

    @SuppressWarnings("unchecked")
    public static <T> T bean(String name) {
        return (T) get().getBean(name);
    }

    public static boolean contains(String name) {
        return get().containsBean(name);
    }
}
