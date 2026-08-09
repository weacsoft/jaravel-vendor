package com.weacsoft.jaravel.vendor.core.queue;

import java.util.List;

/**
 * 队列驱动接口，对齐 Laravel {@code Illuminate\Contracts\Queue\Queue}。
 * <p>
 * 抽象队列存储后端，支持数据库 / Redis 等实现。
 * 定义于 core 模块，使 queue-database 为可选扩展：未引入 queue-database 时，
 * 事件模块自动降级为内存队列（sync 同步模式）。
 *
 * <h3>多实例消费</h3>
 * 当多个应用实例使用同一队列驱动（如同一数据库 / 同一 Redis）时，
 * 每个实例的 worker 竞争消费同一队列，天然实现负载均衡。
 *
 * <h3>失败队列</h3>
 * 对齐 Laravel {@code failed_jobs} 表。任务超过最大重试次数后通过 {@link #fail} 归档到失败队列，
 * 可通过 {@link #getFailedJobs()} 查看、{@link #retryFailedJob(long)} 重试、{@link #deleteFailedJob(long)} 删除。
 * 失败队列是必须功能，所有驱动实现都必须支持。
 */
public interface QueueDriver {

    long push(String queueName, String payload);

    long push(String queueName, String payload, long delayMs);

    QueuedJob pop(String queueName);

    void delete(long jobId);

    void release(long jobId);

    void release(long jobId, long delayMs);

    int size(String queueName);

    void clear(String queueName);

    void fail(long jobId, String queue, String payload, int attempts, String exception);

    List<QueuedJob> getFailedJobs();

    void retryFailedJob(long failedJobId);

    void deleteFailedJob(long failedJobId);

    void clearFailedJobs();
}
