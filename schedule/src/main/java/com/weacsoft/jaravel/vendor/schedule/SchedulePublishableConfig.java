package com.weacsoft.jaravel.vendor.schedule;

import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;

/**
 * schedule 模块的可发布配置类模板，
 * 由 {@code artisan vendor:publish --tag=schedule} 发布。
 * <p>
 * 发布后在业务工程生成 {@code config/ScheduleConfig.java}，
 * 内含 {@code jaravel.schedule.*} 配置项说明与任务注册示例。
 */
public class SchedulePublishableConfig implements PublishableConfig {

    @Override
    public String tag() {
        return "schedule";
    }

    @Override
    public String className() {
        return "ScheduleConfig";
    }

    @Override
    public String description() {
        return "定时任务调度配置（启用开关、任务注册入口）";
    }

    @Override
    public String source(String basePackage) {
        return "package " + basePackage + ".config;\n"
                + """

                import com.weacsoft.jaravel.vendor.schedule.RegisterSchedule;
                import com.weacsoft.jaravel.vendor.schedule.Schedule;
                import com.weacsoft.jaravel.vendor.schedule.ScheduledTask;
                import org.springframework.context.annotation.Configuration;

                /**
                 * 定时任务配置，对齐 Laravel 的 Console Kernel schedule。
                 * <p>
                 * 由 {@code artisan vendor:publish --tag=schedule} 发布生成，可自由修改。
                 *
                 * <h3>配置项（application.yml）</h3>
                 * <pre>
                 * jaravel:
                 *   schedule:
                 *     enabled: true    # 是否启用定时任务调度，默认 true
                 * </pre>
                 *
                 * <h3>如何注册任务</h3>
                 * 使用 {@code @RegisterSchedule} 注解标记方法，方法返回
                 * {@link ScheduledTask} 实例，框架会自动扫描并注册：
                 * <pre>{@code
                 * @RegisterSchedule
                 * public ScheduledTask inspire(Schedule schedule) {
                 *     return schedule.createTask("inspire", () -> {
                 *         System.out.println("Inspire!");
                 *     }).everyMinute();
                 * }
                 * }</pre>
                 *
                 * <h3>说明</h3>
                 * <ul>
                 *   <li>本类只提供任务注册入口，<b>不会</b>产生额外的 Spring Bean 冲突。</li>
                 *   <li>删除本文件不影响启动。</li>
                 * </ul>
                 */
                @Configuration
                public class ScheduleConfig {

                    @RegisterSchedule
                    public ScheduledTask inspire(Schedule schedule) {
                        return schedule.createTask("inspire", () -> {
                            System.out.println("Inspire!");
                        }).everyMinute();
                    }
                }
                """;
    }
}
