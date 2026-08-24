package com.weacsoft.jaravel.vendor.event;

/**
 * 队列分发器接口，对齐 Laravel {@code Illuminate\Contracts\Queue\Queue}。
 * <p>
 * 抽象异步事件的队列分发后端。当可用时，{@link EventDispatcher} 优先使用
 * {@link QueueDispatcher} 将事件分发到持久化队列（如数据库队列），
 * 实现多实例消费和任务持久化；不可用时降级为内存队列（{@link QueueManager}）。
 *
 * <h3>实现方</h3>
 * <ul>
 *   <li>{@code queue-database} 模块提供 {@code DatabaseQueueDispatcher} 实现</li>
 *   <li>业务方也可自定义实现，通过 Spring bean 注入自动生效</li>
 * </ul>
 */
public interface QueueDispatcher {

    /**
     * 将事件分发到指定队列。
     *
     * @param queueName 队列名
     * @param listener  事件监听器
     * @param event     被分发的事件
     * @param delayMs   延迟毫秒数（0 表示立即执行）
     */
    void dispatch(String queueName, Object listener, Event event, long delayMs);

    /**
     * @return 队列分发器是否可用（已连接到后端）
     */
    default boolean isAvailable() {
        return true;
    }
}
