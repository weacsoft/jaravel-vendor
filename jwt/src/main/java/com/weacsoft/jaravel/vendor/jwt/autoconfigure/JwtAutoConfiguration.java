package com.weacsoft.jaravel.vendor.jwt.autoconfigure;

import com.weacsoft.jaravel.vendor.auth.AuthManager;
import com.weacsoft.jaravel.vendor.cache.CacheManager;
import com.weacsoft.jaravel.vendor.cache.CacheStore;
import com.weacsoft.jaravel.vendor.jwt.JwtConfig;
import com.weacsoft.jaravel.vendor.jwt.JwtGuard;
import com.weacsoft.jaravel.vendor.jwt.JwtService;
import com.weacsoft.jaravel.vendor.jwt.JwtTokenResponseFilter;
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
 * <b>黑名单开关</b>：当 {@code jaravel.jwt.blacklist-enabled=false}（默认）时，JwtService 表现为
 * 标准 JWT，不依赖缓存模块。开启后从 {@link CacheManager} 获取指定 store 做 token 黑名单。
 * <p>
 * <b>宽限期</b>：当 {@code jaravel.jwt.grace-period-seconds > 0} 且黑名单开启时，
 * 过期 token 在宽限期内仍可请求一次，{@link JwtTokenResponseFilter} 会自动将新 token
 * 写入响应 header。
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
                .setBlacklistEnabled(properties.isBlacklistEnabled())
                .setBlacklistStore(properties.getBlacklistStore())
                .setBlacklistPrefix(properties.getBlacklistPrefix())
                .setGracePeriodSeconds(properties.getGracePeriodSeconds())
                .setGraceHeader(properties.getGraceHeader());
    }

    /**
     * 创建 JwtService。
     * <p>
     * 当 {@code blacklistEnabled=true} 时，从 {@link CacheManager} 获取指定 store 注入黑名单。
     * 当 {@code blacklistEnabled=false} 时，blacklistStore 传 null，JwtService 表现为标准 JWT。
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtService jwtService(JwtConfig jwtConfig, CacheManager cacheManager) {
        CacheStore blacklistStore = null;
        if (jwtConfig.isBlacklistEnabled()) {
            try {
                blacklistStore = cacheManager.store(jwtConfig.getBlacklistStore());
            } catch (IllegalStateException e) {
                blacklistStore = cacheManager.store();
            }
        }
        return new JwtService(jwtConfig, blacklistStore);
    }

    /**
     * JWT token 响应过滤器：当请求中签发了新 token（自动续期或宽限期续期）时，
     * 自动将新 token 写入响应 header。
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtTokenResponseFilter jwtTokenResponseFilter(JwtConfig jwtConfig, AuthManager authManager) {
        return new JwtTokenResponseFilter(jwtConfig, authManager);
    }

    /**
     * 所有单例 Bean 就绪后，将 jwt 守卫工厂注册到 AuthManager。
     */
    @Override
    public void afterSingletonsInstantiated() {
        JwtService jwtService = applicationContext.getBean(JwtService.class);
        JwtConfig jwtConfig = applicationContext.getBean(JwtConfig.class);
        boolean refreshEnabled = jwtConfig.isRefreshEnabled();
        authManager.registerGuardDriver("jwt",
                (name, provider, config) -> new JwtGuard(name, provider, jwtService, refreshEnabled, jwtConfig));
    }
}
