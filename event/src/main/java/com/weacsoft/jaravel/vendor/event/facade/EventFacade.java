package com.weacsoft.jaravel.vendor.event.facade;

import com.weacsoft.jaravel.vendor.core.Facade;
import com.weacsoft.jaravel.vendor.event.Dispatcher;
import com.weacsoft.jaravel.vendor.event.Event;
import com.weacsoft.jaravel.vendor.event.Listener;

/**
 * 事件门面，对齐 Laravel {@code Event::} 静态调用。
 * <p>
 * 通过 {@link Facade#resolve(Class)} 从 Spring 容器解析 {@link Dispatcher}，
 * 暴露与 Laravel 一致的静态 API，便于在业务代码中以静态方式分发与监听事件。
 * <pre>
 * EventFacade.dispatch(new UserRegistered(1L));
 * EventFacade.listen(UserRegistered.class, event -&gt; mailService.send(event.userId));
 * EventFacade.clearListeners(UserRegistered.class);
 * EventFacade.clearAllListeners();
 * </pre>
 */
public final class EventFacade {

    private EventFacade() {
    }

    private static Dispatcher inst() {
        return Facade.resolve(Dispatcher.class);
    }

    public static void dispatch(Event event) {
        inst().dispatch(event);
    }

    public static <T extends Event> void listen(Class<T> eventClass, Listener<T> listener) {
        inst().listen(eventClass, listener);
    }

    public static void clearListeners(Class<? extends Event> eventClass) {
        inst().clearListeners(eventClass);
    }

    public static void clearAllListeners() {
        inst().clearAllListeners();
    }
}
