# session-redis

Redis Session 守卫模块，对齐 Laravel 的 `SessionGuard`（Session 后端为 Redis）。基于 `redis-config` 模块将 Session 数据存储在共享 Redis 中，实现多机 Session 同步——用户在任一节点登录后，其他节点可通过同一 Session ID 读取登录态。

## 依赖

- `core` — 基础设施
- `http` — 提供 `Request`（读取 Cookie、添加 Cookie）
- `auth` — 提供 `AuthGuard` 接口、`AuthManager`、`AuthContext`、`UserProvider`
- `redis-config` — 提供 `RedisManager` 命名连接管理
- `spring-boot-autoconfigure` — 自动装配
- `jakarta.servlet-api` — Cookie（provided）
- `jackson-databind` — Session 属性 JSON 序列化
- `slf4j-api` — 日志

## 核心接口

### RedisSessionGuard

Redis Session 守卫，实现 `AuthGuard` 接口，对齐 Laravel 的 `SessionGuard`，但 Session 后端为 Redis。登录态写入 Redis Session（通过 `RedisSessionStore`），用户信息按需通过 `UserProvider` 取出并缓存于当前线程。

```java
public class RedisSessionGuard implements AuthGuard {
    public RedisSessionGuard(String name, UserProvider provider, RedisSessionStore sessionStore);

    // AuthGuard 接口实现
    public boolean check();                  // 是否已登录
    public boolean guest();                  // 是否未登录
    public Authenticatable user();            // 获取当前用户（请求级缓存）
    public void login(Authenticatable user);  // 登录，写入 Redis Session 并通过 Cookie 返回 Session ID
    public void logout();                     // 登出，销毁 Redis Session 登录态
    public String token();                    // 返回 null（Session 守卫不产生 token，仅 JWT 守卫支持）
}
```

Session ID 流转：
1. 请求到达时，从 Cookie 中读取 Session ID（Cookie 名由 `RedisSessionStore.getCookieName()` 指定）
2. 若 Cookie 中无 Session ID，则不创建新 Session（惰性创建，仅在 login 时生成）
3. login 时生成新 Session ID，写入 Redis，并通过 Cookie 返回给客户端
4. logout 时销毁 Redis 中的 Session 数据

线程安全：本守卫实例由 `AuthManager` 通过 ThreadLocal 按请求隔离，`cachedUser`、`resolved`、`sessionId` 为请求级状态，不跨请求共享。

### RedisSessionStore

Redis Session 存储，对齐 Laravel `RedisSessionHandler`。将 Session 数据以 Hash 结构存储在 Redis 中，键格式为 `<prefix>:<sessionId>`，TTL 为 Session 生命周期。每次读写都会刷新 TTL，实现滑动过期。

```java
public class RedisSessionStore {
    public RedisSessionStore(RedisManager redisManager, String connectionName,
                             String prefix, long lifetimeMinutes, String cookieName);

    public String generateSessionId();                          // 生成新的 UUID Session ID
    public Map<String, Object> getAll(String sessionId);        // 读取所有属性（刷新 TTL）
    public Object get(String sessionId, String key);            // 读取单个属性（刷新 TTL）
    public void put(String sessionId, String key, Object value); // 写入属性（刷新 TTL）
    public void remove(String sessionId, String key);           // 移除属性（刷新 TTL）
    public void destroy(String sessionId);                      // 销毁整个 Session
    public boolean exists(String sessionId);                    // 检查 Session 是否存在
    public String getCookieName();                              // Cookie 名称
    public long getLifetimeSeconds();                            // Session 生命周期（秒）
}
```

存储格式（Redis Hash）：

```
HSET <prefix>:<sessionId> login_web_id "12345" login_wechat_id "67890"
EXPIRE <prefix>:<sessionId> 1800
```

### SessionRedisProperties

配置属性，前缀 `jaravel.session.redis`，对齐 Laravel `config/session.php`。

```java
@ConfigurationProperties(prefix = "jaravel.session.redis")
public class SessionRedisProperties {
    private String connection = "session";        // Redis 连接名
    private String prefix = "laravel_session";    // Session 键前缀
    private long lifetime = 30;                    // Session 生命周期（分钟）
    private String cookie = "manage_session";     // Cookie 名称
    private boolean autoRegister = true;          // 是否自动注册到 AuthManager
}
```

## 配置

```yaml
jaravel:
  session:
    redis:
      connection: session          # Redis 连接名，对应 jaravel.redis.connections.session
      prefix: laravel_session      # Session 键前缀
      lifetime: 30                 # Session 生命周期（分钟）
      cookie: manage_session       # Cookie 名称
      auto-register: true          # 是否自动注册 redis-session guard 驱动到 AuthManager

  auth:
    guards:
      web:
        driver: redis-session      # 使用 Redis Session 守卫
        provider: users
```

配合 redis-config 模块配置连接：

```yaml
jaravel:
  redis:
    connections:
      session:
        host: 127.0.0.1
        port: 6379
        database: 2
```

## 使用示例

注册后，业务方在 auth 配置中将 guard driver 设为 `redis-session` 即可启用 Redis Session：

```java
// 业务代码无需感知 Redis，通过 Auth facade 操作
Auth.guard("web").login(user);          // 登录，写入 Redis Session

if (Auth.guard("web").check()) {        // 检查登录态（从 Redis Session 读取）
    Authenticatable user = Auth.guard("web").user();
}

Auth.guard("web").logout();             // 登出，销毁 Redis Session
```

## 自动装配

`SessionRedisAutoConfiguration` 通过 `@AutoConfiguration` 注册，在 `RedisAutoConfiguration` 与 `AuthAutoConfiguration` 之后装配。当 `RedisManager` 与 `AuthManager` 均存在，且 `jaravel.session.redis.auto-register` 为 true（默认）时生效。

创建的 bean：
- `RedisSessionStore` — Redis Session 存储（`@ConditionalOnMissingBean`，便于业务方覆盖）
- `RedisSessionGuardRegistrar` — 注册器，通过 `AuthManager.registerGuardDriver("redis-session", ...)` 将 `redis-session` guard 驱动注册到 `AuthManager`。注册后，guard 配置中 `driver=redis-session` 的守卫将使用 `RedisSessionGuard`。
