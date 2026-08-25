package com.weacsoft.jaravel.vendor.springboot.core;

import com.weacsoft.jaravel.vendor.core.lookup.GlobalBeanProvider;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 {@link ApplicationContext} 的 {@link GlobalBeanProvider} 适配器
 * （P3 核心交付：Spring 宿主与零 Spring core 之间的桥）。
 * <p>
 * <h3>行为契约（与 P3 前的 SpringContext 直连容器完全一致）</h3>
 * <ul>
 *   <li>按名 / 按类型解析 Bean — 委托容器（含别名、作用域、AOP 代理）。</li>
 *   <li>{@link #targetClass(Object)} — {@code AopUtils.getTargetClass} 解包 CGLIB / JDK 代理，
 *       保持注册器扫描的代理类方法解析语义。</li>
 *   <li>{@link #findAnnotation(Method, Class)} — {@code AnnotatedElementUtils.findMergedAnnotation}
 *       合并注解解析，保持 @AliasFor / 元注解语义。</li>
 *   <li>{@link #registerSingleton(String, Object)} — 同名先 {@code destroySingleton} 再
 *       {@code registerSingleton}，保持「更新」语义。</li>
 * </ul>
 */
public class ContextBeanProvider implements GlobalBeanProvider {

    private final ApplicationContext context;

    public ContextBeanProvider(ApplicationContext context) {
        if (context == null) {
            throw new IllegalArgumentException("ApplicationContext 不能为 null");
        }
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
        List<String> names = new ArrayList<>();
        for (String name : context.getBeanDefinitionNames()) {
            names.add(name);
        }
        return names;
    }

    @Override
    public <T> java.util.Map<String, T> beansOfType(Class<T> type) {
        // org.springframework.context.ApplicationContext 即 ListableBeanFactory
        return BeanFactoryUtils.beansOfTypeIncludingAncestors(
                (org.springframework.beans.factory.ListableBeanFactory) context, type);
    }

    @Override
    public Class<?> targetClass(Object bean) {
        return AopUtils.getTargetClass(bean);
    }

    @Override
    public <A extends Annotation> A findAnnotation(Method method, Class<A> annotationType) {
        return AnnotatedElementUtils.findMergedAnnotation(method, annotationType);
    }

    @Override
    public void registerSingleton(String name, Object instance) {
        if (!(context instanceof ConfigurableApplicationContext)) {
            throw new IllegalStateException("registerSingleton 需要 ConfigurableApplicationContext（Spring Boot 应用默认满足）");
        }
        ConfigurableListableBeanFactory factory = ((ConfigurableApplicationContext) context).getBeanFactory();
        if (factory.containsSingleton(name)) {
            ((DefaultListableBeanFactory) factory).destroySingleton(name);
        }
        factory.registerSingleton(name, instance);
    }
}
