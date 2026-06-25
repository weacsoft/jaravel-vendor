# queue-database AI-API Reference

> Module: `queue-database` | Package: `com.weacsoft.jaravel.vendor.queue.database` | Version: 0.1.0

## Overview

queue-database 模块提供了基于数据库的任务队列实现。`DatabaseQueueDriver` 将任务以 JSON 序列化存储到数据库表 `jaravel_jobs`，支持延迟执行、重试次数限制和失败任务归档。`DatabaseQueueWorker` 在后台线程中轮询数据库，取出可用任务并通过反射调用 Job 处理类。适用于不需要引入 Redis/RabbitMQ 等中间件的轻量级异步任务场景。

## Classes & Interfaces

### QueueDriver
- **Type**: interface
- **Package**: `com.weacsoft.jaravel.vendor.queue.database`
- **Description**: 队列驱动接口，定义任务入队、出队、释放和统计的标准方法。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `push` | `String queue, String job, Object payload, long delaySeconds` | `String` | 将任务推入指定队列，返回任务 ID |
| `pop` | `String queue` | `QueuedJob` | 从队列取出一个可用任务，无任务返回 null |
| `size` | `String queue` | `long` | 获取队列中待处理任务数量 |
| `release` | `String queue, QueuedJob job, long delaySeconds` | `void` | 将任务重新放回队列（重试场景） |
| `delete` | `String queue, String jobId` | `void` | 从队列中删除指定任务 |
| `clear` | `String queue` | `void` | 清空指定队列的所有任务 |

#### Usage Example
```java
QueueDriver driver = new DatabaseQueueDriver(jdbcTemplate, objectMapper);
String jobId = driver.push("emails", "SendEmailJob", emailPayload, 0);
QueuedJob job = driver.pop("emails");
```

### DatabaseQueueDriver
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.queue.database`
- **Implements**: `com.weacsoft.jaravel.vendor.queue.database.QueueDriver`
- **Description**: 基于数据库的队列驱动实现。使用 `JdbcTemplate` 操作 `jaravel_jobs` 表，任务 payload 通过 Jackson `ObjectMapper` 序列化为 JSON。`pop` 使用 `SELECT ... FOR UPDATE SKIP LOCKED`（MySQL）或 `SELECT ... FOR UPDATE` 实现并发安全的任务取出。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `DatabaseQueueDriver` | `JdbcTemplate jdbcTemplate, ObjectMapper objectMapper` | - | 构造数据库队列驱动 |
| `push` | `String queue, String job, Object payload, long delaySeconds` | `String` | 插入任务记录，available_at = now + delay |
| `pop` | `String queue` | `QueuedJob` | 取出最早可用任务并标记为 reserved |
| `size` | `String queue` | `long` | 统计队列中 available 状态任务数 |
| `release` | `String queue, QueuedJob job, long delaySeconds` | `void` | 重置任务为 available 状态，增加 attempts |
| `delete` | `String queue, String jobId` | `void` | 删除任务记录 |
| `clear` | `String queue` | `void` | 删除队列中所有任务 |
| `getFailedJobs` | `String queue, int limit` | `List<QueuedJob>` | 获取失败任务列表（attempts 超过最大重试次数） |
| `retry` | `String queue, String jobId` | `void` | 重试失败任务，重置 attempts 为 0 |

#### Usage Example
```java
DatabaseQueueDriver driver = new DatabaseQueueDriver(jdbcTemplate, objectMapper);

// 推入即时任务
String jobId = driver.push("default", "ProcessOrderJob", orderData, 0);

// 推入延迟任务（60 秒后执行）
String delayedId = driver.push("default", "SendReminderJob", reminderData, 60);

// 取出并处理
QueuedJob job = driver.pop("default");
if (job != null) {
    try {
        processJob(job);
        driver.delete("default", job.getId());
    } catch (Exception e) {
        driver.release("default", job, 30);  // 30 秒后重试
    }
}
```

### DatabaseQueueWorker
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.queue.database`
- **Description**: 数据库队列消费者。在独立线程中循环调用 `DatabaseQueueDriver.pop()` 取出任务，通过反射实例化 Job 类并调用 `handle(Object payload)` 方法处理。支持最大重试次数、异常捕获和失败任务归档。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `DatabaseQueueWorker` | `DatabaseQueueDriver driver, String queue, int maxAttempts` | - | 构造队列消费者 |
| `start` | - | `void` | 启动消费线程 |
| `stop` | - | `void` | 停止消费线程 |
| `isRunning` | - | `boolean` | 检查消费者是否正在运行 |
| `getProcessedCount` | - | `long` | 获取已处理任务总数 |
| `getFailedCount` | - | `long` | 获取失败任务总数 |
| `setSleepInterval` | `long millis` | `void` | 设置无任务时的休眠间隔，默认 1000ms |

