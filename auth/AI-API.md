# auth AI-API Reference

> Module: `auth` | Package: `com.weacsoft.jaravel.vendor.auth` | Version: 0.1.0

## Overview
auth 模块提供 Laravel 风格的认证系统，包含 AuthManager（多守卫管理器）、AuthGuard 契约、SessionGuard（Session 驱动守卫）、UserProvider 契约、Authenticatable 契约、GuardFactory 插件机制、Auth 门面、Authenticate 认证中间件和 AuthLifecycleFilter 生命周期过滤器。支持通过 GuardFactory 插件式扩展新的 Guard 驱动（如 JWT），第三方模块可在不修改 auth 模块的前提下扩展。

## Classes & Interfaces

### AuthManager
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.auth`
- **Description**: 认证管理器，对齐 Laravel AuthManager。维护多个守卫（guard）与用户提供者（provider），按名称解析守卫实例（请求级缓存于 ThreadLocal）。支持通过 registerGuardDriver 插件式注册新的 Guard 驱动。
- **Annotations**: 无（由 AuthAutoConfiguration 注册为 @Bean）

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `setDefaultGuard` | `String name` | `void` | 设置默认守卫名 |
| `getDefaultGuard` | 无 | `String` | 获取默认守卫名 |
| `registerProvider` | `String name, UserProvider provider` | `void` | 注册用户提供者（启动阶段调用） |
| `registerGuard` | `String name, String driver, String providerName` | `void` | 注册守卫配置（启动阶段调用） |
| `registerGuardDriver` | `String driver, GuardFactory factory` | `void` | 注册 Guard 驱动工厂（插件扩展用） |
| `guard` | 无 | `AuthGuard` | 获取默认守卫 |
| `guard` | `String name` | `AuthGuard` | 按名称获取守卫（请求级缓存） |
| `user` | 无 | `Authenticatable` | 获取默认守卫的当前用户 |
| `id` | 无 | `Object` | 获取默认守卫的当前用户 ID |
| `check` | 无 | `boolean` | 默认守卫是否已登录 |
| `guest` | 无 | `boolean` | 默认守卫是否访客 |
| `login` | `Authenticatable user` | `void` | 通过默认守卫登录 |
| `login` | `Authenticatable user, String guardName` | `void` | 通过指定守卫登录 |
| `logout` | 无 | `void` | 登出默认守卫 |
| `logout` | `String guardName` | `void` | 登出指定守卫 |
| `token` | 无 | `String` | 获取默认守卫最近签发的 token |
| `token` | `String guardName` | `String` | 获取指定守卫最近签发的 token |
| `clear` | 无 | `void` | 清理 ThreadLocal（请求结束时调用） |

#### Usage Example
```java
// 应用启动时注册
authManager.registerProvider("users", userProvider);
authManager.registerGuard("web", "session", "users");
authManager.setDefaultGuard("web");

// 请求中使用
Authenticatable user = authManager.user();
boolean isLogin = authManager.check();
authManager.login(user);
authManager.logout();

// 多守卫
authManager.guard("api").check();
authManager.login(user, "api");
```

---

### AuthGuard
- **Type**: interface
- **Package**: `com.weacsoft.jaravel.vendor.auth.contract`
- **Description**: 认证守卫契约，对齐 Laravel Guard。定义登录态检查、用户获取、登录/登出和 token 获取。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `check` | 无 | `boolean` | 是否已登录 |
| `guest` | 无 | `boolean` | 是否访客 |
| `user` | 无 | `Authenticatable` | 当前用户，未登录返回 null |
| `id` | 无 | `Object` | 当前用户 ID（default 方法，从 user() 获取） |
| `login` | `Authenticatable user` | `void` | 登录指定用户 |
| `logout` | 无 | `void` | 登出 |
| `token` | 无 | `String` | 登录后获取签发的 token（default null） |

---

### Authenticatable
- **Type**: interface
- **Package**: `com.weacsoft.jaravel.vendor.auth.contract`
- **Description**: 可认证实体契约，对齐 Laravel Authenticatable。仅承担「以主键标识用户」的职责，不包含密码相关方法。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `getAuthIdentifier` | 无 | `Object` | 获取主键值 |
| `getAuthIdentifierName` | 无 | `String` | 获取主键字段名（default "id"） |
| `getRememberTokenName` | 无 | `String` | 记住我令牌字段名（default "remember_token"） |
| `getRememberToken` | 无 | `String` | 记住我令牌（default null） |
| `setRememberToken` | `String value` | `void` | 设置记住我令牌（default 空实现） |

#### Usage Example
```java
public class User implements Authenticatable {
    private Long id;
    private String name;

