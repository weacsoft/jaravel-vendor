package com.weacsoft.jaravel.vendor.jwt.autoconfigure;

import com.weacsoft.jaravel.vendor.auth.AuthManager;
import com.weacsoft.jaravel.vendor.cache.CacheManager;
import com.weacsoft.jaravel.vendor.cache.CacheStore;
import com.weacsoft.jaravel.vendor.core.crypto.AppKey;
import com.weacsoft.jaravel.vendor.jwt.JwtConfig;
import com.weacsoft.jaravel.vendor.jwt.JwtGuardDriver;
import com.weacsoft.jaravel.vendor.jwt.JwtService;
import com.weacsoft.jaravel.vendor.jwt.JwtTokenResponseFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

/**
 * JWT 自动装配：注册 JwtConfig、JwtService、JwtGuardDriver Bean。
 * <p>
 * <b>工厂模式</b>：{@link JwtGuardDriver} 实现 {@link com.weacsoft.jaravel.vendor.auth.contract.AuthGuardDriver}，
 * 注册为 Spring Bean 后由 auth 模块的 {@code AuthAutoConfiguration} 自动收集并注册到 {@link AuthManager}，
 * 无需手动调用 {@code registerGuardDriver}。
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
public class JwtAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JwtAutoConfiguration.class);

    /**
     * 创建 JwtConfig。
     * <p>
     * <b>密钥兜底</b>：若用户没有显式配置 {@code jaravel.jwt.secret}（值仍等于
     * {@link JwtConfig#DEFAULT_SECRET}），则回退到 core 模块的全局应用密钥
     * {@code jaravel.key}，遵循「模块自身配置优先 → core 全局密钥兜底」。
     *
     * @param properties     JWT 配置属性
     * @param appKeyProvider 全局应用密钥（core 模块提供，缺失时保持模块默认值）
     * @return JWT 配置
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtConfig jwtConfig(JwtProperties properties, ObjectProvider<AppKey> appKeyProvider) {
        String secret = properties.getSecret();
        AppKey appKey = appKeyProvider.getIfAvailable();
        if (appKey != null) {
            String resolved = appKey.resolve(secret, JwtConfig.DEFAULT_SECRET);
            if (!resolved.equals(secret)) {
                log.info("[JWT] 未配置 jaravel.jwt.secret，签名密钥回退到全局应用密钥 jaravel.key");
            }
            secret = resolved;
        }
        return new JwtConfig()
                .setSecret(secret)
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
            String storeName = jwtConfig.getBlacklistStore();
            if (storeName == null || storeName.isEmpty()) {
                blacklistStore = cacheManager.store();
            } else {
                try {
                    blacklistStore = cacheManager.store(storeName);
                } catch (IllegalStateException e) {
                    blacklistStore = cacheManager.store();
                }
            }
        }
        return new JwtService(jwtConfig, blacklistStore);
    }

    /**
     * JWT 守卫驱动（工厂模式）。
     * <p>
     * 实现 {@link com.weacsoft.jaravel.vendor.auth.contract.AuthGuardDriver}，支持 "jwt" 驱动。
     * 由 auth 模块的 {@code AuthAutoConfiguration} 自动收集并注册到 {@link AuthManager}。
     */
    @Bean
    @ConditionalOnMissingBean
    @Conditional(OnJwtGuardDriverCondition.class)
    public JwtGuardDriver jwtGuardDriver(JwtService jwtService, JwtConfig jwtConfig) {
        return new JwtGuardDriver(jwtService, jwtConfig);
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
}
