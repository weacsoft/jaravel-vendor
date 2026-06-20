package com.weacsoft.jaravel.vendor.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 监听器绑定注解，对齐 Laravel 中「在 {@code EventServiceProvider::$listen} 数组里声明监听器对应事件」的用法。
 * <p>
 * 标注在实现了 {@link Listener} 的 Bean 类上，声明该监听器所监听的事件类型。
 * {@link EventListenerRegistrar} 会在所有单例就绪后扫描带本注解的监听器 Bean，
 * 自动将其注册到 {@link Dispatcher}。
 * <pre>
 * &#64;Component
 * &#64;ListensTo(UserRegistered.class)
 * public class SendWelcomeMail implements Listener&lt;UserRegistered&gt; {
 *     &#64;Override
 *     public void handle(UserRegistered event) { ... }
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ListensTo {

    /**
     * @return 该监听器监听的事件类型
     */
    Class<? extends Event> value();
}
