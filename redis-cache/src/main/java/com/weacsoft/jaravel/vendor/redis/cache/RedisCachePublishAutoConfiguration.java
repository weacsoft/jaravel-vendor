package com.weacsoft.jaravel.vendor.redis.cache;

import com.weacsoft.jaravel.vendor.core.publish.PublishableRegistry;

/**
 * redis-cache 模块「发布配置」自动装配。
 * <p>
 * <b>为什么要从 {@link RedisCacheAutoConfiguration} 中拆出来单独成类？</b>
 * <p>
 * {@code RedisCacheAutoConfiguration} 在<b>类级别</b>叠加了一组运行时条件：
 * {@code @Conditional(OnRedisCacheStoreCondition.class)}（必须已显式配置
 * {@code driver: redis} 的缓存 store）与 {@code @ConditionalOnBean(RedisManager.class)}
 * （容器里必须已经装配出 Redis 管理器）。这些条件对<b>运行期</b>的缓存驱动装配是正确的，
 * 但会连带把 {@link RedisCachePublishableConfig} 这个 Bean 一起掐掉。
 * <p>
 * 而 {@code artisan vendor:publish} 属于<b>构建期脚手架</b>：它的使用者恰恰是
 * 「还没配好 Redis 缓存、正想生成一份配置模板来照着填」的开发者。若发布模板被
 * 运行期基础设施反向卡住，就会陷入「要先配好 Redis 缓存才能拿到它的配置模板」的死循环。
 * <p>
 * 因此本类使用静态注册表，确保任何情况下都能执行
 * {@code artisan vendor:publish --tag=redis-cache}。
 */
@org.springframework.boot.autoconfigure.AutoConfiguration
public class RedisCachePublishAutoConfiguration {
    static {
        PublishableRegistry.register(new RedisCachePublishableConfig());
    }
}
