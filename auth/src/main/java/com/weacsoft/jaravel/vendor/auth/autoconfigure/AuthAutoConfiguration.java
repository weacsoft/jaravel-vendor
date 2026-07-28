package com.weacsoft.jaravel.vendor.auth.autoconfigure;

import com.weacsoft.jaravel.vendor.auth.AuthManager;
import com.weacsoft.jaravel.vendor.auth.contract.AuthGuardDriver;
import com.weacsoft.jaravel.vendor.auth.contract.SessionStore;
import com.weacsoft.jaravel.vendor.auth.contract.UserProviderDriver;
import com.weacsoft.jaravel.vendor.auth.filter.AuthLifecycleFilter;
import com.weacsoft.jaravel.vendor.auth.guard.SessionGuardDriver;
import com.weacsoft.jaravel.vendor.auth.session.CookieSessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * 认证自动装配：注册 AuthManager、生命周期过滤器、内置 Session 存储和守卫驱动。
 * <p>
 * <b>双层工厂模式</b>：
 * <ul>
 *   <li>{@link AuthGuardDriver} — 守卫驱动（session/jwt/...），创建守卫实例</li>
 *   <li>{@link UserProviderDriver} — 提供者驱动（eloquent/...），创建提供者实例</li>
 * </ul>
 * 两者均由 Spring 自动收集，第三方模块只需实现接口并注册为 Bean。
 *
 * <h3>三种注册方式（可共存，注解声明优先）</h3>
 * <ol>
 *   <li><b>注解声明式</b>（推荐）：在 Config 类中用
 *       {@link com.weacsoft.jaravel.vendor.auth.RegisterGuard @RegisterGuard} 和
 *       {@link com.weacsoft.jaravel.vendor.auth.RegisterProvider @RegisterProvider}
 *       注解方法。不注册为 Spring Bean，避免 bean name 冲突。
 *       可通过 {@code defaultGuard = true} 标记默认守卫</li>
 *   <li><b>配置式</b>：{@code jaravel.auth.providers} 和 {@code jaravel.auth.guards} 配置，
 *       由工厂驱动按配置创建</li>
 *   <li><b>手动调用</b>：直接调用 {@link AuthManager#registerProvider} / {@link AuthManager#registerGuard}</li>
 * </ol>
 * 注解声明优先于配置式（同名时覆盖）。
 *
 * <h3>注册流程</h3>
 * 所有注册逻辑由 {@link AuthRegistrar} 在所有单例 Bean 初始化完成后统一执行：
 * <ol>
 *   <li>注册提供者驱动（{@link UserProviderDriver}）</li>
 *   <li>注册配置式提供者（通过工厂驱动创建）</li>
 *   <li>扫描 {@code @RegisterProvider} 注解方法，注册注解声明式提供者</li>
 *   <li>注册配置式守卫</li>
 *   <li>扫描 {@code @RegisterGuard} 注解方法，注册注解声明式守卫（含默认守卫标记）</li>
 *   <li>注册守卫驱动（{@link AuthGuardDriver}）</li>
 * </ol>
 *
 * <h3>Session 存储是全局配置</h3>
 * {@link SessionStore} 作为全局唯一的 Bean 注入到 {@link SessionGuardDriver}。
 * 如果应用未注册任何 {@code SessionStore} Bean，本配置类默认提供 {@link CookieSessionStore}。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(AuthManager.class)
@EnableConfigurationProperties(AuthProperties.class)
public class AuthAutoConfiguration {

    @Autowired
    private AuthProperties properties;

    /** 所有已注册的守卫驱动（Spring 自动注入，含 auth 模块的 SessionGuardDriver、jwt 模块的 JwtGuardDriver 等） */
    @Autowired
    private List<AuthGuardDriver> guardDrivers;

    /** 所有已注册的提供者驱动（Spring 自动注入，含 database 模块的 EloquentUserProviderDriver 等） */
    @Autowired
    private List<UserProviderDriver> providerDrivers;

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
     * 注册 {@link AuthRegistrar}，负责在所有单例 Bean 初始化完成后统一执行认证配置注册：
     * <ul>
     *   <li>提供者驱动 / 守卫驱动注册</li>
     *   <li>配置式 provider / guard 注册</li>
     *   <li>{@code @RegisterProvider} / {@code @RegisterGuard} 注解扫描注册（含默认守卫标记）</li>
     * </ul>
     */
    @Bean
    @ConditionalOnMissingBean(AuthRegistrar.class)
    public AuthRegistrar authRegistrar(AuthManager authManager) {
        return new AuthRegistrar(properties, guardDrivers, providerDrivers, authManager);
    }
}
