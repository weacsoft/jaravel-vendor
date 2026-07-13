# Auth 模块（认证）

> 包名：`com.weacsoft.jaravel.vendor.auth`
> 对齐 Laravel 特性：`Illuminate\Auth`（AuthManager、Guard、UserProvider、Auth 门面、auth 中间件）

## 目录

- [模块概述](#模块概述)
- [设计原则：retrieveById 不缓存](#设计原则retrievebyid-不缓存)
- [Maven 依赖](#maven-依赖)
- [类总览](#类总览)
- [契约层（contract）](#契约层contract)
  - [Authenticatable](#authenticatable)
  - [AuthGuard](#authguard)
  - [UserProvider](#userprovider)
  - [GuardFactory](#guardfactory)
- [核心层](#核心层)
  - [AuthManager](#authmanager)
  - [AuthContext](#authcontext)
- [守卫实现（guard）](#守卫实现guard)
  - [SessionGuard](#sessionguard)
- [门面（facade）](#门面facade)
  - [Auth](#auth)
- [中间件（middleware）](#中间件middleware)
  - [Authenticate](#authenticate)
- [过滤器（filter）](#过滤器filter)
  - [AuthLifecycleFilter](#authlifecyclefilter)
- [自动装配（autoconfigure）](#自动装配autoconfigure)
  - [AuthAutoConfiguration](#authautoconfiguration)
  - [AuthProperties](#authproperties)
- [配置项（application.yml）](#配置项applicationyml)
- [完整使用示例](#完整使用示例)
- [线程安全说明](#线程安全说明)

---

## 模块概述

Auth 模块是 Jaravel 框架的认证核心，对齐 Laravel 的 `Illuminate\Auth` 体系。它提供了完整的认证基础设施：

- **多守卫（Multi-Guard）**：支持在同一应用中配置多个认证守卫（如 `web` 使用 Session、`api` 使用 JWT），按名称解析。
- **多提供者（Multi-Provider）**：支持注册多个 `UserProvider`，不同守卫可绑定不同提供者。
- **插件式驱动**：通过 `GuardFactory` 机制，第三方模块（如 `jwt` 模块）可在不修改 auth 模块的前提下注册新的 Guard 驱动。
- **请求级隔离**：基于 `ThreadLocal` 实现每请求独立的认证上下文，杜绝线程池复用导致的串态问题。
- **密码校验解耦**：`Authenticatable` 与 `UserProvider` 均不包含密码相关方法，密码校验完全由应用层负责。

### 与 Laravel 的对齐关系

| Laravel | Jaravel Auth 模块 |
|---|---|
| `AuthManager` | `AuthManager` |
| `Illuminate\Contracts\Auth\Authenticatable` | `Authenticatable` |
| `Illuminate\Contracts\Auth\Guard` | `AuthGuard` |
| `Illuminate\Contracts\Auth\UserProvider` | `UserProvider` |
| `SessionGuard` | `SessionGuard` |
| `Auth` 门面 | `Auth` 门面 |
| `auth` 中间件 | `Authenticate` 中间件 |
| `auth:api` 语法 | `new Authenticate("api")` |

### 关键设计决策：密码校验解耦

Laravel 的 `Authenticatable` 接口包含 `getAuthPassword()`，`UserProvider` 包含 `validateCredentials()`。本模块**有意移除**了这两个方法：

- `Authenticatable` 仅承担「以主键标识用户」的职责，**不**包含 `getAuthPassword()`。
- `UserProvider` 仅负责按标识/凭证从存储中**取出**用户，**不**包含 `validateCredentials()`。

认证流程为：应用层通过 query 查出用户 → 在应用代码中校验密码 → `Auth.login(user)` 登入 → `Auth.check()` 以主键校验登录态。密码校验是应用层的责任，不应出现在本契约或 UserProvider 中。

### 关键设计决策：retrieveById 不缓存用户

`UserProvider.retrieveById(Object identifier)` 的实现（如 `EloquentUserProvider`）**每次调用都从数据库查询最新用户**，不做任何跨请求缓存。

**原因**：用户数据可能被其他模块或请求修改（如管理员修改角色权限、用户更新资料），如果缓存用户对象，后续请求将使用过期数据，导致权限检查、数据展示等出现不一致。

**请求级缓存是合理的**：Guard 层（`SessionGuard`、`JwtGuard`）在同一请求内会缓存 `cachedUser`（通过 `AuthManager` 的 ThreadLocal 隔离），避免同一请求内多次调用 `user()` 重复查库。请求结束后由 `AuthLifecycleFilter` 清理 ThreadLocal，下次请求重新查库。

**如需缓存用户对象**：请使用 `model-cache` 模块在 Model 层手动开启缓存，而非在认证层缓存。但**不建议缓存 User 模型**，因为用户数据需要实时性。

---

## Maven 依赖

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>auth</artifactId>
    <version>0.1.2</version>
</dependency>
```

该模块传递依赖 `core` 与 `http` 模块，并依赖 Spring Boot 自动装配能力。

---

## 类总览

```
com.weacsoft.jaravel.vendor.auth
├── AuthManager              # 认证管理器：多守卫/多提供者/ThreadLocal 请求级隔离
├── AuthContext              # 认证上下文：ThreadLocal 持有当前请求
├── contract
│   ├── Authenticatable      # 可认证实体契约（仅主键标识，无密码方法）
│   ├── AuthGuard            # 认证守卫契约
│   ├── UserProvider         # 用户提供者契约（仅取出用户，无密码校验）
│   └── GuardFactory         # 守卫工厂契约（插件式驱动注册）
├── facade
│   └── Auth                 # Auth 门面（静态 API）
├── guard
│   └── SessionGuard         # Session 守卫实现
├── middleware
│   └── Authenticate         # 认证中间件（支持守卫名称参数）
├── filter
│   └── AuthLifecycleFilter  # 认证生命周期过滤器
└── autoconfigure
    ├── AuthAutoConfiguration # Spring Boot 自动装配
    └── AuthProperties        # 配置属性（jaravel.auth.*）
```

---

## 契约层（contract）

### Authenticatable

可认证实体契约，对齐 Laravel `Illuminate\Contracts\Auth\Authenticatable`。

**核心设计**：仅承担「以主键标识用户」的职责，**不**包含 `getAuthPassword()` 等密码相关方法。

```java
public interface Authenticatable {

    /** 主键值，Auth 比对一般只用主键进行比对 */
    Object getAuthIdentifier();

    /** 主键字段名，如 "id"（默认 "id"） */
    default String getAuthIdentifierName() {
        return "id";
    }

    /** 记住我令牌字段名（默认 "remember_token"） */
    default String getRememberTokenName() {
        return "remember_token";
    }

    /** 记住我令牌，未启用时返回 null */
    default String getRememberToken() {
        return null;
    }

    /** 设置记住我令牌，未启用时为空实现 */
    default void setRememberToken(String value) {
    }
}
```

**方法说明**：

| 方法 | 说明 |
|---|---|
| `getAuthIdentifier()` | 返回用户主键值，`Auth.check()` / `Auth.user()` 通过主键比对登录态 |
| `getAuthIdentifierName()` | 返回主键字段名，默认 `"id"` |
| `getRememberTokenName()` | 返回「记住我」令牌字段名，默认 `"remember_token"` |
| `getRememberToken()` | 返回「记住我」令牌，未启用返回 `null` |
| `setRememberToken(String)` | 设置「记住我」令牌，未启用为空实现 |

**实现示例**：

```java
import com.weacsoft.jaravel.vendor.auth.contract.Authenticatable;

public class User implements Authenticatable {
    private Long id;
    private String name;
    private String password; // 密码字段由应用自行管理，不在契约中

    @Override
    public Object getAuthIdentifier() {
        return id;
    }

    @Override
    public String getAuthIdentifierName() {
        return "id";
    }

    // getter/setter ...
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPassword() { return password; }
}
```

---

### AuthGuard

认证守卫契约，对齐 Laravel `Guard`。定义守卫的标准行为。

```java
public interface AuthGuard {

    /** 是否已登录 */
    boolean check();

    /** 是否访客（未登录） */
    boolean guest();

    /** 当前用户，未登录返回 null */
    Authenticatable user();

    /** 当前用户 id，未登录返回 null（默认实现） */
    default Object id() {
        Authenticatable u = user();
        return u == null ? null : u.getAuthIdentifier();
    }

    /** 登录指定用户 */
    void login(Authenticatable user);

    /** 登出 */
    void logout();

    /**
     * 登录后获取签发的 token（仅对支持 token 的守卫有效，如 JWT 守卫）。
     * 默认返回 null，由具体守卫按需覆盖。
     */
    default String token() {
        return null;
    }
}
```

**方法说明**：

| 方法 | 说明 |
|---|---|
| `check()` | 返回当前是否已登录（`user() != null`） |
| `guest()` | 返回当前是否为访客（`!check()`） |
| `user()` | 返回当前已认证用户，未登录返回 `null` |
| `id()` | 返回当前用户主键，未登录返回 `null` |
| `login(Authenticatable)` | 登入指定用户（写入 session / 签发 token） |
| `logout()` | 登出（清除 session / 加入黑名单） |
| `token()` | 登录后获取签发的 token，不支持 token 的守卫返回 `null` |

---

### UserProvider

用户提供者契约，对齐 Laravel `UserProvider`。

**核心设计**：仅负责按标识/凭证从存储中**取出**用户，**不**负责校验密码，因此**不**包含 `validateCredentials()` 方法。

```java
public interface UserProvider {

    /**
     * 按主键取出用户，Auth.check() / Auth.user() 通过主键比对登录态时使用。
     *
     * @param identifier 主键值
     * @return 用户实体，未找到返回 null
     */
    Authenticatable retrieveById(Object identifier);

    /**
     * 按凭证（如 number）取出用户，仅用于查询，不校验密码。
     *
     * @param credentials 查询凭证（字段名 -> 值）
     * @return 用户实体，未找到返回 null
     */
    Authenticatable retrieveByCredentials(Map<String, Object> credentials);
}
```

**方法说明**：

| 方法 | 说明 |
|---|---|
| `retrieveById(Object)` | 按主键取出用户，供 `Auth.check()` / `Auth.user()` 通过主键比对登录态时使用 |
| `retrieveByCredentials(Map)` | 按凭证（如工号、邮箱）查出用户，**仅用于查询，不校验密码** |

**典型用法**：

```java
// 查出用户后，由应用层自行比对密码
User user = (User) provider.retrieveByCredentials(Map.of("number", "1001"));
if (user == null || !encoder.matches(inputPassword, user.getPassword())) {
    throw new RuntimeException("工号或密码错误");
}
Auth.login(user);
```

---

### GuardFactory

守卫工厂契约，用于插件式注册新的 Guard 驱动。这是一个 `@FunctionalInterface`。

第三方模块（如 `jwt` 模块）通过实现此接口并向 `AuthManager` 注册，即可扩展 AuthManager 支持的 guard driver，无需修改 auth 模块本身。

```java
@FunctionalInterface
public interface GuardFactory {

    /**
     * 创建守卫实例。
     *
     * @param name     守卫名称
     * @param provider 用户提供者
     * @param config  额外配置（由具体驱动解释，可为空）
     * @return 守卫实例
     */
    AuthGuard create(String name, UserProvider provider, Object... config);
}
```

**注册示例**（jwt 模块在自动装配时调用）：

```java
authManager.registerGuardDriver("jwt", (name, provider, config) ->
    new JwtGuard(name, provider, jwtService));
```

---

## 核心层

### AuthManager

认证管理器，对齐 Laravel `AuthManager`。维护多个守卫（guard）与用户提供者（provider），按名称解析守卫实例（请求级缓存于 ThreadLocal）。

**核心特性**：
- 多守卫、多提供者管理
- 插件式 Guard 驱动注册（`registerGuardDriver`）
- ThreadLocal 请求级守卫实例隔离
- ConcurrentHashMap 注册表保证并发安全

#### 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `guards` | `ConcurrentHashMap<String, GuardConfig>` | 守卫配置：name -> {driver, providerName}，进程级共享，启动后只读 |
| `providers` | `ConcurrentHashMap<String, UserProvider>` | 提供者：name -> UserProvider，进程级共享，启动后只读 |
| `driverFactories` | `ConcurrentHashMap<String, GuardFactory>` | 插件式驱动工厂：driver(lowercase) -> GuardFactory，进程级共享 |
| `current` | `ThreadLocal<Map<String, AuthGuard>>` | 请求级守卫实例：name -> AuthGuard，每线程独立，请求结束清理 |
| `defaultGuard` | `volatile String` | 默认守卫名，启动阶段设置后不再变更，`volatile` 保证可见性 |

#### 方法文档

**注册方法（应用启动阶段调用）**：

```java
/** 注册用户提供者 */
public void registerProvider(String name, UserProvider provider)

/** 注册守卫 */
public void registerGuard(String name, String driver, String providerName)

/**
 * 注册 Guard 驱动工厂，允许插件模块扩展支持的 driver 类型。
 * @param driver  驱动名称（不区分大小写）
 * @param factory 守卫工厂
 */
public void registerGuardDriver(String driver, GuardFactory factory)
```

示例：

```java
// 注册用户提供者
authManager.registerProvider("users", new EloquentUserProvider(userModel, "number"));

// 注册 session 守卫
authManager.registerGuard("web", "session", "users");

// 注册 jwt 守卫（由 jwt 模块自动装配完成）
authManager.registerGuard("api", "jwt", "users");

// 插件式注册 jwt 驱动工厂
authManager.registerGuardDriver("jwt", (name, provider, config) ->
    new JwtGuard(name, provider, jwtService));
```

**默认守卫管理**：

```java
public void setDefaultGuard(String name)   // 设置默认守卫
public String getDefaultGuard()            // 获取默认守卫（默认 "web"）
```

**守卫获取（请求级缓存）**：

```java
/** 获取默认守卫 */
public AuthGuard guard()

/**
 * 按名称获取守卫（请求级缓存）。
 * 守卫实例缓存在当前线程的 ThreadLocal 中，同一请求内重复调用
 * guard("api") 返回同一实例，保证单次请求内一致。
 * 不同请求（即使复用同一线程）因 clear() 清理而获得全新实例。
 */
public AuthGuard guard(String name)
```

示例：

```java
// 同一请求内多次获取返回同一实例
AuthGuard g1 = authManager.guard("api");
AuthGuard g2 = authManager.guard("api");
assert g1 == g2; // true
```

**便捷方法（作用于默认守卫）**：

```java
public Authenticatable user()                          // 当前用户
public Object id()                                     // 当前用户 id
public boolean check()                                 // 是否已登录
public boolean guest()                                 // 是否访客
public void login(Authenticatable user)                // 登录（默认守卫）
public void login(Authenticatable user, String guard)  // 登录（指定守卫）
public void logout()                                   // 登出（默认守卫）
public void logout(String guardName)                   // 登出（指定守卫）
public String token()                                  // 最近签发的 token（默认守卫）
public String token(String guardName)                  // 最近签发的 token（指定守卫）
```

**清理方法**：

```java
/**
 * 请求结束时清理 ThreadLocal，防止线程池复用导致的串态。
 * 由 AuthLifecycleFilter 在 finally 中调用。
 */
public void clear()
```

#### 守卫创建逻辑

`createGuard(name)` 的解析顺序：

1. 从 `guards` 注册表查找守卫配置（driver + providerName）
2. 从 `providers` 注册表查找对应的 UserProvider
3. 若 driver 为 `"session"`（不区分大小写），创建内置 `SessionGuard`
4. 否则从 `driverFactories` 查找插件式驱动工厂（如 `"jwt"`），调用工厂创建
5. 均未命中则抛出 `IllegalStateException`

---

### AuthContext

认证上下文：以 ThreadLocal 持有当前请求，供 Guard 读取 session / token。由 `AuthLifecycleFilter` 在请求开始时设置、结束时清理。

```java
public final class AuthContext {

    private static final ThreadLocal<Request> CURRENT = new ThreadLocal<>();

    /** 请求开始时设置当前请求 */
    public static void set(Request request)

    /** 获取当前请求（供 Guard 读取 session / token） */
    public static Request get()

    /** 请求结束时清理 ThreadLocal */
    public static void clear()
}
```

该类为工具类，构造器私有，不可实例化。所有方法均为静态方法。

---

## 守卫实现（guard）

### SessionGuard

Session 守卫，对齐 Laravel 的 `SessionGuard`。登录态写入 HTTP Session，用户信息按需通过 `UserProvider` 取出并缓存于当前线程。

#### 构造器

```java
public SessionGuard(String name, UserProvider provider)
```

| 参数 | 说明 |
|---|---|
| `name` | 守卫名称，用于生成 session key（`login_{name}_id`） |
| `provider` | 用户提供者，用于按主键取出用户 |

#### 方法文档

| 方法 | 说明 |
|---|---|
| `check()` | 是否已登录（`user() != null`） |
| `guest()` | 是否访客（`!check()`） |
| `user()` | 从 session 读取主键，通过 `provider.retrieveById()` 取出用户并缓存。首次调用后缓存结果，同一请求内不再重复查询 |
| `login(Authenticatable)` | 将用户主键写入 HTTP Session，并缓存用户实例 |
| `logout()` | 从 session 移除登录标记，清理缓存 |

#### Session Key 规则

每个守卫使用独立的 session key，格式为 `login_{守卫名}_id`，例如守卫名为 `web` 时 key 为 `login_web_id`。

#### user() 解析流程

```
user()
  ├── 已解析过？ → 返回缓存
  └── 首次解析
        ├── 获取 HttpSession（不创建新 session）
        ├── 读取 session 中的主键 (login_{name}_id)
        ├── 主键为 null？ → 返回 null
        └── provider.retrieveById(主键) → 缓存并返回
```

#### login() 流程

```java
public void login(Authenticatable user) {
    cachedUser = user;
    resolved = true;
    // 将主键写入 session（getSession(true) 会创建新 session）
    HttpSession session = servlet.getSession(true);
    session.setAttribute(sessionKey(), user.getAuthIdentifier());
}
```

---

## 门面（facade）

### Auth

Auth 门面，对齐 Laravel `Auth::`。提供静态方法访问认证功能，内部通过 `Facade.resolve(AuthManager.class)` 获取 `AuthManager` 单例。

```java
public final class Auth {

    // 状态查询
    public static boolean check()                         // 是否已登录（默认守卫）
    public static boolean guest()                         // 是否访客（默认守卫）
    public static Authenticatable user()                  // 当前用户（默认守卫）
    public static Object id()                             // 当前用户 id（默认守卫）

    // 守卫获取
    public static AuthGuard guard()                       // 获取默认守卫
    public static AuthGuard guard(String name)            // 获取指定守卫

    // 登录/登出
    public static void login(Authenticatable user)        // 登录（默认守卫）
    public static void login(Authenticatable user, String guardName) // 登录（指定守卫）
    public static void logout()                           // 登出（默认守卫）
    public static void logout(String guardName)           // 登出（指定守卫）

    // Token
    public static String token()                          // 最近签发的 token（默认守卫）
    public static String token(String guardName)          // 最近签发的 token（指定守卫）
}
```

#### 使用示例

```java
import static com.weacsoft.jaravel.vendor.auth.facade.Auth.*;

// 检查登录态
if (Auth.check()) {
    Authenticatable user = Auth.user();
    System.out.println("当前用户 ID: " + user.getAuthIdentifier());
}

// 多 guard 用法
Auth.guard("api").login(user);   // 通过 api guard（JWT）登录
Auth.guard("web").login(user);   // 通过 web guard（Session）登录
Auth.guard("api").check();       // 检查 api guard 登录态
Auth.guard("web").check();       // 检查 web guard 登录态

// 登出指定 guard
Auth.logout("api");

// JWT 登录后获取 token
Auth.login(user, "api");
String token = Auth.token("api"); // 获取签发的 access token
```

---

## 中间件（middleware）

### Authenticate

认证中间件，对齐 Laravel 的 `auth` 中间件。支持指定守卫名称，对齐 Laravel 的 `auth:api`、`auth:web` 语法。未登录时分三种情况处理：Wire 请求返回 401 JSON（含 `redirect` 字段）/ API 请求返回 401 JSON / 其它请求 302 重定向到登录页。

#### 构造器

```java
public Authenticate()                          // 使用默认守卫，登录页 /login
public Authenticate(String guard)              // 指定守卫，登录页 /login
public Authenticate(String guard, String loginPath) // 指定守卫 + 登录页
```

#### 使用示例

```java
// 对齐 Laravel auth 中间件（默认守卫）
router.get("/dashboard", handler).middleware(new Authenticate());

// 对齐 Laravel auth:web 语法
router.get("/admin", handler).middleware(new Authenticate("web"));

// 对齐 Laravel auth:api 语法
router.get("/api/profile", handler).middleware(new Authenticate("api"));

// 自定义登录页
router.get("/portal", handler).middleware(new Authenticate("web", "/portal/login"));

// Wire 请求路由（前端 wire.js 通过 X-Wire-Request 头发起请求）
// 未认证时中间件返回 401 JSON（含 redirect 字段），wire.js 自动跳转登录页
router.post("/api/wire/demo", handler).middleware(new Authenticate());
```

#### 未登录处理逻辑

中间件根据请求类型分三种情况决定响应方式：

```
未登录时：
  ├── 指定了守卫？ → Auth.guard(guard).check()
  └── 未指定？     → Auth.check()
  ├── 已认证 → next.apply(request)
  └── 未认证
        ├── Wire 请求（X-Wire-Request: true）
        │     → 返回 401 JSON: {"code":401, "message":"Unauthorized", "redirect":"/login"}
        ├── API 请求（Accept/Content-Type 含 application/json，或路径以 /api 开头）
        │     → 返回 401 JSON: {"code":401, "message":"Unauthorized"}
        └── 其它请求
              → 302 重定向到登录页（默认 /login）
```

#### Wire 请求认证过期无感重定向

当用户在浏览页面期间认证过期（如 Session 失效），传统做法下后续的 AJAX 请求会收到 401，前端通常只能整体刷新页面跳转登录，出现白屏。Authenticate 中间件对 **Wire 请求**做了特殊处理，配合前端 `wire.js` 实现「无感重定向」：

- **识别方式**：Wire 请求通过自定义请求头 `X-Wire-Request: true` 标识。前端 `wire.js` 在发起请求时会自动带上该头。
- **响应内容**：未认证时返回 401 JSON，其中额外包含 `redirect` 字段指向登录页：

  ```json
  {
    "code": 401,
    "message": "Unauthorized",
    "redirect": "/login"
  }
  ```

- **前端跳转**：前端 `wire.js` 收到该响应后，自动跳转到 `/login?redirect=当前页面URL`，将当前页面地址作为回跳参数。
- **登录后回跳**：用户在登录页完成登录后，根据 `redirect` 参数回到之前的页面，整个过程不出现白屏，体验上接近 SPA 的路由切换。

> Wire 请求响应中的 `redirect` 字段取自 Authenticate 构造方法中配置的 `loginPath`（默认 `/login`）。若通过 `new Authenticate("web", "/portal/login")` 自定义了登录页，则 `redirect` 字段值同步变为 `/portal/login`。

---

## 过滤器（filter）

### AuthLifecycleFilter

认证生命周期过滤器，继承 Spring 的 `OncePerRequestFilter`。每个请求开始时绑定 `AuthContext`，结束时清理 ThreadLocal，对齐 Laravel 每个请求独立的认证上下文。

```java
public class AuthLifecycleFilter extends OncePerRequestFilter {

    public AuthLifecycleFilter(AuthManager authManager)

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) {
        try {
            Request req = new Request();
            req.setRequest(request);
            AuthContext.set(req);           // 绑定当前请求到 ThreadLocal
            filterChain.doFilter(request, response);
        } finally {
            AuthContext.clear();            // 清理请求上下文
            authManager.clear();            // 清理守卫实例缓存
        }
    }
}
```

**职责**：
1. 请求开始时创建 `Request` 对象并绑定到 `AuthContext`（ThreadLocal），供 Guard 读取 session / token。
2. 请求结束时（`finally` 块）清理 `AuthContext` 和 `AuthManager` 的 ThreadLocal，防止线程池复用导致的串态。

该过滤器由 `AuthAutoConfiguration` 自动注册为 Spring Bean。

---

## 自动装配（autoconfigure）

### AuthAutoConfiguration

Spring Boot 自动装配类，注册 `AuthManager` 与 `AuthLifecycleFilter`。

```java
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(AuthManager.class)
@EnableConfigurationProperties(AuthProperties.class)
public class AuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuthManager authManager(AuthProperties properties) {
        AuthManager manager = new AuthManager();
        manager.setDefaultGuard(properties.getDefaultGuard());
        return manager;
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthLifecycleFilter authLifecycleFilter(AuthManager authManager) {
        return new AuthLifecycleFilter(authManager);
    }
}
```

**装配条件**：
- Servlet Web 应用环境
- classpath 存在 `AuthManager` 类

**说明**：JWT 等扩展驱动由独立插件模块（如 `jwt` 模块）通过 `AuthManager.registerGuardDriver()` 自行注册，auth 模块本身不包含 JWT 实现。认证中间件 `Authenticate` 为普通 `Middleware` 实现，可直接传入 `Router.middleware()` 使用，无需别名注册。

---

### AuthProperties

认证配置属性，前缀 `jaravel.auth`。

```java
@ConfigurationProperties(prefix = "jaravel.auth")
public class AuthProperties {

    /** 默认守卫名（默认 "web"） */
    private final String defaultGuard = "web";

    // getter/setter ...
}
```

> JWT 相关配置已移至独立 jwt 模块的 `JwtProperties`（前缀 `jaravel.jwt`）。

---

## 配置项（application.yml）

```yaml
jaravel:
  auth:
    # 默认守卫名，未指定守卫时 Auth.check() / Auth.user() 等便捷方法作用于此守卫
    # 内置支持 "session" 驱动；引入 jwt 模块后还支持 "jwt" 驱动
    default-guard: web
```

| 配置项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `jaravel.auth.default-guard` | String | `web` | 默认守卫名称 |

### 完整多守卫配置示例

守卫与提供者的注册通常在应用启动时通过 `@Configuration` 完成：

```yaml
jaravel:
  auth:
    default-guard: api   # 默认使用 api（JWT）守卫
```

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
            // 注册提供者
            authManager.registerProvider("users", userProvider);
            // 注册 session 守卫
            authManager.registerGuard("web", "session", "users");
            // 注册 jwt 守卫（jwt 驱动由 jwt 模块自动注册）
            authManager.registerGuard("api", "jwt", "users");
        };
    }
}
```

---

## 完整使用示例

### 1. 定义用户实体

```java
import com.weacsoft.jaravel.vendor.auth.contract.Authenticatable;

public class User implements Authenticatable {
    private Long id;
    private String number;
    private String password;

    @Override
    public Object getAuthIdentifier() {
        return id;
    }

    // getter/setter ...
}
```

### 2. 注册提供者与守卫

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
            authManager.registerGuard("web", "session", "users");
        };
    }
}
```

### 3. 登录（密码校验由应用层负责）

```java
@PostMapping("/login")
public Response login(@RequestBody LoginRequest req, UserProvider provider) {
    // 1. 按凭证查出用户
    User user = (User) provider.retrieveByCredentials(Map.of("number", req.getNumber()));
    // 2. 应用层校验密码
    if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
        return ResponseBuilder.error(401, "工号或密码错误");
    }
    // 3. 登入（Auth 以主键比对，不涉及密码）
    Auth.login(user);
    return ResponseBuilder.success("登录成功");
}
```

### 4. 路由保护

```java
// 使用中间件保护路由
router.get("/dashboard", this::dashboard)
      .middleware(new Authenticate());          // 默认守卫

router.get("/admin", this::admin)
      .middleware(new Authenticate("web"));     // 指定 web 守卫
```

### 5. 获取当前用户

```java
@GetMapping("/profile")
public Response profile() {
    if (Auth.check()) {
        User user = (User) Auth.user();
        return ResponseBuilder.success(user);
    }
    return ResponseBuilder.error(401, "未登录");
}
```

### 6. 登出

```java
@PostMapping("/logout")
public Response logout() {
    Auth.logout();
    return ResponseBuilder.success("已登出");
}
```

---

## 线程安全说明

Auth 模块在设计上充分考虑了并发场景，核心线程安全策略如下：

### 1. 注册表（guards / providers / driverFactories）

使用 `ConcurrentHashMap`，支持并发读写。

- **注册阶段**：应用启动时由 ServiceProvider 调用 `registerProvider` / `registerGuard` / `registerGuardDriver`。
- **运行阶段**：请求线程调用 `guard(name)`。
- 两者可安全并发。注册表本身是进程级共享的不可变配置（启动后不再修改），`ConcurrentHashMap` 保证可见性与原子性。

### 2. 请求级守卫实例（current）

使用 `ThreadLocal<Map<String, AuthGuard>>`，每个请求线程持有独立的 `Map<String, AuthGuard>`。

- `AuthGuard` 实例（如 `SessionGuard`、`JwtGuard`）中缓存的 `cachedUser`、`resolved`、`lastToken` 等可变状态天然按请求隔离，**不会**跨请求共享。
- 请求结束时由 `AuthLifecycleFilter` 调用 `AuthManager.clear()` 清理 ThreadLocal，防止线程池复用导致的串态。

### 3. defaultGuard

启动阶段设置后不再变更，使用 `volatile` 修饰保证多线程可见性。

### 4. AuthContext

使用 `ThreadLocal<Request>` 持有当前请求，每请求独立，请求结束由 `AuthLifecycleFilter` 清理。

### 关键约束

> `AuthGuard` 实例**必须**通过 `AuthManager.guard(String)` 获取，不可跨请求缓存或共享，否则其内部的可变状态会串态。

| 组件 | 隔离机制 | 生命周期 |
|---|---|---|
| `guards` / `providers` / `driverFactories` | `ConcurrentHashMap` | 进程级，启动后只读 |
| `current`（守卫实例缓存） | `ThreadLocal` | 请求级，请求结束清理 |
| `defaultGuard` | `volatile` | 进程级，启动后不变 |
| `AuthContext`（当前请求） | `ThreadLocal` | 请求级，请求结束清理 |
| `SessionGuard` 可变状态 | ThreadLocal 隔离 | 请求级 |
