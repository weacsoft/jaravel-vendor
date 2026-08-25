package com.weacsoft.jaravel.vendor.springboot.event;

import com.weacsoft.jaravel.vendor.core.provider.ServiceProvider;
import com.weacsoft.jaravel.vendor.event.Dispatcher;
import com.weacsoft.jaravel.vendor.event.Event;
import com.weacsoft.jaravel.vendor.event.Listener;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 事件服务提供者基类，对齐 Laravel 的 EventServiceProvider。
 * 子类在 {@link #register()} 中调用 {@link #listen(Class, Listener)} 注册事件-监听器映射。
 * <pre>
 * &#64;Component
 * public class AppEventServiceProvider extends EventServiceProvider {
 *     &#64;Override
 *     public void register() {
 *         listen(UserRegisteredEvent.class, new SendWelcomeEmailListener());
 *         listen(UserRegisteredEvent.class, new LogRegistrationListener());
 *     }
 * }
 * </pre>
 * <p>
 * {@link #dispatcher} 由 Spring 容器自动注入，{@code com.weacsoft.jaravel.vendor.core.provider.ProviderRegistry}
 * 会在所有单例就绪后调用 {@link #register()}，此时调度器已就绪，可安全注册监听器。
 * <p>
 * Spring 装配收口于 springboot 模块（event 核心模块零 Spring 依赖）：本基类含 {@code @Autowired} 字段，随装配同迁。
 */
public abstract class EventServiceProvider extends ServiceProvider {

    @Autowired
    protected Dispatcher dispatcher;

    /**
     * 注册监听器到指定事件类型，委托给 {@code Dispatcher#listen(Class, Listener)}。
     *
     * @param eventClass 事件类型
     * @param listener   监听器实例
     * @param <T>        事件泛型
     */
    protected <T extends Event> void listen(Class<T> eventClass, Listener<T> listener) {
        dispatcher.listen(eventClass, listener);
    }
}
