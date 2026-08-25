package com.weacsoft.jaravel.vendor.springboot.schedule;

import com.weacsoft.jaravel.vendor.core.lock.LockProviderManager;
import com.weacsoft.jaravel.vendor.schedule.Schedule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ScheduleRunner} 单元测试（Spring `@Scheduled` 驱动的调度执行器，
 * 随 schedule 去 Spring 化迁入 springboot 模块）。
 */
class ScheduleRunnerTest {

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
}
