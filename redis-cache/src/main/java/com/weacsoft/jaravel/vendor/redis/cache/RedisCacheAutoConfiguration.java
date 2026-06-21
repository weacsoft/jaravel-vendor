package com.weacsoft.jaravel.vendor.redis.cache;

import com.weacsoft.jaravel.vendor.cache.CacheManager;
import com.weacsoft.jaravel.vendor.cache.DefaultCacheStore;
import com.weacsoft.jaravel.vendor.redis.RedisManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Redis 缓存自动装配。
 * <p>
 * 当 {@link RedisManager} 和 {@link CacheManager} 均存在时，
 * 创建 {@link RedisCacheDriver} 并将其注册为 {@code redis} store 到 {@link CacheManager}。
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
@AutoConfigureAfter({com.weacsoft.jaravel.vendor.cache.CacheAutoConfiguration.class,
                     com.weacsoft.jaravel.vendor.redis.RedisAutoConfiguration.class})
@ConditionalOnClass({RedisCacheDriver.class, CacheManager.class, RedisManager.class})
@ConditionalOnBean({RedisManager.class, CacheManager.class})
@ConditionalOnProperty(prefix = "jaravel.cache.redis", name = "auto-register", havingValue = "true", matchIfMissing = true)
public class RedisCacheAutoConfiguration {

    /**
     * Redis 缓存驱动 bean。
     * <p>
     * 使用 {@code cache} 命名连接（对应 {@code jaravel.redis.connections.cache}），
     * 以 {@code @ConditionalOnMissingBean} 暴露，便于业务方覆盖。
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
    public RedisCacheDriver redisCacheDriver(RedisManager redisManager,
                                             RedisCacheProperties properties) {
        return new RedisCacheDriver(redisManager, properties.getConnection());
    }

    /**
     * 将 Redis 缓存驱动注册到 CacheManager，作为 {@code redis} store。
     * <p>
     * 通过 {@link CacheManager#addStore} 动态注册，使用全局缓存前缀。
     */
    @Bean
    public RedisCacheRegistrar redisCacheRegistrar(CacheManager cacheManager,
                                                   RedisCacheDriver redisCacheDriver,
                                                   com.weacsoft.jaravel.vendor.cache.CacheProperties cacheProperties) {
        return new RedisCacheRegistrar(cacheManager, redisCacheDriver, cacheProperties.getPrefix());
    }

    /** 注册器：将 Redis store 添加到 CacheManager */
    public static class RedisCacheRegistrar {
        public RedisCacheRegistrar(CacheManager cacheManager,
                                   RedisCacheDriver driver,
                                   String prefix) {
            cacheManager.addStore("redis", new DefaultCacheStore(driver, prefix));
        }
    }
}
