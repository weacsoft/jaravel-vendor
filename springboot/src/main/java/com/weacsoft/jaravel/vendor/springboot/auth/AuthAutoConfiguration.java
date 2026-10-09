package com.weacsoft.jaravel.vendor.springboot.auth;

import com.weacsoft.jaravel.vendor.auth.AuthManager;
import com.weacsoft.jaravel.vendor.auth.contract.AuthGuardDriver;
import com.weacsoft.jaravel.vendor.auth.contract.UserProviderDriver;
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
 * Spring 装配收口于 springboot 模块（auth 核心模块零 Spring 依赖）。
 *
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
 *   <li><b>手动调用</b>：直接调用 {@code AuthManager} 的 {@code registerProvider} / {@code registerGuard}</li>
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
 * <h3>auth 不依赖 session：本类只装配「认证标准」</h3>
 * 本类只注册 auth 标准相关 Bean（AuthManager、生命周期过滤器、注册器）。
 * 「用 Session 保存登录态」属于一种<b>实现</b>，装配在 {@link SessionGuardAutoConfiguration}
 * （该类带类级条件，仅在引入了 {@code auth-session} + {@code session} 时才生效）。
 * 因此只依赖 auth 的应用不会被拖入 session 能力，也不会因缺这两个模块而启动失败。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(AuthManager.class)
@EnableConfigurationProperties(AuthProperties.class)
public class AuthAutoConfiguration {

    @Autowired
    private AuthProperties properties;

    /**
     * 认证管理器 Bean（{@code @ConditionalOnMissingBean}，便于业务方自定义覆盖）。
     *
     * @return AuthManager 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public AuthManager authManager() {
        AuthManager manager = new AuthManager();
        manager.setDefaultGuard(properties.getDefaultGuard());
        return manager;
    }

    /**
     * 认证生命周期过滤器：绑定 {@code AuthContext}、清理 ThreadLocal。
     *
     * @param authManager 认证管理器
     * @return 生命周期过滤器
     */
    @Bean
    @ConditionalOnMissingBean
    public AuthLifecycleFilter authLifecycleFilter(AuthManager authManager) {
        return new AuthLifecycleFilter(authManager);
    }

    /**
     * 认证配置注册器：所有单例 Bean 就绪后统一注册 providers / guards / 驱动 / 注解声明。
     *
     * @param authManager      认证管理器
     * @param guardDrivers     已注册的守卫驱动 Bean 列表
     * @param providerDrivers  已注册的提供者驱动 Bean 列表
     * @return 注册器实例
     */
    @Bean
    @ConditionalOnMissingBean(AuthRegistrar.class)
    public AuthRegistrar authRegistrar(@Autowired AuthManager authManager,
                                       @Autowired List<AuthGuardDriver> guardDrivers,
                                       @Autowired List<UserProviderDriver> providerDrivers) {
        return new AuthRegistrar(properties, guardDrivers, providerDrivers, authManager);
    }
}
