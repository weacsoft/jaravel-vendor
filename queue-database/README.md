# queue-database

队列模块，对齐 Laravel `Illuminate\Queue`。支持 **database** 与 **redis** 两种驱动，将任务持久化存储，支持多实例消费、延迟执行、重试机制与失败队列（`failed_jobs`），用于事件监听器的异步队列处理。

## 依赖

- `core` — 基础设施（含 `core.lookup` BeanLookup SPI，D3 起监听器 bean 解析经此）
- `event` — 队列驱动由 `EventDispatcher` 在监听器实现 `ShouldQueue` 时调用
- `jackson-databind` — 任务负载 JSON 序列化
- `slf4j-api` — 日志
- `redis`（**optional**）— Redis 驱动按需依赖，未引入时 `RedisQueueDriver` 根本不装配（严格按需，不回退到 database）

> **D3（Spring 解耦终收）**：本模块主源码已**零 Spring import**（pom 的
> `spring-jdbc` / `spring-context` 编译依赖已移除，仅测试态保留 `spring-jdbc`）：
> `DatabaseQueueDriver` 的 SQL 改为原生 JDBC（复刻 `cache-database` 驱动模板），
> `DatabaseQueueDispatcher` / `DatabaseQueueWorker` 的监听器 bean 解析改经
> `core.lookup.BeanLookup` SPI；Spring 宿主接线见 `jaravel-springboot` 的
> `springboot.queuedatabase` 包（`ContextBeanProvider` 适配 `ApplicationContext`）。

## 驱动选择

通过 `jaravel.queue.driver` 选择驱动：

| 驱动 | 实现类 | 依赖 | 失败队列存储 |
| --- | --- | --- | --- |
| `sync`（默认） | 内存队列（QueueManager） | 无 | 无 |
| `database` | `DatabaseQueueDriver` | `DataSource` | `failed_jobs` 表 |
| `redis` | `RedisQueueDriver` | `RedisManager`（redis） | `jaravel:queue:failed` List |

**sync 模式（默认）**：当 `driver=sync`（或完全不配置 `jaravel.queue.driver`）时，不创建任何 `QueueDriver` Bean，`EventDispatcher` 自动降级为内存队列（`QueueManager`），不会创建数据库表，无需额外配置。对齐 Laravel 的 `sync` 队列驱动。

**用上了才注册（安装 ≠ 启用）**：`redis` 与 `database` 两种持久化驱动均通过 core 的 `OnDriverInUseCondition`
**严格按需**装配——仅当 `jaravel.queue.driver` 显式取值为 `redis` / `database` 时才创建对应 `QueueDriver` Bean，
且依然受 `@ConditionalOnBean(RedisManager.class)` / `@ConditionalOnBean(DataSource.class)` 约束。
不存在「redis 不可用回退 database」逻辑：redis 与 database 各自独立按需装配，sync 始终作为无驱动时的内存兜底。

## 核心接口

### QueueDriver

队列驱动接口，对齐 Laravel `Illuminate\Contracts\Queue\Queue`。抽象队列存储后端，含失败队列方法（对齐 `failed_jobs`）。

```java
public interface QueueDriver {
    // 队列操作
    long push(String queueName, String payload);                 // 推送任务立即执行
    long push(String queueName, String payload, long delayMs);   // 延迟推送任务
    QueuedJob pop(String queueName);                              // 弹出一个到期任务（多实例竞争）
    void delete(long jobId);                                       // 标记成功，删除任务
    void release(long jobId);                                      // 标记失败，释放锁以便重试
    void release(long jobId, long delayMs);                       // 标记失败，释放锁并设置重试延迟
    int size(String queueName);                                    // 待处理任务数
    void clear(String queueName);                                  // 清空队列

    // 失败队列（对齐 Laravel failed_jobs）
    void fail(long jobId, String queue, String payload, int attempts, String exception); // 归档到失败队列
    List<QueuedJob> getFailedJobs();                              // 查询失败任务（最新失败在前）
    void retryFailedJob(long failedJobId);                        // 重试失败任务（对齐 queue:retry）
    void deleteFailedJob(long failedJobId);                       // 删除失败任务（对齐 queue:forget）
    void clearFailedJobs();                                       // 清空失败任务（对齐 queue:flush）
}
```

### DatabaseQueueDriver

数据库队列驱动实现。将任务持久化到 `jobs` 表，使用基于 `reserved_at` 的乐观锁实现多实例抢占式消费。失败任务归档到 `failed_jobs` 表。**不会自动建表**：需通过 `artisan queue:table` 命令或手动调用 `createTable()` 方法创建表。

```java
public class DatabaseQueueDriver implements QueueDriver {
    public DatabaseQueueDriver(DataSource dataSource, String table, long retryAfterSeconds);
    public DatabaseQueueDriver(DataSource dataSource, String table, long retryAfterSeconds, int failedJobRetentionDays);
    public void purgeOldFailedJobs();  // 清理超过保留天数的失败任务（对齐 queue:prune-failed-jobs）
}
```

### RedisQueueDriver

