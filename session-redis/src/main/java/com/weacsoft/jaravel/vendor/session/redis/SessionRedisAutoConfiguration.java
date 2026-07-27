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
 * 创建 {@link RedisSessionStore} 并注册为全局 {@link SessionStore} Bean。
 * <p>
 * <b>Session 存储是全局配置，不与 Guard 绑定</b>。{@link RedisSessionStore} 注册为
 * 唯一的 {@link SessionStore} Bean 后，所有 {@code session} 驱动的守卫
 * 都将使用 Redis 存储。如果应用同时引入了 auth 模块的 {@code CookieSessionStore}
 * （默认实现），本配置通过 {@code @ConditionalOnMissingBean(SessionStore.class)}
 * 确保只有第一个注册的存储生效。
 * <p>
 * 注册后，业务方在 {@code config/AuthConfig.java} 中注册 session 守卫即可自动使用 Redis 存储：
 * <pre>
 * authManager.registerGuard("web", "session", "users");
 * </pre>
 * <p>
 * 如需自定义 Redis Session 参数（连接名、前缀、过期时间、Cookie 名），
 * 通过 {@code jaravel.session.redis.*} 配置：
 * <pre>
 * jaravel:
 *   session:
 *     redis:
 *       auto-register: true
 *       connection: default
 *       prefix: session
 *       lifetime: 30
 *       cookie: manage_session
 * </pre>
 * <p>
 * 如需使用其他 Session 存储（如 file），在应用的 {@code config/SessionConfig.java}
 * 中注册自定义 {@code SessionStore} Bean 即可覆盖此实现。
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
     * 注册为全局唯一的 {@link SessionStore} 类型 Bean。
     * 当 auth 模块的默认 {@code CookieSessionStore} 尚未注册时（通过
     * {@code @ConditionalOnMissingBean(SessionStore.class)} 互斥），本 Bean 生效。
     * <p>
     * 由于 {@code @AutoConfigureAfter(AuthAutoConfiguration.class)}，
     * auth 模块的 {@code cookieSessionStore()} 会先尝试注册（也带 {@code @ConditionalOnMissingBean}），
     * 两者中只有一个会生效，取决于加载顺序。为避免歧义，建议业务方在
     * {@code config/SessionConfig.java} 中显式注册所需的 {@code SessionStore} Bean。
     */
    @Bean
    @ConditionalOnMissingBean(SessionStore.class)
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
