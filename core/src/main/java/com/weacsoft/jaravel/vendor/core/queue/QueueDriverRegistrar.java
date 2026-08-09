package com.weacsoft.jaravel.vendor.core.queue;

import com.weacsoft.jaravel.vendor.core.registrar.SingletonRegistrar;
import org.springframework.context.ApplicationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 扫描 {@link RegisterQueueDriver @RegisterQueueDriver} 注解方法，
 * 注册全局唯一的 {@link QueueDriver}。
 * <p>
 * 继承 {@link SingletonRegistrar}，具备<b>唯一性约束</b>：
 * 存在多个注册项时启动报错，除非其中一个显式声明 {@code override = true}。
 *
 * <h3>解析优先级（从高到低）</h3>
 * <ol>
 *   <li>{@code @RegisterQueueDriver(override = true)} 注解方法</li>
 *   <li>{@code @RegisterQueueDriver} 注解方法</li>
 *   <li>自动装配的 {@link QueueDriver} Bean（redis → database）</li>
 *   <li>无驱动：退化为 sync 同步模式（由 event 模块的内存队列承接）</li>
 * </ol>
 */
public class QueueDriverRegistrar extends SingletonRegistrar<RegisterQueueDriver, QueueDriver> {

    private static final Logger log = LoggerFactory.getLogger(QueueDriverRegistrar.class);

    private final QueueDriverHolder holder;

    public QueueDriverRegistrar(ApplicationContext context, QueueDriverHolder holder) {
        super(context, RegisterQueueDriver.class, QueueDriver.class);
        this.holder = holder;
    }

    @Override
    protected boolean isOverride(RegisterQueueDriver annotation) {
        return annotation.override();
    }

    @Override
    protected void apply(QueueDriver instance) {
        holder.set(instance);
        log.info("[queue] 队列驱动: {}（@RegisterQueueDriver）", instance.getClass().getSimpleName());
    }

    @Override
    protected void applyFallback() {
        QueueDriver fromBean = resolveFromBeans();
        if (fromBean != null) {
            holder.set(fromBean);
            log.info("[queue] 队列驱动: {}（自动装配）", fromBean.getClass().getSimpleName());
            return;
        }
        log.info("[queue] 未注册任何队列驱动，使用 sync 同步模式（内存队列）");
    }

    private QueueDriver resolveFromBeans() {
        for (QueueDriver candidate : context.getBeansOfType(QueueDriver.class).values()) {
            if (candidate != holder && !(candidate instanceof QueueDriverHolder)) {
                return candidate;
            }
        }
        return null;
    }
}
