package com.weacsoft.jaravel.vendor.queue.database;

import com.weacsoft.jaravel.vendor.event.QueueDispatcher;
import com.weacsoft.jaravel.vendor.redis.RedisManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * 队列自动装配（database 驱动 + 通用 worker / dispatcher）。
 * <p>
 * 通过 {@code jaravel.queue.driver} 选择驱动：
 * <ul>
 *   <li>{@code sync}（默认）：内存队列，不创建 QueueDriver Bean，EventDispatcher 使用 QueueManager 内存队列</li>
 *   <li>{@code database}：基于 {@link DataSource} 的 {@link DatabaseQueueDriver}</li>
 *   <li>{@code redis}：基于 {@link RedisManager} 的 {@link RedisQueueDriver}（由
 *       {@link RedisQueueAutoConfiguration} 注册，先于本类处理）</li>
 * </ul>
 * <b>自动回退</b>：当 {@code driver=redis} 但 {@code redis-config} 未引入或无 {@link RedisManager}
 * 时，本类的 database bean 会因 {@code @ConditionalOnMissingBean(QueueDriver.class)} 兜底创建，
 * 并打印回退告警，确保不硬依赖 redis。
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
 * <p><b>注意</b>：worker 默认不自动启动。生产环境应通过 artisan 命令
 * {@code java -jar app.jar artisan queue:work} 启动 worker，
 * 或设置 {@code jaravel.queue.database.auto-start=true} 自动启动。
 * <p>
 * <b>建表</b>：使用 database 驱动前需先执行 {@code artisan queue:table} 创建 jobs/failed_jobs 表，
 * 或手动调用 {@link DatabaseQueueDriver#createTable()}。
 */
@AutoConfiguration
@ConditionalOnClass(QueueDriver.class)
@EnableConfigurationProperties({QueueProperties.class, QueueDatabaseProperties.class})
public class QueueDatabaseAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(QueueDatabaseAutoConfiguration.class);

    /**
     * 数据库队列驱动 bean。
     * <p>
     * 仅当 driver 不为 sync 且尚无 {@link QueueDriver} bean 且存在 {@link DataSource} 时创建。
     * 当 driver=redis 但 RedisManager 不可用时，作为回退驱动自动创建。
     */
    @Bean
    @ConditionalOnMissingBean(QueueDriver.class)
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnExpression("'${jaravel.queue.driver:sync}' != 'sync'")
    @ConditionalOnProperty(prefix = "jaravel.queue.database", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DatabaseQueueDriver databaseQueueDriver(DataSource dataSource,
                                                   QueueDatabaseProperties dbProps,
                                                   QueueProperties props) {
        if ("redis".equalsIgnoreCase(props.getDriver())) {
            logger.warn("[queue] 配置了 jaravel.queue.driver=redis 但 RedisManager 不可用，回退到 database 驱动");
        } else {
            logger.info("[queue] 使用 database 驱动");
        }
        return new DatabaseQueueDriver(dataSource, dbProps.getTable(),
                dbProps.getRetryAfter(), props.getFailedJobRetentionDays());
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
                applicationContext,
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
        return new DatabaseQueueDispatcher(driver, applicationContext);
    }
}
