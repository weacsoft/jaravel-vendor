package com.weacsoft.jaravel.vendor.event;

/**
 * 事件标记接口，对齐 Laravel {@code Event}。
 * <p>
 * 所有业务事件类只需实现本接口，即可被 {@link Dispatcher} 分发、被 {@link Listener} 监听。
 * <pre>
 * public class UserRegistered implements Event {
 *     public final Long userId;
 *     public UserRegistered(Long userId) { this.userId = userId; }
 * }
 * </pre>
 */
public interface Event {
}
