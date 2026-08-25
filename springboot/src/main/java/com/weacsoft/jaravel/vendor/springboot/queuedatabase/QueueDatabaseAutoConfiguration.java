package com.weacsoft.jaravel.vendor.springboot.queuedatabase;

import com.weacsoft.jaravel.vendor.core.publish.PublishableRegistry;
import com.weacsoft.jaravel.vendor.springboot.core.ContextBeanProvider;
import com.weacsoft.jaravel.vendor.core.queue.QueueDriver;
import com.weacsoft.jaravel.vendor.core.queue.RegisterQueueDriver;
import com.weacsoft.jaravel.vendor.core.queue.QueueDriverHolder;
import com.weacsoft.jaravel.vendor.core.queue.QueueProperties;
import com.weacsoft.jaravel.vendor.queue.database.DatabaseQueueDispatcher;
import com.weacsoft.jaravel.vendor.queue.database.DatabaseQueueDriver;
import com.weacsoft.jaravel.vendor.queue.database.DatabaseQueueWorker;
import com.weacsoft.jaravel.vendor.queue.database.QueueDatabaseProperties;
import com.weacsoft.jaravel.vendor.queue.database.QueueDatabasePublishableConfig;


import com.weacsoft.jaravel.vendor.event.QueueDispatcher;
import com.weacsoft.jaravel.vendor.redis.RedisManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

import javax.sql.DataSource;

/**
 * 队列自动装配（database 驱动 + 通用 worker / dispatcher）。
 * <p>
 * 通过 {@code jaravel.queue.driver} 选择驱动（vendor 模块组统一原则：<b>安装 ≠ 启用，用上了才注册</b>）：
 * <ul>
 *   <li>{@code sync}（默认）：内存队列，不创建任何 QueueDriver Bean，EventDispatcher 使用 QueueManager 内存队列</li>
 *   <li>{@code database}：基于 {@link DataSource} 的 {@link DatabaseQueueDriver}（由
 *       {@link OnDatabaseQueueDriverCondition} 条件装配，仅显式 {@code driver=database} 时启用）</li>
 *   <li>{@code redis}：基于 {@link RedisManager} 的 {@link RedisQueueDriver}（由
 *       {@link RedisQueueAutoConfiguration} 注册，先于本类处理，仅显式 {@code driver=redis} 时启用）</li>
 * </ul>
 * 不再存在「redis 不可用回退 database」逻辑：redis 与 database 各自独立按需装配，
 * sync 始终作为无驱动时的内存兜底。
 * <p>
 * <b>sync 模式</b>：当 {@code driver=sync}（默认）时，不创建任何 QueueDriver Bean，
 * EventDispatcher 自动降级为内存队列（{@link com.weacsoft.jaravel.vendor.event.QueueManager}），
 * 不会创建数据库表，无需额外配置。
 *
 * <p>配置项：
 * <pre>
 * jaravel:
 *   queue:
 *     driver: sync                     # sync（默认）| database | redis
 *     redis-connection: ""            # redis 驱动连接名，空 = 默认连接
 *     failed-job-retention-days: 7    # 失败任务保留天数
 *     database:
 *       table: jobs
 *       retry-after: 1800
 *       auto-start: false             # 默认不自动启动 worker
 *       max-attempts: 3
 *       retry-delay-ms: 5000
 *       poll-interval-ms: 1000
 *       worker-threads: 1
 *       queues: default
 * </pre>
 *
 * <p><b>注意</b>：worker 默认不自动启动，由后台 Bean {@link DatabaseQueueWorker}
 * 承担消费。生产环境设置 {@code jaravel.queue.database.auto-start=true} 随应用启动 worker，
 * 或业务方自行注入 {@link DatabaseQueueWorker} 并调用其启动方法。
 * 框架<b>不提供</b> {@code artisan queue:work} 命令（与 Laravel 不同）。
 * </p>
 * <b>建表</b>：使用 database 驱动前需先执行 {@code artisan queue:table} 创建 jobs/failed_jobs 表，
 * 或手动调用 {@link DatabaseQueueDriver#createTable()}。
 */
