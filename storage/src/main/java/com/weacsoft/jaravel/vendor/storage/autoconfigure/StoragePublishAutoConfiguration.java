package com.weacsoft.jaravel.vendor.storage.autoconfigure;

import com.weacsoft.jaravel.vendor.core.publish.PublishableRegistry;

/**
 * 存储模块「发布配置」自动配置。
 * <p>
 * 与 {@link StorageAutoConfiguration} 解耦：后者受 {@code jaravel.storage.enabled} 开关控制，
 * 而本配置<b>无条件加载</b>，确保业务工程无论是否启用存储运行能力，
 * 都能通过 {@code artisan vendor:publish} 发布 {@code StorageConfig.java}。
 * <p>
 * 使用静态注册表，不依赖 Spring Bean 机制。
 */
@org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
public class StoragePublishAutoConfiguration {
    static {
        PublishableRegistry.register(new StoragePublishableConfig());
    }
}
