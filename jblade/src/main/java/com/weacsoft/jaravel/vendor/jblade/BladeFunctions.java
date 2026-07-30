package com.weacsoft.jaravel.vendor.jblade;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * jblade 动态函数注册表。
 * <p>
 * 允许在不修改 jblade 核心源码的前提下，从外部"合并"注册自定义模板函数。
 * 模板中调用未内置的函数（如 {@code {{ route('admin.login') }}}、
 * {@code {{ myFunc($a, $b) }}}）时，编译器会生成对本注册表的运行时调用。
 * <p>
 * 典型用法（在应用启动阶段注册）：
 * <pre>{@code
 * // 注册 route() 函数，对接 http 模块的路由别名
 * BladeFunctions.register("route", args -> Route.url((String) args[0]));
 *
 * // 批量合并
 * Map<String, BladeFunction> fns = new HashMap<>();
 * fns.put("upper", args -> String.valueOf(args[0]).toUpperCase());
 * BladeFunctions.merge(fns);
 * }</pre>
 * <p>
 * 注册在渲染前完成即可（无需在编译前），因为编译产物通过名称做运行时查找。
 */
public final class BladeFunctions {

    /**
     * 模板函数接口：接收模板传入的实参数组，返回任意结果。
     */
    @FunctionalInterface
    public interface BladeFunction {
        Object apply(Object... args);
    }

    private static final Map<String, BladeFunction> FUNCTIONS = new ConcurrentHashMap<>();

    private BladeFunctions() {
    }

    /**
     * 注册（或覆盖）一个模板函数。
     *
     * @param name     函数名（模板中调用使用的名称）
     * @param function 函数实现
     */
    public static void register(String name, BladeFunction function) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("函数名不能为空");
        }
        if (function == null) {
            throw new IllegalArgumentException("函数实现不能为 null");
        }
        FUNCTIONS.put(name, function);
    }

    /**
     * 批量合并注册函数（外部合并机制）。
     *
     * @param functions 名称到实现的映射
     */
    public static void merge(Map<String, BladeFunction> functions) {
        if (functions == null) {
            return;
        }
        functions.forEach(BladeFunctions::register);
    }

    /**
     * 是否已注册指定名称的函数。
     */
    public static boolean has(String name) {
        return name != null && FUNCTIONS.containsKey(name);
    }

    /**
     * 调用已注册的函数。
     *
     * @param name 函数名
     * @param args 实参
     * @return 函数返回值
     * @throws IllegalStateException 如果函数未注册
     */
    public static Object call(String name, Object... args) {
        BladeFunction fn = FUNCTIONS.get(name);
        if (fn == null) {
            throw new IllegalStateException("jblade 模板函数未注册: " + name
                    + "。请通过 BladeFunctions.register(\"" + name + "\", ...) 注册。");
        }
        return fn.apply(args);
    }

    /**
     * 调用已注册的函数；未注册时返回默认值（不抛异常）。
     */
    public static Object callOrDefault(String name, Object defaultValue, Object... args) {
        BladeFunction fn = FUNCTIONS.get(name);
        if (fn == null) {
            return defaultValue;
        }
        return fn.apply(args);
    }

    /**
     * 注销一个函数（主要用于测试）。
     */
    public static void unregister(String name) {
        if (name != null) {
            FUNCTIONS.remove(name);
        }
    }

    /**
     * 清空所有注册（主要用于测试）。
     */
    public static void clear() {
        FUNCTIONS.clear();
    }
}
