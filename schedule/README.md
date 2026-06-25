# schedule

Cron 定时任务调度器，对齐 Laravel `Illuminate\Console\Scheduling`。提供 Laravel 风格的链式调度方法（`dailyAt` / `hourly` / `everyMinute` 等），支持回调任务与 Artisan 命令任务，并通过 Redis 分布式锁防止多机重复执行。

## 依赖

- `core` — 基础设施
- `artisan` — 调度 Artisan 命令任务（`ArtisanApplication`）
- `spring-boot-autoconfigure` — 自动装配与 `@EnableScheduling`
- `spring-context` — `CronExpression` 解析
- `slf4j-api` — 日志

> 分布式锁通过 `RedisLockProvider` 接口抽象，由 `redis-config` 模块提供实现。schedule 模块本身不直接依赖 redis-config，当 redis-config 不存在时分布式锁任务降级为单机执行。

## 核心接口

### Schedule

任务调度器，对齐 Laravel `Illuminate\Console\Scheduling\Schedule`。维护任务注册表，提供 Laravel 风格的调度方法。使用 `CopyOnWriteArrayList` 维护任务列表，支持运行时动态添加任务。

```java
public class Schedule {
    public ScheduledTask call(Runnable callback);                       // 注册回调任务
    public ScheduledTask call(String name, Runnable callback);          // 注册命名回调任务
    public ScheduledTask command(String command);                        // 注册 Artisan 命令任务
    public ScheduledTask command(String command, String[] args);        // 注册带参数的 Artisan 命令任务
    public List<ScheduledTask> all();                                   // 获取所有已注册任务
    public int size();                                                  // 获取任务数量
}
```

### ScheduledTask

定时任务，对齐 Laravel `Illuminate\Console\Scheduling\Event`。通过 Builder 模式构建，支持 Laravel 风格的调度方法。

```java
public class ScheduledTask {
    // 调度方法（对齐 Laravel）
    public ScheduledTask cron(String expression);        // 自定义 6 字段 cron：秒 分 时 日 月 周
    public ScheduledTask everyMinute();                  // 每分钟
    public ScheduledTask everyNMinutes(int n);           // 每 N 分钟
    public ScheduledTask hourly();                        // 每小时（整点）
    public ScheduledTask hourlyAt(int minute);            // 每小时的第 N 分钟
    public ScheduledTask dailyAt(String time);           // 每天指定时间，如 "18:30"
    public ScheduledTask daily();                         // 每天午夜
    public ScheduledTask twiceDailyAt(int firstHour, int secondHour);  // 每天两个指定小时
    public ScheduledTask weekly();                        // 每周
    public ScheduledTask monthly();                       // 每月

    // 分布式锁
    public ScheduledTask withDistributedLock();           // 启用分布式锁（默认 TTL 300 秒）
    public ScheduledTask withDistributedLock(long ttlSeconds);  // 启用分布式锁，指定 TTL

    public ScheduledTask description(String desc);        // 设置任务描述
}
```

### ScheduleRunner

任务执行器，对齐 Laravel `Illuminate\Console\Scheduling\ScheduleRunCommand`。每分钟检查所有注册的任务，若 cron 表达式匹配当前时间则执行。

```java
public class ScheduleRunner {
    public ScheduleRunner(Schedule schedule, ArtisanApplication artisanApplication,
                          RedisLockProvider redisLockProvider);

    @Scheduled(fixedDelay = 60000, initialDelay = 10000)
    public void run();                  // 每分钟检查并执行到期任务

    public int getExecutedCount();       // 已执行任务数
    public int getFailedCount();         // 执行失败任务数
    public void shutdown();              // 优雅关闭线程池
}
```

执行策略：
- 每分钟（延迟 10 秒启动）扫描所有任务，避免与整点任务冲突
- 到期任务提交到独立线程池（4 线程）异步执行，不阻塞调度线程
- Artisan 命令任务通过 `ArtisanApplication` 调度
- 分布式锁任务通过 Redis 抢占，未获取锁的实例跳过执行

### RedisLockProvider

Redis 分布式锁提供者接口。抽象分布式锁实现，使 schedule 模块不直接依赖 redis-config 模块。

```java
public interface RedisLockProvider {
    boolean tryLock(String key, long ttlSeconds);   // 尝试获取锁
    void unlock(String key);                         // 释放锁
}
```

### ScheduleProperties

配置属性，前缀 `jaravel.schedule`。

## 配置

```yaml
jaravel:
  schedule:
    enabled: true    # 是否启用定时任务调度（默认 true）
```

## 使用示例

注册定时任务：

```java
@Bean
public Schedule schedule(Schedule schedule) {
    // 回调任务：每天 18:30 缓存成绩，启用分布式锁
    schedule.call(() -> scoreService.cacheScore())
           .dailyAt("18:30")
           .withDistributedLock();

    // Artisan 命令任务：每天 11:50 发送生日祝福
    schedule.command("user:birthday:send")
           .dailyAt("11:50")
           .withDistributedLock();

    // 每小时整点执行
    schedule.call("cleanup-temp", () -> fileService.cleanTemp())
           .hourly();

    return schedule;
}
```

## 自动装配

`ScheduleAutoConfiguration` 通过 `@AutoConfiguration` 注册，在 `ArtisanAutoConfiguration` 之后装配（`@AutoConfigureAfter`）。当 classpath 存在 `Schedule` 类且 `jaravel.schedule.enabled` 为 true（默认）时生效，同时启用 Spring `@EnableScheduling` 驱动 `ScheduleRunner.run()` 定期执行。

创建的 bean：
- `Schedule` — 任务注册表（`@ConditionalOnMissingBean`）
- `ScheduleRunner` — 任务执行器（`@ConditionalOnMissingBean`），通过 `ObjectProvider` 可选注入 `ArtisanApplication` 和 `RedisLockProvider`，当二者不存在时对应功能降级。
