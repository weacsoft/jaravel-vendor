package com.weacsoft.jaravel.vendor.route;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 静态路由门面，对齐 Laravel {@code Route::get()} / {@code Route::group()} 静态调用风格。
 * <p>
 * 使用 ThreadLocal 自动追踪当前 {@link Router} 上下文，实现：
 * <ul>
 *   <li>静态方法注册路由（{@code Route.get()}、{@code Route.post()} 等），无需传递 Router 实例</li>
 *   <li>无参闭包创建路由组（{@code Route.group(params, () -> { ... })}），自动计算层级</li>
 *   <li>流式分组构建器（{@code Route.middleware("auth").prefix("api").group(() -> { ... })}）</li>
 * </ul>
 *
 * <h3>初始化</h3>
 * 在 RouteServiceProvider 中调用 {@link #setRootRouter(Router)} 设置根 Router：
 * <pre>
 * Router baseRouter = new Router();
 * Route.setRootRouter(baseRouter);
 * // 之后即可使用 Route.get()、Route.group() 等静态方法
 * </pre>
 *
 * <h3>用法一：Map 参数式分组（对齐 Laravel Route::group(['prefix' => 'admin'], ...))</h3>
 * <pre>
 * Route.group(Map.of(
 *     Route.Group.PREFIX, "admin",
 *     Route.Group.NAMESPACE, "Admin"
 * ), () -> {
 *     Route.get("login", "LoginController::loginIndex").name("admin.login.index");
 *     Route.post("login", "LoginController::login").name("admin.login");
 *
 *     Route.group(Map.of(
 *         Route.Group.MIDDLEWARE, new String[]{"auth:admin", "permission:admin"}
 *     ), () -> {
 *         Route.get("home", "HomeController::index").name("admin.home");
 *         Route.get("logout", "LoginController::logout").name("admin.logout");
 *     });
 * });
 * </pre>
 *
 * <h3>用法二：流式构建器（对齐 Laravel Route::middleware('api')->prefix('api')->group(...))</h3>
 * <pre>
 * // 闭包形式
 * Route.middleware("auth:admin", "permission:admin").prefix("admin").group(() -> {
 *     Route.get("/home", "HomeController::index").name("admin.home");
 *     Route.get("/logout", "LoginController::logout").name("admin.logout");
 * });
 *
 * // 方法引用形式（对齐 Laravel ->group(base_path('routes/api.php'))）
 * Route.middleware("api").prefix("api").namespace("com.example.controller").group(Api::register);
 * </pre>
 *
 * <h3>用法三：静态 import（最简洁）</h3>
 * <pre>
 * import static com.weacsoft.jaravel.vendor.route.Route.*;
 *
 * group(Map.of(Route.Group.PREFIX, "api"), () -> {
 *     get("/users", "UserController::list").name("users.index");
 *     post("/users", "UserController::create").name("users.create");
 * });
 * </pre>
 *
 * <h3>与 Router API 的关系</h3>
 * {@code Route} 是 {@link Router} 的静态门面封装，两者可以混用：
 * <ul>
 *   <li>{@code router.get(uri, action)} — 实例 API，需要传递 Router 实例</li>
 *   <li>{@code Route.get(uri, action)} — 静态 API，通过 ThreadLocal 自动定位当前 Router</li>
 * </ul>
 * 静态门面在路由组闭包内自动切换上下文，嵌套分组时无需手动传递 Router。
 *
 * @see RouteDefinition
 * @see Router
 * @see Route.Group
 */
public final class Route {

    private Route() {
    }

    /**
     * 路由分组参数键，对齐 Laravel {@code Route::group(['prefix' => ..., 'middleware' => ...], ...)}。
     */
    public enum Group {
        NAMESPACE,
        PREFIX,
        NAME,
        MIDDLEWARE
    }

    /**
     * ThreadLocal 路由器上下文栈，用于静态门面方法追踪当前 Router。
     * <p>
     * 栈底为根 Router（通过 {@link #setRootRouter} 设置），每次 {@link #group} 压入子 Router，
     * 回调结束后弹出，实现自动层级计算。
     */
    private static final ThreadLocal<Deque<Router>> ROUTER_STACK = ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * 初始化静态门面，设置根 Router。
     * <p>
     * 在 RouteServiceProvider 中调用：
     * <pre>
     * Router baseRouter = new Router();
     * Route.setRootRouter(baseRouter);
     * </pre>
     *
     * @param router 根路由器
     */
    public static void setRootRouter(Router router) {
        ROUTER_STACK.get().clear();
        ROUTER_STACK.get().push(router);
    }

    /**
     * 清理 ThreadLocal，防止线程池复用时上下文泄漏。
     */
    public static void clearContext() {
        ROUTER_STACK.remove();
    }

