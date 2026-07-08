# JWT 模块（JWT 认证插件）

> 包名：`com.weacsoft.jaravel.vendor.jwt`
> 对齐 Laravel 特性：`tymon/jwt-auth`（JWT 签发/解析/校验/刷新/黑名单）

## 目录

- [模块概述](#模块概述)
- [三种运行模式](#三种运行模式)
- [Maven 依赖](#maven-依赖)
- [类总览](#类总览)
- [JwtConfig](#jwtconfig)
- [JwtService](#jwtservice)
- [JwtGuard](#jwtguard)
- [JwtTokenResponseFilter](#jwttokenresponsefilter)
- [JwtProperties](#jwtproperties)
- [JwtAutoConfiguration](#jwtautoconfiguration)
- [配置项（application.yml）](#配置项applicationyml)
- [黑名单功能](#黑名单功能)
- [宽限期功能](#宽限期功能)
- [核心流程详解](#核心流程详解)
- [完整使用示例](#完整使用示例)
- [线程安全说明](#线程安全说明)

---

## 模块概述

JWT 模块是 Jaravel 框架的 JWT 认证插件，对齐 Laravel 的 `tymon/jwt-auth` 扩展包。它作为 `auth` 模块的**插件式驱动**，通过 `GuardFactory` 机制注册到 `AuthManager`，提供完整的 JWT 认证能力：

- **Token 签发与解析**：基于 HS256 签名算法签发 access token 与 refresh token。
- **自动续期（Auto-Refresh）**：当 access token 已过其 TTL 的一半时，下次请求自动签发新 token（默认启用，可关闭）。
- **登出黑名单**：开启黑名单后，登出时将 token 加入缓存黑名单，即使 token 仍在有效期内也无法再次通过校验。
- **宽限期续期**：开启黑名单并设置宽限期后，过期 token 在宽限期内仍可请求一次，响应 header 自动携带新 token，旧 token 被加入黑名单。
- **Refresh Token 换取**：使用 refresh token 换取新的 access token。
- **插件式集成**：引入 `jwt` 模块即自动启用 JWT 认证能力；未引入时 `AuthManager` 不会识别 `"jwt"` 驱动。

### 与 auth 模块的关系

JWT 模块**依赖** auth 模块，但**不修改** auth 模块代码。它通过 auth 模块提供的 `GuardFactory` 插件机制注册 `"jwt"` 驱动：

```
auth 模块（核心）
  ├── AuthManager.registerGuardDriver("jwt", factory)  ← 插件注册点
  └── 内置 "session" 驱动

jwt 模块（插件）
  ├── JwtAutoConfiguration      → 注册 "jwt" 驱动工厂到 AuthManager，装配 JwtTokenResponseFilter
  ├── JwtService                → JWT 签发/解析/校验/刷新/黑名单/宽限期
  ├── JwtGuard                  → 实现 AuthGuard 契约
  └── JwtTokenResponseFilter    → 响应 header 自动写入新 token
```

### 与 Laravel 的对齐关系

| Laravel (tymon/jwt-auth) | Jaravel JWT 模块 |
|---|---|
| `JWTAuth` | `JwtGuard` |
| `JWTManager` | `JwtService` |
| `config/jwt.php` | `JwtConfig` / `JwtProperties` |
| `blacklist` 机制 | `JwtConfig.blacklistEnabled` + `JwtService.blacklist()` / `isBlacklisted()` |
| `blacklist_grace_period` | `JwtConfig.gracePeriodSeconds` + `JwtService.isInGracePeriod()` |
| `refresh` 机制 | `JwtService.shouldRefresh()` / `refresh()` |
| 自动续期 | `refreshEnabled` + `shouldRefresh()` |

---

## 三种运行模式

JWT 模块通过 `blacklistEnabled` 与 `gracePeriodSeconds` 两个配置项的组合，提供三种运行模式：

### 1. 标准 JWT 模式（默认）

- **配置**：`blacklist-enabled=false`（默认）
- **行为**：JWT 仅校验签名与过期，**不依赖任何缓存模块**。`blacklist()` 成为空操作，`isBlacklisted()` 始终返回 `false`。
- **适用场景**：无状态、可水平扩展的纯 JWT 认证，登出仅靠客户端丢弃 token 实现。
- **特点**：性能最优，无需引入 cache 模块的运行时开销。

### 2. 黑名单模式

- **配置**：`blacklist-enabled=true`
- **行为**：登出踢 token 功能生效。`blacklist()` 将 token 写入 cache 模块的 `CacheStore`，`isBlacklisted()` 校验是否已注销，`validate()` 自动拒绝黑名单 token。
- **适用场景**：需要在服务端主动失效 token（如用户修改密码、管理员封禁账号、主动登出）。
- **依赖**：需配合 cache 模块使用，通过 `blacklist-store` 指定缓存 store。多实例部署建议使用 redis 等共享缓存。

### 3. 宽限期模式

- **配置**：`blacklist-enabled=true` 且 `grace-period-seconds>0`
- **行为**：在黑名单模式基础上，过期 token 在宽限期窗口内仍可请求一次。请求正常执行后，响应 header 自动携带新 token（默认 `X-New-Token`），旧 token 被加入黑名单防止重复使用。
- **适用场景**：access token 过期时对客户端实现"无感续期"，避免 token 刚过期就返回 401 导致用户体验中断。
- **依赖**：必须开启黑名单（宽限期结束后需要将旧 token 加入黑名单以防止重复使用）。

> **模式关系**：宽限期模式是黑名单模式的增强，二者为包含关系。标准模式与黑名单模式互斥（由 `blacklistEnabled` 切换）。

---

## Maven 依赖

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>jwt</artifactId>
    <version>0.1.1</version>
</dependency>
```

该模块传递依赖：
- `auth` 模块（提供 `AuthManager`、`AuthGuard`、`GuardFactory` 等契约）
- `cache` 模块（提供 `CacheStore`，用于 JWT 黑名单；**仅当 `blacklist-enabled=true` 时实际使用**，标准 JWT 模式下不读写缓存）
- `io.jsonwebtoken:jjwt-api` / `jjwt-impl` / `jjwt-jackson`（JWT 库）

---

## 类总览

```
com.weacsoft.jaravel.vendor.jwt
├── JwtConfig                # JWT 配置（token 签发/刷新/黑名单/宽限期参数）
├── JwtService               # JWT 服务（签发/解析/校验/刷新/黑名单/宽限期判断）
├── JwtGuard                 # JWT 守卫（实现 AuthGuard 契约）
├── JwtTokenResponseFilter   # 响应过滤器（自动将新 token 写入响应 header）
└── autoconfigure
    ├── JwtProperties        # 配置属性（jaravel.jwt.*）
    └── JwtAutoConfiguration # Spring Boot 自动装配
```

---

## JwtConfig

JWT 配置，对齐 manage8 的 `config/jwt.php`。包含 token 签发、刷新（refresh）、黑名单（logout）与宽限期（grace period）相关配置。

该类为链式 setter 风格（每个 setter 返回 `this`），便于在 `@Bean` 方法中流式构建。

### 设计原则

当 `blacklistEnabled` 为 `false`（默认）时，JWT 表现为标准形式——仅校验签名与过期，不依赖任何缓存。开启黑名单后，登出踢 token 功能生效，需配合 cache 模块使用。宽限期功能需要黑名单开启才能工作（因为宽限期结束后需要将旧 token 加入黑名单以防止重复使用）。

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
| `blacklistEnabled` | boolean | `false` | 是否启用黑名单（登出踢 token），关闭时为标准 JWT |
| `blacklistStore` | String | `array` | 黑名单使用的缓存 store 名称（仅 `blacklistEnabled=true` 时生效） |
| `blacklistPrefix` | String | `jwt:blacklist:` | 黑名单缓存键前缀 |
| `gracePeriodSeconds` | long | `0` | 宽限期秒数，0 关闭；需 `blacklistEnabled=true` 才生效 |
| `graceHeader` | String | `X-New-Token` | 宽限期/自动续期时新 token 写入响应 header 的名称 |

### 方法

所有 getter/setter 均为标准命名，setter 返回 `JwtConfig`（链式）：

```java
JwtConfig config = new JwtConfig()
        .setSecret("my-production-secret-key-32-bytes-long!!")
        .setTtl(7200_000L)           // 2 小时
        .setRefreshTtl(14L * 24 * 3600_000L) // 14 天
        .setRefreshEnabled(true)
        .setBlacklistEnabled(true)   // 开启黑名单
        .setBlacklistStore("redis")  // 多实例用 Redis
        .setBlacklistPrefix("jwt:blacklist:")
        .setGracePeriodSeconds(30)   // 30 秒宽限期
        .setGraceHeader("X-New-Token");
```

### refreshEnabled 详解

启用后（默认），当 access token 过了 TTL 的一半时，下次请求会自动签发新 token，对齐 Laravel `tymon/jwt-auth` 的自动续期机制。项目可通过 `jaravel.jwt.refresh-enabled=false` 手动禁用。

### blacklistEnabled 详解

是否启用黑名单（登出踢 token），默认**关闭**。

- **关闭（标准 JWT）**：JWT 仅校验签名与过期，不依赖缓存模块。`blacklist()` 为空操作，`isBlacklisted()` 始终返回 `false`。
- **开启（黑名单模式）**：`blacklist()` 和 `isBlacklisted()` 生效，需配合 cache 模块使用（通过 `blacklistStore` 指定缓存 store）。多实例部署建议使用 redis 等共享缓存，保证登出后所有节点都能识别。

### blacklistStore 详解

黑名单使用的缓存 store 名称，默认 `array`（内存）。仅当 `blacklistEnabled=true` 时生效。可选值：`array`、`file`、`redis`、`database`。

- **单机部署**：可用 `array`（内存），登出立即生效。
- **多实例部署**：应使用 `file` 或 Redis 等共享缓存，以保证登出后所有节点都能识别黑名单 token。

### gracePeriodSeconds 详解

宽限期秒数，默认 `0`（关闭）。当 access token 过期后，在宽限期时间内仍可正常请求一次：

- 请求正常执行（用户通过认证）；
- 响应 header 中携带新 token（通过 `graceHeader` 指定的响应头）；
- 旧 token 被加入黑名单，无法再次使用。

**注意**：宽限期功能需要 `blacklistEnabled=true` 才能工作（因为宽限期结束后需要将旧 token 加入黑名单以防止重复使用）。

### graceHeader 详解

宽限期续期或自动续期时，新 token 放入响应 header 的名称，默认 `X-New-Token`。`JwtTokenResponseFilter` 会使用此名称将新 token 写入响应 header。

---

## JwtService

JWT 服务，对齐 manage8 的 `Tymon\JWTAuth`。提供 access token / refresh token 的签发、解析、校验，以及 token 刷新、登出黑名单与宽限期判断。

### 黑名单开关

当 `JwtConfig.isBlacklistEnabled()` 为 `false`（默认）时，本类表现为标准 JWT：仅校验签名与过期，不依赖任何缓存。`blacklist(String)` 和 `isBlacklisted(String)` 成为空操作/始终返回 `false`。开启后才真正读写 `CacheStore`。

### 线程安全

本类为**无状态单例**（`config`、`key`、`blacklistStore` 均为构造后不可变字段），可被多线程并发安全调用。黑名单状态全部委托给 `CacheStore`（底层驱动如 `ArrayCacheDriver` / `FileCacheDriver` 自身线程安全）。当 `blacklistEnabled=false` 时 `blacklistStore` 可为 `null`。

### 构造器

```java
public JwtService(JwtConfig config, CacheStore blacklistStore)
```

| 参数 | 说明 |
|---|---|
| `config` | JWT 配置 |
| `blacklistStore` | 黑名单缓存 store（array / file / redis 等），构造后不可变；`blacklistEnabled=false` 时可传 `null` |

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

/**
 * 从可能已过期的 token 中获取 subject。
 * 用于宽限期场景：token 已过期但需要取出 subject 以查询用户。
 * @return subject 字符串，无法解析返回 null
 */
public String getSubjectFromExpired(String token)

/** 获取 token 的签发时间 */
public Date getIssuedAt(String token)

/** 获取 token 的过期时间 */
public Date getExpiration(String token)
```

`getSubjectFromExpired()` 的逻辑：

```
getSubjectFromExpired(token)
  ├── parse(token) 成功 → 返回 subject
  ├── 抛出 ExpiredJwtException → 从异常的 Claims 中取 subject 返回
  └── 其他异常 → 返回 null
```

#### Token 校验

```java
/**
 * 校验 token 是否有效：签名正确、未过期、且不在黑名单中。
 * 当黑名单关闭时，仅校验签名与过期。
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
  ├── 已过期（now >= exp） → false（应由 refresh token 换取新 token，或走宽限期）
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

#### 宽限期判断

```java
/**
 * 判断 token 是否处于宽限期内。
 * 宽限期是指 token 已过期，但距离过期时间未超过 gracePeriodSeconds 秒。
 * 在此期间，token 仍可用于请求一次（由 JwtGuard 处理），请求成功后会签发新 token
 * 并将旧 token 加入黑名单。
 * 当 gracePeriodSeconds <= 0 时，宽限期功能关闭，始终返回 false。
 * @return 是否处于宽限期内
 */
public boolean isInGracePeriod(String token)
```

`isInGracePeriod()` 的判断逻辑：

```
isInGracePeriod(token)
  ├── gracePeriodSeconds <= 0 → false（宽限期关闭）
  ├── parse(token) 成功（未过期） → false
  ├── 抛出 ExpiredJwtException
  │     └── now < expiredAt + gracePeriodSeconds * 1000 → true（在宽限期内）
  └── 其他异常 → false
```

示例：

```java
// 判断过期 token 是否仍在宽限期内
if (jwtService.isInGracePeriod(expiredToken)) {
    // 处于宽限期，可取出用户并签发新 token
    String subject = jwtService.getSubjectFromExpired(expiredToken);
}
```

#### 黑名单管理

```java
/**
 * 将 token 加入黑名单（登出或宽限期使用后调用）。
 * 当 blacklistEnabled=false 时为空操作。
 * 黑名单条目的 TTL 设为 token 剩余有效期（秒），token 自然过期后黑名单条目自动清除。
 * 对于已过期的 token（宽限期场景），TTL 设为宽限期剩余秒数。
 */
public void blacklist(String token)

/**
 * 判断 token 是否在黑名单中（已登出或宽限期已使用）。
 * 当 blacklistEnabled=false 时始终返回 false。
 */
public boolean isBlacklisted(String token)
```

```java
/**
 * 从黑名单中移除 token（误杀恢复）。
 * 当 blacklistEnabled=false 时为空操作。
 */
public void removeFromBlacklist(String token)
```

`removeFromBlacklist()` 调用 `blacklistStore.forget(key)` 移除黑名单条目，适用于误将 token 加入黑名单后需要恢复的场景。移除后该 token 在有效期内可再次通过 `validate()` 校验。

`blacklist()` 的逻辑：

```
blacklist(token)
  ├── blacklistEnabled=false 或 blacklistStore=null → 直接返回（空操作）
  ├── token 为空 → 直接返回
  ├── 计算 token 剩余有效期（秒）
  │     ├── ttlSeconds > 0 → 以剩余秒数为 TTL 写入缓存
  │     └── ttlSeconds <= 0（已过期，宽限期场景）
  │           └── ttlSeconds = max(1, gracePeriodSeconds)
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

- **登出黑名单**（需 `blacklistEnabled=true`）：`logout()` 将当前 token 加入缓存黑名单，后续请求即使携带该 token 也无法通过 `user()`。
- **自动续期**：当 `refreshEnabled=true`（默认）且 token 已过半 TTL 时，`user()` 自动签发新 token，可通过 `token()` 获取。
- **宽限期续期**（需 `blacklistEnabled=true` 且 `gracePeriodSeconds>0`）：过期 token 在宽限期内仍可请求一次，请求正常执行后在响应 header 中携带新 token，旧 token 被加入黑名单。
- **Refresh token 换取**：`refresh(String)` 用 refresh token 换取新 access token。

### 响应 header 中的新 token

当自动续期或宽限期续期触发时，新 token 可通过 `token()` 获取。`JwtTokenResponseFilter` 会在请求结束时自动将新 token 写入响应 header，应用层通常无需手动处理。

### 构造器

```java
/** 完整构造器（带 JwtConfig，支持宽限期等高级特性） */
public JwtGuard(String name, UserProvider provider, JwtService jwtService, boolean refreshEnabled, JwtConfig jwtConfig)

/** 旧构造器（不带 JwtConfig，宽限期相关逻辑不生效） */
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
| `jwtConfig` | JWT 配置（用于宽限期等高级特性；旧构造器传 `null`） |

> `JwtAutoConfiguration` 注册驱动工厂时使用带 `JwtConfig` 的完整构造器，确保宽限期逻辑可用。

### 请求级状态字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `cachedUser` | `Authenticatable` | 当前请求解析出的用户（请求级缓存） |
| `resolved` | `boolean` | 是否已解析过当前请求 |
| `lastToken` | `String` | 最近一次签发的 token（login 或自动续期或宽限期续期产生），供 `token()` 返回 |
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
 * 解析当前请求的用户。流程：
 * 1. 从 Authorization 头提取 Bearer token
 * 2. 校验 token（签名 + 过期 + 黑名单，见 JwtService.validate）
 * 3. 若校验通过，按 subject（主键）通过 UserProvider.retrieveById 取出用户
 * 4. 若 refreshEnabled=true 且 token 已过半 TTL，自动签发新 token 存入 lastToken
 * 5. 若校验未通过但 token 处于宽限期内，允许请求一次：
 *    取出用户、签发新 token、将旧 token 加入黑名单
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
        │
        ├── 1. 正常校验
        │     validate(token) 通过？
        │     ├── requestToken = token
        │     ├── subject = getSubject(token)
        │     ├── cachedUser = provider.retrieveById(subject)
        │     └── 自动续期检查
        │           cachedUser != null && refreshEnabled && shouldRefresh(token)
        │             → lastToken = generate(主键)  // 签发新 token
        │     └── 返回 cachedUser
        │
        └── 2. 宽限期（token 已过期但在宽限期窗口内）
              isInGracePeriod(token) && !isBlacklisted(token)？
              ├── requestToken = token
              ├── subject = getSubjectFromExpired(token)
              ├── cachedUser = provider.retrieveById(subject)
              ├── cachedUser != null？
              │     ├── lastToken = generate(主键)        // 签发新 token
              │     └── jwtService.blacklist(token)        // 旧 token 加入黑名单
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
 * 登出：将当前请求的 token 加入黑名单（需 blacklistEnabled=true），并清理请求级状态。
 * 加入黑名单后，该 token 后续请求无法通过校验。
 * 当 blacklistEnabled=false 时仅清理请求级状态（标准 JWT 无登出踢 token 能力）。
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
 * 包括：login() 签发的 token，或 user() 自动续期/宽限期续期签发的新 token。
 * 若本次请求既未登录也未触发续期，返回 null（客户端继续使用原有 token 即可）。
 *
 * JwtTokenResponseFilter 会在请求结束时自动将此 token 写入响应 header。
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

## JwtTokenResponseFilter

JWT token 响应过滤器，继承 Spring 的 `OncePerRequestFilter`。在请求处理完成后，检查当前 JWT 守卫是否签发了新 token（自动续期或宽限期续期）。如果有，将新 token 写入响应 header（默认 `X-New-Token`），客户端可在响应头中获取新 token。

### 典型场景

- **自动续期**：token 已过半 TTL，请求正常处理，响应头携带新 token；
- **宽限期续期**：token 已过期但在宽限期内，请求正常处理，响应头携带新 token，旧 token 被黑名单。

### 工作流程

```
请求进入
  │
  ▼
JwtTokenResponseFilter.doFilterInternal()
  ├── filterChain.doFilter(request, response)  ← 先执行业务逻辑
  │     （期间 JwtGuard.user() 可能签发新 token 存入 lastToken）
  │
  └── 请求处理完成后
        ├── authManager.guard("jwt") → 取出 JwtGuard
        ├── newToken = jwtGuard.token()
        └── newToken 非空？
              └── response.setHeader(graceHeader, newToken)
                  （默认响应头名 X-New-Token）
```

### 构造器

```java
public JwtTokenResponseFilter(JwtConfig jwtConfig, AuthManager authManager)
```

| 参数 | 说明 |
|---|---|
| `jwtConfig` | JWT 配置（用于获取 `graceHeader` 响应头名称） |
| `authManager` | 认证管理器（用于取出当前请求的 JwtGuard） |

### 设计说明

- 该过滤器在 `filterChain.doFilter()` **之后**执行 header 写入，确保业务逻辑已完整执行。
- 通过 `authManager.guard("jwt")` 获取当前请求的守卫实例（ThreadLocal 隔离），取 `token()` 即最近一次签发的新 token。
- 若取守卫时抛异常（如当前请求未走 jwt 守卫），仅记录 debug 日志，不影响响应。
- 该 Bean 由 `JwtAutoConfiguration` 自动装配，应用层无需手动注册。

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
    private boolean blacklistEnabled = false;              // 黑名单，默认关闭
    private String blacklistStore = "array";
    private String blacklistPrefix = "jwt:blacklist:";
    private long gracePeriodSeconds = 0;                   // 宽限期秒数，默认关闭
    private String graceHeader = "X-New-Token";            // 新 token 响应头名

    // 标准 getter/setter ...
}
```

该属性类与 `JwtConfig` 字段一一对应，由 `JwtAutoConfiguration` 转换为 `JwtConfig` Bean。

---

## JwtAutoConfiguration

JWT 自动装配类，注册 `JwtConfig`、`JwtService`、`JwtTokenResponseFilter` Bean，并通过 `AuthManager.registerGuardDriver` 将 jwt 驱动插件式注册到 `AuthManager`。

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

    @Bean
    @ConditionalOnMissingBean
    public JwtTokenResponseFilter jwtTokenResponseFilter(JwtConfig jwtConfig, AuthManager authManager) { ... }

    @Override
    public void afterSingletonsInstantiated() {
        // 所有单例 Bean 就绪后，注册 jwt 守卫工厂到 AuthManager
        authManager.registerGuardDriver("jwt",
            (name, provider, config) -> new JwtGuard(name, provider, jwtService, refreshEnabled, jwtConfig));
    }
}
```

### 装配条件

- Servlet Web 应用环境
- classpath 同时存在 `AuthManager` 与 `JwtService` 类

### 关键设计

#### 1. 黑名单缓存 store 获取（按需）

`jwtService()` Bean 方法根据 `blacklistEnabled` 决定是否获取缓存 store：

- `blacklistEnabled=true`：从 `CacheManager` 按 `JwtConfig.getBlacklistStore()` 指定的名称获取 store。若指定的 store 未注册，回退到默认 store。
- `blacklistEnabled=false`：`blacklistStore` 传 `null`，`JwtService` 表现为标准 JWT，不读写缓存。

```java
CacheStore blacklistStore = null;
if (jwtConfig.isBlacklistEnabled()) {
    try {
        blacklistStore = cacheManager.store(jwtConfig.getBlacklistStore());
    } catch (IllegalStateException e) {
        // 指定的 store 未注册，回退到默认 store
        blacklistStore = cacheManager.store();
    }
}
return new JwtService(jwtConfig, blacklistStore);
```

#### 2. JwtTokenResponseFilter 装配

`jwtTokenResponseFilter()` Bean 方法注入 `JwtConfig` 与 `AuthManager`，构造响应过滤器。该过滤器在请求结束后自动将新 token 写入响应 header（自动续期或宽限期续期场景）。

#### 3. 延迟注册驱动工厂

`JwtService` 由本类的 `@Bean` 方法产生，因此不能在字段上 `@Autowired` 自身产生的 Bean（Spring 6 默认禁止循环引用）。这里改为在 `afterSingletonsInstantiated()` 中通过 `ApplicationContext` 获取，该回调在所有单例 Bean 就绪后才执行，天然避免循环依赖。

工厂创建 `JwtGuard` 时传入 `JwtConfig`（带完整构造器），控制是否启用自动续期并支持宽限期逻辑。

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

    # 黑名单（登出踢 token），默认 false（标准 JWT，不依赖缓存）
    # 开启后需配合 cache 模块使用；多实例部署建议用 redis 等共享缓存
    blacklist-enabled: false

    # 黑名单缓存 store，默认 array（内存）
    # 仅当 blacklist-enabled=true 时生效
    # 单机部署可用 array；多实例部署应使用 file 或 redis 等共享缓存
    blacklist-store: array

    # 黑名单缓存键前缀
    blacklist-prefix: "jwt:blacklist:"

    # 宽限期秒数，默认 0（关闭）
    # 需 blacklist-enabled=true 才生效；过期 token 在宽限期内仍可请求一次，
    # 响应头携带新 token，旧 token 被加入黑名单
    grace-period-seconds: 0

    # 宽限期/自动续期时新 token 写入响应 header 的名称，默认 X-New-Token
    grace-header: X-New-Token
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
| `jaravel.jwt.blacklist-enabled` | boolean | `false` | 是否启用黑名单（登出踢 token） |
| `jaravel.jwt.blacklist-store` | String | `array` | 黑名单缓存 store 名称（仅黑名单开启时生效） |
| `jaravel.jwt.blacklist-prefix` | String | `jwt:blacklist:` | 黑名单缓存键前缀 |
| `jaravel.jwt.grace-period-seconds` | long | `0` | 宽限期秒数（需黑名单开启才生效） |
| `jaravel.jwt.grace-header` | String | `X-New-Token` | 新 token 写入响应 header 的名称 |

### 多实例部署配置示例

```yaml
jaravel:
  jwt:
    secret: ${JWT_SECRET}           # 从环境变量读取
    blacklist-enabled: true         # 开启黑名单
    blacklist-store: redis          # 使用 Redis 共享黑名单
    refresh-enabled: true
    grace-period-seconds: 30        # 30 秒宽限期，过期 token 无感续期
```

---

## 黑名单功能

### 用途

标准 JWT 一旦签发，在过期前无法主动失效。黑名单功能用于在服务端主动踢掉 token，适用于：

- 用户主动登出，要求旧 token 立即失效；
- 用户修改密码，要求已签发的 token 失效；
- 管理员封禁/注销账号，强制下线；
- 检测到 token 被盗用，紧急吊销。

### 配置方法

```yaml
jaravel:
  jwt:
    blacklist-enabled: true       # 开启黑名单
    blacklist-store: redis        # 缓存 store（单机可用 array，多实例用 redis）
    blacklist-prefix: "jwt:blacklist:"
```

开启后：

- `JwtService.blacklist(token)` 将 token 写入 `CacheStore`，TTL 为 token 剩余有效期；
- `JwtService.isBlacklisted(token)` 校验 token 是否已注销；
- `JwtService.validate(token)` 自动拒绝黑名单 token；
- `JwtGuard.logout()` 调用 `blacklist()` 实现登出踢 token。

### 与 cache 模块的关系

黑名单功能**依赖** cache 模块提供的 `CacheStore`：

- `blacklistEnabled=false`（默认）：不读写缓存，`JwtAutoConfiguration` 向 `JwtService` 传入 `null` 的 `blacklistStore`，表现为标准 JWT。
- `blacklistEnabled=true`：`JwtAutoConfiguration` 从 `CacheManager` 按 `blacklist-store` 名称获取 store 注入 `JwtService`。

| 缓存 store | 适用场景 | 说明 |
|---|---|---|
| `array`（内存） | 单机部署 | 登出立即生效，重启后丢失 |
| `file` | 多实例部署 | 文件共享 |
| `redis` | 多实例部署（推荐） | 共享黑名单，性能好 |
| `database` | 多实例部署 | 持久化 |

### 黑名单条目生命周期

黑名单条目的 TTL 设为 token 剩余有效期（秒），token 自然过期后黑名单条目自动清除，避免黑名单无限膨胀。对于宽限期场景下已过期的 token，TTL 设为宽限期剩余秒数。

---

## 宽限期功能

### 用途

宽限期模式解决 access token 过期瞬间的"硬中断"问题：token 一过期就返回 401，导致用户体验中断。开启宽限期后，过期 token 在宽限期内仍可请求一次，服务端在响应 header 中返回新 token，客户端可无感续期。

### 前置条件

宽限期功能需要同时满足：

- `blacklist-enabled=true`（黑名单必须开启）
- `grace-period-seconds>0`（宽限期大于 0 秒）

> **为什么需要黑名单？** 宽限期允许过期 token 再用一次，用完后必须将旧 token 加入黑名单，否则同一个过期 token 可被无限复用。因此宽限期依赖黑名单机制。

### 配置方法

```yaml
jaravel:
  jwt:
    blacklist-enabled: true         # 必须开启黑名单
    blacklist-store: redis          # 多实例用 redis
    grace-period-seconds: 30        # 30 秒宽限期
    grace-header: X-New-Token       # 新 token 响应头名（默认）
```

### 工作流程

```
客户端携带已过期的 access token 请求
  │
  ▼
JwtGuard.user()
  ├── validate(token) 失败（token 已过期）
  ├── isInGracePeriod(token)？ → true（过期未超 30 秒）
  ├── isBlacklisted(token)？ → false（尚未使用过宽限期）
  │
  ├── subject = getSubjectFromExpired(token)   ← 从过期 token 取出 subject
  ├── cachedUser = provider.retrieveById(subject)
  ├── lastToken = generate(主键)                ← 签发新 token
  └── jwtService.blacklist(token)               ← 旧 token 加入黑名单
        │
        ▼
请求正常执行，返回业务数据
  │
  ▼
JwtTokenResponseFilter（请求结束后）
  └── response.setHeader("X-New-Token", newToken)   ← 响应头携带新 token
        │
        ▼
客户端从响应头取出 X-New-Token，替换旧 token
后续请求使用新 token（旧 token 已在黑名单，无法再用）
```

### 响应 header

- 新 token 通过 `grace-header` 指定的响应头返回，默认 `X-New-Token`。
- 该 header 由 `JwtTokenResponseFilter` 自动写入，应用层无需手动处理。
- 自动续期（token 过半 TTL 但未过期）场景同样会写入此 header。

### 重要约束

- 宽限期内每个过期 token **仅可使用一次**：使用后立即加入黑名单，第二次请求同一 token 会被 `isBlacklisted()` 拒绝。
- 宽限期只对 **access token** 生效，refresh token 过期需走 `refresh()` 流程。
- 宽限期窗口应设置合理（如 30~60 秒），过大会增加过期 token 被滥用的风险。

---

## 核心流程详解

### 1. Token 自动续期流程

```
客户端请求（携带 access token，未过期但已过半 TTL）
  │
  ▼
JwtGuard.user()
  ├── validate(token) → 通过
  ├── 取出用户
  └── shouldRefresh(token)？
        ├── false → 正常返回用户，token() 返回 null
        └── true  → lastToken = generate(主键)  // 签发新 token
                     │
                     ▼
              JwtTokenResponseFilter 请求结束后：
              response.setHeader("X-New-Token", newToken)
              │
              ▼
        客户端从响应头取新 token，替换旧 token
```

`shouldRefresh` 判断逻辑：当 token 已过其 TTL 的一半时返回 `true`。已过期的 token 返回 `false`（应由 refresh token 换取新 token，或走宽限期）。

### 2. 宽限期续期流程

```
客户端请求（携带已过期的 access token，宽限期内）
  │
  ▼
JwtGuard.user()
  ├── validate(token) → 失败（已过期）
  ├── isInGracePeriod(token) && !isBlacklisted(token) → true
  ├── getSubjectFromExpired(token) → subject
  ├── cachedUser = provider.retrieveById(subject)
  ├── lastToken = generate(主键)        // 签发新 token
  └── blacklist(token)                   // 旧 token 加入黑名单
        │
        ▼
请求正常执行，返回业务数据
  │
  ▼
JwtTokenResponseFilter：response.setHeader("X-New-Token", newToken)
  │
  ▼
客户端用新 token 替换旧 token
（旧 token 已黑名单，再次使用会被拒绝）
```

### 3. Refresh Token 换取流程

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

### 4. 登出流程

```
客户端请求登出（携带 access token）
  │
  ▼
JwtGuard.logout()
  ├── requestToken != null？
  │     └── jwtService.blacklist(requestToken)
  │           ├── blacklistEnabled=false → 空操作（标准 JWT）
  │           └── blacklistEnabled=true
  │                 ├── 计算剩余有效期
  │                 └── blacklistStore.put(prefix + token, "1", 剩余秒数)
  └── 清理请求级状态（cachedUser/lastToken/requestToken = null）
        │
        ▼
后续请求携带同一 token（需 blacklistEnabled=true）：
  JwtGuard.user()
    └── validate(token)
          └── isBlacklisted(token) → true → 返回 false
                └── user() 返回 null → 认证失败
```

黑名单条目的 TTL 设为 token 剩余有效期，token 自然过期后黑名单条目自动清除，避免黑名单无限膨胀。

---

## 完整使用示例

### 1. 配置

#### 标准 JWT 模式（默认，无状态）

```yaml
jaravel:
  auth:
    default-guard: api
  jwt:
    secret: my-production-secret-key-32-bytes-long!!
    ttl: 3600000
    refresh-ttl: 604800000
    refresh-enabled: true
    blacklist-enabled: false        # 标准 JWT，不依赖缓存
```

#### 黑名单 + 宽限期模式

```yaml
jaravel:
  auth:
    default-guard: api
  jwt:
    secret: my-production-secret-key-32-bytes-long!!
    ttl: 3600000
    refresh-ttl: 604800000
    refresh-enabled: true
    blacklist-enabled: true         # 开启黑名单
    blacklist-store: redis          # 多实例共享
    grace-period-seconds: 30        # 30 秒宽限期
    grace-header: X-New-Token
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

### 6. 自动续期 / 宽限期续期（响应头自动处理）

开启 `JwtTokenResponseFilter` 后，自动续期与宽限期续期产生的新 token 会**自动**写入响应 header（默认 `X-New-Token`），应用层通常无需手动处理：

```java
@GetMapping("/api/profile")
public Response profile() {
    User user = (User) Auth.user();
    if (user == null) {
        return ResponseBuilder.error(401, "Unauthorized");
    }
    // 新 token 已由 JwtTokenResponseFilter 自动写入响应头 X-New-Token
    // 客户端从响应头取出即可，无需在此手动处理
    return ResponseBuilder.success(user);
}
```

若需自定义逻辑，仍可手动读取：

```java
@GetMapping("/api/profile")
public Response profile() {
    User user = (User) Auth.user();
    if (user == null) {
        return ResponseBuilder.error(401, "Unauthorized");
    }

    Response response = ResponseBuilder.success(user);

    // 也可手动检查是否签发了新 token（自动续期或宽限期续期）
    String newToken = Auth.token("api");
    if (newToken != null) {
        response.header("X-New-Token", newToken);
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
    Auth.logout("api");  // blacklistEnabled=true 时将当前 token 加入黑名单
    return ResponseBuilder.success("已登出");
}
```

> 当 `blacklistEnabled=false`（标准 JWT）时，`logout()` 仅清理请求级状态，不会真正踢掉 token（标准 JWT 无服务端失效能力）。

---

## 线程安全说明

### 1. JwtService（无状态单例）

本类为**无状态单例**，`config`、`key`、`blacklistStore` 均为构造后不可变字段，可被多线程并发安全调用。

- 所有签发/解析/校验操作均为无状态计算，不持有任何可变状态。
- 黑名单状态全部委托给 `CacheStore`，底层驱动（如 `ArrayCacheDriver` / `FileCacheDriver` / Redis）自身保证线程安全。
- 当 `blacklistEnabled=false` 时 `blacklistStore` 为 `null`，`blacklist()` / `isBlacklisted()` 为空操作，无并发问题。
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

### 3. JwtTokenResponseFilter

该过滤器为无状态单例（`jwtConfig`、`authManager` 为构造后不可变字段），通过 `authManager.guard("jwt")` 获取当前请求的 ThreadLocal 隔离守卫实例，本身不持有可变状态，线程安全。

### 4. 黑名单缓存

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
| `JwtTokenResponseFilter` | 无状态单例 | 不可变字段 + 通过 ThreadLocal 守卫取 token |
| `JwtConfig` | 不可变配置 | 构造后不再修改 |
| 黑名单缓存 | 委托 `CacheStore` | 底层驱动自身线程安全 |

> **关键约束**：`JwtGuard` 实例**必须**通过 `AuthManager.guard(String)` 获取，不可跨请求缓存或共享，否则其内部的可变状态会串态。`JwtService` 与 `JwtTokenResponseFilter` 作为无状态单例可安全地在任意线程共享调用。
