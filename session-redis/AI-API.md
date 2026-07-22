# session-redis AI-API Reference

> Module: `session-redis` | Package: `com.weacsoft.jaravel.vendor.session.redis` | Version: 0.1.2

## Overview

session-redis 模块是 Redis Session 守卫插件，对齐 Laravel 的 `SessionGuard`（Session 后端为 Redis）。`RedisSessionStore` 将 Session 数据以 Hash 结构存储在共享 Redis 中，键格式为 `<prefix>:<sessionId>`，每个属性为一个 Hash field，TTL 为 Session 生命周期（分钟级）；`RedisSessionGuard` 实现 `AuthGuard` 接口，登录态写入 Redis Session，用户信息按需通过 `UserProvider` 取出并缓存于当前线程。

由于所有应用实例共享同一 Redis，用户在任一节点登录后，其他节点可通过同一 Session ID（从 Cookie 获取）读取登录态，天然实现多机 Session 同步。Session ID 通过 Cookie 传递，Cookie 名由配置指定（默认 `manage_session`）。每次读写删除都会刷新 TTL，实现滑动过期。

该模块在 Spring Boot 自动装配时，通过 `AuthManager.registerGuardDriver("redis-session", ...)` 将 `redis-session` guard 驱动注册到 `AuthManager`。业务方在 auth 配置中将 guard driver 设为 `redis-session` 即可启用 Redis Session，适用于多机部署场景。

## Classes & Interfaces

### RedisSessionGuard
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.session.redis`
- **Implements**: `com.weacsoft.jaravel.vendor.auth.contract.AuthGuard`
- **Description**: Redis Session 守卫，对齐 Laravel 的 `SessionGuard`，但 Session 后端为 Redis。登录态写入 Redis Session（通过 `RedisSessionStore`），用户信息按需通过 `UserProvider` 取出并缓存于当前线程。Session ID 从当前请求的 Cookie 中读取（Cookie 名由 `RedisSessionStore.getCookieName()` 指定）。守卫实例由 `AuthManager` 通过 ThreadLocal 按请求隔离，`cachedUser`、`resolved`、`sessionId` 为请求级状态，不跨请求共享。

#### Session ID 流转
1. 请求到达时，从 Cookie 中读取 Session ID（Cookie 名由 `RedisSessionStore.getCookieName()` 指定）；
2. 若 Cookie 中无 Session ID，则不创建新 Session（惰性创建，仅在 `login` 时生成）；
3. `login` 时生成新 Session ID，写入 Redis，并通过 Cookie 返回给客户端；
4. `logout` 时移除 Redis 中该 Session 的登录态属性。

#### 多机同步
由于 Session 数据存储在共享的 Redis 中，用户在任一节点登录后，其他节点可通过同一 Session ID（从 Cookie 获取）读取登录态，实现多机 Session 同步。

#### Session 属性键
对齐 Laravel 的 `login_<guard>_id` 约定，守卫名为 `name` 时，登录用户主键存储在 Session 的 `login_<name>_id` 属性中。例如守卫名 `web` 对应属性键 `login_web_id`，守卫名 `wechat` 对应 `login_wechat_id`。同一 Session（同一 Cookie）可同时承载多个守卫的登录态。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `RedisSessionGuard` | `String name, UserProvider provider, RedisSessionStore sessionStore` | 构造方法 | 创建 Redis Session 守卫。`name` 为守卫名称（如 web / wechat / admin），`provider` 为用户提供者，`sessionStore` 为 Redis Session 存储 |
| `check` | 无 | `boolean` | 是否已登录（等价于 `user() != null`） |
| `guest` | 无 | `boolean` | 是否未登录（`!check()`） |
| `user` | 无 | `Authenticatable` | 获取当前用户（请求级缓存）。从 Cookie 取 Session ID，再从 Redis Session 读取 `login_<name>_id`，通过 `UserProvider.retrieveById` 取出用户；不存在返回 null |
| `login` | `Authenticatable user` | `void` | 登录。缓存用户，生成（或复用）Session ID，将用户主键写入 Redis Session，并通过 Cookie 返回 Session ID 给客户端 |
| `logout` | 无 | `void` | 登出。清理缓存的用户，从 Redis Session 中移除 `login_<name>_id` 属性 |
| `token` | 无 | `String` | 返回 null（Session 守卫不产生 token，仅 JWT 守卫支持） |

#### Constructor Details

##### `RedisSessionGuard(String name, UserProvider provider, RedisSessionStore sessionStore)`

| Parameter | Description |
|-----------|-------------|
| `name` | 守卫名称（如 web / wechat / admin），同时决定 Session 属性键 `login_<name>_id` |
| `provider` | 用户提供者，用于通过主键 `retrieveById` 取出用户 |
| `sessionStore` | Redis Session 存储，提供 Session 读写与 Cookie 名 |

#### `user()` Method Details

`user()` 解析当前请求的用户，带请求级缓存（首次解析后缓存，后续直接返回）：

```
user()
  ├── resolved 已解析过？ → 返回 cachedUser（可能为 null）
  └── 首次解析（resolved = true）
        ├── sid = getSessionId()
        │     ├── sessionId 已缓存 → 返回缓存值
        │     └── 从 AuthContext 当前请求的 Cookie 读取（cookieName）
        │           └── cookie 值非空 → sessionId = cookie 值
        ├── sid == null → 返回 null
        ├── id = sessionStore.get(sid, "login_" + name + "_id")
        ├── id == null → 返回 null
        ├── cachedUser = provider.retrieveById(id)
        └── 返回 cachedUser
