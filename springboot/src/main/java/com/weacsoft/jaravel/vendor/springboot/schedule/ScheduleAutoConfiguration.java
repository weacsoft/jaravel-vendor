package com.weacsoft.jaravel.vendor.springboot.schedule;

import com.weacsoft.jaravel.vendor.artisan.ArtisanApplication;
import com.weacsoft.jaravel.vendor.core.lock.LockProviderManager;
import com.weacsoft.jaravel.vendor.core.lock.LockProviderRegistrar;
import com.weacsoft.jaravel.vendor.schedule.Schedule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

    /**
     * 定时任务注册器（P3：core 纯扫描器；扫描由下方 SmartInitializingSingleton 触发，
     * 保持原「所有单例就绪后扫描」时序）。
     */
    @Bean
    @ConditionalOnMissingBean
    public ScheduleRegistrar scheduleRegistrar(Schedule schedule) {
        return new ScheduleRegistrar(schedule);
    }

    /**
     * 定时任务注册器扫描触发。
     */
    @Bean
    public SmartInitializingSingleton scheduleRegistrarScanner(ScheduleRegistrar registrar) {
        return registrar::scan;
    }

    @Bean
    @ConditionalOnMissingBean
    public LockProviderManager lockProviderManager() {
        return new LockProviderManager();
    }

    /**
     * 锁提供者注册器（P3：core 纯扫描器，@RegisterLockProvider 由 scan() 触发）。
     */
    @Bean
    @ConditionalOnMissingBean
    public LockProviderRegistrar lockProviderRegistrar(LockProviderManager lockProviderManager) {
        return new LockProviderRegistrar(lockProviderManager);
    }

    /**
     * 锁提供者注册器扫描触发。
     */
    @Bean
    public SmartInitializingSingleton lockProviderRegistrarScanner(LockProviderRegistrar registrar) {
        return registrar::scan;
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