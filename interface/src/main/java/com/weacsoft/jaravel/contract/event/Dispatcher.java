package com.weacsoft.jaravel.contract.event;

/**
 * 事件调度器接口，定义事件分发与监听器注册契约。
 *
 * <p>参考 Laravel {@code Illuminate\Contracts\Events\Dispatcher}，
 * 本接口定义事件系统的核心调度能力，解耦事件的触发与处理。</p>
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li>实现类必须保证线程安全</li>
 *   <li>同一事件可注册多个监听器，执行顺序由实现决定</li>
 *   <li>{@link #dispatch(Event)} 应同步执行所有监听器；异步支持由实现扩展</li>
 *   <li>监听器执行异常不应影响后续监听器的执行</li>
 * </ul>
 *
 * @see Event
 * @see Listener
 */
public interface Dispatcher {

    /**
     * 注册事件监听器。
     *
     * @param eventClass 事件类型
     * @param listener   监听器实例
     * @param <T>        事件类型泛型
     */
    <T extends Event> void listen(Class<T> eventClass, Listener<T> listener);

    /**
     * 分发事件到所有已注册的监听器。
     *
     * @param event 事件实例
     */
    void dispatch(Event event);

    /**
     * 清除指定事件类型的所有监听器。
     *
     * @param eventClass 事件类型
     */
    void clearListeners(Class<? extends Event> eventClass);

    /**
     * 清除所有事件监听器。
     */
    void clearAllListeners();
}
