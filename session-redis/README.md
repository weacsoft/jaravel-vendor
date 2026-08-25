# session-redis

Redis Session 存储模块，提供 `RedisSessionStore`（实现 `SessionStore` 接口），对齐 Laravel 的 `RedisSessionHandler`。基于 `redis` 模块将 Session 数据存储在共享 Redis 中，实现多机 Session 同步——用户在任一节点登录后，其他节点可通过同一 Session ID 读取登录态。

本模块**不再是 Guard**，而是 `SessionStore` 实现。auth 模块的 `SessionGuard`（由 `SessionGuardDriver` 创建）通过 `SessionStore` 接口抽象存储后端。**Session 存储是全局配置，不与 Guard 绑定**：`RedisSessionStore` 通过 `@RegisterSessionStore(override = true)` 注册为全局唯一的 `SessionStore`（由 http 模块的 `SessionStoreRegistrar` 统一扫描保证唯一性），`SessionGuardDriver` 注入 http 提供的 `SessionStoreHolder` 用于创建所有 `session` 驱动的守卫。

> 本模块**不强依赖 auth**：只依赖 http 模块提供的 Session 功能（`SessionStore` 接口与 `@RegisterSessionStore` 注册机制）。当项目仅引入 core + auth（无 http 的 Session 功能）时，auth 退化为使用原生 Servlet HttpSession。

> **零 Spring 依赖**：Spring 自动装配收口于 **springboot** 模块（`vendor.springboot.sessionredis` 包）：`SessionRedisAutoConfiguration` / `SessionRedisProperties` / `OnRedisSessionDriverCondition` / `SessionRedisPublishAutoConfiguration`。本模块保留纯 Java 的 `RedisSessionStore` 与 `SessionRedisPublishableConfig`。

## 依赖

- `core` — 基础设施
- `http` — 提供 `SessionStore` 接口、`Request`（读取 Cookie、添加 Cookie）、`@RegisterSessionStore` 注册机制与默认 `CookieSessionStore`
- `redis` — 提供 `RedisManager` 命名连接管理
- `jakarta.servlet-api` — Cookie（provided）
- `jackson-databind` — Session 属性 JSON 序列化
- `slf4j-api` — 日志

## 核心接口

### RedisSessionStore

Redis Session 存储，实现 `SessionStore` 接口，对齐 Laravel `RedisSessionHandler`。将 Session 数据以 Hash 结构存储在 Redis 中，键格式为 `<prefix>:<sessionId>`，TTL 为 Session 生命周期。每次读写都会刷新 TTL，实现滑动过期。

本类注册为全局 `SessionStore` 后，由 http 的 `SessionStoreHolder` 注入到 `SessionGuardDriver` 用于创建所有 `session` 驱动的守卫。本类为**无状态单例**，通过 `RequestFactory` 获取当前请求上下文（读取 Cookie 中的 Session ID），无需在方法参数中传递 sessionId。

```java
public class RedisSessionStore implements SessionStore {
    public RedisSessionStore(RedisManager redisManager, String connectionName,
                             String prefix, long lifetimeMinutes, String cookieName);

    // SessionStore 接口实现
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

线程安全：本类为无状态单例，通过 `RequestFactory` 获取当前请求上下文。Redis 命令本身是原子的，多线程并发读写同一 Session 时通过 Redis 保证一致性。

### SessionRedisProperties（springboot 模块）

配置属性，前缀 `jaravel.session.redis`，对齐 Laravel `config/session.php`。该类位于 springboot 模块（`vendor.springboot.sessionredis.SessionRedisProperties`）。

```java
@ConfigurationProperties(prefix = "jaravel.session.redis")
public class SessionRedisProperties {
    private String connection = "session";        // Redis 连接名
    private String prefix = "laravel_session";    // Session 键前缀
    private long lifetime = 30;                    // Session 生命周期（分钟）
    private String cookie = "manage_session";     // Cookie 名称
    private Boolean autoRegister;                 // 装配覆盖开关，null=按 driver 自动判定
}
```

## 装配条件：安装 ≠ 启用（重要）

本模块遵循 vendor 模块组的统一原则：**把依赖放进 classpath 只表示"可用"，不表示"启用"**。
必须**显式选用** redis 作为 session 驱动，本模块才会注册与配置：

```yaml
jaravel:
  session:
    driver: redis      # ← 必须显式声明，否则本模块完全不装配
