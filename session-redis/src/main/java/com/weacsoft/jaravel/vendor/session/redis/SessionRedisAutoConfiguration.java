package com.weacsoft.jaravel.vendor.session.redis;

import com.weacsoft.jaravel.vendor.auth.AuthManager;
import com.weacsoft.jaravel.vendor.auth.contract.SessionStore;
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
 * 创建 {@link RedisSessionStore} 并注册为 {@link SessionStore} Bean。
 * <p>
 * <b>工厂模式</b>：{@link RedisSessionStore} 实现 {@link SessionStore} 接口，
 * {@code SessionGuardDriver} 会自动收集所有 {@code SessionStore} Bean，
 * 在创建 SessionGuard 时通过 {@code support("redis")} 匹配。
 * <p>
 * 注册后，业务方在 auth 配置中将 guard 的 {@code session-store} 设为 {@code redis} 即可启用：
 * <pre>
 * jaravel:
 *   auth:
 *     guards:
 *       web:
 *         driver: session
 *         provider: users
 *         session-store: redis
 * </pre>
 * <p>
 * 或编程式注册：
 * <pre>
 * authManager.registerGuard("web", "session", "users", "redis");
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
     * Redis Session 存储 bean，实现 {@link SessionStore} 接口。
     * <p>
     * 注册为 {@link SessionStore} 类型，使 {@code SessionGuardDriver} 能自动发现。
     * 以 {@code @ConditionalOnMissingBean} 暴露，便于业务方覆盖。
     */
    @Bean
    @ConditionalOnMissingBean(RedisSessionStore.class)
    public SessionStore redisSessionStore(RedisManager redisManager,
                                          SessionRedisProperties properties) {
        return new RedisSessionStore(
                redisManager,
                properties.getConnection(),
                properties.getPrefix(),
                properties.getLifetime(),
                properties.getCookie()
        );
    }
}
