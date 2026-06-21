package com.weacsoft.jaravel.vendor.event;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * 事件模块自动装配，对齐 Laravel {@code EventServiceProvider} 与队列配置。
 * <p>
 * 注册以下 Bean：
 * <ul>
 *   <li>{@link QueueManager}：队列管理器，管理命名队列的独立线程池与重试配置；</li>
 *   <li>{@link EventDispatcher}（作为 {@link Dispatcher} 契约实现）：使用 QueueManager 进行异步分发；</li>
 *   <li>{@link EventListenerRegistrar}：扫描 {@link ListensTo} 注解自动注册监听器。</li>
 * </ul>
 * 并依据 {@link EventProperties} 配置异步分发开关、队列大小与重试参数。
 * <p>
 * 所有 Bean 均带 {@code @ConditionalOnMissingBean}，允许业务方自定义替换。
 */
@AutoConfiguration
@ConditionalOnClass(Dispatcher.class)
@EnableConfigurationProperties(EventProperties.class)
public class EventAutoConfiguration {

    /**
     * 注册队列管理器，按配置初始化默认线程池大小、各队列大小覆盖与重试参数。
     *
     * @param properties 事件配置属性
     * @return 队列管理器实例
     */
    @Bean
    @ConditionalOnMissingBean(QueueManager.class)
    public QueueManager queueManager(EventProperties properties) {
        return new QueueManager(properties);
    }

    /**
     * 注册事件调度器，注入 QueueManager 并按配置初始化异步分发开关。
     * <p>
     * 当容器中存在 {@link QueueDispatcher} 时，自动注入以启用持久化队列分发。
     *
     * @param queueManager       队列管理器
     * @param properties         事件配置属性
     * @param queueDispatcherOpt 持久化队列分发器（可选）
     * @return 事件调度器实例
     */
    @Bean
    @ConditionalOnMissingBean(Dispatcher.class)
    public EventDispatcher eventDispatcher(QueueManager queueManager, EventProperties properties,
                                           org.springframework.beans.factory.ObjectProvider<QueueDispatcher> queueDispatcherOpt) {
        EventDispatcher dispatcher = new EventDispatcher(queueManager);
        dispatcher.setQueueEnabled(properties.isQueueEnabled());
        QueueDispatcher qd = queueDispatcherOpt.getIfAvailable();
        if (qd != null) {
            dispatcher.setQueueDispatcher(qd);
        }
        return dispatcher;
    }

    /**
     * 注册监听器自动注册器，在所有单例就绪后扫描 {@link ListensTo} 注解。
     *
     * @param dispatcher       事件调度器
     * @param applicationContext Spring 上下文
     * @return 监听器自动注册器实例
     */
    @Bean
    @ConditionalOnMissingBean(EventListenerRegistrar.class)
    public EventListenerRegistrar eventListenerRegistrar(Dispatcher dispatcher, ApplicationContext applicationContext) {
        return new EventListenerRegistrar(dispatcher, applicationContext);
    }
}
