package com.weacsoft.jaravel.vendor.springboot.sessionredis;

import com.weacsoft.jaravel.vendor.core.publish.PublishableRegistry;
import com.weacsoft.jaravel.vendor.session.redis.SessionRedisPublishableConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * session-redis 模块「发布配置」自动装配。
 * <p>
 * <b>为什么要从 {@link SessionRedisAutoConfiguration} 中拆出来单独成类？</b>
 * <p>
 * {@code SessionRedisAutoConfiguration} 在<b>类级别</b>叠加了一组运行时条件：
 * {@code @ConditionalOnWebApplication}、{@code @Conditional(OnRedisSessionDriverCondition.class)}
 * 以及 {@code @ConditionalOnBean(RedisManager.class)}。这些条件保证「安装 ≠ 启用」，
 * 对<b>运行期</b>的 Session 存储装配是正确的；但它们会连带把
 * {@link SessionRedisPublishableConfig} 这个 Bean 一起掐掉。
 * <p>
 * 而 {@code artisan vendor:publish} 属于<b>构建期脚手架</b>：它的使用者恰恰是
 * 「还没配好 Redis、正想生成一份配置模板来照着填」的开发者。若发布模板被
 * 运行期基础设施（Redis 连接是否就绪、驱动是否已选为 redis、是否 Web 应用）反向卡住，
 * 就会陷入「要先配好 Redis 才能拿到 Redis 的配置模板」的死循环。
 * <p>
 * 因此本类使用静态注册表（注册 {@code vendor.session.redis.SessionRedisPublishableConfig}，
 * 纯契约载体位于 session-redis 模块），确保任何情况下都能执行
 * {@code artisan vendor:publish --tag=session-redis}。
 */
@AutoConfiguration
public class SessionRedisPublishAutoConfiguration {
    static {
        PublishableRegistry.register(new SessionRedisPublishableConfig());
    }
}
