# jwt AI-API Reference

> Module: `jwt` | Package: `com.weacsoft.jaravel.vendor.jwt` | Version: 0.1.1

## Overview
jwt 模块是 JWT 认证插件，对齐 Laravel tymon/jwt-auth。提供 JwtService（token 签发/解析/校验/黑名单/刷新/宽限期）、JwtGuard（JWT 守卫，从请求头解析 Bearer token 并认证用户）、JwtConfig（配置）与 JwtTokenResponseFilter（响应过滤器，自动将新 token 写入响应 header）。通过 GuardFactory 插件式注册到 AuthManager，引入 jwt 模块即自动启用 JWT 认证能力。

模块支持三种运行模式：
- **标准 JWT 模式**（默认，`blacklistEnabled=false`）：仅校验签名与过期，不依赖缓存模块，`blacklist()` 为空操作。
- **黑名单模式**（`blacklistEnabled=true`）：登出踢 token 功能生效，使用 cache 模块的 CacheStore。
- **宽限期模式**（`blacklistEnabled=true` 且 `gracePeriodSeconds>0`）：过期 token 在宽限期内仍可请求一次，响应 header 携带新 token，旧 token 被黑名单。

支持 token 自动续期、refresh token 换取新 access token、登出黑名单、宽限期无感续期等特性。

## Classes & Interfaces

### JwtService
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.jwt`
- **Description**: JWT 服务，对齐 tymon/jwt-auth。提供 access token / refresh token 的签发、解析、校验，以及 token 刷新（续期）、登出黑名单与宽限期判断功能。无状态单例，可被多线程并发安全调用。
- **Blacklist Switch**: 当 `JwtConfig.isBlacklistEnabled()` 为 `false`（默认）时，本类表现为标准 JWT：仅校验签名与过期，不依赖任何缓存。`blacklist()` 为空操作，`isBlacklisted()` 始终返回 `false`。开启后才真正读写 `CacheStore`。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `JwtService` | `JwtConfig config, CacheStore blacklistStore` | 构造方法 | 创建 JWT 服务；`blacklistEnabled=false` 时 `blacklistStore` 可传 `null` |
| `generate` | `String subject` | `String` | 签发 access token |
| `generate` | `String subject, Map<String, Object> claims, long ttl` | `String` | 签发 access token（带自定义声明和 TTL） |
| `generateRefreshToken` | `String subject` | `String` | 签发 refresh token（带 type=refresh 声明） |
| `parse` | `String token` | `Claims` | 解析 token，返回 Claims（过期抛 ExpiredJwtException） |
| `validate` | `String token` | `boolean` | 校验 token 是否有效（签名+未过期+不在黑名单）；黑名单关闭时仅校验签名与过期 |
| `getSubject` | `String token` | `String` | 获取 token 的 subject（主键） |
| `getSubjectFromExpired` | `String token` | `String` | 从可能已过期的 token 中获取 subject（宽限期场景用），无法解析返回 null |
| `isExpired` | `String token` | `boolean` | 判断 token 是否已过期 |
| `getIssuedAt` | `String token` | `Date` | 获取 token 签发时间 |
| `getExpiration` | `String token` | `Date` | 获取 token 过期时间 |
| `shouldRefresh` | `String token` | `boolean` | 判断 token 是否应当刷新（已过半 TTL） |
| `refresh` | `String refreshToken` | `String` | 用 refresh token 换取新 access token，失败返回 null |
| `isInGracePeriod` | `String token` | `boolean` | 判断 token 是否处于宽限期内（已过期但未超 gracePeriodSeconds）；`gracePeriodSeconds<=0` 时返回 false |
| `blacklist` | `String token` | `void` | 将 token 加入黑名单（登出或宽限期使用后调用）；`blacklistEnabled=false` 时为空操作 |
| `isBlacklisted` | `String token` | `boolean` | 判断 token 是否在黑名单中；`blacklistEnabled=false` 时始终返回 false |
| `removeFromBlacklist` | `String token` | `void` | 从黑名单移除 token（误杀恢复）；`blacklistEnabled=false` 时为空操作。移除后 token 在有效期内可再次通过 `validate` 校验 |

#### Method Details

##### `getSubjectFromExpired(String token)`
从可能已过期的 token 中获取 subject。用于宽限期场景：token 已过期但需要取出 subject 以查询用户。

```
getSubjectFromExpired(token)
  ├── parse(token) 成功 → 返回 subject
  ├── 抛出 ExpiredJwtException → 从异常的 Claims 中取 subject 返回
  └── 其他异常 → 返回 null
