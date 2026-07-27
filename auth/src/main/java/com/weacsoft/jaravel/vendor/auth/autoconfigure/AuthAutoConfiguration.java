package com.weacsoft.jaravel.vendor.auth.autoconfigure;

import com.weacsoft.jaravel.vendor.auth.AuthManager;
import com.weacsoft.jaravel.vendor.auth.contract.AuthGuardDriver;
import com.weacsoft.jaravel.vendor.auth.contract.SessionStore;
import com.weacsoft.jaravel.vendor.auth.filter.AuthLifecycleFilter;
import com.weacsoft.jaravel.vendor.auth.guard.SessionGuardDriver;
import com.weacsoft.jaravel.vendor.auth.session.CookieSessionStore;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * 认证自动装配：注册 AuthManager、生命周期过滤器、内置 Session 存储和守卫驱动，并自动收集所有 {@link AuthGuardDriver} Bean。
 * <p>
 * <b>工厂模式</b>：所有 {@link AuthGuardDriver} 实现（如 SessionGuardDriver、JwtGuardDriver）注册为 Spring Bean 后，
 * 本配置类在所有单例就绪后自动将它们注册到 {@link AuthManager}，无需各模块手动调用注册方法。
 * <p>
 * <b>Session 存储是全局配置</b>：{@link SessionStore} 作为全局唯一的 Bean 注入到 {@link SessionGuardDriver}。
 * 如果应用未注册任何 {@code SessionStore} Bean，本配置类默认提供 {@link CookieSessionStore}（Servlet HttpSession）。
 * 应用可在 {@code config/SessionConfig.java} 中注册自定义 {@code SessionStore} Bean 来覆盖默认实现（如 Redis）。
 * <p>
 * <b>配置式守卫注册</b>：支持通过 {@code jaravel.auth.guards} 配置注册守卫，
 * 也支持通过 {@code AuthServiceProvider} 编程式注册。两者可共存。
 * <p>
 * 认证中间件 {@code Authenticate} 为普通 {@code Middleware} 实现，
 * 可直接传入 {@code Router.middleware()} 使用，无需别名注册。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(AuthManager.class)
@EnableConfigurationProperties(AuthProperties.class)
public class AuthAutoConfiguration implements SmartInitializingSingleton {

    @Autowired
    private AuthProperties properties;

    /** 所有已注册的守卫驱动（Spring 自动注入，含 auth 模块的 SessionGuardDriver、jwt 模块的 JwtGuardDriver 等） */
    @Autowired
    private List<AuthGuardDriver> guardDrivers;

    /**
     * 容器中的 AuthManager Bean。
     * <p>
     * 不能直接调用 {@link #authManager()} 方法获取实例：因为 {@code @AutoConfiguration}
     * 等价于 {@code @Configuration(proxyBeanMethods = false)}，不会生成 CGLIB 代理，
     * 直接调用 {@code @Bean} 方法会 new 出一个脱离容器管理的临时对象，导致注册的守卫驱动丢失。
     * 必须通过依赖注入获取容器中实际管理的单例 Bean。
     */
    @Autowired
    private AuthManager authManager;

    @Bean
    @ConditionalOnMissingBean
    public AuthManager authManager() {
        AuthManager manager = new AuthManager();
        manager.setDefaultGuard(properties.getDefaultGuard());

        // 配置式守卫注册
        if (properties.getGuards() != null) {
            properties.getGuards().forEach((name, cfg) -> {
                manager.registerGuard(name, cfg.getDriver(), cfg.getProvider());
            });
        }

        return manager;
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthLifecycleFilter authLifecycleFilter(AuthManager authManager) {
        return new AuthLifecycleFilter(authManager);
    }

    /**
     * 默认 Cookie Session 存储（使用 Servlet HttpSession）。
     * <p>
     * 当应用未注册任何 {@link SessionStore} Bean 时，提供默认实现。
     * 业务方可通过在 {@code config/SessionConfig.java} 中注册 {@code SessionStore} Bean 覆盖此默认实现。
     */
    @Bean
    @ConditionalOnMissingBean(SessionStore.class)
    public SessionStore cookieSessionStore() {
        return new CookieSessionStore();
    }

    /**
     * Session 守卫驱动（工厂模式）。
     * <p>
     * 实现 {@link AuthGuardDriver}，支持 "session" 驱动。
     * 注入全局唯一的 {@link SessionStore} Bean，由 {@code SessionConfig} 决定具体实现。
     */
    @Bean
    @ConditionalOnMissingBean
    public SessionGuardDriver sessionGuardDriver(SessionStore sessionStore) {
        return new SessionGuardDriver(sessionStore);
    }

    /**
     * 所有单例 Bean 就绪后，自动将所有 {@link AuthGuardDriver} 注册到 {@link AuthManager}。
     * <p>
     * 这样各模块（auth 的 SessionGuardDriver、jwt 的 JwtGuardDriver 等）只需实现接口并注册为 Bean，
     * 无需手动调用 {@link AuthManager#registerGuardDriver}。
     */
    @Override
    public void afterSingletonsInstantiated() {
        // 使用容器中注入的 AuthManager 单例，而非直接调用 authManager() 方法
        // （@AutoConfiguration 的 proxyBeanMethods=false 不会拦截方法调用）
        for (AuthGuardDriver driver : guardDrivers) {
            authManager.registerGuardDriver(driver);
        }
    }
}
