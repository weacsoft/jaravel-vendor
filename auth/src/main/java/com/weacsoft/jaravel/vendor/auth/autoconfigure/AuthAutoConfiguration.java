package com.weacsoft.jaravel.vendor.auth.autoconfigure;

import com.weacsoft.jaravel.vendor.auth.AuthManager;
import com.weacsoft.jaravel.vendor.auth.filter.AuthLifecycleFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 认证自动装配：注册 AuthManager、生命周期过滤器。
 * <p>
 * JWT 等扩展驱动由独立插件模块（如 {@code jwt} 模块）通过
 * {@link AuthManager#registerGuardDriver(String, com.weacsoft.jaravel.vendor.auth.contract.GuardFactory)}
 * 自行注册，auth 模块本身不再包含 JWT 实现。
 * <p>
 * 认证中间件 {@code Authenticate} 为普通 {@code Middleware} 实现，
 * 可直接传入 {@code Router.middleware()} 使用，无需别名注册。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(AuthManager.class)
@EnableConfigurationProperties(AuthProperties.class)
public class AuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuthManager authManager(AuthProperties properties) {
        AuthManager manager = new AuthManager();
        manager.setDefaultGuard(properties.getDefaultGuard());
        return manager;
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthLifecycleFilter authLifecycleFilter(AuthManager authManager) {
        return new AuthLifecycleFilter(authManager);
    }
}
