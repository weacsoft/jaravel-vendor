package com.weacsoft.jaravel.vendor.queue.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 数据库队列工作线程，对齐 Laravel {@code php artisan queue:work}。
 * <p>
 * 持续轮询数据库队列，弹出到期任务并执行。支持多队列、多线程消费。
 *
 * <h3>任务执行</h3>
 * 任务负载（payload）为 JSON 格式，包含：
 * <ul>
 *   <li>{@code listenerClass}：监听器类名（Spring bean 名或全限定类名）</li>
 *   <li>{@code eventData}：事件数据（JSON 对象）</li>
 *   <li>{@code eventClass}：事件类名（用于反序列化）</li>
 * </ul>
 * 工作线程通过 Spring {@link ApplicationContext} 获取监听器 bean，
 * 将事件数据反序列化后调用监听器的 {@code handle} 方法。
 *
 * <h3>多实例消费</h3>
 * 多个应用实例可同时运行 worker，通过数据库行锁竞争任务，
 * 同一任务只会被一个实例获取并执行。
 *
 * <h3>重试机制</h3>
 * 任务执行失败时，若尝试次数未超过最大重试次数，则释放任务并设置重试延迟；
 * 超过最大重试次数则删除任务（或移入失败任务表）。
 */
public class DatabaseQueueWorker {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseQueueWorker.class);

    /** 队列驱动 */
    private final QueueDriver driver;

    /** Spring 应用上下文，用于获取监听器 bean */
    private final ApplicationContext applicationContext;

    /** JSON 序列化器 */
    private final ObjectMapper objectMapper;

    /** 要消费的队列名列表 */
    private final List<String> queues;

    /** 最大重试次数 */
    private final int maxAttempts;

    /** 重试延迟毫秒 */
    private final long retryDelayMs;

    /** 轮询间隔毫秒 */
    private final long pollIntervalMs;

    /** 任务执行线程池 */
    private final ExecutorService executor;

    /** 工作线程 */
    private final Map<String, Thread> workerThreads = new ConcurrentHashMap<>();

    /** 是否正在运行 */
    private volatile boolean running = false;

    /**
     * 构造数据库队列工作线程。
     *
     * @param driver           队列驱动
     * @param applicationContext Spring 上下文
     * @param queues           要消费的队列名列表
     * @param maxAttempts      最大重试次数
     * @param retryDelayMs     重试延迟毫秒
     * @param pollIntervalMs   轮询间隔毫秒
     * @param workerThreads    每队列工作线程数
     */
    public DatabaseQueueWorker(QueueDriver driver, ApplicationContext applicationContext,
                               List<String> queues, int maxAttempts, long retryDelayMs,
                               long pollIntervalMs, int workerThreads) {
        this.driver = driver;
        this.applicationContext = applicationContext;
        this.objectMapper = new ObjectMapper();
        this.queues = queues;
        this.maxAttempts = maxAttempts;
        this.retryDelayMs = retryDelayMs;
        this.pollIntervalMs = pollIntervalMs;
        this.executor = Executors.newFixedThreadPool(queues.size() * workerThreads, r -> {
            Thread t = new Thread(r, "jaravel-queue-worker-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 启动工作线程。
     */
    public void start() {
        if (running) {
            return;
        }
        running = true;
        for (String queue : queues) {
            Thread worker = new Thread(() -> workLoop(queue), "jaravel-queue-" + queue);
            worker.setDaemon(true);
            worker.start();
            workerThreads.put(queue, worker);
        }
        logger.info("[queue-worker] 启动 {} 个队列工作线程: queues={}", queues.size(), queues);
    }

    /**
     * 工作循环：持续轮询指定队列。
     */
    private void workLoop(String queueName) {
        logger.info("[queue-worker] 队列 '{}' 工作线程启动", queueName);
        while (running) {
            try {
                QueuedJob job = driver.pop(queueName);
                if (job == null) {
                    // 无任务，等待
                    Thread.sleep(pollIntervalMs);
                    continue;
                }
                // 提交到线程池执行
                executor.submit(() -> executeJob(job));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("[queue-worker] 队列 '{}' 轮询异常: {}", queueName, e.getMessage(), e);
                try {
                    Thread.sleep(pollIntervalMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        logger.info("[queue-worker] 队列 '{}' 工作线程停止", queueName);
    }

    /**
     * 执行单个任务。
     */
    private void executeJob(QueuedJob job) {
        try {
            logger.info("[queue-worker] 执行任务: {}", job);

            // 解析负载
            Map<String, Object> payload = objectMapper.readValue(job.getPayload(), Map.class);
            String listenerBeanName = (String) payload.get("listenerBeanName");
            String listenerClassName = (String) payload.get("listenerClass");

            // 获取监听器 bean
            Object listener = null;
            if (listenerBeanName != null && applicationContext.containsBean(listenerBeanName)) {
                listener = applicationContext.getBean(listenerBeanName);
            } else if (listenerClassName != null) {
                try {
                    Class<?> clazz = Class.forName(listenerClassName);
                    listener = applicationContext.getBean(clazz);
                } catch (ClassNotFoundException e) {
                    logger.error("[queue-worker] 监听器类不存在: {}", listenerClassName);
                }
            }

            if (listener == null) {
                logger.error("[queue-worker] 找不到监听器: beanName={}, class={}", listenerBeanName, listenerClassName);
                driver.delete(job.getId());
                return;
            }

            // 获取事件数据
            Object eventData = payload.get("eventData");
            String eventClassName = (String) payload.get("eventClass");

            // 反序列化事件
            Object event = null;
            if (eventData != null && eventClassName != null) {
                try {
                    Class<?> eventClass = Class.forName(eventClassName);
                    event = objectMapper.convertValue(eventData, eventClass);
                } catch (ClassNotFoundException e) {
                    logger.warn("[queue-worker] 事件类不存在，使用原始数据: {}", eventClassName);
                    event = eventData;
                }
            }

            // 调用监听器的 handle 方法
            invokeListener(listener, event);

            // 执行成功，删除任务
            driver.delete(job.getId());
            logger.info("[queue-worker] 任务执行成功: {}", job);

        } catch (Exception e) {
            logger.error("[queue-worker] 任务执行失败: {} - {}", job, e.getMessage(), e);
            // 重试或删除
            if (job.getAttempts() < maxAttempts) {
                driver.release(job.getId(), retryDelayMs);
                logger.info("[queue-worker] 任务重试: {}, attempts={}/{}", job, job.getAttempts(), maxAttempts);
            } else {
                driver.delete(job.getId());
                logger.warn("[queue-worker] 任务超过最大重试次数，已删除: {}, attempts={}", job, job.getAttempts());
            }
        }
    }

    /** 反射调用监听器的 handle 方法 */
    private void invokeListener(Object listener, Object event) throws Exception {
        try {
            // 尝试调用 handle(Object) 方法
            var method = listener.getClass().getMethod("handle", Object.class);
            method.invoke(listener, event);
        } catch (NoSuchMethodException e) {
            // 尝试调用 handle(eventType) 方法
            if (event != null) {
                var method = listener.getClass().getMethod("handle", event.getClass());
                method.invoke(listener, event);
            } else {
                throw new RuntimeException("监听器没有 handle 方法: " + listener.getClass().getName());
            }
        }
    }

    /**
     * 停止工作线程。
     */
    @PreDestroy
    public void stop() {
        logger.info("[queue-worker] 正在停止...");
        running = false;
        // 中断工作线程
        for (Thread t : workerThreads.values()) {
            t.interrupt();
        }
        // 关闭线程池
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("[queue-worker] 已停止");
    }
}
