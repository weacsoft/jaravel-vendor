package com.weacsoft.jaravel.vendor.core;

/**
 * 应用静态入口，对齐 Laravel 的 {@code app()} 全局函数。
 * <p>
 * 通过 {@code App.app()} 获取 {@link Application} 实例，再链式访问各类服务，
 * 替代 Facade 静态代理模式：
 * <pre>
 * // 通用方式（任何模块均可使用）
 * AuthManager auth = App.app().make(AuthManager.class);
 * CacheManager cache = App.app().make(CacheManager.class);
 *
 * // 自定义注册的服务
 * App.app().singleton("myService", () -> new MyService());
 * MyService svc = App.app().make("myService");
 * </pre>
 *
 * <h3>typed 访问器</h3>
 * 应用配置类继承 {@link Application} 后可添加 typed 访问器方法。
 * 在应用代码中可直接强转为具体子类使用：
 * <pre>
 * // AppConfig extends Application，提供 auth()、cache() 等 typed 方法
 * AppConfig app = (AppConfig) App.app();
 * app.auth().check();
 * app.cache().get("key");
 * </pre>
 * 也可在应用模块中自定义 {@code App} 类返回具体子类，免去强转。
 */
public final class App {

    private App() {
    }

    /**
     * 获取应用容器实例。
     * <p>
     * 返回 Spring 容器中唯一的 {@link Application} Bean
     * （通常是应用模块中继承 {@link Application} 的 {@code @Configuration} 类）。
     *
     * @return 应用容器实例
     */
    public static Application app() {
        return SpringContext.bean(Application.class);
    }
}
