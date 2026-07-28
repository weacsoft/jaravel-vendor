package com.weacsoft.jaravel.vendor.cache.autoconfigure;

import com.weacsoft.jaravel.vendor.cache.CacheDriverFactory;
import com.weacsoft.jaravel.vendor.cache.CacheManager;
import com.weacsoft.jaravel.vendor.cache.driver.ArrayCacheDriverFactory;
import com.weacsoft.jaravel.vendor.cache.driver.DatabaseCacheDriverFactory;
import com.weacsoft.jaravel.vendor.cache.driver.FileCacheDriverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;

/**
 * 缓存自动装配，对齐 Laravel 缓存服务提供者。
 * <p>
 * 采用<b>工厂模式 + 手动装配</b>（对齐 Laravel {@code CacheManager}）：
 * <ul>
 *   <li>注册驱动工厂（{@link CacheDriverFactory}）而非直接创建驱动/Store Bean</li>
 *   <li>{@link CacheManager} 启动时根据 {@code jaravel.cache.stores} 配置按需创建 Store</li>
 *   <li>只有配置在 stores 中的 Store 才会被创建，驱动实例在创建 Store 时才实例化</li>
 *   <li>stores 为空时只创建 default-store 对应的默认 Store</li>
 * </ul>
 *
 * <h3>内置驱动工厂</h3>
 * <ul>
 *   <li>{@code array} — 内存缓存（始终注册）</li>
 *   <li>{@code file} — 文件缓存（始终注册）</li>
 *   <li>{@code database} — 数据库缓存（DataSource 存在时条件注册）</li>
 * </ul>
 * 第三方模块（如 redis-cache）只需将 {@link CacheDriverFactory} 实现注册为 Spring Bean，
 * 本类会自动收集并注册到 {@link CacheManager}。
 *
 * <h3>配置示例</h3>
 * <pre>
 * jaravel:
 *   cache:
 *     default-store: array
 *     stores:
 *       array:
 *         driver: array
 *       file:
 *         driver: file
 *         dir: /tmp/jaravel-cache
 * </pre>
 */
@AutoConfiguration
@ConditionalOnClass(CacheManager.class)
@AutoConfigureAfter(name = "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")
@EnableConfigurationProperties(CacheProperties.class)
public class CacheAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(CacheAutoConfiguration.class);

    /**
     * 内存缓存驱动工厂（始终注册）。
     */
    @Bean
    @ConditionalOnMissingBean(ArrayCacheDriverFactory.class)
    public ArrayCacheDriverFactory arrayCacheDriverFactory() {
        return new ArrayCacheDriverFactory();
    }

    /**
     * 文件缓存驱动工厂（始终注册）。
     */
    @Bean
    @ConditionalOnMissingBean(FileCacheDriverFactory.class)
    public FileCacheDriverFactory fileCacheDriverFactory() {
        return new FileCacheDriverFactory();
    }

    /**
     * 缓存管理器 bean：手动装配，收集所有驱动工厂并根据配置按需创建 Store。
     * <p>
     * 不再使用 {@code Map<String, CacheStore>} 自动收集所有 Store Bean，
     * 而是根据 {@code CacheProperties.stores} 配置按需创建。
     *
     * @param properties 缓存配置
     * @param factories  所有 {@link CacheDriverFactory} Bean（内置 + 第三方）
     * @return 缓存管理器
     */
    @Bean
    @ConditionalOnMissingBean
    public CacheManager cacheManager(CacheProperties properties,
                                     List<CacheDriverFactory> factories) {
        CacheManager manager = new CacheManager();
        // 注册所有驱动工厂
        for (CacheDriverFactory factory : factories) {
            manager.registerDriverFactory(factory);
            logger.info("[cache] 注册驱动工厂: {}", factory.getClass().getSimpleName());
        }
        // 根据配置按需创建 Store
        manager.initFromConfig(properties);
        return manager;
    }

    /**
     * 数据库缓存驱动工厂装配：依赖可选的 {@code spring-jdbc}。
     * <p>
     * 独立为内部 {@code @Configuration} 类并配合 {@code @ConditionalOnClass(JdbcTemplate.class)}，
     * 使得仅使用 array / file 驱动的应用不会因加载 {@link DatabaseCacheDriverFactory} 而抛出
     * {@code NoClassDefFoundError}。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({DataSource.class, JdbcTemplate.class})
    @ConditionalOnBean(DataSource.class)
    static class DatabaseCacheConfiguration {

        /**
         * 数据库缓存驱动工厂 bean，仅当 DataSource 存在时注册。
         */
        @Bean
        @ConditionalOnMissingBean(DatabaseCacheDriverFactory.class)
        public DatabaseCacheDriverFactory databaseCacheDriverFactory(DataSource dataSource) {
            return new DatabaseCacheDriverFactory(dataSource);
        }
    }
}