    @Override
    public Object getAuthIdentifier() {
        return id;
    }
    // 其他方法使用默认实现
}
```

---

### UserProvider
- **Type**: interface
- **Package**: `com.weacsoft.jaravel.vendor.auth.contract`
- **Description**: 用户提供者契约，对齐 Laravel UserProvider。仅负责按标识/凭证从存储中取出用户，不负责校验密码。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `retrieveById` | `Object identifier` | `Authenticatable` | 按主键取出用户，未找到返回 null |
| `retrieveByCredentials` | `Map<String, Object> credentials` | `Authenticatable` | 按凭证取出用户，未找到返回 null |

#### Usage Example
```java
public class DatabaseUserProvider implements UserProvider {
    @Override
    public Authenticatable retrieveById(Object identifier) {
        return userRepository.findById((Long) identifier);
    }
    @Override
    public Authenticatable retrieveByCredentials(Map<String, Object> credentials) {
        String number = (String) credentials.get("number");
        return userRepository.findByNumber(number);
    }
}
```

---

### GuardFactory
- **Type**: interface (functional)
- **Package**: `com.weacsoft.jaravel.vendor.auth.contract`
- **Description**: 守卫工厂契约，用于插件式注册新的 Guard 驱动。第三方模块通过实现此接口向 AuthManager 注册，即可扩展支持的 guard driver。
- **Annotations**: `@FunctionalInterface`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `create` | `String name, UserProvider provider, Object... config` | `AuthGuard` | 创建守卫实例 |

#### Usage Example
```java
// jwt 模块注册 JWT 驱动
authManager.registerGuardDriver("jwt", (name, provider, config) -> {
    return new JwtGuard(name, provider, jwtService);
});
```

---

### SessionGuard
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.auth.guard`
- **Description**: Session 守卫，对齐 Laravel SessionGuard。登录态写入 HTTP Session，用户信息按需通过 UserProvider 取出并缓存于当前线程。
- **Implements**: `AuthGuard`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `SessionGuard` | `String name, UserProvider provider` | 构造方法 | 创建 Session 守卫 |
| `check` | 无 | `boolean` | 是否已登录 |
| `guest` | 无 | `boolean` | 是否访客 |
| `user` | 无 | `Authenticatable` | 获取当前用户（从 Session 取 ID 后查 provider） |
| `login` | `Authenticatable user` | `void` | 登录（写入 Session） |
| `logout` | 无 | `void` | 登出（清除 Session） |

---

### Auth
- **Type**: class (final)
- **Package**: `com.weacsoft.jaravel.vendor.auth.facade`
- **Description**: Auth 门面，对齐 Laravel `Auth::`。所有方法为静态方法，内部通过 Facade 解析 AuthManager。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `check` | 无 | `boolean` | 默认守卫是否已登录 |
| `guest` | 无 | `boolean` | 默认守卫是否访客 |
| `user` | 无 | `Authenticatable` | 获取当前用户 |
| `id` | 无 | `Object` | 获取当前用户 ID |
| `guard` | 无 | `AuthGuard` | 获取默认守卫 |
| `guard` | `String name` | `AuthGuard` | 获取指定守卫 |
| `login` | `Authenticatable user` | `void` | 登录（默认守卫） |
| `login` | `Authenticatable user, String guardName` | `void` | 登录（指定守卫） |
| `logout` | 无 | `void` | 登出（默认守卫） |
| `logout` | `String guardName` | `void` | 登出（指定守卫） |
| `token` | 无 | `String` | 获取默认守卫签发的 token |
| `token` | `String guardName` | `String` | 获取指定守卫签发的 token |

