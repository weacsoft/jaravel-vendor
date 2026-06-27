package com.weacsoft.jaravel.vendor.queue.database;

import com.weacsoft.jaravel.vendor.event.QueueDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
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
 *   <li>{@code database}（默认）：基于 {@link DataSource} 的 {@link DatabaseQueueDriver}</li>
 *   <li>{@code redis}：基于 {@link RedisManager} 的 {@link RedisQueueDriver}（由
 *       {@link RedisQueueAutoConfiguration} 注册，先于本类处理）</li>
 * </ul>
 * <b>自动回退</b>：当 {@code driver=redis} 但 {@code redis-config} 未引入或无 {@link RedisManager}
 * 时，本类的 database bean 会因 {@code @ConditionalOnMissingBean(QueueDriver.class)} 兜底创建，
 * 并打印回退告警，确保不硬依赖 redis。
 *
 * <p>配置项：
 * <pre>
 * jaravel:
 *   queue:
 *     driver: database                # database | redis
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
 */
@AutoConfiguration
@ConditionalOnClass(QueueDriver.class)
@EnableConfigurationProperties({QueueProperties.class, QueueDatabaseProperties.class})
public class QueueDatabaseAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(QueueDatabaseAutoConfiguration.class);

    /**
     * 数据库队列驱动 bean（默认驱动，亦是 redis 不可用时的回退驱动）。
     * <p>
     * 仅当尚无 {@link QueueDriver} bean 且存在 {@link DataSource} 时创建。
     */
    @Bean
    @ConditionalOnMissingBean(QueueDriver.class)
    @ConditionalOnBean(DataSource.class)
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
