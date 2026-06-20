package com.weacsoft.jaravel.vendor.cache;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 缓存自动装配，对齐 Laravel 缓存服务提供者。
 * <p>
 * 注册 {@link ArrayCacheDriver}、{@link FileCacheDriver} 两个底层驱动 bean，
 * 并构造 {@link CacheManager}：将 {@code array}、{@code file} 两个 store（均以
 * {@link CacheProperties#getPrefix()} 作为键前缀）注册进去，再设置默认 store。
 * 所有 bean 均以 {@code @ConditionalOnMissingBean} 暴露，便于业务方覆盖。
 */
@AutoConfiguration
@ConditionalOnClass(CacheManager.class)
@EnableConfigurationProperties(CacheProperties.class)
public class CacheAutoConfiguration {

    /**
     * 内存缓存驱动 bean。
     */
    @Bean
    @ConditionalOnMissingBean
    public ArrayCacheDriver arrayCacheDriver() {
        return new ArrayCacheDriver();
    }

    /**
     * 文件缓存驱动 bean，目录取自 {@link CacheProperties#getFileDir()}，为空则用默认临时目录。
     */
    @Bean
    @ConditionalOnMissingBean
    public FileCacheDriver fileCacheDriver(CacheProperties properties) {
        String dir = properties.getFileDir();
        return (dir == null || dir.isEmpty()) ? new FileCacheDriver() : new FileCacheDriver(dir);
    }

    /**
     * 缓存管理器 bean：注册 array / file 两个 store 并设置默认 store。
     */
    @Bean
    @ConditionalOnMissingBean
    public CacheManager cacheManager(CacheProperties properties,
                                     ArrayCacheDriver arrayCacheDriver,
                                     FileCacheDriver fileCacheDriver) {
        CacheManager manager = new CacheManager();
        manager.addStore("array", new DefaultCacheStore(arrayCacheDriver, properties.getPrefix()));
        manager.addStore("file", new DefaultCacheStore(fileCacheDriver, properties.getPrefix()));
        manager.setDefaultStore(properties.getDefaultStore());
        return manager;
    }
}
