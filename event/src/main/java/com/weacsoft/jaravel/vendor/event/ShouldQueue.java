package com.weacsoft.jaravel.vendor.event;

/**
 * 标记监听器应通过队列异步执行，对齐 Laravel 的 ShouldQueue。
 * 实现此接口的监听器将被异步分发，未实现的将同步执行。
 */
public interface ShouldQueue {

    /**
     * 队列名称，默认为 "default"
     *
     * @return 队列名称
     */
    default String queue() {
        return "default";
    }

    /**
     * 延迟执行毫秒数，默认为 0
     *
     * @return 延迟毫秒数
     */
    default long delay() {
        return 0;
    }
}
