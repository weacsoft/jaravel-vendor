# queue-database AI-API Reference

> Module: `queue-database` | Package: `com.weacsoft.jaravel.vendor.queue.database` | Version: 0.1.2

## Overview

queue-database 模块提供持久化任务队列，对齐 Laravel `Illuminate\Queue`。支持 **database** 与 **redis** 两种驱动，将任务以 JSON 序列化持久化存储，支持多实例消费、延迟执行、重试机制与失败队列（`failed_jobs`）。`DatabaseQueueWorker` 在后台线程中轮询队列取出任务并执行；`DatabaseQueueDispatcher` 桥接 event 模块，将 `ShouldQueue` 事件分发到队列。

## Classes & Interfaces

### QueueDriver
- **Type**: interface
- **Package**: `com.weacsoft.jaravel.vendor.queue.database`
- **Description**: 队列驱动接口，对齐 Laravel `Illuminate\Contracts\Queue\Queue`。定义任务入队、出队、释放、统计与失败队列操作的标准方法。所有驱动实现都必须支持失败队列。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `push` | `String queueName, String payload` | `long` | 推送任务到队列立即执行，返回任务 ID |
| `push` | `String queueName, String payload, long delayMs` | `long` | 延迟推送任务（毫秒），返回任务 ID |
| `pop` | `String queueName` | `QueuedJob` | 弹出一个到期任务（多实例竞争），无任务返回 null |
| `delete` | `long jobId` | `void` | 标记成功，删除任务 |
| `release` | `long jobId` | `void` | 标记失败，释放锁以便重试 |
| `release` | `long jobId, long delayMs` | `void` | 标记失败，释放锁并设置重试延迟 |
| `size` | `String queueName` | `int` | 获取队列中待处理任务数 |
| `clear` | `String queueName` | `void` | 清空指定队列的所有任务 |
| `fail` | `long jobId, String queue, String payload, int attempts, String exception` | `void` | 归档任务到失败队列（对齐 `failed_jobs`） |
| `getFailedJobs` | - | `List<QueuedJob>` | 查询失败任务（最新失败在前，`getId()` 为失败任务 ID） |
| `retryFailedJob` | `long failedJobId` | `void` | 重试失败任务（对齐 `queue:retry`） |
| `deleteFailedJob` | `long failedJobId` | `void` | 删除失败任务（对齐 `queue:forget`） |
| `clearFailedJobs` | - | `void` | 清空所有失败任务（对齐 `queue:flush`） |

#### Usage Example
```java
QueueDriver driver = ...;  // 注入 DatabaseQueueDriver 或 RedisQueueDriver
long jobId = driver.push("emails", payloadJson);
QueuedJob job = driver.pop("emails");
```

### DatabaseQueueDriver
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.queue.database`
- **Implements**: `QueueDriver`
- **Description**: 数据库队列驱动，对齐 Laravel `Illuminate\Queue\DatabaseQueue`。使用 `JdbcTemplate` 操作 `jobs` / `failed_jobs` 表，基于 `reserved_at` 乐观锁实现多实例抢占式消费。**构造时自动建表**（`CREATE TABLE IF NOT EXISTS`）。

#### Constructors

| Constructor | Parameters | Description |
|-------------|-----------|-------------|
| `DatabaseQueueDriver` | `DataSource dataSource, String table, long retryAfterSeconds` | 构造（失败任务保留 7 天） |
| `DatabaseQueueDriver` | `DataSource dataSource, String table, long retryAfterSeconds, int failedJobRetentionDays` | 构造（指定保留天数） |
| `DatabaseQueueDriver` | `DataSource dataSource, String table, String failedTable, long retryAfterSeconds, int failedJobRetentionDays` | 全参数构造 |

#### Additional Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `purgeOldFailedJobs` | - | `void` | 清理超过保留天数的失败任务（对齐 `queue:prune-failed-jobs`） |

#### Usage Example
```java
DatabaseQueueDriver driver = new DatabaseQueueDriver(dataSource, "jobs", 1800, 7);

