package com.weacsoft.jaravel.vendor.schedule;

import com.weacsoft.jaravel.vendor.artisan.ArtisanApplication;
import com.weacsoft.jaravel.vendor.redis.lock.RedisLockProvider;
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
 * 通过 Redis SET NX EX 实现分布式锁，确保多机环境下同一任务同一时间只执行一次。
 *
 * <h3>执行策略</h3>
 * <ul>
 *   <li>每分钟（整 10 秒后）扫描所有任务，避免与整点任务冲突</li>
 *   <li>到期任务提交到独立线程池异步执行，不阻塞调度线程</li>
 *   <li>artisan 命令任务通过 {@link ArtisanApplication} 调度</li>
 *   <li>分布式锁任务通过 Redis 抢占，未获取锁的实例跳过执行</li>
 * </ul>
 */
public class ScheduleRunner {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleRunner.class);

    /** 任务调度器 */
    private final Schedule schedule;

    /** Artisan 应用（可选，用于执行 artisan 命令任务） */
    private final ArtisanApplication artisanApplication;

    /** Redis 命令执行器（可选，用于分布式锁） */
    private final RedisLockProvider redisLockProvider;

    /** 任务执行线程池 */
    private final ExecutorService executor;

    /** 统计：已执行任务数 */
    private final AtomicInteger executedCount = new AtomicInteger(0);

    /** 统计：执行失败任务数 */
    private final AtomicInteger failedCount = new AtomicInteger(0);

    public ScheduleRunner(Schedule schedule, ArtisanApplication artisanApplication,
                          RedisLockProvider redisLockProvider) {
        this.schedule = schedule;
        this.artisanApplication = artisanApplication;
        this.redisLockProvider = redisLockProvider;
        this.executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "jaravel-schedule-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        });
        logger.info("[schedule] ScheduleRunner 初始化: {} 个任务", schedule.size());
    }

    /**
     * 每分钟检查并执行到期任务。
     * <p>
     * 延迟 10 秒启动，避免与整点任务的高峰冲突。
     */
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

    /** 判断任务是否在当前时间到期 */
    private boolean isDue(ScheduledTask task, LocalDateTime now) {
        String cron = task.getCronExpression();
        if (cron == null || cron.isEmpty()) {
            return false;
        }
        try {
            // Spring CronExpression 使用 6 字段：秒 分 时 日 月 周
            CronExpression cronExpr = CronExpression.parse(cron);
            // 检查上一分钟是否匹配
            LocalDateTime lastMinute = now.minusMinutes(1);
            LocalDateTime next = cronExpr.next(lastMinute);
            return next != null && next.equals(now);
        } catch (Exception e) {
            logger.error("[schedule] 解析 cron 表达式失败: {} - {}", cron, e.getMessage());
            return false;
        }
    }

    /** 执行单个任务 */
    private void executeTask(ScheduledTask task) {
        String taskName = task.getName();
        try {
            // 分布式锁
            if (task.isDistributedLock() && redisLockProvider != null) {
                String lockKey = "schedule:lock:" + taskName;
                if (!redisLockProvider.tryLock(lockKey, task.getLockTtlSeconds())) {
                    logger.info("[schedule] 任务 '{}' 未获取分布式锁，跳过执行", taskName);
                    return;
                }
            }

            logger.info("[schedule] 执行任务: {} (cron={})", taskName, task.getCronExpression());
            long start = System.currentTimeMillis();

            if (task.isArtisanCommand() && artisanApplication != null) {
                // artisan 命令任务
                int exitCode = artisanApplication.call(task.getArtisanCommand(), task.getArtisanArgs());
                if (exitCode != 0) {
                    throw new RuntimeException("artisan 命令返回非零退出码: " + exitCode);
                }
            } else {
                // 回调任务
                task.getCallback().run();
            }

            long elapsed = System.currentTimeMillis() - start;
            executedCount.incrementAndGet();
            logger.info("[schedule] 任务 '{}' 执行成功, 耗时 {}ms", taskName, elapsed);

        } catch (Exception e) {
            failedCount.incrementAndGet();
            logger.error("[schedule] 任务 '{}' 执行失败: {}", taskName, e.getMessage(), e);
        } finally {
            // 释放分布式锁
            if (task.isDistributedLock() && redisLockProvider != null) {
                String lockKey = "schedule:lock:" + taskName;
                redisLockProvider.unlock(lockKey);
            }
        }
    }

    /** @return 已执行任务数 */
    public int getExecutedCount() {
        return executedCount.get();
    }

    /** @return 执行失败任务数 */
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
