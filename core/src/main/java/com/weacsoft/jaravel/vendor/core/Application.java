package com.weacsoft.jaravel.vendor.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 应用容器基类，对齐 Laravel 的 {@code Application} / {@code Container}。
 * <p>
 * 提供服务定位器（Service Locator）能力，替代 Facade 静态代理模式：
 * <ul>
 *   <li>{@link #make(Class)} — 从 Spring 容器按类型解析 Bean（等价于 {@code Facade.resolve}）</li>
 *   <li>{@link #make(String)} — 按名称解析自定义注册的服务</li>
 *   <li>{@link #bind(String, Supplier)} — 注册工厂（每次 make 创建新实例，对齐 Laravel {@code App::bind}）</li>
 *   <li>{@link #singleton(String, Supplier)} — 注册单例工厂（首次 make 后缓存，对齐 Laravel {@code App::singleton}）</li>
 *   <li>{@link #register(String, Object)} — 直接注册现成实例</li>
 * </ul>
 *
 * <h3>继承扩展</h3>
 * 应用配置类继承本类后，可添加 typed 访问器方法，避免 Facade：
 * <pre>
 * &#64;Configuration
 * public class AppConfig extends Application {
 *     public AuthManager auth() { return make(AuthManager.class); }
 *     public CacheManager cache() { return make(CacheManager.class); }
 * }
 * </pre>
 * 使用方通过 {@code App.app().auth()} 获取服务实例，无需再写 {@code Auth.check()} 风格的静态门面。
 *
 * <h3>CGLIB 兼容性</h3>
 * {@code @Configuration} 类会被 Spring CGLIB 代理子类化。本类的服务注册表使用
 * {@code static} 字段，确保代理对象与原始实例共享同一份注册表，不会因 CGLIB 代理
 * 导致字段未初始化的问题。
 */
public class Application {

    /** 单例服务缓存：name -> instance（进程级，启动后只读为主） */
    private static final Map<String, Object> singletons = new ConcurrentHashMap<>();

    /** 工厂注册表：name -> Supplier（transient，每次 make 调用） */
    private static final Map<String, Supplier<Object>> factories = new ConcurrentHashMap<>();

    // ==================== Spring Bean 解析 ====================

    /**
     * 从 Spring 容器按类型解析 Bean。
     * <p>
     * 等价于 {@link SpringContext#bean(Class)}，是所有 typed 访问器的基础。
     *
     * @param type Bean 类型
     * @return Bean 实例
     */
    public <T> T make(Class<T> type) {
        return SpringContext.bean(type);
    }

    /**
     * 从 Spring 容器按名称 + 类型解析 Bean。
     *
     * @param name Bean 名称
     * @param type Bean 类型
     * @return Bean 实例
     */
    public <T> T make(String name, Class<T> type) {
        return SpringContext.bean(name, type);
    }

    // ==================== 自定义服务注册与解析 ====================

    /**
     * 按名称解析自定义服务。
     * <p>
     * 查找顺序：
     * <ol>
     *   <li>单例缓存（{@link #register} 或 {@link #singleton} 注册的实例）</li>
     *   <li>工厂注册（{@link #bind} 注册的 Supplier，每次调用创建新实例）</li>
     * </ol>
     *
     * @param name 服务名称
     * @return 服务实例，未注册返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> T make(String name) {
        Object instance = singletons.get(name);
        if (instance != null) {
            return (T) instance;
        }
        Supplier<Object> factory = factories.get(name);
        if (factory != null) {
            return (T) factory.get();
        }
        return null;
    }

    /**
     * 注册单例工厂（对齐 Laravel {@code App::singleton}）。
     * <p>
     * 首次 {@link #make(String)} 时调用工厂创建实例并缓存，后续直接返回缓存实例。
     *
     * @param name    服务名称
     * @param factory 实例工厂
     */
    public void singleton(String name, Supplier<Object> factory) {
        factories.put(name, () -> {
            Object instance = singletons.get(name);
            if (instance == null) {
                instance = factory.get();
                singletons.put(name, instance);
            }
            return instance;
        });
    }

    /**
     * 注册工厂（对齐 Laravel {@code App::bind}）。
     * <p>
     * 每次 {@link #make(String)} 都调用工厂创建新实例，不缓存。
     *
     * @param name    服务名称
     * @param factory 实例工厂
     */
    public void bind(String name, Supplier<Object> factory) {
        factories.put(name, factory);
    }

    /**
     * 直接注册现成实例（对齐 Laravel {@code App::instance}）。
     *
     * @param name     服务名称
     * @param instance 服务实例
     */
    public void register(String name, Object instance) {
        singletons.put(name, instance);
    }

    /**
     * 检查指定名称的服务是否已注册。
     *
     * @param name 服务名称
     * @return 已注册返回 {@code true}
     */
    public boolean bound(String name) {
        return singletons.containsKey(name) || factories.containsKey(name);
    }

    /**
     * 注销指定名称的服务。
     *
     * @param name 服务名称
     */
    public void forget(String name) {
        singletons.remove(name);
        factories.remove(name);
    }
}
