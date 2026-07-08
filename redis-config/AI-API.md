# redis-config AI-API Reference

> Module: `redis-config` | Package: `com.weacsoft.jaravel.vendor.redis` | Version: 0.1.1

## Overview
redis-config 模块提供 Laravel 风格的 Redis 连接管理，基于 Lettuce 客户端实现。核心包含 RedisManager（多命名连接管理器，支持 standalone/sentinel/cluster 三种模式）、RedisProperties（配置属性，对齐 Laravel `config/database.php` redis 段）、RedisAutoConfiguration（自动装配）和 RedisLockProviderImpl（基于 Redis 的分布式锁实现）。连接在首次访问时惰性创建，进程生命周期内复用，所有连接线程安全可共享。

## Classes & Interfaces

### RedisManager
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.redis`
- **Description**: Redis 连接管理器，对齐 Laravel `Illuminate\Redis\RedisManager`。管理多个命名连接（default/cache/session/model-cache 等），每个连接对应一个独立的 Lettuce 连接对象。支持 standalone（单机）、sentinel（哨兵高可用）和 cluster（Redis Cluster）三种连接模式。连接在首次访问时惰性创建，使用 ConcurrentHashMap 维护，线程安全可共享。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `RedisManager` | `RedisProperties properties` | 构造方法 | 创建 Redis 管理器 |
| `sync` | 无 | `RedisCommands<String, String>` | 获取默认连接的同步命令接口 |
| `sync` | `String name` | `RedisCommands<String, String>` | 获取指定连接的同步命令接口 |
| `async` | 无 | `RedisAsyncCommands<String, String>` | 获取默认连接的异步命令接口 |
| `async` | `String name` | `RedisAsyncCommands<String, String>` | 获取指定连接的异步命令接口 |
| `connection` | `String name` | `StatefulConnection<String, String>` | 获取字符串编码连接对象（null/空使用默认） |
| `binaryConnection` | `String name` | `StatefulConnection<byte[], byte[]>` | 获取字节编码连接对象（用于序列化对象存储） |
| `getPrefix` | 无 | `String` | 获取全局键前缀 |
| `connectionNames` | 无 | `Set<String>` | 获取所有已配置的连接名 |
| `getDefaultConnection` | 无 | `String` | 获取默认连接名 |
| `shutdown` | 无 | `void` | 优雅关闭所有连接与客户端资源（@PreDestroy） |

#### Usage Example
```java
@Autowired
private RedisManager redisManager;

// 使用默认连接
RedisCommands<String, String> cmd = redisManager.sync();
cmd.set("key", "value");
String value = cmd.get("key");
cmd.expire("key", 60);

// 使用指定连接
RedisCommands<String, String> cacheCmd = redisManager.sync("cache");
cacheCmd.set("user:1", json);

// 异步操作
RedisAsyncCommands<String, String> async = redisManager.async("session");
async.set("session:abc", token).thenAccept(ok -> {
    System.out.println("设置成功");
});

// 查看所有连接
Set<String> names = redisManager.connectionNames(); // [default, cache, session, model-cache]
```

---

### RedisProperties
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.redis`
- **Description**: Redis 配置属性，前缀 `jaravel.redis`，对齐 Laravel `config/database.php` 的 redis 段。支持多命名连接，每个连接独立 host/port/database/password。
- **Annotations**: `@ConfigurationProperties(prefix = "jaravel.redis")`

#### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `client` | `String` | `"lettuce"` | 客户端实现名称 |
| `options` | `Options` | - | 全局选项 |
| `connections` | `Map<String, ConnectionConfig>` | `{}` | 命名连接映射 |

#### Nested Types

**RedisProperties.Options** - 全局选项

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `cluster` | `String` | `"redis"` | 集群模式：redis(单机)/cluster/sentinel |
| `prefix` | `String` | `""` | 全局键前缀 |

