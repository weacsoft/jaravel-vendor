package com.weacsoft.jaravel.vendor.redis.cache;

import com.weacsoft.jaravel.vendor.cache.CacheStore;
import com.weacsoft.jaravel.vendor.cache.store.DefaultCacheStore;
import com.weacsoft.jaravel.vendor.redis.RedisManager;
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
 * 当 {@link RedisManager} 存在时，创建 {@link RedisCacheDriver} 并注册为 {@code @Bean("redis")} CacheStore。
 * {@code CacheManager} 通过 {@code Map<String, CacheStore>} 自动收集，无需手动 {@code addStore}。
 * <p>
 * 配置项：
 * <pre>
 * jaravel:
 *   cache:
 *     redis:
 *       connection: cache          # 使用的 Redis 连接名，默认 cache
 *       auto-register: true        # 是否自动注册到 CacheManager
 * </pre>
 * <p>
 * 注册后，业务方可通过 {@code Cache::store("redis")} 使用 Redis 缓存，
 * 或将 {@code jaravel.cache.default-store} 设为 {@code redis} 使其成为默认 store。
 */
@AutoConfiguration
@AutoConfigureAfter(com.weacsoft.jaravel.vendor.redis.RedisAutoConfiguration.class)
@ConditionalOnClass({RedisCacheDriver.class, CacheStore.class, RedisManager.class})
@ConditionalOnBean(RedisManager.class)
@ConditionalOnProperty(prefix = "jaravel.cache.redis", name = "auto-register", havingValue = "true", matchIfMissing = true)
public class RedisCacheAutoConfiguration {

    /**
     * Redis 缓存驱动 bean。
     * <p>
     * 使用 {@code cache} 命名连接（对应 {@code jaravel.redis.connections.cache}），
     * 以 {@code @ConditionalOnMissingBean} 暴露，便于业务方覆盖。
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisCacheDriver redisCacheDriver(RedisManager redisManager,
                                             RedisCacheProperties properties) {
        return new RedisCacheDriver(redisManager, properties.getConnection());
    }

    /**
     * Redis 缓存 store（bean name "redis" 即 store name）。
     * <p>
     * 由 {@code CacheManager} 通过 {@code Map<String, CacheStore>} 自动收集，无需手动 {@code addStore}。
     * 使用全局缓存前缀。
     */
    @Bean("redis")
    @ConditionalOnMissingBean(name = "redis")
    public CacheStore redisCacheStore(RedisCacheDriver redisCacheDriver,
                                      com.weacsoft.jaravel.vendor.cache.autoconfigure.CacheProperties cacheProperties) {
        return new DefaultCacheStore(redisCacheDriver, cacheProperties.getPrefix());
    }
}