```

#### `login(Authenticatable user)` Method Details

```
login(user)
  ├── cachedUser = user; resolved = true
  ├── sid = getSessionId()
  │     └── sid == null 或 sessionStore.exists(sid) == false
  │           → sid = sessionStore.generateSessionId()  // 生成新 UUID
  │             sessionId = sid
  ├── sessionStore.put(sid, "login_" + name + "_id", user.getAuthIdentifier())
  │     // 写入 Redis Hash 并刷新 TTL
  └── 通过 Cookie 返回 Session ID
        ├── req = AuthContext.get()
        └── req != null？
              ├── cookie = new Cookie(cookieName, sid)
              ├── cookie.setPath("/")
              ├── cookie.setHttpOnly(true)
              ├── cookie.setMaxAge(sessionStore.getLifetimeSeconds())
              └── req.addCookie(cookie)
```

#### `logout()` Method Details

```
logout()
  ├── cachedUser = null; resolved = true
  ├── sid = getSessionId()
  └── sid != null？
        └── sessionStore.remove(sid, "login_" + name + "_id")
              // 从 Redis Hash 删除该属性并刷新 TTL
```

#### Usage Example
```java
// 通过 Auth 门面使用（自动装配后，guard driver 设为 redis-session）
Auth.guard("web").login(user);          // 登录，写入 Redis Session，Cookie 返回 Session ID

if (Auth.guard("web").check()) {        // 检查登录态（从 Redis Session 读取）
    Authenticatable user = Auth.guard("web").user();
}

Auth.guard("web").logout();             // 登出，移除 Redis Session 登录态属性

// 多守卫共享同一 Session：同一 Cookie 可同时承载多个守卫登录态
Auth.guard("web").login(webUser);
Auth.guard("wechat").login(wechatUser);  // 写入 login_wechat_id，不影响 login_web_id
```

---

### RedisSessionStore
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.session.redis`
- **Implements**: 无（独立类，不实现任何接口）
- **Description**: Redis Session 存储，对齐 Laravel `RedisSessionHandler`。将 Session 数据以 Hash 结构存储在 Redis 中，键格式为 `<prefix>:<sessionId>`，每个属性为一个 Hash field，TTL 为 Session 生命周期（秒级，由配置的分钟数 × 60 得到）。所有应用实例共享同一 Redis，天然实现多机 Session 同步。每次读、写、删除都会刷新 TTL，实现滑动过期。Session 属性值通过 Jackson `ObjectMapper` 序列化为 JSON 存储，读取时反序列化为 Java 对象。Redis 命令本身是原子的，多线程并发读写同一 Session 时通过 Redis 保证一致性。

