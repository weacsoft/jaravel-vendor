package com.weacsoft.jaravel.vendor.queue.database;

/**
 * 队列任务实体，对齐 Laravel {@code Illuminate\Queue\Jobs\DatabaseJob}。
 * <p>
 * 表示从队列中弹出的一个任务，包含任务 ID、负载和元数据。
 */
public class QueuedJob {

    /** 任务 ID */
    private final long id;

    /** 队列名 */
    private final String queue;

    /** 任务负载（JSON 序列化的监听器 + 事件数据） */
    private final String payload;

    /** 尝试次数 */
    private final int attempts;

    /** 预约时间（毫秒时间戳，0 表示未预约） */
    private final long reservedAt;

    /** 可用时间（毫秒时间戳，到期后可被消费） */
    private final long availableAt;

    /** 创建时间（毫秒时间戳） */
    private final long createdAt;

    public QueuedJob(long id, String queue, String payload, int attempts,
                     long reservedAt, long availableAt, long createdAt) {
        this.id = id;
        this.queue = queue;
        this.payload = payload;
        this.attempts = attempts;
        this.reservedAt = reservedAt;
        this.availableAt = availableAt;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public String getQueue() {
        return queue;
    }

    public String getPayload() {
        return payload;
    }

    public int getAttempts() {
        return attempts;
    }

    public long getReservedAt() {
        return reservedAt;
    }

    public long getAvailableAt() {
        return availableAt;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "QueuedJob{id=" + id + ", queue='" + queue + "', attempts=" + attempts + "}";
    }
}
