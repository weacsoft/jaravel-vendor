package com.weacsoft.jaravel.vendor.event;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 队列管理器，对齐 Laravel 的队列配置与多队列能力。
 * <p>
 * 每个命名队列（由 {@link ShouldQueue#queue()} 指定）拥有独立的线程池，
 * 不同队列的监听器互不阻塞。队列按需创建：首次向某队列提交任务时，
 * 才根据 {@link EventProperties} 配置创建对应大小的线程池。
 * <p>
 * <b>配置项</b>（前缀 {@code jaravel.event}）：
 * <ul>
 *   <li>{@code queue.default.pool-size}：默认队列线程池大小，未配置时使用 CPU 核心数；</li>
 *   <li>{@code queue.<name>.pool-size}：指定队列的线程池大小覆盖；</li>
 *   <li>{@code retry.max-attempts}：监听器失败最大重试次数（不含首次执行），默认 3；</li>
 *   <li>{@code retry.delay-ms}：重试间隔毫秒，默认 1000。</li>
 * </ul>
 * <p>
 * <b>线程安全</b>：使用 {@link ConcurrentHashMap} 维护队列名到执行器的映射，
 * {@link #getOrCreateExecutor(String)} 通过 {@code computeIfAbsent} 保证每队列只创建一个执行器。
 * 延迟任务通过单独的调度线程池（{@link #scheduler}）统一管理，避免每队列各建调度器。
 */
public class QueueManager {

    private static final Logger logger = LoggerFactory.getLogger(QueueManager.class);

    /** 默认队列名 */
    public static final String DEFAULT_QUEUE = "default";

    /** 队列名 -> 执行器（每队列独立线程池） */
    private final ConcurrentMap<String, ExecutorService> executors = new ConcurrentHashMap<>();

    /** 延迟任务调度器，所有队列共享，用于 schedule 延迟提交 */
    private final ScheduledExecutorService scheduler;

    /** 默认线程池大小 */
    private final int defaultPoolSize;

    /** 各队列线程池大小覆盖 */
    private final ConcurrentMap<String, Integer> queuePoolSizes;

    /** 最大重试次数（不含首次执行） */
    private final int retryMaxAttempts;

    /** 重试间隔毫秒 */
    private final long retryDelayMs;

    /**
     * 构造队列管理器。
     *
     * @param properties 事件配置属性
     */
    public QueueManager(EventProperties properties) {
        EventProperties.Queue queueCfg = properties.getQueue();
        EventProperties.Retry retryCfg = properties.getRetry();

        this.defaultPoolSize = resolveDefaultPoolSize(queueCfg);
        this.queuePoolSizes = new ConcurrentHashMap<>();
        if (queueCfg != null && queueCfg.getQueuePoolSizes() != null) {
            this.queuePoolSizes.putAll(queueCfg.getQueuePoolSizes());
        }
        this.retryMaxAttempts = retryCfg != null ? retryCfg.getMaxAttempts() : 3;
        this.retryDelayMs = retryCfg != null ? retryCfg.getDelayMs() : 1000L;

        // 调度器使用守护线程，2 个线程足以处理延迟提交
        this.scheduler = Executors.newScheduledThreadPool(2, new DaemonThreadFactory("jaravel-event-scheduler"));

        logger.info("[event] QueueManager 初始化: defaultPoolSize={}, retryMaxAttempts={}, retryDelayMs={}",
            defaultPoolSize, retryMaxAttempts, retryDelayMs);
    }

    /** 计算默认线程池大小：配置优先，否则使用 CPU 核心数 */
    private int resolveDefaultPoolSize(EventProperties.Queue queueCfg) {
        if (queueCfg != null && queueCfg.getDefaultPoolSize() != null && queueCfg.getDefaultPoolSize() > 0) {
            return queueCfg.getDefaultPoolSize();
        }
        return Runtime.getRuntime().availableProcessors();
    }

    /**
     * 获取或创建指定队列的执行器。
     * <p>
     * 首次请求某队列时，按配置创建固定大小的线程池；后续请求直接返回已有执行器。
     * 使用 {@code computeIfAbsent} 保证线程安全与单例。
     *
     * @param queueName 队列名，null 或空时使用 {@link #DEFAULT_QUEUE}
     * @return 该队列的执行器
     */
    public ExecutorService getOrCreateExecutor(String queueName) {
        String name = (queueName == null || queueName.isEmpty()) ? DEFAULT_QUEUE : queueName;
        return executors.computeIfAbsent(name, this::createExecutor);
    }

    /** 创建指定队列的线程池 */
    private ExecutorService createExecutor(String queueName) {
        int poolSize = queuePoolSizes.getOrDefault(queueName, defaultPoolSize);
        if (poolSize <= 0) {
            poolSize = defaultPoolSize;
        }
        logger.info("[event] 创建队列 '{}' 线程池, poolSize={}", queueName, poolSize);
        return Executors.newFixedThreadPool(poolSize, new DaemonThreadFactory("jaravel-event-" + queueName));
    }

    /**
     * 提交任务到指定队列立即执行。
     *
     * @param queueName 队列名
     * @param task      待执行任务
     */
    public void submit(String queueName, Runnable task) {
        getOrCreateExecutor(queueName).submit(task);
    }

    /**
     * 延迟提交任务到指定队列。
     * <p>
     * 通过共享的 {@link #scheduler} 延迟后，再将任务提交到目标队列的执行器。
     *
     * @param queueName 队列名
     * @param task      待执行任务
     * @param delayMs   延迟毫秒数
     */
    public void schedule(String queueName, Runnable task, long delayMs) {
        if (delayMs <= 0) {
            submit(queueName, task);
            return;
        }
        scheduler.schedule(() -> submit(queueName, task), delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * @return 最大重试次数（不含首次执行）
     */
    public int getRetryMaxAttempts() {
        return retryMaxAttempts;
    }

    /**
     * @return 重试间隔毫秒
     */
    public long getRetryDelayMs() {
        return retryDelayMs;
    }

    /**
     * 优雅关闭所有队列执行器与调度器：先 {@code shutdown}，最多等待 5 秒，
     * 超时则执行 {@code shutdownNow} 强制关闭。
     */
    @PreDestroy
    public void shutdown() {
        logger.info("[event] QueueManager 正在关闭...");
        // 关闭调度器
        scheduler.shutdown();
        // 关闭所有队列执行器
        for (var entry : executors.entrySet()) {
            entry.getValue().shutdown();
        }
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("[event] 调度器在 5 秒内未完成全部任务，执行强制关闭");
                scheduler.shutdownNow();
            }
            for (var entry : executors.entrySet()) {
                if (!entry.getValue().awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warn("[event] 队列 '{}' 执行器在 5 秒内未完成全部任务，执行强制关闭", entry.getKey());
                    entry.getValue().shutdownNow();
                }
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            for (var entry : executors.entrySet()) {
                entry.getValue().shutdownNow();
            }
            Thread.currentThread().interrupt();
        }
        logger.info("[event] QueueManager 已关闭");
    }

    /**
     * 守护线程工厂，创建名为 {@code prefix-N} 的守护线程，
     * 避免异步事件任务阻止 JVM 退出。
     */
    private static final class DaemonThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(0);
        private final String prefix;

        DaemonThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
