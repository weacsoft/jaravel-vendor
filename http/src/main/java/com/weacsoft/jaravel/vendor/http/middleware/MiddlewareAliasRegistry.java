package com.weacsoft.jaravel.vendor.http.middleware;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 中间件别名注册表，对齐 Laravel {@code App\Http\Kernel::$routeMiddleware} 别名机制。
 * <p>
 * 在 Laravel 中，中间件通过别名注册，路由通过字符串别名引用中间件：
 * <pre>
 * // Kernel.php 中注册别名
 * protected $routeMiddleware = [
 *     'auth' => \App\Http\Middleware\Authenticate::class,
 * ];
 *
 * // 路由中使用别名（支持参数）
 * Route::middleware('auth:api');        // auth 中间件，参数 "api"
 * Route::middleware('auth:api,admin');  // auth 中间件，参数 "api" 和 "admin"
 * </pre>
 * <p>
 * 本类提供等价能力。别名映射到 {@link Middleware} 实例，解析别名表达式时，
 * 通过闭包将参数烘焙到返回的 Middleware 中：
 * <ul>
 *   <li>{@link #register(String, Middleware)} — 注册别名</li>
 *   <li>{@link #resolve(String)} — 解析别名表达式，返回带参数的 Middleware 闭包</li>
 * </ul>
 * <p>
 * 通过 {@link #getGlobal()} 获取全局静态实例。SpringBoot 模块的 {@code MiddlewareAliasRegistrar}
 * 会在启动时自动扫描 {@code @MiddlewareAlias} 注解的 Bean 并注册到全局实例。
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
 * MiddlewareAliasRegistry.getGlobal().register("auth", authMiddleware);
 *
 * // 路由中使用别名
 * router.get("/api/users", action).middleware("auth:api", "log");
 * // resolve("auth:api") 返回 (req, next, ignored) -> authMiddleware.handle(req, next, "api")
 * }</pre>
 */
public class MiddlewareAliasRegistry {

    private static final MiddlewareAliasRegistry GLOBAL = new MiddlewareAliasRegistry();

    private final Map<String, Middleware> aliases = new ConcurrentHashMap<>();

    /**
     * 获取全局静态实例。
     *
     * @return 全局注册表实例
     */
    public static MiddlewareAliasRegistry getGlobal() {
        return GLOBAL;
    }

    /**
     * 注册中间件别名。
     *
     * @param alias      别名（如 "auth"、"log"）
     * @param middleware 中间件实例
     */
    public void register(String alias, Middleware middleware) {
        aliases.put(alias, middleware);
    }

    /**
     * 解析别名表达式，返回带参数烘焙的 {@link Middleware} 闭包。
     * <p>
     * 表达式语法：
     * <ul>
     *   <li>{@code "auth"} → 别名 "auth"，无参数</li>
     *   <li>{@code "auth:api"} → 别名 "auth"，参数 ["api"]</li>
     *   <li>{@code "auth:api,admin"} → 别名 "auth"，参数 ["api", "admin"]</li>
     * </ul>
     * <p>
     * 返回的 Middleware 闭包会忽略调用时传入的 params，使用解析时烘焙的参数。
     *
     * @param expression 别名表达式
     * @return 带参数的 Middleware 闭包
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

        Middleware original = aliases.get(alias);
        if (original == null) {
            throw new IllegalArgumentException("未注册的中间件别名: " + alias);
        }

        // 通过闭包烘焙参数，返回的 Middleware 忽略调用时传入的 params
        final String[] bakedParams = params;
        return (request, next, ignored) -> original.handle(request, next, bakedParams);
    }

    /**
     * 批量解析别名表达式列表，按顺序返回 Middleware 列表。
     *
     * @param expressions 别名表达式列表（如 ["auth:api", "log"]）
     * @return 按序解析的 Middleware 列表
     */
    public List<Middleware> resolveAll(List<String> expressions) {
        List<Middleware> result = new ArrayList<>();
        for (String expr : expressions) {
            result.add(resolve(expr));
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
        return aliases.containsKey(alias);
    }

    /**
     * 获取所有已注册的别名（不可修改视图）。
     *
     * @return 别名集合
     */
    public Set<String> getRegisteredAliases() {
        return Collections.unmodifiableSet(aliases.keySet());
    }

    /**
     * 清除所有已注册的别名（主要用于测试）。
     */
    public void clear() {
        aliases.clear();
    }

    // ========== 静态便捷方法（委托给全局实例） ==========

    /**
     * 向全局注册表注册中间件别名。
     */
    public static void registerGlobal(String alias, Middleware middleware) {
        GLOBAL.register(alias, middleware);
    }

    /**
     * 通过全局注册表解析别名表达式。
     */
    public static Middleware resolveGlobal(String expression) {
        return GLOBAL.resolve(expression);
    }
}
