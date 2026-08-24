package com.weacsoft.jaravel.vendor.redis;

import com.weacsoft.jaravel.vendor.core.lock.LockProvider;
import com.weacsoft.jaravel.vendor.core.lock.RegisterLockProvider;
import com.weacsoft.jaravel.vendor.redis.lock.RedisLockProviderImpl;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Redis 自动配置：连接管理 + 分布式锁。
 * <p>
 * 当 Redis 依赖存在且 {@code jaravel.redis.connections} 配置存在时自动启用。
 * <p>
 * Redis 分布式锁通过 {@code @RegisterLockProvider} 注解注册到
 * {@link com.weacsoft.jaravel.vendor.core.lock.LockProviderManager}，不进入 Spring 容器。
 */
@AutoConfiguration
@ConditionalOnClass(RedisManager.class)
@ConditionalOnProperty(prefix = "jaravel.redis", name = "connections")
@EnableConfigurationProperties(RedisProperties.class)
public class RedisAutoConfiguration {

    /**
     * Redis 管理器 bean：管理所有命名连接。
     */
    @Bean
    public RedisManager redisManager(RedisProperties properties) {
        return new RedisManager(properties);
    }

    /**
     * 通过 {@code @RegisterLockProvider} 注解注册分布式锁提供者，
     * 不进入 Spring 容器，避免 bean name 冲突。
     */
    @RegisterLockProvider(value = "redis", defaultProvider = true)
    public LockProvider redisLockProvider(RedisManager redisManager) {
        return new RedisLockProviderImpl(redisManager, null);
    }
}