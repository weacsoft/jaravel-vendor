# redis

Redis 连接管理模块，对齐 Laravel `RedisManager`（`Illuminate\Redis\RedisManager`）。基于 Lettuce 客户端管理多个命名连接（default / cache / session / model-cache 等），支持 standalone、sentinel、cluster 三种部署模式，是 redis-cache、session-redis 等模块的基础依赖。

## 依赖

- `core` — 基础设施（含 `LockProvider` 分布式锁契约与 `LockProviderManager` 管理器）
- `spring-boot-autoconfigure` — 自动装配
- `lettuce-core` — 非阻塞、线程安全的 Redis 客户端
- `slf4j-api` — 日志

## 核心接口

### RedisManager

Redis 连接管理器，对齐 Laravel `RedisManager`。管理多个命名连接，每个连接对应一个独立的 Lettuce 连接对象。所有连接均为线程安全，可被多线程共享，在首次访问时惰性创建，进程生命周期内复用。

```java
public class RedisManager {
    public RedisManager(RedisProperties properties);

    // 字符串编码同步命令接口
    public RedisCommands<String, String> sync();              // 默认连接
    public RedisCommands<String, String> sync(String name);   // 指定连接

    // 字符串编码异步命令接口
    public RedisAsyncCommands<String, String> async();
    public RedisAsyncCommands<String, String> async(String name);

    // 连接对象
    public StatefulConnection<String, String> connection(String name);          // 字符串编码连接
    public StatefulConnection<byte[], byte[]> binaryConnection(String name);    // 字节编码连接（序列化对象存储）

    public String getPrefix();                       // 全局键前缀
    public Set<String> connectionNames();            // 所有已配置的连接名
    public String getDefaultConnection();            // 默认连接名
    public void shutdown();                           // 优雅关闭所有连接与客户端资源（@PreDestroy）
}
```

连接模式：
- **standalone**（默认）：单机或主从，通过 host:port 连接
- **sentinel**：哨兵高可用，通过 sentinel 列表自动发现 master
- **cluster**：Redis Cluster，通过集群节点列表连接，自动路由

### RedisProperties

配置属性，前缀 `jaravel.redis`，对齐 Laravel `config/database.php` 的 redis 段。支持多命名连接，每个连接独立 host/port/database/password。

```java
@ConfigurationProperties(prefix = "jaravel.redis")
public class RedisProperties {
    public static class Options {
        private String cluster = "redis";   // 集群模式：redis(单机) / cluster / sentinel
        private String prefix = "";          // 全局键前缀
    }
    public static class ConnectionConfig {
        private String url;                 // Redis URL，设置后覆盖 host/port/database
        private String host = "127.0.0.1";
        private int port = 6379;
        private String username = "";       // Redis 6+ ACL
        private String password = "";
        private int database = 0;
        private int timeoutMs = 2000;
        private String sentinelMaster = ""; // 哨兵主节点名
        private String sentinels = "";      // 哨兵列表 host:port,host:port
        private final String clusterNodes = "";   // 集群节点列表 host:port,host:port
    }
}
```

### RedisLockProvider / RedisLockProviderImpl

本模块定义 `RedisLockProvider` 接口（继承 core 模块的 `LockProvider` 契约），并提供基于 Redis 的分布式锁实现 `RedisLockProviderImpl`，对齐 Laravel `Illuminate\Cache\RedisLock`。锁契约（`core.lock.LockProvider`）与消费方（`schedule` 模块）均不依赖本模块：Redis 不可用或未注册时，`LockProviderManager` 自动兜底为内置同步锁（代码级兜底，单机执行）。使用 Redis `SET key value NX EX seconds` 实现原子性加锁，`DEL key` 释放锁。

```java
public class RedisLockProviderImpl implements RedisLockProvider {
    public RedisLockProviderImpl(RedisManager redisManager, String connectionName);
    public boolean tryLock(String key, long ttlSeconds);   // SET key value NX EX ttl
    public void unlock(String key);                         // DEL key
}
```

### RedisAutoConfiguration

自动装配类：创建 `RedisManager` bean，并通过 `@RegisterLockProvider(value = "redis", defaultProvider = true)` 把 Redis 分布式锁注册为 `LockProviderManager` 的默认 provider（注解驱动注册，**不进入 Spring 容器**）。

## 配置

```yaml
jaravel:
  redis:
    client: lettuce              # 客户端实现，目前仅支持 lettuce
    options:
      cluster: redis             # 集群模式：redis(单机) / cluster / sentinel
      prefix: "manage_database_" # 全局键前缀
    connections:
      default:
        host: 127.0.0.1
        port: 6379
        username: ""
        password: ""
        database: 0
      cache:
        host: 127.0.0.1
        port: 6379
        database: 1
      session:
        host: 127.0.0.1
        port: 6379
        database: 2
      model-cache:
        host: 127.0.0.1
        port: 6379
        database: 3
```

哨兵模式配置示例：

```yaml
jaravel:
  redis:
    options:
      cluster: sentinel
      prefix: "myapp_"
    connections:
      default:
        sentinelMaster: mymaster
        sentinels: "sentinel1:26379,sentinel2:26379,sentinel3:26379"
        password: "secret"
        database: 0
```

集群模式配置示例：

```yaml
jaravel:
  redis:
    options:
      cluster: cluster
      prefix: "myapp_"
    connections:
      default:
        clusterNodes: "node1:6379,node2:6379,node3:6379"
        password: "secret"
```

## 使用示例

直接使用 Redis 命令：

```java
@Autowired
private RedisManager redisManager;

public void cacheScore(String studentId, String score) {
    RedisCommands<String, String> cmd = redisManager.sync("cache");
    cmd.set("score:" + studentId, score);
    cmd.expire("score:" + studentId, 3600);
}

public String getScore(String studentId) {
    RedisCommands<String, String> cmd = redisManager.sync("cache");
    return cmd.get("score:" + studentId);
}
```

使用分布式锁：

```java
@Autowired
private RedisLockProvider lockProvider;

public void executeWithLock(String taskName) {
    String lockKey = "schedule:lock:" + taskName;
    if (lockProvider.tryLock(lockKey, 300)) {
        try {
            // 执行任务
        } finally {
            lockProvider.unlock(lockKey);
        }
    }
}
```

## 自动装配

`RedisAutoConfiguration` 通过 `@AutoConfiguration` 注册，当 classpath 存在 `RedisManager` 且配置了 `jaravel.redis.connections` 时生效。

创建的 bean：
- `RedisManager` — Redis 管理器（`@ConditionalOnMissingBean`，便于业务方覆盖）
- Redis 分布式锁提供者 — **不创建 bean**：通过 `@RegisterLockProvider(value = "redis", defaultProvider = true)` 注册到 core 的 `LockProviderManager`，供 `schedule` 模块消费

> 锁契约位于 core 模块（`core.lock.LockProvider`）。redis 模块不存在或 provider 未注册时，
> `LockProviderManager` 自动兜底为内置「同步锁」（代码级兜底，单机执行），
> `schedule` 模块的定时任务不受影响，不会启动失败。

该模块是其他 Redis 相关模块（redis-cache、session-redis）的基础依赖，提供统一的 Redis 连接管理能力，避免每个模块各自创建连接池。
