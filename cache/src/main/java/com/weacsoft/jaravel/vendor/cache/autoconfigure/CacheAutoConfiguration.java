package com.weacsoft.jaravel.vendor.cache.autoconfigure;

import com.weacsoft.jaravel.vendor.cache.CacheManager;
import com.weacsoft.jaravel.vendor.cache.CacheStore;
import com.weacsoft.jaravel.vendor.cache.driver.ArrayCacheDriver;
import com.weacsoft.jaravel.vendor.cache.driver.DatabaseCacheDriver;
import com.weacsoft.jaravel.vendor.cache.driver.FileCacheDriver;
import com.weacsoft.jaravel.vendor.cache.store.DefaultCacheStore;
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
import java.util.Map;

/**
 * 缓存自动装配，对齐 Laravel 缓存服务提供者。
 * <p>
 * 采用 {@code Map<String, CacheStore>} 自动收集模式（对齐 auth 模块的 UserProvider 自动收集）：
 * <ul>
 *   <li>内置 {@code @Bean("array")}、{@code @Bean("file")} 两个 CacheStore</li>
 *   <li>DatabaseCacheConfiguration 提供 {@code @Bean("database")} CacheStore（条件加载）</li>
 *   <li>第三方模块（如 redis-cache）只需 {@code @Bean("redis")} 声明 CacheStore 即可自动注册</li>
 * </ul>
 * {@code CacheManager} 通过 {@code Map<String, CacheStore>} 注入所有 store，bean name 即 store name。
 * <p>
 * 所有 bean 均以 {@code @ConditionalOnMissingBean} 暴露，便于业务方覆盖。
 *
 * <h3>编程式注册</h3>
 * 业务方可在 Config 类中用 {@code @Bean} 声明自定义 CacheStore：
 * <pre>
 * &#64;Bean("myStore")
 * public CacheStore myStore() {
 *     return new DefaultCacheStore(new MyCacheDriver(), "myapp");
 * }
 * </pre>
 * {@code CacheManager} 会自动收集并通过 {@code store("myStore")} 访问。
 */
@AutoConfiguration
@ConditionalOnClass(CacheManager.class)
@AutoConfigureAfter(name = "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")
@EnableConfigurationProperties(CacheProperties.class)
public class CacheAutoConfiguration {

    /**
     * 内存缓存驱动 bean（保留供直接注入使用）。
     */
    @Bean
    @ConditionalOnMissingBean
    public ArrayCacheDriver arrayCacheDriver() {
        return new ArrayCacheDriver();
    }

    /**
     * 文件缓存驱动 bean（保留供直接注入使用），目录取自 {@link CacheProperties#getFileDir()}。
     */
    @Bean
    @ConditionalOnMissingBean
    public FileCacheDriver fileCacheDriver(CacheProperties properties) {
        String dir = properties.getFileDir();
        return (dir == null || dir.isEmpty()) ? new FileCacheDriver() : new FileCacheDriver(dir);
    }

    /**
     * 内存缓存 store（bean name "array" 即 store name）。
     */
    @Bean("array")
    @ConditionalOnMissingBean(name = "array")
    public CacheStore arrayCacheStore(ArrayCacheDriver arrayCacheDriver, CacheProperties properties) {
        return new DefaultCacheStore(arrayCacheDriver, properties.getPrefix());
    }

    /**
     * 文件缓存 store（bean name "file" 即 store name）。
     */
    @Bean("file")
    @ConditionalOnMissingBean(name = "file")
    public CacheStore fileCacheStore(FileCacheDriver fileCacheDriver, CacheProperties properties) {
        return new DefaultCacheStore(fileCacheDriver, properties.getPrefix());
    }

    /**
     * 缓存管理器 bean：通过 {@code Map<String, CacheStore>} 自动收集所有 store。
     * <p>
     * bean name 即 store name，第三方模块只需 {@code @Bean("redis")} 声明 CacheStore 即可自动注册。
     *
     * @param properties 缓存配置
     * @param stores     所有 CacheStore bean（name -> store）
     * @return 缓存管理器
     */
    @Bean
    @ConditionalOnMissingBean
    public CacheManager cacheManager(CacheProperties properties,
                                     Map<String, CacheStore> stores) {
        CacheManager manager = new CacheManager();
        stores.forEach(manager::addStore);
        manager.setDefaultStore(properties.getDefaultStore());
        return manager;
    }

    /**
     * 数据库缓存驱动装配：依赖可选的 {@code spring-jdbc}。
     * <p>
     * 独立为内部 {@code @Configuration} 类并配合 {@code @ConditionalOnClass(JdbcTemplate.class)}，
     * 使得仅使用 array / file 驱动的应用不会因加载 {@link DatabaseCacheDriver} 而抛出 {@code NoClassDefFoundError}。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({DataSource.class, JdbcTemplate.class})
    @ConditionalOnBean(DataSource.class)
    static class DatabaseCacheConfiguration {

        /**
         * 数据库缓存驱动 bean，表名取自 {@link CacheProperties#getDatabaseTable()}。
         */
        @Bean
        @ConditionalOnMissingBean
        public DatabaseCacheDriver databaseCacheDriver(DataSource dataSource, CacheProperties properties) {
            return new DatabaseCacheDriver(dataSource, properties.getDatabaseTable());
        }

        /**
         * 数据库缓存 store（bean name "database" 即 store name）。
         * <p>
         * 由 {@code CacheManager} 通过 {@code Map<String, CacheStore>} 自动收集，无需手动 {@code addStore}。
         */
        @Bean("database")
        @ConditionalOnMissingBean(name = "database")
        public CacheStore databaseCacheStore(DatabaseCacheDriver databaseCacheDriver, CacheProperties properties) {
            return new DefaultCacheStore(databaseCacheDriver, properties.getPrefix());
        }
    }
}