#### Session ID 管理
- Session ID 通过 Cookie 传递（Cookie 名由配置指定，默认 `manage_session`）；
- `generateSessionId()` 生成新的 UUID（去除中划线）作为 Session ID；
- 每次读写删除都会刷新 TTL，实现滑动过期。

#### 存储格式
Session 数据以 Redis Hash 存储，每个属性为一个 Hash field，值为 JSON 字符串：

```
HSET <prefix>:<sessionId> login_web_id "12345" login_wechat_id "67890"
EXPIRE <prefix>:<sessionId> 1800     # 30 分钟 = 1800 秒
```

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `RedisSessionStore` | `RedisManager redisManager, String connectionName, String prefix, long lifetimeMinutes, String cookieName` | 构造方法 | 创建 Redis Session 存储。`connectionName` 为 Redis 连接名（null 使用默认连接），`prefix` 为 Session 键前缀，`lifetimeMinutes` 为生命周期（分钟，内部 ×60 转秒），`cookieName` 为 Cookie 名称 |
| `generateSessionId` | 无 | `String` | 生成新的 Session ID（UUID 去除中划线） |
| `getAll` | `String sessionId` | `Map<String, Object>` | 读取 Session 中的所有属性（`HGETALL`），反序列化为 Java 对象，并刷新 TTL；`sessionId` 为 null/空返回空 Map |
| `get` | `String sessionId, String key` | `Object` | 读取 Session 中的单个属性（`HGET`），反序列化，刷新 TTL；不存在返回 null |
| `put` | `String sessionId, String key, Object value` | `void` | 写入 Session 属性（`HSET`，值序列化为 JSON），刷新 TTL |
| `remove` | `String sessionId, String key` | `void` | 移除 Session 属性（`HDEL`），刷新 TTL |
| `destroy` | `String sessionId` | `void` | 销毁整个 Session（`DEL` 键） |
| `exists` | `String sessionId` | `boolean` | 检查 Session 是否存在且未过期（`EXISTS` > 0） |
| `getCookieName` | 无 | `String` | 获取 Cookie 名称 |
| `getLifetimeSeconds` | 无 | `long` | 获取 Session 生命周期（秒） |

#### Constructor Details

##### `RedisSessionStore(RedisManager redisManager, String connectionName, String prefix, long lifetimeMinutes, String cookieName)`

| Parameter | Description |
|-----------|-------------|
| `redisManager` | Redis 管理器，用于获取命名连接的同步命令接口 |
| `connectionName` | Redis 连接名（如 `session`），对应 `jaravel.redis.connections` 中的配置；null 使用默认连接 |
| `prefix` | Session 键前缀（如 `laravel_session`），最终键为 `<prefix>:<sessionId>` |
| `lifetimeMinutes` | Session 生命周期（分钟），内部转换为秒（`lifetimeMinutes * 60`） |
| `cookieName` | Cookie 名称，用于传递 Session ID（如 `manage_session`） |

#### Method Details

##### 滑动过期（TTL 刷新）
`getAll`、`get`、`put`、`remove` 四个方法在执行各自的主操作后，均会调用 `EXPIRE <prefix>:<sessionId> <lifetimeSeconds>` 刷新 TTL，实现滑动过期。`destroy` 仅删除键，`exists` 仅检查存在性，均不刷新 TTL。所有方法对 `sessionId` 为 null 或空做防御性处理，对异常进行捕获并记录日志（`getAll`/`get` 返回空值，`put`/`remove`/`destroy` 静默返回，`exists` 返回 false）。

##### 序列化
- `serialize(Object value)`：通过 `ObjectMapper.writeValueAsString` 序列化为 JSON；失败时回退为 `value.toString()`（null 回退为 `"null"`）。
- `deserialize(String json)`：通过 `ObjectMapper.readValue` 反序列化为 Java 对象；失败时原样返回字符串。

