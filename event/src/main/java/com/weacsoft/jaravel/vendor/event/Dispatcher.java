package com.weacsoft.jaravel.vendor.event;

import java.util.List;

/**
 * 事件调度器契约，对齐 Laravel {@code Illuminate\Contracts\Events\Dispatcher}。
 * <p>
 * 负责维护「事件类型 -&gt; 监听器列表」的映射，并提供监听注册、事件分发、监听器查询与清理能力。
 */
public interface Dispatcher {

    /**
     * 注册监听器到指定事件类型。
     *
     * @param eventClass 事件类型
     * @param listener   监听器
     */
    void listen(Class<? extends Event> eventClass, Listener<? extends Event> listener);

    /**
     * 分发事件，依次触发该事件类型对应的所有监听器。
     *
     * @param event 被分发的事件
     */
    void dispatch(Event event);

    /**
     * 获取指定事件类型已注册的监听器列表。
     *
     * @param eventClass 事件类型
     * @param <T>        事件泛型
     * @return 监听器列表（可能为空，不会返回 null）
     */
    <T extends Event> List<Listener<T>> getListeners(Class<T> eventClass);

    /**
     * 清除指定事件类型的全部监听器。
     *
     * @param eventClass 事件类型
     */
    void clearListeners(Class<? extends Event> eventClass);

    /**
     * 清除所有事件类型的全部监听器。
     */
    void clearAllListeners();
}