    /**
     * 获取当前上下文的 Router（栈顶）。
     *
     * @return 当前 Router
     * @throws IllegalStateException 如果未调用 {@link #setRootRouter} 初始化
     */
    public static Router currentRouter() {
        Deque<Router> stack = ROUTER_STACK.get();
        if (stack.isEmpty()) {
            throw new IllegalStateException(
                    "Route 静态门面未初始化，请先调用 Route.setRootRouter(router)");
        }
        return stack.peek();
    }

    private static void pushRouter(Router router) {
        ROUTER_STACK.get().push(router);
    }

    private static void popRouter() {
        ROUTER_STACK.get().pop();
    }

    // ===== 静态路由注册方法（委托给 currentRouter()） =====

    /**
     * 静态注册 GET 路由（对齐 Laravel {@code Route::get('/users', 'UserController@index')}）。
     *
     * @param uri              URI
     * @param controllerAction 控制器引用（如 {@code "UserController::list"}）
     * @return 路由实例，可链式调用 {@code .name()} / {@code .middleware()}
     */
    public static RouteDefinition get(String uri, String controllerAction) {
        return currentRouter().get(uri, controllerAction);
    }

    /**
     * 静态注册 POST 路由。
     *
     * @param uri              URI
     * @param controllerAction 控制器引用
     * @return 路由实例
     */
    public static RouteDefinition post(String uri, String controllerAction) {
        return currentRouter().post(uri, controllerAction);
    }

    /**
     * 静态注册 PUT 路由。
     *
     * @param uri              URI
     * @param controllerAction 控制器引用
     * @return 路由实例
     */
    public static RouteDefinition put(String uri, String controllerAction) {
        return currentRouter().put(uri, controllerAction);
    }

    /**
     * 静态注册 DELETE 路由。
     *
     * @param uri              URI
     * @param controllerAction 控制器引用
     * @return 路由实例
     */
    public static RouteDefinition delete(String uri, String controllerAction) {
        return currentRouter().delete(uri, controllerAction);
    }

    /**
     * 静态注册 PATCH 路由。
     *
     * @param uri              URI
     * @param controllerAction 控制器引用
     * @return 路由实例
     */
    public static RouteDefinition patch(String uri, String controllerAction) {
        return currentRouter().patch(uri, controllerAction);
    }

    /**
     * 静态注册多方法路由（GET/POST/PUT/DELETE/PATCH）。
     *
     * @param uri              URI
     * @param controllerAction 控制器引用
     * @return 路由组实例
     */
    public static Router all(String uri, String controllerAction) {
        return currentRouter().all(uri, controllerAction);
    }

    // ===== 静态 group 方法（Runnable 回调，无需传 Router 参数） =====

    /**
     * 静态创建路由组（对齐 Laravel {@code Route::group(['prefix' => 'admin'], function () { ... })}）。
     * <p>
     * 使用 ThreadLocal 自动追踪 Router 层级，回调内可直接使用 {@code Route.get()} 等静态方法，
     * 无需接收 Router 参数。
     * <p>
     * 支持 {@link Group#MIDDLEWARE} 参数，值可以是：
     * <ul>
     *   <li>{@code String} — 单个别名（如 {@code "auth:api"}）</li>
     *   <li>{@code String[]} — 多个别名（如 {@code new String[]{"auth:admin", "permission:admin"}}）</li>
     *   <li>{@code List<String>} — 别名列表</li>
     * </ul>
     *
     * <h3>示例</h3>
     * <pre>
     * Route.group(Map.of(
     *     Route.Group.PREFIX, "admin",
     *     Route.Group.NAMESPACE, "Admin"
     * ), () -> {
     *     Route.get("login", "LoginController::loginIndex").name("admin.login.index");
     *
     *     Route.group(Map.of(
     *         Route.Group.MIDDLEWARE, new String[]{"auth:admin", "permission:admin"}
     *     ), () -> {
     *         Route.get("home", "HomeController::index").name("admin.home");
     *     });
     * });
     * </pre>
     *
     * @param params   分组参数（prefix / namespace / name / middleware）
     * @param callback 无参回调（内部使用 Route.get() 等静态方法注册路由）
     */
    public static void group(Map<Group, ?> params, Runnable callback) {
        Router parent = currentRouter();
        Router groupRouter = new Router();
        groupRouter.setParentRouter(parent);

        params.forEach((key, value) -> {
            if (key.equals(Group.NAMESPACE)) {
                groupRouter.setNamespace((String) value);
            } else if (key.equals(Group.PREFIX)) {
                groupRouter.setPrefix((String) value);
            } else if (key.equals(Group.NAME)) {
                groupRouter.setName((String) value);
            } else if (key.equals(Group.MIDDLEWARE)) {
                applyGroupMiddleware(groupRouter, value);
            }
        });

        pushRouter(groupRouter);
        try {
            callback.run();
        } finally {
            popRouter();
        }

        parent.addGroupRouter(groupRouter);
    }

