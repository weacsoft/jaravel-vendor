package com.weacsoft.jaravel.vendor.cache.autoconfigure;

import com.weacsoft.jaravel.vendor.cache.CacheDriverFactory;
import com.weacsoft.jaravel.vendor.cache.CacheManager;
import com.weacsoft.jaravel.vendor.cache.CacheStore;
import com.weacsoft.jaravel.vendor.cache.driver.ArrayCacheDriverFactory;
import com.weacsoft.jaravel.vendor.cache.driver.DatabaseCacheDriverFactory;
import com.weacsoft.jaravel.vendor.cache.driver.FileCacheDriverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;

/**
 * 缓存自动装配，对齐 Laravel 缓存服务提供者。
 * <p>
 * 采用<b>工厂模式 + 手动装配</b>（对齐 Laravel {@code CacheManager}），支持三种 Store 注册方式：
 * <ul>
 *   <li><b>配置式</b>：{@code jaravel.cache.stores} 配置按需创建（工厂模式，用到才构建）</li>
 *   <li><b>注解声明式</b>：业务方在 Config 类中用 {@link com.weacsoft.jaravel.vendor.cache.RegisterCacheStore @RegisterCacheStore}
 *       注解方法，返回 {@link CacheStore}，注解 value 即 store name（不注册为 Spring Bean，避免 bean name 冲突）</li>
 *   <li><b>编程式</b>：通过 {@code CacheManager.addStore()} 手动注册</li>
 * </ul>
 * 注解声明优先于配置式（同名时覆盖）。
 *
 * <h3>内置驱动工厂</h3>
 * <ul>
 *   <li>{@code array} — 内存缓存（零外部依赖，始终注册）</li>
 *   <li>{@code file} — 文件缓存（零外部依赖，始终注册）</li>
 *   <li>{@code database} — 数据库缓存（<b>配置里用上了才注册</b>，见
 *       {@link OnDatabaseCacheStoreCondition}）</li>
 * </ul>
 * 第三方模块（如 redis-cache）只需将 {@link CacheDriverFactory} 实现注册为 Spring Bean，
 * 本类会自动收集并注册到 {@link CacheManager}。
 *
 * <h3>装配原则：用上了才注册</h3>
 * 需要外部资源的驱动（database、redis 等）一律遵循「显式选用才装配」：
 * 只有 {@code jaravel.cache.stores.*.driver} 里出现了该驱动名，对应的工厂才会被注册。
 * 这样「引入依赖」与「启用驱动」彻底解耦，未启用的驱动不会拖累启动，
 * 也不会在缺少外部服务时报错。
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
 * <h3>注解声明式示例</h3>
 * <pre>
 * &#64;Configuration
 * public class CacheConfig {
 *     // 额外 store：@RegisterCacheStore("file") → 注册为 store "file"
 *     // 不会注册为 Spring Bean，因此不会与其他 @Bean("file") 冲突
 *     &#64;RegisterCacheStore("file")
 *     public CacheStore fileStore(CacheProperties properties) {
 *         return new DefaultCacheStore(new FileCacheDriver(properties.getFileDir()),
 *                 properties.getPrefix());
 *     }
 *
 *     // 默认 store：标记 defaultStore = true，自动设为默认 store
 *     &#64;RegisterCacheStore(value = "array", defaultStore = true)
 *     public DefaultCacheStore arrayStore() {
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
     * 缓存管理器 bean：手动装配，收集驱动工厂 + 配置式创建。
     * <p>
     * 装配顺序：
     * <ol>
     *   <li>注册所有 {@link CacheDriverFactory} Bean（内置 + 第三方）</li>
     *   <li>根据 {@code jaravel.cache.stores} 配置按需创建 Store</li>
     * </ol>
     * 注解声明式 Store（{@link com.weacsoft.jaravel.vendor.cache.RegisterCacheStore @RegisterCacheStore}）
     * 由 {@link CacheStoreRegistrar} 在所有 Bean 初始化完成后扫描注册，覆盖同名配置 Store。
     *
     * <h3>注解声明式 Store</h3>
     * 业务方可在 Config 类中用 {@code @RegisterCacheStore} 注解方法，返回 {@link CacheStore}：
     * <pre>
     * // 额外 store：@RegisterCacheStore("file") → 注册为名为 "file" 的 store
     * // 不会注册为 Spring Bean，因此不会与其他 @Bean("file") 冲突
     * &#64;RegisterCacheStore("file")
     * public CacheStore fileStore(CacheProperties properties) {
     *     return new DefaultCacheStore(new FileCacheDriver(properties.getFileDir()),
     *             properties.getPrefix());
     * }
     *
     * // 默认 store：标记 defaultStore = true，自动设为默认 store
     * &#64;RegisterCacheStore(value = "array", defaultStore = true)
     * public DefaultCacheStore arrayStore() {
     *     return new DefaultCacheStore(new ArrayCacheDriver(), "jaravel");
     * }
     * </pre>
     * 注解声明优先于配置式（同名时覆盖）。default-store 由 {@code jaravel.cache.default-store} 决定，
     * 也可通过 {@code @RegisterCacheStore(defaultStore = true)} 注解标记覆盖。
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
        // 1. 注册所有驱动工厂
        for (CacheDriverFactory factory : factories) {
            manager.registerDriverFactory(factory);
            logger.info("[cache] 注册驱动工厂: {}", factory.getClass().getSimpleName());
        }
        // 2. 根据配置按需创建 Store
        manager.initFromConfig(properties);
        // 3. @RegisterCacheStore 注解声明的 Store 由 CacheStoreRegistrar 在所有 Bean 初始化后注册
        return manager;
    }

    /**
     * 注册 {@link CacheStoreRegistrar}，负责扫描 {@link com.weacsoft.jaravel.vendor.cache.RegisterCacheStore @RegisterCacheStore}
     * 注解方法并注册到 {@link CacheManager}。
     */
    @Bean
    @ConditionalOnMissingBean(CacheStoreRegistrar.class)
    public CacheStoreRegistrar cacheStoreRegistrar(ApplicationContext context, CacheManager cacheManager) {
        return new CacheStoreRegistrar(context, cacheManager);
    }

    /**
     * 声明 cache 模块的可发布配置类，供 {@code artisan vendor:publish --tag=cache} 使用。
     * <p>
     * 仅声明元数据，不依赖 artisan 模块；未引入 artisan 时该 Bean 无人消费，无副作用。
     */
    @Bean
    @ConditionalOnMissingBean(CachePublishableConfig.class)
    public CachePublishableConfig cachePublishableConfig() {
        return new CachePublishableConfig();
    }

    /**
     * 数据库缓存驱动工厂装配。
     *
     * <h3>装配条件：用上了才装配</h3>
     * 仅当<b>确实声明了</b> {@code driver: database} 的 store 时才注册工厂，由
     * {@link OnDatabaseCacheStoreCondition} 直接读取 {@code jaravel.cache.stores.*.driver}
     * 判定。没用数据库缓存的应用，这里完全不装配。
     *
     * <h3>不再使用 {@code @ConditionalOnBean(DataSource.class)}</h3>
     * 缓存驱动不应与 Spring 的 {@code DataSource} Bean 绑定。数据源在
     * {@link DatabaseCacheDriverFactory#create} 时才通过 {@link CacheDataSourceResolver}
     * 惰性解析，顺序为「先 jaravel database 模块的连接注册表 → 再 Spring 容器」。
     * <p>
     * {@code @ConditionalOnClass(JdbcTemplate.class)} 仍保留，
     * 避免缺少 {@code spring-jdbc} 时加载工厂类抛出 {@code NoClassDefFoundError}。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({DataSource.class, JdbcTemplate.class})
    @Conditional(OnDatabaseCacheStoreCondition.class)
    static class DatabaseCacheConfiguration {

        /**
         * 数据库缓存驱动工厂 bean，持有惰性数据源解析器。
         *
         * @param context Spring 上下文，用于回退解析 {@code DataSource}
         * @return 数据库缓存驱动工厂
         */
        @Bean
        @ConditionalOnMissingBean(DatabaseCacheDriverFactory.class)
        public DatabaseCacheDriverFactory databaseCacheDriverFactory(ApplicationContext context) {
            logger.info("[cache] 检测到 driver: database 的 store，注册数据库缓存驱动工厂");
            return new DatabaseCacheDriverFactory(new CacheDataSourceResolver(context));
        }
    }
}
