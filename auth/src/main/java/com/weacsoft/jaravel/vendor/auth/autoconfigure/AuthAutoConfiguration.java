package com.weacsoft.jaravel.vendor.auth.autoconfigure;

import com.weacsoft.jaravel.vendor.auth.AuthManager;
import com.weacsoft.jaravel.vendor.auth.contract.AuthGuardDriver;
import com.weacsoft.jaravel.vendor.auth.contract.GuardDefinition;
import com.weacsoft.jaravel.vendor.auth.contract.SessionStore;
import com.weacsoft.jaravel.vendor.auth.contract.UserProvider;
import com.weacsoft.jaravel.vendor.auth.contract.UserProviderDriver;
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
import java.util.Map;

/**
 * 认证自动装配：注册 AuthManager、生命周期过滤器、内置 Session 存储和守卫驱动，并自动收集所有驱动 Bean。
 * <p>
 * <b>双层工厂模式</b>：
 * <ul>
 *   <li>{@link AuthGuardDriver} — 守卫驱动（session/jwt/...），创建守卫实例</li>
 *   <li>{@link UserProviderDriver} — 提供者驱动（eloquent/...），创建提供者实例</li>
 * </ul>
 * 两者均由 Spring 自动收集，第三方模块只需实现接口并注册为 Bean。
 *
 * <h3>三种注册方式（可共存，编程式优先）</h3>
 * <ol>
 *   <li><b>编程式 @Bean</b>：{@code @Bean("users")} 声明 UserProvider，
 *       {@code @Bean("web")} 声明 GuardDefinition。通过 {@code Map<String, ?>} 自动收集</li>
 *   <li><b>配置式</b>：{@code jaravel.auth.providers} 和 {@code jaravel.auth.guards} 配置，
 *       由工厂驱动按配置创建</li>
 *   <li><b>手动调用</b>：直接调用 {@link AuthManager#registerProvider} / {@link AuthManager#registerGuard}</li>
 * </ol>
 * 编程式 @Bean 优先于配置式（同名时覆盖）。
 *
 * <h3>Session 存储是全局配置</h3>
 * {@link SessionStore} 作为全局唯一的 Bean 注入到 {@link SessionGuardDriver}。
 * 如果应用未注册任何 {@code SessionStore} Bean，本配置类默认提供 {@link CookieSessionStore}。
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

    /** 所有已注册的提供者驱动（Spring 自动注入，含 database 模块的 EloquentUserProviderDriver 等） */
    @Autowired
    private List<UserProviderDriver> providerDrivers;

    /** 编程式注册的 UserProvider（bean name 即 provider name） */
    @Autowired(required = false)
    private Map<String, UserProvider> userProviders;

    /** 编程式注册的 GuardDefinition（bean name 即 guard name） */
    @Autowired(required = false)
    private Map<String, GuardDefinition> guardDefinitions;

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
     * 所有单例 Bean 就绪后，完成认证配置的自动注册。
     * <p>
     * 注册顺序（编程式优先于配置式）：
     * <ol>
     *   <li>注册提供者驱动（{@link UserProviderDriver}）</li>
     *   <li>注册配置式提供者（通过工厂驱动创建）</li>
     *   <li>注册编程式提供者（{@code @Bean} 声明，覆盖同名配置式）</li>
     *   <li>注册配置式守卫</li>
     *   <li>注册编程式守卫（{@code @Bean} 声明，覆盖同名配置式）</li>
     *   <li>注册守卫驱动（{@link AuthGuardDriver}）</li>
     * </ol>
     */
    @Override
    public void afterSingletonsInstantiated() {
        // 1. 注册提供者驱动
        for (UserProviderDriver driver : providerDrivers) {
            authManager.registerProviderDriver(driver);
        }

        // 2. 配置式提供者注册（通过工厂驱动创建）
        if (properties.getProviders() != null) {
            properties.getProviders().forEach((name, cfg) -> {
                authManager.registerProvider(name, cfg.getDriver(), cfg.toConfigMap());
            });
        }

        // 3. 编程式提供者注册（@Bean 声明，覆盖同名配置式）
        if (userProviders != null) {
            userProviders.forEach(authManager::registerProvider);
        }

        // 4. 配置式守卫注册
        if (properties.getGuards() != null) {
            properties.getGuards().forEach((name, cfg) -> {
                authManager.registerGuard(name, cfg.getDriver(), cfg.getProvider());
            });
        }

        // 5. 编程式守卫注册（@Bean 声明，覆盖同名配置式）
        if (guardDefinitions != null) {
            guardDefinitions.forEach((name, def) -> {
                authManager.registerGuard(name, def.driver(), def.provider(), def.config());
            });
        }

        // 6. 守卫驱动注册（使用容器中注入的 AuthManager 单例）
        for (AuthGuardDriver driver : guardDrivers) {
            authManager.registerGuardDriver(driver);
        }
    }
}
