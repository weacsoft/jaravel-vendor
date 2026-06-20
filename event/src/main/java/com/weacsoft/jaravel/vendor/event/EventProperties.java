package com.weacsoft.jaravel.vendor.event;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 事件模块配置属性，前缀 {@code jaravel.event}，对齐 Laravel {@code config/event.php} 与 {@code config/queue.php}。
 * <p>
 * 支持异步队列分发与高级队列管理：每个命名队列拥有独立的线程池，可单独配置大小；
 * 监听器执行失败时支持自动重试（可配置最大重试次数与重试间隔）。
 * <pre>
 * jaravel:
 *   event:
 *     queue-enabled: true                        # 启用异步队列分发
 *     queue:
 *       default:
 *         pool-size: 4                           # 默认队列线程池大小（默认 CPU 核心数）
 *       email:
 *         pool-size: 2                           # "email" 队列线程池大小
 *     retry:
 *       max-attempts: 3                          # 最大重试次数（默认 3）
 *       delay-ms: 1000                           # 重试间隔毫秒（默认 1000）
 * </pre>
 */
@ConfigurationProperties(prefix = "jaravel.event")
public class EventProperties {

    /** 是否启用异步队列分发，默认关闭（同步分发） */
    private boolean queueEnabled = false;

    /** 队列相关配置 */
    private Queue queue = new Queue();

    /** 重试相关配置 */
    private Retry retry = new Retry();

    public boolean isQueueEnabled() {
        return queueEnabled;
    }

    public void setQueueEnabled(boolean queueEnabled) {
        this.queueEnabled = queueEnabled;
    }

    public Queue getQueue() {
        return queue;
    }

    public void setQueue(Queue queue) {
        this.queue = queue;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry;
    }

    /**
     * 队列配置。
     * <p>
     * {@code defaultPoolSize} 为默认队列的线程池大小；
     * {@code queuePoolSizes} 为各命名队列的线程池大小覆盖，key 为队列名（即 {@link ShouldQueue#queue()} 返回值）。
     */
    public static class Queue {
        /** 默认队列线程池大小，null 表示使用 CPU 核心数 */
        private Integer defaultPoolSize = null;

        /** 各命名队列的线程池大小覆盖，key = 队列名，value = 线程池大小 */
        private Map<String, Integer> queuePoolSizes = new HashMap<>();

        public Integer getDefaultPoolSize() {
            return defaultPoolSize;
        }

        public void setDefaultPoolSize(Integer defaultPoolSize) {
            this.defaultPoolSize = defaultPoolSize;
        }

        public Map<String, Integer> getQueuePoolSizes() {
            return queuePoolSizes;
        }

        public void setQueuePoolSizes(Map<String, Integer> queuePoolSizes) {
            this.queuePoolSizes = queuePoolSizes != null ? queuePoolSizes : new HashMap<>();
        }
    }

    /**
     * 重试配置。监听器执行抛出异常时，按此配置自动重试。
     */
    public static class Retry {
        /** 最大重试次数（不含首次执行），默认 3 */
        private int maxAttempts = 3;

        /** 重试间隔毫秒数，默认 1000 */
        private long delayMs = 1000;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getDelayMs() {
            return delayMs;
        }

        public void setDelayMs(long delayMs) {
            this.delayMs = delayMs;
        }
    }
}
