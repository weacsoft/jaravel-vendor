package com.weacsoft.jaravel.vendor.core.publish;

/**
 * core 模块的可发布配置类模板，由 {@code artisan vendor:publish --tag=app} 发布。
 * <p>
 * 发布后在业务工程生成 {@code config/AppConfig.java}，对齐 Laravel {@code config/app.php}：
 * <ul>
 *   <li>继承 {@link com.weacsoft.jaravel.vendor.core.Application Application}，
 *       既是 Spring {@code @Configuration}（用 {@code @Import} 控制功能开关），
 *       又是应用容器（提供 typed 服务访问器）；</li>
 *   <li>static 块中集中注册服务别名（对齐 Laravel {@code aliases} 数组），
 *       {@code make("auth")} 即可解析；</li>
 *   <li><b>完整保留</b> {@code auth()} / {@code cache()} / {@code config()} /
 *       {@code event()} / {@code session()} / {@code router()} / {@code route()}
 *       等全部访问器方法。</li>
 * </ul>
 *
 * <h3>为什么 AppConfig 也要能发布</h3>
 * AppConfig 是整个应用的中央配置入口（功能开关 + 服务别名 + 访问器），
 * 业务工程新建后应当第一时间拥有它，否则 {@code AppConfig.app().auth()} 之类的
 * 写法无从谈起。因此把它做成 {@code app} 标签的可发布配置，
 * 执行 {@code artisan vendor:publish} 时随其它配置一并发布。
 */
public class AppPublishableConfig implements PublishableConfig {

    @Override
    public String tag() {
        return "app";
    }

    @Override
    public String className() {
        return "AppConfig";
    }

    @Override
    public String description() {
        return "应用中央配置（功能开关 / 服务别名 / typed 访问器）";
    }

