package com.weacsoft.jaravel.vendor.core.lookup;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bean 值查找 SPI（零 Spring 抽象），对应传统实现里 {@code ApplicationContext}
 * 在纯代码中承担的角色：按名 / 按类型取 Bean、枚举 Bean 名。
 * <p>
 * <h3>设计原则</h3>
 * core 模块保持零 Spring。需要「容器里取 Bean」的能力统一经由本接口：
 * <ul>
 *   <li>Spring 宿主提供基于 {@code ApplicationContext} 的适配器（见 springboot 模块
 *       {@code ContextBeanProvider}），并额外重写 {@link #targetClass(Object)} 与
 *       {@link #findAnnotation(Method, Class)} 两个扩展点以保留 CGLIB 代理解包与
 *       合并注解解析语义；</li>
 *   <li>非 Spring 宿主（纯 JVM / 测试 / 嵌入式网关）提供基于 Map 的适配器即可。</li>
 * </ul>
 * 纯实现的默认行为（代理解包 = {@code bean.getClass()}，注解 = 直接查找）
 * 适用于没有 Spring AOP / CGLIB 代理的场景。
 */
public interface BeanLookup {

    /**
     * 按类型解析 Bean。
     *
     * @param type Bean 类型
     * @return Bean 实例
     * @throws RuntimeException 容器中不存在该 Bean 时
     */
    Object bean(Class<?> type);

    /**
     * 按名称解析 Bean（不强转类型）。
     *
     * @param name Bean 名称
     * @return Bean 实例
     */
    Object bean(String name);

    /**
     * 按名称 + 类型解析 Bean。
     *
     * @param name Bean 名称
     * @param type 期望类型
     * @return Bean 实例
     */
    Object bean(String name, Class<?> type);

    /** 容器是否包含指定名称的 Bean。 */
    boolean contains(String name);

    /** 枚举容器内所有 Bean 名称（含单例、工厂、注册项）。 */
    List<String> beanNames();

    /**
     * 按类型枚举容器内所有 Bean（保持名称顺序）。
     *
     * @param type Bean 类型
     * @param <T>  Bean 类型变量
     * @return 该类及其子类的 Bean 集合
     */
    @SuppressWarnings("unchecked")
    default <T> Map<String, T> beansOfType(Class<T> type) {
        Map<String, T> result = new LinkedHashMap<>();
        for (String name : beanNames()) {
            T candidate = (T) beanQuiet(name);
            if (candidate != null && type.isInstance(candidate)) {
                result.put(name, candidate);
            }
        }
        return result;
    }

    /**
     * 空安全的按类型解析：不存在（或解析失败）时返回 {@code null}（不抛异常）。
     *
     * @param type Bean 类型
     * @param <T>  Bean 类型
     * @return Bean 实例，或 {@code null}
     */
    @SuppressWarnings("unchecked")
    default <T> T beanOrNull(Class<T> type) {
        try {
            return (T) bean(type);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 空安全的按名解析：不存在或解析失败时返回 {@code null}（不抛异常）。
     *
     * @param name Bean 名称
     * @return Bean 实例，或 {@code null}
     */
    default Object beanQuiet(String name) {
        if (!contains(name)) {
            return null;
        }
        try {
            return bean(name);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 扩展点：解析 Bean 的目标类（纯实现 = 自身类；
     * Spring 适配器应解包 CGLIB / JDK 代理）。
     *
     * @param bean Bean 实例
     * @return 目标类
     */
    default Class<?> targetClass(Object bean) {
        return bean.getClass();
    }

    /**
     * 扩展点：在方法上查找注解（纯实现 = 直接查找；
     * Spring 适配器应支持合并注解 / 元注解解析）。
     *
     * @param method         方法
     * @param annotationType 注解类型
     * @param <A>            注解类型变量
     * @return 注解实例，未找到返回 {@code null}
     */
    default <A extends Annotation> A findAnnotation(Method method, Class<A> annotationType) {
        return method.isAnnotationPresent(annotationType) ? method.getAnnotation(annotationType) : null;
    }
}