Redis 队列驱动实现，对齐 Laravel `Illuminate\Queue\RedisQueue`。基于 Redis List / ZSET 实现队列存储。

```java
public class RedisQueueDriver implements QueueDriver {
    public RedisQueueDriver(RedisManager redisManager, String connectionName,
                            long retryAfterSeconds, int failedJobRetentionDays);
    public void purgeOldFailedJobs();
}
```

**Redis 数据结构**：

| 键 | 类型 | 说明 |
| --- | --- | --- |
| `jaravel:queue:{queueName}` | List | 就绪队列，LPUSH 入队，RPOP 出队（FIFO） |
| `jaravel:queue:{queueName}:delayed` | ZSET | 延迟队列，score=到期时间戳 |
| `jaravel:queue:{queueName}:reserved` | ZSET | 预约队列，score=预约超时时间戳 |
| `jaravel:queue:failed` | List | 失败队列，LPUSH 入队，最新失败在前 |
| `jaravel:queue:seq` | String | 任务 ID 序列（INCR） |
| `jaravel:queue:failed:seq` | String | 失败任务 ID 序列（INCR） |
| `jaravel:queue:index` | Hash | jobId -> queueName 索引（用于按 jobId 定位队列） |

- Job 以 JSON 字符串存储，包含 `id` / `queue` / `payload` / `attempts` / `reservedAt` / `availableAt` / `createdAt`
- pop 时迁移到期延迟任务与超时预约任务到就绪队列，RPOP 弹出后 ZADD 到预约队列
- 多实例：RPOP 原子操作 + ZREM 返回值抢占，确保同一任务只被一个实例获取

### QueuedJob

队列任务实体，对齐 Laravel `Illuminate\Queue\Jobs\DatabaseJob`。失败任务也包装为本类，此时 `getException()` 携带失败异常信息，`getId()` 为失败任务 ID。

```java
public class QueuedJob {
    public long getId();             // 任务 ID（失败任务场景为失败任务 ID）
    public String getQueue();
    public String getPayload();      // JSON 序列化的监听器 + 事件数据
    public int getAttempts();
    public long getReservedAt();
    public long getAvailableAt();    // 失败任务场景为失败时间
    public long getCreatedAt();
    public String getException();    // 失败异常信息（仅失败任务非 null）
}
```

### DatabaseQueueWorker

队列工作线程，对齐 Laravel `php artisan queue:work`。持续轮询队列，弹出到期任务并执行。适用于任何 `QueueDriver` 实现（database / redis）。

```java
public class DatabaseQueueWorker {
    public DatabaseQueueWorker(QueueDriver driver, ApplicationContext applicationContext,
                               List<String> queues, int maxAttempts, long retryDelayMs,
                               long pollIntervalMs, int workerThreads);
    public void start();   // 启动工作线程
    public void stop();    // 停止工作线程（@PreDestroy）
}
```

任务执行失败时：若尝试次数未超过最大重试次数则释放任务并设置重试延迟；**超过则归档到失败队列**（`driver.fail()`，对齐 Laravel `failed_jobs`）。监听器缺失为永久性错误，直接归档到失败队列。

### DatabaseQueueDispatcher

持久化队列分发器，对齐 Laravel `Illuminate\Queue\Queue::push`。实现 event 模块的 `QueueDispatcher` 接口，将 `ShouldQueue` 事件分发到 `QueueDriver`，桥接事件模块与队列模块。

```java
public class DatabaseQueueDispatcher implements QueueDispatcher {
    public DatabaseQueueDispatcher(QueueDriver driver, ApplicationContext applicationContext);
    public void dispatch(String queueName, Object listener, Event event, long delayMs);
}
```

由 `EventDispatcher` 通过 `ObjectProvider<QueueDispatcher>` 自动注入，无需手动配置。

## 配置

```yaml
jaravel:
  queue:
    driver: sync                    # sync（默认，内存队列）| database | redis
    redis-connection: ""            # redis 驱动使用的连接名，空 = 默认连接
    failed-job-retention-days: 7    # 失败任务保留天数（用于清理过期失败任务）
    database:
      enabled: true                 # 是否启用 database 驱动（默认 true）
      table: jobs                   # 任务表名
      # 说明：database 驱动的实际启用由 jaravel.queue.driver=database 决定（见下文自动装配），
      # 此 enabled 仅作兼容占位，不再控制装配。
      retry-after: 1800             # 重试超时秒数（30 分钟）
      max-attempts: 3               # 最大重试次数
      retry-delay-ms: 1000          # 重试延迟毫秒
      poll-interval-ms: 1000        # 轮询间隔毫秒
      worker-threads: 1             # 每队列工作线程数
      queues:                       # 要消费的队列名列表
        - default
        - score
        - oracle
      auto-start: false             # 是否自动启动 worker（默认 false）
```

### 数据库表结构

需通过 `artisan queue:table` 命令创建（`CREATE TABLE IF NOT EXISTS`），对齐 Laravel `jobs` / `failed_jobs` 表：

