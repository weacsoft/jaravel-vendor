package com.weacsoft.jaravel.vendor.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 事件调度器默认实现，对齐 Laravel {@code Illuminate\Events\Dispatcher}。
 * <p>
 * 使用 {@link ConcurrentHashMap} + {@link CopyOnWriteArrayList} 维护事件与监听器的映射，
 * 保证多线程并发注册与分发时的线程安全。分发时采用 per-listener 队列决策：
 * 实现了 {@link ShouldQueue} 的监听器将被异步提交到 {@link QueueManager} 管理的命名队列中执行
 * （支持延迟与重试），对齐 Laravel 的「队列化事件」语义；未实现的则同步执行。
 * 此外 {@link #queueEnabled} 作为全局开关，对未实现 {@link ShouldQueue} 的监听器生效，
 * 但 per-listener 的 {@link ShouldQueue} 决策优先。
 * <p>
 * <b>多队列能力</b>：每个命名队列（由 {@link ShouldQueue#queue()} 返回）拥有独立的线程池，
 * 不同队列的监听器互不阻塞。队列大小、重试次数等通过 {@link EventProperties} 配置。
 * <p>
 * <b>重试机制</b>：监听器执行抛出异常时，按 {@code retry.max-attempts} 配置自动重试，
 * 重试间隔由 {@code retry.delay-ms} 配置。重试在原队列的线程池中执行，线程安全。
 * <p>
 * 单个监听器最终失败（重试耗尽）后仅记录日志，不会中断其它监听器的执行。
 */
@Component
public class EventDispatcher implements Dispatcher {

    private static final Logger logger = LoggerFactory.getLogger(EventDispatcher.class);

    /** 事件类型 -&gt; 监听器列表，使用并发安全容器 */
    private final ConcurrentHashMap<Class<? extends Event>, CopyOnWriteArrayList<Listener<? extends Event>>> listeners =
            new ConcurrentHashMap<>();

    /** 是否启用异步队列分发，默认关闭（同步分发） */
    private volatile boolean queueEnabled = false;

    /** 队列管理器，管理命名队列的线程池与重试配置 */
    private final QueueManager queueManager;

    /**
     * 构造事件调度器。
     *
     * @param queueManager 队列管理器
     */
    public EventDispatcher(QueueManager queueManager) {
        this.queueManager = queueManager;
    }

    @Override
    public void listen(Class<? extends Event> eventClass, Listener<? extends Event> listener) {
        listeners.computeIfAbsent(eventClass, key -> new CopyOnWriteArrayList<>()).add(listener);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void dispatch(Event event) {
        CopyOnWriteArrayList<Listener<? extends Event>> list = listeners.get(event.getClass());
        if (list == null || list.isEmpty()) {
            return;
        }
        for (Listener<? extends Event> listener : list) {
            final Listener<Event> typed = (Listener<Event>) listener;
            final String listenerName = listener.getClass().getName();
            if (listener instanceof ShouldQueue) {
                // per-listener 队列决策优先：实现 ShouldQueue 的监听器异步执行
                ShouldQueue sq = (ShouldQueue) listener;
                final String queueName = sq.queue();
                final long delay = sq.delay();
                dispatchToQueue(queueName, typed, event, listenerName, delay);
            } else if (queueEnabled) {
                // 全局开关：未实现 ShouldQueue 但全局开启异步时，也异步执行到默认队列
                dispatchToQueue(QueueManager.DEFAULT_QUEUE, typed, event, listenerName, 0);
            } else {
                // 同步执行（含重试）
                invokeWithRetry(typed, event, listenerName, QueueManager.DEFAULT_QUEUE);
            }
        }
    }

    /**
     * 将监听器异步分发到指定队列。
     *
     * @param queueName    队列名
     * @param listener     监听器
     * @param event        被分发的事件
     * @param listenerName 监听器类名（用于日志）
     * @param delayMs      延迟毫秒数（0 表示立即执行）
     */
    private void dispatchToQueue(String queueName, Listener<Event> listener, Event event,
                                 String listenerName, long delayMs) {
        if (delayMs > 0) {
            queueManager.schedule(queueName,
                () -> invokeWithRetry(listener, event, listenerName, queueName), delayMs);
        } else {
            queueManager.submit(queueName,
                () -> invokeWithRetry(listener, event, listenerName, queueName));
        }
    }

    /**
     * 执行单个监听器，支持自动重试。
     * <p>
     * 若监听器抛出异常，按 {@link QueueManager#getRetryMaxAttempts()} 配置的重试次数自动重试，
     * 重试间隔由 {@link QueueManager#getRetryDelayMs()} 配置。重试通过 {@link Thread#sleep} 实现，
     * 在队列线程池的工作线程中阻塞等待，不影响其它队列。所有重试耗尽后仅记录错误日志。
     * <p>
     * 线程安全：本方法无共享可变状态，{@code listener} 与 {@code event} 由调用方保证可见性
     * （通过线程池提交时的 happens-before 关系）。
     *
     * @param listener     监听器
     * @param event        被分发的事件
     * @param listenerName 监听器类名（用于日志）
     * @param queueName    队列名（用于日志）
     */
    private void invokeWithRetry(Listener<Event> listener, Event event, String listenerName, String queueName) {
        int maxAttempts = queueManager.getRetryMaxAttempts();
        int attempt = 0;
        while (true) {
            try {
                listener.handle(event);
                return;
            } catch (Exception e) {
                attempt++;
                if (attempt > maxAttempts) {
                    logger.error("事件监听器执行失败（已重试 {} 次）, queue={}, event={}, listener={}",
                        maxAttempts, queueName, event.getClass().getName(), listenerName, e);
                    return;
                }
                logger.warn("事件监听器执行异常，将重试 ({}/{}), queue={}, event={}, listener={}",
                    attempt, maxAttempts, queueName, event.getClass().getName(), listenerName, e);
                try {
                    Thread.sleep(queueManager.getRetryDelayMs());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.error("事件监听器重试等待被中断, listener={}", listenerName, ie);
                    return;
                }
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Event> List<Listener<T>> getListeners(Class<T> eventClass) {
        CopyOnWriteArrayList<Listener<? extends Event>> list = listeners.get(eventClass);
        if (list == null) {
            return new ArrayList<>();
        }
        List<Listener<T>> result = new ArrayList<>(list.size());
        for (Listener<? extends Event> listener : list) {
            result.add((Listener<T>) listener);
        }
        return result;
    }

    @Override
    public void clearListeners(Class<? extends Event> eventClass) {
        listeners.remove(eventClass);
    }

    @Override
    public void clearAllListeners() {
        listeners.clear();
    }

    /**
     * 设置是否启用异步队列分发。
     *
     * @param queueEnabled 是否启用
     */
    public void setQueueEnabled(boolean queueEnabled) {
        this.queueEnabled = queueEnabled;
    }

    /**
     * @return 是否启用异步队列分发
     */
    public boolean isQueueEnabled() {
        return queueEnabled;
    }

    /**
     * @return 队列管理器
     */
    public QueueManager getQueueManager() {
        return queueManager;
    }
}
