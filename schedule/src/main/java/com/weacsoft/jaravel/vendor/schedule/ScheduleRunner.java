package com.weacsoft.jaravel.vendor.schedule;

import com.weacsoft.jaravel.vendor.artisan.ArtisanApplication;
import com.weacsoft.jaravel.vendor.core.lock.LockProvider;
import com.weacsoft.jaravel.vendor.core.lock.LockProviderManager;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 定时任务执行器，对齐 Laravel {@code Illuminate\Console\Scheduling\ScheduleRunCommand}。
 * <p>
 * 每分钟检查所有注册的 {@link ScheduledTask}，若 cron 表达式匹配当前时间则执行。
 * <p>
 * <b>分布式锁</b>：当任务启用 {@link ScheduledTask#withDistributedLock()} 时，
 * 通过 {@link LockProviderManager} 获取锁提供者实现分布式锁。
 * 未注册任何 provider 时自动兜底为同步锁（单机执行），
 * 引入 Redis 模块后通过 {@code @RegisterLockProvider} 注册 Redis 分布式锁。
 *
 * <h3>执行策略</h3>
 * <ul>
 *   <li>每分钟（整 10 秒后）扫描所有任务，避免与整点任务冲突</li>
 *   <li>到期任务提交到独立线程池异步执行，不阻塞调度线程</li>
 *   <li>artisan 命令任务通过 {@link ArtisanApplication} 调度</li>
 *   <li>分布式锁任务通过 LockProvider 抢占，未获取锁的实例跳过执行</li>
 * </ul>
 */
public class ScheduleRunner {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleRunner.class);

    private final Schedule schedule;
    private final ArtisanApplication artisanApplication;
    private final LockProviderManager lockProviderManager;
    private final ExecutorService executor;
    private final AtomicInteger executedCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);

    public ScheduleRunner(Schedule schedule, ArtisanApplication artisanApplication,
                          LockProviderManager lockProviderManager) {
        this.schedule = schedule;
        this.artisanApplication = artisanApplication;
        this.lockProviderManager = lockProviderManager != null
                ? lockProviderManager
                : new LockProviderManager();
        this.executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "jaravel-schedule-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        });
        logger.info("[schedule] ScheduleRunner 初始化: {} 个任务", schedule.size());
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 10000)
    public void run() {
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        logger.debug("[schedule] 检查到期任务, time={}", now);

        for (ScheduledTask task : schedule.all()) {
            if (isDue(task, now)) {
                executor.submit(() -> executeTask(task));
            }
        }
    }

    private boolean isDue(ScheduledTask task, LocalDateTime now) {
        String cron = task.getCronExpression();
        if (cron == null || cron.isEmpty()) {
            return false;
        }
        try {
            CronExpression cronExpr = CronExpression.parse(cron);
            LocalDateTime lastMinute = now.minusMinutes(1);
            LocalDateTime next = cronExpr.next(lastMinute);
            return next != null && next.equals(now);
        } catch (Exception e) {
            logger.error("[schedule] 解析 cron 表达式失败: {} - {}", cron, e.getMessage());
            return false;
        }
    }

    private void executeTask(ScheduledTask task) {
        String taskName = task.getName();
        try {
            if (task.isDistributedLock()) {
                LockProvider provider = lockProviderManager.provider();
                String lockKey = "schedule:lock:" + taskName;
                if (!provider.tryLock(lockKey, task.getLockTtlSeconds())) {
                    logger.info("[schedule] 任务 '{}' 未获取分布式锁，跳过执行", taskName);
                    return;
                }
            }

            logger.info("[schedule] 执行任务: {} (cron={})", taskName, task.getCronExpression());
            long start = System.currentTimeMillis();

            if (task.isArtisanCommand() && artisanApplication != null) {
                int exitCode = artisanApplication.call(task.getArtisanCommand(), task.getArtisanArgs());
                if (exitCode != 0) {
                    throw new RuntimeException("artisan 命令返回非零退出码: " + exitCode);
                }
            } else {
                task.getCallback().run();
            }

            long elapsed = System.currentTimeMillis() - start;
            executedCount.incrementAndGet();
            logger.info("[schedule] 任务 '{}' 执行成功, 耗时 {}ms", taskName, elapsed);

        } catch (Exception e) {
            failedCount.incrementAndGet();
            logger.error("[schedule] 任务 '{}' 执行失败: {}", taskName, e.getMessage(), e);
        } finally {
            if (task.isDistributedLock()) {
                LockProvider provider = lockProviderManager.provider();
                String lockKey = "schedule:lock:" + taskName;
                provider.unlock(lockKey);
            }
        }
    }

    public int getExecutedCount() {
        return executedCount.get();
    }

    public int getFailedCount() {
        return failedCount.get();
    }

    @PreDestroy
    public void shutdown() {
        logger.info("[schedule] ScheduleRunner 正在关闭...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("[schedule] ScheduleRunner 已关闭, 总执行: {}, 失败: {}",
                executedCount.get(), failedCount.get());
    }
}