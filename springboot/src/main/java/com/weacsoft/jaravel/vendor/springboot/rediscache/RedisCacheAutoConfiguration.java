package com.weacsoft.jaravel.vendor.springboot.rediscache;

import com.weacsoft.jaravel.vendor.cache.CacheDriverFactory;
import com.weacsoft.jaravel.vendor.redis.RedisManager;
import com.weacsoft.jaravel.vendor.redis.cache.RedisCacheDriver;
import com.weacsoft.jaravel.vendor.redis.cache.RedisCacheDriverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

/**
 * Redis 缓存自动装配。
 * <p>
 * 当 {@link RedisManager} 存在时，创建 {@link RedisCacheDriverFactory} 并注册为 Spring Bean。
 * {@code vendor.springboot.cache.CacheAutoConfiguration} 会自动收集所有 {@link CacheDriverFactory} Bean 并注册到
 * {@code CacheManager}，由 {@code CacheManager} 在配置了 {@code redis} store 时按需创建驱动。
 * <p>
 * 不再直接创建 {@code RedisCacheDriver} 或 {@code RedisCacheStore} Bean，
 * 而是注册工厂，由 {@code CacheManager} 根据配置按需创建（对齐 Laravel 的按需创建模式）。
 * <p>
 * Spring 装配收口于 springboot 模块（redis-cache 核心模块零 Spring 依赖）。
 * <p>
 *
 * <h3>装配条件：用上了才装配</h3>
 * 遵循 vendor 模块组统一原则——<b>安装 ≠ 启用</b>。
 * 仅当配置里确实出现了 {@code driver: redis} 的缓存 store 才注册：
 * <pre>
 * jaravel:
 *   cache:
 *     redis:
 *       connection: cache          # 使用的 Redis 连接名，默认 cache
 *     stores:
 *       redis:
 *         driver: redis            # ← 必须显式声明，否则本模块完全不装配
 *         connection: cache        # 可覆盖顶层 connection 配置
 * </pre>
 * 也可用 {@code jaravel.cache.redis.auto-register} 强制启用（{@code true}）
 * 或强制关闭（{@code false}），优先级最高。
 * <p>
 * 注册后，业务方可通过 {@code Cache::store("redis")} 使用 Redis 缓存，
 * 或将 {@code jaravel.cache.default-store} 设为 {@code redis} 使其成为默认 store。
 */
@AutoConfiguration
@AutoConfigureAfter(com.weacsoft.jaravel.vendor.springboot.redis.RedisAutoConfiguration.class)
@ConditionalOnClass({RedisCacheDriver.class, CacheDriverFactory.class, RedisManager.class})
@Conditional(OnRedisCacheStoreCondition.class)
@ConditionalOnBean(RedisManager.class)
@EnableConfigurationProperties(RedisCacheProperties.class)
public class RedisCacheAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(RedisCacheAutoConfiguration.class);

    /**
     * Redis 缓存驱动工厂 bean。
     * <p>
     * 注册为 {@link CacheDriverFactory}，由 {@code CacheManager} 在创建 {@code redis} store 时
     * 按需调用 {@code factory.create(config)} 创建 {@link RedisCacheDriver} 实例。
     * <p>
     * 使用 {@code cache} 命名连接（对应 {@code jaravel.redis.connections.cache}），
     * 以 {@code @ConditionalOnMissingBean} 暴露，便于业务方覆盖。
     *
     * @param redisManager Redis 管理器
     * @param properties   Redis 缓存配置
     * @return Redis 缓存驱动工厂
     */
    @Bean
    @ConditionalOnMissingBean(RedisCacheDriverFactory.class)
    public RedisCacheDriverFactory redisCacheDriverFactory(RedisManager redisManager,
                                                            RedisCacheProperties properties) {
        logger.info("[redis-cache] 注册 Redis 缓存驱动工厂, 默认连接: {}", properties.getConnection());
        return new RedisCacheDriverFactory(redisManager, properties.getConnection());
    }
}
