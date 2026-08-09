package com.weacsoft.jaravel.vendor.schedule;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 注解式注册定时任务，对齐项目的 {@code @RegisterCommand} / {@code @RegisterDisk} 等模式。
 * <p>
 * 标注在 {@code @Configuration} / {@code @AutoConfiguration} 类的方法上，
 * 方法返回 {@link ScheduledTask} 实例。
 * {@link ScheduleRegistrar} 会在所有单例初始化完成后扫描此注解，调用方法获取任务实例，
 * 并通过 {@link Schedule#register(ScheduledTask)} 注册。
 * <p>
 * 任务实例<b>不进入 Spring 容器</b>，只存入 Schedule 的内部注册表，
 * 与 {@code @RegisterGuard} / {@code @RegisterDisk} 等注解的设计思路一致。
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
 * @see ScheduleRegistrar
 * @see ScheduledTask
 * @see Schedule
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RegisterSchedule {
    /** 任务描述（可选，用于日志） */
    String value() default "";
}