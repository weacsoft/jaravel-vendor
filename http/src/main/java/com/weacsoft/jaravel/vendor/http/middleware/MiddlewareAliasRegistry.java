package com.weacsoft.jaravel.vendor.http.middleware;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 中间件别名注册表，对齐 Laravel {@code App\Http\Kernel::$routeMiddleware} 别名机制。
 * <p>
 * 在 Laravel 中，中间件通过别名注册，路由通过字符串别名引用中间件：
 * <pre>
 * // Kernel.php 中注册别名
 * protected $routeMiddleware = [
 *     'auth' => \App\Http\Middleware\Authenticate::class,
 *     'log'  => \App\Http\Middleware\LogRequest::class,
 * ];
 *
 * // 路由中使用别名（支持参数）
 * Route::middleware('auth:api');        // auth 中间件，参数 "api"
 * Route::middleware('auth:api,admin');  // auth 中间件，参数 "api" 和 "admin"
 * Route::middleware(['auth:api', 'log']); // 按顺序执行 auth 和 log
 * </pre>
 * <p>
 * 本类提供等价能力：
 * <ul>
 *   <li>{@link #register(String, Middleware)} — 注册无参数中间件别名</li>
 *   <li>{@link #register(String, MiddlewareResolver)} — 注册参数化中间件别名</li>
 *   <li>{@link #resolve(String)} — 解析别名表达式（如 {@code "auth:api,admin"}）为 {@link Middleware} 实例</li>
 *   <li>{@link #resolveAll(List)} — 批量解析别名表达式列表</li>
 * </ul>
 * <p>
 * 通过 {@link #getGlobal()} 获取全局静态实例，供 {@code Route} / {@code Router} 在解析别名时使用。
 * SpringBoot 模块的 {@code GlobalMiddlewareRegistry} 会在启动时自动扫描 {@code @Middleware} 注解的 Bean，
 * 并注册到全局实例中。
 *
 * <h3>别名表达式语法</h3>
 * <ul>
 *   <li>{@code "auth"} — 别名 "auth"，无参数</li>
 *   <li>{@code "auth:api"} — 别名 "auth"，参数 ["api"]</li>
 *   <li>{@code "auth:api,admin"} — 别名 "auth"，参数 ["api", "admin"]</li>
 * </ul>
 * 冒号分隔别名与参数，逗号分隔多个参数。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 注册别名
 * MiddlewareAliasRegistry.getGlobal().register("log", logMiddleware);
 * MiddlewareAliasRegistry.getGlobal().register("auth", params -> new AuthMiddleware(params));
 *
 * // 路由中使用别名（在 Route / Router 上调用 middleware(String...)）
 * router.get("/api/users", action).middleware("auth:api", "log");
 * // 等价于按顺序执行 auth(guard=api) → log
 * }</pre>
 */
public class MiddlewareAliasRegistry {

    private static final MiddlewareAliasRegistry GLOBAL = new MiddlewareAliasRegistry();

    private final Map<String, MiddlewareResolver> resolvers = new ConcurrentHashMap<>();

    /**
     * 获取全局静态实例。
     *
     * @return 全局注册表实例
     */
    public static MiddlewareAliasRegistry getGlobal() {
        return GLOBAL;
    }

    /**
     * 注册无参数中间件别名。
     * <p>
     * 别名引用时忽略参数，始终返回同一实例。适用于无状态中间件。
     *
     * @param alias      别名（如 "auth"、"log"）
     * @param middleware 中间件实例
     */
    public void register(String alias, Middleware middleware) {
        resolvers.put(alias, params -> middleware);
    }

    /**
     * 注册参数化中间件别名。
     * <p>
     * 每次引用别名时调用 resolver 根据参数创建中间件实例。
     * 适用于需要根据参数构造不同行为的中间件（如不同 guard 的认证）。
     *
     * @param alias    别名（如 "auth"）
     * @param resolver 中间件解析器
     */
    public void register(String alias, MiddlewareResolver resolver) {
        resolvers.put(alias, resolver);
    }

    /**
     * 解析别名表达式为中间件实例。
     * <p>
     * 表达式语法：
     * <ul>
     *   <li>{@code "auth"} → 别名 "auth"，无参数</li>
     *   <li>{@code "auth:api"} → 别名 "auth"，参数 ["api"]</li>
     *   <li>{@code "auth:api,admin"} → 别名 "auth"，参数 ["api", "admin"]</li>
     * </ul>
     *
     * @param expression 别名表达式
     * @return 中间件实例
     * @throws IllegalArgumentException 别名未注册时抛出
     */
    public Middleware resolve(String expression) {
        String alias;
        String[] params;

        int colonIndex = expression.indexOf(':');
        if (colonIndex < 0) {
            alias = expression.trim();
            params = new String[0];
        } else {
            alias = expression.substring(0, colonIndex).trim();
            String paramPart = expression.substring(colonIndex + 1).trim();
            if (paramPart.isEmpty()) {
                params = new String[0];
            } else {
                String[] rawParams = paramPart.split(",");
                params = new String[rawParams.length];
                for (int i = 0; i < rawParams.length; i++) {
                    params[i] = rawParams[i].trim();
                }
            }
        }

        MiddlewareResolver resolver = resolvers.get(alias);
        if (resolver == null) {
            throw new IllegalArgumentException("未注册的中间件别名: " + alias);
        }
        return resolver.resolve(params);
    }

    /**
     * 批量解析别名表达式列表，按顺序返回中间件实例列表。
     *
     * @param expressions 别名表达式列表（如 ["auth:api", "log"]）
     * @return 按序解析的中间件实例列表
     */
    public List<Middleware> resolveAll(List<String> expressions) {
        List<Middleware> result = new ArrayList<>();
        for (String expr : expressions) {
            Middleware resolved = resolve(expr);
            if (resolved != null) {
                result.add(resolved);
            }
        }
        return result;
    }

    /**
     * 检查别名是否已注册。
     *
     * @param alias 别名
     * @return 已注册返回 true
     */
    public boolean isRegistered(String alias) {
        return resolvers.containsKey(alias);
    }

    /**
     * 获取所有已注册的别名（不可修改视图）。
     *
     * @return 别名集合
     */
    public java.util.Set<String> getRegisteredAliases() {
        return Collections.unmodifiableSet(resolvers.keySet());
    }

    /**
     * 清除所有已注册的别名（主要用于测试）。
     */
    public void clear() {
        resolvers.clear();
    }

    // ========== 静态便捷方法（委托给全局实例） ==========

    /**
     * 向全局注册表注册无参数中间件别名。
     *
     * @param alias      别名
     * @param middleware 中间件实例
     * @see #register(String, Middleware)
     */
    public static void registerGlobal(String alias, Middleware middleware) {
        GLOBAL.register(alias, middleware);
    }

    /**
     * 向全局注册表注册参数化中间件别名。
     *
     * @param alias    别名
     * @param resolver 中间件解析器
     * @see #register(String, MiddlewareResolver)
     */
    public static void registerGlobal(String alias, MiddlewareResolver resolver) {
        GLOBAL.register(alias, resolver);
    }

    /**
     * 通过全局注册表解析别名表达式。
     *
     * @param expression 别名表达式
     * @return 中间件实例
     * @see #resolve(String)
     */
    public static Middleware resolveGlobal(String expression) {
        return GLOBAL.resolve(expression);
    }
}
