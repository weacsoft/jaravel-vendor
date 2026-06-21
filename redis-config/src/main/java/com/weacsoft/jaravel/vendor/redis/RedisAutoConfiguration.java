package com.weacsoft.jaravel.vendor.redis;

import com.weacsoft.jaravel.vendor.redis.lock.RedisLockProviderImpl;
import com.weacsoft.jaravel.vendor.schedule.RedisLockProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Redis 自动装配，对齐 Laravel Redis 服务提供者。
 * <p>
 * 当 classpath 存在 {@link RedisManager} 且配置了 {@code jaravel.redis.connections.*} 时，
 * 创建 {@link RedisManager} bean。所有连接在首次访问时惰性创建，进程生命周期内复用。
 * <p>
 * 该模块是其他 Redis 相关模块（redis-cache、session-redis）的基础依赖，
 * 提供统一的 Redis 连接管理能力，避免每个模块各自创建连接池。
 * <p>
 * 同时提供 {@link RedisLockProvider} 实现，供 schedule 模块的分布式锁使用。
 */
@AutoConfiguration
@ConditionalOnClass(RedisManager.class)
@ConditionalOnProperty(prefix = "jaravel.redis", name = "connections")
@EnableConfigurationProperties(RedisProperties.class)
public class RedisAutoConfiguration {

    /**
     * Redis 管理器 bean：管理所有命名连接。
     * <p>
     * 以 {@code @ConditionalOnMissingBean} 暴露，便于业务方覆盖。
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisManager redisManager(RedisProperties properties) {
        return new RedisManager(properties);
    }

    /**
     * Redis 分布式锁提供者 bean。
     * <p>
     * 使用默认 Redis 连接，供 {@code schedule} 模块的定时任务分布式锁使用。
     * 当 schedule 模块不存在时，此 bean 不会被创建（{@code @ConditionalOnClass} 控制）。
     */
    @Bean
    @ConditionalOnMissingBean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnClass(RedisLockProvider.class)
    public RedisLockProvider redisLockProvider(RedisManager redisManager) {
        return new RedisLockProviderImpl(redisManager, null);
    }
}
