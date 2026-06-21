package com.weacsoft.jaravel.vendor.queue.database;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据库队列配置属性，前缀 {@code jaravel.queue.database}，对齐 Laravel {@code config/queue.php} database 段。
 * <pre>
 * jaravel:
 *   queue:
 *     database:
 *       table: jobs                  # 任务表名
 *       retry-after: 1800            # 重试超时秒数（30 分钟）
 *       max-attempts: 3              # 最大重试次数
 *       retry-delay-ms: 1000         # 重试延迟毫秒
 *       poll-interval-ms: 1000       # 轮询间隔毫秒
 *       worker-threads: 1            # 每队列工作线程数
 *       queues:                      # 要消费的队列名列表
 *         - default
 *         - score
 *         - oracle
 *       auto-start: true             # 是否自动启动 worker
 * </pre>
 */
@ConfigurationProperties(prefix = "jaravel.queue.database")
public class QueueDatabaseProperties {

    /** 任务表名 */
    private String table = "jobs";

    /** 重试超时秒数 */
    private long retryAfter = 1800;

    /** 最大重试次数 */
    private int maxAttempts = 3;

    /** 重试延迟毫秒 */
    private long retryDelayMs = 1000;

    /** 轮询间隔毫秒 */
    private long pollIntervalMs = 1000;

    /** 每队列工作线程数 */
    private int workerThreads = 1;

    /** 要消费的队列名列表 */
    private List<String> queues = new ArrayList<>(List.of("default"));

    /** 是否自动启动 worker */
    private boolean autoStart = false;

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public long getRetryAfter() {
        return retryAfter;
    }

    public void setRetryAfter(long retryAfter) {
        this.retryAfter = retryAfter;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getRetryDelayMs() {
        return retryDelayMs;
    }

    public void setRetryDelayMs(long retryDelayMs) {
        this.retryDelayMs = retryDelayMs;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public void setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
    }

    public List<String> getQueues() {
        return queues;
    }

    public void setQueues(List<String> queues) {
        this.queues = queues;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }
}
