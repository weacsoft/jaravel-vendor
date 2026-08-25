package com.weacsoft.jaravel.vendor.database;

import com.weacsoft.jaravel.vendor.core.lookup.GlobalBeanProvider;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试专用：把 `ApplicationContext` 适配为 core 的 {@link GlobalBeanProvider}
 * （P3 起 core 静态门面经 {@code GlobalLookup} 安装的提供者驱动，
 * 测试中以本类替代旧版 {@code SpringContext#setApplicationContext} 注入）。
 */
class CtxProvider implements GlobalBeanProvider {

    private final ApplicationContext context;

    CtxProvider(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public Object bean(Class<?> type) {
        return context.getBean(type);
    }

    @Override
    public Object bean(String name) {
        return context.getBean(name);
    }

    @Override
    public Object bean(String name, Class<?> type) {
        return context.getBean(name, type);
    }

    @Override
    public boolean contains(String name) {
        return context.containsBean(name);
    }

    @Override
    public List<String> beanNames() {
        return new ArrayList<>(List.of(context.getBeanDefinitionNames()));
    }

    @Override
    public void registerSingleton(String name, Object instance) {
        throw new UnsupportedOperationException("registerSingleton 未实现（测试适配器）");
    }
}