**RedisProperties.ConnectionConfig** - 单个连接配置

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `url` | `String` | `null` | Redis URL（设置后覆盖 host/port/database） |
| `host` | `String` | `"127.0.0.1"` | Redis 主机 |
| `port` | `int` | `6379` | Redis 端口 |
| `username` | `String` | `""` | 用户名（Redis 6+ ACL） |
| `password` | `String` | `""` | 密码 |
| `database` | `int` | `0` | 数据库编号 (0-15) |
| `timeoutMs` | `int` | `2000` | 连接超时毫秒 |
| `sentinelMaster` | `String` | `""` | 哨兵主节点名称（sentinel 模式） |
| `sentinels` | `String` | `""` | 哨兵节点列表，格式 host:port,host:port |
| `clusterNodes` | `String` | `""` | 集群节点列表，格式 host:port,host:port |

#### Usage Example
```yaml
# application.yml - 单机模式
jaravel:
  redis:
    client: lettuce
    options:
      cluster: redis
      prefix: "myapp_"
    connections:
      default:
        host: 127.0.0.1
        port: 6379
        database: 0
      cache:
        host: 127.0.0.1
        port: 6379
        database: 1
      session:
        host: 127.0.0.1
        port: 6379
        database: 2

# 集群模式
jaravel:
  redis:
    options:
      cluster: cluster
    connections:
      default:
        clusterNodes: "192.168.1.1:6379,192.168.1.2:6379,192.168.1.3:6379"
        password: "secret"

# 哨兵模式
jaravel:
  redis:
    options:
      cluster: sentinel
    connections:
      default:
        sentinelMaster: "mymaster"
        sentinels: "192.168.1.1:26379,192.168.1.2:26379"
        password: "secret"

# URL 形式
jaravel:
  redis:
    connections:
      default:
        url: "redis://user:password@127.0.0.1:6379/0"
```

---

### RedisAutoConfiguration
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.redis`
- **Description**: Redis 自动装配，对齐 Laravel Redis 服务提供者。当 classpath 存在 RedisManager 且配置了 `jaravel.redis.connections` 时，创建 RedisManager Bean。同时提供 RedisLockProvider 实现（当 schedule 模块存在时）。
- **Annotations**: `@AutoConfiguration`, `@ConditionalOnClass(RedisManager.class)`, `@ConditionalOnProperty(prefix = "jaravel.redis", name = "connections")`, `@EnableConfigurationProperties(RedisProperties.class)`

#### Bean Methods

| Bean | Parameters | Return | Description |
|------|-----------|--------|-------------|
| `redisManager` | `RedisProperties properties` | `RedisManager` | Redis 管理器（@Bean, @ConditionalOnMissingBean） |
| `redisLockProvider` | `RedisManager redisManager` | `RedisLockProvider` | 分布式锁提供者（@Bean, @ConditionalOnMissingBean, @ConditionalOnClass(RedisLockProvider.class)） |

---

### RedisLockProviderImpl
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.redis.lock`
- **Description**: 基于 Redis 的分布式锁实现，对齐 Laravel `Illuminate\Cache\RedisLock`。使用 Redis `SET key value NX EX seconds` 实现原子性加锁，通过 `DEL key` 释放锁。适用于多机环境下的定时任务防重复执行。
- **Implements**: `com.weacsoft.jaravel.vendor.schedule.RedisLockProvider`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `RedisLockProviderImpl` | `RedisManager redisManager, String connectionName` | 构造方法 | 创建分布式锁提供者 |
| `tryLock` | `String key, long ttlSeconds` | `boolean` | 尝试加锁（SET NX EX），成功返回 true |
| `unlock` | `String key` | `void` | 释放锁（DEL） |

#### Usage Example
```java
@Autowired
private RedisLockProvider lockProvider;

// 定时任务防重复执行
String lockKey = "schedule:cleanup:daily";
if (lockProvider.tryLock(lockKey, 300)) {  // 5 分钟 TTL
    try {
        // 执行定时任务
        cleanupService.runDailyCleanup();
    } finally {
        lockProvider.unlock(lockKey);
    }
} else {
    log.info("另一个实例正在执行此任务，跳过");
}
```
