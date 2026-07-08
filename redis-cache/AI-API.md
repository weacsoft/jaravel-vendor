# redis-cache AI-API Reference

> Module: `redis-cache` | Package: `com.weacsoft.jaravel.vendor.redis.cache` | Version: 0.1.1

## Overview

redis-cache 模块提供了基于 Redis 的缓存驱动实现（`RedisCacheDriver`），对齐 Laravel 的 `RedisStore`。它通过 `RedisManager` 获取指定命名连接的 Redis 命令接口，所有缓存键值均以 JSON 序列化存储，TTL 通过 Redis `SETEX`/`EXPIRE` 实现。由于所有实例共享同一 Redis，天然实现多机缓存同步。该模块在 Spring Boot 自动装配时将 `redis` store 注册到 `CacheManager`，业务方可通过 `Cache::store("redis")` 使用。

## Classes & Interfaces

### RedisCacheDriver
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.redis.cache`
- **Implements**: `com.weacsoft.jaravel.vendor.cache.CacheDriver`
- **Description**: Redis 缓存驱动，底层通过 `RedisManager` 获取 Redis 命令接口，值通过 Jackson `ObjectMapper` 序列化为 JSON 存储。`allKeys()` 使用 SCAN 命令遍历键空间（非 KEYS，避免阻塞）。TTL <= 0 表示永不过期，使用 SET 而非 SETEX。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `RedisCacheDriver` | `RedisManager redisManager, String connectionName` | - | 构造 Redis 缓存驱动，指定命名连接 |
| `RedisCacheDriver` | `RedisManager redisManager` | - | 使用默认连接构造 Redis 缓存驱动 |
| `put` | `String key, Object value, long ttlSeconds` | `boolean` | 写入缓存，TTL > 0 使用 SETEX，否则使用 SET |
| `get` | `String key` | `Object` | 读取缓存，反序列化 JSON 为 Java 对象，不存在返回 null |
| `exists` | `String key` | `boolean` | 检查缓存键是否存在 |
| `remove` | `String key` | `boolean` | 移除指定缓存键 |
| `removeAll` | - | `void` | 清空所有缓存键（使用 SCAN 遍历后 DEL，避免 FLUSHDB） |
| `allKeys` | - | `Collection<String>` | 返回所有缓存键（SCAN 遍历） |

#### Usage Example
```java
// 直接使用 RedisCacheDriver
RedisCacheDriver driver = new RedisCacheDriver(redisManager, "cache");
driver.put("user:1", userObj, 3600);  // 缓存 1 小时
Object cached = driver.get("user:1");
boolean exists = driver.exists("user:1");
driver.remove("user:1");

// 通过 CacheManager 使用（自动装配后）
Cache::store("redis").put("key", value, 60);
Object val = Cache::store("redis").get("key");
```

### RedisCacheProperties
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.redis.cache`
- **Annotations**: `@ConfigurationProperties(prefix = "jaravel.cache.redis")`
- **Description**: Redis 缓存配置属性，前缀 `jaravel.cache.redis`。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `getConnection` | - | `String` | 获取 Redis 连接名，默认 `cache` |
| `setConnection` | `String connection` | `void` | 设置 Redis 连接名 |
| `isAutoRegister` | - | `boolean` | 是否自动注册到 CacheManager，默认 true |
| `setAutoRegister` | `boolean autoRegister` | `void` | 设置是否自动注册 |

#### Usage Example
```yaml
# application.yml
jaravel:
  cache:
    redis:
      connection: cache
      auto-register: true
```

### RedisCacheAutoConfiguration
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.redis.cache`
- **Annotations**: `@AutoConfiguration`, `@AutoConfigureAfter({CacheAutoConfiguration, RedisAutoConfiguration})`, `@ConditionalOnClass({RedisCacheDriver, CacheManager, RedisManager})`, `@ConditionalOnBean({RedisManager, CacheManager})`, `@ConditionalOnProperty(prefix = "jaravel.cache.redis", name = "auto-register", havingValue = "true", matchIfMissing = true)`
- **Description**: Redis 缓存自动装配。当 `RedisManager` 和 `CacheManager` 均存在时，创建 `RedisCacheDriver` 并注册为 `redis` store 到 `CacheManager`。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `redisCacheDriver` | `RedisManager redisManager, RedisCacheProperties properties` | `RedisCacheDriver` | 创建 Redis 缓存驱动 Bean（`@Bean`, `@ConditionalOnMissingBean`） |
| `redisCacheRegistrar` | `CacheManager cacheManager, RedisCacheDriver redisCacheDriver, CacheProperties cacheProperties` | `RedisCacheRegistrar` | 将 Redis 缓存驱动注册到 CacheManager（`@Bean`） |

#### Usage Example
```java
// 自动装配后，业务方通过 Cache facade 使用 Redis 缓存
// 或将 jaravel.cache.default-store 设为 redis 使其成为默认 store
Cache::store("redis").put("key", value, 3600);
```

### RedisCacheAutoConfiguration.RedisCacheRegistrar
- **Type**: class (static inner)
- **Package**: `com.weacsoft.jaravel.vendor.redis.cache`
- **Description**: 注册器，将 Redis store 添加到 CacheManager。构造时调用 `cacheManager.addStore("redis", new DefaultCacheStore(driver, prefix))`。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `RedisCacheRegistrar` | `CacheManager cacheManager, RedisCacheDriver driver, String prefix` | - | 构造时将 Redis store 注册到 CacheManager |
