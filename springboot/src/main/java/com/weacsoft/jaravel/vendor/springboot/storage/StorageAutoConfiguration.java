package com.weacsoft.jaravel.vendor.springboot.storage;

import com.weacsoft.jaravel.vendor.database.ConnectionManager;
import com.weacsoft.jaravel.vendor.storage.StorageManager;
import com.weacsoft.jaravel.vendor.storage.database.DatabaseFilesystemDriver;
import com.weacsoft.jaravel.vendor.storage.local.LocalFilesystemDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * 存储模块自动装配。
 * <p>
 * <b>职责分层</b>（storage / storage-database / springboot 三模块分工）：
 * <ul>
 *   <li><b>storage</b>（零 Spring 依赖）：{@code StorageManager} + {@code Filesystem} 契约
 *       + local/public 驱动 + {@code @RegisterDisk} 注解 + {@code StoragePublishableConfig} 发布声明</li>
 *   <li><b>storage-database</b>（零 Spring 依赖，走 database 模块连接）：
 *       {@code DatabaseFilesystem}/{@code DatabaseFilesystemDriver} + storage:table 命令</li>
 *   <li><b>springboot</b>：本类——全部 Spring 装配（Bean、@ConfigurationProperties、条件装配、
 *       注解扫描、artisan 集成、vendor:publish 注册）</li>
 * </ul>
 *
 * <h3>装配内容</h3>
 * <ul>
 *   <li>{@link StorageManager} — 存储管理器（多磁盘解析）</li>
 *   <li>{@link LocalFilesystemDriver} — 内置 local/public 驱动（声明 local/public 磁盘或缺省磁盘时装配）</li>
 *   <li>{@link DatabaseFilesystemDriver} — database 磁盘驱动（storage-database 模块，声明 database 磁盘时装配）</li>
 *   <li>{@link StorageRegistrar} — 注解与配置扫描注册器</li>
 * </ul>
 *
 * <h3>装配原则：用上了才注册</h3>
 * database 磁盘需要外部数据库，遵循「显式选用才装配」：只有
 * {@code jaravel.storage.disks.*.driver} 里出现了 {@code database}，对应的驱动 Bean 才会注册；
 * local 磁盘无外部资源，缺省装配保证开箱即用。
 *
 * <h3>关闭模块</h3>
 * <pre>
 * jaravel:
 *   storage:
 *     enabled: false
 * </pre>
 */
@AutoConfiguration
@ConditionalOnClass(StorageManager.class)
@EnableConfigurationProperties(StorageProperties.class)
@ConditionalOnProperty(prefix = "jaravel.storage", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StorageAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(StorageAutoConfiguration.class);

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
    @Conditional(OnLocalDiskDriverCondition.class)
    public LocalFilesystemDriver localFilesystemDriver() {
        return new LocalFilesystemDriver();
    }

    /**
     * 磁盘注册器：收集驱动、按配置注册磁盘、扫描 {@code @RegisterDisk} 注解
     * （P3：core 纯扫描器；扫描由下方 SmartInitializingSingleton 触发，
     * 保持原「所有单例就绪后扫描」时序）。
     *
     * @param manager    存储管理器
     * @param properties 配置属性
     * @return 注册器实例
     */
    @Bean
    @ConditionalOnMissingBean
    public StorageRegistrar storageRegistrar(StorageManager manager,
                                             StorageProperties properties) {
        return new StorageRegistrar(manager, properties);
    }

    /**
     * 磁盘注册器扫描触发。
     */
    @Bean
    public org.springframework.beans.factory.SmartInitializingSingleton storageRegistrarScanner(
            StorageRegistrar registrar) {
        return registrar::scan;
    }

    /**
     * 数据库磁盘驱动装配（storage-database 模块，走 database 模块连接）。
     *
     * <h3>装配条件：用上了才装配</h3>
     * 仅当<b>确实声明了</b> {@code driver: database} 的磁盘时才注册驱动 Bean，由
     * {@link OnDatabaseDiskDriverCondition} 直接读取 {@code jaravel.storage.disks.*.driver}
     * 判定。没用 database 磁盘的应用，这里完全不装配。
     *
     * <h3>数据源解析顺序</h3>
     * 优先取 database 模块 {@link ConnectionManager} 的连接注册表（{@code @RegisterConnection}
     * 声明的连接），找不到再回退 Spring 容器中的 {@code DataSource} Bean。
     * 磁盘驱动不与 Spring {@code DataSource} Bean 强绑定，也不会因
     * {@code @ConditionalOnBean} 的求值时序误判。
     * <p>
     * {@code @ConditionalOnClass(DatabaseFilesystemDriver.class)} 保留在内部配置类上，
     * 避免未引入 storage-database 模块时加载类抛出 {@code NoClassDefFoundError}。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(DatabaseFilesystemDriver.class)
    @Conditional(OnDatabaseDiskDriverCondition.class)
    static class DatabaseStorageConfiguration {

        /**
         * 数据库磁盘驱动 bean，持有惰性数据源解析器
         * （先 database 模块连接注册表，再 Spring 容器 {@code DataSource}）。
         *
         * @param context Spring 上下文，用于回退解析 {@code DataSource}
         * @return 数据库磁盘驱动
         */
        @Bean
        @ConditionalOnMissingBean(DatabaseFilesystemDriver.class)
        public DatabaseFilesystemDriver databaseFilesystemDriver(ApplicationContext context) {
            logger.info("[storage] 检测到 driver: database 的磁盘，注册数据库存储驱动工厂");
            return new DatabaseFilesystemDriver(() -> {
                DataSource ds = ConnectionManager.defaultRawDataSource();
                if (ds == null) {
                    ds = context.getBeanProvider(DataSource.class).getIfAvailable();
                }
                return ds;
            });
        }
    }
}
