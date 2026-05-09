package com.weacsoft.jaravel.contract.event;

/**
 * 事件标记接口，所有事件类型必须实现此接口。
 *
 * <p>参考 Laravel 的事件系统，本接口作为类型安全标识，
 * 不定义任何方法，仅用于约束 {@link Listener} 和 {@link Dispatcher} 的泛型参数。</p>
 *
 * <h3>使用约定</h3>
 * <ul>
 *   <li>事件类应为不可变对象，携带事件数据</li>
 *   <li>事件类命名应体现业务语义（如 {@code UserRegistered}、{@code OrderCreated}）</li>
 *   <li>事件类应包含构建事件所需的所有上下文信息</li>
 * </ul>
 *
 * @see Listener
 * @see Dispatcher
 */
public interface Event {
}
