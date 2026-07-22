# Jaravel-Vendor 集群部署指南

> 版本：0.1.2
>
> 本文档讲解如何将 jaravel-vendor 部署到服务器集群（多应用节点）环境，重点说明 Session 共享、缓存共享、Redis 基础设施、模型缓存等在多机场景下的配置与原理。

---

## 目录

- [1. 概述](#1-概述)
- [2. Session 共享（session-redis 模块）](#2-session-共享session-redis-模块)
- [3. 缓存共享（redis-cache 模块）](#3-缓存共享redis-cache-模块)
- [4. Redis 基础设施（redis-config 模块）](#4-redis-基础设施redis-config-模块)
- [5. 完整集群配置示例](#5-完整集群配置示例)
- [6. 部署检查清单](#6-部署检查清单)
- [7. 集群中的模型缓存](#7-集群中的模型缓存)

---

## 1. 概述

### 1.1 为什么需要集群部署

当应用从单机走向多机部署时，原本依赖单进程内存或本地文件的状态将无法在节点间共享，从而导致以下典型问题：

- **登录态丢失**：用户在节点 A 登录后，请求被负载均衡转发到节点 B，由于节点 B 的本地内存中没有该用户的 Session，用户被强制登出。
- **缓存不一致**：节点 A 写入的缓存只存在于本地内存，节点 B 读取时缓存未命中，重复回源，缓存命中率骤降。
- **定时任务重复执行**：多个节点各自运行同一份定时任务，导致重复处理、数据错乱。
- **模型缓存碎片化**：各节点各自维护模型缓存版本号，失效操作无法跨节点传播，读到陈旧数据。

### 1.2 集群中需要共享的状态

在 jaravel-vendor 集群部署中，以下状态必须跨节点共享：

| 共享状态 | 共享介质 | 负责模块 |
| --- | --- | --- |
| 用户登录态（Session） | Redis | `session-redis` |
| 业务缓存 | Redis 或 数据库 | `redis-cache` / `cache` |
| Redis 连接配置 | 统一配置（指向同一 Redis） | `redis-config` |
| 分布式锁 | Redis | `redis-config`（`RedisLockProvider`） |
| 模型查询缓存 | Redis 或 数据库 | `model-cache` + `cache` |
| 定时任务互斥 | Redis 分布式锁 | `schedule` + `redis-config` |

### 1.3 集群部署的核心原则

1. **无状态应用节点**：应用节点本身不持有任何会话或缓存状态，所有可变状态外置到 Redis 或共享数据库。
2. **统一中间件**：所有节点连接同一套 Redis（或集群）、同一套数据库。
3. **配置一致**：各节点的 `application.yml` 中 Redis 连接、缓存前缀、Session 配置必须完全一致，仅应用节点自身的端口/实例标识可不同。

---

## 2. Session 共享（session-redis 模块）

### 2.1 工作原理

`session-redis` 模块提供 `RedisSessionGuard`，对齐 Laravel 的 `SessionGuard`，但将 Session 后端从本地存储替换为共享 Redis，从而实现多机 Session 同步。

#### Session ID 的流转

1. **请求到达**：从 Cookie 中读取 Session ID（Cookie 名由 `RedisSessionStore.getCookieName()` 指定，默认 `manage_session`）。
2. **惰性创建**：若 Cookie 中无 Session ID，则不创建新 Session，仅在 `login` 时才生成。
3. **登录写入**：`login` 时生成新的 UUID Session ID，将登录态写入 Redis，并通过 Cookie 返回给客户端。
4. **登出销毁**：`logout` 时销毁 Redis 中的 Session 数据。

#### Redis 存储格式

Session 数据以 Redis Hash 结构存储，键格式为 `<prefix>:<sessionId>`，例如 `laravel_session:a1b2c3d4-...`。

```
HSET laravel_session:a1b2c3d4-... login_web_id "12345" login_wechat_id "67890"
EXPIRE laravel_session:a1b2c3d4-... 1800
```

#### 滑动过期机制

每次读写 Session 都会刷新 TTL（通过 `EXPIRE` 命令），实现**滑动过期**——只要用户持续活跃，Session 就不会过期；只有当用户超过生命周期（默认 30 分钟）无任何请求时，Session 才会因 TTL 到期被 Redis 自动清除。

```
首次访问  → EXPIRE key 1800   （剩余 1800s）
30s 后访问 → EXPIRE key 1800   （重置为 1800s）
...
无访问 30 分钟 → Redis 自动 DEL
```

### 2.2 多机同步机制

所有应用节点共享同一个 Redis 实例（或集群），且使用相同的 Session 键前缀与 Cookie 名称。因此：

- 用户在**任一节点**登录后，Session 数据写入共享 Redis。
- 后续请求无论被负载均衡转发到**哪个节点**，该节点都能通过 Cookie 中的同一 Session ID 从 Redis 读到登录态。
- 登出操作在任一节点执行，所有节点立即感知（Redis 中的 Session 数据被销毁）。

```
用户 ──登录──▶ 节点A ──HSET──▶ Redis（共享）
                                    ▲
用户 ──请求──▶ 节点B ──HGET─────────┘  （读到登录态）
用户 ──请求──▶ 节点C ──HGET─────────┘  （读到登录态）
```

### 2.3 配置方法

#### 步骤一：配置 Redis 连接（session 命名连接）

```yaml
jaravel:
  redis:
    connections:
      session:
        host: 127.0.0.1
        port: 6379
        database: 2          # 建议为 session 独立分配 database
```

#### 步骤二：配置 session-redis 属性

```yaml
jaravel:
  session:
    redis:
      connection: session          # 对应 jaravel.redis.connections.session
      prefix: laravel_session      # Session 键前缀
      lifetime: 30                 # Session 生命周期（分钟）
      cookie: manage_session       # Cookie 名称
      auto-register: true          # 自动注册 redis-session guard 驱动
```

#### 步骤三：将 guard 驱动切换为 redis-session

```yaml
jaravel:
  auth:
    guards:
      web:
        driver: redis-session      # 使用 Redis Session 守卫
        provider: users
```

配置完成后，业务代码无需感知 Redis，通过 `Auth` 门面操作即可：

```java
Auth.guard("web").login(user);          // 登录，写入 Redis Session

if (Auth.guard("web").check()) {        // 检查登录态（从 Redis Session 读取）
    Authenticatable user = Auth.guard("web").user();
}

Auth.guard("web").logout();             // 登出，销毁 Redis Session
```

### 2.4 与基础 SessionGuard 的对比

| 对比维度 | 基础 SessionGuard（auth 模块） | RedisSessionGuard（session-redis 模块） |
| --- | --- | --- |
| Session 后端 | HttpSession（容器本地内存） | Redis（共享存储） |
| 多机同步 | 不支持，仅单节点有效 | 支持，所有节点共享同一 Redis |
| 登录态可见性 | 仅当前节点可见 | 任一节点登录，全节点可见 |
| 适用场景 | 单机部署、本地开发 | 集群部署、多机负载均衡 |
| 过期机制 | 容器管理 | Redis TTL 滑动过期 |
| 依赖 | 无额外依赖 | 依赖 `redis-config` 模块 |

> **集群部署必须使用 `redis-session` 驱动**。若仍使用基础 SessionGuard，用户在节点切换时会丢失登录态。

---

## 3. 缓存共享（redis-cache 模块）

### 3.1 工作原理

`redis-cache` 模块提供 `RedisCacheDriver`，实现 `CacheDriver` 接口，底层通过 `redis-config` 模块的 `RedisManager` 获取指定命名连接的 Redis 命令接口。

#### 序列化策略

- 缓存值通过 Jackson `ObjectMapper` 序列化为 **JSON 字符串**存储。
- 读取时返回反序列化后的 Java 对象（`Map` / `List` / `String` / `Number` 等）。
- TTL `<= 0` 表示永不过期，使用 `SET` 而非 `SETEX`。

#### TTL 机制

- 写入带 TTL 的缓存时使用 Redis `SETEX key ttl value`，原子性设置值与过期时间。
- 写入永久缓存时使用 `SET key value`。
- 过期由 Redis 自动管理，无需应用轮询清理。

#### 键扫描机制

- `allKeys()` 使用 `SCAN` 命令遍历键空间（非 `KEYS`，避免阻塞 Redis）。
- `removeAll()` 同样基于 `SCAN` 遍历并删除，避免 `FLUSHDB` 影响同一 Redis 中的其他用途（如 Session）。

### 3.2 多机同步机制

由于所有应用实例共享同一个 Redis 实例（或集群），且缓存驱动使用统一的键前缀：

- 节点 A 写入的缓存，对节点 B **立即可见**（Redis 读写强一致）。
- 缓存命中率在多机环境下不会下降，反而因为所有节点共享缓存空间而整体提升。
- 任一节点执行 `removeAll()` 后，所有节点的缓存同时清空。

```
节点A ──SET──▶ Redis（共享） ──GET──▶ 节点B（命中）
节点A ──DEL──▶ Redis（共享） ──GET──▶ 节点C（未命中）
```

### 3.3 配置方法

#### 步骤一：配置 Redis 连接（cache 命名连接）

```yaml
jaravel:
  redis:
    connections:
      cache:
        host: 127.0.0.1
        port: 6379
        database: 1          # 建议为 cache 独立分配 database
```

#### 步骤二：配置 redis-cache 属性

```yaml
jaravel:
  cache:
    redis:
      connection: cache          # 对应 jaravel.redis.connections.cache
      auto-register: true        # 自动注册到 CacheManager
```

#### 步骤三：将默认 store 设为 redis

```yaml
jaravel:
  cache:
    default-store: redis         # 默认缓存 store 设为 redis
```

配置完成后，通过 `Cache` 门面操作：

```java
// 使用默认 redis store
Cache.put("user:123", user, 3600);
Object cached = Cache.get("user:123");

// 或显式指定 redis store
Cache.store("redis").put("user:123", user, 3600);
```

### 3.4 替代方案：DatabaseCacheDriver

如果不希望引入 Redis，可使用 `cache` 模块自带的 `DatabaseCacheDriver`，将缓存持久化到共享数据库的 `jaravel_cache` 表中，同样实现跨节点共享。

```yaml
jaravel:
  cache:
    default-store: database
    prefix: myapp
    database-table: jaravel_cache
```

- 基于 Spring `JdbcTemplate`，自动建表，自动适配 MySQL / PostgreSQL / SQLite / H2 / SQL Server 方言。
- 缓存值以 JSON 字符串存储，TTL 通过 `expires_at` 时间戳管理。
- 适合需要跨进程共享缓存但不想引入 Redis 的轻量场景。

> **注意**：数据库缓存的读写性能低于 Redis 缓存，高并发场景建议优先使用 `redis` store。

### 3.5 与 ArrayCacheDriver 的对比

| 对比维度 | ArrayCacheDriver（cache 模块） | RedisCacheDriver（redis-cache 模块） | DatabaseCacheDriver（cache 模块） |
| --- | --- | --- | --- |
| 存储介质 | JVM 内存（`ConcurrentHashMap`） | Redis | 关系型数据库 |
| 多机同步 | 不支持，仅当前进程有效 | 支持，所有节点共享 Redis | 支持，所有节点共享数据库 |
| 读写性能 | 最快（内存） | 快（网络 + 内存） | 较慢（磁盘 I/O） |
| 进程重启 | 缓存丢失 | 缓存保留 | 缓存保留 |
| 适用场景 | 单元测试、本地开发 | 集群部署、生产环境 | 轻量集群、无 Redis 环境 |
| TTL 机制 | 惰性清理过期条目 | Redis `SETEX` / `EXPIRE` | `expires_at` 时间戳 + 异步删除 |

> **集群部署必须使用 `redis` 或 `database` store**。若使用 `array` store，各节点缓存互相隔离，无法共享。

---

## 4. Redis 基础设施（redis-config 模块）

`redis-config` 是 `redis-cache`、`session-redis` 等模块的基础依赖，提供统一的 Redis 连接管理能力，避免每个模块各自创建连接池。

### 4.1 RedisManager：多命名连接管理

`RedisManager` 对齐 Laravel `Illuminate\Redis\RedisManager`，管理多个命名连接。每个连接对应一个独立的 Lettuce 连接对象，所有连接均为线程安全，可被多线程共享，在首次访问时惰性创建，进程生命周期内复用。

```java
public class RedisManager {
    public RedisCommands<String, String> sync();              // 默认连接
    public RedisCommands<String, String> sync(String name);   // 指定连接

    public StatefulConnection<String, String> connection(String name);          // 字符串编码
    public StatefulConnection<byte[], byte[]> binaryConnection(String name);    // 字节编码

    public String getPrefix();                       // 全局键前缀
    public Set<String> connectionNames();            // 所有已配置的连接名
    public String getDefaultConnection();            // 默认连接名
    public void shutdown();                          // 优雅关闭（@PreDestroy）
}
```

预定义的命名连接及其用途：

| 连接名 | 用途 | 建议 database |
| --- | --- | --- |
| `default` | 默认连接、分布式锁 | 0 |
| `cache` | 缓存（`redis-cache`） | 1 |
| `session` | 会话（`session-redis`） | 2 |
| `model-cache` | 模型缓存（`model-cache` + `redis-cache`） | 3 |

> 为不同用途分配独立的 `database`（或独立 Redis 实例），可避免键空间冲突，便于运维隔离。

### 4.2 三种部署模式

`redis-config` 支持 standalone、sentinel、cluster 三种部署模式，通过 `jaravel.redis.options.cluster` 配置切换。

#### 模式一：standalone（单机/主从）

适用于开发环境或中小规模生产环境。通过 `host:port` 连接单个 Redis 实例（主从模式下连接主节点）。

```yaml
jaravel:
  redis:
    client: lettuce
    options:
      cluster: redis               # 单机模式
      prefix: "myapp_"
    connections:
      default:
        host: 127.0.0.1
        port: 6379
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

#### 模式二：sentinel（哨兵高可用）

适用于要求高可用的生产环境。通过 Sentinel 列表自动发现 master 节点，master 宕机时 Sentinel 自动完成主从切换，应用无感知。

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
      cache:
        sentinelMaster: mymaster
        sentinels: "sentinel1:26379,sentinel2:26379,sentinel3:26379"
        password: "secret"
        database: 1
      session:
        sentinelMaster: mymaster
        sentinels: "sentinel1:26379,sentinel2:26379,sentinel3:26379"
        password: "secret"
        database: 2
      model-cache:
        sentinelMaster: mymaster
        sentinels: "sentinel1:26379,sentinel2:26379,sentinel3:26379"
        password: "secret"
        database: 3
```

#### 模式三：cluster（Redis Cluster）

适用于大规模、高吞吐的生产环境。通过集群节点列表连接，Lettuce 客户端自动路由请求到正确的分片节点，支持水平扩展。

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
      cache:
        clusterNodes: "node1:6379,node2:6379,node3:6379"
        password: "secret"
      session:
        clusterNodes: "node1:6379,node2:6379,node3:6379"
        password: "secret"
      model-cache:
        clusterNodes: "node1:6379,node2:6379,node3:6379"
        password: "secret"
```

> **注意**：Redis Cluster 模式下不支持 `SELECT` 切换 database（仅 database 0 可用），因此各命名连接的 `database` 配置不生效，需通过键前缀（`prefix`）隔离命名空间。

#### 三种模式对比

| 模式 | `cluster` 值 | 高可用 | 水平扩展 | 适用规模 | 配置要点 |
| --- | --- | --- | --- | --- | --- |
| 单机 | `redis` | 否 | 否 | 开发/小规模 | `host` + `port` |
| 哨兵 | `sentinel` | 是（自动故障转移） | 否 | 中规模生产 | `sentinelMaster` + `sentinels` |
| 集群 | `cluster` | 是（分片容错） | 是（分片） | 大规模生产 | `clusterNodes`（无 database 隔离） |

### 4.3 分布式锁（RedisLockProvider）

`redis-config` 提供基于 Redis 的分布式锁实现 `RedisLockProviderImpl`，对齐 Laravel `Illuminate\Cache\RedisLock`，实现 `schedule` 模块的 `RedisLockProvider` 接口。

```java
public class RedisLockProviderImpl implements RedisLockProvider {
    public boolean tryLock(String key, long ttlSeconds);   // SET key value NX EX ttl
    public void unlock(String key);                         // DEL key
}
```

- **加锁**：使用 Redis `SET key value NX EX seconds` 命令，原子性地实现"不存在则写入并设置过期"。
- **解锁**：使用 `DEL key` 释放锁。
- **TTL 兜底**：即使持有锁的节点宕机未正常释放，锁也会在 TTL 到期后自动释放，避免死锁。

#### 在集群中的作用

分布式锁是集群部署的关键组件，主要用于：

1. **定时任务互斥**：`schedule` 模块使用 `RedisLockProvider` 确保同一定时任务在集群中只有一个节点执行，避免重复处理。

```java
@Autowired
private RedisLockProvider lockProvider;

public void executeWithLock(String taskName) {
    String lockKey = "schedule:lock:" + taskName;
    if (lockProvider.tryLock(lockKey, 300)) {     // 加锁，TTL 300 秒
        try {
            // 执行定时任务（仅一个节点会进入此处）
        } finally {
            lockProvider.unlock(lockKey);          // 释放锁
        }
    }
}
```

2. **业务幂等控制**：防止并发操作导致的数据重复处理。

> `RedisLockProvider` bean 使用默认 Redis 连接（`default` 命名连接）。当 `schedule` 模块不存在时（`RedisLockProvider` 类不在 classpath），此 bean 不会被创建。

### 4.4 自动装配

`RedisAutoConfiguration` 通过 `@AutoConfiguration` 注册，当 classpath 存在 `RedisManager` 且配置了 `jaravel.redis.connections` 时生效。

创建的 bean：
- `RedisManager` — Redis 管理器（`@ConditionalOnMissingBean`，便于业务方覆盖）
- `RedisLockProvider` — Redis 分布式锁提供者，使用默认 Redis 连接，供 `schedule` 模块使用

---

## 5. 完整集群配置示例

以下是一个完整的集群部署 `application.yml` 配置示例，涵盖 Redis 基础设施、Session 共享、缓存共享、模型缓存、定时任务锁全部场景：

```yaml
jaravel:
  # ============ Redis 基础设施 ============
  redis:
    client: lettuce
    options:
      cluster: redis                       # 单机模式（生产建议 sentinel 或 cluster）
      prefix: "myapp_"                     # 全局键前缀，隔离不同应用
    connections:
      default:                             # 默认连接 + 分布式锁
        host: 192.168.1.100
        port: 6379
        password: "your_redis_password"
        database: 0
      cache:                               # 缓存连接
        host: 192.168.1.100
        port: 6379
        password: "your_redis_password"
        database: 1
      session:                             # Session 连接
        host: 192.168.1.100
        port: 6379
        password: "your_redis_password"
        database: 2
      model-cache:                         # 模型缓存连接
        host: 192.168.1.100
        port: 6379
        password: "your_redis_password"
        database: 3

  # ============ Session 共享 ============
  session:
    redis:
      connection: session                  # 对应 jaravel.redis.connections.session
      prefix: laravel_session              # Session 键前缀
      lifetime: 30                         # Session 生命周期（分钟）
      cookie: manage_session               # Cookie 名称
      auto-register: true                  # 自动注册 redis-session guard 驱动

  # ============ 认证守卫 ============
  auth:
    guards:
      web:
        driver: redis-session              # 使用 Redis Session 守卫（集群必须）
        provider: users

  # ============ 缓存共享 ============
  cache:
    default-store: redis                   # 默认缓存 store 设为 redis（集群必须）
    prefix: jaravel                        # 缓存键前缀
    redis:
      connection: cache                    # 对应 jaravel.redis.connections.cache
      auto-register: true                  # 自动注册 redis store 到 CacheManager

  # ============ 模型缓存 ============
  model-cache:
    enabled: true                          # 全局开关
    store: redis                           # 使用 redis store（集群共享）
    default-ttl: 3600                      # 默认缓存 TTL（秒）
    key-prefix: "model-cache:"             # 缓存键前缀
```

> **说明**：以上配置中所有节点必须保持一致。各节点仅 `server.port` 等实例级配置可不同。Redis 的 `host` 应指向集群共享的 Redis 地址，而非 `127.0.0.1`（除非所有节点与 Redis 同机）。

### Sentinel 模式的完整配置示例

```yaml
jaravel:
  redis:
    client: lettuce
    options:
      cluster: sentinel
      prefix: "myapp_"
    connections:
      default:
        sentinelMaster: mymaster
        sentinels: "sentinel1:26379,sentinel2:26379,sentinel3:26379"
        password: "your_redis_password"
        database: 0
      cache:
        sentinelMaster: mymaster
        sentinels: "sentinel1:26379,sentinel2:26379,sentinel3:26379"
        password: "your_redis_password"
        database: 1
      session:
        sentinelMaster: mymaster
        sentinels: "sentinel1:26379,sentinel2:26379,sentinel3:26379"
        password: "your_redis_password"
        database: 2
      model-cache:
        sentinelMaster: mymaster
        sentinels: "sentinel1:26379,sentinel2:26379,sentinel3:26379"
        password: "your_redis_password"
        database: 3
  session:
    redis:
      connection: session
      prefix: laravel_session
      lifetime: 30
      cookie: manage_session
      auto-register: true
  auth:
    guards:
      web:
        driver: redis-session
        provider: users
  cache:
    default-store: redis
    prefix: jaravel
    redis:
      connection: cache
      auto-register: true
  model-cache:
    enabled: true
    store: redis
    default-ttl: 3600
    key-prefix: "model-cache:"
```

---

## 6. 部署检查清单

### 6.1 部署前准备

- [ ] **Redis 基础设施就绪**：部署 Redis 实例（单机/哨兵/集群），确认所有应用节点网络可达。
- [ ] **Redis 密码设置**：生产环境必须设置密码，所有连接配置中填写一致的 `password`。
- [ ] **database 分配**：为 `default`、`cache`、`session`、`model-cache` 分配独立的 database 编号（单机/哨兵模式）。
- [ ] **键前缀规划**：确定全局 `prefix`、Session `prefix`、缓存 `prefix`，确保不同应用间命名空间隔离。
- [ ] **负载均衡就绪**：配置负载均衡器（如 Nginx、SLB），确保会话粘性非必需（因 Session 已共享）。

### 6.2 依赖引入

- [ ] **引入 starter**：确认 `pom.xml` 中已引入 `starter` 依赖（聚合了 `redis-config`、`redis-cache`、`session-redis` 等模块）。

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>starter</artifactId>
    <version>0.1.2</version>
</dependency>
```

- [ ] **（可选）引入 model-cache**：如需模型缓存，单独引入。

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>model-cache</artifactId>
    <version>0.1.2</version>
</dependency>
```

### 6.3 配置检查

- [ ] **Redis 连接配置**：`jaravel.redis.connections` 中配置了 `default`、`cache`、`session`、`model-cache` 四个连接，`host` 指向共享 Redis（非 `127.0.0.1`）。
- [ ] **集群模式选择**：`jaravel.redis.options.cluster` 已根据 Redis 部署形态设置为 `redis` / `sentinel` / `cluster`。
- [ ] **Session 驱动**：`jaravel.auth.guards.web.driver` 设为 `redis-session`。
- [ ] **Session 连接**：`jaravel.session.redis.connection` 设为 `session`，`auto-register` 为 `true`。
- [ ] **缓存默认 store**：`jaravel.cache.default-store` 设为 `redis`。
- [ ] **缓存连接**：`jaravel.cache.redis.connection` 设为 `cache`，`auto-register` 为 `true`。
- [ ] **模型缓存 store**：`jaravel.model-cache.store` 设为 `redis`（如启用模型缓存）。
- [ ] **配置一致性**：所有节点的上述配置完全一致（仅 `server.port` 等可不同）。

### 6.4 功能验证

- [ ] **Session 共享验证**：在节点 A 登录，请求转发到节点 B，验证登录态保持。
- [ ] **Session 登出验证**：在任一节点登出，验证所有节点均感知登出。
- [ ] **缓存共享验证**：在节点 A 写入缓存，在节点 B 读取，验证命中。
- [ ] **缓存清空验证**：在任一节点执行 `Cache.flush()`，验证所有节点缓存清空。
- [ ] **定时任务互斥验证**：多节点同时启动，验证同一定时任务仅在一个节点执行。
- [ ] **模型缓存验证**：在节点 A 写入数据并失效缓存，在节点 B 查询，验证读到最新数据。

### 6.5 运维检查

- [ ] **Redis 监控**：配置 Redis 内存、连接数、命中率监控告警。
- [ ] **Redis 持久化**：根据数据重要性配置 RDB / AOF 持久化（Session 丢失影响较小，缓存丢失可重建，但持久化可减少重启开销）。
- [ ] **Redis 备份**：定期备份 Redis 数据。
- [ ] **连接池监控**：监控 Lettuce 连接池使用情况，避免连接耗尽。
- [ ] **故障演练**：模拟单节点宕机，验证请求正常转发到其他节点且登录态保持。

---

## 7. 集群中的模型缓存

### 7.1 模型缓存概述

`model-cache` 是一个可选模块，参考 Laravel `laravel-model-caching` 方案，在 Model 类上通过 `@CachableModel` 注解手动开启查询缓存。它构建在 `cache` 模块之上，复用 `CacheStore` / `CacheManager` 的能力。

### 7.2 版本号失效机制

由于 `cache` 模块的 `CacheStore` 不支持 tag（array / file / database 驱动均无 tag 语义），`model-cache` 采用**版本号机制**实现缓存批量失效：

1. **版本号键**：每个模型类在缓存中维护一个版本号，键为 `model-cache:{modelPrefix}:version`。
2. **缓存键含版本号**：所有数据缓存键都拼入当前版本号：
   - 主键查询：`model-cache:{modelPrefix}:v{version}:find:{id}`
   - 任意查询：`model-cache:{modelPrefix}:v{version}:query:{queryKey}`
3. **失效时递增版本号**：调用 `invalidate(Class)` 时，`increment` 版本号键。新请求读取到新版本号，生成新的缓存键，旧版本键不再被命中。
4. **旧缓存自然清除**：旧版本缓存键随自身 TTL 到期后被驱动惰性清理。

### 7.3 集群中的多节点同步

在集群环境中，模型缓存必须使用 `redis` store（或 `database` store），确保：

- **版本号共享**：所有节点读写同一个版本号键。任一节点执行 `invalidate(Class)` 递增版本号后，其他节点立即读到新版本号，旧缓存键全部失效。
- **数据缓存共享**：节点 A 查询并回填的缓存，节点 B 直接命中，避免重复回源。
- **失效操作全局生效**：在任一节点写入数据后调用 `invalidate`，全集群缓存同步失效。

```
节点A ──写入数据──▶ 调用 invalidate(User.class) ──INCR version──▶ Redis
                                                                    │
节点B ──查询 User──▶ 读取 version（已递增）──▶ 生成新缓存键──▶ 未命中──▶ 回源数据库（读到最新数据）
```

> 若使用 `array` store，各节点版本号互相隔离，节点 A 的失效操作不影响节点 B，节点 B 仍读旧缓存，导致数据不一致。**集群部署中模型缓存禁止使用 `array` store。**

### 7.4 配置方法

#### 步骤一：配置模型缓存使用 redis store

```yaml
jaravel:
  model-cache:
    enabled: true                  # 全局开关
    store: redis                   # 使用 redis store（集群必须）
    default-ttl: 3600              # 默认缓存 TTL（秒）
    key-prefix: "model-cache:"     # 缓存键前缀
```

> `store: redis` 要求已引入 `redis-cache` 模块并在 `CacheManager` 中注册了 `redis` store（即 `jaravel.cache.redis.auto-register: true`）。

#### 步骤二：在 Model 类上标注注解

```java
import com.weacsoft.jaravel.vendor.modelcache.CachableModel;
import com.weacsoft.jaravel.vendor.database.BaseModel;

@CachableModel(ttl = 600)          // 开启缓存，TTL 600 秒
public class User extends BaseModel<User, Long> {
    // ...
}
```

#### 步骤三：使用门面缓存查询

```java
// 按主键查询（命中缓存直接返回，未命中执行 loader 并回填）
User user = ModelCache.find(User.class, 1L, () -> User.find(1L));

// 缓存列表查询
List<User> all = ModelCache.findAll(User.class, "all", () -> User.all());

// 写入后失效缓存
user.save();
ModelCache.invalidate(User.class);          // 失效整个模型类（版本号递增，全集群生效）
// 或仅失效单条：
ModelCache.invalidate(User.class, 1L);      // 仅失效主键查询键
```

### 7.5 集群注意事项

| 注意点 | 说明 |
| --- | --- |
| store 选择 | 集群必须使用 `redis` 或 `database` store，禁止 `array` |
| 版本号共享 | `redis` store 下版本号全局共享，失效操作全集群生效 |
| 序列化局限 | `redis` store 经 JSON 序列化，复杂对象还原为 `LinkedHashMap` / `ArrayList` |
| 不缓存 null | `find` 等 loader 返回 `null` 时不回填，未找到的记录每次回源 |
| 查询缓存一致性 | `invalidate(Class, id)` 仅失效主键查询键，查询缓存可能含旧数据，需整体失效时调用 `invalidate(Class)` |
| TTL 设置 | 旧版本缓存随 TTL 过期清理，建议合理设置 TTL 避免空间浪费 |

### 7.6 模型缓存在集群中的完整链路

```
                          ┌─────────────────────────────────┐
                          │           共享 Redis             │
                          │  ┌─────────────────────────┐    │
                          │  │ model-cache:User:version│    │
                          │  │ model-cache:User:v1:find:1  │  │
                          │  │ model-cache:User:v1:query:all│ │
                          │  └─────────────────────────┘    │
                          └─────────────────────────────────┘
                               ▲           ▲           ▲
                               │           │           │
                    GET/PUT    │    INCR   │    GET/PUT│
                               │           │           │
              ┌────────────────┤           │           │
              │                │           │           │
        ┌─────┴─────┐   ┌──────┴────┐  ┌───┴──────┐
        │  节点 A    │   │  节点 B   │  │  节点 C   │
        │ ModelCache │   │ ModelCache│  │ ModelCache│
        └────────────┘   └───────────┘  └───────────┘
```

所有节点通过 `CacheManager` 访问同一个 `redis` store，底层连接同一套 Redis。版本号、数据缓存均全局共享，任一节点的失效操作对全集群立即可见。

---

## 附录：模块依赖关系

```
starter（聚合入口）
├── redis-config ──────────── Redis 连接管理（基础依赖）
│   └── RedisLockProvider ─── 分布式锁（供 schedule 使用）
├── redis-cache ───────────── Redis 缓存驱动
│   └── 依赖 redis-config
├── session-redis ─────────── Redis Session 守卫
│   └── 依赖 redis-config
├── cache ─────────────────── 缓存基础设施（Array/File/Database 驱动）
├── model-cache（可选）────── 模型查询缓存
│   └── 依赖 cache（复用 CacheStore）
├── schedule ──────────────── 定时任务
│   └── 使用 RedisLockProvider（来自 redis-config）
└── ...（其他模块）
```

> **集群部署的核心三件套**：`redis-config`（连接管理）+ `redis-cache`（缓存共享）+ `session-redis`（Session 共享）。三者均已包含在 `starter` 中，引入 starter 即可获得全部集群能力。
