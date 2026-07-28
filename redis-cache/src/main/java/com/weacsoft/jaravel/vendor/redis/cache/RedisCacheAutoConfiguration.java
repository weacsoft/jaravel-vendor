package com.weacsoft.jaravel.vendor.redis.cache;

import com.weacsoft.jaravel.vendor.cache.CacheDriverFactory;
import com.weacsoft.jaravel.vendor.redis.RedisManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Redis 缓存自动装配。
 * <p>
 * 当 {@link RedisManager} 存在时，创建 {@link RedisCacheDriverFactory} 并注册为 Spring Bean。
 * {@code CacheAutoConfiguration} 会自动收集所有 {@link CacheDriverFactory} Bean 并注册到
 * {@code CacheManager}，由 {@code CacheManager} 在配置了 {@code redis} store 时按需创建驱动。
 * <p>
 * 不再直接创建 {@code RedisCacheDriver} 或 {@code RedisCacheStore} Bean，
 * 而是注册工厂，由 {@code CacheManager} 根据配置按需创建（对齐 Laravel 的按需创建模式）。
 * <p>
 * 配置项：
 * <pre>
 * jaravel:
 *   cache:
 *     redis:
 *       connection: cache          # 使用的 Redis 连接名，默认 cache
 *       auto-register: true        # 是否自动注册工厂到 CacheManager
 *     stores:
 *       redis:
 *         driver: redis
 *         connection: cache        # 可覆盖顶层 connection 配置
 * </pre>
 * <p>
 * 注册后，业务方可通过 {@code Cache::store("redis")} 使用 Redis 缓存，
 * 或将 {@code jaravel.cache.default-store} 设为 {@code redis} 使其成为默认 store。
 */
@AutoConfiguration
@AutoConfigureAfter(com.weacsoft.jaravel.vendor.redis.RedisAutoConfiguration.class)
@ConditionalOnClass({RedisCacheDriver.class, CacheDriverFactory.class, RedisManager.class})
@ConditionalOnBean(RedisManager.class)
@ConditionalOnProperty(prefix = "jaravel.cache.redis", name = "auto-register", havingValue = "true", matchIfMissing = true)
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
