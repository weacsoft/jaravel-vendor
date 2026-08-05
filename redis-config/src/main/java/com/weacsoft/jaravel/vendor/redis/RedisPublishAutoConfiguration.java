package com.weacsoft.jaravel.vendor.redis;

import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * redis-config 模块「发布配置」自动装配。
 * <p>
 * <b>为什么要从 {@link RedisAutoConfiguration} 中拆出来单独成类？</b>
 * <p>
 * {@code RedisAutoConfiguration} 在<b>类级别</b>带有
 * {@code @ConditionalOnProperty(prefix = "jaravel.redis", name = "connections")}。
 * 该条件<b>没有</b> {@code matchIfMissing = true}，即<b>默认不成立</b>——
 * 只有当工程里已经写好了 {@code jaravel.redis.connections.*} 才会装配。
 * <p>
 * 这是本次审计中最典型的死循环：开发者引入 redis-config 依赖的第一件事，
 * 就是执行 {@code artisan vendor:publish --tag=redis-config} 生成 {@code RedisConfig.java}
 * 来<b>声明连接</b>；可在连接声明出来之前，条件不成立，发布配置声明根本不存在，
 * 于是「要先配好连接，才能拿到用于配连接的模板」。
 * <p>
 * {@code artisan vendor:publish} 属于<b>构建期脚手架</b>，绝不应被运行期配置反向卡住。
 * 因此本类<b>只保留 {@code @ConditionalOnClass(PublishableConfig.class)} 这一个条件</b>，
 * 不含任何运行时条件，确保任何情况下都能执行 {@code artisan vendor:publish --tag=redis-config}。
 */
@AutoConfiguration
@ConditionalOnClass(PublishableConfig.class)
public class RedisPublishAutoConfiguration {

    /**
     * 声明 redis-config 模块的可发布配置类，供 {@code artisan vendor:publish --tag=redis-config} 使用。
     * <p>
     * 仅声明元数据，不依赖 artisan 模块；未引入 artisan 时该 Bean 无人消费，无副作用。
     *
     * @return 可发布配置声明
     */
    @Bean
    @ConditionalOnMissingBean(RedisPublishableConfig.class)
    public RedisPublishableConfig redisPublishableConfig() {
        return new RedisPublishableConfig();
    }
}
