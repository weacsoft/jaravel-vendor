package com.weacsoft.jaravel.vendor.schedule;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Schedule} / {@link ScheduledTask} 定时任务注册与 Cron 表达式解析测试。
 * <p>
 * 覆盖任务注册（call / command）、Laravel 风格调度方法生成的 cron 表达式、
 * Spring {@link CronExpression} 对生成表达式的匹配验证、分布式锁与描述等属性。
 */
class ScheduleTest {

    @Test
    void testCallRegistersTask() {
        Schedule schedule = new Schedule();
        assertEquals(0, schedule.size());

        AtomicBoolean executed = new AtomicBoolean(false);
        ScheduledTask task = schedule.call("greet", () -> executed.set(true));

        assertEquals(1, schedule.size());
        assertNotNull(task.getName());
        assertEquals("greet", task.getName());

        List<ScheduledTask> all = schedule.all();
        assertEquals(1, all.size());
        assertSame(task, all.get(0));

        // 直接执行回调
        task.getCallback().run();
        assertTrue(executed.get());
    }

    @Test
    void testCallWithGeneratedName() {
        Schedule schedule = new Schedule();
        ScheduledTask task = schedule.call(() -> {});
        assertNotNull(task.getName());
        assertTrue(task.getName().startsWith("task-"));
    }

    @Test
    void testCommandRegistersArtisanTask() {
        Schedule schedule = new Schedule();
        ScheduledTask task = schedule.command("user:birthday:send", new String[]{"--dry-run"});

        assertTrue(task.isArtisanCommand(), "command 应注册为 artisan 命令任务");
        assertEquals("user:birthday:send", task.getArtisanCommand());
        assertEquals(1, task.getArtisanArgs().length);
        assertEquals("--dry-run", task.getArtisanArgs()[0]);
        assertEquals("cmd:user:birthday:send", task.getName());
    }

    @Test
    void testCommandWithoutArgs() {
        Schedule schedule = new Schedule();
        ScheduledTask task = schedule.command("cache:clear");

        assertTrue(task.isArtisanCommand());
        assertEquals("cache:clear", task.getArtisanCommand());
        assertEquals(0, task.getArtisanArgs().length);
    }

    @Test
    void testCronExpressionGeneration() {
        Schedule schedule = new Schedule();
        ScheduledTask task = schedule.call("t", () -> {});

        // everyMinute -> 0 * * * * *
        task.everyMinute();
        assertEquals("0 * * * * *", task.getCronExpression());

        task.everyNMinutes(5);
        assertEquals("0 */5 * * * *", task.getCronExpression());

        task.hourly();
        assertEquals("0 0 * * * *", task.getCronExpression());

        task.hourlyAt(30);
        assertEquals("0 30 * * * *", task.getCronExpression());

        task.dailyAt("18:30");
        assertEquals("0 30 18 * * *", task.getCronExpression());

        task.daily();
        assertEquals("0 0 0 * * *", task.getCronExpression());

        task.twiceDailyAt(1, 13);
        assertEquals("0 0 1,13 * * *", task.getCronExpression());

        task.weekly();
        assertEquals("0 0 0 * * 0", task.getCronExpression());

        task.monthly();
        assertEquals("0 0 0 1 * *", task.getCronExpression());

        task.cron("30 14 1 1 * *");
        assertEquals("30 14 1 1 * *", task.getCronExpression());
    }

    @Test
    void testCronExpressionMatchingWithSpringCron() {
        // 验证 dailyAt("18:30") 生成的 cron 能被 Spring CronExpression 正确匹配
        CronExpression cron = CronExpression.parse("0 30 18 * * *");

        // 18:29 之后下一个匹配点应为 18:30:00
        LocalDateTime before = LocalDateTime.of(2026, 6, 27, 18, 29, 0);
        LocalDateTime next = cron.next(before);
        assertNotNull(next);
        assertEquals(LocalDateTime.of(2026, 6, 27, 18, 30, 0), next);

        // 18:30:00 之后下一个匹配点应为次日 18:30:00
        LocalDateTime after = cron.next(LocalDateTime.of(2026, 6, 27, 18, 30, 0));
        assertEquals(LocalDateTime.of(2026, 6, 28, 18, 30, 0), after);
    }

    @Test
    void testEveryMinuteMatchesCurrentMinute() {
        // everyMinute 在任意分钟（秒=0）都应匹配
        CronExpression cron = CronExpression.parse("0 * * * * *");
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        LocalDateTime lastMinute = now.minusMinutes(1);
        LocalDateTime next = cron.next(lastMinute);
        assertNotNull(next);
        assertEquals(now, next, "everyMinute 应匹配当前分钟");
    }

    @Test
    void testDistributedLockAndDescription() {
        Schedule schedule = new Schedule();
        ScheduledTask task = schedule.call("locked-task", () -> {});

        assertFalse(task.isDistributedLock());
        assertEquals(300, task.getLockTtlSeconds(), "默认锁 TTL 应为 300 秒");

        task.withDistributedLock(600);
        assertTrue(task.isDistributedLock());
        assertEquals(600, task.getLockTtlSeconds());

        task.withDistributedLock();
        assertEquals(300, task.getLockTtlSeconds(), "无参版本应恢复默认 300 秒");

        task.description("每日清理临时文件");
        assertEquals("每日清理临时文件", task.getDescription());
    }

    /**
     * 验证 {@link ScheduleRunner} 能执行到期的 everyMinute 任务。
     * <p>
     * ScheduleRunner 的 artisanApplication 与 redisLockProvider 传 null（均为可选依赖），
     * 注册一个 everyMinute 回调任务后调用 run()，回调应被异步执行。
     */
    @Test
    void testScheduleRunnerExecutesDueTask() throws InterruptedException {
        Schedule schedule = new Schedule();
        CountDownLatch latch = new CountDownLatch(1);
        schedule.call("runner-test", () -> latch.countDown()).everyMinute();

        ScheduleRunner runner = new ScheduleRunner(schedule, null, null);
        try {
            runner.run();
            assertTrue(latch.await(5, TimeUnit.SECONDS), "everyMinute 任务应在 run() 后执行");

            // executedCount 在回调执行之后才递增，需短暂轮询避免竞态
            long deadline = System.currentTimeMillis() + 2000;
            while (runner.getExecutedCount() < 1 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertEquals(1, runner.getExecutedCount(), "已执行计数应为 1");
            assertEquals(0, runner.getFailedCount(), "失败计数应为 0");
        } finally {
            runner.shutdown();
        }
    }
}
