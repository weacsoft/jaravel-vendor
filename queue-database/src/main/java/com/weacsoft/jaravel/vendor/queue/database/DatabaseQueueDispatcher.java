package com.weacsoft.jaravel.vendor.queue.database;

import com.weacsoft.jaravel.vendor.core.lookup.BeanLookup;
import com.weacsoft.jaravel.vendor.core.queue.QueueDriver;


import com.weacsoft.jaravel.vendor.event.Event;
import com.weacsoft.jaravel.vendor.event.Listener;
import com.weacsoft.jaravel.vendor.event.QueueDispatcher;
import com.weacsoft.jaravel.vendor.json.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 持久化队列分发器，对齐 Laravel {@code Illuminate\Queue\Queue::push}。
 * <p>
 * 实现 {@link QueueDispatcher} 接口，将实现了 {@link com.weacsoft.jaravel.vendor.event.ShouldQueue}
 * 的事件监听器分发到 {@link QueueDriver}（数据库或 Redis），实现多实例消费与任务持久化。
 * <p>
 * 由 {@link com.weacsoft.jaravel.vendor.event.EventDispatcher} 在监听器实现 {@code ShouldQueue}
 * 且 {@link QueueDispatcher} 可用时优先调用，替代内存队列。
 *
 * <h3>负载格式</h3>
 * 任务负载（payload）为 JSON，由 {@link DatabaseQueueWorker} 反序列化执行：
 * <ul>
 *   <li>{@code listenerClass}：监听器全限定类名</li>
 *   <li>{@code listenerBeanName}：监听器宿主 bean 名（可选，优先用于获取 bean）</li>
 *   <li>{@code eventClass}：事件全限定类名（用于反序列化）</li>
 *   <li>{@code eventData}：事件数据（序列化为 JSON 对象）</li>
 * </ul>
 *
 * <h3>D3（Spring 解耦终收）</h3>
 * 监听器 bean 名解析改经 {@link BeanLookup} SPI（core 纯接口，
 * Spring 宿主由 {@code ContextBeanProvider} 适配 {@code ApplicationContext}），
 * 本类不再直接依赖 Spring。
 */
public class DatabaseQueueDispatcher implements QueueDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseQueueDispatcher.class);

    /** 队列驱动（数据库 / Redis） */
    private final QueueDriver driver;

    /** Bean 查找 SPI，用于解析监听器 bean 名 */
    private final BeanLookup beanLookup;

    /**
     * 构造持久化队列分发器。
     *
     * @param driver     队列驱动
     * @param beanLookup Bean 查找 SPI（Spring 宿主传 {@code ContextBeanProvider}，非 Spring 宿主传 Map 版适配器或 {@code null}）
     */
    public DatabaseQueueDispatcher(QueueDriver driver, BeanLookup beanLookup) {
        this.driver = driver;
        this.beanLookup = beanLookup;
    }

    @Override
    public void dispatch(String queueName, Object listener, Event event, long delayMs) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>(4);
            payload.put("listenerClass", listener.getClass().getName());

            // 解析宿主 bean 名，便于 worker 通过 bean 名获取监听器（SPI 不可用则跳过，worker 按类名回退）
            if (beanLookup != null) {
                Map<String, ?> byType = beanLookup.beansOfType(listener.getClass());
                if (!byType.isEmpty()) {
                    payload.put("listenerBeanName", byType.keySet().iterator().next());
                }
            }

            payload.put("eventClass", event.getClass().getName());
            payload.put("eventData", event);

            String json = Json.stringify(payload);
            long jobId = driver.push(queueName, json, delayMs);
            logger.debug("[queue-dispatcher] 分发任务: queue={}, jobId={}, listener={}, event={}, delayMs={}",
                    queueName, jobId, listener.getClass().getName(), event.getClass().getName(), delayMs);
        } catch (Exception e) {
            logger.error("[queue-dispatcher] 分发任务失败: queue={}, listener={}, event={} - {}",
                    queueName, listener.getClass().getName(), event.getClass().getName(), e.getMessage(), e);
            throw new RuntimeException("队列分发失败: " + e.getMessage(), e);
        }
    }

    /**
     * 队列分发器是否可用。
     * <p>
     * 只要底层 {@link QueueDriver} 存在即视为可用。
     *
     * @return true 表示可用
     */
    @Override
    public boolean isAvailable() {
        return driver != null;
    }

    /**
     * @return 底层队列驱动
     */
    public QueueDriver getDriver() {
        return driver;
    }

    /**
     * 便捷方法：直接推送原始 payload 到队列。
     *
     * @param queueName 队列名
     * @param listener  监听器
     * @param event     事件
     * @return 任务 ID
     */
    public long push(String queueName, Listener<?> listener, Event event) {
        dispatch(queueName, listener, event, 0);
        return 0;
    }
}
