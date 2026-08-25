package com.weacsoft.jaravel.vendor.springboot.cache;

import com.weacsoft.jaravel.vendor.cache.CacheDriverFactory;
import com.weacsoft.jaravel.vendor.cache.CacheManager;
import com.weacsoft.jaravel.vendor.cache.RegisterCacheStore;
import com.weacsoft.jaravel.vendor.cache.autoconfigure.CachePublishableConfig;
import com.weacsoft.jaravel.vendor.cache.driver.ArrayCacheDriverFactory;
import com.weacsoft.jaravel.vendor.cache.driver.FileCacheDriverFactory;
import com.weacsoft.jaravel.vendor.cache.database.DatabaseCacheDriverFactory;
import com.weacsoft.jaravel.vendor.database.ConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.List;

/**
 * 缓存自动装配，对齐 Laravel 缓存服务提供者。
 * <p>
 * <b>职责分层</b>（cache / cache-database / springboot 三模块分工）：
 * <ul>
 *   <li><b>cache</b>（零 Spring 依赖）：{@code Cache}/ {@code CacheStore}/ {@code CacheManager}
 *       + array / file 驱动 + 发布配置声明</li>
 *   <li><b>cache-database</b>（零 Spring 依赖，走 database 模块连接）：
 *       {@code DatabaseCacheDriver}/{@code DatabaseCacheDriverFactory} + cache:table 命令</li>
 *   <li><b>springboot</b>：本类——全部 Spring 装配（Bean、@ConfigurationProperties、条件装配、
 *       注解扫描、artisan 集成、vendor:publish 注册）</li>
 * </ul>
 *
 * <h3>三种 Store 注册方式</h3>
 * <ul>
 *   <li><b>配置式</b>：{@code jaravel.cache.stores} 配置按需创建（工厂模式，用到才构建）</li>
 *   <li><b>注解声明式</b>：业务方在 Config 类中用 {@link RegisterCacheStore @RegisterCacheStore}
 *       注解方法，返回 {@com.weacsoft.jaravel.vendor.cache.CacheStore}，注解 value 即 store name
 *       （不注册为 Spring Bean，避免 bean name 冲突）</li>
 *   <li><b>编程式</b>：通过 {@code CacheManager.addStore()} 手动注册</li>
 * </ul>
 * 注解声明优先于配置式（同名时覆盖）。
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
     * 注解声明式 Store（{@link RegisterCacheStore @RegisterCacheStore}）
     * 由 {@link CacheStoreRegistrar} 在所有 Bean 初始化完成后扫描注册，覆盖同名配置 Store。
     *
     * @param properties 缓存配置（Spring 绑定属性）
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
        // 2. 根据配置按需创建 Store（映射为 cache 模块的纯 Java 配置对象）
        manager.initFromConfig(properties.toCacheConfig());
        // 3. @RegisterCacheStore 注解声明的 Store 由 CacheStoreRegistrar 在所有 Bean 初始化后注册
        return manager;
    }

    /**
     * 注册 {@link CacheStoreRegistrar}，负责扫描 {@link RegisterCacheStore @RegisterCacheStore}
     * 注解方法并注册到 {@link CacheManager}（P3：core 纯扫描器；扫描由下方
     * SmartInitializingSingleton 触发，保持原「所有单例就绪后扫描」时序）。
     */
    @Bean
    @ConditionalOnMissingBean(CacheStoreRegistrar.class)
    public CacheStoreRegistrar cacheStoreRegistrar(CacheManager cacheManager) {
        return new CacheStoreRegistrar(cacheManager);
    }

    /**
     * 缓存 store 注册器扫描触发。
     */
    @Bean
    public SmartInitializingSingleton cacheStoreRegistrarScanner(CacheStoreRegistrar registrar) {
        return registrar::scan;
    }

    static {
        com.weacsoft.jaravel.vendor.core.publish.PublishableRegistry.register(new CachePublishableConfig());
    }

    /**
     * 数据库缓存驱动工厂装配（cache-database 模块，走 database 模块连接）。
     *
     * <h3>装配条件：用上了才装配</h3>
     * 仅当<b>确实声明了</b> {@code driver: database} 的 store 时才注册工厂，由
     * {@link OnDatabaseCacheStoreCondition} 直接读取 {@code jaravel.cache.stores.*.driver}
     * 判定。没用数据库缓存的应用，这里完全不装配。
     *
     * <h3>数据源解析顺序</h3>
     * 优先取 database 模块 {@link ConnectionManager} 的连接注册表（{@code @RegisterConnection}
     * 声明的连接），找不到再回退 Spring 容器中的 {@code DataSource} Bean。
     * 缓存驱动不与 Spring {@code DataSource} Bean 强绑定，也不会因
     * {@code @ConditionalOnBean} 的求值时序误判。
     * <p>
     * {@code @ConditionalOnClass(DatabaseCacheDriverFactory.class)} 保留在内部配置类上，
     * 避免未引入 cache-database 模块时加载类抛出 {@code NoClassDefFoundError}。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(DatabaseCacheDriverFactory.class)
    @Conditional(OnDatabaseCacheStoreCondition.class)
    static class DatabaseCacheConfiguration {

        /**
         * 数据库缓存驱动工厂 bean，持有惰性数据源解析器
         * （先 database 模块连接注册表，再 Spring 容器 {@code DataSource}）。
         *
         * @param context Spring 上下文，用于回退解析 {@code DataSource}
         * @return 数据库缓存驱动工厂
         */
        @Bean
        @ConditionalOnMissingBean(DatabaseCacheDriverFactory.class)
        public DatabaseCacheDriverFactory databaseCacheDriverFactory(ApplicationContext context) {
            logger.info("[cache] 检测到 driver: database 的 store，注册数据库缓存驱动工厂");
            return new DatabaseCacheDriverFactory(() -> {
                DataSource ds = ConnectionManager.defaultRawDataSource();
                if (ds == null) {
                    ds = context.getBeanProvider(DataSource.class).getIfAvailable();
                }
                return ds;
            });
        }
    }
}
