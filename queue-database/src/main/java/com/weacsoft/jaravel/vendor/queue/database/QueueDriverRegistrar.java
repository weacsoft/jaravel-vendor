package com.weacsoft.jaravel.vendor.queue.database;

import com.weacsoft.jaravel.vendor.core.registrar.SingletonRegistrar;
import org.springframework.context.ApplicationContext;

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
 *
 * <h3>注意</h3>
 * 注解注册的驱动通过 {@link QueueDriverHolder} 生效。若容器中已存在
 * {@link QueueDriver} Bean，则 worker / dispatcher 直接使用该 Bean；
 * 注解方式主要用于「没有引入 DataSource / Redis，但想自定义队列后端」的场景。
 */
public class QueueDriverRegistrar extends SingletonRegistrar<RegisterQueueDriver, QueueDriver> {

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

    /**
     * 未使用注解时：若容器中已有自动装配的驱动则沿用，否则提示进入 sync 模式。
     */
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

    /**
     * 查找容器中的 {@link QueueDriver} Bean，排除 holder 自身以避免自引用。
     */
    private QueueDriver resolveFromBeans() {
        for (QueueDriver candidate : context.getBeansOfType(QueueDriver.class).values()) {
            if (candidate != holder && !(candidate instanceof QueueDriverHolder)) {
                return candidate;
            }
        }
        return null;
    }
}
