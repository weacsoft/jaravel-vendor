# JWT 模块（JWT 认证插件）

> 包名：`com.weacsoft.jaravel.vendor.jwt`
> 对齐 Laravel 特性：`tymon/jwt-auth`（JWT 签发/解析/校验/刷新/黑名单）

## 目录

- [模块概述](#模块概述)
- [Maven 依赖](#maven-依赖)
- [类总览](#类总览)
- [JwtConfig](#jwtconfig)
- [JwtService](#jwtservice)
- [JwtGuard](#jwtguard)
- [JwtProperties](#jwtproperties)
- [JwtAutoConfiguration](#jwtautoconfiguration)
- [配置项（application.yml）](#配置项applicationyml)
- [核心流程详解](#核心流程详解)
- [完整使用示例](#完整使用示例)
- [线程安全说明](#线程安全说明)

---

## 模块概述

JWT 模块是 Jaravel 框架的 JWT 认证插件，对齐 Laravel 的 `tymon/jwt-auth` 扩展包。它作为 `auth` 模块的**插件式驱动**，通过 `GuardFactory` 机制注册到 `AuthManager`，提供完整的 JWT 认证能力：

- **Token 签发与解析**：基于 HS256 签名算法签发 access token 与 refresh token。
- **自动续期（Auto-Refresh）**：当 access token 已过其 TTL 的一半时，下次请求自动签发新 token（默认启用，可关闭）。
- **登出黑名单**：登出时将 token 加入缓存黑名单，即使 token 仍在有效期内也无法再次通过校验。
- **Refresh Token 换取**：使用 refresh token 换取新的 access token。
- **插件式集成**：引入 `jwt` 模块即自动启用 JWT 认证能力；未引入时 `AuthManager` 不会识别 `"jwt"` 驱动。

### 与 auth 模块的关系

JWT 模块**依赖** auth 模块，但**不修改** auth 模块代码。它通过 auth 模块提供的 `GuardFactory` 插件机制注册 `"jwt"` 驱动：

```
auth 模块（核心）
  ├── AuthManager.registerGuardDriver("jwt", factory)  ← 插件注册点
  └── 内置 "session" 驱动

jwt 模块（插件）
  ├── JwtAutoConfiguration  → 注册 "jwt" 驱动工厂到 AuthManager
  ├── JwtService            → JWT 签发/解析/校验/刷新/黑名单
  └── JwtGuard              → 实现 AuthGuard 契约
```

### 与 Laravel 的对齐关系

| Laravel (tymon/jwt-auth) | Jaravel JWT 模块 |
|---|---|
| `JWTAuth` | `JwtGuard` |
| `JWTManager` | `JwtService` |
| `config/jwt.php` | `JwtConfig` / `JwtProperties` |
| `blacklist` 机制 | `JwtService.blacklist()` / `isBlacklisted()` |
| `refresh` 机制 | `JwtService.shouldRefresh()` / `refresh()` |
| 自动续期（`blacklist_grace_period`） | `refreshEnabled` + `shouldRefresh()` |

---

## Maven 依赖

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>jwt</artifactId>
    <version>0.1.0</version>
</dependency>
```

该模块传递依赖：
- `auth` 模块（提供 `AuthManager`、`AuthGuard`、`GuardFactory` 等契约）
- `cache` 模块（提供 `CacheStore`，用于 JWT 黑名单）
- `io.jsonwebtoken:jjwt-api` / `jjwt-impl` / `jjwt-jackson`（JWT 库）

---

## 类总览

```
com.weacsoft.jaravel.vendor.jwt
├── JwtConfig                # JWT 配置（token 签发/刷新/黑名单参数）
├── JwtService               # JWT 服务（签发/解析/校验/刷新/黑名单）
├── JwtGuard                 # JWT 守卫（实现 AuthGuard 契约）
└── autoconfigure
    ├── JwtProperties        # 配置属性（jaravel.jwt.*）
    └── JwtAutoConfiguration # Spring Boot 自动装配
```

---

## JwtConfig

JWT 配置，对齐 manage8 的 `config/jwt.php`。包含 token 签发、刷新（refresh）与黑名单（logout）相关配置。

该类为链式 setter 风格（每个 setter 返回 `this`），便于在 `@Bean` 方法中流式构建。

### 配置项

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `secret` | String | `jaravel-secret-key-change-this-in-production-32bytes` | 签名密钥（生产环境务必更换） |
| `issuer` | String | `jaravel` | 签发者 |
| `ttl` | long | `3600_000`（1 小时） | access token 有效期（毫秒） |
| `refreshTtl` | long | `604800_000`（7 天） | refresh token 有效期（毫秒） |
| `header` | String | `Authorization` | 请求头名 |
| `prefix` | String | `Bearer ` | token 前缀 |
| `refreshEnabled` | boolean | `true` | 是否启用 token 自动刷新（续期） |
| `blacklistStore` | String | `array` | 黑名单使用的缓存 store 名称 |
| `blacklistPrefix` | String | `jwt:blacklist:` | 黑名单缓存键前缀 |

### 方法

所有 getter/setter 均为标准命名，setter 返回 `JwtConfig`（链式）：

```java
JwtConfig config = new JwtConfig()
        .setSecret("my-production-secret-key-32-bytes-long!!")
        .setTtl(7200_000L)           // 2 小时
        .setRefreshTtl(14L * 24 * 3600_000L) // 14 天
        .setRefreshEnabled(true)
        .setBlacklistStore("redis")  // 多实例用 Redis
        .setBlacklistPrefix("jwt:blacklist:");
```

### refreshEnabled 详解

启用后（默认），当 access token 过了 TTL 的一半时，下次请求会自动签发新 token，对齐 Laravel `tymon/jwt-auth` 的自动续期机制。项目可通过 `jaravel.jwt.refresh-enabled=false` 手动禁用。

### blacklistStore 详解

黑名单使用的缓存 store 名称，默认 `array`（内存）。

- **单机部署**：可用 `array`（内存），登出立即生效。
- **多实例部署**：应使用 `file` 或 Redis 等共享缓存，以保证登出后所有节点都能识别黑名单 token。

---

## JwtService

JWT 服务，对齐 manage8 的 `Tymon\JWTAuth`。提供 access token / refresh token 的签发、解析、校验，以及 token 刷新与登出黑名单。

### 线程安全

本类为**无状态单例**（`config`、`key`、`blacklistStore` 均为构造后不可变字段），可被多线程并发安全调用。黑名单状态全部委托给 `CacheStore`（底层驱动如 `ArrayCacheDriver` / `FileCacheDriver` 自身线程安全）。

### 构造器

```java
public JwtService(JwtConfig config, CacheStore blacklistStore)
```

| 参数 | 说明 |
|---|---|
| `config` | JWT 配置 |
| `blacklistStore` | 黑名单缓存 store（array / file / redis 等），构造后不可变 |

### 方法文档

#### Token 签发

```java
/** 签发 access token（无自定义声明，使用配置的 TTL） */
public String generate(String subject)

/** 签发 access token（带自定义声明，指定 TTL） */
public String generate(String subject, Map<String, Object> claims, long ttl)

/** 签发 refresh token（带 type=refresh 声明，有效期取 refreshTtl） */
public String generateRefreshToken(String subject)
```

示例：

```java
// 签发 access token
String accessToken = jwtService.generate("1001");

// 签发带自定义声明的 access token
String token = jwtService.generate("1001", Map.of("role", "admin"), 3600_000L);

// 签发 refresh token
String refreshToken = jwtService.generateRefreshToken("1001");
```

签发的 access token 包含以下声明：
- `sub`：subject（用户主键）
- `iss`：issuer（签发者，默认 `jaravel`）
- `iat`：签发时间
- `exp`：过期时间（iat + ttl）
- 自定义 claims（如有）

refresh token 额外包含 `type=refresh` 声明。

#### Token 解析

```java
/** 解析 token，返回 Claims（签名错误或过期会抛异常） */
public Claims parse(String token)

/** 获取 token 的 subject（用户主键） */
public String getSubject(String token)

/** 获取 token 的签发时间 */
public Date getIssuedAt(String token)

/** 获取 token 的过期时间 */
public Date getExpiration(String token)
```

示例：

```java
Claims claims = jwtService.parse(token);
String userId = claims.getSubject();
Date exp = claims.getExpiration();
```

#### Token 校验

```java
/**
 * 校验 token 是否有效：签名正确、未过期、且不在黑名单中。
 * @return 有效返回 true，否则返回 false
 */
public boolean validate(String token)

/** token 是否已过期 */
public boolean isExpired(String token)
```

`validate()` 的校验逻辑：

```
validate(token)
  ├── parse(token)  → 签名错误或已过期？ → 返回 false
  └── isBlacklisted(token) → 在黑名单中？ → 返回 false
  └── 返回 true
```

#### Token 刷新判断

```java
/**
 * 判断 token 是否应当刷新（续期）。
 * 当 token 已过其 TTL 的一半时返回 true。
 * token 无效或已过期返回 false。
 */
public boolean shouldRefresh(String token)
```

判断逻辑：

```
shouldRefresh(token)
  ├── parse 失败 → false
  ├── 已过期（now >= exp） → false（应由 refresh token 换取新 token）
  └── halfway = iat + (exp - iat) / 2
        now >= halfway → true（应刷新）
        now <  halfway → false（无需刷新）
```

示例：

```java
if (jwtService.shouldRefresh(token)) {
    // token 已过半 TTL，建议刷新
    String newToken = jwtService.refresh(refreshToken);
}
```

#### Token 刷新

```java
/**
 * 使用 refresh token 换取新的 access token。
 * 校验 refresh token：签名有效、未过期、未在黑名单中、且声明 type=refresh。
 * 校验通过后签发新的 access token（不签发新 refresh token）。
 *
 * @param refreshToken refresh token
 * @return 新的 access token，校验失败返回 null
 */
public String refresh(String refreshToken)
```

`refresh()` 的校验逻辑：

```
refresh(refreshToken)
  ├── parse 失败 → null
  ├── type != "refresh" → null
  ├── isBlacklisted(refreshToken) → null
  └── generate(subject) → 返回新 access token
```

> 注意：对齐 `tymon/jwt-auth` 单次续期，`refresh()` 只签发新 access token，不签发新 refresh token。

#### 黑名单管理

```java
/**
 * 将 token 加入黑名单（登出时调用）。
 * 黑名单条目的 TTL 设为 token 剩余有效期（秒），token 自然过期后黑名单条目自动清除。
 */
public void blacklist(String token)

/** 判断 token 是否在黑名单中（已登出） */
public boolean isBlacklisted(String token)
```

`blacklist()` 的逻辑：

```
blacklist(token)
  ├── token 为空 → 直接返回
  ├── 计算 token 剩余有效期（秒）
  │     ├── ttlSeconds > 0 → 以剩余秒数为 TTL 写入缓存
  │     └── ttlSeconds <= 0 → 写入 1 秒短时记录（防止边界竞态）
  └── blacklistStore.put(prefix + token, "1", ttlSeconds)
```

黑名单缓存键格式：`{blacklistPrefix}{token}`，默认为 `jwt:blacklist:{token}`。

示例：

```java
// 登出时加入黑名单
jwtService.blacklist(accessToken);

// 校验是否已登出
if (jwtService.isBlacklisted(accessToken)) {
    // token 已注销，拒绝访问
}
```

---

## JwtGuard

JWT 守卫，对齐 manage8 的 api guard（jwt 驱动）与 Laravel `tymon/jwt-auth`。实现 `AuthGuard` 契约，从请求头解析 Bearer token，按 subject（主键）取出用户。

### 核心能力

- **登出黑名单**：`logout()` 将当前 token 加入缓存黑名单，后续请求即使携带该 token 也无法通过 `user()`。
- **自动续期**：当 `refreshEnabled=true`（默认）且 token 已过半 TTL 时，`user()` 自动签发新 token，可通过 `token()` 获取并返回给客户端。
- **Refresh token 换取**：`refresh(String)` 用 refresh token 换取新 access token。

### 构造器

```java
/** 完整构造器 */
public JwtGuard(String name, UserProvider provider, JwtService jwtService, boolean refreshEnabled)

/** 兼容旧构造器：默认启用自动续期 */
public JwtGuard(String name, UserProvider provider, JwtService jwtService)
```

| 参数 | 说明 |
|---|---|
| `name` | 守卫名称 |
| `provider` | 用户提供者 |
| `jwtService` | JWT 服务 |
| `refreshEnabled` | 是否启用自动续期（默认 true） |

### 请求级状态字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `cachedUser` | `Authenticatable` | 当前请求解析出的用户（请求级缓存） |
| `resolved` | `boolean` | 是否已解析过当前请求 |
| `lastToken` | `String` | 最近一次签发的 token（login 或自动续期产生），供 `token()` 返回 |
| `requestToken` | `String` | 当前请求携带的 token，供 `logout()` 加入黑名单 |

### 方法文档

#### 状态查询

```java
/** 是否已登录（user() != null） */
public boolean check()

/** 是否访客（!check()） */
public boolean guest()
```

#### 用户解析

```java
/**
 * 解析当前请求的用户。
 * 流程：
 * 1. 从 Authorization 头提取 Bearer token
 * 2. 校验 token（签名 + 过期 + 黑名单，见 JwtService.validate）
 * 3. 按 subject（主键）通过 UserProvider.retrieveById 取出用户
 * 4. 若 refreshEnabled=true 且 token 已过半 TTL，自动签发新 token 存入 lastToken
 */
public Authenticatable user()
```

`user()` 的完整流程：

```
user()
  ├── 已解析过？ → 返回缓存
  └── 首次解析
        ├── 获取 AuthContext 当前请求
        ├── 提取 Authorization: Bearer {token}
        ├── validate(token) 失败？ → 返回 null
        ├── requestToken = token
        ├── subject = getSubject(token)
        ├── cachedUser = provider.retrieveById(subject)
        └── 自动续期检查
              ├── cachedUser != null && refreshEnabled && shouldRefresh(token)
              │     → lastToken = generate(主键)  // 签发新 token
              └── 返回 cachedUser
```

#### 登录

```java
/**
 * 登录指定用户：签发 access token，缓存用户。
 * 签发的 access token 可通过 token() 获取。
 */
public void login(Authenticatable user)
```

示例：

```java
// 登录后获取 token
jwtGuard.login(user);
String accessToken = jwtGuard.token();        // access token
String refreshToken = jwtGuard.refreshToken(); // refresh token
```

#### 登出

```java
/**
 * 登出：将当前请求的 token 加入黑名单，并清理请求级状态。
 * 加入黑名单后，该 token 即使仍在有效期内，后续请求也无法通过 user() 校验。
 */
public void logout()
```

`logout()` 的逻辑：

```
logout()
  ├── requestToken != null？
  │     └── jwtService.blacklist(requestToken)
  └── requestToken == null（未解析过 user()）
        └── 从请求头提取 token → jwtService.blacklist(token)
  ├── cachedUser = null
  ├── lastToken = null
  └── requestToken = null
```

#### Token 获取

```java
/**
 * 获取最近一次签发的 access token。
 * 包括：login() 签发的 token，或 user() 自动续期签发的新 token。
 * 若本次请求既未登录也未触发自动续期，返回 null（客户端继续使用原有 token 即可）。
 */
public String token()
```

#### Refresh Token 相关

```java
/** 签发 refresh token（登录后调用），未登录返回 null */
public String refreshToken()

/** 判断指定 token 是否应当刷新（已过半 TTL） */
public boolean shouldRefresh(String token)

/**
 * 使用 refresh token 换取新的 access token。
 * 校验通过后签发新 access token，存入 lastToken，同时取出用户并缓存。
 * @return 新的 access token，校验失败返回 null
 */
public String refresh(String refreshToken)
```

示例：

```java
// 登录
jwtGuard.login(user);
String accessToken = jwtGuard.token();
String refreshToken = jwtGuard.refreshToken();

// 后续用 refresh token 换取新 access token
String newAccessToken = jwtGuard.refresh(refreshToken);
```

#### 其他

```java
/** 守卫名称 */
public String getName()
```

---

## JwtProperties

JWT 配置属性，前缀 `jaravel.jwt`。由 Spring Boot 自动绑定 `application.yml` 中的配置。

```java
@ConfigurationProperties(prefix = "jaravel.jwt")
public class JwtProperties {

    private String secret = "jaravel-secret-key-change-this-in-production-32bytes";
    private long ttl = 3600_000L;                          // 1 小时
    private long refreshTtl = 7 * 24 * 3600_000L;          // 7 天
    private String header = "Authorization";
    private String prefix = "Bearer ";
    private boolean refreshEnabled = true;
    private String blacklistStore = "array";
    private String blacklistPrefix = "jwt:blacklist:";

    // 标准 getter/setter ...
}
```

该属性类与 `JwtConfig` 字段一一对应，由 `JwtAutoConfiguration` 转换为 `JwtConfig` Bean。

---

## JwtAutoConfiguration

JWT 自动装配类，注册 `JwtConfig`、`JwtService` Bean，并通过 `AuthManager.registerGuardDriver` 将 jwt 驱动插件式注册到 `AuthManager`。

```java
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({AuthManager.class, JwtService.class})
@EnableConfigurationProperties(JwtProperties.class)
public class JwtAutoConfiguration implements SmartInitializingSingleton {

    @Bean
    @ConditionalOnMissingBean
    public JwtConfig jwtConfig(JwtProperties properties) { ... }

    @Bean
    @ConditionalOnMissingBean
    public JwtService jwtService(JwtConfig jwtConfig, CacheManager cacheManager) { ... }

    @Override
    public void afterSingletonsInstantiated() {
        // 所有单例 Bean 就绪后，注册 jwt 守卫工厂到 AuthManager
        authManager.registerGuardDriver("jwt",
            (name, provider, config) -> new JwtGuard(name, provider, jwtService, refreshEnabled));
    }
}
```

### 装配条件

- Servlet Web 应用环境
- classpath 同时存在 `AuthManager` 与 `JwtService` 类

### 关键设计

#### 1. 黑名单缓存 store 获取

`jwtService()` Bean 方法从 `CacheManager` 按 `JwtConfig.getBlacklistStore()` 指定的名称获取 store（默认 `array`）。若指定的 store 未注册，回退到默认 store：

```java
CacheStore blacklistStore;
try {
    blacklistStore = cacheManager.store(jwtConfig.getBlacklistStore());
} catch (IllegalStateException e) {
    // 指定的 store 未注册，回退到默认 store
    blacklistStore = cacheManager.store();
}
return new JwtService(jwtConfig, blacklistStore);
```

#### 2. 延迟注册驱动工厂

`JwtService` 由本类的 `@Bean` 方法产生，因此不能在字段上 `@Autowired` 自身产生的 Bean（Spring 6 默认禁止循环引用）。这里改为在 `afterSingletonsInstantiated()` 中通过 `ApplicationContext` 获取，该回调在所有单例 Bean 就绪后才执行，天然避免循环依赖。

工厂创建 `JwtGuard` 时传入 `JwtConfig.isRefreshEnabled()`，控制是否启用自动续期。

---

## 配置项（application.yml）

```yaml
jaravel:
  jwt:
    # 签名密钥（生产环境务必更换，需 >= 32 字节）
    secret: your-production-secret-key-at-least-32-bytes

    # access token 有效期（毫秒），默认 1 小时
    ttl: 3600000

    # refresh token 有效期（毫秒），默认 7 天
    refresh-ttl: 604800000

    # 请求头名
    header: Authorization

    # token 前缀
    prefix: "Bearer "

    # token 自动刷新（续期），默认 true
    # 启用后，access token 过半 TTL 时下次请求自动签发新 token
    refresh-enabled: true

    # 黑名单缓存 store，默认 array（内存）
    # 单机部署可用 array；多实例部署应使用 file 或 redis 等共享缓存
    blacklist-store: array

    # 黑名单缓存键前缀
    blacklist-prefix: "jwt:blacklist:"
```

### 配置项汇总

| 配置项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `jaravel.jwt.secret` | String | `jaravel-secret-key-...` | 签名密钥（生产环境务必更换，需 >= 32 字节） |
| `jaravel.jwt.ttl` | long | `3600000` | access token 有效期（毫秒），默认 1 小时 |
| `jaravel.jwt.refresh-ttl` | long | `604800000` | refresh token 有效期（毫秒），默认 7 天 |
| `jaravel.jwt.header` | String | `Authorization` | 请求头名 |
| `jaravel.jwt.prefix` | String | `Bearer ` | token 前缀 |
| `jaravel.jwt.refresh-enabled` | boolean | `true` | 是否启用 token 自动刷新 |
| `jaravel.jwt.blacklist-store` | String | `array` | 黑名单缓存 store 名称 |
| `jaravel.jwt.blacklist-prefix` | String | `jwt:blacklist:` | 黑名单缓存键前缀 |

### 多实例部署配置示例

```yaml
jaravel:
  jwt:
    secret: ${JWT_SECRET}           # 从环境变量读取
    blacklist-store: redis          # 使用 Redis 共享黑名单
    refresh-enabled: true
```

---

## 核心流程详解

### 1. Token 刷新流程

```
客户端请求（携带 access token）
  │
  ▼
JwtGuard.user()
  ├── validate(token) → 通过
  ├── 取出用户
  └── shouldRefresh(token)？
        ├── false → 正常返回用户，token() 返回 null（客户端继续用原 token）
        └── true  → lastToken = generate(主键)  // 签发新 token
                     │
                     ▼
              token() 返回新 token
              │
              ▼
        应用层检查 token()，若非 null 则返回给客户端
        客户端用新 token 替换旧 token
```

`shouldRefresh` 判断逻辑：当 token 已过其 TTL 的一半时返回 `true`。已过期的 token 返回 `false`（应由 refresh token 换取新 token）。

### 2. Refresh Token 换取流程

```
客户端用 refresh token 请求刷新
  │
  ▼
JwtGuard.refresh(refreshToken)
  │
  ▼
JwtService.refresh(refreshToken)
  ├── parse(refreshToken) → 签名/过期校验
  ├── type == "refresh"？
  ├── isBlacklisted(refreshToken)？
  └── generate(subject) → 新 access token
        │
        ▼
  JwtGuard:
  ├── lastToken = 新 access token
  ├── cachedUser = provider.retrieveById(subject)
  └── 返回新 access token
```

> 注意：`refresh()` 只签发新 access token，不签发新 refresh token（对齐 `tymon/jwt-auth` 单次续期）。

### 3. 登出流程

```
客户端请求登出（携带 access token）
  │
  ▼
JwtGuard.logout()
  ├── requestToken != null？
  │     └── jwtService.blacklist(requestToken)
  │           ├── 计算剩余有效期
  │           └── blacklistStore.put(prefix + token, "1", 剩余秒数)
  └── 清理请求级状态（cachedUser/lastToken/requestToken = null）
        │
        ▼
后续请求携带同一 token：
  JwtGuard.user()
    └── validate(token)
          └── isBlacklisted(token) → true → 返回 false
                └── user() 返回 null → 认证失败
```

黑名单条目的 TTL 设为 token 剩余有效期，token 自然过期后黑名单条目自动清除，避免黑名单无限膨胀。

---

## 完整使用示例

### 1. 配置

```yaml
jaravel:
  auth:
    default-guard: api
  jwt:
    secret: my-production-secret-key-32-bytes-long!!
    ttl: 3600000
    refresh-ttl: 604800000
    refresh-enabled: true
    blacklist-store: array
```

### 2. 注册守卫

```java
@Configuration
public class AuthConfig {

    @Bean
    public UserProvider userProvider(UserModel userModel) {
        return new EloquentUserProvider(userModel, "number");
    }

    @Bean
    public ApplicationRunner authRegistrar(AuthManager authManager, UserProvider userProvider) {
        return args -> {
            authManager.registerProvider("users", userProvider);
            // jwt 驱动由 JwtAutoConfiguration 自动注册
            authManager.registerGuard("api", "jwt", "users");
        };
    }
}
```

### 3. 登录（签发 token）

```java
@PostMapping("/login")
public Response login(@RequestBody LoginRequest req, UserProvider provider) {
    // 1. 按凭证查出用户
    User user = (User) provider.retrieveByCredentials(Map.of("number", req.getNumber()));
    // 2. 应用层校验密码
    if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
        return ResponseBuilder.error(401, "工号或密码错误");
    }
    // 3. 通过 api guard（JWT）登录
    Auth.login(user, "api");

    // 4. 获取签发的 token
    Map<String, Object> result = new HashMap<>();
    result.put("access_token", Auth.token("api"));

    // 获取 refresh token
    JwtGuard jwtGuard = (JwtGuard) Auth.guard("api");
    result.put("refresh_token", jwtGuard.refreshToken());

    return ResponseBuilder.success(result);
}
```

### 4. 路由保护

```java
// 使用 jwt 守卫保护 API 路由
router.get("/api/profile", this::profile)
      .middleware(new Authenticate("api"));
```

### 5. 获取当前用户

```java
@GetMapping("/api/profile")
public Response profile() {
    if (Auth.check()) {
        User user = (User) Auth.user();
        return ResponseBuilder.success(user);
    }
    return ResponseBuilder.error(401, "Unauthorized");
}
```

### 6. 自动续期处理

```java
@GetMapping("/api/profile")
public Response profile() {
    User user = (User) Auth.user();
    if (user == null) {
        return ResponseBuilder.error(401, "Unauthorized");
    }

    Response response = ResponseBuilder.success(user);

    // 检查是否自动续期签发了新 token
    String newToken = Auth.token("api");
    if (newToken != null) {
        // 返回新 token 给客户端，客户端替换旧 token
        response.header("New-Token", newToken);
    }

    return response;
}
```

### 7. Refresh Token 刷新

```java
@PostMapping("/api/refresh")
public Response refresh(@RequestBody RefreshRequest req) {
    JwtGuard jwtGuard = (JwtGuard) Auth.guard("api");
    String newAccessToken = jwtGuard.refresh(req.getRefreshToken());
    if (newAccessToken == null) {
        return ResponseBuilder.error(401, "refresh token 无效或已过期");
    }
    return ResponseBuilder.success(Map.of("access_token", newAccessToken));
}
```

### 8. 登出

```java
@PostMapping("/api/logout")
public Response logout() {
    Auth.logout("api");  // 将当前 token 加入黑名单
    return ResponseBuilder.success("已登出");
}
```

---

## 线程安全说明

### 1. JwtService（无状态单例）

本类为**无状态单例**，`config`、`key`、`blacklistStore` 均为构造后不可变字段，可被多线程并发安全调用。

- 所有签发/解析/校验操作均为无状态计算，不持有任何可变状态。
- 黑名单状态全部委托给 `CacheStore`，底层驱动（如 `ArrayCacheDriver` / `FileCacheDriver` / Redis）自身保证线程安全。
- `SecretKey` 在构造时一次性生成，后续只读访问。

### 2. JwtGuard（ThreadLocal 隔离）

本守卫实例由 `AuthManager` 通过 `ThreadLocal` 按请求隔离，每个请求获得独立的 `JwtGuard` 实例。

| 状态字段 | 隔离方式 | 说明 |
|---|---|---|
| `cachedUser` | ThreadLocal 隔离 | 请求级缓存，不跨请求共享 |
| `resolved` | ThreadLocal 隔离 | 请求级标志 |
| `lastToken` | ThreadLocal 隔离 | 请求级签发 token |
| `requestToken` | ThreadLocal 隔离 | 请求级请求 token |

请求结束时由 `AuthLifecycleFilter` 调用 `AuthManager.clear()` 清理 ThreadLocal，防止线程池复用导致的串态。

### 3. 黑名单缓存

黑名单存储委托给 `CacheStore`，其线程安全性取决于底层驱动：

| 缓存 store | 线程安全 | 适用场景 |
|---|---|---|
| `array`（内存） | 线程安全（`ConcurrentHashMap`） | 单机部署 |
| `file` | 线程安全（文件锁） | 多实例部署 |
| `redis` | 线程安全（Redis 单线程模型） | 多实例部署，推荐 |

### 线程安全总结

| 组件 | 类型 | 线程安全机制 |
|---|---|---|
| `JwtService` | 无状态单例 | 不可变字段 + 委托 `CacheStore` |
| `JwtGuard` | 请求级实例 | `AuthManager` 的 ThreadLocal 隔离 |
| `JwtConfig` | 不可变配置 | 构造后不再修改 |
| 黑名单缓存 | 委托 `CacheStore` | 底层驱动自身线程安全 |

> **关键约束**：`JwtGuard` 实例**必须**通过 `AuthManager.guard(String)` 获取，不可跨请求缓存或共享，否则其内部的可变状态会串态。`JwtService` 作为无状态单例可安全地在任意线程共享调用。
