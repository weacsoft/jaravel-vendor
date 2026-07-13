# schedule AI-API Reference

> Module: `schedule` | Package: `com.weacsoft.jaravel.vendor.schedule` | Version: 0.1.2

## Overview

schedule 模块提供了 Laravel 风格的任务调度框架，支持 Cron 表达式、固定间隔和一次性延迟任务。`Schedule` 负责任务注册，`ScheduledTask` 封装单个调度任务元数据，`ScheduleRunner` 在应用启动时使用 `ScheduledExecutorService` 执行所有已注册任务。当 `RedisLockProvider` 可用时，通过分布式锁确保多实例环境下同一任务只有一个实例执行。

> RedisLockProvider 接口已移至 redis-config 模块（`com.weacsoft.jaravel.vendor.redis.lock.RedisLockProvider`），由 redis-config 模块的 `RedisLockProviderImpl` 提供实现。schedule 模块通过 `ObjectProvider<RedisLockProvider>` 可选注入，当 redis-config 不存在时分布式锁任务降级为单机执行。schedule 模块不再定义 RedisLockProvider，改为依赖 redis-config 模块（依赖方向已修正）。

## Classes & Interfaces

### Schedule
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.schedule`
- **Annotations**: `@Component`
- **Description**: 任务调度注册中心。提供链式 API 注册 Cron 任务、固定间隔任务和一次性延迟任务，支持配置分布式锁。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `command` | `Runnable task` | `ScheduledTask` | 注册一个任务并返回 `ScheduledTask` 用于进一步配置 |
| `cron` | `String expression` | `ScheduledTask` | 注册 Cron 表达式任务 |
| `every` | `long period, TimeUnit unit` | `ScheduledTask` | 注册固定间隔任务 |
| `at` | `long delay, TimeUnit unit` | `ScheduledTask` | 注册一次性延迟任务 |
| `register` | `ScheduledTask task` | `void` | 直接注册 `ScheduledTask` |
| `getTasks` | - | `List<ScheduledTask>` | 获取所有已注册任务 |
| `clear` | - | `void` | 清除所有已注册任务 |

#### Usage Example
```java
@Component
public class ScheduleConfig {
    @Autowired
    private Schedule schedule;

    @PostConstruct
    public void setup() {
        // Cron 表达式任务（每天凌晨 2 点）
        schedule.cron("0 0 2 * * ?")
                .name("daily-report")
                .locked()
                .run(() -> generateDailyReport());

        // 固定间隔任务（每 5 分钟）
        schedule.every(5, TimeUnit.MINUTES)
                .name("sync-data")
                .run(() -> syncExternalData());

        // 一次性延迟任务（30 秒后执行）
        schedule.at(30, TimeUnit.SECONDS)
                .run(() -> initCache());
    }
}
```

### ScheduledTask
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.schedule`
- **Description**: 调度任务封装类。包含任务名称、Cron 表达式/间隔/延迟、Runnable、是否启用分布式锁等元数据。提供链式配置方法。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `name` | `String name` | `ScheduledTask` | 设置任务名称（链式） |
| `locked` | - | `ScheduledTask` | 启用分布式锁（链式） |
| `locked` | `String lockKey` | `ScheduledTask` | 启用分布式锁并指定锁键（链式） |
| `run` | `Runnable runnable` | `ScheduledTask` | 设置任务执行体（链式） |
| `getName` | - | `String` | 获取任务名称 |
| `getCron` | - | `String` | 获取 Cron 表达式 |
| `getPeriod` | - | `long` | 获取固定间隔（毫秒） |
| `getDelay` | - | `long` | 获取延迟时间（毫秒） |
| `getRunnable` | - | `Runnable` | 获取任务执行体 |
| `isLocked` | - | `boolean` | 是否启用分布式锁 |
| `getLockKey` | - | `String` | 获取分布式锁键 |
| `getType` | - | `TaskType` | 获取任务类型（CRON/FIXED_RATE/ONE_TIME） |

