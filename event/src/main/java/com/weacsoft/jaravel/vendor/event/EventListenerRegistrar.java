package com.weacsoft.jaravel.vendor.event;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 事件监听器自动注册器，对齐 Laravel {@code EventServiceProvider::boot()} 中批量注册监听器的行为。
 * <p>
 * 在所有单例 Bean 实例化完成后，扫描容器中所有 {@link Listener} Bean，
 * 若其标注了 {@link ListensTo}，则按注解声明的事件类型注册到 {@link Dispatcher}。
 * <p>
 * 采用构造器注入，确保 {@link Dispatcher} 与 {@link ApplicationContext} 在注册前已就绪。
 */
@Component
public class EventListenerRegistrar implements SmartInitializingSingleton {

    private final Dispatcher dispatcher;
    private final ApplicationContext applicationContext;

    public EventListenerRegistrar(Dispatcher dispatcher, ApplicationContext applicationContext) {
        this.dispatcher = dispatcher;
        this.applicationContext = applicationContext;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void afterSingletonsInstantiated() {
        Map<String, Listener> beans = applicationContext.getBeansOfType(Listener.class);
        for (Listener bean : beans.values()) {
            ListensTo anno = bean.getClass().getAnnotation(ListensTo.class);
            if (anno != null) {
                dispatcher.listen(anno.value(), bean);
            }
        }
    }
}
