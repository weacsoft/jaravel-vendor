package com.weacsoft.jaravel.vendor.session.redis;

import com.weacsoft.jaravel.vendor.auth.AuthManager;
import com.weacsoft.jaravel.vendor.redis.RedisManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Redis Session 自动装配。
 * <p>
 * 当 {@link RedisManager} 和 {@link AuthManager} 均存在时，
 * 创建 {@link RedisSessionStore} 并将 {@code redis-session} guard 驱动注册到 {@link AuthManager}。
 * <p>
 * 注册后，业务方在 auth 配置中将 guard driver 设为 {@code redis-session} 即可启用 Redis Session：
 * <pre>
 * jaravel:
 *   auth:
 *     guards:
 *       web:
 *         driver: redis-session
 *         provider: users
 * </pre>
 * <p>
 * 这样，web 守卫将使用 Redis 存储 Session，实现多机 Session 同步。
 */
@AutoConfiguration
@AutoConfigureAfter({com.weacsoft.jaravel.vendor.redis.RedisAutoConfiguration.class,
                     com.weacsoft.jaravel.vendor.auth.autoconfigure.AuthAutoConfiguration.class})
@ConditionalOnClass({RedisSessionStore.class, AuthManager.class, RedisManager.class})
@ConditionalOnBean({RedisManager.class, AuthManager.class})
@ConditionalOnProperty(prefix = "jaravel.session.redis", name = "auto-register", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SessionRedisProperties.class)
public class SessionRedisAutoConfiguration {

    /**
     * Redis Session 存储 bean。
     * <p>
     * 以 {@code @ConditionalOnMissingBean} 暴露，便于业务方覆盖。
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisSessionStore redisSessionStore(RedisManager redisManager,
                                               SessionRedisProperties properties) {
        return new RedisSessionStore(
                redisManager,
                properties.getConnection(),
                properties.getPrefix(),
                properties.getLifetime(),
                properties.getCookie()
        );
    }

    /**
     * 将 {@code redis-session} guard 驱动注册到 AuthManager。
     * <p>
     * 注册后，guard 配置中 driver={@code redis-session} 的守卫将使用 {@link RedisSessionGuard}。
     */
    @Bean
    public RedisSessionGuardRegistrar redisSessionGuardRegistrar(AuthManager authManager,
                                                                  RedisSessionStore sessionStore) {
        return new RedisSessionGuardRegistrar(authManager, sessionStore);
    }

    /** 注册器：将 redis-session 驱动注册到 AuthManager */
    public static class RedisSessionGuardRegistrar {
        public RedisSessionGuardRegistrar(AuthManager authManager, RedisSessionStore sessionStore) {
            authManager.registerGuardDriver("redis-session",
                    (name, provider, config) -> new RedisSessionGuard(name, provider, sessionStore));
        }
    }
}
