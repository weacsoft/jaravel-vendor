package com.weacsoft.jaravel.vendor.springboot.schedule;

import com.weacsoft.jaravel.vendor.core.publish.PublishableRegistry;
import com.weacsoft.jaravel.vendor.schedule.SchedulePublishableConfig;

/**
 * schedule 模块「发布配置」自动装配。
 * <p>
 * <b>为什么要从 {@link ScheduleAutoConfiguration} 中拆出来单独成类？</b>
 * <p>
 * {@code ScheduleAutoConfiguration} 在<b>类级别</b>带有运行期开关
 * {@code @ConditionalOnProperty(prefix = "jaravel.schedule", name = "enabled", havingValue = "true", matchIfMissing = true)}。
 * 业务方一旦写下 {@code jaravel.schedule.enabled=false}（例如多实例部署时只让其中一台跑调度），
 * 整个自动配置便不再加载，可发布配置声明也随之消失。
 * <p>
 * 而 {@code artisan vendor:publish} 属于<b>构建期脚手架</b>：能否生成
 * {@code ScheduleConfig.java} 模板，与「调度运行期是否启用」是两回事。
 * <p>
 * 因此本类使用静态注册表，确保任何情况下都能执行
 * {@code artisan vendor:publish --tag=schedule}。
 */
@org.springframework.boot.autoconfigure.AutoConfiguration
public class SchedulePublishAutoConfiguration {
    static {
        PublishableRegistry.register(new SchedulePublishableConfig());
    }
}
