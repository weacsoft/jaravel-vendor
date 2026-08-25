package com.weacsoft.jaravel.vendor.springboot.queuedatabase;

import com.weacsoft.jaravel.vendor.core.lookup.BeanLookup;
import com.weacsoft.jaravel.vendor.core.queue.QueueDriver;
import com.weacsoft.jaravel.vendor.core.queue.QueueDriverHolder;
import com.weacsoft.jaravel.vendor.core.queue.RegisterQueueDriver;
import com.weacsoft.jaravel.vendor.core.registrar.SingletonRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 扫描 {@link RegisterQueueDriver @RegisterQueueDriver} 注解方法，
 * 注册全局唯一的 {@link QueueDriver}。
 * <p>
 * 继承 core 的纯 {@link SingletonRegistrar}，具备<b>唯一性约束</b>：
 * 存在多个注册项时启动报错，除非其中一个显式声明 {@code override = true}。
 * 宿主在单例就绪后调用 {@link #scan()} 执行扫描（Spring 宿主经
 * {@code SmartInitializingSingleton} 包装，保持原有时序）。
 *
 * <h3>解析优先级（从高到低）</h3>
 * <ol>
 *   <li>{@code @RegisterQueueDriver(override = true)} 注解方法</li>
 *   <li>{@code @RegisterQueueDriver} 注解方法</li>
 *   <li>自动装配的 {@link QueueDriver} Bean（redis → database）</li>
 *   <li>无驱动：退化为 sync 同步模式（由 event 模块的内存队列承接）</li>
 * </ol>
 *
 * <h3>P3 解耦说明</h3>
 * P3 前位于 core 模块（{@code core.queue}）；因兜底解析需要容器「按类型枚举 Bean」
 * 能力，随队列装配一并迁入 springboot，与 {@code QueueDatabaseAutoConfiguration} 同包，
 * 使 {@code core.queue} 只保留纯契约（QueuedJob / QueueDriver / QueueDriverHolder / RegisterQueueDriver）。
 */
public class QueueDriverRegistrar extends SingletonRegistrar<RegisterQueueDriver, QueueDriver> {

    private static final Logger log = LoggerFactory.getLogger(QueueDriverRegistrar.class);

    private final QueueDriverHolder holder;

    public QueueDriverRegistrar(QueueDriverHolder holder) {
        super(RegisterQueueDriver.class, QueueDriver.class);
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
        BeanLookup lookup = lookup();
        for (QueueDriver candidate : lookup.beansOfType(QueueDriver.class).values()) {
            if (candidate != holder && !(candidate instanceof QueueDriverHolder)) {
                return candidate;
            }
        }
        return null;
    }
}
