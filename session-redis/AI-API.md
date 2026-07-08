# session-redis AI-API Reference

> Module: `session-redis` | Package: `com.weacsoft.jaravel.vendor.session.redis` | Version: 0.1.1

## Overview

session-redis 模块提供了基于 Redis 的会话存储与认证守卫实现。`RedisSessionStore` 将会话数据以 JSON 序列化存储到 Redis，支持 TTL 过期和多实例共享；`RedisSessionGuard` 继承自框架的 `SessionGuard`，在认证成功后将会话写入 Redis、在请求开始时从 Redis 恢复会话。该模块在 Spring Boot 自动装配时将 Redis 会话存储注册到 `SessionManager`，替代默认的内存会话存储，适用于多机部署场景。

## Classes & Interfaces

### RedisSessionStore
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.session.redis`
- **Implements**: `com.weacsoft.jaravel.vendor.session.SessionStore`
- **Description**: Redis 会话存储实现。会话数据通过 Jackson `ObjectMapper` 序列化为 JSON 存储到 Redis，键格式为 `{prefix}:{sessionId}`。支持 TTL 过期，`allSessions()` 使用 SCAN 遍历键空间。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `RedisSessionStore` | `RedisManager redisManager, String connectionName, String prefix, long defaultTtlSeconds` | - | 构造 Redis 会话存储，指定连接名、键前缀和默认 TTL |
| `RedisSessionStore` | `RedisManager redisManager` | - | 使用默认参数构造（prefix=`session:`, ttl=3600） |
| `save` | `String sessionId, Map<String, Object> data, long ttlSeconds` | `void` | 保存会话数据到 Redis，TTL > 0 使用 SETEX |
| `load` | `String sessionId` | `Map<String, Object>` | 加载会话数据，反序列化 JSON，不存在返回 null |
| `destroy` | `String sessionId` | `void` | 销毁指定会话（DEL 键） |
| `exists` | `String sessionId` | `boolean` | 检查会话是否存在 |
| `renew` | `String sessionId, long ttlSeconds` | `void` | 续期会话（EXPIRE） |
| `allSessions` | - | `Collection<String>` | 返回所有会话 ID（SCAN 遍历） |

#### Usage Example
```java
// 直接使用 RedisSessionStore
RedisSessionStore store = new RedisSessionStore(redisManager, "session", "session:", 3600);
store.save("sess-abc123", sessionData, 3600);
Map<String, Object> data = store.load("sess-abc123");
store.renew("sess-abc123", 7200);  // 续期 2 小时
store.destroy("sess-abc123");
```

### RedisSessionGuard
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.session.redis`
- **Extends**: `com.weacsoft.jaravel.vendor.session.SessionGuard`
- **Description**: Redis 会话认证守卫。继承 `SessionGuard`，在 `attempt` 认证成功后将会话数据写入 Redis 存储；在 `user()` 请求处理时从 Redis 恢复会话。构造时接收 `RedisSessionStore` 替代默认内存存储。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `RedisSessionGuard` | `String name, UserProvider provider, RedisSessionStore sessionStore` | - | 构造 Redis 会话守卫 |
| `attempt` | `Map<String, String> credentials, boolean remember` | `boolean` | 尝试认证，成功后将会话写入 Redis |
| `user` | - | `Object` | 从 Redis 恢复当前会话用户，不存在返回 null |
| `logout` | - | `void` | 登出，销毁 Redis 中的会话 |
| `getSessionId` | - | `String` | 获取当前会话 ID |
| `getSessionStore` | - | `RedisSessionStore` | 获取底层 Redis 会话存储实例 |

#### Usage Example
```java
// 通过 Auth facade 使用（自动装配后）
boolean ok = Auth.guard("redis").attempt(Map.of("email", "a@b.com", "password", "secret"), true);
Object user = Auth.guard("redis").user();
Auth.guard("redis").logout();
```

### SessionRedisProperties
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.session.redis`
- **Annotations**: `@ConfigurationProperties(prefix = "jaravel.session.redis")`
- **Description**: Redis 会话配置属性，前缀 `jaravel.session.redis`。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `getConnection` | - | `String` | 获取 Redis 连接名，默认 `session` |
| `setConnection` | `String connection` | `void` | 设置 Redis 连接名 |
| `getPrefix` | - | `String` | 获取会话键前缀，默认 `session:` |
| `setPrefix` | `String prefix` | `void` | 设置会话键前缀 |
| `getTtl` | - | `long` | 获取会话 TTL（秒），默认 3600 |
| `setTtl` | `long ttl` | `void` | 设置会话 TTL |
| `isAutoRegister` | - | `boolean` | 是否自动注册到 SessionManager，默认 true |
| `setAutoRegister` | `boolean autoRegister` | `void` | 设置是否自动注册 |

#### Usage Example
```yaml
# application.yml
jaravel:
  session:
    redis:
      connection: session
      prefix: "session:"
      ttl: 7200
      auto-register: true
```

### SessionRedisAutoConfiguration
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.session.redis`
- **Annotations**: `@AutoConfiguration`, `@AutoConfigureAfter({SessionAutoConfiguration, RedisAutoConfiguration})`, `@ConditionalOnClass({RedisSessionStore, SessionManager, RedisManager})`, `@ConditionalOnBean({RedisManager, SessionManager})`, `@ConditionalOnProperty(prefix = "jaravel.session.redis", name = "auto-register", havingValue = "true", matchIfMissing = true)`
- **Description**: Redis 会话自动装配。当 `RedisManager` 和 `SessionManager` 均存在时，创建 `RedisSessionStore` 并注册到 `SessionManager`。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `redisSessionStore` | `RedisManager redisManager, SessionRedisProperties properties` | `RedisSessionStore` | 创建 Redis 会话存储 Bean（`@Bean`, `@ConditionalOnMissingBean`） |
| `redisSessionRegistrar` | `SessionManager sessionManager, RedisSessionStore redisSessionStore` | `RedisSessionRegistrar` | 将 Redis 会话存储注册到 SessionManager（`@Bean`） |

#### Usage Example
```java
// 自动装配后，通过 Session facade 使用 Redis 会话
Session::store("redis").put("user_id", 123);
Object userId = Session::store("redis").get("user_id");
```
