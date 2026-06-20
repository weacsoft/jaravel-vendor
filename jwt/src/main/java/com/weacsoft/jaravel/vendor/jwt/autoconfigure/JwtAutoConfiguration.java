package com.weacsoft.jaravel.vendor.jwt.autoconfigure;

import com.weacsoft.jaravel.vendor.auth.AuthManager;
import com.weacsoft.jaravel.vendor.cache.CacheManager;
import com.weacsoft.jaravel.vendor.cache.CacheStore;
import com.weacsoft.jaravel.vendor.jwt.JwtConfig;
import com.weacsoft.jaravel.vendor.jwt.JwtGuard;
import com.weacsoft.jaravel.vendor.jwt.JwtService;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * JWT 自动装配：注册 JwtConfig、JwtService Bean，并通过 {@link AuthManager#registerGuardDriver}
 * 将 jwt 驱动插件式注册到 AuthManager。
 * <p>
 * 引入 {@code jwt} 模块即自动启用 JWT 认证能力；未引入时 AuthManager 不会识别 "jwt" 驱动。
 * <p>
 * <b>黑名单缓存</b>：JwtService 依赖 {@link CacheStore} 实现 token 黑名单（登出踢 token）。
 * 默认使用 {@code jaravel.jwt.blacklist-store} 指定的缓存 store（默认 {@code array}），
 * 多实例部署可切换为 {@code file} 或 Redis 等共享缓存。
 * <p>
 * 注意：{@link JwtService} 由本类的 {@code @Bean} 方法产生，因此不能在字段上
 * {@code @Autowired} 自身产生的 Bean（Spring 6 默认禁止循环引用）。这里改为在
 * {@link #afterSingletonsInstantiated()} 中通过 {@link ApplicationContext} 获取，
 * 该回调在所有单例 Bean 就绪后才执行，天然避免循环依赖。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({AuthManager.class, JwtService.class})
@EnableConfigurationProperties(JwtProperties.class)
public class JwtAutoConfiguration implements SmartInitializingSingleton {

    @Autowired
    private AuthManager authManager;

    @Autowired
    private ApplicationContext applicationContext;

    @Bean
    @ConditionalOnMissingBean
    public JwtConfig jwtConfig(JwtProperties properties) {
        return new JwtConfig()
                .setSecret(properties.getSecret())
                .setTtl(properties.getTtl())
                .setRefreshTtl(properties.getRefreshTtl())
                .setHeader(properties.getHeader())
                .setPrefix(properties.getPrefix())
                .setRefreshEnabled(properties.isRefreshEnabled())
                .setBlacklistStore(properties.getBlacklistStore())
                .setBlacklistPrefix(properties.getBlacklistPrefix());
    }

    /**
     * 创建 JwtService，注入黑名单缓存 store。
     * <p>
     * 从 {@link CacheManager} 按 {@link JwtConfig#getBlacklistStore()} 指定的名称获取 store
     * （默认 {@code array}）。若指定的 store 未注册，回退到默认 store。
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtService jwtService(JwtConfig jwtConfig, CacheManager cacheManager) {
        CacheStore blacklistStore;
        try {
            blacklistStore = cacheManager.store(jwtConfig.getBlacklistStore());
        } catch (IllegalStateException e) {
            // 指定的 store 未注册，回退到默认 store
            blacklistStore = cacheManager.store();
        }
        return new JwtService(jwtConfig, blacklistStore);
    }

    /**
     * 所有单例 Bean 就绪后，将 jwt 守卫工厂注册到 AuthManager。
     * <p>
     * 工厂创建 JwtGuard 时传入 {@link JwtConfig#isRefreshEnabled()}，控制是否启用自动续期。
     */
    @Override
    public void afterSingletonsInstantiated() {
        JwtService jwtService = applicationContext.getBean(JwtService.class);
        JwtConfig jwtConfig = applicationContext.getBean(JwtConfig.class);
        boolean refreshEnabled = jwtConfig.isRefreshEnabled();
        authManager.registerGuardDriver("jwt",
                (name, provider, config) -> new JwtGuard(name, provider, jwtService, refreshEnabled));
    }
}