#### Usage Example
```java
// 检查登录
if (Auth.check()) {
    Authenticatable user = Auth.user();
}

// 多守卫用法
Auth.guard("api").login(user);  // JWT 登录
Auth.guard("web").login(user);  // Session 登录
String token = Auth.token("api"); // 获取 JWT token

// 登出
Auth.logout();
Auth.logout("api");
```

---

### AuthContext
- **Type**: class (final)
- **Package**: `com.weacsoft.jaravel.vendor.auth`
- **Description**: 认证上下文，以 ThreadLocal 持有当前请求，供 Guard 读取 session/token。由 AuthLifecycleFilter 在请求开始时设置、结束时清理。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `set` | `Request request` | `void` | 设置当前线程的请求 |
| `get` | 无 | `Request` | 获取当前线程的请求 |
| `clear` | 无 | `void` | 清理当前线程的请求 |

---

### Authenticate
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.auth.middleware`
- **Description**: 认证中间件，对齐 Laravel auth 中间件。支持指定守卫名称（对齐 auth:api 语法）。未登录时 API 请求返回 401 JSON，其它请求重定向到登录页。
- **Implements**: `Middleware`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `Authenticate` | 无 | 构造方法 | 默认守卫，登录页 /login |
| `Authenticate` | `String guard` | 构造方法 | 指定守卫 |
| `Authenticate` | `String guard, String loginPath` | 构造方法 | 指定守卫和登录页 |
| `handle` | `Request request, NextFunction next` | `Response` | 执行认证检查 |

#### Usage Example
```java
// 路由中使用
router.get("/admin", handler).middleware(new Authenticate("web"));
router.get("/api/profile", handler).middleware(new Authenticate("api"));
```

---

### AuthLifecycleFilter
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.auth.filter`
- **Description**: 认证生命周期过滤器，每个请求开始时绑定 AuthContext，结束时清理 ThreadLocal，对齐 Laravel 每个请求独立的认证上下文。
- **Extends**: `org.springframework.web.filter.OncePerRequestFilter`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `AuthLifecycleFilter` | `AuthManager authManager` | 构造方法 | 创建过滤器 |
| `doFilterInternal` | `HttpServletRequest, HttpServletResponse, FilterChain` | `void` | 绑定/清理认证上下文（由框架调用） |

---

### AuthAutoConfiguration
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.auth.autoconfigure`
- **Description**: 认证自动装配，注册 AuthManager 和 AuthLifecycleFilter。
- **Annotations**: `@AutoConfiguration`, `@ConditionalOnWebApplication(SERVLET)`, `@ConditionalOnClass(AuthManager.class)`, `@EnableConfigurationProperties(AuthProperties.class)`

#### Bean Methods

| Bean | Return | Description |
|------|--------|-------------|
| `authManager` | `AuthManager` | 创建 AuthManager（@Bean, @ConditionalOnMissingBean） |
| `authLifecycleFilter` | `AuthLifecycleFilter` | 创建认证生命周期过滤器（@Bean, @ConditionalOnMissingBean） |

---

### AuthProperties
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.auth.autoconfigure`
- **Description**: 认证配置属性，前缀 `jaravel.auth`。
- **Annotations**: `@ConfigurationProperties(prefix = "jaravel.auth")`

#### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `defaultGuard` | `String` | `"web"` | 默认守卫名 |

#### Usage Example
```yaml
# application.yml
jaravel:
  auth:
    default-guard: api
```
