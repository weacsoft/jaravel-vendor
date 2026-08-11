# redis-cache 模块

> Jaravel-Vendor 的 Redis 缓存驱动模块，对齐 Laravel `Illuminate\Cache\RedisStore`。提供 `RedisCacheDriverFactory` 驱动工厂（实现 `CacheDriverFactory`，按需创建），由 `CacheManager` 在配置了 `redis` store 时按需创建 `RedisCacheDriver` 实例。底层通过 `redis` 模块的 `RedisManager` 获取指定命名连接的 Redis 命令接口，所有缓存键值以 JSON 序列化存储，TTL 通过 Redis `SETEX`/`EXPIRE` 实现。包名统一为 `com.weacsoft.jaravel.vendor.redis.cache`。

---

## 目录

- [1. 模块概述](#1-模块概述)
- [2. 依赖信息](#2-依赖信息)
- [3. 类总览](#3-类总览)
- [4. RedisCacheDriverFactory —— Redis 驱动工厂](#4-rediscachedriverfactory--redis-驱动工厂)
- [5. RedisCacheDriver —— Redis 缓存驱动](#5-rediscachedriver--redis-缓存驱动)
- [6. RedisCacheProperties —— 配置属性](#6-rediscacheproperties--配置属性)
- [7. RedisCacheAutoConfiguration —— 自动装配](#7-rediscacheautoconfiguration--自动装配)
- [8. 配置](#8-配置)
- [9. 使用示例](#9-使用示例)

---

## 1. 模块概述

`redis-cache` 模块对齐 Laravel 的 Redis 缓存驱动，核心特性如下：

| Laravel 特性 | redis-cache 对应实现 | 说明 |
| --- | --- | --- |
| `Illuminate\Cache\RedisStore` | `RedisCacheDriver` | Redis 缓存驱动（JSON 序列化，TTL 管理） |
| 驱动工厂（按需创建） | `RedisCacheDriverFactory` | `support("redis")` + `create(config)`，由 `CacheManager` 按需调用 |
| `config/cache.php` stores.redis | `jaravel.cache.stores.redis` | 配置式注册 redis store |

### 架构分层

```
CacheManager（cache 模块，按配置按需创建 store）
  └── CacheDriverFactory（驱动工厂，support + create）
        └── RedisCacheDriverFactory   → RedisCacheDriver（Redis 缓存驱动）
                                          └── RedisManager（redis 模块，命名连接管理）
```

采用**工厂模式 + 配置驱动按需创建**（对齐 cache 模块的设计）：`RedisCacheDriverFactory` 注册为 Spring Bean，`CacheManager` 在配置了 `redis` store 时才调用 `factory.create(config)` 创建驱动实例，不会预创建。

---

## 2. 依赖信息

### Maven 坐标

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>redis-cache</artifactId>
    <version>0.1.2</version>
</dependency>
```

### 传递依赖

| 依赖 | 用途 |
| --- | --- |
| `cache` | 提供 `CacheDriver` 接口、`CacheDriverFactory` 工厂契约与 `CacheManager` |
| `redis` | 提供 `RedisManager` 命名连接管理 |
| `spring-boot-autoconfigure` | 自动装配 |
| `jackson-databind` | 缓存值 JSON 序列化 |
| `slf4j-api` | 日志门面 |

> 运行环境要求：JDK 17+，Spring Boot 3.2.12（Spring 6.x）。

---

## 3. 类总览

```
com.weacsoft.jaravel.vendor.redis.cache
├── RedisCacheDriverFactory     // Redis 驱动工厂（support("redis") + create(config)）
├── RedisCacheDriver            // Redis 缓存驱动（实现 CacheDriver，JSON 序列化）
├── RedisCacheProperties        // 配置属性（jaravel.cache.redis.*）
└── RedisCacheAutoConfiguration // 自动装配（注册 RedisCacheDriverFactory Bean）
```

---

## 4. RedisCacheDriverFactory —— Redis 驱动工厂

`com.weacsoft.jaravel.vendor.redis.cache.RedisCacheDriverFactory`

实现 `CacheDriverFactory` 接口，支持 `"redis"` 驱动名。由 `RedisCacheAutoConfiguration` 在 `RedisManager` 存在时注册为 Bean，`CacheManager` 通过 `CacheDriverFactory` 自动收集并按需创建 redis store。

### 构造器

| 构造器签名 | 说明 |
| --- | --- |
| `RedisCacheDriverFactory(RedisManager redisManager)` | 默认连接名为 `cache` |
| `RedisCacheDriverFactory(RedisManager redisManager, String defaultConnection)` | 指定默认连接名（store 配置未指定 connection 时回退到此值） |

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `boolean support(String driver)` | 判断是否支持该驱动名，`"redis"` 返回 `true` |
| `CacheDriver create(Map<String, Object> config)` | 创建 `RedisCacheDriver` 实例；从 config 读取 `connection`，未指定则回退到默认连接名 |

### 连接名解析逻辑

`create(config)` 创建驱动时，连接名按以下优先级确定：

1. store 配置中的 `connection`（`jaravel.cache.stores.redis.connection`）
2. 工厂构造时传入的默认连接名（通常来自 `jaravel.cache.redis.connection`，默认 `cache`）

```java
// RedisCacheDriverFactory.create 内部逻辑
Object connection = config.get("connection");
String connectionName = (connection != null && !connection.toString().isEmpty())
        ? connection.toString()
        : defaultConnection;
return new RedisCacheDriver(redisManager, connectionName);
```

---

## 5. RedisCacheDriver —— Redis 缓存驱动

`com.weacsoft.jaravel.vendor.redis.cache.RedisCacheDriver`

实现 `CacheDriver` 接口。所有缓存键值均以 JSON 序列化存储，读取时返回反序列化后的 Java 对象。由于所有实例共享同一 Redis 实例（或集群），写入的缓存对所有实例立即可见，天然实现多机缓存同步。

### 构造器

| 构造器签名 | 说明 |
| --- | --- |
| `RedisCacheDriver(RedisManager redisManager, String connectionName)` | 指定命名连接 |
| `RedisCacheDriver(RedisManager redisManager)` | 使用默认连接（`cache`） |

### 序列化策略

- 值通过 Jackson `ObjectMapper` 序列化为 JSON 字符串存储
- 读取时返回反序列化后的 Java 对象（Map / List / String / Number 等）
- TTL `<= 0` 表示永不过期，使用 `SET` 而非 `SETEX`

### 键扫描

`allKeys()` 使用 `SCAN` 命令遍历键空间（非 `KEYS`，避免阻塞），`removeAll()` 同样基于 `SCAN` 遍历并删除，避免 `FLUSHDB` 影响其他用途。

### 方法文档

继承 `CacheDriver` 接口全部方法。

| 方法 | 行为说明 |
| --- | --- |
| `put(key, value, ttl)` | 序列化为 JSON，`ttl > 0` 使用 `SETEX`，否则使用 `SET` |
| `get(key)` | 读取并反序列化 JSON 为 Java 对象，不存在返回 `null` |
| `exists(key)` | 检查缓存键是否存在 |
| `remove(key)` | 移除指定缓存键 |
| `removeAll()` | 清空所有缓存（SCAN 遍历删除，避免 `FLUSHDB`） |
| `allKeys()` | 获取所有缓存键（SCAN 遍历） |

### 使用示例

```java
// 直接使用 RedisCacheDriver
RedisCacheDriver driver = new RedisCacheDriver(redisManager, "cache");
driver.put("user:1", userObj, 3600);  // 缓存 1 小时
Object cached = driver.get("user:1");
boolean exists = driver.exists("user:1");
driver.remove("user:1");

// 通过 CacheManager 使用（自动装配后）
Cache.store("redis").put("key", value, 60);
Object val = Cache.store("redis").get("key");
```

---

## 6. RedisCacheProperties —— 配置属性

`com.weacsoft.jaravel.vendor.redis.cache.RedisCacheProperties`

配置属性，前缀 `jaravel.cache.redis`。

```java
@ConfigurationProperties(prefix = "jaravel.cache.redis")
public class RedisCacheProperties {
    private String connection = "cache";     // Redis 连接名，对应 jaravel.redis.connections
    private Boolean autoRegister;            // 装配覆盖开关，null=按 store 的 driver 自动判定
}
```

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `String getConnection()` | 获取 Redis 连接名，默认 `cache` |
| `void setConnection(String connection)` | 设置 Redis 连接名 |
| `Boolean getAutoRegister()` | 装配覆盖开关，`null`（默认）表示按 store 的 driver 自动判定 |
| `void setAutoRegister(Boolean autoRegister)` | 设置覆盖开关 |

---

## 7. RedisCacheAutoConfiguration —— 自动装配

`com.weacsoft.jaravel.vendor.redis.cache.RedisCacheAutoConfiguration`

Redis 缓存自动装配。当 `RedisManager` 存在时，创建 `RedisCacheDriverFactory` 并注册为 Spring Bean。`CacheAutoConfiguration` 会自动收集所有 `CacheDriverFactory` Bean 并注册到 `CacheManager`，由 `CacheManager` 在配置了 `redis` store 时按需创建驱动。

> 不再直接创建 `RedisCacheDriver` 或通过 `RedisCacheRegistrar` 注册 store，而是注册工厂，由 `CacheManager` 根据配置按需创建（对齐 cache 模块的工厂模式）。

### 装配条件

| 注解 | 条件 |
| --- | --- |
| `@AutoConfiguration` | Spring Boot 自动装配 |
| `@AutoConfigureAfter(RedisAutoConfiguration.class)` | 在 Redis 配置之后装配 |
| `@ConditionalOnClass({RedisCacheDriver, CacheDriverFactory, RedisManager})` | 类路径存在相关类 |
| `@Conditional(OnRedisCacheStoreCondition.class)` | **配置里确实声明了 `driver: redis` 的缓存 store**（安装 ≠ 启用） |
| `@ConditionalOnBean(RedisManager.class)` | 容器中存在 `RedisManager` bean（兜底保护） |

> **装配原则：用上了才注册。** 仅把 `jaravel-redis-cache` 放进 classpath **不会**触发装配，
> 必须在 `jaravel.cache.stores.*.driver` 中显式选用 `redis`。
> 未选用时本模块不创建任何 Bean、不连接 Redis，**无 Redis 环境也能正常启动**。
>
> 覆盖开关 `jaravel.cache.redis.auto-register` 优先级最高：
> `true` 强制启用、`false` 强制关闭、不配置则按 driver 自动判定。

### 注册的 Bean

| Bean | 类型 | 说明 |
| --- | --- | --- |
| `redisCacheDriverFactory` | `RedisCacheDriverFactory` | Redis 缓存驱动工厂（`@ConditionalOnMissingBean`，便于业务方覆盖），使用 `cache` 命名连接 |

### 第三方模块扩展说明

`RedisCacheDriverFactory` 注册为 `CacheDriverFactory` Bean 后，`CacheManager` 在创建 `redis` store 时遍历所有工厂，找到 `support("redis")` 返回 `true` 的工厂并调用 `create(config)` 创建 `RedisCacheDriver` 实例。

---

## 8. 配置

### Redis 缓存配置

```yaml
jaravel:
  cache:
    redis:
      connection: cache          # Redis 连接名，对应 jaravel.redis.connections 中的配置
      # auto-register: true      # 可选，覆盖开关（true 强制启用 / false 强制关闭）
    stores:
      redis:
        driver: redis            # ← 本模块的启用开关，不写则完全不装配
        connection: cache        # 可覆盖顶层 jaravel.cache.redis.connection 配置
```

### 配合 redis 模块配置连接

```yaml
jaravel:
  redis:
    connections:
      cache:
        host: 127.0.0.1
        port: 6379
        database: 1
```

> `jaravel.cache.stores.redis.connection` 优先于 `jaravel.cache.redis.connection`。若 stores 中未配置 connection，则回退到 `jaravel.cache.redis.connection`（默认 `cache`）。

---

## 9. 使用示例

注册后，业务方可通过 `Cache.store("redis")` 使用 Redis 缓存，或将 `jaravel.cache.default-store` 设为 `redis` 使其成为默认 store。

### 配置式（推荐）

```yaml
jaravel:
  cache:
    default-store: redis         # 设为默认 store
    stores:
      redis:
        driver: redis
        connection: cache
```

```java
// 设为默认 store 后直接使用
Cache.put("user:123", user, 3600);
Object cached = Cache.get("user:123");

// 或按名称使用
Cache.store("redis").put("user:123", user, 3600);
Object cached = Cache.store("redis").get("user:123");
```

### 多 Redis 连接

可为不同 store 配置不同连接：

```yaml
jaravel:
  cache:
    stores:
      redis:               # 默认 redis store，使用 cache 连接
        driver: redis
        connection: cache
      redis-session:       # 会话专用 redis store，使用 session 连接
        driver: redis
        connection: session
```

```java
Cache.store("redis").put("key", value, 60);
Cache.store("redis-session").put("session:123", sessionData, 1800);
```
