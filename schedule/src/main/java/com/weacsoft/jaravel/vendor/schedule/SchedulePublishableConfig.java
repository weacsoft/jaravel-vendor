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

                import com.weacsoft.jaravel.vendor.schedule.Schedule;
                import org.springframework.beans.factory.ObjectProvider;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;

                import java.util.LinkedHashMap;

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
                 * 注入框架的 {@code Schedule} Bean 后调用其注册方法即可，例如在
                 * {@code @PostConstruct} 或 {@code ApplicationRunner} 中：
                 * <pre>{@code
                 * schedule.command("inspire").everyMinute();
                 * }</pre>
                 *
                 * <h3>说明</h3>
                 * <ul>
                 *   <li>本类只读取配置生成一份快照，<b>不会</b>覆盖框架自动装配的 Schedule / ScheduleRunner。</li>
                 *   <li>删除本文件不影响启动。</li>
                 * </ul>
                 */
                @Configuration
                public class ScheduleConfig {

                    /**
                     * 定时任务模块生效状态快照。
                     *
                     * @param provider Schedule 提供者（模块未启用时为空）
                     * @return 解析后的配置键值对
                     */
                    @Bean
                    public LinkedHashMap<String, Object> scheduleConfigMetadata(ObjectProvider<Schedule> provider) {
                        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
                        Schedule schedule = provider.getIfAvailable();
                        metadata.put("jaravel.schedule.enabled", schedule != null);
                        metadata.put("jaravel.schedule.registry",
                                schedule == null ? "未装配（schedule 模块未启用）" : schedule.getClass().getName());
                        return metadata;
                    }
                }
                """;
    }
}