// 推入即时任务
long jobId = driver.push("default", payloadJson);

// 推入延迟任务（60 秒后执行）
long delayedId = driver.push("default", payloadJson, 60 * 1000L);

// 取出并处理
QueuedJob job = driver.pop("default");
if (job != null) {
    try {
        processJob(job);
        driver.delete(job.getId());
    } catch (Exception e) {
        if (job.getAttempts() < 3) {
            driver.release(job.getId(), 30 * 1000L);  // 30 秒后重试
        } else {
            driver.fail(job.getId(), job.getQueue(), job.getPayload(), job.getAttempts(), e.toString());
        }
    }
}
```

### RedisQueueDriver
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.queue.database`
- **Implements**: `QueueDriver`
- **Description**: Redis 队列驱动，对齐 Laravel `Illuminate\Queue\RedisQueue`。基于 Redis List / ZSET 实现队列存储，通过 `RedisManager.sync()` 获取 `RedisCommands`。多实例通过 RPOP 原子操作 + ZREM 返回值抢占。依赖 `redis-config` 模块。

#### Constructors

| Constructor | Parameters | Description |
|-------------|-----------|-------------|
| `RedisQueueDriver` | `RedisManager redisManager, String connectionName, long retryAfterSeconds, int failedJobRetentionDays` | 构造（connectionName 为 null/空使用默认连接） |

#### Redis Data Structures

| Key | Type | Description |
|-----|------|-------------|
| `jaravel:queue:{queueName}` | List | 就绪队列（LPUSH/RPOP，FIFO） |
| `jaravel:queue:{queueName}:delayed` | ZSET | 延迟队列（score=到期时间戳） |
| `jaravel:queue:{queueName}:reserved` | ZSET | 预约队列（score=预约超时时间戳） |
| `jaravel:queue:failed` | List | 失败队列（LPUSH，最新失败在前） |
| `jaravel:queue:seq` | String | 任务 ID 序列（INCR） |
| `jaravel:queue:failed:seq` | String | 失败任务 ID 序列（INCR） |
| `jaravel:queue:index` | Hash | jobId -> queueName 索引 |

#### Additional Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `purgeOldFailedJobs` | - | `void` | 清理超过保留天数的失败任务 |

#### Usage Example
```java
RedisQueueDriver driver = new RedisQueueDriver(redisManager, null, 1800, 7);
long jobId = driver.push("default", payloadJson);
QueuedJob job = driver.pop("default");
```

### QueuedJob
- **Type**: class (immutable)
- **Package**: `com.weacsoft.jaravel.vendor.queue.database`
- **Description**: 队列任务实体，对齐 Laravel `Illuminate\Queue\Jobs\DatabaseJob`。失败任务也包装为本类，此时 `getException()` 携带失败异常信息，`getId()` 为失败任务 ID。

#### Constructors

| Constructor | Parameters | Description |
|-------------|-----------|-------------|
| `QueuedJob` | `long id, String queue, String payload, int attempts, long reservedAt, long availableAt, long createdAt` | 构造（exception=null） |
| `QueuedJob` | `long id, String queue, String payload, int attempts, long reservedAt, long availableAt, long createdAt, String exception` | 构造（含异常信息） |

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `getId` | - | `long` | 任务 ID（失败任务场景为失败任务 ID） |
| `getQueue` | - | `String` | 队列名 |
| `getPayload` | - | `String` | 任务负载 JSON 字符串 |
| `getAttempts` | - | `int` | 尝试次数 |
| `getReservedAt` | - | `long` | 预约时间（毫秒时间戳，0=未预约） |
| `getAvailableAt` | - | `long` | 可用时间（失败任务场景为失败时间） |
| `getCreatedAt` | - | `long` | 创建时间（毫秒时间戳） |
| `getException` | - | `String` | 失败异常信息（仅失败任务非 null） |