#### Usage Example
```java
// 直接使用 RedisSessionStore
RedisSessionStore store = new RedisSessionStore(
        redisManager, "session", "laravel_session", 30, "manage_session");

String sessionId = store.generateSessionId();        // 生成新 Session ID
store.put(sessionId, "login_web_id", 12345);          // 写入属性（刷新 TTL）
Object userId = store.get(sessionId, "login_web_id"); // 读取属性（刷新 TTL）
Map<String, Object> all = store.getAll(sessionId);    // 读取所有属性（刷新 TTL）
store.remove(sessionId, "login_web_id");              // 移除属性（刷新 TTL）
boolean exists = store.exists(sessionId);             // 检查是否存在
store.destroy(sessionId);                             // 销毁整个 Session

// Cookie 名与生命周期
String cookieName = store.getCookieName();            // "manage_session"
long lifetimeSeconds = store.getLifetimeSeconds();    // 1800
```

---

### SessionRedisProperties
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.session.redis`
- **Annotations**: `@ConfigurationProperties(prefix = "jaravel.session.redis")`
- **Description**: Redis Session 配置属性，前缀 `jaravel.session.redis`，对齐 Laravel `config/session.php`。

#### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `connection` | `String` | `"session"` | Redis 连接名，对应 `jaravel.redis.connections` 中的配置 |
| `prefix` | `String` | `"laravel_session"` | Session 键前缀，最终键为 `<prefix>:<sessionId>` |
| `lifetime` | `long` | `30` | Session 生命周期（分钟），内部 ×60 转秒 |
| `cookie` | `String` | `"manage_session"` | Cookie 名称，用于传递 Session ID |
| `autoRegister` | `boolean` | `true` | 是否自动注册 `redis-session` guard 驱动到 `AuthManager` |

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `getConnection` / `setConnection` | `String` | `String` / `void` | Redis 连接名，默认 `session` |
| `getPrefix` / `setPrefix` | `String` | `String` / `void` | Session 键前缀，默认 `laravel_session` |
| `getLifetime` / `setLifetime` | `long` | `long` / `void` | Session 生命周期（分钟），默认 30 |
| `getCookie` / `setCookie` | `String` | `String` / `void` | Cookie 名称，默认 `manage_session` |
| `isAutoRegister` / `setAutoRegister` | `boolean` | `boolean` / `void` | 是否自动注册到 AuthManager，默认 true |

#### Usage Example
```yaml
# application.yml
jaravel:
  session:
    redis:
      connection: session            # Redis 连接名，对应 jaravel.redis.connections.session
      prefix: laravel_session        # Session 键前缀
      lifetime: 30                   # Session 生命周期（分钟）
      cookie: manage_session         # Cookie 名称
      auto-register: true            # 是否自动注册 redis-session guard 驱动到 AuthManager

  auth:
    guards:
      web:
        driver: redis-session        # 使用 Redis Session 守卫
        provider: users

  # 配合 redis-config 模块配置连接
  redis:
    connections:
      session:
        host: 127.0.0.1
        port: 6379
        database: 2
```

---

### SessionRedisAutoConfiguration
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.session.redis`
- **Annotations**: `@AutoConfiguration`, `@AutoConfigureAfter({RedisAutoConfiguration, AuthAutoConfiguration})`, `@ConditionalOnClass({RedisSessionStore, AuthManager, RedisManager})`, `@ConditionalOnBean({RedisManager, AuthManager})`, `@ConditionalOnProperty(prefix = "jaravel.session.redis", name = "auto-register", havingValue = "true", matchIfMissing = true)`, `@EnableConfigurationProperties(SessionRedisProperties.class)`
- **Description**: Redis Session 自动装配。在 `RedisAutoConfiguration` 与 `AuthAutoConfiguration` 之后装配。当 `RedisManager` 与 `AuthManager` 均存在，且 `jaravel.session.redis.auto-register` 为 true（默认）时生效。创建 `RedisSessionStore` Bean，并通过 `RedisSessionGuardRegistrar` 将 `redis-session` guard 驱动注册到 `AuthManager`。