```

未选用时，本模块**不创建任何 Bean、不连接 Redis**，Session 回退到 http 模块的
`CookieSessionStore`。因此项目即便引入了本依赖，在**没有 Redis 的环境下也能正常启动**。

> **历史行为变更**：早期版本使用 `@ConditionalOnProperty(..., matchIfMissing = true)`，
> 只要依赖在 classpath 上就自动装配并注入 `RedisManager`，导致"装了但没启用"的项目
> 在无 Redis 时启动失败。现已修正为显式选用。

覆盖开关（优先级最高，用于特殊场景）：

| `jaravel.session.redis.auto-register` | 行为 |
|---|---|
| 不配置（默认） | 由 `jaravel.session.driver` 自动判定 |
| `true` | 强制启用 |
| `false` | 强制关闭 |

此外还叠加了 `@ConditionalOnBean(RedisManager.class)` 兜底：即便误开了开关，
只要 redis 模块本身没装配出 `RedisManager`，也不会因注入失败中断启动。

## 配置

```yaml
jaravel:
  session:
    driver: redis                # 启用本模块的开关
    redis:
      connection: session        # Redis 连接名，对应 jaravel.redis.connections.session
      prefix: laravel_session    # Session 键前缀
      lifetime: 30               # Session 生命周期（分钟）
      cookie: manage_session     # Cookie 名称
      # auto-register: true      # 可选，覆盖开关

  auth:
    guards:
      web:
        driver: session            # 使用 Session 守卫
        provider: users            # Session 存储由全局 SessionStore Bean 决定，无需在此指定
```

配合 redis 模块配置连接：

```yaml
jaravel:
  redis:
    connections:
      session:
        host: 127.0.0.1
        port: 6379
        database: 2
```

## 如何使用

启用 Redis Session 存储有两种方式：

**方式一：自动注册（推荐）**

引入本模块依赖 + 配置 `jaravel.session.driver: redis` 即可。
`SessionRedisAutoConfiguration` 会在 `RedisManager` 存在时，通过
`@RegisterSessionStore(override = true)` 自动将 `RedisSessionStore` 注册为全局
`SessionStore`（覆盖 http 默认的 `CookieSessionStore`）。业务方无需额外代码。

**方式二：手动注册**

如需关闭自动注册（`auto-register: false`）或自定义参数，在应用的 `config/SessionConfig.java` 中通过 `@RegisterSessionStore(override = true)` 注册即可：

```java
@Configuration
public class SessionConfig {
    @RegisterSessionStore(override = true)
    public SessionStore redisSessionStore(RedisManager redisManager, SessionRedisProperties props) {
        return new RedisSessionStore(redisManager, props.getConnection(), props.getPrefix(),
                props.getLifetime(), props.getCookie());
    }
}
```

**注册守卫**

无论采用哪种方式，业务方只需以 3 参数形式注册 `session` 驱动的守卫，无需指定存储后端（存储由全局 `SessionStore` Bean 决定）：

```java
authManager.registerGuard("web", "session", "users");
```

**业务代码**

业务代码无需感知 Redis，通过 Auth facade 操作：

```java
Auth.guard("web").login(user);          // 登录，写入 Redis Session

if (Auth.guard("web").check()) {        // 检查登录态（从 Redis Session 读取）
    Authenticatable user = Auth.guard("web").user();
}

Auth.guard("web").logout();             // 登出，销毁 Redis Session
```

## 自动装配

`SessionRedisAutoConfiguration`（springboot 模块 `vendor.springboot.sessionredis`）通过 `@AutoConfiguration` 注册，在 `RedisAutoConfiguration`（springboot `vendor.springboot.redis`）与 http 的 `HttpSessionAutoConfiguration` 之后装配。当 `RedisManager` 存在（`@ConditionalOnClass(RedisManager.class)`）、为 Servlet Web 应用、且 `jaravel.session.redis.auto-register` 为 true（默认）时生效，**与 AuthManager 是否存在无关**。

创建的 bean：
- `RedisSessionStore` — Redis Session 存储，实现 `SessionStore` 接口，通过 `@RegisterSessionStore(override = true)` 注册为全局唯一的 `SessionStore`（由 http 的 `SessionStoreRegistrar` 统一扫描保证唯一性，便于业务方覆盖）。注册后，http 的 `SessionStoreHolder` 持有该实例，auth 模块的 `SessionGuardDriver` 注入该 Holder，所有 `session` 驱动的守卫都将使用 `RedisSessionStore` 作为存储后端。
