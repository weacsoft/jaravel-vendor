package com.weacsoft.jaravel.vendor.core.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 队列全局配置属性，前缀 {@code jaravel.queue}，对齐 Laravel {@code config/queue.php} 顶层配置。
 * <p>
 * 控制队列驱动选择与失败任务保留策略。数据库 / Redis 驱动的细分配置分别在
 * queue-database 模块的 {@code QueueDatabaseProperties}（前缀 {@code jaravel.queue.database}）中。
 * <pre>
 * jaravel:
 *   queue:
 *     driver: sync                   # sync（默认，内存队列）| database | redis
 *     redis-connection: ""            # redis 驱动使用的连接名，空 = 默认连接
 *     failed-job-retention-days: 7    # 失败任务保留天数
 * </pre>
 */
@ConfigurationProperties(prefix = "jaravel.queue")
public class QueueProperties {

    private String driver = "sync";
    private String redisConnection = "";
    private int failedJobRetentionDays = 7;

    public String getDriver() { return driver; }
    public void setDriver(String driver) { this.driver = driver; }
    public String getRedisConnection() { return redisConnection; }
    public void setRedisConnection(String redisConnection) { this.redisConnection = redisConnection; }
    public int getFailedJobRetentionDays() { return failedJobRetentionDays; }
    public void setFailedJobRetentionDays(int failedJobRetentionDays) { this.failedJobRetentionDays = failedJobRetentionDays; }
}