```

##### `isInGracePeriod(String token)`
判断 token 是否处于宽限期内。宽限期是指 token 已过期，但距离过期时间未超过 `gracePeriodSeconds` 秒。在此期间，token 仍可用于请求一次（由 JwtGuard 处理），请求成功后会签发新 token 并将旧 token 加入黑名单。

```
isInGracePeriod(token)
  ├── gracePeriodSeconds <= 0 → false（宽限期关闭）
  ├── parse(token) 成功（未过期） → false
  ├── 抛出 ExpiredJwtException
  │     └── now < expiredAt + gracePeriodSeconds * 1000 → true（在宽限期内）
  └── 其他异常 → false
```

##### `blacklist(String token)`
将 token 加入黑名单（登出或宽限期使用后调用）。当 `blacklistEnabled=false` 时为空操作。黑名单条目的 TTL 设为 token 剩余有效期（秒），token 自然过期后黑名单条目自动清除。对于已过期的 token（宽限期场景），TTL 设为宽限期剩余秒数。

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

##### `isBlacklisted(String token)`
判断 token 是否在黑名单中（已登出或宽限期已使用）。当 `blacklistEnabled=false` 时始终返回 `false`。

#### Usage Example
```java
// 签发 token
String accessToken = jwtService.generate("user:123");
String refreshToken = jwtService.generateRefreshToken("user:123");

// 校验
if (jwtService.validate(accessToken)) {
    String subject = jwtService.getSubject(accessToken);
}

// 刷新
if (jwtService.shouldRefresh(accessToken)) {
    String newToken = jwtService.refresh(refreshToken);
}

// 宽限期判断（过期 token 仍可使用一次）
if (jwtService.isInGracePeriod(expiredToken)) {
    String subject = jwtService.getSubjectFromExpired(expiredToken);
    // 取出用户后签发新 token，并将旧 token 加入黑名单
}

// 登出黑名单（需 blacklistEnabled=true）
jwtService.blacklist(accessToken);
```

---

### JwtGuard
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.jwt`
- **Description**: JWT 守卫，对齐 Laravel api guard（jwt 驱动）。从请求头解析 Bearer token，按 subject 取出用户；登录时签发 token 并缓存。支持登出黑名单（需 `blacklistEnabled=true`）、自动续期、宽限期续期（需 `blacklistEnabled=true` 且 `gracePeriodSeconds>0`）和 refresh token 换取。守卫实例由 AuthManager 通过 ThreadLocal 按请求隔离。
- **Implements**: `AuthGuard`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `JwtGuard` | `String name, UserProvider provider, JwtService jwtService, boolean refreshEnabled, JwtConfig jwtConfig` | 构造方法 | 创建 JWT 守卫（完整构造器，带 JwtConfig，支持宽限期等高级特性） |
| `JwtGuard` | `String name, UserProvider provider, JwtService jwtService, boolean refreshEnabled` | 构造方法 | 创建 JWT 守卫（旧构造器，不带 JwtConfig，宽限期逻辑不生效） |
| `JwtGuard` | `String name, UserProvider provider, JwtService jwtService` | 构造方法 | 创建 JWT 守卫（兼容旧构造器，默认启用自动续期） |
| `check` | 无 | `boolean` | 是否已登录 |
| `guest` | 无 | `boolean` | 是否访客 |
| `user` | 无 | `Authenticatable` | 解析当前请求用户（从 Authorization 头提取 Bearer token；支持宽限期续期） |
| `login` | `Authenticatable user` | `void` | 登录（签发 access token） |
| `logout` | 无 | `void` | 登出（`blacklistEnabled=true` 时将当前 token 加入黑名单；标准 JWT 仅清理状态） |
| `token` | 无 | `String` | 获取最近一次签发的 access token（login/自动续期/宽限期续期产生） |
| `refreshToken` | 无 | `String` | 签发 refresh token（登录后调用） |
| `shouldRefresh` | `String token` | `boolean` | 判断 token 是否应当刷新 |
| `refresh` | `String refreshToken` | `String` | 用 refresh token 换取新 access token |
| `getName` | 无 | `String` | 获取守卫名称 |

#### Constructor Details

##### `JwtGuard(String name, UserProvider provider, JwtService jwtService, boolean refreshEnabled, JwtConfig jwtConfig)`
完整构造器，注入 `JwtConfig`，使宽限期相关逻辑可用。`JwtAutoConfiguration` 注册驱动工厂时使用此构造器。