#### Usage Example
```java
ScheduledTask task = new ScheduledTask()
    .name("cleanup-temp")
    .cron("0 */30 * * * ?")
    .locked("cleanup:lock")
    .run(() -> cleanupTempFiles());
schedule.register(task);
```

### ScheduleRunner
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.schedule`
- **Implements**: `org.springframework.boot.CommandLineRunner`
- **Annotations**: `@Component`
- **Description**: 调度任务执行器。在应用启动时遍历 `Schedule` 中所有已注册任务，根据任务类型使用 `ScheduledExecutorService` 调度执行。对于启用分布式锁的任务，执行前通过 `RedisLockProvider`（来自 redis-config 模块，通过 `ObjectProvider` 可选注入）尝试获取锁，获取失败则跳过本次执行。当 `RedisLockProvider` 不存在时，分布式锁任务降级为单机执行。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `run` | `String... args` | `void` | CommandLineRunner 入口，调度所有已注册任务 |
| `shutdown` | - | `void` | 关闭调度线程池 |

#### Usage Example
```java
// 自动装配后，ScheduleRunner 在应用启动时自动运行
// 无需手动调用，只需通过 Schedule 注册任务即可
```

### RedisLockProvider（已移至 redis-config 模块）
- **Type**: interface
- **Package**: `com.weacsoft.jaravel.vendor.redis.lock`（原 `com.weacsoft.jaravel.vendor.schedule`）
- **Description**: Redis 分布式锁提供者接口，已从 schedule 模块移至 redis-config 模块。由 redis-config 模块的 `RedisLockProviderImpl` 提供实现。schedule 模块通过 `ObjectProvider<RedisLockProvider>` 可选注入，当 redis-config 不存在时分布式锁任务降级为单机执行。接口定义和方法详情请参见 redis-config 模块文档。

### ScheduleProperties
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.schedule`
- **Annotations**: `@ConfigurationProperties(prefix = "jaravel.schedule")`
- **Description**: 调度配置属性，前缀 `jaravel.schedule`。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `isEnabled` | - | `boolean` | 是否启用调度，默认 true |
| `setEnabled` | `boolean enabled` | `void` | 设置是否启用 |
| `getPoolSize` | - | `int` | 获取线程池大小，默认 4 |
| `setPoolSize` | `int poolSize` | `void` | 设置线程池大小 |
| `isDistributedLock` | - | `boolean` | 是否启用分布式锁，默认 true |
| `setDistributedLock` | `boolean distributedLock` | `void` | 设置是否启用分布式锁 |
| `getLockConnection` | - | `String` | 获取锁 Redis 连接名，默认 `cache` |
| `setLockConnection` | `String lockConnection` | `void` | 设置锁 Redis 连接名 |

#### Usage Example
```yaml
# application.yml
jaravel:
  schedule:
    enabled: true
    pool-size: 8
    distributed-lock: true
    lock-connection: cache
```

### ScheduleAutoConfiguration
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.schedule`
- **Annotations**: `@AutoConfiguration`, `@ConditionalOnClass(Schedule.class)`, `@ConditionalOnProperty(prefix = "jaravel.schedule", name = "enabled", havingValue = "true", matchIfMissing = true)`
- **Description**: 调度自动装配。创建 `Schedule`、`ScheduleRunner` Bean。`ScheduleRunner` 通过 `ObjectProvider<RedisLockProvider>` 可选注入分布式锁提供者（来自 redis-config 模块），当 `RedisLockProvider` 不存在时分布式锁任务降级为单机执行。不再创建 `RedisLockProvider` Bean（由 redis-config 模块的 `RedisAutoConfiguration` 提供）。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `schedule` | - | `Schedule` | 创建调度注册中心 Bean（`@Bean`, `@ConditionalOnMissingBean`） |
| `scheduleRunner` | `Schedule schedule, ObjectProvider<ArtisanApplication> artisanProvider, ObjectProvider<RedisLockProvider> lockProvider` | `ScheduleRunner` | 创建调度执行器 Bean（`@Bean`, `@ConditionalOnMissingBean`）；`RedisLockProvider` 来自 redis-config 模块，通过 `ObjectProvider.getIfAvailable()` 可选注入，不存在时为 null |
