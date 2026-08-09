package com.weacsoft.jaravel.vendor.schedule;

import com.weacsoft.jaravel.vendor.core.lock.LockProviderManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Schedule 与 ScheduleRunner 单元测试。
 */
class ScheduleTest {

    @Test
    @DisplayName("Schedule 注册回调任务并在运行时执行")
    void testScheduleCallAndRun() {
        Schedule schedule = new Schedule();

        AtomicBoolean executed = new AtomicBoolean(false);

        schedule.call("test-task", () -> executed.set(true)).everyMinute();

        assertEquals(1, schedule.size(), "应注册 1 个任务");

        ScheduledTask task = schedule.all().iterator().next();
        assertEquals("test-task", task.getName());
        assertEquals("0 * * * * *", task.getCronExpression());
        assertFalse(task.isArtisanCommand());
        assertFalse(task.isDistributedLock());

        task.getCallback().run();
        assertTrue(executed.get(), "回调应被执行");
    }

    @Test
    @DisplayName("Schedule 注册 artisan 命令任务")
    void testScheduleCommand() {
        Schedule schedule = new Schedule();

        schedule.command("inspire").everyMinute();

        assertEquals(1, schedule.size());

        ScheduledTask task = schedule.all().iterator().next();
        assertTrue(task.isArtisanCommand());
        assertEquals("inspire", task.getArtisanCommand());
        assertEquals("0 * * * * *", task.getCronExpression());
    }

    @Test
    @DisplayName("Schedule 运行时动态注册新任务")
    void testDynamicRegistration() {
        Schedule schedule = new Schedule();
        schedule.call("initial", () -> { }).everyMinute();
        assertEquals(1, schedule.size());

        schedule.call("added", () -> { }).everyMinute();
        assertEquals(2, schedule.size());
    }

    @Test
    @DisplayName("ScheduleRunner 执行到期任务并获取分布式锁")
    void testScheduleRunnerExecuteTask() throws InterruptedException {
        Schedule schedule = new Schedule();
        AtomicInteger counter = new AtomicInteger(0);

        schedule.call("runner-test", () -> counter.incrementAndGet())
                .everyMinute()
                .withDistributedLock();

        ScheduleRunner runner = new ScheduleRunner(schedule, null, new LockProviderManager());

        runner.run();

        Thread.sleep(1000);

        runner.shutdown();
    }

    @Test
    @DisplayName("LockProviderManager 默认同步锁")
    void testLockProviderManagerDefault() {
        LockProviderManager manager = new LockProviderManager();
        assertTrue(manager.provider().tryLock("test-key", 60));
    }

    @Test
    @DisplayName("ScheduledTask 名称重复注册覆盖")
    void testDuplicateNameRegistration() {
        Schedule schedule = new Schedule();
        AtomicBoolean first = new AtomicBoolean(false);
        AtomicBoolean second = new AtomicBoolean(false);

        schedule.register(new ScheduledTask("task-a", () -> first.set(true)).everyMinute());
        schedule.register(new ScheduledTask("task-a", () -> second.set(true)).everyMinute());

        assertEquals(1, schedule.size());
        ScheduledTask task = schedule.all().iterator().next();

        task.getCallback().run();
        assertTrue(second.get());
        assertFalse(first.get(), "首次注册的回调不应被执行");
    }
}