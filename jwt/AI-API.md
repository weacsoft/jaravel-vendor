# jwt AI-API Reference

> Module: `jwt` | Package: `com.weacsoft.jaravel.vendor.jwt` | Version: 0.1.0

## Overview
jwt 模块是 JWT 认证插件，对齐 Laravel tymon/jwt-auth。提供 JwtService（token 签发/解析/校验/黑名单/刷新）、JwtGuard（JWT 守卫，从请求头解析 Bearer token 并认证用户）和 JwtConfig（配置）。通过 GuardFactory 插件式注册到 AuthManager，引入 jwt 模块即自动启用 JWT 认证能力。支持 token 自动续期、refresh token 换取新 access token、登出黑名单等特性。

## Classes & Interfaces

### JwtService
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.jwt`
- **Description**: JWT 服务，对齐 tymon/jwt-auth。提供 access token / refresh token 的签发、解析、校验，以及 token 刷新（续期）和登出黑名单功能。无状态单例，可被多线程并发安全调用。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `JwtService` | `JwtConfig config, CacheStore blacklistStore` | 构造方法 | 创建 JWT 服务 |
| `generate` | `String subject` | `String` | 签发 access token |
| `generate` | `String subject, Map<String, Object> claims, long ttl` | `String` | 签发 access token（带自定义声明和 TTL） |
| `generateRefreshToken` | `String subject` | `String` | 签发 refresh token（带 type=refresh 声明） |
| `parse` | `String token` | `Claims` | 解析 token，返回 Claims |
| `validate` | `String token` | `boolean` | 校验 token 是否有效（签名+未过期+不在黑名单） |
| `getSubject` | `String token` | `String` | 获取 token 的 subject（主键） |
| `isExpired` | `String token` | `boolean` | 判断 token 是否已过期 |
| `getIssuedAt` | `String token` | `Date` | 获取 token 签发时间 |
| `getExpiration` | `String token` | `Date` | 获取 token 过期时间 |
| `shouldRefresh` | `String token` | `boolean` | 判断 token 是否应当刷新（已过半 TTL） |
| `refresh` | `String refreshToken` | `String` | 用 refresh token 换取新 access token，失败返回 null |
| `blacklist` | `String token` | `void` | 将 token 加入黑名单（登出时调用） |
| `isBlacklisted` | `String token` | `boolean` | 判断 token 是否在黑名单中 |

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

// 登出黑名单
jwtService.blacklist(accessToken);
```

---

### JwtGuard
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.jwt`
- **Description**: JWT 守卫，对齐 Laravel api guard（jwt 驱动）。从请求头解析 Bearer token，按 subject 取出用户；登录时签发 token 并缓存。支持登出黑名单、自动续期和 refresh token 换取。守卫实例由 AuthManager 通过 ThreadLocal 按请求隔离。
- **Implements**: `AuthGuard`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `JwtGuard` | `String name, UserProvider provider, JwtService jwtService, boolean refreshEnabled` | 构造方法 | 创建 JWT 守卫（指定是否启用自动续期） |
| `JwtGuard` | `String name, UserProvider provider, JwtService jwtService` | 构造方法 | 创建 JWT 守卫（默认启用自动续期） |
| `check` | 无 | `boolean` | 是否已登录 |
| `guest` | 无 | `boolean` | 是否访客 |
| `user` | 无 | `Authenticatable` | 解析当前请求用户（从 Authorization 头提取 Bearer token） |
| `login` | `Authenticatable user` | `void` | 登录（签发 access token） |
| `logout` | 无 | `void` | 登出（将当前 token 加入黑名单） |
| `token` | 无 | `String` | 获取最近一次签发的 access token |
| `refreshToken` | 无 | `String` | 签发 refresh token（登录后调用） |
| `shouldRefresh` | `String token` | `boolean` | 判断 token 是否应当刷新 |
| `refresh` | `String refreshToken` | `String` | 用 refresh token 换取新 access token |
| `getName` | 无 | `String` | 获取守卫名称 |

#### Usage Example
```java
// 通过 Auth 门面使用（推荐）
Auth.guard("api").login(user);           // JWT 登录
String token = Auth.token("api");         // 获取签发的 token
Auth.guard("api").check();               // 检查登录态
Auth.guard("api").logout();              // 登出（token 加入黑名单）

// 直接使用 JwtGuard
JwtGuard guard = (JwtGuard) authManager.guard("api");
guard.login(user);
String accessToken = guard.token();
String refreshToken = guard.refreshToken();
String newToken = guard.refresh(refreshToken);
```

---

### JwtConfig
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.jwt`
- **Description**: JWT 配置，对齐 config/jwt.php。包含 token 签发、刷新和黑名单相关配置。链式 setter。

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
| `blacklistStore` | `String` | `"array"` | 黑名单缓存 store 名称 |
| `blacklistPrefix` | `String` | `"jwt:blacklist:"` | 黑名单缓存键前缀 |

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
| `getBlacklistStore` / `setBlacklistStore` | `String` | `String`/`JwtConfig` | 黑名单 store |
| `getBlacklistPrefix` / `setBlacklistPrefix` | `String` | `String`/`JwtConfig` | 黑名单键前缀 |

#### Usage Example
```java
JwtConfig config = new JwtConfig()
    .setSecret("my-production-secret-key-32bytes")
    .setTtl(7200_000L)        // 2 小时
    .setRefreshTtl(14 * 24 * 3600_000L)  // 14 天
    .setRefreshEnabled(true)
    .setBlacklistStore("redis");
```

---

### JwtAutoConfiguration
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.jwt.autoconfigure`
- **Description**: JWT 自动装配，注册 JwtConfig、JwtService Bean，并通过 GuardFactory 将 jwt 驱动插件式注册到 AuthManager。引入 jwt 模块即自动启用 JWT 认证能力。
- **Annotations**: `@AutoConfiguration`, `@ConditionalOnWebApplication(SERVLET)`, `@ConditionalOnClass({AuthManager.class, JwtService.class})`, `@EnableConfigurationProperties(JwtProperties.class)`
- **Implements**: `SmartInitializingSingleton`

#### Bean Methods

| Bean | Parameters | Return | Description |
|------|-----------|--------|-------------|
| `jwtConfig` | `JwtProperties properties` | `JwtConfig` | 创建 JWT 配置（@Bean, @ConditionalOnMissingBean） |
| `jwtService` | `JwtConfig jwtConfig, CacheManager cacheManager` | `JwtService` | 创建 JWT 服务，注入黑名单缓存 store（@Bean, @ConditionalOnMissingBean） |
| `afterSingletonsInstantiated` | 无 | `void` | 所有单例就绪后注册 jwt 守卫工厂到 AuthManager |

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
| `blacklistStore` | `String` | `"array"` | 黑名单缓存 store |
| `blacklistPrefix` | `String` | `"jwt:blacklist:"` | 黑名单键前缀 |

#### Usage Example
```yaml
# application.yml
jaravel:
  jwt:
    secret: my-production-secret-key-32bytes-long
    ttl: 7200000          # 2 小时
    refresh-ttl: 1209600000  # 14 天
    refresh-enabled: true
    blacklist-store: redis
```
