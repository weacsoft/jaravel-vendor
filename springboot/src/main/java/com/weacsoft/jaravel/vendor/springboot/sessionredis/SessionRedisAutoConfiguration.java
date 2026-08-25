package com.weacsoft.jaravel.vendor.springboot.sessionredis;

import com.weacsoft.jaravel.vendor.http.autoconfigure.HttpSessionAutoConfiguration;
import com.weacsoft.jaravel.vendor.http.session.RegisterSessionStore;
import com.weacsoft.jaravel.vendor.http.session.SessionStore;
import com.weacsoft.jaravel.vendor.redis.RedisManager;
import com.weacsoft.jaravel.vendor.session.redis.RedisSessionStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

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
 *
 * <h3>装配条件：显式选用才装配（重要）</h3>
 * 本模块遵循 vendor 模块组的统一原则——<b>安装 ≠ 启用</b>。
 * 仅当明确把 session 驱动选为 redis 时才会注册与配置：
 * <pre>
 * jaravel:
 *   session:
 *     driver: redis            # ← 必须显式声明，否则本模块完全不装配
 *     redis:
 *       connection: session    # Redis 连接名
 *       prefix: laravel_session
 *       lifetime: 30
 *       cookie: manage_session
 * </pre>
 * 也可用开关强制启用/关闭（优先级最高）：
 * <pre>
 * jaravel.session.redis.auto-register: true   # 强制启用
 * jaravel.session.redis.auto-register: false  # 强制关闭
 * </pre>
 * <p>
 * 这样，项目即使引入了 {@code jaravel-session-redis} 依赖，只要没有选用 redis 驱动，
 * 就<b>不会创建任何 Bean、不会连接 Redis</b>，在没有 Redis 的环境下也能正常启动。
 * <p>
 * 此外还叠加了 {@code @ConditionalOnBean(RedisManager.class)} 作为兜底：
 * 即便误开了开关，只要 redis 模块本身没装配出 {@code RedisManager}，也不会因注入失败而中断启动。
 *
 * <p>如需使用其他 Session 存储，在应用的 {@code config/SessionConfig.java}
 * 中注册自定义 {@code @RegisterSessionStore} 即可覆盖本实现（用 {@code override = true} 显式提升优先级）。</p>
 * <p>
 * Spring 装配收口于 springboot 模块（session-redis 核心模块零 Spring 依赖）。
 */
@AutoConfiguration
@AutoConfigureAfter({com.weacsoft.jaravel.vendor.springboot.redis.RedisAutoConfiguration.class, HttpSessionAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({RedisSessionStore.class, RedisManager.class})
@Conditional(OnRedisSessionDriverCondition.class)
@ConditionalOnBean(RedisManager.class)
@EnableConfigurationProperties(SessionRedisProperties.class)
public class SessionRedisAutoConfiguration {

    /**
     * Redis Session 存储，通过 {@link RegisterSessionStore} 注册为全局 Session 存储。
     * <p>
     * 覆盖 http 模块默认的 {@code CookieSessionStore}（Servlet HttpSession）。
     * 由 http 的 {@code SessionStoreRegistrar} 在所有 Bean 初始化完成后统一扫描注册，
     * 保证唯一性（若业务方显式注册了 {@code @RegisterSessionStore(override = true)}，则以其为准）。
     *
     * @param redisManager Redis 管理器
     * @param properties   Redis Session 配置
     * @return Redis Session 存储
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
