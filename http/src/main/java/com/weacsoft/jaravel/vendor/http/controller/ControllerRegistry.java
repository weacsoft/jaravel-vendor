package com.weacsoft.jaravel.vendor.http.controller;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 控制器注册表，对齐 Laravel 控制器路由引用机制。
 * <p>
 * 在 Laravel 中，路由通过字符串引用控制器方法：
 * <pre>
 * Route::get('/users', 'UserController@index');
 * </pre>
 * <p>
 * 本类提供等价能力，支持两种引用方式：
 * <ul>
 *   <li><b>字符串</b>：{@code "UserController::index"} — 类名 + 方法名</li>
 *   <li><b>类对象</b>：{@code UserController.class} + 方法名 — 忽略包名</li>
 * </ul>
 * 内部维护两张映射表：
 * <ul>
 *   <li>{@code Class → 实例}：类映射（通过 {@link #register(Object)} 注册）</li>
 *   <li>{@code String → 实例}：名称映射（简名和全限定名都写入）</li>
 * </ul>
 * <p>
 * <b>扫描范围指定</b>：用户可在 RouteServiceProvider 中通过
 * {@link #setScanBasePackages(String...)} 静态指定控制器扫描的基础包列表
 * （对齐 Laravel RouteServiceProvider 中手动指定路由文件加载范围）。
 * 若未指定，框架将自动扫描容器中所有实现了 {@link Controllers} 的 Bean
 * 以及标注了 Spring {@code @Controller} / {@code @RestController} 的 Bean。
 *
 * <h3>Spring @Controller 支持</h3>
 * 当使用 Spring Boot 时，标注了 {@code @Controller} 或 {@code @RestController} 的类
 * 会被自动扫描并注册，无需手动实现 {@link Controllers} 接口或调用
 * {@link #register(Object)}。框架在启动时通过 classpath 扫描发现这些类，
 * 并在运行时通过 {@link #setFallbackResolver(Function)} 设置的回退解析器
 * 从 Spring 容器中按需获取控制器实例，确保即使扫描遗漏也能正确解析。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 1. 在 RouteServiceProvider 中指定控制器扫描范围（推荐）
 * ControllerRegistry.setScanBasePackages("com.weacsoft.jaravel.app.http.controller");
 *
 * // 2. 框架自动扫描注册后，通过字符串/类对象引用
 * router.get("/users", "UserController::list");
 * router.get("/users/{id}", UserController.class, "show");
 *
 * // 3. 也支持纯 Spring @Controller（无需实现 Controllers 接口）
 * // @Controller
 * // public class TestController { ... }
 * // router.get("/", "TestController::index");  // 自动从 Spring 容器解析
 * }</pre>
 *
 * @see ControllerActionResolver
 * @see com.weacsoft.jaravel.vendor.springboot.SpringBootRouteAutoConfiguration
 */
public class ControllerRegistry {

    private static final ControllerRegistry GLOBAL = new ControllerRegistry();

    /** Class → 控制器实例 */
    private final Map<Class<?>, Object> classMap = new ConcurrentHashMap<>();
    /** 名称（简名/全限定名）→ 控制器实例 */
    private final Map<String, Object> nameMap = new ConcurrentHashMap<>();

    /** 用户手动指定的控制器扫描基础包列表（null 表示未指定，使用自动扫描） */
    private static volatile List<String> scanBasePackages = null;

    /**
     * 回退解析器：当控制器未在注册表中找到时，通过此回调从外部容器（如 Spring ApplicationContext）解析。
     * <p>
     * 由 {@code SpringBootRouteAutoConfiguration} 在启动时设置，用于支持 Spring {@code @Controller} /
     * {@code @RestController} 标注的控制器无需手动注册即可被路由引用。
     */
    private static volatile Function<String, Object> fallbackResolver = null;

    /**
     * 获取全局静态实例。
     *
     * @return 全局注册表实例
     */
    public static ControllerRegistry getGlobal() {
        return GLOBAL;
    }

    // ========== 扫描范围配置 ==========

    /**
     * 设置控制器扫描的基础包列表（对齐 Laravel RouteServiceProvider 手动指定范围）。
     * <p>
     * 在 RouteServiceProvider 中调用此方法后，{@code SpringBootRouteAutoConfiguration}
     * 将通过 classpath 扫描这些包下所有实现了 {@link Controllers} 的类，
     * 使用 Spring 的 {@code AutowireCapableBeanFactory} 实例化并自动注入依赖。
     * <p>
     * 若未调用此方法，框架将自动扫描容器中所有实现了 {@link Controllers} 的 Bean。
     *
     * @param packages 基础包列表（如 {@code "com.example.app.http.controller"}）
     */
    public static void setScanBasePackages(String... packages) {
        if (packages == null || packages.length == 0) {
            scanBasePackages = null;
        } else {
            scanBasePackages = Arrays.asList(packages);
        }
    }

    /**
     * 获取用户手动指定的扫描基础包列表。
     *
     * @return 基础包列表，未指定时返回 null
     */
    public static List<String> getScanBasePackages() {
        return scanBasePackages;
    }

    /**
     * 检查是否已手动指定扫描基础包。
     *
     * @return 已指定返回 true
     */
    public static boolean hasScanBasePackages() {
        return scanBasePackages != null && !scanBasePackages.isEmpty();
    }

    // ========== 回退解析器配置 ==========

    /**
     * 设置回退解析器，用于在注册表中未找到控制器时从外部容器（如 Spring ApplicationContext）按需解析。
     * <p>
     * 由 {@code SpringBootRouteAutoConfiguration} 在启动时调用，使标注了
     * Spring {@code @Controller} / {@code @RestController} 的控制器无需手动注册即可被路由引用。
     * <p>
     * 解析器接收控制器名称（简名或全限定名），返回控制器实例；找不到时返回 null。
     *
     * @param resolver 回退解析器（null 清除回退解析器）
     */
    public static void setFallbackResolver(Function<String, Object> resolver) {
        fallbackResolver = resolver;
    }

    /**
     * 获取当前回退解析器。
     *
     * @return 回退解析器，未设置时返回 null
     */
    public static Function<String, Object> getFallbackResolver() {
        return fallbackResolver;
    }

    // ========== 注册与解析 ==========

    /**
     * 注册控制器实例。
     * <p>
     * 同时写入类映射和名称映射（简名 + 全限定名），确保通过类对象或类名均能找到。
     *
     * @param controller 控制器实例
     */
    public void register(Object controller) {
        if (controller == null) return;
        Class<?> clazz = controller.getClass();
        classMap.put(clazz, controller);
        nameMap.put(clazz.getSimpleName(), controller);
        nameMap.put(clazz.getName(), controller);
    }

    /**
     * 按 Class 对象解析控制器实例。
     * <p>
     * 若注册表中未找到，尝试通过回退解析器（如 Spring ApplicationContext）按全限定名解析。
     *
     * @param clazz 控制器类
     * @return 控制器实例
     * @throws IllegalArgumentException 类未注册时抛出
     */
    public Object resolve(Class<?> clazz) {
        Object controller = classMap.get(clazz);
        if (controller == null && fallbackResolver != null) {
            controller = fallbackResolver.apply(clazz.getName());
            if (controller != null) {
                register(controller);
            }
        }
        if (controller == null) {
            throw new IllegalArgumentException("未注册的控制器类: " + clazz.getName());
        }
        return controller;
    }

    /**
     * 按名称（简名或全限定名）解析控制器实例。
     * <p>
     * 若注册表中未找到，尝试通过回退解析器（如 Spring ApplicationContext）按名称解析。
     * 解析成功后会自动注册到注册表，后续调用直接命中缓存。
     *
     * @param name 控制器名称（简名或全限定名）
     * @return 控制器实例
     * @throws IllegalArgumentException 名称未注册时抛出
     */
    public Object resolve(String name) {
        Object controller = nameMap.get(name);
        if (controller == null && fallbackResolver != null) {
            controller = fallbackResolver.apply(name);
            if (controller != null) {
                register(controller);
            }
        }
        if (controller == null) {
            throw new IllegalArgumentException("未注册的控制器: " + name);
        }
        return controller;
    }

    /**
     * 检查类是否已注册。
     *
     * @param clazz 控制器类
     * @return 已注册返回 true
     */
    public boolean isClassRegistered(Class<?> clazz) {
        return classMap.containsKey(clazz);
    }

    /**
     * 检查名称是否已注册。
     *
     * @param name 控制器名称
     * @return 已注册返回 true
     */
    public boolean isNameRegistered(String name) {
        return name != null && nameMap.containsKey(name);
    }

    /**
     * 获取所有已注册的控制器类（不可修改视图）。
     *
     * @return 类集合
     */
    public Set<Class<?>> getRegisteredClasses() {
        return Collections.unmodifiableSet(classMap.keySet());
    }

    /**
     * 清除所有已注册的控制器（主要用于测试）。
     */
    public void clear() {
        classMap.clear();
        nameMap.clear();
    }

    // ========== 静态便捷方法（委托给全局实例） ==========

    /**
     * 向全局注册表注册控制器。
     */
    public static void registerGlobal(Object controller) {
        GLOBAL.register(controller);
    }
}
