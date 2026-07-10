package com.weacsoft.jaravel.vendor.modelcache;

import com.weacsoft.jaravel.vendor.cache.CacheManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 模型缓存自动装配。
 * <p>
 * 仅当 classpath 存在 {@link CacheManager} 与 {@link ModelCacheService}，且容器中已存在
 * {@link CacheManager} Bean 时生效（即 cache 模块已装配）。注册 {@link ModelCacheService}
 * Bean，从 {@link CacheManager} 按配置的 store 名称解析缓存仓库。
 * <p>
 * 通过 {@code @AutoConfigureAfter} 确保在 cache 自动装配之后执行，避免
 * {@link CacheManager} 尚未就绪。所有 Bean 均带 {@code @ConditionalOnMissingBean}，
 * 便于业务方覆盖。
 * <p>
 * 本模块为<b>可选模块</b>：不引入 model-cache 依赖时，本自动装配不会被加载，
 * 不影响其他模块。
 */
@AutoConfiguration
@ConditionalOnClass({CacheManager.class, ModelCacheService.class})
@AutoConfigureAfter(name = "com.weacsoft.jaravel.vendor.cache.autoconfigure.CacheAutoConfiguration")
@EnableConfigurationProperties(ModelCacheProperties.class)
public class ModelCacheAutoConfiguration {

    /**
     * 注册模型缓存服务 Bean。
     * <p>
     * 需容器中存在 {@link CacheManager} Bean（由 cache 模块装配）。store 在运行时按
     * {@link ModelCacheProperties#getStore()} 从 {@link CacheManager} 解析，未注册则抛出
     * {@link IllegalStateException}。
     *
     * @param cacheManager 缓存管理器
     * @param properties   模型缓存配置
     * @return 模型缓存服务实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(CacheManager.class)
    public ModelCacheService modelCacheService(CacheManager cacheManager,
                                               ModelCacheProperties properties) {
        return new ModelCacheService(cacheManager, properties);
    }
}
