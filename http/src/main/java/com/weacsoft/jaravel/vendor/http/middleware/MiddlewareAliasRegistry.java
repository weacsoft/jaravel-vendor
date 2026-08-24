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
 * 本类提供等价能力，同时支持三种引用方式：
 * <ul>
 *   <li><b>别名</b>：{@code middleware("auth:api")} — 字符串别名 + 参数</li>
 *   <li><b>类对象</b>：{@code middleware(AuthMiddleware.class, "api")} — Class + 可选参数</li>
 *   <li><b>类名</b>：{@code middleware("AuthMiddleware:api")} — 类名字符串，语法与别名一致</li>
 * </ul>
 * 内部维护两张映射表：
 * <ul>
 *   <li>{@code alias → Middleware}：别名映射（通过 {@link #register(String, Middleware)} 注册，alias 非空时写入）</li>
 *   <li>{@code Class → Middleware}：类映射（所有注册的中间件都写入，无论是否有别名）</li>
 * </ul>
 * 解析时，字符串表达式优先按别名查找，未命中则按类名（简名或全限定名）查找。
 * <p>
 * 通过 {@link #getGlobal()} 获取全局静态实例。SpringBoot 模块的 {@code SpringBootRouteAutoConfiguration}
 * 会在启动时自动扫描 {@code @MiddlewareAlias} 注解的 Bean 并注册到全局实例。
 *
 * <h3>别名/类名表达式语法</h3>
 * <ul>
 *   <li>{@code "auth"} — 别名 "auth" 或类名 "auth"，无参数</li>
 *   <li>{@code "auth:api"} — 别名/类名 "auth"，参数 ["api"]</li>
 *   <li>{@code "auth:api,admin"} — 别名/类名 "auth"，参数 ["api", "admin"]</li>
 * </ul>
 * 冒号分隔名称与参数，逗号分隔多个参数。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 注册别名（同时注册类映射）
 * MiddlewareAliasRegistry.getGlobal().register("auth", authMiddleware);
 *
 * // 仅按类注册（无别名）
 * MiddlewareAliasRegistry.getGlobal().register(logMiddleware);
 *
 * // 路由中通过别名引用
 * router.get("/api/users", action).middleware("auth:api", "log");
 *
 * // 路由中通过类对象引用
 * router.get("/log", action).middleware(LogMiddleware.class);
 * router.get("/log", action).middleware(LogMiddleware.class, "debug");
 *
 * // 路由中通过类名引用（语法与别名一致）
 * router.get("/log", action).middleware("LogMiddleware:debug");
 * }</pre>
 */
public class MiddlewareAliasRegistry {

    private static final MiddlewareAliasRegistry GLOBAL = new MiddlewareAliasRegistry();

    /** 别名 → Middleware 映射 */
    private final Map<String, Middleware> aliases = new ConcurrentHashMap<>();
    /** Class → Middleware 映射（所有注册的中间件都写入） */
    private final Map<Class<?>, Middleware> classMap = new ConcurrentHashMap<>();

    /**
     * 获取全局静态实例。
     *
     * @return 全局注册表实例
     */
    public static MiddlewareAliasRegistry getGlobal() {
        return GLOBAL;
    }

    /**
     * 注册中间件别名（同时注册类映射）。
     * <p>
     * 如果 alias 为空字符串或 null，则仅注册类映射，不注册别名。
     * 类映射始终写入，确保通过类对象或类名也能找到该中间件。
     *
     * @param alias      别名（如 "auth"、"log"），可为空
     * @param middleware 中间件实例
     */
    public void register(String alias, Middleware middleware) {
        if (alias != null && !alias.isEmpty()) {
            aliases.put(alias, middleware);
        }
        classMap.put(middleware.getClass(), middleware);
    }

    /**
     * 仅按类注册中间件（不注册别名）。
     * <p>
     * 适用于标注了 {@code @MiddlewareAlias} 但未填写别名的中间件，
     * 路由中可通过类对象或类名引用。
     *
     * @param middleware 中间件实例
     */
    public void register(Middleware middleware) {
        classMap.put(middleware.getClass(), middleware);
    }

    /**
     * 解析别名/类名表达式，返回带参数烘焙的 {@link Middleware} 闭包。
     * <p>
     * 表达式语法：
     * <ul>
     *   <li>{@code "auth"} → 名称 "auth"，无参数</li>
     *   <li>{@code "auth:api"} → 名称 "auth"，参数 ["api"]</li>
     *   <li>{@code "auth:api,admin"} → 名称 "auth"，参数 ["api", "admin"]</li>
     * </ul>
     * 解析顺序：先按别名查找，未命中再按类名（简名或全限定名）查找。
     * <p>
     * 返回的 Middleware 闭包会忽略调用时传入的 params，使用解析时烘焙的参数。
     *
     * @param expression 别名/类名表达式
     * @return 带参数的 Middleware 闭包
     * @throws IllegalArgumentException 别名和类名均未注册时抛出
     */
    public Middleware resolve(String expression) {
        String name;
        String[] params;

        int colonIndex = expression.indexOf(':');
        if (colonIndex < 0) {
            name = expression.trim();
            params = new String[0];
        } else {
            name = expression.substring(0, colonIndex).trim();
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

        // 先按别名查找
        Middleware byAlias = aliases.get(name);
        if (byAlias != null) {
            return bakeParams(byAlias, params);
        }

        // 再按类名查找（简名或全限定名）
        Middleware byClass = findByClassName(name);
        if (byClass != null) {
            return bakeParams(byClass, params);
        }

        throw new IllegalArgumentException("未注册的中间件别名或类名: " + name);
    }

    /**
     * 按 Class 对象解析中间件（无参数）。
     *
     * @param clazz 中间件类
     * @return 中间件实例
     * @throws IllegalArgumentException 类未注册时抛出
     */
    public Middleware resolve(Class<?> clazz) {
        Middleware middleware = classMap.get(clazz);
        if (middleware == null) {
            throw new IllegalArgumentException("未注册的中间件类: " + clazz.getName());
        }
        return middleware;
    }

    /**
     * 按 Class 对象解析中间件（带参数）。
     * <p>
     * 返回的 Middleware 闭包会忽略调用时传入的 params，使用此处指定的参数。
     *
     * @param clazz  中间件类
     * @param params 参数
     * @return 带参数的 Middleware 闭包
     * @throws IllegalArgumentException 类未注册时抛出
     */
    public Middleware resolve(Class<?> clazz, String... params) {
        Middleware middleware = classMap.get(clazz);
        if (middleware == null) {
            throw new IllegalArgumentException("未注册的中间件类: " + clazz.getName());
        }
        return bakeParams(middleware, params);
    }

    /**
     * 批量解析别名/类名表达式列表，按顺序返回 Middleware 列表。
     *
     * @param expressions 别名/类名表达式列表（如 ["auth:api", "log"]）
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
        return alias != null && aliases.containsKey(alias);
    }

    /**
     * 检查类是否已注册。
     *
     * @param clazz 中间件类
     * @return 已注册返回 true
     */
    public boolean isClassRegistered(Class<?> clazz) {
        return classMap.containsKey(clazz);
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
     * 获取所有已注册的中间件类（不可修改视图）。
     *
     * @return 类集合
     */
    public Set<Class<?>> getRegisteredClasses() {
        return Collections.unmodifiableSet(classMap.keySet());
    }

    /**
     * 清除所有已注册的别名和类映射（主要用于测试）。
     */
    public void clear() {
        aliases.clear();
        classMap.clear();
    }

    // ========== 静态便捷方法（委托给全局实例） ==========

    /**
     * 向全局注册表注册中间件别名。
     */
    public static void registerGlobal(String alias, Middleware middleware) {
        GLOBAL.register(alias, middleware);
    }

    /**
     * 通过全局注册表解析别名/类名表达式。
     */
    public static Middleware resolveGlobal(String expression) {
        return GLOBAL.resolve(expression);
    }

    // ========== 内部方法 ==========

    /**
     * 通过类名（简名或全限定名）在 classMap 中查找中间件。
     */
    private Middleware findByClassName(String className) {
        for (Map.Entry<Class<?>, Middleware> entry : classMap.entrySet()) {
            Class<?> clazz = entry.getKey();
            if (clazz.getSimpleName().equals(className) || clazz.getName().equals(className)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 将参数烘焙到 Middleware 闭包中，返回的闭包忽略调用时传入的 params。
     */
    private Middleware bakeParams(Middleware original, String[] params) {
        final String[] bakedParams = params;
        return (request, next, ignored) -> original.handle(request, next, bakedParams);
    }
}
