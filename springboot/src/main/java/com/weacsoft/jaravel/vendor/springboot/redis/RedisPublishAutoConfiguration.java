package com.weacsoft.jaravel.vendor.springboot.redis;

import com.weacsoft.jaravel.vendor.core.publish.PublishableRegistry;
import com.weacsoft.jaravel.vendor.redis.RedisPublishableConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

/**
 * redis 模块「发布配置」自动装配。
 * <p>
 * <b>为什么从 {@link RedisAutoConfiguration} 中拆出来单独成类</b>：
 * {@code RedisAutoConfiguration} 在类级带有
 * {@code @ConditionalOnProperty(prefix = "jaravel.redis", name = "connections")}，
 * 该条件无 {@code matchIfMissing = true}，默认不成立——
 * 仅当工程已写好 {@code jaravel.redis.connections.*} 才会装配。
 * <p>
 * 开发者引入 redis 依赖后的第一步，是执行 {@code artisan vendor:publish --tag=redis}
 * 生成 {@code RedisConfig.java} 来<b>声明连接</b>；而在连接声明之前条件不成立，
 * 发布配置声明不存在，形成「先要连接，才能拿到配置连接的模板」的循环。
 * <p>
 * {@code artisan vendor:publish} 属于<b>构建期脚手架</b>，不应被运行期配置反向约束。
 * 因此本类使用静态注册表（注册 {@code vendor.redis.RedisPublishableConfig}，
 * 纯契约载体位于 redis 模块），确保任何情况下都能执行
 * {@code artisan vendor:publish --tag=redis}。
 */
@AutoConfiguration
@ConditionalOnClass(RedisPublishableConfig.class)
public class RedisPublishAutoConfiguration {
    static {
        PublishableRegistry.register(new RedisPublishableConfig());
    }
}