@AutoConfiguration
@ConditionalOnClass({QueueDriver.class, QueueDatabaseProperties.class, DatabaseQueueDriver.class})
public class QueueDatabaseAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(QueueDatabaseAutoConfiguration.class);

    /**
     * 队列全局配置 Bean，绑定 {@code jaravel.queue.*} 配置。
     * <p>
     * P3 起 {@link QueueProperties} 为纯 POJO（保留在 core，FQCN 稳定，
     * publish 模板代码引用不受影响），经此 {@code @Bean @ConfigurationProperties} 完成绑定，
     * 字段与默认值与 P3 前 {@code @EnableConfigurationProperties} 方式完全一致。
     *
     * @return 队列全局配置
     */
    @Bean
    @ConfigurationProperties(prefix = "jaravel.queue")
    @ConditionalOnMissingBean
    public QueueProperties queueProperties() {
        return new QueueProperties();
    }

    /**
     * 数据库队列配置 Bean，绑定 {@code jaravel.queue.database.*} 配置。
     * <p>
     * {@link QueueDatabaseProperties} 为纯 POJO（保留在 queue-database 模块，
     * {@code DatabaseQueueDriver}/{@code DatabaseQueueWorker} 直接消费），
     * 经此 {@code @Bean @ConfigurationProperties} 完成绑定。
     *
     * @return 数据库队列配置
     */
    @Bean
    @ConfigurationProperties(prefix = "jaravel.queue.database")
    @ConditionalOnMissingBean
    public QueueDatabaseProperties queueDatabaseProperties() {
        return new QueueDatabaseProperties();
    }

    /**
     * 数据库队列驱动 bean。
     * <p>
     * 仅当 driver 不为 sync 且尚无 {@link QueueDriver} bean 且存在 {@link DataSource} 时创建。
     */
    @Bean
    @ConditionalOnMissingBean(QueueDriver.class)
    @ConditionalOnBean(DataSource.class)
    @Conditional(OnDatabaseQueueDriverCondition.class)
    public DatabaseQueueDriver databaseQueueDriver(DataSource dataSource,
                                                   QueueDatabaseProperties dbProps,
                                                   QueueProperties props) {
        logger.info("[queue] 使用 database 驱动: table={}", dbProps.getTable());
        return new DatabaseQueueDriver(dataSource, dbProps.getTable(),
                dbProps.getRetryAfter(), props.getFailedJobRetentionDays());
    }

    /**
     * 队列驱动持有者。
     * <p>
     * 声明为 {@link QueueDriverHolder} 类型而非 {@link QueueDriver}，
     * 避免被 {@code @ConditionalOnMissingBean(QueueDriver.class)} 误判，
     * 从而不影响 database / redis 驱动的自动装配条件。
     */
    @Bean
    @ConditionalOnMissingBean
    public QueueDriverHolder queueDriverHolder() {
        return new QueueDriverHolder();
    }

    /**
     * 注册 {@link QueueDriverRegistrar}，扫描 {@code @RegisterQueueDriver}
     * 注解方法并解析出全局唯一的队列驱动（含唯一性校验与 sync 回退）。
     */
    @Bean
    @ConditionalOnMissingBean
    public QueueDriverRegistrar queueDriverRegistrar(QueueDriverHolder holder) {
        return new QueueDriverRegistrar(holder);
    }

    /**
     * 队列驱动注册器扫描触发：所有单例初始化完成后执行 {@code @RegisterQueueDriver}
     * 扫描（P3 起扫描与 Spring 生命周期解耦，由宿主按原有时序显式触发，语义不变）。
     */
    @Bean
    public SmartInitializingSingleton queueDriverRegistrarScanner(QueueDriverRegistrar registrar) {
        return registrar::scan;
    }

    /**
     * 队列工作线程 bean，适用于任何 {@link QueueDriver} 实现（database / redis）。
     * <p>
     * 仅当 {@code auto-start=true} 时自动启动。
     */
    @Bean
    @ConditionalOnMissingBean(DatabaseQueueWorker.class)
    @ConditionalOnBean(QueueDriver.class)
    public DatabaseQueueWorker databaseQueueWorker(QueueDriver driver,
                                                   ApplicationContext applicationContext,
                                                   QueueDatabaseProperties properties) {
        DatabaseQueueWorker worker = new DatabaseQueueWorker(
                driver,
                new ContextBeanProvider(applicationContext),
                properties.getQueues(),
                properties.getMaxAttempts(),
                properties.getRetryDelayMs(),
                properties.getPollIntervalMs(),
                properties.getWorkerThreads()
        );
        if (properties.isAutoStart()) {
            worker.start();
        }
        return worker;
    }

    /**
     * 持久化队列分发器 bean，桥接 event 模块。
     * <p>
     * 实现 {@link QueueDispatcher}，由 {@link com.weacsoft.jaravel.vendor.event.EventDispatcher}
     * 通过 {@code ObjectProvider<QueueDispatcher>} 自动注入，将 {@code ShouldQueue} 事件分发到队列。
     */
    @Bean
    @ConditionalOnMissingBean(QueueDispatcher.class)
    @ConditionalOnBean(QueueDriver.class)
    public DatabaseQueueDispatcher databaseQueueDispatcher(QueueDriver driver,
                                                           ApplicationContext applicationContext) {
        logger.info("[queue] 注册 DatabaseQueueDispatcher，桥接 event 模块到队列驱动");
        return new DatabaseQueueDispatcher(driver, new ContextBeanProvider(applicationContext));
    }

    static {
        PublishableRegistry.register(new QueueDatabasePublishableConfig());
    }
}
