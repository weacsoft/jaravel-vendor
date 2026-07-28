package com.weacsoft.jaravel.vendor.cache.autoconfigure;

import com.weacsoft.jaravel.vendor.cache.CacheDriverFactory;
import com.weacsoft.jaravel.vendor.cache.CacheManager;
import com.weacsoft.jaravel.vendor.cache.CacheStore;
import com.weacsoft.jaravel.vendor.cache.driver.ArrayCacheDriverFactory;
import com.weacsoft.jaravel.vendor.cache.driver.DatabaseCacheDriverFactory;
import com.weacsoft.jaravel.vendor.cache.driver.FileCacheDriverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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
import java.util.Map;

/**
 * 缓存自动装配，对齐 Laravel 缓存服务提供者。
 * <p>
 * 采用<b>工厂模式 + 手动装配</b>（对齐 Laravel {@code CacheManager}），支持三种 Store 注册方式：
 * <ul>
 *   <li><b>配置式</b>：{@code jaravel.cache.stores} 配置按需创建（工厂模式，用到才构建）</li>
 *   <li><b>Bean 声明式</b>：业务方在 Config 类中 {@code @Bean("name")} 声明 CacheStore，bean name 即 store name</li>
 *   <li><b>编程式</b>：通过 {@code CacheManager.addStore()} 手动注册</li>
 * </ul>
 * Bean 声明优先于配置式（同名时覆盖）。
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
 * <h3>配置式示例</h3>
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
 *
 * <h3>Bean 声明式示例</h3>
 * <pre>
 * &#64;Configuration
 * public class CacheConfig {
 *     // 额外 store：@Bean("file") 返回 CacheStore → 注册为 store "file"
 *     &#64;Bean("file")
 *     public CacheStore fileStore() {
 *         return new DefaultCacheStore(new FileCacheDriver("/tmp/cache"), "jaravel");
 *     }
 *
 *     // 默认 store：@Bean 返回 DefaultCacheStore，方法名匹配 default-store
 *     // default-store: array → 方法名 "array" 即 bean name，自动成为默认 store
 *     &#64;Bean
 *     public DefaultCacheStore array() {
 *         return new DefaultCacheStore(new ArrayCacheDriver(), "jaravel");
 *     }
 * }
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
     * 缓存管理器 bean：手动装配，收集驱动工厂 + 配置式创建 + Bean 声明式覆盖。
     * <p>
     * 装配顺序：
     * <ol>
     *   <li>注册所有 {@link CacheDriverFactory} Bean（内置 + 第三方）</li>
     *   <li>根据 {@code jaravel.cache.stores} 配置按需创建 Store</li>
     *   <li>收集用户声明的 {@link CacheStore} Bean，按 bean name 覆盖同名配置 Store</li>
     * </ol>
     *
     * <h3>Bean 声明式 Store（编程式覆盖）</h3>
     * 业务方可在 Config 类中用 {@code @Bean} 声明 CacheStore，bean name 即 store name：
     * <pre>
     * // 额外 store：@Bean("name") 返回 CacheStore → 注册为名为 "name" 的 store
     * &#64;Bean("file")
     * public CacheStore fileStore() {
     *     return new DefaultCacheStore(new FileCacheDriver("/tmp/cache"), "jaravel");
     * }
     *
     * // 默认 store：@Bean 返回 DefaultCacheStore，方法名匹配 default-store 配置
     * // default-store: array → 方法名 "array" 即 bean name "array" 自动成为默认
     * &#64;Bean
     * public DefaultCacheStore array() {
     *     return new DefaultCacheStore(new ArrayCacheDriver(), "jaravel");
     * }
     * </pre>
     * Bean 声明优先于配置式（同名时覆盖）。default-store 由 {@code jaravel.cache.default-store} 决定，
     * bean name 匹配该值的 Store 即为默认 Store。
     *
     * @param properties        缓存配置
     * @param factories         所有 {@link CacheDriverFactory} Bean（内置 + 第三方）
     * @param storeBeansProvider 用户声明的 CacheStore Bean（可选，可能为空）
     * @return 缓存管理器
     */
    @Bean
    @ConditionalOnMissingBean
    public CacheManager cacheManager(CacheProperties properties,
                                     List<CacheDriverFactory> factories,
                                     ObjectProvider<Map<String, CacheStore>> storeBeansProvider) {
        CacheManager manager = new CacheManager();
        // 1. 注册所有驱动工厂
        for (CacheDriverFactory factory : factories) {
            manager.registerDriverFactory(factory);
            logger.info("[cache] 注册驱动工厂: {}", factory.getClass().getSimpleName());
        }
        // 2. 根据配置按需创建 Store
        manager.initFromConfig(properties);
        // 3. 收集用户声明的 CacheStore Bean，覆盖同名配置 Store
        Map<String, CacheStore> storeBeans = storeBeansProvider.getIfAvailable(Map::of);
        for (Map.Entry<String, CacheStore> entry : storeBeans.entrySet()) {
            manager.addStore(entry.getKey(), entry.getValue());
            logger.info("[cache] 注册 Bean 声明的 store: name={}, type={}",
                    entry.getKey(), entry.getValue().getClass().getSimpleName());
        }
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
