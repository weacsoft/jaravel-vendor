package com.weacsoft.jaravel.vendor.queue.database;

import java.util.List;

/**
 * 队列驱动接口，对齐 Laravel {@code Illuminate\Contracts\Queue\Queue}。
 * <p>
 * 抽象队列存储后端，支持数据库 / Redis 等实现。
 * 由 {@link com.weacsoft.jaravel.vendor.event.EventDispatcher} 在监听器实现
 * {@link com.weacsoft.jaravel.vendor.event.ShouldQueue} 时调用。
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

    /**
     * 推送任务到队列立即执行。
     *
     * @param queueName 队列名
     * @param payload   任务负载（JSON 序列化的监听器 + 事件数据）
     * @return 任务 ID
     */
    long push(String queueName, String payload);

    /**
     * 延迟推送任务到队列。
     *
     * @param queueName 队列名
     * @param payload   任务负载
     * @param delayMs   延迟毫秒数
     * @return 任务 ID
     */
    long push(String queueName, String payload, long delayMs);

    /**
     * 从队列弹出一个到期任务（阻塞式，带超时）。
     * <p>
     * 多实例环境下，通过数据库行锁或 Redis 原子操作确保同一任务只被一个实例获取。
     *
     * @param queueName 队列名
     * @return 任务对象，无任务返回 null
     */
    QueuedJob pop(String queueName);

    /**
     * 标记任务执行成功，从队列中删除。
     *
     * @param jobId 任务 ID
     */
    void delete(long jobId);

    /**
     * 标记任务执行失败，释放锁以便重试。
     *
     * @param jobId 任务 ID
     */
    void release(long jobId);

    /**
     * 标记任务执行失败，释放锁并设置重试延迟。
     *
     * @param jobId   任务 ID
     * @param delayMs 重试延迟毫秒
     */
    void release(long jobId, long delayMs);

    /**
     * 获取队列中待处理任务数。
     *
     * @param queueName 队列名
     * @return 任务数
     */
    int size(String queueName);

    /**
     * 清空指定队列的所有任务。
     *
     * @param queueName 队列名
     */
    void clear(String queueName);

    // ==================== 失败队列（对齐 Laravel failed_jobs） ====================

    /**
     * 将任务归档到失败队列，对齐 Laravel {@code failed_jobs} 表。
     * <p>
     * 由 {@link DatabaseQueueWorker} 在任务超过最大重试次数时调用。
     * 实现应将任务从当前队列移除并持久化到失败队列（数据库表或 Redis List）。
     *
     * @param jobId     原任务 ID
     * @param queue     队列名
     * @param payload   任务负载（JSON）
     * @param attempts  已尝试次数
     * @param exception 失败异常信息
     */
    void fail(long jobId, String queue, String payload, int attempts, String exception);

    /**
     * 获取失败队列中的所有任务，按失败时间倒序（最新失败在前）。
     *
     * @return 失败任务列表，{@link QueuedJob#getId()} 为失败任务 ID（用于重试 / 删除）
     */
    List<QueuedJob> getFailedJobs();

    /**
     * 重试一个失败任务：重新推入原队列并从失败队列移除，对齐 Laravel {@code queue:retry}。
     *
     * @param failedJobId 失败任务 ID（{@link QueuedJob#getId()}）
     */
    void retryFailedJob(long failedJobId);

    /**
     * 删除一个失败任务，对齐 Laravel {@code queue:forget}。
     *
     * @param failedJobId 失败任务 ID
     */
    void deleteFailedJob(long failedJobId);

    /**
     * 清空所有失败任务，对齐 Laravel {@code queue:flush}。
     */
    void clearFailedJobs();
}