| Parameter | Description |
|-----------|-------------|
| `name` | 守卫名称 |
| `provider` | 用户提供者 |
| `jwtService` | JWT 服务 |
| `refreshEnabled` | 是否启用自动续期 |
| `jwtConfig` | JWT 配置（用于宽限期等高级特性） |

##### `JwtGuard(String name, UserProvider provider, JwtService jwtService, boolean refreshEnabled)`
旧构造器，`jwtConfig` 内部传 `null`，宽限期相关逻辑不生效。

##### `JwtGuard(String name, UserProvider provider, JwtService jwtService)`
兼容旧构造器，默认启用自动续期（`refreshEnabled=true`），`jwtConfig` 为 `null`。

#### `user()` Method Details

`user()` 解析当前请求的用户，流程：

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

#### Usage Example
```java
// 通过 Auth 门面使用（推荐）
Auth.guard("api").login(user);           // JWT 登录
String token = Auth.token("api");         // 获取签发的 token
Auth.guard("api").check();               // 检查登录态
Auth.guard("api").logout();              // 登出（blacklistEnabled=true 时 token 加入黑名单）

// 直接使用 JwtGuard
JwtGuard guard = (JwtGuard) authManager.guard("api");
guard.login(user);
String accessToken = guard.token();
String refreshToken = guard.refreshToken();
String newToken = guard.refresh(refreshToken);

// 宽限期续期由 user() 自动处理，新 token 通过 JwtTokenResponseFilter 写入响应头
// 应用层通常无需手动处理，客户端从响应头 X-New-Token 取新 token
```

---

### JwtTokenResponseFilter
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.jwt`
- **Description**: JWT token 响应过滤器，继承 Spring 的 `OncePerRequestFilter`。在请求处理完成后，检查当前 JWT 守卫是否签发了新 token（自动续期或宽限期续期）。如果有，将新 token 写入响应 header（默认 `X-New-Token`），客户端可在响应头中获取新 token。
- **Extends**: `org.springframework.web.filter.OncePerRequestFilter`

#### 典型场景
- **自动续期**：token 已过半 TTL，请求正常处理，响应头携带新 token；
- **宽限期续期**：token 已过期但在宽限期内，请求正常处理，响应头携带新 token，旧 token 被黑名单。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `JwtTokenResponseFilter` | `JwtConfig jwtConfig, AuthManager authManager` | 构造方法 | 创建响应过滤器 |
| `doFilterInternal` | `HttpServletRequest request, HttpServletResponse response, FilterChain filterChain` | `void` | 先执行 filterChain，请求完成后将新 token 写入响应 header |

#### Constructor Details

##### `JwtTokenResponseFilter(JwtConfig jwtConfig, AuthManager authManager)`

| Parameter | Description |
|-----------|-------------|
| `jwtConfig` | JWT 配置（用于获取 `graceHeader` 响应头名称） |
| `authManager` | 认证管理器（用于取出当前请求的 JwtGuard） |

#### `doFilterInternal` 工作流程

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

#### 设计说明
- 该过滤器在 `filterChain.doFilter()` **之后**执行 header 写入，确保业务逻辑已完整执行。
- 通过 `authManager.guard("jwt")` 获取当前请求的守卫实例（ThreadLocal 隔离），取 `token()` 即最近一次签发的新 token。
- 若取守卫时抛异常（如当前请求未走 jwt 守卫），仅记录 debug 日志，不影响响应。
- 该 Bean 由 `JwtAutoConfiguration` 自动装配，应用层无需手动注册。

#### Usage Example
```java
// 该过滤器由 JwtAutoConfiguration 自动装配为 Bean，无需手动注册。
// 引入 jwt 模块后即生效：当请求中签发了新 token（自动续期或宽限期续期），
// 响应 header 自动携带新 token（默认 X-New-Token）。

// 客户端从响应头取出新 token：
// String newToken = response.getHeader("X-New-Token");
// if (newToken != null) { /* 用新 token 替换旧 token */ }
```

---

### JwtConfig
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.jwt`
- **Description**: JWT 配置，对齐 config/jwt.php。包含 token 签发、刷新、黑名单与宽限期相关配置。链式 setter。

#### 设计原则
当 `blacklistEnabled` 为 `false`（默认）时，JWT 表现为标准形式——仅校验签名与过期，不依赖任何缓存。开启黑名单后，登出踢 token 功能生效，需配合 cache 模块使用。宽限期功能需要黑名单开启才能工作（因为宽限期结束后需要将旧 token 加入黑名单以防止重复使用）。

#### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `secret` | `String` | `"jaravel-secret-key-..."` | 签名密钥（生产环境务必更换） |
| `issuer` | `String` | `"jaravel"` | 签发者 |
| `ttl` | `long` | `3600000` (1h) | access token 有效期（毫秒） |
| `refreshTtl` | `long` | `604800000` (7d) | refresh token 有效期（毫秒） |
| `header` | `String` | `"Authorization"` | 请求头名 |
| `prefix` | `String` | `"Bearer "` | token 前缀 |
| `refreshEnabled` | `boolean` | `true` | 是否启用 token 自动刷新 |
| `blacklistEnabled` | `boolean` | `false` | 是否启用黑名单（登出踢 token），关闭时为标准 JWT |
| `blacklistStore` | `String` | `"array"` | 黑名单缓存 store 名称（仅 `blacklistEnabled=true` 时生效） |
| `blacklistPrefix` | `String` | `"jwt:blacklist:"` | 黑名单缓存键前缀 |
| `gracePeriodSeconds` | `long` | `0` | 宽限期秒数，0 关闭；需 `blacklistEnabled=true` 才生效 |
| `graceHeader` | `String` | `"X-New-Token"` | 宽限期/自动续期时新 token 写入响应 header 的名称 |

#### Methods (getter/setter 均为链式)

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `getSecret` / `setSecret` | `String` | `String`/`JwtConfig` | 签名密钥 |
| `getIssuer` / `setIssuer` | `String` | `String`/`JwtConfig` | 签发者 |
| `getTtl` / `setTtl` | `long` | `long`/`JwtConfig` | access token TTL |
| `getRefreshTtl` / `setRefreshTtl` | `long` | `long`/`JwtConfig` | refresh token TTL |
| `getHeader` / `setHeader` | `String` | `String`/`JwtConfig` | 请求头名 |
| `getPrefix` / `setPrefix` | `String` | `String`/`JwtConfig` | token 前缀 |
| `isRefreshEnabled` / `setRefreshEnabled` | `boolean` | `boolean`/`JwtConfig` | 自动刷新开关 |
| `isBlacklistEnabled` / `setBlacklistEnabled` | `boolean` | `boolean`/`JwtConfig` | 黑名单开关（默认 false，标准 JWT） |
| `getBlacklistStore` / `setBlacklistStore` | `String` | `String`/`JwtConfig` | 黑名单 store（仅黑名单开启时生效） |
| `getBlacklistPrefix` / `setBlacklistPrefix` | `String` | `String`/`JwtConfig` | 黑名单键前缀 |
| `getGracePeriodSeconds` / `setGracePeriodSeconds` | `long` | `long`/`JwtConfig` | 宽限期秒数（0 关闭） |
| `getGraceHeader` / `setGraceHeader` | `String` | `String`/`JwtConfig` | 新 token 响应头名称 |

#### Usage Example
```java
// 标准 JWT 模式（默认）
JwtConfig standardConfig = new JwtConfig()
    .setSecret("my-production-secret-key-32bytes")
    .setTtl(7200_000L)        // 2 小时
    .setRefreshTtl(14 * 24 * 3600_000L)  // 14 天
    .setRefreshEnabled(true);
    // blacklistEnabled 默认 false，不依赖缓存

// 黑名单 + 宽限期模式
JwtConfig config = new JwtConfig()
    .setSecret("my-production-secret-key-32bytes")
    .setTtl(7200_000L)
    .setRefreshTtl(14 * 24 * 3600_000L)
    .setRefreshEnabled(true)
    .setBlacklistEnabled(true)        // 开启黑名单
    .setBlacklistStore("redis")       // 多实例用 Redis
    .setBlacklistPrefix("jwt:blacklist:")
    .setGracePeriodSeconds(30)        // 30 秒宽限期
    .setGraceHeader("X-New-Token");
```

---

