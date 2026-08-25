package com.weacsoft.jaravel.vendor.springboot.schedule;

import com.weacsoft.jaravel.vendor.core.registrar.AnnotationDrivenRegistrar;
import com.weacsoft.jaravel.vendor.schedule.RegisterSchedule;
import com.weacsoft.jaravel.vendor.schedule.Schedule;
import com.weacsoft.jaravel.vendor.schedule.ScheduledTask;

import java.lang.reflect.Method;

/**
 * 定时任务注册器，扫描 {@link RegisterSchedule} 注解方法并注册任务。
 * <p>
 * 对齐 {@code @RegisterCommand} / {@code @RegisterDisk} / {@code @RegisterGuard} 等模式，
 * 在所有单例 Bean 初始化完成后执行扫描。
 * <p>
 * 扫描到的任务<b>不进入 Spring 容器</b>，只存入 {@link Schedule} 的内部注册表，
 * 组件名称与 Spring bean name 解耦，不会触发 {@code BeanDefinitionOverrideException}。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Configuration
 * public class MyScheduleConfig {
 *     @RegisterSchedule
 *     public ScheduledTask cacheScore(Schedule schedule) {
 *         return schedule.createTask("cacheScore", () -> scoreService.cacheScore())
 *                        .dailyAt("18:30")
 *                        .withDistributedLock();
 *     }
 * }
 * }</pre>
 *
 * @see RegisterSchedule
 * @see Schedule
 * @see ScheduledTask
 */
public class ScheduleRegistrar extends AnnotationDrivenRegistrar<RegisterSchedule> {

    private final Schedule schedule;

    public ScheduleRegistrar(Schedule schedule) {
        super(RegisterSchedule.class);
        this.schedule = schedule;
    }

    @Override
    protected void register(Object result, Method method, RegisterSchedule annotation) {
        ScheduledTask task = requireType(result, ScheduledTask.class, method);
        schedule.register(task);
        log.info("[schedule] 注册定时任务: {} (cron={})", task.getName(), task.getCronExpression());
    }
}