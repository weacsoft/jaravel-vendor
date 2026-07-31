package com.weacsoft.jaravel.vendor.auth.autoconfigure;

import com.weacsoft.jaravel.vendor.auth.AuthManager;
import com.weacsoft.jaravel.vendor.auth.contract.AuthGuardDriver;
import com.weacsoft.jaravel.vendor.auth.contract.SessionStore;
import com.weacsoft.jaravel.vendor.auth.contract.UserProviderDriver;
import com.weacsoft.jaravel.vendor.auth.filter.AuthLifecycleFilter;
import com.weacsoft.jaravel.vendor.auth.guard.SessionGuardDriver;
import com.weacsoft.jaravel.vendor.auth.session.CookieSessionStore;
import com.weacsoft.jaravel.vendor.auth.session.SessionStoreHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
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
     * 全局 Session 存储持有者。
     * <p>
     * 作为 {@link SessionStore} 的间接层注入到 {@link SessionGuardDriver}，
     * 使得 {@code @RegisterSessionStore} 注解可以在所有 Bean 初始化完成后
     * 再决定真正的实现，而不影响驱动的构造时机。
     */
    @Bean
    @ConditionalOnMissingBean
    public SessionStoreHolder sessionStoreHolder() {
        return new SessionStoreHolder();
    }

    /**
     * 注册 {@link SessionStoreRegistrar}，扫描 {@code @RegisterSessionStore}
     * 注解方法并解析出全局唯一的 Session 存储（含唯一性校验与默认回退）。
     */
    @Bean
    @ConditionalOnMissingBean
    public SessionStoreRegistrar sessionStoreRegistrar(ApplicationContext context,
                                                      SessionStoreHolder holder) {
        return new SessionStoreRegistrar(context, holder);
    }

    /**
     * Session 守卫驱动（工厂模式）。
     * <p>
     * 实现 {@link AuthGuardDriver}，支持 "session" 驱动。
     * 注入 {@link SessionStoreHolder}，实际存储实现由
     * {@code @RegisterSessionStore} 注解或 {@link SessionStore} Bean 决定，
     * 都没有时回退到 {@link CookieSessionStore}。
     */
    @Bean
    @ConditionalOnMissingBean
    public SessionGuardDriver sessionGuardDriver(SessionStoreHolder sessionStoreHolder) {
        return new SessionGuardDriver(sessionStoreHolder);
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
    public AuthRegistrar authRegistrar(@Autowired AuthManager authManager,@Autowired List<AuthGuardDriver> guardDrivers,@Autowired List<UserProviderDriver> providerDrivers) {
        return new AuthRegistrar(properties, guardDrivers, providerDrivers, authManager);
    }
}
