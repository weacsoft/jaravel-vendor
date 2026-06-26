package com.weacsoft.jaravel.vendor.cache;

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

/**
 * 缓存自动装配，对齐 Laravel 缓存服务提供者。
 * <p>
 * 注册 {@link ArrayCacheDriver}、{@link FileCacheDriver} 两个底层驱动 bean，
 * 并构造 {@link CacheManager}：将 {@code array}、{@code file} 两个 store（均以
 * {@link CacheProperties#getPrefix()} 作为键前缀）注册进去，再设置默认 store。
 * 所有 bean 均以 {@code @ConditionalOnMissingBean} 暴露，便于业务方覆盖。
 * <p>
 * {@link DatabaseCacheDriver} 依赖可选的 {@code spring-jdbc}，因此独立到内部
 * {@link DatabaseCacheConfiguration} 装配：仅当 classpath 存在 {@link DataSource} /
 * {@link JdbcTemplate} 且容器中已注册 {@code DataSource} bean 时生效，并将
 * {@code database} store 注册到 {@link CacheManager}。使用 {@code @AutoConfigureAfter}
 * 确保 {@code DataSource} 先于本配置就绪。
 */
@AutoConfiguration
@ConditionalOnClass(CacheManager.class)
@AutoConfigureAfter(name = "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")
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
     * <p>
     * {@code database} store 由 {@link DatabaseCacheConfiguration} 在
     * {@link DatabaseCacheDriver} bean 存在时注册。
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

    /**
     * 数据库缓存驱动装配：依赖可选的 {@code spring-jdbc}。
     * <p>
     * 独立为内部 {@code @Configuration} 类并配合 {@code @ConditionalOnClass(JdbcTemplate.class)}，
     * 使得仅使用 array / file 驱动（未引入 {@code spring-jdbc}）的应用不会因加载
     * {@link DatabaseCacheDriver}（其引用 {@link JdbcTemplate}）而抛出 {@code NoClassDefFoundError}。
     * 仅当容器中存在 {@link DataSource} bean 时才创建驱动与 store。
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
         * 当 {@link DatabaseCacheDriver} bean 存在时，将 {@code database} store 注册到
         * {@link CacheManager}（以 {@link CacheProperties#getPrefix()} 作为键前缀），
         * 同时以 bean 形式暴露该 store，便于业务方直接注入。
         */
        @Bean
        public DefaultCacheStore databaseCacheStore(CacheManager cacheManager,
                                                    DatabaseCacheDriver databaseCacheDriver,
                                                    CacheProperties properties) {
            DefaultCacheStore store = new DefaultCacheStore(databaseCacheDriver, properties.getPrefix());
            cacheManager.addStore("database", store);
            return store;
        }
    }
}