```sql
CREATE TABLE jobs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  queue VARCHAR(255) NOT NULL,
  payload LONGTEXT NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  reserved_at BIGINT NULL,
  available_at BIGINT NOT NULL,
  created_at BIGINT NOT NULL,
  INDEX jobs_queue_index (queue)
);

CREATE TABLE failed_jobs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  queue VARCHAR(255) NOT NULL,
  payload LONGTEXT NOT NULL,
  exception LONGTEXT,
  attempts INT NOT NULL DEFAULT 0,
  failed_at BIGINT NOT NULL,
  INDEX failed_jobs_queue_index (queue)
);
```

### artisan queue:table 命令

使用 database 队列驱动前，需先执行建表命令：

```bash
java -jar app.jar artisan queue:table
```

该命令调用 `DatabaseQueueDriver.createTable()` 创建任务表和失败任务表（`CREATE TABLE IF NOT EXISTS`），并创建队列索引。也可手动调用 `createTable()` 方法：

```java
@Autowired
private DatabaseQueueDriver queueDriver;

queueDriver.createTable();  // 创建 jobs 和 failed_jobs 表
```

> **重要**：`DatabaseQueueDriver` **不会在构造时自动建表**。使用 database 队列驱动前，必须先执行 `artisan queue:table` 命令或手动调用 `createTable()` 方法。

## 使用示例

### 启用 Redis 驱动

引入 `redis` 依赖后，设置 `jaravel.queue.driver=redis`：

```yaml
jaravel:
  queue:
    driver: redis
    redis-connection: ""        # 使用默认 redis 连接
```

### 失败任务管理

```java
@Autowired
private QueueDriver queueDriver;

// 查看失败任务（最新失败在前，QueuedJob.getException() 携带异常信息）
List<QueuedJob> failed = queueDriver.getFailedJobs();

// 重试一个失败任务（重新入队并从失败队列移除，对齐 queue:retry）
queueDriver.retryFailedJob(failedJobId);

// 删除一个失败任务（对齐 queue:forget）
queueDriver.deleteFailedJob(failedJobId);

// 清空所有失败任务（对齐 queue:flush）
queueDriver.clearFailedJobs();
```

### 手动推送任务

```java
@Autowired
private QueueDriver queueDriver;

public void dispatchAsync(String queueName, Object listener, Object event) {
    Map<String, Object> payload = Map.of(
        "listenerClass", listener.getClass().getName(),
        "eventClass", event.getClass().getName(),
        "eventData", event
    );
    String json = objectMapper.writeValueAsString(payload);
    queueDriver.push(queueName, json);          // 立即执行
    queueDriver.push(queueName, json, 5 * 60 * 1000L);  // 延迟 5 分钟
}
```

## 自动装配

> **P3/D3 说明**：D3 起本模块连 driver 层也已去 Spring（见上方「依赖」节 D3 注记）；
> 原属于本模块的两个装配条件类 `OnDatabaseQueueDriverCondition` / `OnRedisQueueDriverCondition`
> **已迁入 `jaravel-springboot` 的 `springboot.queuedatabase` 包**（避免 springboot → queue-database
> 反向形成循环依赖；条件逻辑原样保留）。
> 原位于 core 的 `QueueDriverRegistrar`（`@RegisterQueueDriver` 扫描器）亦迁入该包，现为**纯类**，
> 由 `jaravel-springboot` 的 `QueueDatabaseAutoConfiguration` 的 `SmartInitializingSingleton` 触发扫描
> （行为与 P3 前一致）。

通过 `@AutoConfiguration` 注册，自动配置类在 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 中声明：

- `RedisQueueAutoConfiguration`（先处理）：受 `@Conditional(OnRedisQueueDriverCondition.class)` 约束，仅当
  `redis` 在类路径、存在 `RedisManager` bean、且 `jaravel.queue.driver=redis` 时注册 `RedisQueueDriver`
- `QueueDatabaseAutoConfiguration`：受 `@Conditional(OnDatabaseQueueDriverCondition.class)` 约束，仅当
  `jaravel.queue.driver=database` 且存在 `DataSource` bean 时注册 `DatabaseQueueDriver`、`DatabaseQueueWorker`、`DatabaseQueueDispatcher`
- 当 `jaravel.queue.driver` 为 `sync`（默认）或完全未配置时，两个驱动条件都不命中，不创建 `QueueDriver` Bean，
  `EventDispatcher` 使用内存队列（`QueueManager`），不会创建数据库表

创建的 bean：
- `QueueDriver`（`DatabaseQueueDriver` 或 `RedisQueueDriver`）— 队列驱动（`@ConditionalOnMissingBean`）
- `DatabaseQueueWorker` — 队列工作线程（`@ConditionalOnMissingBean`），由后台 Bean 承担消费。框架**不提供** `artisan queue:work` 命令（与 Laravel 不同）；生产环境设置 `jaravel.queue.database.auto-start=true` 随应用启动 worker，或业务方自行注入 `DatabaseQueueWorker` 调用其启动方法。
- `DatabaseQueueDispatcher` — 持久化队列分发器（`@ConditionalOnMissingBean(QueueDispatcher.class)`），桥接 event 模块