### JwtAutoConfiguration
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.jwt.autoconfigure`
- **Description**: JWT 自动装配，注册 JwtConfig、JwtService、JwtTokenResponseFilter Bean，并通过 GuardFactory 将 jwt 驱动插件式注册到 AuthManager。引入 jwt 模块即自动启用 JWT 认证能力。当 `blacklist-enabled=false`（默认）时，JwtService 表现为标准 JWT，不读写缓存；当 `grace-period-seconds>0` 且黑名单开启时，过期 token 在宽限期内仍可请求一次，JwtTokenResponseFilter 会自动将新 token 写入响应 header。
- **Annotations**: `@AutoConfiguration`, `@ConditionalOnWebApplication(SERVLET)`, `@ConditionalOnClass({AuthManager.class, JwtService.class})`, `@EnableConfigurationProperties(JwtProperties.class)`
- **Implements**: `SmartInitializingSingleton`

#### Bean Methods

| Bean | Parameters | Return | Description |
|------|-----------|--------|-------------|
| `jwtConfig` | `JwtProperties properties` | `JwtConfig` | 创建 JWT 配置（@Bean, @ConditionalOnMissingBean） |
| `jwtService` | `JwtConfig jwtConfig, CacheManager cacheManager` | `JwtService` | 创建 JWT 服务；`blacklistEnabled=true` 时注入黑名单缓存 store，`false` 时传 null（@Bean, @ConditionalOnMissingBean） |
| `jwtTokenResponseFilter` | `JwtConfig jwtConfig, AuthManager authManager` | `JwtTokenResponseFilter` | 创建响应过滤器，自动将新 token 写入响应 header（@Bean, @ConditionalOnMissingBean） |
| `afterSingletonsInstantiated` | 无 | `void` | 所有单例就绪后注册 jwt 守卫工厂到 AuthManager（使用带 JwtConfig 的完整构造器） |

#### Bean Details

##### `jwtService(JwtConfig jwtConfig, CacheManager cacheManager)`
根据 `blacklistEnabled` 决定是否获取缓存 store：

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

##### `jwtTokenResponseFilter(JwtConfig jwtConfig, AuthManager authManager)`
构造响应过滤器，注入 `JwtConfig`（用于获取 `graceHeader`）与 `AuthManager`（用于取出当前请求守卫）。

##### `afterSingletonsInstantiated()`
所有单例 Bean 就绪后注册 jwt 守卫工厂，使用带 `JwtConfig` 的完整构造器：

```java
JwtService jwtService = applicationContext.getBean(JwtService.class);
JwtConfig jwtConfig = applicationContext.getBean(JwtConfig.class);
boolean refreshEnabled = jwtConfig.isRefreshEnabled();
authManager.registerGuardDriver("jwt",
    (name, provider, config) -> new JwtGuard(name, provider, jwtService, refreshEnabled, jwtConfig));
```

---

### JwtProperties
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.jwt.autoconfigure`
- **Description**: JWT 配置属性，前缀 `jaravel.jwt`。
- **Annotations**: `@ConfigurationProperties(prefix = "jaravel.jwt")`

#### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `secret` | `String` | `"jaravel-secret-key-..."` | 签名密钥 |
| `ttl` | `long` | `3600000` | access token 有效期（毫秒） |
| `refreshTtl` | `long` | `604800000` | refresh token 有效期（毫秒） |
| `header` | `String` | `"Authorization"` | 请求头名 |
| `prefix` | `String` | `"Bearer "` | token 前缀 |
| `refreshEnabled` | `boolean` | `true` | 是否启用自动刷新 |
| `blacklistEnabled` | `boolean` | `false` | 是否启用黑名单（登出踢 token），默认关闭 |
| `blacklistStore` | `String` | `"array"` | 黑名单缓存 store（仅黑名单开启时生效） |
| `blacklistPrefix` | `String` | `"jwt:blacklist:"` | 黑名单键前缀 |
| `gracePeriodSeconds` | `long` | `0` | 宽限期秒数，默认 0 关闭（需黑名单开启才生效） |
| `graceHeader` | `String` | `"X-New-Token"` | 宽限期/自动续期时新 token 响应头名称 |

#### Usage Example
```yaml
# application.yml
# 标准 JWT 模式（默认）
jaravel:
  jwt:
    secret: my-production-secret-key-32bytes-long
    ttl: 7200000          # 2 小时
    refresh-ttl: 1209600000  # 14 天
    refresh-enabled: true
    blacklist-enabled: false   # 标准 JWT，不依赖缓存

# 黑名单 + 宽限期模式
jaravel:
  jwt:
    secret: my-production-secret-key-32bytes-long
    ttl: 7200000
    refresh-ttl: 1209600000
    refresh-enabled: true
    blacklist-enabled: true        # 开启黑名单
    blacklist-store: redis         # 多实例用 Redis
    blacklist-prefix: "jwt:blacklist:"
    grace-period-seconds: 30       # 30 秒宽限期
    grace-header: X-New-Token
```
