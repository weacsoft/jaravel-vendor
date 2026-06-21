package com.weacsoft.jaravel.vendor.schedule;

/**
 * 定时任务，对齐 Laravel {@code Illuminate\Console\Scheduling\Event}。
 * <p>
 * 每个任务包含一个 cron 表达式和一个执行体（{@link Runnable}）。
 * 通过 Builder 模式构建，支持 Laravel 风格的调度方法。
 *
 * <h3>调度方法</h3>
 * 对齐 Laravel 常用调度方法：
 * <ul>
 *   <li>{@code cron(String)} - 自定义 cron 表达式</li>
 *   <li>{@code everyMinute()} - 每分钟</li>
 *   <li>{@code hourly()} - 每小时</li>
 *   <li>{@code dailyAt("18:30")} - 每天指定时间</li>
 *   <li>{@code twiceDailyAt(1, 6)} - 每天指定两个小时</li>
 *   <li>{@code weekly()} - 每周</li>
 *   <li>{@code monthly()} - 每月</li>
 * </ul>
 */
public class ScheduledTask {

    /** 任务名称，用于日志和分布式锁标识 */
    private final String name;

    /** cron 表达式（6 字段：秒 分 时 日 月 周） */
    private String cronExpression;

    /** 任务执行体 */
    private final Runnable callback;

    /** 是否启用分布式锁（防止多机重复执行） */
    private boolean distributedLock = false;

    /** 分布式锁持有时间（秒），默认 300 秒 */
    private long lockTtlSeconds = 300;

    /** 任务描述 */
    private String description = "";

    /** Artisan 命令名（非空时表示该任务执行 artisan 命令，由 ScheduleRunner 调度） */
    private String artisanCommand;

    /** Artisan 命令参数 */
    private String[] artisanArgs = new String[0];

    public ScheduledTask(String name, Runnable callback) {
        this.name = name;
        this.callback = callback;
    }

    /** 标记此任务为 artisan 命令任务 */
    void setArtisanCommand(String command, String[] args) {
        this.artisanCommand = command;
        this.artisanArgs = args != null ? args : new String[0];
    }

    /** @return 是否为 artisan 命令任务 */
    public boolean isArtisanCommand() {
        return artisanCommand != null;
    }

    public String getArtisanCommand() {
        return artisanCommand;
    }

    public String[] getArtisanArgs() {
        return artisanArgs;
    }

    // ---- 调度方法（对齐 Laravel） ----

    /**
     * 自定义 cron 表达式。
     *
     * @param expression 6 字段 cron：秒 分 时 日 月 周
     */
    public ScheduledTask cron(String expression) {
        this.cronExpression = expression;
        return this;
    }

    /** 每分钟执行 */
    public ScheduledTask everyMinute() {
        return cron("0 * * * * *");
    }

    /** 每 N 分钟执行 */
    public ScheduledTask everyNMinutes(int n) {
        return cron("0 */" + n + " * * * *");
    }

    /** 每小时执行（整点） */
    public ScheduledTask hourly() {
        return cron("0 0 * * * *");
    }

    /** 每小时的第 N 分钟执行 */
    public ScheduledTask hourlyAt(int minute) {
        return cron("0 " + minute + " * * * *");
    }

    /** 每天指定时间执行 */
    public ScheduledTask dailyAt(String time) {
        String[] parts = time.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        return cron("0 " + minute + " " + hour + " * * *");
    }

    /** 每天执行（午夜） */
    public ScheduledTask daily() {
        return dailyAt("00:00");
    }

    /** 每天在两个指定小时执行（分钟为 0） */
    public ScheduledTask twiceDailyAt(int firstHour, int secondHour) {
        return cron("0 0 " + firstHour + "," + secondHour + " * * *");
    }

    /** 每周执行 */
    public ScheduledTask weekly() {
        return cron("0 0 0 * * 0");
    }

    /** 每月执行 */
    public ScheduledTask monthly() {
        return cron("0 0 0 1 * *");
    }

    // ---- 分布式锁 ----

    /**
     * 启用分布式锁，防止多机重复执行。
     *
     * @param ttlSeconds 锁持有时间（秒）
     */
    public ScheduledTask withDistributedLock(long ttlSeconds) {
        this.distributedLock = true;
        this.lockTtlSeconds = ttlSeconds;
        return this;
    }

    /** 启用分布式锁（默认 TTL） */
    public ScheduledTask withDistributedLock() {
        return withDistributedLock(300);
    }

    /** 设置任务描述 */
    public ScheduledTask description(String desc) {
        this.description = desc;
        return this;
    }

    // ---- Getters ----

    public String getName() {
        return name;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public Runnable getCallback() {
        return callback;
    }

    public boolean isDistributedLock() {
        return distributedLock;
    }

    public long getLockTtlSeconds() {
        return lockTtlSeconds;
    }

    public String getDescription() {
        return description;
    }
}
