package com.weacsoft.jaravel.vendor.schedule;

import com.weacsoft.jaravel.vendor.artisan.ArtisanApplication;
import com.weacsoft.jaravel.vendor.core.lock.LockProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
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
 * 业务方通过 {@link RegisterSchedule} 注解注册任务（推荐）：
 * <pre>
 * &#64;Configuration
 * public class MyScheduleConfig {
 *     &#64;RegisterSchedule
 *     public ScheduledTask cacheScore(Schedule schedule) {
 *         return schedule.createTask("cacheScore", () -> scoreService.cacheScore())
 *                        .dailyAt("18:30")
 *                        .withDistributedLock();
 *     }
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
     * ScheduleRegistrar bean：扫描 {@link RegisterSchedule} 注解方法并注册任务。
     */
    @Bean
    @ConditionalOnMissingBean
    public ScheduleRegistrar scheduleRegistrar(ApplicationContext context, Schedule schedule) {
        return new ScheduleRegistrar(context, schedule);
    }

    /**
     * 默认同步锁提供者：单机模式下所有任务直接执行。
     * <p>
     * 引入 Redis 模块后，{@code RedisLockProviderImpl} 会自动覆盖此兜底实现。
     */
    @Bean
    @ConditionalOnMissingBean(LockProvider.class)
    public LockProvider syncLockProvider() {
        return new SyncLockProvider();
    }

    /**
     * ScheduleRunner bean：任务执行器。
     * <p>
     * 通过 {@link LockProvider} 抽象接口实现分布式锁，未引入 Redis 时使用
     * 默认 {@link SyncLockProvider}（单机执行）。
     */
    @Bean
    @ConditionalOnMissingBean
    public ScheduleRunner scheduleRunner(Schedule schedule,
                                          org.springframework.beans.factory.ObjectProvider<ArtisanApplication> artisanProvider,
                                          LockProvider lockProvider) {
        return new ScheduleRunner(
                schedule,
                artisanProvider.getIfAvailable(),
                lockProvider
        );
    }
}
