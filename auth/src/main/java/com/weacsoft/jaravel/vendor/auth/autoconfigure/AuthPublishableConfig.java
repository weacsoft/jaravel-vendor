package com.weacsoft.jaravel.vendor.auth.autoconfigure;

import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;

/**
 * auth 模块的可发布配置类模板，由 {@code artisan vendor:publish --tag=auth} 发布。
 * <p>
 * 发布后在业务工程生成 {@code config/AuthConfig.java}，
 * 内含 {@code @RegisterGuard} / {@code @RegisterProvider} / {@code @RegisterSessionStore}
 * 示例方法，对齐 Laravel {@code config/auth.php}。
 */
public class AuthPublishableConfig implements PublishableConfig {

    @Override
    public String tag() {
        return "auth";
    }

    @Override
    public String className() {
        return "AuthConfig";
    }

    @Override
    public String description() {
        return "守卫 / 用户提供者 / Session 存储注册";
    }

    @Override
    public String source(String basePackage) {
        return "package " + basePackage + ".config;\n"
                + "\n"
                + "import com.weacsoft.jaravel.vendor.auth.RegisterGuard;\n"
                + "import com.weacsoft.jaravel.vendor.auth.RegisterProvider;\n"
                + "import com.weacsoft.jaravel.vendor.auth.RegisterSessionStore;\n"
                + "import com.weacsoft.jaravel.vendor.auth.contract.GuardDefinition;\n"
                + "import com.weacsoft.jaravel.vendor.auth.contract.SessionStore;\n"
                + "import com.weacsoft.jaravel.vendor.auth.session.CookieSessionStore;\n"
                + "import org.springframework.context.annotation.Configuration;\n"
                + "\n"
                + "/**\n"
                + " * 认证配置，对齐 Laravel config/auth.php。\n"
                + " * <p>\n"
                + " * 由 {@code artisan vendor:publish --tag=auth} 发布生成，可自由修改。\n"
                + " *\n"
                + " * <h3>说明</h3>\n"
                + " * <ul>\n"
                + " *   <li>{@code @RegisterGuard} / {@code @RegisterProvider} 的产物<b>不会</b>\n"
                + " *       成为 Spring Bean，因此守卫名可以叫 {@code admin} 而不与同名 bean 冲突。</li>\n"
                + " *   <li>{@code @RegisterSessionStore} <b>全局只允许一个</b>；\n"
                + " *       如需覆盖框架默认，请使用 {@code @RegisterSessionStore(override = true)}。</li>\n"
                + " *   <li>删除本文件即可回退到框架默认（session 守卫 + CookieSessionStore）。</li>\n"
                + " * </ul>\n"
                + " */\n"
                + "@Configuration\n"
                + "public class AuthConfig {\n"
                + "\n"
                + "    /**\n"
                + "     * 默认 web 守卫，使用 session 驱动 + users 提供者。\n"
                + "     */\n"
                + "    @RegisterGuard(value = \"web\", defaultGuard = true)\n"
                + "    public GuardDefinition webGuard() {\n"
                + "        return GuardDefinition.of(\"session\", \"users\");\n"
                + "    }\n"
                + "\n"
                + "    // JWT 守卫：需引入 jaravel-jwt 模块（其提供 JwtGuardDriver）\n"
                + "    // @RegisterGuard(\"api\")\n"
                + "    // public GuardDefinition apiGuard() {\n"
                + "    //     return GuardDefinition.of(\"jwt\", \"users\");\n"
                + "    // }\n"
                + "\n"
                + "    // 自定义用户提供者：返回 UserProvider 实现\n"
                + "    // @RegisterProvider(\"users\")\n"
                + "    // public UserProvider usersProvider(UserRepository repository) {\n"
                + "    //     return new MyUserProvider(repository);\n"
                + "    // }\n"
                + "\n"
                + "    /**\n"
                + "     * Session 存储（全局唯一）。\n"
                + "     * <p>\n"
                + "     * 默认基于 Servlet HttpSession；若引入 jaravel-session-redis\n"
                + "     * 并希望使用 Redis，请改为返回 RedisSessionStore。\n"
                + "     */\n"
                + "    @RegisterSessionStore\n"
                + "    public SessionStore sessionStore() {\n"
                + "        return new CookieSessionStore();\n"
                + "    }\n"
                + "}\n";
    }
}
