package com.weacsoft.jaravel.vendor.session.redis;

import com.weacsoft.jaravel.vendor.http.autoconfigure.HttpSessionAutoConfiguration;
import com.weacsoft.jaravel.vendor.http.session.RegisterSessionStore;
import com.weacsoft.jaravel.vendor.http.session.SessionStore;
import com.weacsoft.jaravel.vendor.redis.RedisAutoConfiguration;
import com.weacsoft.jaravel.vendor.redis.RedisManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Redis Session 自动装配（多机 Session 同步场景）。
 * <p>
 * 本模块<b>不强依赖 auth</b>：它只依赖 http 模块提供的 Session 功能
 * （{@link SessionStore} 接口与 {@link RegisterSessionStore} 注册机制），
 * 通过 {@code @RegisterSessionStore} 把 {@link RedisSessionStore} 注册为全局 Session 存储，
 * 覆盖 http 默认的 {@code CookieSessionStore}（Servlet HttpSession）。
 * <p>
 * <b>Session 存储是全局配置，不与 Guard 绑定</b>。注册后，所有 {@code session} 驱动的守卫
 * （无论由哪个认证模块提供）都将使用 Redis 存储，天然实现多机 Session 同步。
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
 *
 * <p>如需使用其他 Session 存储，在应用的 {@code config/SessionConfig.java}
 * 中注册自定义 {@code @RegisterSessionStore} 即可覆盖本实现（用 {@code override = true} 显式提升优先级）。</p>
 */
@AutoConfiguration
@AutoConfigureAfter({RedisAutoConfiguration.class, HttpSessionAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({RedisSessionStore.class, RedisManager.class})
@ConditionalOnProperty(prefix = "jaravel.session.redis", name = "auto-register", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SessionRedisProperties.class)
public class SessionRedisAutoConfiguration {

    /**
     * Redis Session 存储，通过 {@link RegisterSessionStore} 注册为全局 Session 存储。
     * <p>
     * 覆盖 http 模块默认的 {@code CookieSessionStore}（Servlet HttpSession）。
     * 由 http 的 {@code SessionStoreRegistrar} 在所有 Bean 初始化完成后统一扫描注册，
     * 保证唯一性（若业务方显式注册了 {@code @RegisterSessionStore(override = true)}，则以其为准）。
     */
    @Bean
    @RegisterSessionStore(override = true)
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
