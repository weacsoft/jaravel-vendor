package com.weacsoft.jaravel.vendor.queue.database;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 队列模块「发布配置」自动配置。
 * <p>
 * 与 {@link QueueDatabaseAutoConfiguration} 解耦：后者依赖 DataSource / 驱动条件装配，
 * 而本配置<b>无条件加载</b>（仅要求引入 queue 模块），确保业务工程只要依赖 queue 模块，
 * 就能通过 {@code artisan vendor:publish --tag=queue} 发布 {@code QueueConfig.java}，
 * 不受驱动装配、DataSource 是否就绪等运行期条件影响。
 * <p>
 * 仅声明可发布配置元数据（{@link QueuePublishableConfig}），不依赖 artisan 模块；
 * 未引入 artisan 时该 Bean 无人消费，无副作用。
 */
@Configuration(proxyBeanMethods = false)
public class QueuePublishAutoConfiguration {

    /**
     * 声明 queue 模块的可发布配置类，供 {@code artisan vendor:publish --tag=queue} 使用。
     *
     * @return 可发布配置
     */
    @Bean
    @ConditionalOnMissingBean
    public QueuePublishableConfig queuePublishableConfig() {
        return new QueuePublishableConfig();
    }
}
