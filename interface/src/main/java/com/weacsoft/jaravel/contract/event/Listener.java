package com.weacsoft.jaravel.contract.event;

/**
 * 事件监听器接口，定义事件处理契约。
 *
 * <p>参考 Laravel 的事件监听器，本接口为函数式接口，
 * 每个监听器负责处理一种特定类型的事件。</p>
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li>实现类必须保证线程安全</li>
 *   <li>{@link #handle(T)} 不应抛出未检查异常；如需传播错误，应包装为受检异常</li>
 *   <li>监听器执行顺序由 {@link Dispatcher} 决定</li>
 *   <li>避免在监听器中执行耗时操作，如需异步处理应使用队列</li>
 * </ul>
 *
 * @param <T> 监听的事件类型
 * @see Event
 * @see Dispatcher
 */
public interface Listener<T extends Event> {

    /**
     * 处理事件。
     *
     * @param event 待处理的事件实例
     */
    void handle(T event);
}
