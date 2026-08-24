package com.weacsoft.jaravel.vendor.core.queue;

/**
 * 队列任务实体，对齐 Laravel {@code Illuminate\Queue\Jobs\DatabaseJob}。
 * <p>
 * 表示从队列中弹出的一个任务，包含任务 ID、负载和元数据。
 * 失败任务（来自 {@code failed_jobs}）也会被包装为本类，
 * 此时 {@link #getException()} 携带失败异常信息，{@link #getId()} 为失败任务 ID。
 */
public class QueuedJob {

    private final long id;
    private final String queue;
    private final String payload;
    private final int attempts;
    private final long reservedAt;
    private final long availableAt;
    private final long createdAt;
    private final String exception;

    public QueuedJob(long id, String queue, String payload, int attempts,
                     long reservedAt, long availableAt, long createdAt) {
        this(id, queue, payload, attempts, reservedAt, availableAt, createdAt, null);
    }

    public QueuedJob(long id, String queue, String payload, int attempts,
                     long reservedAt, long availableAt, long createdAt, String exception) {
        this.id = id;
        this.queue = queue;
        this.payload = payload;
        this.attempts = attempts;
        this.reservedAt = reservedAt;
        this.availableAt = availableAt;
        this.createdAt = createdAt;
        this.exception = exception;
    }

    public long getId() { return id; }
    public String getQueue() { return queue; }
    public String getPayload() { return payload; }
    public int getAttempts() { return attempts; }
    public long getReservedAt() { return reservedAt; }
    public long getAvailableAt() { return availableAt; }
    public long getCreatedAt() { return createdAt; }
    public String getException() { return exception; }

    @Override
    public String toString() {
        return "QueuedJob{id=" + id + ", queue='" + queue + "', attempts=" + attempts
                + (exception != null ? ", exception='" + exception + "'" : "") + "}";
    }
}
