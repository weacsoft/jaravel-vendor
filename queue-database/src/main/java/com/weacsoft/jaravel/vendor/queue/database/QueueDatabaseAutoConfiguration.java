package com.weacsoft.jaravel.vendor.queue.database;

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
 * 数据库队列自动装配。
 * <p>
 * 当 {@link DataSource} 存在时，创建 {@link DatabaseQueueDriver} 和 {@link DatabaseQueueWorker}。
 * <p>
 * 配置项：
 * <pre>
 * jaravel:
 *   queue:
 *     database:
 *       table: jobs
 *       retry-after: 1800
 *       auto-start: false    # 默认不自动启动 worker，需手动启动或配置为 true
 * </pre>
 * <p>
 * <b>注意</b>：worker 默认不自动启动。生产环境应通过 artisan 命令
 * {@code java -jar app.jar artisan queue:work} 启动 worker，
 * 或设置 {@code jaravel.queue.database.auto-start=true} 自动启动。
 */
@AutoConfiguration
@ConditionalOnClass({DatabaseQueueDriver.class, DataSource.class})
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "jaravel.queue.database", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(QueueDatabaseProperties.class)
public class QueueDatabaseAutoConfiguration {

    /**
     * 数据库队列驱动 bean。
     */
    @Bean
    @ConditionalOnMissingBean
    public DatabaseQueueDriver databaseQueueDriver(DataSource dataSource,
                                                    QueueDatabaseProperties properties) {
        return new DatabaseQueueDriver(dataSource, properties.getTable(), properties.getRetryAfter());
    }

    /**
     * 数据库队列工作线程 bean。
     * <p>
     * 仅当 {@code auto-start=true} 时自动启动。
     */
    @Bean
    @ConditionalOnMissingBean
    public DatabaseQueueWorker databaseQueueWorker(DatabaseQueueDriver driver,
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
}
