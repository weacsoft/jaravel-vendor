# redis-cache

Redis 缓存驱动，对齐 Laravel `Illuminate\Cache\RedisStore`。实现 `CacheDriver` 接口，底层通过 `redis-config` 模块的 `RedisManager` 获取指定命名连接的 Redis 命令接口，所有缓存键值以 JSON 序列化存储，TTL 通过 Redis `SETEX`/`EXPIRE` 实现。

## 依赖

- `core` — 基础设施
- `cache` — 提供 `CacheDriver` 接口与 `CacheManager`
- `redis-config` — 提供 `RedisManager` 命名连接管理
- `spring-boot-autoconfigure` — 自动装配
- `jackson-databind` — 缓存值 JSON 序列化
- `slf4j-api` — 日志

## 核心接口

### RedisCacheDriver

Redis 缓存驱动，实现 `CacheDriver` 接口。所有缓存键值均以 JSON 序列化存储，读取时返回反序列化后的 Java 对象。由于所有实例共享同一 Redis 实例（或集群），写入的缓存对所有实例立即可见，天然实现多机缓存同步。

```java
public class RedisCacheDriver implements CacheDriver {
    public RedisCacheDriver(RedisManager redisManager, String connectionName);
    public RedisCacheDriver(RedisManager redisManager);   // 使用默认连接

    // CacheDriver 接口实现
    public boolean put(String key, Object value, long ttlSeconds);  // 写入缓存，ttl<=0 永不过期
    public Object get(String key);                                   // 读取缓存（反序列化 Java 对象）
    public boolean exists(String key);                              // 检查缓存是否存在
    public boolean remove(String key);                              // 移除缓存
    public void removeAll();                                         // 清空所有缓存（SCAN 遍历删除）
    public Collection<String> allKeys();                             // 获取所有缓存键（SCAN 遍历）
}
```

序列化策略：
- 值通过 Jackson `ObjectMapper` 序列化为 JSON 字符串存储
- 读取时返回反序列化后的 Java 对象（Map / List / String / Number 等）
- TTL `<= 0` 表示永不过期，使用 `SET` 而非 `SETEX`

键扫描：`allKeys()` 使用 `SCAN` 命令遍历键空间（非 `KEYS`，避免阻塞），`removeAll()` 同样基于 `SCAN` 遍历并删除，避免 `FLUSHDB` 影响其他用途。

### RedisCacheProperties

配置属性，前缀 `jaravel.cache.redis`。

```java
@ConfigurationProperties(prefix = "jaravel.cache.redis")
public class RedisCacheProperties {
    private String connection = "cache";     // Redis 连接名，对应 jaravel.redis.connections
    private boolean autoRegister = true;     // 是否自动注册到 CacheManager
}
```

## 配置

```yaml
jaravel:
  cache:
    redis:
      connection: cache          # Redis 连接名，对应 jaravel.redis.connections 中的配置
      auto-register: true        # 是否自动注册到 CacheManager（默认 true）
```

配合 redis-config 模块配置连接：

```yaml
jaravel:
  redis:
    connections:
      cache:
        host: 127.0.0.1
        port: 6379
        database: 1
```

## 使用示例

注册后，业务方可通过 `Cache::store("redis")` 使用 Redis 缓存，或将 `jaravel.cache.default-store` 设为 `redis` 使其成为默认 store。

```java
// 使用 redis store
Cache.store("redis").put("user:123", user, 3600);
Object cached = Cache.store("redis").get("user:123");

// 设为默认 store 后直接使用
// jaravel.cache.default-store: redis
Cache.put("user:123", user, 3600);
Object cached = Cache.get("user:123");
```

## 自动装配

`RedisCacheAutoConfiguration` 通过 `@AutoConfiguration` 注册，在 `CacheAutoConfiguration` 与 `RedisAutoConfiguration` 之后装配。当 `RedisManager` 与 `CacheManager` 均存在，且 `jaravel.cache.redis.auto-register` 为 true（默认）时生效。

创建的 bean：
- `RedisCacheDriver` — Redis 缓存驱动（`@ConditionalOnMissingBean`，便于业务方覆盖），使用 `cache` 命名连接
- `RedisCacheRegistrar` — 注册器，通过 `CacheManager.addStore("redis", ...)` 将 Redis 缓存驱动动态注册为 `redis` store，使用全局缓存前缀
