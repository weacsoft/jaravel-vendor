package com.weacsoft.jaravel.vendor.queue.database;

import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;

/**
 * queue-database 模块的可发布配置类模板，
 * 由 {@code artisan vendor:publish --tag=queue-database} 发布。
 * <p>
 * 发布后在业务工程生成 {@code config/QueueDatabaseConfig.java}，
 * 内含 {@code jaravel.queue.*} 与 {@code jaravel.queue.database.*} 配置项说明。
 */
public class QueueDatabasePublishableConfig implements PublishableConfig {

    @Override
    public String tag() {
        return "queue-database";
    }

    @Override
    public String className() {
        return "QueueDatabaseConfig";
    }

    @Override
    public String description() {
        return "数据库队列驱动配置（jobs 表、重试、轮询、worker 线程）";
    }

    @Override
    public String source(String basePackage) {
        return "package " + basePackage + ".config;\n"
                + """

                import com.weacsoft.jaravel.vendor.queue.database.QueueDatabaseProperties;
                import com.weacsoft.jaravel.vendor.queue.database.QueueProperties;
                import org.springframework.beans.factory.ObjectProvider;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;

                import java.util.LinkedHashMap;

                /**
                 * 队列配置，对齐 Laravel config/queue.php 的 database 驱动。
                 * <p>
                 * 由 {@code artisan vendor:publish --tag=queue-database} 发布生成，可自由修改。
                 *
                 * <h3>配置项（application.yml）</h3>
                 * <pre>
                 * jaravel:
                 *   queue:
                 *     driver: sync                   # 队列驱动：sync / database / redis，默认 sync
                 *     redis-connection: ""           # driver=redis 时使用的 redis 连接名
                 *     failed-job-retention-days: 7   # 失败任务保留天数，默认 7
                 *     database:
                 *       table: jobs                  # 队列表名，默认 jobs
                 *       retry-after: 1800            # 任务超时回收秒数，默认 1800
                 *       max-attempts: 3              # 最大重试次数，默认 3
                 *       retry-delay-ms: 1000         # 重试间隔毫秒，默认 1000
                 *       poll-interval-ms: 1000       # 轮询间隔毫秒，默认 1000
                 *       worker-threads: 1            # worker 线程数，默认 1
                 *       queues: [default]            # 消费的队列名列表，默认 [default]
                 *       auto-start: false            # 应用启动时是否自动拉起 worker，默认 false
                 * </pre>
                 *
                 * <h3>说明</h3>
                 * <ul>
                 *   <li>本类只读取配置生成一份快照，<b>不会</b>覆盖框架自动装配的 QueueDriver。</li>
                 *   <li>建表可用 {@code artisan queue:table} 生成迁移。</li>
                 *   <li>删除本文件不影响启动。</li>
                 * </ul>
                 */
                @Configuration
                public class QueueDatabaseConfig {

                    /**
                     * 队列生效配置快照。
                     *
                     * @param queueProvider    QueueProperties 提供者
                     * @param databaseProvider QueueDatabaseProperties 提供者
                     * @return 解析后的配置键值对
                     */
                    @Bean
                    public LinkedHashMap<String, Object> queueDatabaseConfigMetadata(
                            ObjectProvider<QueueProperties> queueProvider,
                            ObjectProvider<QueueDatabaseProperties> databaseProvider) {
                        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
                        QueueProperties queue = queueProvider.getIfAvailable();
                        if (queue == null) {
                            metadata.put("jaravel.queue", "未装配（queue 模块未启用）");
                        } else {
                            metadata.put("jaravel.queue.driver", queue.getDriver());
                            metadata.put("jaravel.queue.redis-connection", queue.getRedisConnection());
                            metadata.put("jaravel.queue.failed-job-retention-days",
                                    queue.getFailedJobRetentionDays());
                        }
                        QueueDatabaseProperties database = databaseProvider.getIfAvailable();
                        if (database == null) {
                            metadata.put("jaravel.queue.database", "未装配（database 队列未启用）");
                            return metadata;
                        }
                        metadata.put("jaravel.queue.database.table", database.getTable());
                        metadata.put("jaravel.queue.database.retry-after", database.getRetryAfter());
                        metadata.put("jaravel.queue.database.max-attempts", database.getMaxAttempts());
                        metadata.put("jaravel.queue.database.retry-delay-ms", database.getRetryDelayMs());
                        metadata.put("jaravel.queue.database.poll-interval-ms", database.getPollIntervalMs());
                        metadata.put("jaravel.queue.database.worker-threads", database.getWorkerThreads());
                        metadata.put("jaravel.queue.database.queues", database.getQueues());
                        metadata.put("jaravel.queue.database.auto-start", database.isAutoStart());
                        return metadata;
                    }
                }
                """;
    }
}
