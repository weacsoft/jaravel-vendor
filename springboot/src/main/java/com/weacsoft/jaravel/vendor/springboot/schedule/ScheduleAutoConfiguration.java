package com.weacsoft.jaravel.vendor.springboot.schedule;

import com.weacsoft.jaravel.vendor.artisan.ArtisanApplication;
import com.weacsoft.jaravel.vendor.core.lock.LockProviderManager;
import com.weacsoft.jaravel.vendor.core.lock.LockProviderRegistrar;
import com.weacsoft.jaravel.vendor.schedule.Schedule;
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
 * 创建 {@link Schedule}、{@link LockProviderManager} 和 {@link ScheduleRunner} bean，
 * 启用 Spring {@link EnableScheduling} 驱动 {@link ScheduleRunner#run()} 定期执行。
 * <p>
 * 锁提供者通过 {@code @RegisterLockProvider} 注解注册到 {@link LockProviderManager}，
 * 不进入 Spring 容器。未注册任何 provider 时自动兜底为同步锁（单机执行）。
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

    @Bean
    @ConditionalOnMissingBean
    public Schedule schedule() {
        return new Schedule();
    }

    @Bean
    @ConditionalOnMissingBean
    public ScheduleRegistrar scheduleRegistrar(ApplicationContext context, Schedule schedule) {
        return new ScheduleRegistrar(context, schedule);
    }

    @Bean
    @ConditionalOnMissingBean
    public LockProviderManager lockProviderManager() {
        return new LockProviderManager();
    }

    @Bean
    @ConditionalOnMissingBean
    public LockProviderRegistrar lockProviderRegistrar(ApplicationContext context,
                                                        LockProviderManager lockProviderManager) {
        return new LockProviderRegistrar(context, lockProviderManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public ScheduleRunner scheduleRunner(Schedule schedule,
                                          org.springframework.beans.factory.ObjectProvider<ArtisanApplication> artisanProvider,
                                          LockProviderManager lockProviderManager) {
        return new ScheduleRunner(
                schedule,
                artisanProvider.getIfAvailable(),
                lockProviderManager
        );
    }
}