### DatabaseQueueWorker
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.queue.database`
- **Description**: 队列工作线程，对齐 Laravel `php artisan queue:work`。持续轮询队列取出任务并执行。适用于任何 `QueueDriver` 实现。任务执行失败超过最大重试次数时归档到失败队列（`driver.fail()`）。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `DatabaseQueueWorker` | `QueueDriver driver, ApplicationContext applicationContext, List<String> queues, int maxAttempts, long retryDelayMs, long pollIntervalMs, int workerThreads` | - | 构造 |
| `start` | - | `void` | 启动工作线程 |
| `stop` | - | `void` | 停止工作线程（`@PreDestroy`） |

#### Usage Example
```java
DatabaseQueueWorker worker = new DatabaseQueueWorker(driver, applicationContext,
        List.of("default"), 3, 5000L, 1000L, 1);
worker.start();
// ... 运行中
worker.stop();
```

### DatabaseQueueDispatcher
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.queue.database`
- **Implements**: `com.weacsoft.jaravel.vendor.event.QueueDispatcher`
- **Description**: 持久化队列分发器，对齐 Laravel `Illuminate\Queue\Queue::push`。桥接 event 模块，将 `ShouldQueue` 事件分发到 `QueueDriver`。由 `EventDispatcher` 通过 `ObjectProvider<QueueDispatcher>` 自动注入。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `DatabaseQueueDispatcher` | `QueueDriver driver, ApplicationContext applicationContext` | - | 构造 |
| `dispatch` | `String queueName, Object listener, Event event, long delayMs` | `void` | 分发事件到队列（序列化 listener + event 为 payload JSON） |
| `isAvailable` | - | `boolean` | 队列分发器是否可用 |
| `getDriver` | - | `QueueDriver` | 获取底层队列驱动 |
| `push` | `String queueName, Listener<?> listener, Event event` | `long` | 便捷推送方法（立即执行） |

#### Payload Format
任务负载为 JSON，由 `DatabaseQueueWorker` 反序列化执行：
- `listenerClass`：监听器全限定类名
- `listenerBeanName`：监听器 Spring bean 名（可选，优先用于获取 bean）
- `eventClass`：事件全限定类名（用于反序列化）
- `eventData`：事件数据（JSON 对象）

### QueueProperties
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.queue.database`
- **Annotations**: `@ConfigurationProperties(prefix = "jaravel.queue")`
- **Description**: 队列全局配置属性，前缀 `jaravel.queue`，对齐 Laravel `config/queue.php` 顶层配置。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `getDriver` | - | `String` | 队列驱动（`database` 或 `redis`），默认 `database` |
| `setDriver` | `String driver` | `void` | 设置驱动 |
| `getRedisConnection` | - | `String` | Redis 驱动连接名，空=默认连接 |
| `setRedisConnection` | `String redisConnection` | `void` | 设置连接名 |
| `getFailedJobRetentionDays` | - | `int` | 失败任务保留天数，默认 7 |
| `setFailedJobRetentionDays` | `int failedJobRetentionDays` | `void` | 设置保留天数 |

### QueueDatabaseProperties
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.queue.database`
- **Annotations**: `@ConfigurationProperties(prefix = "jaravel.queue.database")`
- **Description**: 数据库队列细分配置属性，前缀 `jaravel.queue.database`。

#### Methods

| Method | Return | Description |
|--------|--------|-------------|
| `getTable` | `String` | 任务表名，默认 `jobs` |
| `getRetryAfter` | `long` | 重试超时秒数，默认 1800 |
| `getMaxAttempts` | `int` | 最大重试次数，默认 3 |
| `getRetryDelayMs` | `long` | 重试延迟毫秒，默认 1000 |
| `getPollIntervalMs` | `long` | 轮询间隔毫秒，默认 1000 |
| `getWorkerThreads` | `int` | 每队列工作线程数，默认 1 |
| `getQueues` | `List<String>` | 要消费的队列名列表，默认 `["default"]` |
| `isAutoStart` | `boolean` | 是否自动启动 worker，默认 false |

