package com.weacsoft.jaravel.vendor.core.queue;

import java.util.Collections;
import java.util.List;

/**
 * 队列驱动持有者，解决「注册时机」与「使用时机」的先后问题。
 * <p>
 * {@code DatabaseQueueWorker} / {@code DatabaseQueueDispatcher} 在 Bean 创建阶段
 * 就需要 {@link QueueDriver}，而 {@code @RegisterQueueDriver} 注解要等到
 * 所有单例初始化完成后才扫描得到。因此由 {@code QueueDriverRegistrar}
 * 把最终解析出的驱动写入本持有者。
 *
 * <h3>无驱动时的行为</h3>
 * 若最终没有任何驱动（sync 模式），调用写操作会抛出
 * {@link IllegalStateException} 提示改用同步分发；读操作返回空值，
 * 保证 {@code queue:failed} 等命令不会因空指针崩溃。
 */
public class QueueDriverHolder implements QueueDriver {

    private volatile QueueDriver delegate;

    public void set(QueueDriver delegate) {
        this.delegate = delegate;
    }

    public boolean isPresent() {
        return delegate != null;
    }

    private QueueDriver require() {
        QueueDriver current = delegate;
        if (current == null) {
            throw new IllegalStateException(
                    "当前未注册任何队列驱动（sync 同步模式）。如需持久化队列，请配置 "
                            + "jaravel.queue.driver=database 并提供 DataSource，"
                            + "或使用 @RegisterQueueDriver 注册自定义驱动。");
        }
        return current;
    }

    @Override
    public long push(String queueName, String payload) { return require().push(queueName, payload); }
    @Override
    public long push(String queueName, String payload, long delayMs) { return require().push(queueName, payload, delayMs); }
    @Override
    public QueuedJob pop(String queueName) { return delegate == null ? null : delegate.pop(queueName); }
    @Override
    public void delete(long jobId) { require().delete(jobId); }
    @Override
    public void release(long jobId) { require().release(jobId); }
    @Override
    public void release(long jobId, long delayMs) { require().release(jobId, delayMs); }
    @Override
    public int size(String queueName) { return delegate == null ? 0 : delegate.size(queueName); }
    @Override
    public void clear(String queueName) { if (delegate != null) { delegate.clear(queueName); } }
    @Override
    public void fail(long jobId, String queue, String payload, int attempts, String exception) { require().fail(jobId, queue, payload, attempts, exception); }
    @Override
    public List<QueuedJob> getFailedJobs() { return delegate == null ? Collections.emptyList() : delegate.getFailedJobs(); }
    @Override
    public void retryFailedJob(long failedJobId) { require().retryFailedJob(failedJobId); }
    @Override
    public void deleteFailedJob(long failedJobId) { require().deleteFailedJob(failedJobId); }
    @Override
    public void clearFailedJobs() { if (delegate != null) { delegate.clearFailedJobs(); } }
}
