package com.weacsoft.jaravel.vendor.redis.cache;

import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

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
 * 因此本类<b>只保留 {@code @ConditionalOnClass(PublishableConfig.class)} 这一个条件</b>
 * ——即「引入了 core 的 publish 能力」这一纯 classpath 判断，不含任何运行时条件，
 * 确保任何情况下都能执行 {@code artisan vendor:publish --tag=redis-cache}。
 * <p>
 * 同时，{@link RedisCachePublishableConfig} 的 Bean 定义<b>只在本类中出现一次</b>：
 * 同一 Bean 类型在两个自动配置里重复声明，即使加了 {@code @ConditionalOnMissingBean}
 * 也依赖自动配置之间不确定的加载顺序，属于脆弱写法，故原类中的同名方法已一并移除。
 */
@AutoConfiguration
@ConditionalOnClass(PublishableConfig.class)
public class RedisCachePublishAutoConfiguration {

    /**
     * 声明 redis-cache 模块的可发布配置类，供 {@code artisan vendor:publish --tag=redis-cache} 使用。
     * <p>
     * 仅声明元数据，不依赖 artisan 模块；未引入 artisan 时该 Bean 无人消费，无副作用。
     *
     * @return 可发布配置声明
     */
    @Bean
    @ConditionalOnMissingBean(RedisCachePublishableConfig.class)
    public RedisCachePublishableConfig redisCachePublishableConfig() {
        return new RedisCachePublishableConfig();
    }
}
