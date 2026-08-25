package com.weacsoft.jaravel.vendor.springboot.storage;

import com.weacsoft.jaravel.vendor.core.publish.PublishableRegistry;
import com.weacsoft.jaravel.vendor.storage.autoconfigure.StoragePublishableConfig;

/**
 * 存储模块「发布配置」自动装配。
 * <p>
 * 与 {@link StorageAutoConfiguration} 解耦：后者受 {@code jaravel.storage.enabled} 开关控制，
 * 而本配置<b>无条件加载</b>，确保业务工程无论是否启用存储运行能力，
 * 都能通过 {@code artisan vendor:publish} 发布 {@code StorageConfig.java}。
 * <p>
 * 使用静态注册表，不依赖 Spring Bean 机制（对齐
 * {@code vendor.springboot.cache.CacheAutoConfiguration} 的静态块模式）。
 */
@org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
public class StoragePublishAutoConfiguration {
    static {
        PublishableRegistry.register(new StoragePublishableConfig());
    }
}
