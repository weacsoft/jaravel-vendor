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

    /**
     * 投递任务（立即就绪）。
     *
     * @param queueName 队列名
     * @param payload   任务负载（驱动原样保存，通常为 JSON）
     * @return 任务 id
     */
    long push(String queueName, String payload);

    /**
     * 投递任务（延迟就绪）。
     *
     * @param queueName 队列名
     * @param payload   任务负载
     * @param delayMs   延迟毫秒；{@code <= 0} 等价于立即就绪
     * @return 任务 id
     */
    long push(String queueName, String payload, long delayMs);

    /**
     * 取出一个可执行任务并<b>预约</b>它：同一任务在同一时刻只会被一个实例取到。
     * <p>
     * <b>投递保证为「至少一次」</b>：任务被取出后若未在 {@code retryAfterSeconds} 内
     * {@link #delete(long)} 或 {@link #release(long)}，会被重新投递 —— 消费方必须<b>幂等</b>。
     * <p>
     * 返回 {@code null} 只应表示「当前没有可执行任务」；实现不得把「抢占竞争失败」
     * 与「队列为空」混为一谈（前者应重试，否则上层会误判为空闲并 sleep）。
     *
     * @param queueName 队列名
     * @return 任务；无任务返回 {@code null}
     */
    QueuedJob pop(String queueName);

    /**
     * 确认完成（删除任务）。
     *
     * @param jobId 任务 id
     */
    void delete(long jobId);

    /**
     * 释放任务：立即重新入队（用于重试）。
     *
     * @param jobId 任务 id
     */
    void release(long jobId);

    /**
     * 释放任务：延迟重新入队。
     *
     * @param jobId   任务 id
     * @param delayMs 延迟毫秒
     */
    void release(long jobId, long delayMs);

    /**
     * 队列长度。
     * <p>
     * <b>口径说明</b>：只统计「就绪可用」的任务，<b>不含</b>延迟队列与已被预约（执行中）的任务；
     * 不同驱动对「就绪」的判定可能略有差异（如数据库驱动按 {@code available_at} 到期且未被预约）。
     * 用作监控指标时请勿与「队列积压总量」直接等同。
     *
     * @param queueName 队列名
     * @return 就绪任务数量（近似值）
     */
    int size(String queueName);

    void clear(String queueName);

    void fail(long jobId, String queue, String payload, int attempts, String exception);

    List<QueuedJob> getFailedJobs();

    void retryFailedJob(long failedJobId);

    void deleteFailedJob(long failedJobId);

    void clearFailedJobs();
}
