package com.weacsoft.jaravel.vendor.jblade;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * jblade 自定义指令注册表。
 * <p>
 * 支持两类扩展（均无需修改 jblade 核心源码）：
 * <ol>
 *   <li><b>条件指令</b>（对应 Laravel 的 {@code Blade::if()}）：
 *       注册后模板可使用 {@code @xxx(expr) ... @endxxx}，以及 {@code @elsexxx}。
 *       条件求值在<b>运行时</b>进行，因此只要在渲染前注册即可。
 *       <pre>{@code
 *       BladeDirectives.condition("isPaginate", args ->
 *           args.length > 0 && args[0] instanceof Paginator);
 *       }</pre>
 *   </li>
 *   <li><b>输出指令</b>：注册后模板可使用 {@code @xxx(expr)}，
 *       运行时以表达式实参调用处理器，返回值直接输出（不转义）。
 *       <pre>{@code
 *       BladeDirectives.directive("datetime", args ->
 *           new SimpleDateFormat("yyyy-MM-dd").format(args[0]));
 *       }</pre>
 *   </li>
 * </ol>
 * <p>
 * 注意：编译器需要在<b>编译期</b>知道 {@code @xxx} 是条件指令还是输出指令
 * （决定生成 if 块还是输出语句），因此指令必须在模板<b>编译前</b>注册。
 * 一般在应用启动（ServiceProvider.register）阶段注册即可满足。
 */
public final class BladeDirectives {

    /**
     * 条件指令处理器：返回 true/false。
     */
    @FunctionalInterface
    public interface Condition {
        boolean test(Object... args);
    }

    /**
     * 输出指令处理器：返回要写入模板输出的内容。
     */
    @FunctionalInterface
    public interface Handler {
        Object handle(Object... args);
    }

    private static final Map<String, Condition> CONDITIONS = new ConcurrentHashMap<>();
    private static final Map<String, Handler> HANDLERS = new ConcurrentHashMap<>();

    private BladeDirectives() {
    }

    /**
     * 注册条件指令（等价 Laravel Blade::if）。
     * 模板用法：@name(args) ... @elsename(args) ... @else ... @endname
     */
    public static void condition(String name, Condition condition) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("指令名不能为空");
        }
        if (condition == null) {
            throw new IllegalArgumentException("条件实现不能为 null");
        }
        CONDITIONS.put(name, condition);
    }

    /**
     * 注册输出指令。模板用法：@name(args)，返回值直接输出。
     */
    public static void directive(String name, Handler handler) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("指令名不能为空");
        }
        if (handler == null) {
            throw new IllegalArgumentException("指令实现不能为 null");
        }
        HANDLERS.put(name, handler);
    }

    /**
     * 批量合并注册条件指令。
     */
    public static void mergeConditions(Map<String, Condition> conditions) {
        if (conditions != null) {
            conditions.forEach(BladeDirectives::condition);
        }
    }

    /**
     * 批量合并注册输出指令。
     */
    public static void mergeDirectives(Map<String, Handler> handlers) {
        if (handlers != null) {
            handlers.forEach(BladeDirectives::directive);
        }
    }

    public static boolean hasCondition(String name) {
        return name != null && CONDITIONS.containsKey(name);
    }

    public static boolean hasDirective(String name) {
        return name != null && HANDLERS.containsKey(name);
    }

    /**
     * 运行时求值条件指令。未注册时返回 false。
     */
    public static boolean evaluateCondition(String name, Object... args) {
        Condition c = CONDITIONS.get(name);
        if (c == null) {
            return false;
        }
        return c.test(args);
    }

    /**
     * 运行时执行输出指令。未注册时返回空串。
     */
    public static Object evaluateDirective(String name, Object... args) {
        Handler h = HANDLERS.get(name);
        if (h == null) {
            return "";
        }
        Object result = h.handle(args);
        return result == null ? "" : result;
    }

    /**
     * 注销指令（主要用于测试）。
     */
    public static void remove(String name) {
        if (name != null) {
            CONDITIONS.remove(name);
            HANDLERS.remove(name);
        }
    }

    /**
     * 清空所有注册（主要用于测试）。
     */
    public static void clear() {
        CONDITIONS.clear();
        HANDLERS.clear();
    }
}
