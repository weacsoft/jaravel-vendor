package com.weacsoft.jaravel.vendor.schedule;

import com.weacsoft.jaravel.vendor.artisan.ArtisanApplication;
import com.weacsoft.jaravel.vendor.redis.lock.RedisLockProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务自动装配。
 * <p>
 * 创建 {@link Schedule} 和 {@link ScheduleRunner} bean，
 * 启用 Spring {@link EnableScheduling} 驱动 {@link ScheduleRunner#run()} 定期执行。
 * <p>
 * 配置项：
 * <pre>
 * jaravel:
 *   schedule:
 *     enabled: true    # 是否启用定时任务调度
 * </pre>
 * <p>
 * 业务方通过注入 {@link Schedule} 注册任务：
 * <pre>
 * &#64;Bean
 * public Schedule schedule(Schedule schedule) {
 *     schedule.call(() -> scoreService.cacheScore())
 *            .dailyAt("18:30")
 *            .withDistributedLock();
 *     return schedule;
 * }
 * </pre>
 */
@AutoConfiguration
@AutoConfigureAfter(com.weacsoft.jaravel.vendor.artisan.ArtisanAutoConfiguration.class)
@ConditionalOnClass(Schedule.class)
@ConditionalOnProperty(prefix = "jaravel.schedule", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class ScheduleAutoConfiguration {

    /**
     * Schedule bean：任务注册表。
     */
    @Bean
    @ConditionalOnMissingBean
    public Schedule schedule() {
        return new Schedule();
    }

    /**
     * ScheduleRunner bean：任务执行器。
     * <p>
     * 当 {@link RedisLockProvider} 存在时启用分布式锁，否则分布式锁任务降级为单机执行。
     */
    @Bean
    @ConditionalOnMissingBean
    public ScheduleRunner scheduleRunner(Schedule schedule,
                                          org.springframework.beans.factory.ObjectProvider<ArtisanApplication> artisanProvider,
                                          org.springframework.beans.factory.ObjectProvider<RedisLockProvider> lockProvider) {
        return new ScheduleRunner(
                schedule,
                artisanProvider.getIfAvailable(),
                lockProvider.getIfAvailable()
        );
    }
}