    /**
     * 将中间件参数应用到分组 Router。
     * <p>
     * 支持的值类型：
     * <ul>
     *   <li>{@code String} — 单个别名表达式</li>
     *   <li>{@code String[]} — 多个别名表达式</li>
     *   <li>{@code List<String>} — 别名列表</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private static void applyGroupMiddleware(Router groupRouter, Object value) {
        if (value instanceof String) {
            groupRouter.middleware((String) value);
        } else if (value instanceof String[]) {
            groupRouter.middleware((String[]) value);
        } else if (value instanceof List) {
            List<String> list = (List<String>) value;
            groupRouter.middleware(list.toArray(new String[0]));
        }
    }

    // ===== 流式 GroupBuilder（对齐 Laravel Route::middleware('api')->prefix('api')->group(...)） =====

    /**
     * 创建流式分组构建器，设置中间件别名。
     * <p>
     * 对齐 Laravel：
     * <pre>
     * Route::middleware('api')->prefix('api')->group(function () { ... });
     * </pre>
     * Java 等价：
     * <pre>
     * Route.middleware("api").prefix("api").group(() -> {
     *     Route.get("/users", "UserController::list");
     * });
     * </pre>
     *
     * @param aliases 中间件别名表达式
     * @return 分组构建器
     */
    public static GroupBuilder middleware(String... aliases) {
        return new GroupBuilder().middleware(aliases);
    }

    /**
     * 创建流式分组构建器，设置前缀。
     *
     * @param prefix URI 前缀
     * @return 分组构建器
     */
    public static GroupBuilder prefix(String prefix) {
        return new GroupBuilder().prefix(prefix);
    }

    /**
     * 创建流式分组构建器，设置命名空间。
     *
     * @param namespace 命名空间
     * @return 分组构建器
     */
    public static GroupBuilder namespace(String namespace) {
        return new GroupBuilder().namespace(namespace);
    }

    /**
     * 创建流式分组构建器，设置名称前缀。
     *
     * @param name 名称前缀
     * @return 分组构建器
     */
    public static GroupBuilder name(String name) {
        return new GroupBuilder().name(name);
    }

    /**
     * 流式分组构建器，对齐 Laravel {@code Route::middleware('api')->prefix('api')->group(...)}。
     * <p>
     * 累积 prefix / namespace / name / middleware 属性，调用 {@link #group(Runnable)} 时创建子路由组。
     * <p>
     * <h3>示例</h3>
     * <pre>
     * // 方法引用形式（对齐 Laravel ->group(base_path('routes/api.php'))）
     * Route.middleware("api").prefix("api").namespace("com.example.controller").group(Api::register);
     *
     * // 闭包形式
     * Route.middleware("auth:admin", "permission:admin").prefix("admin").group(() -> {
     *     Route.get("/home", "HomeController::index").name("admin.home");
     *     Route.get("/logout", "LoginController::logout").name("admin.logout");
     * });
     * </pre>
     */
    public static class GroupBuilder {
        private String prefix = "";
        private String namespace = "";
        private String name = "";
        private final List<String> middlewareAliases = new ArrayList<>();

        /**
         * 设置 URI 前缀。
         *
         * @param prefix URI 前缀
         * @return this
         */
        public GroupBuilder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        /**
         * 设置命名空间。
         *
         * @param namespace 命名空间
         * @return this
         */
        public GroupBuilder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        /**
         * 设置名称前缀。
         *
         * @param name 名称前缀
         * @return this
         */
        public GroupBuilder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * 添加中间件别名。
         *
         * @param aliases 中间件别名表达式（如 {@code "auth:api"}）
         * @return this
         */
        public GroupBuilder middleware(String... aliases) {
            this.middlewareAliases.addAll(Arrays.asList(aliases));
            return this;
        }

        /**
         * 创建路由组并执行回调。
         * <p>
         * 将累积的属性转换为 {@link Group} 参数 Map，委托给 {@link Route#group(Map, Runnable)}。
         *
         * @param callback 无参回调（内部使用 Route.get() 等静态方法注册路由）
         */
        public void group(Runnable callback) {
            Map<Group, Object> params = new HashMap<>();
            if (!prefix.isEmpty()) {
                params.put(Group.PREFIX, prefix);
            }
            if (!namespace.isEmpty()) {
                params.put(Group.NAMESPACE, namespace);
            }
            if (!name.isEmpty()) {
                params.put(Group.NAME, name);
            }
            if (!middlewareAliases.isEmpty()) {
                params.put(Group.MIDDLEWARE, middlewareAliases.toArray(new String[0]));
            }
            Route.group(params, callback);
        }
    }
}