### QueueDatabaseAutoConfiguration
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.queue.database`
- **Annotations**: `@AutoConfiguration`, `@ConditionalOnClass(QueueDriver.class)`, `@EnableConfigurationProperties({QueueProperties.class, QueueDatabaseProperties.class})`
- **Description**: 队列自动装配（database 驱动 + 通用 worker / dispatcher）。database 驱动为默认驱动，亦是 redis 不可用时的回退驱动。

#### Beans

| Method | Return | Description |
|--------|--------|-------------|
| `databaseQueueDriver` | `DatabaseQueueDriver` | database 驱动 bean（`@ConditionalOnMissingBean(QueueDriver.class)`, `@ConditionalOnBean(DataSource.class)`），redis 不可用时回退并打印告警 |
| `databaseQueueWorker` | `DatabaseQueueWorker` | 队列工作线程 bean（适用于任何 `QueueDriver`），`auto-start=true` 时自动启动 |
| `databaseQueueDispatcher` | `DatabaseQueueDispatcher` | 持久化队列分发器 bean（`@ConditionalOnMissingBean(QueueDispatcher.class)`），桥接 event 模块 |

### RedisQueueAutoConfiguration
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.queue.database`
- **Annotations**: `@AutoConfiguration(before = QueueDatabaseAutoConfiguration.class)`, `@ConditionalOnClass(RedisManager.class)`, `@ConditionalOnBean(RedisManager.class)`, `@ConditionalOnProperty(prefix = "jaravel.queue", name = "driver", havingValue = "redis")`
- **Description**: Redis 队列驱动自动装配。仅当 `redis-config` 在类路径、存在 `RedisManager` bean 且 `driver=redis` 时启用，先于 database 装配处理。

#### Beans

| Method | Return | Description |
|--------|--------|-------------|
| `redisQueueDriver` | `RedisQueueDriver` | Redis 驱动 bean（`@ConditionalOnMissingBean(QueueDriver.class)`） |

## Configuration

```yaml
jaravel:
  queue:
    driver: database                # database | redis
    redis-connection: ""            # redis 驱动连接名，空 = 默认连接
    failed-job-retention-days: 7    # 失败任务保留天数
    database:
      enabled: true                 # 是否启用 database 驱动（默认 true）
      table: jobs                   # 任务表名
      retry-after: 1800             # 重试超时秒数
      max-attempts: 3               # 最大重试次数
      retry-delay-ms: 1000          # 重试延迟毫秒
      poll-interval-ms: 1000        # 轮询间隔毫秒
      worker-threads: 1             # 每队列工作线程数
      queues:                       # 消费队列列表
        - default
      auto-start: false             # 是否自动启动 worker
```

## Auto-Fallback Behavior

| 场景 | 结果 |
|------|------|
| `driver=database`（默认） | 使用 `DatabaseQueueDriver`（需 `DataSource`） |
| `driver=redis` + `redis-config` 已引入 + `RedisManager` 存在 | 使用 `RedisQueueDriver` |
| `driver=redis` + `redis-config` 未引入 / 无 `RedisManager` | 自动回退 `DatabaseQueueDriver`，打印告警 |
| `driver=redis` + 无 `DataSource` + 无 `RedisManager` | 无 `QueueDriver` bean，worker / dispatcher 不创建 |

## Module Dependencies

```
event (QueueDispatcher interface)
  │
  ├── DatabaseQueueDispatcher (implements QueueDispatcher, bridges to QueueDriver)
  │
  └── QueueDriver (interface)
        ├── DatabaseQueueDriver (requires DataSource)
        └── RedisQueueDriver (requires RedisManager, optional dependency)
              │
              └── DatabaseQueueWorker (consumes QueueDriver)
```
