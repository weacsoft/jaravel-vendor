package com.weacsoft.jaravel.vendor.queue.database;

import com.weacsoft.jaravel.vendor.redis.RedisManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Redis 队列驱动自动装配。
 * <p>
 * 仅当以下条件全部满足时启用：
 * <ul>
 *   <li>{@code redis-config} 模块在类路径上（{@link RedisManager} 可加载）</li>
 *   <li>容器中存在 {@link RedisManager} Bean</li>
 *   <li>{@code jaravel.queue.driver=redis}</li>
 *   <li>尚不存在 {@link QueueDriver} Bean</li>
 * </ul>
 * <p>
 * 通过 {@code @AutoConfiguration(before = QueueDatabaseAutoConfiguration.class)} 保证先于 database
 * 自动装配处理：当 redis 驱动生效时，{@link QueueDatabaseAutoConfiguration} 的 database bean 因
 * {@code @ConditionalOnMissingBean(QueueDriver.class)} 被跳过；当 redis 不可用（未引入 redis-config
 * 或无 RedisManager）时自动回退到 database 驱动。
 * <p>
 * redis-config 为 optional 依赖，未引入时本配置类不会被加载（{@code @ConditionalOnClass} 保护），
 * 因此 {@link RedisManager} / {@link RedisQueueDriver} 的类引用不会引发 {@code NoClassDefFoundError}。
 */
@AutoConfiguration(before = QueueDatabaseAutoConfiguration.class)
@ConditionalOnClass(RedisManager.class)
@ConditionalOnBean(RedisManager.class)
@ConditionalOnProperty(prefix = "jaravel.queue", name = "driver", havingValue = "redis")
public class RedisQueueAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(RedisQueueAutoConfiguration.class);

    /**
     * 注册 Redis 队列驱动。
     *
     * @param redisManager Redis 管理器
     * @param props        队列全局配置
     * @param dbProps      数据库队列配置（复用 retry-after 等参数）
     * @return RedisQueueDriver 实例
     */
    @Bean
    @ConditionalOnMissingBean(QueueDriver.class)
    public RedisQueueDriver redisQueueDriver(RedisManager redisManager,
                                             QueueProperties props,
                                             QueueDatabaseProperties dbProps) {
        logger.info("[queue] 使用 redis 驱动: connection={}, retryAfter={}s, failedRetention={}d",
                props.getRedisConnection() == null || props.getRedisConnection().isEmpty() ? "default" : props.getRedisConnection(),
                dbProps.getRetryAfter(), props.getFailedJobRetentionDays());
        return new RedisQueueDriver(redisManager, props.getRedisConnection(),
                dbProps.getRetryAfter(), props.getFailedJobRetentionDays());
    }
}
