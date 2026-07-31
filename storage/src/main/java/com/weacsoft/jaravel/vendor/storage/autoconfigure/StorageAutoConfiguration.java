package com.weacsoft.jaravel.vendor.storage.autoconfigure;

import com.weacsoft.jaravel.vendor.storage.StorageManager;
import com.weacsoft.jaravel.vendor.storage.database.DatabaseFilesystemDriver;
import com.weacsoft.jaravel.vendor.storage.local.LocalFilesystemDriver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 存储模块自动配置。
 * <p>
 * 通过 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 被 Spring Boot 自动发现，引入依赖即生效，无需任何注解。
 *
 * <h3>装配内容</h3>
 * <ul>
 *   <li>{@link StorageManager} — 存储管理器（多磁盘解析）</li>
 *   <li>{@link LocalFilesystemDriver} — 内置 local/public 驱动</li>
 *   <li>{@link StorageRegistrar} — 注解与配置扫描注册器</li>
 * </ul>
 *
 * <h3>关闭模块</h3>
 * <pre>
 * jaravel:
 *   storage:
 *     enabled: false
 * </pre>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StorageProperties.class)
@ConditionalOnProperty(prefix = "jaravel.storage", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StorageAutoConfiguration {

    /**
     * 存储管理器。
     *
     * @return StorageManager 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public StorageManager storageManager() {
        return new StorageManager();
    }

    /**
     * 内置本地文件系统驱动，支持 {@code local} 与 {@code public} 两个驱动名。
     *
     * @return 驱动实例
     */
    @Bean
    @ConditionalOnMissingBean
    public LocalFilesystemDriver localFilesystemDriver() {
        return new LocalFilesystemDriver();
    }

    /**
     * 声明 storage 模块的可发布配置类，供 {@code artisan vendor:publish --tag=storage} 使用。
     * <p>
     * 仅声明元数据，不依赖 artisan 模块；未引入 artisan 时该 Bean 无人消费，无副作用。
     *
     * @return 可发布配置
     */
    @Bean
    @ConditionalOnMissingBean
    public StoragePublishableConfig storagePublishableConfig() {
        return new StoragePublishableConfig();
    }

    /**
     * 内置数据库文件存储驱动，支持 {@code database} 驱动名。
     * 通过 {@code ApplicationContext} 解析数据源（默认主数据源，可指定独立数据源 Bean）。
     *
     * @return 驱动实例
     */
    @Bean
    @ConditionalOnMissingBean
    public DatabaseFilesystemDriver databaseFilesystemDriver() {
        return new DatabaseFilesystemDriver();
    }

    /**
     * 磁盘注册器：收集驱动、按配置注册磁盘、扫描 {@code @RegisterDisk} 注解。
     *
     * @param context    应用上下文
     * @param manager    存储管理器
     * @param properties 配置属性
     * @return 注册器实例
     */
    @Bean
    @ConditionalOnMissingBean
    public StorageRegistrar storageRegistrar(ApplicationContext context,
                                             StorageManager manager,
                                             StorageProperties properties) {
        return new StorageRegistrar(context, manager, properties);
    }
}
