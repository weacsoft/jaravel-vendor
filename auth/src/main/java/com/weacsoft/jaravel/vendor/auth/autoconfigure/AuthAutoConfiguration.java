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

    @Bean
    @ConditionalOnMissingBean
    public AuthManager authManager() {
        AuthManager manager = new AuthManager();
        manager.setDefaultGuard(properties.getDefaultGuard());

        // 配置式守卫注册
        if (properties.getGuards() != null) {
            properties.getGuards().forEach((name, cfg) -> {
                manager.registerGuard(name, cfg.getDriver(), cfg.getProvider(),
                        cfg.getSessionStore());
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
     * 注册为 {@link SessionStore} 类型，使 {@link SessionGuardDriver} 能自动发现。
     * 业务方可通过覆盖此 Bean 使用自定义存储。
     */
    @Bean
    @ConditionalOnMissingBean(CookieSessionStore.class)
    public SessionStore cookieSessionStore() {
        return new CookieSessionStore();
    }

    /**
     * Session 守卫驱动（工厂模式）。
     * <p>
     * 实现 {@link AuthGuardDriver}，支持 "session" 驱动。
     * 注入所有 {@link SessionStore} Bean，在创建 SessionGuard 时按配置的 store 名称匹配。
     */
    @Bean
    @ConditionalOnMissingBean
    public SessionGuardDriver sessionGuardDriver(List<SessionStore> sessionStores) {
        return new SessionGuardDriver(sessionStores);
    }

    /**
     * 所有单例 Bean 就绪后，自动将所有 {@link AuthGuardDriver} 注册到 {@link AuthManager}。
     * <p>
     * 这样各模块（auth 的 SessionGuardDriver、jwt 的 JwtGuardDriver 等）只需实现接口并注册为 Bean，
     * 无需手动调用 {@link AuthManager#registerGuardDriver}。
     */
    @Override
    public void afterSingletonsInstantiated() {
        AuthManager manager = authManager();
        for (AuthGuardDriver driver : guardDrivers) {
            manager.registerGuardDriver(driver);
        }
    }
}
