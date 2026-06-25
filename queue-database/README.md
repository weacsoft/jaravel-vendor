# queue-database

数据库队列驱动，对齐 Laravel `Illuminate\Queue\DatabaseQueue`。将任务持久化到数据库 `jobs` 表，支持多实例消费、延迟执行与重试机制，用于事件监听器的异步队列处理。

## 依赖

- `core` — 基础设施
- `event` — 队列驱动由 `EventDispatcher` 在监听器实现 `ShouldQueue` 时调用
- `spring-boot-autoconfigure` — 自动装配
- `spring-jdbc` — `JdbcTemplate` 数据库操作
- `jackson-databind` — 任务负载 JSON 序列化
- `slf4j-api` — 日志

## 核心接口

### QueueDriver

队列驱动接口，对齐 Laravel `Illuminate\Contracts\Queue\Queue`。抽象队列存储后端，支持数据库 / Redis 等实现。

```java
public interface QueueDriver {
    long push(String queueName, String payload);                 // 推送任务立即执行
    long push(String queueName, String payload, long delayMs);   // 延迟推送任务
    QueuedJob pop(String queueName);                              // 弹出一个到期任务（多实例通过行锁竞争）
    void delete(long jobId);                                       // 标记成功，删除任务
    void release(long jobId);                                      // 标记失败，释放锁以便重试
    void release(long jobId, long delayMs);                       // 标记失败，释放锁并设置重试延迟
    int size(String queueName);                                    // 待处理任务数
    void clear(String queueName);                                  // 清空队列
}
```

### DatabaseQueueDriver

数据库队列驱动实现。将任务持久化到数据库 `jobs` 表，使用基于 `reserved_at` 的乐观锁实现多实例抢占式消费。

```java
public class DatabaseQueueDriver implements QueueDriver {
    public DatabaseQueueDriver(DataSource dataSource, String table, long retryAfterSeconds);
}
```

多实例消费：使用 `SELECT ... FOR UPDATE SKIP LOCKED`（MySQL 8+）思路，通过 `reserved_at` 乐观锁确保同一任务只被一个实例获取。对于不支持 SKIP LOCKED 的数据库，降级为基于 `reserved_at` 的乐观锁。

重试机制：任务执行失败后通过 `release(jobId, delayMs)` 释放预约，设置延迟后重新入队。超过 `retryAfterSeconds`（默认 1800 秒 = 30 分钟）未被确认的任务会被重新预约。

### QueuedJob

队列任务实体，对齐 Laravel `Illuminate\Queue\Jobs\DatabaseJob`。

```java
public class QueuedJob {
    public long getId();
    public String getQueue();
    public String getPayload();       // JSON 序列化的监听器 + 事件数据
    public int getAttempts();         // 尝试次数
    public long getReservedAt();      // 预约时间（毫秒时间戳）
    public long getAvailableAt();     // 可用时间（毫秒时间戳）
    public long getCreatedAt();       // 创建时间（毫秒时间戳）
}
```

### DatabaseQueueWorker

数据库队列工作线程，对齐 Laravel `php artisan queue:work`。持续轮询数据库队列，弹出到期任务并执行。

```java
public class DatabaseQueueWorker {
    public DatabaseQueueWorker(QueueDriver driver, ApplicationContext applicationContext,
                               List<String> queues, int maxAttempts, long retryDelayMs,
                               long pollIntervalMs, int workerThreads);

    public void start();   // 启动工作线程
    public void stop();    // 停止工作线程（@PreDestroy）
}
```

任务负载（payload）为 JSON 格式，包含：
- `listenerBeanName` / `listenerClass`：监听器类名（Spring bean 名或全限定类名）
- `eventData`：事件数据（JSON 对象）
- `eventClass`：事件类名（用于反序列化）

工作线程通过 Spring `ApplicationContext` 获取监听器 bean，将事件数据反序列化后调用监听器的 `handle` 方法。任务执行失败时，若尝试次数未超过最大重试次数则释放任务并设置重试延迟，超过则删除任务。

### QueueDatabaseProperties

配置属性，前缀 `jaravel.queue.database`。

## 配置

```yaml
jaravel:
  queue:
    database:
      table: jobs                  # 任务表名
      retry-after: 1800            # 重试超时秒数（30 分钟）
      max-attempts: 3              # 最大重试次数
      retry-delay-ms: 1000         # 重试延迟毫秒
      poll-interval-ms: 1000       # 轮询间隔毫秒
      worker-threads: 1            # 每队列工作线程数
      queues:                      # 要消费的队列名列表
        - default
        - score
        - oracle
      auto-start: false            # 是否自动启动 worker（默认 false）
```

数据库表结构（对齐 Laravel `jobs` 表）：

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
```

## 使用示例

推送任务到队列：

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
    queueDriver.push(queueName, json);
}
```

延迟推送：

```java
// 5 分钟后执行
queueDriver.push("default", payloadJson, 5 * 60 * 1000L);
```

## 自动装配

`QueueDatabaseAutoConfiguration` 通过 `@AutoConfiguration` 注册，当 classpath 存在 `DatabaseQueueDriver` 与 `DataSource`，且存在 `DataSource` bean，且 `jaravel.queue.database.enabled` 为 true（默认）时生效。

创建的 bean：
- `DatabaseQueueDriver` — 数据库队列驱动（`@ConditionalOnMissingBean`）
- `DatabaseQueueWorker` — 队列工作线程（`@ConditionalOnMissingBean`），仅当 `auto-start=true` 时自动启动。生产环境应通过 artisan 命令 `java -jar app.jar artisan queue:work` 启动 worker，或设置 `auto-start=true` 自动启动。
