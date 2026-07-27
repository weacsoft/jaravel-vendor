package com.weacsoft.jaravel.vendor.core;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 应用容器基类，对齐 Laravel 的 {@code Application} / {@code Container}。
 * <p>
 * 提供服务定位器（Service Locator）能力，替代 Facade 静态代理模式：
 * <ul>
 *   <li>{@link #make(Class)} — 从 Spring 容器按类型解析 Bean（等价于 {@code Facade.resolve}）</li>
 *   <li>{@link #make(String)} — 按名称解析服务（自动注册别名 / bind / singleton / register）</li>
 *   <li>{@link #bind(String, Supplier)} — 注册工厂（每次 make 创建新实例，对齐 Laravel {@code App::bind}）</li>
 *   <li>{@link #singleton(String, Supplier)} — 注册单例工厂（首次 make 后缓存，对齐 Laravel {@code App::singleton}）</li>
 *   <li>{@link #register(String, Object)} — 直接注册现成实例</li>
 *   <li>{@link #registerDefaultBinding(String, Class)} — 注册别名到 Spring Bean 类型的映射（自动注册）</li>
 * </ul>
 *
 * <h3>自动注册（对齐 Laravel aliases 数组）</h3>
 * Laravel 在 {@code config/app.php} 的 {@code aliases} 数组中集中声明常用服务别名。
 * 本类通过 {@link #registerDefaultBinding(String, Class)} 实现相同机制：
 * 应用配置类在 static 块中集中注册，{@code make("auth")} 即可解析为对应的 Spring Bean。
 * <pre>
 * static {
 *     registerDefaultBinding("auth", AuthManager.class);
 *     registerDefaultBinding("cache", CacheManager.class);
 * }
 * </pre>
 *
 * <h3>继承扩展 + 免强转</h3>
 * 应用配置类继承本类后，添加 {@code public static AppConfig app()} 方法返回自身类型，
 * 即可实现 {@code AppConfig.app().auth()} 免强转调用：
 * <pre>
 * &#64;Configuration
 * public class AppConfig extends Application {
 *     public static AppConfig app() {
 *         return SpringContext.bean(AppConfig.class);
 *     }
 *     public AuthManager auth() { return make(AuthManager.class); }
 * }
 * </pre>
 *
 * <h3>CGLIB 兼容性</h3>
 * {@code @Configuration} 类会被 Spring CGLIB 代理子类化。本类的服务注册表使用
 * {@code static} 字段，确保代理对象与原始实例共享同一份注册表，不会因 CGLIB 代理
 * 导致字段未初始化的问题。
 *
 * <h3>与 Spring 容器的关系</h3>
 * {@code singleton} / {@code register} / {@code bind} 注册的服务仅存在于本类的 static Map 中，
 * <b>不会</b>进入 Spring 的 BeanFactory。这意味着：
 * <ul>
 *   <li>{@code @Autowired} 无法注入这些服务</li>
 *   <li>它们只能通过 {@code make(name)} 获取</li>
 *   <li>适用于非 Spring Bean 的自定义服务</li>
 * </ul>
 * 如果需要 Spring 管理，应使用 {@code @Bean} 方法注册，或在运行时调用
 * {@link #publishToSpring(String)} / {@link #publishAllToSpring()} 将服务发布到 Spring。
 *
 * <h3>发布到 Spring 容器</h3>
 * 默认情况下，{@code singleton} / {@code bind} / {@code register} 注册的服务不进入 Spring 容器。
 * 当需要让 Spring 管理（如 {@code @Autowired} 注入）时，可手动发布：
 * <ul>
 *   <li>{@link #publishToSpring(String)} — 发布单个服务到 Spring（如已存在则替换）</li>
 *   <li>{@link #publishAllToSpring()} — 批量发布所有已注册服务到 Spring</li>
 * </ul>
 * <b>注意</b>：{@code registerDefaultBinding} 注册的别名（如 "auth" -> AuthManager.class）
 * 不会被发布，因为它们指向的 Bean 已经存在于 Spring 容器中。
 * <pre>
 * // 发布单个服务
 * AppConfig.app().publishToSpring("myService");
 *
 * // 批量发布所有自定义服务
 * int count = AppConfig.app().publishAllToSpring();
 * </pre>
 */
public class Application {

    /** 单例服务缓存：name -> instance（进程级，启动后只读为主） */
    private static final Map<String, Object> singletons = new ConcurrentHashMap<>();

    /** 工厂注册表：name -> Supplier（transient，每次 make 调用） */
    private static final Map<String, Supplier<Object>> factories = new ConcurrentHashMap<>();

    /** 自动注册别名表：name -> Class（对齐 Laravel aliases 数组，make(name) 时从 Spring 解析） */
    private static final Map<String, Class<?>> defaultBindings = new ConcurrentHashMap<>();

    // ==================== 自动注册（对齐 Laravel aliases 数组） ====================

    /**
     * 注册服务别名到 Spring Bean 类型的映射（对齐 Laravel {@code config/app.php} 的 aliases 数组）。
     * <p>
     * 注册后 {@link #make(String)} 会按名称查找此映射，找到后从 Spring 容器按类型解析 Bean。
     * <p>
     * 通常在应用配置类的 static 块中集中调用，实现「常用服务自动注册」：
     * <pre>
     * static {
     *     registerDefaultBinding("auth", AuthManager.class);
     *     registerDefaultBinding("cache", CacheManager.class);
     *     registerDefaultBinding("config", ConfigRepository.class);
     * }
     * </pre>
     *
     * @param name 服务别名（如 {@code "auth"}、{@code "cache"}）
     * @param type Spring Bean 类型
     */
    public static void registerDefaultBinding(String name, Class<?> type) {
        defaultBindings.put(name, type);
    }

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

    // ==================== 按名称解析服务 ====================

    /**
     * 按名称解析服务。
     * <p>
     * 查找顺序：
     * <ol>
     *   <li>单例缓存（{@link #register} 或 {@link #singleton} 注册的实例）</li>
     *   <li>工厂注册（{@link #bind} 注册的 Supplier，每次调用创建新实例）</li>
     *   <li>自动注册别名（{@link #registerDefaultBinding} 注册的 name -> Class 映射，从 Spring 解析）</li>
     * </ol>
     *
     * @param name 服务名称
     * @return 服务实例，未注册返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> T make(String name) {
        // 1. 单例缓存
        Object instance = singletons.get(name);
        if (instance != null) {
            return (T) instance;
        }
        // 2. 工厂注册
        Supplier<Object> factory = factories.get(name);
        if (factory != null) {
            return (T) factory.get();
        }
        // 3. 自动注册别名（name -> Class，从 Spring 解析）
        Class<?> type = defaultBindings.get(name);
        if (type != null) {
            return (T) SpringContext.bean(type);
        }
        return null;
    }

    // ==================== 自定义服务注册 ====================

    /**
     * 注册单例工厂（对齐 Laravel {@code App::singleton}）。
     * <p>
     * 首次 {@link #make(String)} 时调用工厂创建实例并缓存，后续直接返回缓存实例。
     * <p>
     * <b>注意</b>：注册的服务仅存在于本类的 static Map 中，不会进入 Spring 容器。
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
     * <p>
     * <b>注意</b>：注册的服务仅存在于本类的 static Map 中，不会进入 Spring 容器。
     *
     * @param name    服务名称
     * @param factory 实例工厂
     */
    public void bind(String name, Supplier<Object> factory) {
        factories.put(name, factory);
    }

    /**
     * 直接注册现成实例（对齐 Laravel {@code App::instance}）。
     * <p>
     * <b>注意</b>：注册的服务仅存在于本类的 static Map 中，不会进入 Spring 容器。
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
        return singletons.containsKey(name) || factories.containsKey(name) || defaultBindings.containsKey(name);
    }

    /**
     * 注销指定名称的服务。
     *
     * @param name 服务名称
     */
    public void forget(String name) {
        singletons.remove(name);
        factories.remove(name);
        defaultBindings.remove(name);
    }

    // ==================== 发布到 Spring 容器 ====================

    /**
     * 发布指定名称的服务到 Spring 容器（默认单例）。
     * <p>
     * 从 Application 的注册表中解析服务实例，然后通过 {@link SpringContext#registerSingleton}
     * 注册为 Spring 单例 Bean。如果 Spring 中已存在同名 Bean，会先销毁旧实例再注册新实例。
     * <p>
     * <b>别名不会被发布</b>：{@link #registerDefaultBinding} 注册的别名（如 "auth" -> AuthManager.class）
     * 指向的 Bean 已存在于 Spring 容器中，无需重复发布。只有通过 {@code singleton} /
     * {@code bind} / {@code register} 注册的自定义服务才会被发布。
     * <p>
     * 默认不调用此方法——服务仅在 Application 的 static Map 中，不进入 Spring。
     * 需要让 Spring 管理（如 {@code @Autowired} 注入）时才手动调用。
     *
     * @param name 服务名称
     * @return 发布成功返回 {@code true}，服务不存在返回 {@code false}
     */
    public boolean publishToSpring(String name) {
        Object instance = make(name);
        if (instance == null) {
            return false;
        }
        SpringContext.registerSingleton(name, instance);
        return true;
    }

    /**
     * 发布所有已注册的自定义服务到 Spring 容器（默认单例）。
     * <p>
     * 遍历 {@code singleton} 和 {@code bind} 注册的所有服务，解析实例后注册到 Spring。
     * 别名（{@code defaultBindings}）不会被发布，因为它们指向的 Bean 已在 Spring 中。
     * <p>
     * 默认不调用此方法。需要批量发布时才手动调用。
     *
     * @return 成功发布的服务数量
     */
    public int publishAllToSpring() {
        int count = 0;
        Set<String> published = new HashSet<>();
        // 发布单例服务
        for (String name : singletons.keySet()) {
            if (publishToSpring(name)) {
                published.add(name);
                count++;
            }
        }
        // 发布工厂服务（调用一次 factory 创建实例）
        for (String name : factories.keySet()) {
            if (!published.contains(name) && publishToSpring(name)) {
                count++;
            }
        }
        return count;
    }
}
