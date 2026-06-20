package com.weacsoft.jaravel.vendor.event;

/**
 * 监听器接口，对齐 Laravel {@code Listener}。
 * <p>
 * 泛型 {@code T} 表示该监听器能处理的事件类型。实现类只需实现 {@link #handle(T)}，
 * 在其中编写事件响应逻辑。
 * <pre>
 * public class SendWelcomeMail implements Listener&lt;UserRegistered&gt; {
 *     &#64;Override
 *     public void handle(UserRegistered event) {
 *         mailService.send(event.userId);
 *     }
 * }
 * </pre>
 *
 * @param <T> 监听器处理的事件类型
 */
@FunctionalInterface
public interface Listener<T extends Event> {

    /**
     * 处理事件。
     *
     * @param event 被分发的事件
     */
    void handle(T event);
}
