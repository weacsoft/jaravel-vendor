package com.weacsoft.jaravel.vendor.storage.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 存储模块「发布配置」自动配置。
 * <p>
 * 与 {@link StorageAutoConfiguration} 解耦：后者受 {@code jaravel.storage.enabled} 开关控制，
 * 而本配置<b>无条件加载</b>，确保业务工程无论是否启用存储运行能力，
 * 都能通过 {@code artisan vendor:publish} 发布 {@code StorageConfig.java}。
 * <p>
 * 仅声明可发布配置元数据（{@link StoragePublishableConfig}），不依赖 artisan 模块；
 * 未引入 artisan 时该 Bean 无人消费，无副作用。
 */
@Configuration(proxyBeanMethods = false)
public class StoragePublishAutoConfiguration {

    /**
     * 声明 storage 模块的可发布配置类，供 {@code artisan vendor:publish --tag=storage} 使用。
     *
     * @return 可发布配置
     */
    @Bean
    @ConditionalOnMissingBean
    public StoragePublishableConfig storagePublishableConfig() {
        return new StoragePublishableConfig();
    }
}