    @Override
    public String source(String basePackage) {
        return "package " + basePackage + ".config;\n"
                + "\n"
                + "import com.weacsoft.jaravel.vendor.auth.AuthManager;\n"
                + "import com.weacsoft.jaravel.vendor.cache.CacheManager;\n"
                + "import com.weacsoft.jaravel.vendor.core.Application;\n"
                + "import com.weacsoft.jaravel.vendor.core.SpringContext;\n"
                + "import com.weacsoft.jaravel.vendor.core.config.ConfigRepository;\n"
                + "import com.weacsoft.jaravel.vendor.event.Dispatcher;\n"
                + "import com.weacsoft.jaravel.vendor.http.session.SessionStore;\n"
                + "import com.weacsoft.jaravel.vendor.route.RouteHelper;\n"
                + "import com.weacsoft.jaravel.vendor.route.Router;\n"
                + "import org.springframework.context.annotation.Configuration;\n"
                + "import org.springframework.context.annotation.Import;\n"
                + "\n"
                + "/**\n"
                + " * 应用中央配置，对齐 Laravel config/app.php。\n"
                + " * <p>\n"
                + " * 由 {@code artisan vendor:publish --tag=app} 发布生成，可自由修改。\n"
                + " * 继承 {@link Application}，既作为 Spring {@code @Configuration} 控制功能开关，\n"
                + " * 又作为应用容器提供 typed 服务访问器。\n"
                + " *\n"
                + " * <h3>功能开关</h3>\n"
                + " * 所有功能的启用/禁用在 {@code @Import} 中显式控制：要启用某个功能就添加对应配置类，\n"
                + " * 要禁用就移除。若某个配置类尚未发布（例如还没执行\n"
                + " * {@code vendor:publish --tag=auth}），请先注释掉对应行以免编译报错。\n"
                + " *\n"
                + " * <h3>免强转访问</h3>\n"
                + " * <pre>\n"
                + " * AppConfig.app().auth().check();\n"
                + " * AppConfig.app().cache().get(\"key\");\n"
                + " * AppConfig.app().config().string(\"app.name\");\n"
                + " * AppConfig.app().event().dispatch(new UserRegistered(1L));\n"
                + " * AppConfig.app().session().get(\"user_id\");\n"
                + " * AppConfig.app().router().getAllRoutes();\n"
                + " * </pre>\n"
                + " *\n"
                + " * <h3>自定义服务注册</h3>\n"
                + " * <pre>\n"
                + " * AppConfig.app().singleton(\"myService\", () -> new MyService());\n"
                + " * MyService svc = AppConfig.app().make(\"myService\");\n"
                + " * </pre>\n"
                + " */\n"
                + "@Configuration\n"
                + "@Import({\n"
                + "    DatabaseConfig.class,\n"
                + "    AuthConfig.class\n"
                + "    // 按需启用（需先 vendor:publish 对应 tag 生成配置类）：\n"
                + "    // , ViewConfig.class\n"
                + "    // , SessionConfig.class\n"
                + "    // , CacheConfig.class\n"
                + "    // , StorageConfig.class\n"
                + "})\n"
                + "public class AppConfig extends Application {\n"
                + "\n"
                + "    // ==================== 自动注册（对齐 Laravel aliases 数组） ====================\n"
                + "\n"
                + "    static {\n"
                + "        // 常用服务自动注册，make(\"auth\") 即可解析\n"
                + "        registerDefaultBinding(\"auth\", AuthManager.class);\n"
                + "        registerDefaultBinding(\"cache\", CacheManager.class);\n"
                + "        registerDefaultBinding(\"config\", ConfigRepository.class);\n"
                + "        registerDefaultBinding(\"event\", Dispatcher.class);\n"
                + "        registerDefaultBinding(\"session\", SessionStore.class);\n"
                + "        registerDefaultBinding(\"router\", Router.class);\n"
                + "    }\n"
                + "\n"
                + "    // ==================== 免强转静态入口 ====================\n"
                + "\n"
                + "    /**\n"
                + "     * 获取应用容器实例（返回具体类型，免强转）。\n"
                + "     * <p>\n"
                + "     * 对齐 Laravel {@code app()}，但返回 {@code AppConfig} 而非基类，\n"
                + "     * 因此可以直接链式调用 typed 访问器。\n"
                + "     *\n"
                + "     * @return AppConfig 实例\n"
                + "     */\n"
                + "    public static AppConfig app() {\n"
                + "        return SpringContext.bean(AppConfig.class);\n"
                + "    }\n"
                + "\n"
                + "    // ==================== typed 服务访问器 ====================\n"
                + "\n"
                + "    /**\n"
                + "     * 获取认证管理器（对齐 Laravel {@code app('auth')}）。\n"
                + "     */\n"
                + "    public AuthManager auth() {\n"
                + "        return make(AuthManager.class);\n"
                + "    }\n"
                + "\n"
                + "    /**\n"
                + "     * 获取缓存管理器（对齐 Laravel {@code app('cache')}）。\n"
                + "     */\n"
                + "    public CacheManager cache() {\n"
                + "        return make(CacheManager.class);\n"
                + "    }\n"
                + "\n"
                + "    /**\n"
                + "     * 获取配置仓库（对齐 Laravel {@code app('config')}）。\n"
                + "     */\n"
                + "    public ConfigRepository config() {\n"
                + "        return make(ConfigRepository.class);\n"
                + "    }\n"
                + "\n"
                + "    /**\n"
                + "     * 获取事件分发器（对齐 Laravel {@code app('events')}）。\n"
                + "     */\n"
                + "    public Dispatcher event() {\n"
                + "        return make(Dispatcher.class);\n"
                + "    }\n"
                + "\n"
                + "    /**\n"
                + "     * 获取 Session 存储器（对齐 Laravel {@code app('session')}）。\n"
                + "     */\n"
                + "    public SessionStore session() {\n"
                + "        return make(SessionStore.class);\n"
                + "    }\n"
                + "\n"
                + "    /**\n"
                + "     * 获取路由器（对齐 Laravel {@code app('router')}）。\n"
                + "     */\n"
                + "    public Router router() {\n"
                + "        return make(Router.class);\n"
                + "    }\n"
                + "\n"
                + "    /**\n"
                + "     * 路由辅助门面（对齐 Laravel 全局辅助函数 {@code route()} / {@code url()}）。\n"
                + "     * <pre>\n"
                + "     * // route(别名) —— 按路由别名解析 URL\n"
                + "     * String url = AppConfig.app().route().route(\"admin.login\");\n"
                + "     * // url(路径) —— 单纯生成 URL，不校验是否存在\n"
                + "     * String url2 = AppConfig.app().route().url(\"admin/login\");\n"
                + "     * </pre>\n"
                + "     */\n"
                + "    public RouteHelper route() {\n"
                + "        return RouteHelper.instance();\n"
                + "    }\n"
                + "}\n";
    }
}
