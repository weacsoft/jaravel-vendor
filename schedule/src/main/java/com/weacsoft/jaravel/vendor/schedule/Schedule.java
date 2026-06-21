package com.weacsoft.jaravel.vendor.schedule;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 定时任务调度器，对齐 Laravel {@code Illuminate\Console\Scheduling\Schedule}。
 * <p>
 * 维护任务注册表，提供 Laravel 风格的调度方法（{@code call} / {@code command}）。
 * 由 {@link ScheduleRunner} 定期检查并执行到期任务。
 *
 * <h3>使用方式</h3>
 * <pre>
 * &#64;Bean
 * public Schedule schedule(Schedule schedule) {
 *     schedule.call(() -> scoreService.cacheScore())
 *            .dailyAt("18:30")
 *            .withDistributedLock();
 *     schedule.command("user:birthday:send")
 *            .dailyAt("11:50")
 *            .withDistributedLock();
 *     return schedule;
 * }
 * </pre>
 *
 * <h3>线程安全</h3>
 * 使用 {@link CopyOnWriteArrayList} 维护任务列表，支持运行时动态添加任务。
 */
public class Schedule {

    /** 任务列表，进程级共享 */
    private final List<ScheduledTask> tasks = new CopyOnWriteArrayList<>();

    /**
     * 注册一个回调任务。
     *
     * @param callback 任务执行体
     * @return 任务对象，可链式调用调度方法
     */
    public ScheduledTask call(Runnable callback) {
        return call("task-" + System.nanoTime(), callback);
    }

    /**
     * 注册一个命名回调任务。
     *
     * @param name     任务名称（用于日志和分布式锁）
     * @param callback 任务执行体
     * @return 任务对象
     */
    public ScheduledTask call(String name, Runnable callback) {
        ScheduledTask task = new ScheduledTask(name, callback);
        tasks.add(task);
        return task;
    }

    /**
     * 注册一个 Artisan 命令任务。
     * <p>
     * 任务执行时调用指定的 artisan 命令。由 {@link ScheduleRunner} 通过
     * {@link com.weacsoft.jaravel.vendor.artisan.ArtisanApplication} 执行命令。
     *
     * @param command artisan 命令名
     * @return 任务对象
     */
    public ScheduledTask command(String command) {
        return command(command, new String[0]);
    }

    /**
     * 注册一个带参数的 Artisan 命令任务。
     *
     * @param command artisan 命令名
     * @param args    命令参数
     * @return 任务对象
     */
    public ScheduledTask command(String command, String[] args) {
        ScheduledTask task = new ScheduledTask("cmd:" + command, () -> {
            // 占位回调：实际执行由 ScheduleRunner 通过 ArtisanApplication 调度
        });
        task.setArtisanCommand(command, args);
        task.description("artisan " + command);
        tasks.add(task);
        return task;
    }

    /**
     * @return 所有已注册的任务
     */
    public List<ScheduledTask> all() {
        return new ArrayList<>(tasks);
    }

    /**
     * @return 任务数量
     */
    public int size() {
        return tasks.size();
    }
}
