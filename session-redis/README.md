# session-redis

Redis Session 存储模块，提供 `RedisSessionStore`（实现 `SessionStore` 接口），对齐 Laravel 的 `RedisSessionHandler`。基于 `redis-config` 模块将 Session 数据存储在共享 Redis 中，实现多机 Session 同步——用户在任一节点登录后，其他节点可通过同一 Session ID 读取登录态。

本模块**不再是 Guard**，而是 `SessionStore` 实现。auth 模块的 `SessionGuard`（由 `SessionGuardDriver` 创建）通过 `SessionStore` 接口抽象存储后端，`RedisSessionStore` 注册为 Spring Bean 后由 `SessionGuardDriver` 自动收集，在创建 `SessionGuard` 时通过 `support("redis")` 匹配。

## 依赖

- `core` — 基础设施
- `http` — 提供 `Request`（读取 Cookie、添加 Cookie）
- `auth` — 提供 `SessionStore` 接口、`AuthManager`、`AuthContext`、`SessionGuard`、`SessionGuardDriver`
- `redis-config` — 提供 `RedisManager` 命名连接管理
- `spring-boot-autoconfigure` — 自动装配
- `jakarta.servlet-api` — Cookie（provided）
- `jackson-databind` — Session 属性 JSON 序列化
- `slf4j-api` — 日志

## 核心接口

### RedisSessionStore

Redis Session 存储，实现 `SessionStore` 接口，对齐 Laravel `RedisSessionHandler`。将 Session 数据以 Hash 结构存储在 Redis 中，键格式为 `<prefix>:<sessionId>`，TTL 为 Session 生命周期。每次读写都会刷新 TTL，实现滑动过期。

`support("redis")` 返回 `true`，由 `SessionGuardDriver` 自动收集后在创建 `SessionGuard` 时按 `session-store` 配置匹配。本类为**无状态单例**，通过 `AuthContext` 获取当前请求上下文（读取 Cookie 中的 Session ID），无需在方法参数中传递 sessionId。

```java
public class RedisSessionStore implements SessionStore {
    public RedisSessionStore(RedisManager redisManager, String connectionName,
                             String prefix, long lifetimeMinutes, String cookieName);

    // SessionStore 接口实现
    @Override
    public boolean support(String store);        // "redis" 返回 true
    @Override
    public Object get(String key);               // 从当前 Session 读取属性（刷新 TTL）
    @Override
    public void put(String key, Object value);   // 写入属性（刷新 TTL，无 Session 时惰性创建并写 Cookie）
    @Override
    public void remove(String key);              // 移除属性（刷新 TTL）
    @Override
    public void destroy();                       // 销毁当前 Session
}
```

Session ID 流转（由 `RedisSessionStore` 内部管理，业务层无感）：
1. 请求到达时，从 Cookie 中读取 Session ID（Cookie 名由配置 `jaravel.session.redis.cookie` 指定）
2. 若 Cookie 中无 Session ID，则不创建新 Session（惰性创建，仅在 `put` 时生成）
3. `put` 时若 Session 不存在则生成新 Session ID，写入 Redis，并通过 Cookie 返回给客户端
4. `destroy` 时销毁 Redis 中的 Session 数据

存储格式（Redis Hash）：

```
HSET <prefix>:<sessionId> login_web_id "12345" login_wechat_id "67890"
EXPIRE <prefix>:<sessionId> 1800
```

线程安全：本类为无状态单例，通过 `AuthContext` 获取当前请求上下文。Redis 命令本身是原子的，多线程并发读写同一 Session 时通过 Redis 保证一致性。

### SessionRedisProperties

配置属性，前缀 `jaravel.session.redis`，对齐 Laravel `config/session.php`。

```java
@ConfigurationProperties(prefix = "jaravel.session.redis")
public class SessionRedisProperties {
    private String connection = "session";        // Redis 连接名
    private String prefix = "laravel_session";    // Session 键前缀
    private long lifetime = 30;                    // Session 生命周期（分钟）
    private String cookie = "manage_session";     // Cookie 名称
    private boolean autoRegister = true;          // 是否自动注册 RedisSessionStore 为 SessionStore Bean
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
      auto-register: true          # 是否自动注册 RedisSessionStore 为 SessionStore Bean

  auth:
    guards:
      web:
        driver: session            # 使用 Session 守卫
        provider: users
        session-store: redis       # Session 存储后端使用 Redis
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

注册后，业务方在 auth 配置中将 guard 的 `session-store` 设为 `redis`（driver 仍为 `session`）即可启用 Redis Session：

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
- `RedisSessionStore` — Redis Session 存储，实现 `SessionStore` 接口，`support("redis")` 返回 `true`（以 `@ConditionalOnMissingBean` 暴露，便于业务方覆盖）。注册为 `SessionStore` 类型后，由 auth 模块的 `SessionGuardDriver` 自动收集；guard 配置中 `driver=session` 且 `session-store=redis` 的守卫将使用 `RedisSessionStore` 作为存储后端。
