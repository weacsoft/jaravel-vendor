package com.weacsoft.jaravel.vendor.http.controller;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
 * 控制器是 Spring Bean（需要 {@code @Autowired} 依赖注入），由
 * {@code SpringBootRouteAutoConfiguration} 在启动时扫描容器中实现了
 * {@link Controllers} 的 Bean 并注册到全局实例。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 注册控制器（通常由框架自动完成）
 * ControllerRegistry.getGlobal().register(userController);
 *
 * // 通过类名解析
 * Object controller = ControllerRegistry.getGlobal().resolve("UserController");
 *
 * // 通过类对象解析
 * Object controller = ControllerRegistry.getGlobal().resolve(UserController.class);
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

    /**
     * 获取全局静态实例。
     *
     * @return 全局注册表实例
     */
    public static ControllerRegistry getGlobal() {
        return GLOBAL;
    }

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
     *
     * @param clazz 控制器类
     * @return 控制器实例
     * @throws IllegalArgumentException 类未注册时抛出
     */
    public Object resolve(Class<?> clazz) {
        Object controller = classMap.get(clazz);
        if (controller == null) {
            throw new IllegalArgumentException("未注册的控制器类: " + clazz.getName());
        }
        return controller;
    }

    /**
     * 按名称（简名或全限定名）解析控制器实例。
     *
     * @param name 控制器名称（简名或全限定名）
     * @return 控制器实例
     * @throws IllegalArgumentException 名称未注册时抛出
     */
    public Object resolve(String name) {
        Object controller = nameMap.get(name);
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
