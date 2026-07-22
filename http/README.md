# http 模块

> Jaravel-Vendor 的 HTTP 层模块，提供 Laravel 风格的中间件管道、Request / Response 抽象、路由系统与控制器契约。包名统一为 `com.weacsoft.jaravel.vendor.*`（含 `middleware`、`route`、`http.request`、`http.response`、`controller` 子包）。

---

## 目录

- [1. 模块概述](#1-模块概述)
- [2. 依赖信息](#2-依赖信息)
- [3. 类总览](#3-类总览)
- [4. 中间件体系（Middleware）](#4-中间件体系middleware)
  - [4.1 Middleware 接口](#41-middleware-接口)
  - [4.2 TrimStrings](#42-trimstrings)
  - [4.3 ConvertEmptyStringsToNull](#43-convertemptystringstonull)
  - [4.4 EncryptCookies](#44-encryptcookies)
  - [4.5 TrustProxies](#45-trustproxies)
  - [4.6 VerifyCsrfToken](#46-verifycsrftoken)
  - [4.7 中间件别名与类引用（Middleware Alias & Class Resolution）](#47-中间件别名与类引用middleware-alias--class-resolution)
- [5. 请求（Request / RequestFactory）](#5-请求request--requestfactory)
- [6. 响应（Response / ResponseBuilder / RawResponse / JSONResponseResolver）](#6-响应response--responsebuilder--rawresponse--jsonresponseresolver)
- [7. 路由系统（Router / Route / RouteService）](#7-路由系统router--route--routeservice)
- [8. 控制器契约（Controllers / ControllerRegistry / ControllerActionResolver）](#8-控制器契约controllers--controllerregistry--controlleractionresolver)
  - [8.1 Controllers 接口](#81-controllers-接口)
  - [8.2 控制器注册表（ControllerRegistry）](#82-控制器注册表controllerregistry)
  - [8.3 控制器动作解析器（ControllerActionResolver）](#83-控制器动作解析器controlleractionresolver)
  - [8.4 控制器引用（Controller References）](#84-控制器引用controller-references)
  - [8.5 在路由中使用控制器引用](#85-在路由中使用控制器引用)
- [9. 配置选项](#9-配置选项)
- [10. 线程安全说明](#10-线程安全说明)
- [11. 静态资源目录（StaticResource）](#11-静态资源目录staticresource)
  - [11.1 架构](#111-架构)
  - [11.2 使用示例](#112-使用示例)
  - [11.3 多目录回退查找](#113-多目录回退查找)
  - [11.4 配置（StaticResourceProperties）](#114-配置staticresourceproperties)
  - [11.5 MIME 类型](#115-mime-类型)
  - [11.6 路径安全](#116-路径安全)
  - [11.7 Blade 模板 @asset 指令](#117-blade-模板-asset-指令)

---

## 1. 模块概述

`http` 模块对齐 Laravel 的 HTTP 层核心特性：

| Laravel 特性 | http 对应实现 | 说明 |
| --- | --- | --- |
| HTTP Middleware | `Middleware` 接口 + 5 个内置中间件 | 洋葱模型管道，`handle(request, next, params)` |
| `TrimStrings` | `TrimStrings` | 自动裁剪请求参数首尾空白 |
| `ConvertEmptyStringsToNull` | `ConvertEmptyStringsToNull` | 空字符串转 null |
| `EncryptCookies` | `EncryptCookies` | AES/CBC 加解密 Cookie |
| `TrustProxies` | `TrustProxies` | 信任反向代理头 |
| `VerifyCsrfToken` | `VerifyCsrfToken` | CSRF 令牌校验 |
| `Request` | `Request` / `RequestFactory` | Laravel 风格请求对象 |
| `Response` | `Response` / `ResponseBuilder` | 链式响应构建 |
| 路由 | `Router` / `Route` / `RouteService` | 路由注册与分组 |
| 控制器路由 | `ControllerRegistry` / `ControllerActionResolver` + `Router` 控制器引用重载 | 对齐 Laravel `Route::get('/users', 'UserController@index')`，支持字符串与类对象引用 |
| `public` 目录 / `asset()` | `StaticResourceHandler` / `StaticResourceRoute` / `Router.serveStatic()` | 静态资源目录服务，对齐 Laravel `public` 与 `asset()` |

**重要设计原则**：内置中间件**不是** Spring Bean（不再标注 `@Component`），而是普通类。配置通过**继承式**模式完成——子类覆盖 `protected` 方法（如 `except()`、`encryptionKey()`、`trustedProxies()`）自定义行为，而非通过构造器传参。使用者继承预定义中间件后标注 `@MiddlewareAlias` 注解，由 `springboot` 模块通过反射实例化（要求有无参构造器）并注册到全局 `MiddlewareAliasRegistry`，从而以别名、类对象或类名三种方式在路由中引用。预定义中间件本身不标注 `@MiddlewareAlias`，由使用者继承后自行标注。

---

## 2. 依赖信息

### Maven 坐标

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>http</artifactId>
    <version>0.1.2</version>
</dependency>
```

### 传递依赖

| 依赖 | scope | 用途 |
| --- | --- | --- |
| `com.weacsoft:core` | compile | 门面、配置、工具类基础 |
| `org.projectlombok:lombok` | optional | `@Getter` / `@Setter` |
| `jakarta.servlet:jakarta.servlet-api` | provided | Servlet API（Cookie、HttpServletRequest） |
| `org.springframework:spring-webmvc` | compile | `MultipartFile`、`ServerRequest` 等 |
| `com.fasterxml.jackson.core:jackson-databind` | compile | JSON 解析 |

> 运行环境要求：JDK 17+，Spring Boot 3.2.5（Jakarta Servlet）。

---

## 3. 类总览

```
com.weacsoft.jaravel.vendor
├── middleware
│   ├── Middleware                     // 中间件函数式接口
│   ├── MiddlewareAliasRegistry        // 中间件别名注册表（对齐 Laravel $routeMiddleware，支持别名与类双映射）
│   ├── ClassMiddlewareSpec            // 类引用中间件规格（Class + params，内部类）
│   ├── TrimStrings                    // 字符串裁剪（普通类，继承式配置）
│   ├── ConvertEmptyStringsToNull      // 空串转 null（普通类，继承式配置）
│   ├── EncryptCookies                 // Cookie 加解密（普通类，继承式配置）
│   ├── TrustProxies                   // 信任代理（普通类，继承式配置）
│   └── VerifyCsrfToken                // CSRF 校验（普通类，继承式配置）
├── http
│   ├── request
│   │   ├── Request                    // Laravel 风格请求对象
│   │   └── RequestFactory             // 请求构建工厂
│   ├── response
│   │   ├── Response                   // 响应接口
│   │   ├── ResponseBuilder            // 响应构建器（静态工厂）
│   │   └── JSONResponseResolver       // JSON 响应工具
│   └── staticresource
│       ├── StaticResourceProperties   // 静态资源配置属性
│       ├── StaticResourceHandler      // 静态资源处理器（MIME 推断/路径安全/双模式加载）
│       └── StaticResourceRoute        // 静态资源路由（实现 Controllers.Runner）
├── route
│   ├── Router                         // 路由器（注册与分组，支持控制器引用重载）
│   ├── Route                          // 单条路由
│   └── RouteService                   // 路由规范化工具
└── controller
    ├── Controllers                    // 控制器契约（含 Runner 函数式接口）
    ├── ControllerRegistry             // 控制器注册表（全局静态，Class/名称双映射）
    └── ControllerActionResolver       // 控制器动作解析器（字符串/类对象 → Runner，带缓存）
```

---

## 4. 中间件体系（Middleware）

### 4.1 Middleware 接口

`com.weacsoft.jaravel.vendor.middleware.Middleware`

函数式接口，定义中间件契约。采用洋葱模型：中间件可在调用 `next` 前预处理请求，在 `next` 返回后后处理响应。`handle` 的 `String... params` 参数来自中间件别名表达式（如 `auth:api,admin` 解析为 `["api","admin"]`），直接以 `Middleware` 实例使用时该参数为空数组。

```java
@FunctionalInterface
public interface Middleware {
    Response handle(Request request, NextFunction next, String... params);

    @FunctionalInterface
    interface NextFunction {
        Response apply(Request request);
    }
}
```

自定义中间件示例：

```java
@MiddlewareAlias("log")
public class LogMiddleware implements Middleware {
    @Override
    public Response handle(Request request, Middleware.NextFunction next, String... params) {
        long start = System.currentTimeMillis();
        Response response = next.apply(request);   // 调用下一层
        long cost = System.currentTimeMillis() - start;
        System.out.println(request.getRequest().getRequestURI() + " 耗时 " + cost + "ms");
        return response;
    }
}
```

> 中间件**不是** Spring Bean（不标注 `@Component`）。标注 `@MiddlewareAlias` 后，`springboot` 模块在启动时通过反射实例化（要求有无参构造器）并注册到全局 `MiddlewareAliasRegistry`，路由中即可用别名、类对象或类名引用。未标注 `@MiddlewareAlias` 的中间件视为用户自建，框架不予扫描注册，需手动 `register`。

### 4.2 TrimStrings

`com.weacsoft.jaravel.vendor.middleware.TrimStrings`

对齐 Laravel `TrimStrings`。自动裁剪 query 与 input 参数中字符串值的首尾空白。普通类，**继承式配置**——通过覆盖 `protected` 方法自定义行为，而非构造器传参。

| 可配置方法 | 默认值 | 说明 |
| --- | --- | --- |
| `protected String[] except()` | `new String[0]`（空数组） | 不裁剪的字段名数组，子类可覆盖 |

| 方法 | 说明 |
| --- | --- |
| `Response handle(Request, NextFunction, String...)` | 裁剪 query 与 input 后传递给下一层 |
| `protected String[] except()` | 返回排除字段数组（默认空），子类覆盖以自定义 |
| `protected void trimQueryParameters(Request)` | 裁剪 query 参数 |
| `protected void trimInputParameters(Request)` | 裁剪 input 参数 |
| `protected boolean isExcluded(String key)` | 是否在排除列表中 |
| `protected Object trimValue(Object value)` | 裁剪单个值（支持 String / List / String[]） |

裁剪逻辑：对 `String` 调用 `trim()`；对 `List` 逐元素裁剪；对 `String[]` 流式裁剪；其它类型原样返回。

预定义中间件不标注 `@MiddlewareAlias`，使用者继承后自行标注：

```java
import com.weacsoft.jaravel.vendor.springboot.annotation.MiddlewareAlias;

// 默认排除（空数组）
@MiddlewareAlias("trim")
public class AppTrimStrings extends TrimStrings { }

// 覆盖 except() 自定义排除字段
@MiddlewareAlias("trim")
public class AppTrimStrings extends TrimStrings {
    @Override
    protected String[] except() {
        return new String[]{"password", "password_confirmation"};
    }
}

// 路由中引用
router.get("/users", action).middleware("trim");
// 或
router.get("/users", action).middleware(AppTrimStrings.class);
```

### 4.3 ConvertEmptyStringsToNull

`com.weacsoft.jaravel.vendor.middleware.ConvertEmptyStringsToNull`

对齐 Laravel `ConvertEmptyStringsToNull`。将 input 与 query 中的空字符串转为 `null`。普通类，**继承式配置**——通过覆盖 `protected` 方法自定义行为，而非构造器传参。

| 可配置方法 | 默认值 | 说明 |
| --- | --- | --- |
| `protected String[] except()` | `{"password", "password_confirmation", "current_password"}` | 不转换的字段名数组，子类可覆盖 |

| 方法 | 说明 |
| --- | --- |
| `Response handle(Request, NextFunction, String...)` | 转换空串后传递给下一层 |
| `protected String[] except()` | 返回排除字段数组（默认排除密码类字段），子类覆盖以自定义 |
| `private boolean isExcluded(String name)` | 是否在排除列表中（大小写不敏感） |

预定义中间件不标注 `@MiddlewareAlias`，使用者继承后自行标注：

```java
import com.weacsoft.jaravel.vendor.springboot.annotation.MiddlewareAlias;

// 默认排除 password 类字段
@MiddlewareAlias("nullEmpty")
public class AppConvertEmptyStringsToNull extends ConvertEmptyStringsToNull { }

// 覆盖 except() 自定义排除
@MiddlewareAlias("nullEmpty")
public class AppConvertEmptyStringsToNull extends ConvertEmptyStringsToNull {
    @Override
    protected String[] except() {
        return new String[]{"password", "remark"};
    }
}
```

### 4.4 EncryptCookies

`com.weacsoft.jaravel.vendor.middleware.EncryptCookies`

对齐 Laravel `EncryptCookies`。使用 AES/CBC/PKCS5Padding 加解密 Cookie。请求阶段解密入站 Cookie，响应阶段加密出站 Cookie。普通类，**继承式配置**——通过覆盖 `protected` 方法自定义密钥与排除列表，而非构造器传参。

| 可配置方法 | 默认值 | 说明 |
| --- | --- | --- |
| `protected String encryptionKey()` | `"default-encryption-key-32bytes"`（仅用于演示） | 加密密钥，子类可覆盖指定安全密钥 |
| `protected String[] except()` | `new String[0]`（空数组） | 不加密的 Cookie 名数组，子类可覆盖 |

| 方法 | 说明 |
| --- | --- |
| `Response handle(Request, NextFunction, String...)` | 先解密请求 Cookie，执行下一层，再加密响应 Cookie |
| `protected String encryptionKey()` | 返回加密密钥（默认演示密钥），子类覆盖以指定安全密钥 |
| `protected String[] except()` | 返回排除 Cookie 名数组（默认空），子类覆盖以自定义 |
| `protected void decryptCookies(Request)` | 解密请求中的 Cookie |
| `protected void encryptCookies(Response)` | 加密响应中的 Cookie |
| `protected String encrypt(String value)` | AES 加密，返回 Base64（IV 前置） |
| `protected String decrypt(String encryptedValue)` | AES 解密 |
| `protected SecretKeySpec generateKey()` | 生成 32 字节 AES 密钥 |
| `protected IvParameterSpec generateIv()` | 生成 16 字节 IV（全 0） |
| `protected boolean isExcluded(String cookieName)` | 是否在排除列表中 |

加解密格式：密文 = Base64( IV(16字节) + 加密内容 )。解密失败时保留原值（不抛异常）。

预定义中间件不标注 `@MiddlewareAlias`，使用者继承后自行标注：

```java
import com.weacsoft.jaravel.vendor.springboot.annotation.MiddlewareAlias;

// 生产环境务必覆盖 encryptionKey() 指定强密钥
@MiddlewareAlias("encryptCookies")
public class AppEncryptCookies extends EncryptCookies {
    @Override
    protected String encryptionKey() {
        return "my-super-secret-key-32bytes!";
    }

    @Override
    protected String[] except() {
        return new String[]{"XSRF-TOKEN"};
    }
}
```

> 安全提示：默认密钥仅用于演示。生产环境必须覆盖 `encryptionKey()` 指定安全密钥（建议 32 字节）。

### 4.5 TrustProxies

`com.weacsoft.jaravel.vendor.middleware.TrustProxies`

对齐 Laravel `TrustProxies`。当请求来自受信任的代理时，从 `X-Forwarded-*` 等头中还原真实客户端信息。普通类，**继承式配置**——通过覆盖 `protected` 方法自定义行为，而非构造器传参。

| 可配置方法 | 默认值 | 说明 |
| --- | --- | --- |
| `protected List<String> trustedProxies()` | `Arrays.asList("127.0.0.1", "::1")` | 受信任的代理 IP 列表，子类可覆盖 |

| 方法 | 说明 |
| --- | --- |
| `Response handle(Request, NextFunction, String...)` | 若来自受信任代理则设置真实头，再传递给下一层 |
| `protected List<String> trustedProxies()` | 返回信任代理 IP 列表（默认本机），子类覆盖以自定义 |
| `protected boolean isTrustedProxy(Request)` | 判断请求来源是否为受信任代理 |
| `protected void setTrustedHeaders(Request)` | 从转发头提取真实信息写入 request attribute |

处理的转发头与写入的 attribute：

| 转发头 | attribute key | 说明 |
| --- | --- | --- |
| `X-Forwarded-For` | `real_ip` | 取第一个 IP |
| `X-Real-IP` | `real_ip` | 覆盖为真实 IP |
| `X-Forwarded-Proto` | `real_scheme` | 真实协议（http/https） |
| `X-Forwarded-Host` | `real_host` | 真实主机名 |
| `X-Forwarded-Port` | `real_port` | 真实端口 |

预定义中间件不标注 `@MiddlewareAlias`，使用者继承后自行标注：

```java
import com.weacsoft.jaravel.vendor.springboot.annotation.MiddlewareAlias;

@MiddlewareAlias("trustProxies")
public class AppTrustProxies extends TrustProxies {
    @Override
    protected List<String> trustedProxies() {
        return Arrays.asList("127.0.0.1", "10.0.0.1", "::1");
    }
}
```

### 4.6 VerifyCsrfToken

`com.weacsoft.jaravel.vendor.middleware.VerifyCsrfToken`

对齐 Laravel `VerifyCsrfToken`。对非安全方法（非 GET/HEAD/OPTIONS/TRACE）的请求校验 CSRF 令牌。普通类，**继承式配置**——通过覆盖 `protected` 方法自定义行为，而非构造器传参。

| 常量 | 值 | 说明 |
| --- | --- | --- |
| `CSRF_TOKEN_COOKIE_NAME` | `XSRF-TOKEN` | CSRF Cookie 名 |
| `CSRF_TOKEN_HEADER_NAME` | `X-XSRF-TOKEN` | CSRF 请求头名 |
| `CSRF_TOKEN_INPUT_NAME` | `_token` | 表单字段名 |
| `CSRF_SESSION_KEY` | `csrf_token` | Session 中存储 token 的 key |
| `SAFE_METHODS` | GET, HEAD, OPTIONS, TRACE | 不校验 CSRF 的安全方法 |

| 可配置方法 | 默认值 | 说明 |
| --- | --- | --- |
| `protected String[] except()` | `new String[0]`（空数组） | 不校验 CSRF 的 URI 数组，子类可覆盖 |

| 方法 | 说明 |
| --- | --- |
| `Response handle(Request, NextFunction, String...)` | 安全校验或排除则放行并附加 token Cookie；否则校验 token |
| `protected String[] except()` | 返回排除 URI 数组（默认空），子类覆盖以自定义 |
| `protected boolean isSafeMethod(String method)` | 是否为安全方法 |
| `protected boolean isExcluded(Request)` | URI 是否在排除列表中 |
| `protected boolean verifyCsrfToken(Request)` | 校验 session token 与请求 token 是否一致 |
| `protected String getSessionToken(Request)` | 获取/生成 session 中的 CSRF token |
| `protected String getRequestToken(Request)` | 从头/表单/Cookie 中提取请求 token |
| `protected void addCsrfTokenCookie(Request, Response)` | 向响应附加 XSRF-TOKEN Cookie |
| `protected String generateToken()` | 用 SecureRandom 生成 32 字节 Base64URL token |

token 查找顺序：`X-XSRF-TOKEN` 请求头 -> `_token` 表单字段 -> `XSRF-TOKEN` Cookie。校验失败抛 `RuntimeException("CSRF token validation failed")`。

预定义中间件不标注 `@MiddlewareAlias`，使用者继承后自行标注：

```java
import com.weacsoft.jaravel.vendor.springboot.annotation.MiddlewareAlias;

@MiddlewareAlias("csrf")
public class AppVerifyCsrfToken extends VerifyCsrfToken {
    @Override
    protected String[] except() {
        return new String[]{"/api/webhook"};
    }
}
```

### 4.7 中间件别名与类引用（Middleware Alias & Class Resolution）

`com.weacsoft.jaravel.vendor.http.middleware.MiddlewareAliasRegistry`

对齐 Laravel `$routeMiddleware` 别名机制，并在此基础上扩展了基于 Class 的中间件引用。开发者既可将中间件注册为字符串别名，也可直接通过 `Class` 对象或类名引用中间件——无需在路由处手动 `new` 中间件实例。

注册表内部维护 **两张映射表**：

- `Map<String, Middleware>` —— 别名 → `Middleware` 实例
- `Map<Class<?>, Middleware>` —— Class → `Middleware` 实例

`resolve()` 在解析别名/类名表达式时，会把表达式中冒号后的参数（如 `auth:api,admin` 解析为 `["api","admin"]`）通过闭包烘焙（bake）到返回的 `Middleware` 包装实例中：

```java
(request, next, ignored) -> original.handle(request, next, bakedParams)
```

即包装后的中间件在执行时忽略运行时传入的 `params`，直接以解析阶段烘焙好的参数调用原始中间件的 `handle`。

| 类 | 职责 |
| --- | --- |
| `MiddlewareAliasRegistry` | 别名注册表，维护别名→`Middleware` 与 Class→`Middleware` 双映射；提供全局静态实例 `getGlobal()` |
| `ClassMiddlewareSpec` | 类引用中间件规格，封装 `Class` + `params`，由 `Route.middleware(Class, String...)` / `Router.middleware(Class, String...)` 内部构造 |

#### 三种中间件引用模式

`Route` 与 `Router` 的 `middleware(...)` 方法支持以下三种引用模式，解析时均通过全局 `MiddlewareAliasRegistry`：

| 模式 | 调用方式 | 说明 |
| --- | --- | --- |
| **别名** | `middleware("auth:api")` | 字符串别名 + 参数；需通过 `@MiddlewareAlias("auth")` 或 `register(alias, mw)` 注册别名 |
| **Class 对象** | `middleware(AuthMiddleware.class)` 或 `middleware(AuthMiddleware.class, "api", "admin")` | `Class` 对象 + 可选参数；需先注册 Class 映射 |
| **类名字符串** | `middleware("AuthMiddleware:api")` | 类名字符串，语法与别名一致（冒号分隔参数） |

> Class 对象与类名字符串两种模式均依赖注册表中已注册的 Class 映射。当 `register(alias, middleware)` 被调用时会**自动**同时注册 Class 映射，因此通过别名注册过的中间件也可直接以 Class 方式引用。

#### 别名 / 类名表达式语法

与 Laravel 一致，冒号分隔标识（别名或类名）与参数，逗号分隔多个参数：

| 表达式 | 标识 | 参数 | 说明 |
| --- | --- | --- | --- |
| `"auth"` | `auth` | `[]` | 无参数 |
| `"auth:api"` | `auth` | `["api"]` | 单参数 |
| `"auth:api,admin"` | `auth` | `["api", "admin"]` | 多参数 |
| `"AuthMiddleware:api"` | `AuthMiddleware` | `["api"]` | 类名 + 参数 |

标识两端的空格会被自动裁剪（`trim`）。`resolve(String expression)` 解析时**先查别名表**；若别名未命中，则回退到类名查找（依次尝试简单名与全限定名），命中则按 Class 解析；两者均未注册时抛出 `IllegalArgumentException`。

#### 注册中间件

`MiddlewareAliasRegistry` 提供两种注册方式：

1. **`register(String alias, Middleware middleware)`** —— 按别名注册。调用时**自动**同时注册 Class 映射；若 `alias` 为 `null` 或空字符串，则**仅**注册 Class 映射（不写入别名表）。
2. **`register(Middleware middleware)`** —— 仅按 Class 注册（不设别名），仅写入 Class→`Middleware` 表。

注册的中间件在引用时其 `handle(Request request, NextFunction next, String... params)` 会接收到表达式解析出的参数；通过 `resolve()` 返回的包装实例会忽略运行时 `params`，使用解析阶段烘焙好的参数。

```java
import com.weacsoft.jaravel.vendor.http.middleware.MiddlewareAliasRegistry;

// 1. 注册中间件别名（同时自动注册 Class 映射）
Middleware logMiddleware = (request, next, params) -> {
    System.out.println(request.getRequest().getRequestURI());
    return next.apply(request);
};
MiddlewareAliasRegistry.registerGlobal("log", logMiddleware);

// 2. 注册参数化中间件别名（params 来自别名表达式如 "auth:api"，由 handle 的 String... params 接收）
Middleware authMiddleware = (request, next, params) -> {
    String guard = params.length > 0 ? params[0] : "web";
    if (!isAuthorized(request, guard)) {
        return ResponseBuilder.unauthorized("未授权");
    }
    return next.apply(request);
};
MiddlewareAliasRegistry.registerGlobal("auth", authMiddleware);

// 3. 仅按 Class 注册（无别名）
MiddlewareAliasRegistry.getGlobal().register(rateLimitMiddleware);
// 之后可用 middleware(RateLimitMiddleware.class) 或 middleware("RateLimitMiddleware") 引用
```

> `registerGlobal` / `resolveGlobal` 是委托给 `getGlobal()` 全局实例的静态便捷方法。也可通过 `new MiddlewareAliasRegistry()` 创建独立实例用于测试或隔离场景。

#### ClassMiddlewareSpec

`com.weacsoft.jaravel.vendor.http.middleware.ClassMiddlewareSpec`

内部类，封装一个 `Class<?>` 与一组 `String... params`，表示「按 Class 引用且带参数」的中间件规格。开发者**无需直接构造**该类——它由 `Route.middleware(Class<?> clazz, String... params)` 与 `Router.middleware(Class<?> clazz, String... params)` 在内部创建，并在 `getMiddlewares()` / `getAllMiddlewares()` 解析时通过 `MiddlewareAliasRegistry.resolve(Class, String...)` 解析为中间件实例。

#### 在路由中使用中间件

`Route` 和 `Router` 均提供多个 `middleware(...)` 重载，支持 `Middleware` 实例、`String` 表达式、`Class<?>` 对象三种引用方式。中间件不会立即解析，而是在调用 `getMiddlewares()` / `getAllMiddlewares()` 时通过 `MiddlewareAliasRegistry.getGlobal()` 解析为中间件实例。

```java
Router router = new Router();

// —— 模式一：字符串别名 ——
// 路由级别名中间件
router.get("/api/users", req -> ResponseBuilder.json(users))
      .middleware("auth:api", "log");

// 路由器级别名中间件（对该路由器下所有路由生效）
router.middleware("auth:api")
      .get("/api/profile", req -> ResponseBuilder.json(profile))
      .get("/api/settings", req -> ResponseBuilder.json(settings));

// —— 模式二：Class 对象 ——
// 无参数
router.get("/api/orders", req -> ResponseBuilder.json(orders))
      .middleware(AuthMiddleware.class);

// 带参数（内部创建 ClassMiddlewareSpec，解析时烘焙 params）
router.get("/api/admin", req -> ResponseBuilder.json(adminData))
      .middleware(AuthMiddleware.class, "api", "admin");

// —— 模式三：类名字符串（语法同别名） ——
router.get("/api/logs", req -> ResponseBuilder.json(logs))
      .middleware("AuthMiddleware:api");

// 路由分组中使用
router.group(Map.of(Route.Group.PREFIX, "admin"), admin -> {
    admin.middleware("auth:admin");               // 分组级别名中间件
    admin.middleware(RateLimitMiddleware.class);   // 分组级 Class 中间件
    admin.get("/dashboard", req -> ResponseBuilder.json(data));
    admin.get("/users", req -> ResponseBuilder.json(users));
});
```

#### 混合使用直接中间件、别名与类引用

`middleware(Middleware...)`、`middleware(String...)` 与 `middleware(Class<?>, String...)` 可混合调用，保持插入顺序，解析时按出现顺序展开：

```java
Middleware corsMiddleware = new CorsMiddleware();   // 直接实例

router.get("/mixed", req -> ResponseBuilder.ok())
      .middleware(corsMiddleware)                    // 直接中间件
      .middleware("auth:api")                        // 别名（解析为注册的中间件）
      .middleware(AuthMiddleware.class, "admin")     // Class + 参数
      .middleware("log");                            // 别名
// 执行顺序：cors → auth(guard=api) → auth(guard=admin) → log
```

#### MiddlewareAliasRegistry 方法

| 方法签名 | 说明 |
| --- | --- |
| `static MiddlewareAliasRegistry getGlobal()` | 获取全局静态实例 |
| `void register(String alias, Middleware middleware)` | 注册中间件别名（引用时参数由别名表达式烘焙注入）；**同时自动注册 Class 映射**；`alias` 为 null/空时仅注册 Class 映射 |
| `void register(Middleware middleware)` | 仅按 Class 注册中间件（不设别名），写入 Class→`Middleware` 表 |
| `Middleware resolve(String expression)` | 解析别名/类名表达式为中间件实例（参数烘焙到返回的包装 lambda 中）；先查别名表，未命中则回退类名查找（简单名、全限定名）；均未注册抛 `IllegalArgumentException` |
| `Middleware resolve(Class<?> clazz)` | 按 Class 解析中间件实例（无参数）；未注册抛 `IllegalArgumentException` |
| `Middleware resolve(Class<?> clazz, String... params)` | 按 Class 解析中间件实例并烘焙参数；未注册抛 `IllegalArgumentException` |
| `List<Middleware> resolveAll(List<String> expressions)` | 批量解析别名/类名表达式（保持顺序） |
| `boolean isRegistered(String alias)` | 别名是否已注册 |
| `boolean isClassRegistered(Class<?> clazz)` | Class 是否已注册 |
| `Set<String> getRegisteredAliases()` | 获取所有已注册别名（不可修改视图） |
| `Set<Class<?>> getRegisteredClasses()` | 获取所有已注册 Class（不可修改视图） |
| `void clear()` | 清除所有别名与 Class 映射（主要用于测试） |
| `static void registerGlobal(String alias, Middleware middleware)` | 向全局注册表注册别名 |
| `static Middleware resolveGlobal(String expression)` | 通过全局注册表解析别名/类名表达式 |

---

## 5. 请求（Request / RequestFactory）

### 5.1 Request

`com.weacsoft.jaravel.vendor.http.request.Request`

Laravel 风格请求对象，封装 query、input、file、header、cookie、session、attributes、routeParams 等多维数据。所有读取方法返回副本（防御性拷贝）。

#### 数据维度与核心方法

| 维度 | 全量获取 | 单值获取 | 多值获取 | 名称集合 | 是否存在 |
| --- | --- | --- | --- | --- | --- |
| query | `query()` | `query(key)` / `query(key, default)` / `query(key, Class)` | `queries(key)` | `queryNames()` | `has(key)` |
| input | `input()` | `input(key)` / `input(key, default)` / `input(key, Class)` | `inputs(key)` | `inputNames()` | `has(key)` |
| file | `file()` | `file(key)` | `files(key)` | `fileNames()` | `hasFile(key)` |
| header | `header()` | `header(key)` / `header(key, Class)` | `headers(key)` | `headerNames()` | `hasHeader(key)` |
| cookie | `cookie()` | `cookie(key)` / `cookie(key, Class)` | `cookies(key)` | `cookieNames()` | `hasCookie(key)` |
| session | `session()` | `session(key)` / `session(key, Class)` | `sessions(key)` | `sessionNames()` | `hasSession(key)` |

#### 通用方法

| 方法签名 | 说明 |
| --- | --- |
| `Map<String,Object> get()` | 合并 query + input |
| `Map<String,Object> all()` | 合并 query + input（同 `get`） |
| `String get(String key)` | 从 input/query 取值，默认 `null` |
| `String get(String key, String defaultValue)` | 取值带默认值 |
| `<T> T get(String key, T defaultValue)` | 取值带类型默认值 |
| `<T> T get(String key, Class<T> clazz)` | 按类型取值 |
| `List<Object> gets(String key)` | 取多值 |
| `Set<String> getNames()` | input + query 的字段名并集 |
| `boolean has(String key)` | query 或 input 是否包含该 key |

#### 写入方法（add / replace / remove）

每个维度都提供 `addXxx`（追加，重复 key 转 List）、`replaceXxx`（覆盖）、`removeXxx`（删除）三组方法：

```java
request.addInput("name", "Alice");
request.replaceInput("name", "Bob");
request.removeInput("name");

request.addCookie("token", "abc123");
request.replaceCookie("token", "new-value");
request.removeCookie("token");
```

#### Attributes（中间件间传参）

独立于 input/query 等维度，用于中间件间传递数据：

| 方法签名 | 说明 |
| --- | --- |
| `void setAttribute(String key, Object value)` | 设置属性 |
| `Object getAttribute(String key)` | 获取属性 |
| `<T> T getAttribute(String key, Class<T> clazz)` | 按类型获取属性 |
| `Map<String,Object> attributes()` | 获取全部属性 |
| `boolean hasAttribute(String key)` | 是否存在属性 |
| `void removeAttribute(String key)` | 移除属性 |

#### 路由参数

| 方法签名 | 说明 |
| --- | --- |
| `Map<String,Object> routeParams()` | 获取全部路由参数 |
| `String routeParam(String key)` | 获取路由参数（字符串） |
| `<T> T routeParam(String key, Class<T> clazz)` | 按类型获取路由参数（支持 Long/Integer/String/Boolean） |
| `boolean hasRouteParam(String key)` | 是否存在路由参数 |
| `void setRouteParams(Map<String,Object>)` | 设置路由参数 |

#### Servlet 桥接

| 方法签名 | 说明 |
| --- | --- |
| `HttpServletRequest getRequest()` | 获取底层 Servlet 请求 |
| `void setRequest(HttpServletRequest)` | 绑定 Servlet 请求，自动同步 header、cookie、session |
| `Cookie[] getCookieObjects()` | 获取全部 Cookie 对象数组 |
| `Cookie[] getNewCookies()` | 获取本次新增的 Cookie 对象数组 |

#### 客户端信息

| 方法签名 | 说明 |
| --- | --- |
| `String ip()` | 获取客户端 IP 地址，对齐 Laravel `$request->ip()` |

`ip()` 方法优先从 `X-Forwarded-For` 请求头获取（经过反向代理时取第一个 IP），否则使用 `HttpServletRequest.getRemoteAddr()`。若底层 Servlet 请求未绑定则返回 `"unknown"`。

```java
public Response store(Request request) {
    String clientIp = request.ip();   // 对齐 Laravel $request->ip()
    log.info("请求来自: {}", clientIp);
    return ResponseBuilder.json(Map.of("ip", clientIp));
}
```

#### 内部类 FluxMultipartFile

`Request.FluxMultipartFile` 实现 `MultipartFile`，基于 Servlet `Part` 适配，支持流式读取并缓存字节数据。用于 `ServerRequest` 场景下的文件上传解析。

```java
// 在控制器中获取请求参数
public Response store(Request request) {
    String name = request.input("name");
    int age = request.input("age", 0);
    MultipartFile avatar = request.file("avatar");
    String token = request.header("Authorization");
    String userId = request.routeParam("id");

    // 获取全部参数
    Map<String, Object> all = request.all();
    return ResponseBuilder.json(Map.of("success", true));
}
```

### 5.2 RequestFactory

`com.weacsoft.jaravel.vendor.http.request.RequestFactory`

请求构建工厂，从不同来源构建 `Request` 对象，并维护当前线程的请求引用。

| 方法签名 | 说明 |
| --- | --- |
| `static Request getCurrentRequest()` | 获取当前线程的 Request（ThreadLocal） |
| `static void setCurrentRequest(Request)` | 设置当前线程的 Request |
| `static void clearCurrentRequest()` | 清除当前线程的 Request |
| `static Request buildFromHttpServletRequest(HttpServletRequest)` | 从 Servlet 请求构建 |
| `static Request buildFromServerRequest(ServerRequest)` | 从 Spring `ServerRequest` 构建 |

构建流程（按 Content-Type 分发）：

```
解析 query string
    │
    ▼
判断 Content-Type
    ├── multipart/form-data     -> handleMultipartRequest（提取字段 + 文件）
    ├── application/json         -> handleJsonRequest（解析 JSON body 为 input）
    ├── application/x-www-form... -> handleFormUrlEncodedRequest（解析表单）
    └── null（如 GET）           -> 不解析 body
```

- `buildFromServerRequest` 会同时调用 `setCurrentRequest` 设置线程局部变量。
- multipart 解析通过 `submittedFileName` 是否为 null 区分文件字段与文本字段。
- form-urlencoded 优先使用 Servlet API 的 `getParameterMap`（更可靠），失败时回退到手动读取 body。

```java
// 通常由框架自动调用，无需手动构建
Request request = RequestFactory.buildFromHttpServletRequest(servletRequest);

// 在任意位置获取当前请求
Request current = RequestFactory.getCurrentRequest();
```

---

## 6. 响应（Response / ResponseBuilder / RawResponse / JSONResponseResolver）

### 6.1 Response 接口

`com.weacsoft.jaravel.vendor.http.response.Response`

响应抽象接口，定义状态码、响应头、Cookie、内容等契约。

| 方法签名 | 说明 |
| --- | --- |
| `int getStatus()` | 获取 HTTP 状态码 |
| `Map<String,List<String>> getHeaders()` | 获取响应头 |
| `void addHeader(String name, String value)` | 追加响应头 |
| `Cookie[] getCookies()` | 获取 Cookie 数组 |
| `void addCookie(Cookie cookie)` | 追加 Cookie 对象 |
| `void addCookie(String name, String value)` | 追加 Cookie（名值） |
| `String getContent()` | 获取文本内容 |
| `default byte[] getBytes()` | 获取二进制内容（默认 null） |
| `default String getContentType()` | 从响应头提取 Content-Type；若未设置，返回默认值 `text/plain;charset=utf-8`（兜底机制） |
| `default Object getBody()` | 获取响应体（默认返回 getContent） |

> **Content-Type 兜底机制**：如果 `Response` 没有设置 `Content-Type` 响应头，框架在写入 HTTP 响应时会通过 `getContentType()` 兜底为 `text/plain;charset=utf-8`。`raw()` 构建的空响应若不显式设置 Content-Type 即走此兜底逻辑。

### 6.2 ResponseBuilder

`com.weacsoft.jaravel.vendor.http.response.ResponseBuilder`

响应构建器，提供静态工厂方法快速构建各类响应。内部 `AbstractResponse` 抽象类提供 headers / cookies 的默认实现。

| 方法签名 | 说明 |
| --- | --- |
| `static Response ok()` | 200 状态，内容 `"ok"` |
| `static Response json(Object data)` | 200 状态，JSON 序列化，Content-Type: application/json |
| `static Response content(String content)` | 200 状态，纯文本，Content-Type: text/plain |
| `static Response html(String html)` | 200 状态，HTML 响应，Content-Type: text/html |
| `static Response view(String templateName, Map<String,Object> data)` | 200 状态，渲染 Blade 模板，Content-Type: text/html |
| `static Response file(byte[] data, String filename)` | 200 状态，文件下载，Content-Type: application/octet-stream |
| `static Response staticFile(byte[] data, String mimeType, int cacheMaxAge)` | 200 状态，静态文件响应，设置 Content-Type / Cache-Control / Content-Length |
| `static Response redirect(String url)` | 302 重定向，设置 Location 头 |
| `static Response unauthorized(String message)` | 401 未授权 |
| `static Response forbidden(String message)` | 403 禁止访问 |
| `static Response error(int status, String message)` | 自定义错误状态，JSON 格式 `{"message": "..."}` |
| `static RawResponse raw()` | 创建空的 `RawResponse` 构建器，不预设任何 header / status，开发者自由组织（见 6.3） |
| `static String toJson(Object data)` | 将对象序列化为 JSON 字符串 |
| `static void setBladeEngine(Object engine)` | 注入 Blade 模板引擎实例（用于 `view`） |

```java
// JSON 响应
Response r1 = ResponseBuilder.json(Map.of("id", 1, "name", "Alice"));

// 视图响应（需先注入 BladeEngine）
Response r2 = ResponseBuilder.view("user.profile", Map.of("user", user));

// HTML 响应
Response r3 = ResponseBuilder.html("<h1>Hello</h1>");

// 文件下载
Response r4 = ResponseBuilder.file(fileBytes, "report.pdf");

// 重定向
Response r5 = ResponseBuilder.redirect("/login");

// 错误响应
Response r6 = ResponseBuilder.error(404, "资源不存在");
Response r7 = ResponseBuilder.unauthorized("请先登录");

// Raw 模式：自定义 XML 响应
Response r8 = ResponseBuilder.raw()
    .status(200)
    .header("Content-Type", "application/xml;charset=utf-8")
    .header("X-Custom-Header", "hello")
    .body("<xml><name>test</name></xml>");
```

> `view` 方法依赖 jblade 模块。若未通过 `setBladeEngine` 注入引擎，调用时会抛 `RuntimeException("jblade 模块未引入")`。

### 6.3 RawResponse

`com.weacsoft.jaravel.vendor.http.response.ResponseBuilder.RawResponse`

`ResponseBuilder.raw()` 返回的 Raw 响应构建器，实现 `Response` 接口。不预设任何 Content-Type 或状态码，全部由开发者通过链式方法决定。

| 方法签名 | 说明 |
| --- | --- |
| `RawResponse status(int status)` | 设置 HTTP 状态码（默认 200） |
| `RawResponse header(String name, String value)` | 追加响应头（同名可多次添加） |
| `RawResponse contentType(String contentType)` | 设置 Content-Type（覆盖已有值） |
| `RawResponse cookie(Cookie cookie)` | 追加 Cookie 对象 |
| `RawResponse cookie(String name, String value)` | 追加 Cookie（名值） |
| `Response body(String content)` | 设置文本响应体 |
| `Response body(byte[] bytes)` | 设置二进制响应体 |

同时实现 `Response` 接口的全部方法：`getStatus` / `getHeaders` / `addHeader` / `getCookies` / `addCookie(Cookie)` / `addCookie(String,String)` / `getContent` / `getBytes` / `getContentType` / `getBody`。

> `body(...)` 方法返回 `Response` 类型（而非 `RawResponse`），调用后即结束链式构建。若未通过 `header` 或 `contentType` 设置 Content-Type，框架在写入 HTTP 响应时兜底为 `text/plain;charset=utf-8`。

```java
// 自定义二进制响应（如图片）
return ResponseBuilder.raw()
    .status(200)
    .header("Content-Type", "image/png")
    .body(imageBytes);

// 不设 Content-Type，框架兜底为 text/plain;charset=utf-8
return ResponseBuilder.raw()
    .status(204)
    .body("");
```

### 6.4 JSONResponseResolver

`com.weacsoft.jaravel.vendor.http.response.JSONResponseResolver`

JSON 响应工具，快速构造标准格式的响应 Map。

| 方法签名 | 说明 |
| --- | --- |
| `static Map<String,Object> createErrorResponse(String message)` | 构造错误响应 `{"success":false,"message":...,"data":null}` |
| `static Map<String,Object> createSuccessResponse()` | 构造成功响应（无数据） |
| `static Map<String,Object> createSuccessResponse(Object[] data)` | 构造成功响应（带数据） |
| `static Map<String,Object> createResponse(boolean success, String message, Object[] data)` | 构造完整响应 |

```java
Map<String, Object> ok = JSONResponseResolver.createSuccessResponse(new Object[]{user});
// {"success": true, "message": "ok", "data": [user]}

Map<String, Object> err = JSONResponseResolver.createErrorResponse("参数错误");
// {"success": false, "message": "参数错误", "data": null}
```

---

## 7. 路由系统（Router / Route / RouteService）

### 7.1 Router

`com.weacsoft.jaravel.vendor.route.Router`

路由器，支持链式注册路由与分组。内部使用 `CopyOnWriteArrayList` 存储路由、子路由器与中间件。

| 方法签名 | 说明 |
| --- | --- |
| `Router middleware(Middleware... middleware)` | 添加中间件，返回 this（链式） |
| `Router middleware(String... aliases)` | 通过别名/类名表达式添加中间件（如 `"auth:api"`），返回 this（链式），通过 `MiddlewareAliasRegistry.getGlobal()` 解析 |
| `Router middleware(Class<?> clazz, String... params)` | 通过 Class 对象引用中间件（可选参数），返回 this（链式）；内部创建 `ClassMiddlewareSpec`，由全局 `MiddlewareAliasRegistry` 解析 |
| `Route get(String uri, Controllers.Runner action)` | 注册 GET 路由 |
| `Route post(String uri, Controllers.Runner action)` | 注册 POST 路由 |
| `Route put(String uri, Controllers.Runner action)` | 注册 PUT 路由 |
| `Route delete(String uri, Controllers.Runner action)` | 注册 DELETE 路由 |
| `Route patch(String uri, Controllers.Runner action)` | 注册 PATCH 路由 |
| `Router all(String uri, Controllers.Runner action)` | 注册匹配所有方法的路由组 |
| `Route get/post/put/delete/patch(String uri, String controllerAction)` | 注册路由（字符串形式控制器引用，如 `"UserController::list"`），延迟解析 |
| `Route get/post/put/delete/patch(String uri, Class<?> controllerClass, String methodName)` | 注册路由（类对象形式控制器引用，如 `UserController.class, "list"`），延迟解析 |
| `Router all(String uri, String controllerAction)` | 注册多方法路由组（字符串形式控制器引用） |
| `Router all(String uri, Class<?> controllerClass, String methodName)` | 注册多方法路由组（类对象形式控制器引用） |
| `Route addRoute(String method, String uri, Controllers.Runner action)` | 注册指定方法路由 |
| `Router addMultiRoute(String[] methods, String uri, Controllers.Runner action)` | 注册多方法路由组 |
| `Router group(Map<Route.Group,String> params, Consumer<Router> router)` | 路由分组（namespace/prefix/name） |
| `StaticResourceRoute serveStatic(String urlPrefix, String location, int cacheMaxAge)` | 注册静态资源目录（指定缓存时间），对齐 Laravel `public` |
| `StaticResourceRoute serveStatic(String urlPrefix, String location)` | 注册静态资源目录（默认缓存 1 小时） |
| `StaticResourceRoute serveStatic(String urlPrefix, List<String> locations, int cacheMaxAge)` | 注册多目录静态资源（按顺序回退查找） |
| `List<Route> getAllRoutes()` | 递归获取所有路由（含子路由器） |
| `List<Middleware> getAllMiddlewares()` | 获取本路由器及父级链的中间件 |
| `String getName()` / `setName(String)` | 路由器名称 |
| `String getNamespace()` / `setNamespace(String)` | 命名空间 |
| `String getPrefix()` / `setPrefix(String)` | URI 前缀 |

> 控制器引用重载对齐 Laravel `Route::get('/users', 'UserController@index')`。字符串形式用 `::` 分隔类名与方法名（如 `"UserController::list"`），类对象形式传入 `Class` 与方法名（如 `UserController.class, "list"`）。两种形式均**延迟解析**——控制器引用在首次请求时才通过 `ControllerActionResolver` 解析，保证路由注册顺序与控制器扫描顺序无关，解析结果会缓存。详见 [8. 控制器契约](#8-控制器契约controllers--controllerregistry--controlleractionresolver)。

```java
Router router = new Router();

// 基本路由
router.get("/users", request -> ResponseBuilder.json(userService.list()));
router.post("/users", request -> ResponseBuilder.json(userService.create(request)));

// 路由分组
router.group(Map.of(
    Route.Group.PREFIX, "api",
    Route.Group.NAMESPACE, "api"
), api -> {
    api.group(Map.of(Route.Group.PREFIX, "v1"), v1 -> {
        v1.get("/users", request -> ResponseBuilder.json(users));
        v1.get("/posts", request -> ResponseBuilder.json(posts));
    });
});

// 带中间件的路由
router.get("/admin", request -> ResponseBuilder.json(adminData))
      .middleware(new Authenticate("admin"));

// 通过别名引用中间件（需先在 MiddlewareAliasRegistry 注册别名）
router.get("/api/users", request -> ResponseBuilder.json(users))
      .middleware("auth:api", "log");

// 控制器引用（对齐 Laravel Route::get('/users', 'UserController@index')）
// 字符串形式：ControllerName::methodName
router.get("/users", "UserController::list");
router.get("/users/{id}", "UserController::show");
router.post("/users", "UserController::store");

// 类对象形式：忽略包名，类型安全
router.get("/users", UserController.class, "list");
router.get("/users/{id}", UserController.class, "show");
```

### 7.2 Route

`com.weacsoft.jaravel.vendor.route.Route`

单条路由，记录方法、URI、action、中间件、name、namespace、prefix，并能计算完整 URI / 名称 / 命名空间。

| 方法签名 | 说明 |
| --- | --- |
| `Route(String method, String uri, Controllers.Runner action)` | 构造器 |
| `Route middleware(Middleware... middleware)` | 添加路由级中间件，返回 this |
| `Route middleware(String... aliases)` | 通过别名/类名表达式添加路由级中间件（如 `"auth:api"`），返回 this，通过 `MiddlewareAliasRegistry.getGlobal()` 解析 |
| `Route middleware(Class<?> clazz, String... params)` | 通过 Class 对象引用路由级中间件（可选参数），返回 this；内部创建 `ClassMiddlewareSpec`，由全局 `MiddlewareAliasRegistry` 解析 |
| `Route name(String name)` | 设置路由名称，返回 this |
| `Route prefix(String prefix)` | 设置前缀，返回 this |
| `String generateFullUri()` | 生成完整 URI（合并父路由器前缀） |
| `String getFullUri()` | 同 `generateFullUri()` |
| `String generateFullNamespace()` | 生成完整命名空间 |
| `String getFullNamespace()` | 同上 |
| `String getFullName()` | 生成完整名称 |
| `List<Middleware> getMiddlewares()` | 获取路由中间件（含父路由器中间件） |
| `String getMethod()` | 获取 HTTP 方法 |
| `String getUri()` | 获取原始 URI |
| `Controllers.Runner getAction()` / `setAction(...)` | 获取/设置处理动作 |
| `String getName()` / `setName(String)` | 路由名称 |
| `String getNamespace()` / `setNamespace(String)` | 命名空间 |
| `String getPrefix()` / `setPrefix(String)` | 前缀 |

`Route.Group` 枚举定义分组维度：`NAMESPACE`、`PREFIX`、`NAME`。

### 7.3 RouteService

`com.weacsoft.jaravel.vendor.route.RouteService`

路由规范化工具，提供静态方法规范化 URI、命名空间与名称。

| 方法签名 | 说明 |
| --- | --- |
| `static String normalizeUri(String uri)` | 规范化 URI：去空白、合并多余 `/`、确保以 `/` 开头、去除尾部 `/`，空则返回 `/` |
| `static String normalizeNamesapce(String namespace)` | 规范化命名空间：去空白、合并多余 `.`、去除首尾 `.`，空则返回 `""` |
| `static String normalizeName(String name)` | 规范化名称：去空白、合并多余 `.`、确保以 `.` 开头 |

```java
RouteService.normalizeUri("api//users/");    // "/api/users"
RouteService.normalizeUri("  /posts  ");     // "/posts"
RouteService.normalizeNamesapce(".api.v1.");  // "api.v1"
```

---

## 8. 控制器契约（Controllers / ControllerRegistry / ControllerActionResolver）

`com.weacsoft.jaravel.vendor.controller`

控制器契约与控制器引用机制，对齐 Laravel `Route::get('/users', 'UserController@index')` 的控制器路由能力。该子包包含三类组件：`Controllers` 接口（统一 action 签名）、`ControllerRegistry`（控制器注册表）、`ControllerActionResolver`（控制器动作解析器）。

| 类 | 职责 |
| --- | --- |
| `Controllers` | 控制器契约接口，内部定义 `Runner` 函数式接口作为路由 action 的统一签名 |
| `ControllerRegistry` | 控制器注册表（全局静态），维护 Class→实例 与 名称→实例 双映射 |
| `ControllerActionResolver` | 控制器动作解析器，将字符串/类对象引用解析为 `Controllers.Runner`，带缓存 |

### 8.1 Controllers 接口

`com.weacsoft.jaravel.vendor.controller.Controllers`

控制器契约接口，内部定义 `Runner` 函数式接口作为路由 action 的统一签名。

```java
public interface Controllers {
    @FunctionalInterface
    interface Runner {
        Response handle(Request request);
    }
}
```

`Runner` 接收 `Request` 返回 `Response`，可直接用 Lambda 或方法引用作为路由 action：

```java
// Lambda
router.get("/users", request -> ResponseBuilder.json(userService.list()));

// 方法引用
router.get("/users", userController::index);
```

控制器通常是 Spring Bean（需要 `@Autowired` 依赖注入），实现 `Controllers` 接口并定义形如 `Response method(Request request)` 的方法。除直接用 `Runner` 外，还可通过控制器引用（字符串或类对象）在路由中引用控制器方法，见 8.4。

```java
public class UserController implements Controllers {

    public Response list(Request request) {
        return ResponseBuilder.json(userService.list());
    }

    public Response show(Request request) {
        Long id = request.routeParam("id", Long.class);
        return ResponseBuilder.json(userService.find(id));
    }

    public Response store(Request request) {
        return ResponseBuilder.json(userService.create(request));
    }
}
```

### 8.2 控制器注册表（ControllerRegistry）

`com.weacsoft.jaravel.vendor.controller.ControllerRegistry`

控制器注册表，对齐 Laravel 控制器路由引用机制。在 Laravel 中路由通过字符串引用控制器方法（`Route::get('/users', 'UserController@index')`），本类提供等价能力。

注册表为**全局静态单例**，内部维护两张映射表：

- `Map<Class<?>, Object>` —— Class → 控制器实例（通过 `register(Object)` 注册）
- `Map<String, Object>` —— 名称（简名 + 全限定名）→ 控制器实例

控制器是 Spring Bean（需要 `@Autowired` 依赖注入），由 `springboot` 模块的 `SpringBootRouteAutoConfiguration` 在启动时扫描容器中实现了 `Controllers` 的 Bean 并注册到全局实例。注册时同时写入类映射与名称映射（简名 + 全限定名），确保通过类对象或类名（简名/全限定名）均能解析。

| 方法签名 | 说明 |
| --- | --- |
| `static ControllerRegistry getGlobal()` | 获取全局静态实例 |
| `void register(Object controller)` | 注册控制器实例，同时写入类映射与名称映射（简名 + 全限定名） |
| `Object resolve(Class<?> clazz)` | 按 Class 解析控制器实例；未注册抛 `IllegalArgumentException` |
| `Object resolve(String name)` | 按名称（简名或全限定名）解析控制器实例；未注册抛 `IllegalArgumentException` |
| `boolean isClassRegistered(Class<?> clazz)` | 类是否已注册 |
| `boolean isNameRegistered(String name)` | 名称是否已注册 |
| `Set<Class<?>> getRegisteredClasses()` | 获取所有已注册的控制器类（不可修改视图） |
| `void clear()` | 清除所有已注册的控制器（主要用于测试） |
| `static void registerGlobal(Object controller)` | 向全局注册表注册控制器的静态便捷方法 |

```java
// 注册控制器（通常由框架在启动时自动完成）
ControllerRegistry.getGlobal().register(userController);
// 或静态便捷方法
ControllerRegistry.registerGlobal(userController);

// 通过类名解析（简名或全限定名）
Object controller = ControllerRegistry.getGlobal().resolve("UserController");

// 通过类对象解析
Object controller = ControllerRegistry.getGlobal().resolve(UserController.class);
```

### 8.3 控制器动作解析器（ControllerActionResolver）

`com.weacsoft.jaravel.vendor.http.controller.ControllerActionResolver`

控制器动作解析器，将控制器引用解析为 `Controllers.Runner`。支持两种引用方式：

- **字符串**：`"UserController::index"` — 类名（简名或全限定名）+ 方法名，使用 `::` 分隔。解析时从 `ControllerRegistry` 查找控制器实例。
- **类对象**：`UserController.class` + 方法名 — 忽略包名，通过 `ControllerRegistry.resolve(Class)` 查找实例。

**延迟解析**：控制器引用在路由定义时存储为字符串/类对象，在首次请求时才解析。这保证了路由注册顺序与控制器扫描顺序无关——即使路由先于控制器注册定义，只要请求到达时控制器已注册即可正常工作。

**缓存**：解析结果（`Method` + 控制器实例）会缓存为 `Controllers.Runner`，后续请求直接复用，避免重复反射查找。控制器方法签名要求为 `Response method(Request request)`。

| 方法签名 | 说明 |
| --- | --- |
| `static Controllers.Runner resolve(String controllerAction)` | 解析字符串形式的控制器引用为 `Runner`（格式 `"ControllerName::methodName"`），解析后缓存 |
| `static Controllers.Runner resolve(Class<?> controllerClass, String methodName)` | 解析类对象形式的控制器引用为 `Runner`，解析后缓存 |
| `static void clearCache()` | 清除解析缓存（主要用于测试） |

解析流程：`resolve(String)` 以 `::` 分隔类名与方法名，从 `ControllerRegistry` 查找控制器实例，反射查找签名 `Response method(Request)` 的方法；`resolve(Class, String)` 直接按 Class 查找实例与方法。方法不存在时抛 `IllegalArgumentException`，调用失败时抛 `RuntimeException`（包装原始异常）。

```java
// 字符串形式解析为 Runner
Controllers.Runner runner = ControllerActionResolver.resolve("UserController::list");
Response response = runner.handle(request);

// 类对象形式解析为 Runner
Controllers.Runner runner = ControllerActionResolver.resolve(UserController.class, "list");
Response response = runner.handle(request);
```

### 8.4 控制器引用（Controller References）

对齐 Laravel 的控制器路由引用机制，`Router` 的所有 HTTP 方法（`get`/`post`/`put`/`delete`/`patch`/`all`）均提供控制器引用重载，支持以下两种形式：

| 形式 | 调用方式 | 说明 |
| --- | --- | --- |
| **字符串** | `router.get("/users", "UserController::list")` | 类名（简名或全限定名）+ `::` + 方法名 |
| **类对象** | `router.get("/users", UserController.class, "list")` | `Class` 对象 + 方法名，类型安全、忽略包名 |

#### 字符串形式

格式：`"ControllerName::methodName"` 或 `"com.example.ControllerName::methodName"`。`::` 分隔类名与方法名，两端空格会被裁剪（`trim`）。类名可为简名或全限定名，均从 `ControllerRegistry` 解析。

| 表达式 | 类名 | 方法名 | 说明 |
| --- | --- | --- | --- |
| `"UserController::list"` | `UserController`（简名） | `list` | 简名 + 方法名 |
| `"UserController::show"` | `UserController` | `show` | 简名 + 方法名 |
| `"com.app.controller.UserController::store"` | `com.app.controller.UserController`（全限定名） | `store` | 全限定名 + 方法名 |

```java
router.get("/users", "UserController::list");
router.get("/users/{id}", "UserController::show");
router.post("/users", "UserController::store");
router.put("/users/{id}", "UserController::update");
router.delete("/users/{id}", "UserController::destroy");
```

#### 类对象形式

通过 `Class` 对象与方法名引用控制器，类型安全且无需输入完整类名。解析时忽略包名，通过 `ControllerRegistry.resolve(Class)` 查找实例。

```java
router.get("/users", UserController.class, "list");
router.get("/users/{id}", UserController.class, "show");
router.post("/users", UserController.class, "store");
router.put("/users/{id}", UserController.class, "update");
router.delete("/users/{id}", UserController.class, "destroy");
```

> 与中间件引用类似，控制器引用同样支持字符串与类对象两种形式。区别在于：控制器引用用 `::` 分隔（对齐 Laravel `Controller@index` 语义的 Java 适配），解析依赖 `ControllerRegistry`；中间件引用用 `:` 分隔参数，解析依赖 `MiddlewareAliasRegistry`。

### 8.5 在路由中使用控制器引用

控制器引用在路由定义时存储为字符串/类对象，在首次请求时才通过 `ControllerActionResolver` 解析为 `Controllers.Runner`，解析结果缓存后复用。`Router` 内部以 `lazyResolve` 包装为延迟解析的 Runner：

```java
// 路由注册时仅存储引用，不立即解析
router.get("/users", "UserController::list");

// 首次请求到达时才解析（此时 ControllerRegistry 中控制器已注册）
// 解析结果缓存，后续请求直接复用
```

控制器引用与中间件、分组可自由组合：

```java
Router router = new Router();

// 字符串形式控制器引用 + 中间件
router.get("/api/users", "UserController::list")
      .middleware("auth:api", "log");

// 类对象形式控制器引用 + 路由分组
router.group(Map.of(Route.Group.PREFIX, "api"), api -> {
    api.middleware("auth:api");
    api.get("/users", UserController.class, "list");
    api.get("/users/{id}", UserController.class, "show");
    api.post("/users", UserController.class, "store");
});

// 控制器引用与 Runner 混合使用
router.get("/health", request -> ResponseBuilder.ok());              // Runner（Lambda）
router.get("/users", "UserController::list");                       // 字符串控制器引用
router.get("/users/{id}", UserController.class, "show");            // 类对象控制器引用
```

> **延迟解析的意义**：路由通常在配置类中定义，而控制器由 Spring 容器扫描注册。延迟解析保证两者的顺序无关——即使路由先于控制器注册，只要请求到达时控制器已在 `ControllerRegistry` 中注册即可正常工作。`ControllerActionResolver` 内部使用 `ConcurrentHashMap` 缓存解析结果，首次解析后无额外反射开销。

---

## 9. 配置选项

`http` 模块本身不读取外部配置文件。内置中间件**不是** Spring Bean，不再通过构造器参数配置，而是采用**继承式配置**——使用者继承预定义中间件后覆盖 `protected` 方法自定义行为，并标注 `@MiddlewareAlias` 注解：

| 中间件 | 可覆盖方法（protected） | 默认值 |
| --- | --- | --- |
| `TrimStrings` | `except()`（排除字段） | 空数组 |
| `ConvertEmptyStringsToNull` | `except()`（排除字段） | `password`, `password_confirmation`, `current_password` |
| `EncryptCookies` | `encryptionKey()`（密钥）、`except()`（排除 Cookie） | 硬编码演示密钥 / 空数组 |
| `TrustProxies` | `trustedProxies()`（信任代理 IP） | `127.0.0.1`, `::1` |
| `VerifyCsrfToken` | `except()`（排除 URI） | 空数组 |

全局中间件不再通过独立的注册表管理，而是直接在根 `Router` 上通过 `router.middleware(...)` 声明；`springboot` 模块的 `SpringBootRouteAutoConfiguration` 负责在启动时扫描 `@MiddlewareAlias` 注解的类，通过反射实例化（要求有无参构造器，中间件不是 Spring Bean）并注册到全局 `MiddlewareAliasRegistry`。预定义中间件本身不标注 `@MiddlewareAlias`，由使用者继承后自行标注。

---

## 10. 线程安全说明

| 类 | 线程安全性 | 说明 |
| --- | --- | --- |
| 所有中间件（`TrimStrings` / `ConvertEmptyStringsToNull` / `EncryptCookies` / `TrustProxies` / `VerifyCsrfToken`） | **线程安全** | 非 Spring Bean（不再标注 `@Component`），由框架通过反射实例化为单例。采用继承式配置，配置项通过覆盖 `protected` 方法返回，只要子类不在运行时引入可变状态即为线程安全；预定义实现本身无状态，可安全在并发请求间复用 |
| `Middleware` / `NextFunction` | 线程安全 | 函数式接口，无状态 |
| `MiddlewareAliasRegistry` | 线程安全 | 内部使用 `ConcurrentHashMap` 存储别名映射与 Class 映射，全局静态实例可在并发下安全注册与解析；`getRegisteredAliases()` / `getRegisteredClasses()` 返回不可修改视图 |
| `Request` | **单请求隔离** | 每次请求新建实例，内部 Map 非线程安全。`RequestFactory` 通过 `ThreadLocal` 维护当前请求，确保请求间隔离。不应跨请求共享同一个 `Request` |
| `RequestFactory` | 线程安全 | 静态方法无共享可变状态；`currentRequest` 为 `ThreadLocal`，天然线程隔离 |
| `ResponseBuilder` | 线程安全 | 静态工厂方法，每次返回新的 `AbstractResponse` 实例。`ObjectMapper` 为静态 final 线程安全。`bladeEngine` 静态字段在启动阶段单次写入后只读 |
| `JSONResponseResolver` | 线程安全 | 静态方法，`ObjectMapper` 为静态 final |
| `Router` / `Route` | 启动期安全 | 内部使用 `CopyOnWriteArrayList`，适合启动阶段注册、运行时只读。运行时动态增删路由虽线程安全但开销较大 |
| `RouteService` | 线程安全 | 纯静态无状态方法 |
| `StaticResourceHandler` | 线程安全 | 字段 `final`（location/cacheMaxAge），MIME 表为静态 final 只读 Map，多请求只读复用 |
| `StaticResourceRoute` | 启动期安全 | 通过 `Router.serveStatic()` 在启动阶段构造，handlers 列表构造后只读；运行时仅读 |
| `StaticResourceProperties` | 线程安全 | 配置 POJO，启动阶段注入后只读 |
| `Controllers` / `Controllers.Runner` | 取决于实现 | 函数式接口，线程安全性取决于具体 action 实现 |
| `ControllerRegistry` | 线程安全 | 全局静态单例，内部使用 `ConcurrentHashMap` 存储 Class 映射与名称映射，可在并发下安全注册与解析；`getRegisteredClasses()` 返回不可修改视图 |
| `ControllerActionResolver` | 线程安全 | 全部为静态方法，解析缓存使用 `ConcurrentHashMap`（`computeIfAbsent`），首次解析后并发请求直接复用缓存的 `Runner` |

---

## 11. 静态资源目录（StaticResource）

`com.weacsoft.jaravel.vendor.http.staticresource`

`http` 模块新增静态资源目录功能，对齐 Laravel 的 `public` 目录与 `asset()` 辅助函数。开发者通过 `Router.serveStatic()` 注册一个或多个资源目录，框架自动处理 MIME 推断、缓存头与路径穿越防护，将 GET 请求映射到 classpath 或文件系统中的静态文件。

涉及类：

| 类 | 职责 |
| --- | --- |
| `StaticResourceProperties` | 配置属性（前缀 `jaravel.http.static-resource`） |
| `StaticResourceHandler` | 核心处理器：MIME 推断、路径安全、classpath/文件系统双模式加载 |
| `StaticResourceRoute` | 路由处理器（实现 `Controllers.Runner`），多目录回退查找并返回响应 |

### 11.1 架构

```
GET /static/css/app.css
        │
        ▼
Router.serveStatic("/static", "classpath:/static/", 3600)
   注册路由 GET /static/{path} → StaticResourceRoute
        │
        ▼
StaticResourceRoute.handle(Request)
   从 routeParam("path") 提取相对路径 css/app.css
        │
        ▼
StaticResourceHandler.load("css/app.css")
   ├── sanitizePath()   路径穿越防护（拒绝 .. 与反斜杠 \）
   ├── guessMimeType()  推断 text/css; charset=utf-8
   └── 双模式加载
        ├── classpath:/static/  → ClassLoader.getResource() 读取（JAR 内）
        └── file:./public/      → Files.readAllBytes() 读取（外部目录）
        │
        ▼
ResourceResult(content / mimeType / cacheMaxAge)
        │
        ▼
ResponseBuilder.staticFile(bytes, mimeType, cacheMaxAge)
   设置 Content-Type / Cache-Control / Content-Length
        │
        ▼
200 OK 响应
```

### 11.2 使用示例

```java
Router router = new Router();

// 基本用法：单目录 + 缓存 1 小时
router.serveStatic("/static", "classpath:/static/", 3600);
// 访问 /static/css/app.css → classpath:/static/css/app.css

// 默认缓存 1 小时（cacheMaxAge 默认 3600）
router.serveStatic("/static", "classpath:/static/");

// 使用文件系统目录（部署在 JAR 外部，便于热更新）
router.serveStatic("/assets", "file:./public/", 7200);
// 访问 /assets/js/app.js → ./public/js/app.js
```

> `serveStatic` 有 3 个重载：单目录带缓存时间、单目录默认缓存、多目录带缓存时间。位置前缀支持 `classpath:`（打包在 JAR 内）与 `file:`（外部文件系统）。

### 11.3 多目录回退查找

当同一 URL 前缀需要从多个目录查找资源时，可传入目录列表。`StaticResourceRoute` 会按列表顺序逐个尝试，第一个命中的目录返回结果，全部未命中则返回 404：

```java
Router router = new Router();

// 先在文件系统 public 目录查找，找不到再回退到 classpath
router.serveStatic("/static", List.of(
    "file:./public/",         // 优先：外部目录（可热更新）
    "classpath:/static/"      // 回退：打包在 JAR 内的默认资源
), 3600);

// 访问 /static/img/logo.png
//   1. 尝试 ./public/img/logo.png          → 命中则返回
//   2. 尝试 classpath:/static/img/logo.png  → 命中则返回
//   3. 均未命中 → 404 Not Found
```

### 11.4 配置（StaticResourceProperties）

通过 `application.yml` 以 `jaravel.http.static-resource` 前缀配置：

```yaml
jaravel:
  http:
    static-resource:
      enabled: true
      url-prefix: /static
      default-location: classpath:/static/
      cache-max-age: 3600
      directory-listing: false
```

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enabled` | `boolean` | `true` | 是否启用静态资源服务 |
| `urlPrefix` | `String` | `/static` | URL 前缀，对齐 Laravel `mix()`（如 `/static`） |
| `defaultLocation` | `String` | `classpath:/static/` | 默认资源目录（`classpath:` 或 `file:` 前缀） |
| `cacheMaxAge` | `int` | `3600` | 缓存时间（秒），写入 `Cache-Control: max-age=N` |
| `directoryListing` | `boolean` | `false` | 是否允许目录列表（默认关闭，对齐生产安全实践） |

### 11.5 MIME 类型

`StaticResourceHandler` 内置常见文件扩展名到 MIME 的映射表，未匹配的扩展名回退为 `application/octet-stream`：

| 扩展名 | MIME 类型 |
| --- | --- |
| `.css` | `text/css; charset=utf-8` |
| `.js` / `.mjs` | `application/javascript; charset=utf-8` |
| `.html` / `.htm` | `text/html; charset=utf-8` |
| `.json` | `application/json; charset=utf-8` |
| `.xml` | `application/xml; charset=utf-8` |
| `.txt` | `text/plain; charset=utf-8` |
| `.csv` | `text/csv; charset=utf-8` |
| `.png` | `image/png` |
| `.jpg` / `.jpeg` | `image/jpeg` |
| `.gif` | `image/gif` |
| `.svg` | `image/svg+xml` |
| `.ico` | `image/x-icon` |
| `.webp` | `image/webp` |
| `.pdf` | `application/pdf` |
| `.woff` / `.woff2` | `font/woff` / `font/woff2` |
| `.mp4` | `video/mp4` |
| `.wasm` | `application/wasm` |

### 11.6 路径安全

为防止路径穿越攻击（Path Traversal），`StaticResourceHandler` 在加载前对请求路径进行两层防护：

1. **`sanitizePath()` 字符级检查**：拒绝包含 `..` 或反斜杠 `\` 的路径，并规范化多余的 `/`，不安全时返回 `null`。
2. **文件系统路径越界检查**：对 `file:` 前缀的资源，将解析后的绝对路径与基础目录比较（`path.startsWith(baseDir)`），确保最终文件落在允许的根目录内。

```text
以下请求会被拦截，返回 404：
  /static/../../etc/passwd   → sanitizePath 检测到 .. 直接拒绝
  /static/..%2f..%2fsecret   → 解析后路径越界，startsWith 校验失败
```

### 11.7 Blade 模板 @asset 指令

在 Blade 模板中使用 `@asset` 指令生成静态资源 URL，对齐 Laravel `asset()` 辅助函数：

```blade
{{-- 生成 /static/css/app.css --}}
<link rel="stylesheet" href="@asset('css/app.css')">

{{-- 生成 /static/js/app.js --}}
<script src="@asset('js/app.js')"></script>

{{-- 生成 /static/img/logo.png --}}
<img src="@asset('img/logo.png')" alt="logo">
```

`@asset('相对路径')` 会拼接配置的 `urlPrefix`（默认 `/static`），渲染为完整 URL。资源目录与 URL 前缀保持一致即可正确服务模板引用的资源。