#### 装配条件
| 条件 | 说明 |
|------|------|
| `@AutoConfigureAfter` | 在 `RedisAutoConfiguration`、`AuthAutoConfiguration` 之后装配，确保 `RedisManager`、`AuthManager` 就绪 |
| `@ConditionalOnClass` | 类路径存在 `RedisSessionStore`、`AuthManager`、`RedisManager` |
| `@ConditionalOnBean` | 容器中存在 `RedisManager`、`AuthManager` Bean |
| `@ConditionalOnProperty` | `jaravel.session.redis.auto-register=true`（默认开启，`matchIfMissing=true`） |

#### Bean Methods

| Bean | Parameters | Return | Description |
|------|-----------|--------|-------------|
| `redisSessionStore` | `RedisManager redisManager, SessionRedisProperties properties` | `RedisSessionStore` | 创建 Redis Session 存储 Bean（`@Bean`, `@ConditionalOnMissingBean`，便于业务方覆盖） |
| `redisSessionGuardRegistrar` | `AuthManager authManager, RedisSessionStore sessionStore` | `RedisSessionGuardRegistrar` | 创建注册器，构造时将 `redis-session` guard 驱动注册到 `AuthManager`（`@Bean`） |

#### Bean Details

##### `redisSessionStore(RedisManager redisManager, SessionRedisProperties properties)`
以 `@ConditionalOnMissingBean` 暴露，便于业务方自定义覆盖。从 properties 读取连接名、前缀、生命周期（分钟）、Cookie 名构造 `RedisSessionStore`：

```java
return new RedisSessionStore(
        redisManager,
        properties.getConnection(),
        properties.getPrefix(),
        properties.getLifetime(),
        properties.getCookie()
);
```

##### `redisSessionGuardRegistrar(AuthManager authManager, RedisSessionStore sessionStore)`
创建 `RedisSessionGuardRegistrar`，其构造方法中调用 `AuthManager.registerGuardDriver("redis-session", ...)` 完成驱动注册。注册后，guard 配置中 `driver=redis-session` 的守卫将使用 `RedisSessionGuard`。

#### Usage Example
```java
// 自动装配后，业务方在 auth 配置中将 guard driver 设为 redis-session 即可启用 Redis Session。
// 业务代码无需感知 Redis，通过 Auth 门面操作：
Auth.guard("web").login(user);          // 登录，写入 Redis Session
Auth.guard("web").check();              // 检查登录态（从 Redis Session 读取）
Auth.guard("web").user();               // 获取当前用户
Auth.guard("web").logout();             // 登出，销毁 Redis Session 登录态
```

---

### SessionRedisAutoConfiguration.RedisSessionGuardRegistrar
- **Type**: class (static inner)
- **Package**: `com.weacsoft.jaravel.vendor.session.redis`
- **Description**: 注册器（`SessionRedisAutoConfiguration` 的静态内部类），将 `redis-session` guard 驱动注册到 `AuthManager`。构造时调用 `authManager.registerGuardDriver("redis-session", (name, provider, config) -> new RedisSessionGuard(name, provider, sessionStore))`，使得 guard 配置中 `driver=redis-session` 的守卫使用 `RedisSessionGuard`，并共享同一个 `RedisSessionStore` 实例。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `RedisSessionGuardRegistrar` | `AuthManager authManager, RedisSessionStore sessionStore` | 构造方法 | 构造时将 `redis-session` guard 驱动注册到 `AuthManager`。驱动工厂以传入的 `sessionStore` 构造 `RedisSessionGuard`，所有 `redis-session` 守卫共享同一 `RedisSessionStore` |

#### Registration Details

构造时执行的注册逻辑：

```java
authManager.registerGuardDriver("redis-session",
        (name, provider, config) -> new RedisSessionGuard(name, provider, sessionStore));
```

| Parameter | Description |
|-----------|-------------|
| `authManager` | 认证管理器，提供 `registerGuardDriver` 注册守卫驱动工厂 |
| `sessionStore` | Redis Session 存储，被所有 `redis-session` 守卫共享 |

注册后，`AuthManager` 在解析 `driver=redis-session` 的 guard 配置时，会调用该工厂，以 guard 名称 `name`、用户提供者 `provider` 与共享的 `sessionStore` 构造 `RedisSessionGuard` 实例。