#### Usage Example
```java
DatabaseQueueWorker worker = new DatabaseQueueWorker(driver, "emails", 3);
worker.setSleepInterval(500);
worker.start();

// ... 运行中

worker.stop();
System.out.println("Processed: " + worker.getProcessedCount());
```

### QueuedJob
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.queue.database`
- **Description**: 队列任务实体，封装任务 ID、队列名、Job 类名、payload（JSON）、尝试次数、可用时间和保留时间。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `getId` | - | `String` | 获取任务 ID |
| `getQueue` | - | `String` | 获取队列名 |
| `getJob` | - | `String` | 获取 Job 处理类全限定名 |
| `getPayload` | - | `String` | 获取 payload JSON 字符串 |
| `getPayload` | `Class<T> type` | `T` | 反序列化 payload 为指定类型 |
| `getAttempts` | - | `int` | 获取尝试次数 |
| `getAvailableAt` | - | `long` | 获取可用时间戳（毫秒） |
| `getReservedAt` | - | `long` | 获取保留时间戳（毫秒） |
| `setId` | `String id` | `void` | 设置任务 ID |
| `setQueue` | `String queue` | `void` | 设置队列名 |
| `setJob` | `String job` | `void` | 设置 Job 类名 |
| `setPayload` | `String payload` | `void` | 设置 payload JSON |
| `setAttempts` | `int attempts` | `void` | 设置尝试次数 |
| `setAvailableAt` | `long availableAt` | `void` | 设置可用时间 |
| `setReservedAt` | `long reservedAt` | `void` | 设置保留时间 |

#### Usage Example
```java
QueuedJob job = driver.pop("default");
if (job != null) {
    OrderData data = job.getPayload(OrderData.class);
    System.out.println("Processing job: " + job.getId() + ", attempts: " + job.getAttempts());
}
```

### QueueDatabaseProperties
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.queue.database`
- **Annotations**: `@ConfigurationProperties(prefix = "jaravel.queue.database")`
- **Description**: 数据库队列配置属性，前缀 `jaravel.queue.database`。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `isEnabled` | - | `boolean` | 是否启用数据库队列，默认 true |
| `setEnabled` | `boolean enabled` | `void` | 设置是否启用 |
| `getTable` | - | `String` | 获取队列表名，默认 `jaravel_jobs` |
| `setTable` | `String table` | `void` | 设置队列表名 |
| `getDefaultQueue` | - | `String` | 获取默认队列名，默认 `default` |
| `setDefaultQueue` | `String defaultQueue` | `void` | 设置默认队列名 |
| `getMaxAttempts` | - | `int` | 获取最大重试次数，默认 3 |
| `setMaxAttempts` | `int maxAttempts` | `void` | 设置最大重试次数 |
| `getSleepInterval` | - | `long` | 获取休眠间隔（毫秒），默认 1000 |
| `setSleepInterval` | `long sleepInterval` | `void` | 设置休眠间隔 |

#### Usage Example
```yaml
# application.yml
jaravel:
  queue:
    database:
      enabled: true
      table: jaravel_jobs
      default-queue: default
      max-attempts: 5
      sleep-interval: 500
```

### QueueDatabaseAutoConfiguration
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.queue.database`
- **Annotations**: `@AutoConfiguration`, `@ConditionalOnClass({DatabaseQueueDriver, JdbcTemplate})`, `@ConditionalOnBean(JdbcTemplate)`, `@ConditionalOnProperty(prefix = "jaravel.queue.database", name = "enabled", havingValue = "true", matchIfMissing = true)`
- **Description**: 数据库队列自动装配。当 `JdbcTemplate` 存在时，创建 `DatabaseQueueDriver` 和 `DatabaseQueueWorker` Bean，并注册 `database` 驱动到 `QueueManager`。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `databaseQueueDriver` | `JdbcTemplate jdbcTemplate, QueueDatabaseProperties properties` | `DatabaseQueueDriver` | 创建数据库队列驱动 Bean（`@Bean`, `@ConditionalOnMissingBean`） |
| `databaseQueueWorker` | `DatabaseQueueDriver driver, QueueDatabaseProperties properties` | `DatabaseQueueWorker` | 创建队列消费者 Bean（`@Bean`, `@ConditionalOnMissingBean`） |

#### Usage Example
```java
// 自动装配后，通过 Queue facade 使用
Queue::push("SendEmailJob", emailData, "emails");
Queue::later(60, "SendReminderJob", reminderData, "default");
```
