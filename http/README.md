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
- [5. 请求（Request / RequestFactory）](#5-请求request--requestfactory)
- [6. 响应（Response / ResponseBuilder / JSONResponseResolver）](#6-响应response--responsebuilder--jsonresponseesolver)
- [7. 路由系统（Router / Route / RouteService）](#7-路由系统router--route--routeservice)
- [8. 控制器契约（Controllers）](#8-控制器契约controllers)
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
| HTTP Middleware | `Middleware` 接口 + 5 个内置中间件 | 洋葱模型管道，`handle(request, next)` |
| `TrimStrings` | `TrimStrings` | 自动裁剪请求参数首尾空白 |
| `ConvertEmptyStringsToNull` | `ConvertEmptyStringsToNull` | 空字符串转 null |
| `EncryptCookies` | `EncryptCookies` | AES/CBC 加解密 Cookie |
| `TrustProxies` | `TrustProxies` | 信任反向代理头 |
| `VerifyCsrfToken` | `VerifyCsrfToken` | CSRF 令牌校验 |
| `Request` | `Request` / `RequestFactory` | Laravel 风格请求对象 |
| `Response` | `Response` / `ResponseBuilder` | 链式响应构建 |
| 路由 | `Router` / `Route` / `RouteService` | 路由注册与分组 |
| `public` 目录 / `asset()` | `StaticResourceHandler` / `StaticResourceRoute` / `Router.serveStatic()` | 静态资源目录服务，对齐 Laravel `public` 与 `asset()` |

**重要设计原则**：所有内置中间件均为 `@Component`、无状态（stateless）、不可变（immutable）——所有字段为 `final`，构造后不可修改，无 setter。因此它们可被 Spring 容器作为单例管理，并安全地在并发请求间复用。

---

## 2. 依赖信息

### Maven 坐标

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>http</artifactId>
    <version>0.1.1</version>
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
│   ├── TrimStrings                    // 字符串裁剪（@Component，无状态）
│   ├── ConvertEmptyStringsToNull      // 空串转 null（@Component，无状态）
│   ├── EncryptCookies                 // Cookie 加解密（@Component，无状态）
│   ├── TrustProxies                   // 信任代理（@Component，无状态）
│   └── VerifyCsrfToken                // CSRF 校验（@Component，无状态）
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
│   ├── Router                         // 路由器（注册与分组）
│   ├── Route                          // 单条路由
│   └── RouteService                   // 路由规范化工具
└── controller
    └── Controllers                    // 控制器契约（含 Runner 函数式接口）
```

---

## 4. 中间件体系（Middleware）

### 4.1 Middleware 接口

`com.weacsoft.jaravel.vendor.middleware.Middleware`

函数式接口，定义中间件契约。采用洋葱模型：中间件可在调用 `next` 前预处理请求，在 `next` 返回后后处理响应。

```java
@FunctionalInterface
public interface Middleware {
    Response handle(Request request, NextFunction next);

    @FunctionalInterface
    interface NextFunction {
        Response apply(Request request);
    }
}
```

自定义中间件示例：

```java
@Component
public class LogMiddleware implements Middleware {
    @Override
    public Response handle(Request request, Middleware.NextFunction next) {
        long start = System.currentTimeMillis();
        Response response = next.apply(request);   // 调用下一层
        long cost = System.currentTimeMillis() - start;
        System.out.println(request.getRequest().getRequestURI() + " 耗时 " + cost + "ms");
        return response;
    }
}
```

### 4.2 TrimStrings

`com.weacsoft.jaravel.vendor.middleware.TrimStrings`

对齐 Laravel `TrimStrings`。自动裁剪 query 与 input 参数中字符串值的首尾空白。`@Component`，无状态、不可变。

| 构造器 | 说明 |
| --- | --- |
| `TrimStrings()` | 无排除字段 |
| `TrimStrings(String[] except)` | 指定不裁剪的字段名数组 |

| 方法 | 说明 |
| --- | --- |
| `Response handle(Request, NextFunction)` | 裁剪 query 与 input 后传递给下一层 |
| `protected void trimQueryParameters(Request)` | 裁剪 query 参数 |
| `protected void trimInputParameters(Request)` | 裁剪 input 参数 |
| `protected boolean isExcluded(String key)` | 是否在排除列表中 |
| `protected Object trimValue(Object value)` | 裁剪单个值（支持 String / List / String[]） |

裁剪逻辑：对 `String` 调用 `trim()`；对 `List` 逐元素裁剪；对 `String[]` 流式裁剪；其它类型原样返回。

```java
// 使用默认排除
TrimStrings mw = new TrimStrings();

// 排除 password 字段
TrimStrings mw = new TrimStrings(new String[]{"password", "password_confirmation"});
```

### 4.3 ConvertEmptyStringsToNull

`com.weacsoft.jaravel.vendor.middleware.ConvertEmptyStringsToNull`

对齐 Laravel `ConvertEmptyStringsToNull`。将 input 与 query 中的空字符串转为 `null`。`@Component`，无状态、不可变。

| 构造器 | 说明 |
| --- | --- |
| `ConvertEmptyStringsToNull(String... except)` | 指定排除字段 |
| `ConvertEmptyStringsToNull()` | 默认排除 `password`、`password_confirmation`、`current_password` |

| 方法 | 说明 |
| --- | --- |
| `Response handle(Request, NextFunction)` | 转换空串后传递给下一层 |
| `private boolean isExcluded(String name)` | 是否在排除列表中（大小写不敏感） |

```java
// 默认排除密码类字段
ConvertEmptyStringsToNull mw = new ConvertEmptyStringsToNull();

// 自定义排除
ConvertEmptyStringsToNull mw = new ConvertEmptyStringsToNull("password", "remark");
```

### 4.4 EncryptCookies

`com.weacsoft.jaravel.vendor.middleware.EncryptCookies`

对齐 Laravel `EncryptCookies`。使用 AES/CBC/PKCS5Padding 加解密 Cookie。请求阶段解密入站 Cookie，响应阶段加密出站 Cookie。`@Component`，无状态、不可变。

| 构造器 | 说明 |
| --- | --- |
| `EncryptCookies()` | 默认密钥（仅用于演示） |
| `EncryptCookies(String encryptionKey)` | 指定加密密钥 |
| `EncryptCookies(String encryptionKey, String[] except)` | 指定密钥与排除 Cookie 名 |

| 方法 | 说明 |
| --- | --- |
| `Response handle(Request, NextFunction)` | 先解密请求 Cookie，执行下一层，再加密响应 Cookie |
| `protected void decryptCookies(Request)` | 解密请求中的 Cookie |
| `protected void encryptCookies(Response)` | 加密响应中的 Cookie |
| `protected String encrypt(String value)` | AES 加密，返回 Base64（IV 前置） |
| `protected String decrypt(String encryptedValue)` | AES 解密 |
| `protected SecretKeySpec generateKey()` | 生成 32 字节 AES 密钥 |
| `protected IvParameterSpec generateIv()` | 生成 16 字节 IV（全 0） |
| `protected boolean isExcluded(String cookieName)` | 是否在排除列表中 |

加解密格式：密文 = Base64( IV(16字节) + 加密内容 )。解密失败时保留原值（不抛异常）。

```java
// 生产环境务必指定强密钥
EncryptCookies mw = new EncryptCookies("my-super-secret-key-32bytes!");

// 排除某些 Cookie 不加密
EncryptCookies mw = new EncryptCookies("my-key-32bytes-long-enough!!", new String[]{"XSRF-TOKEN"});
```

> 安全提示：默认构造器使用硬编码密钥，仅用于演示。生产环境必须通过构造器传入安全密钥（建议 32 字节）。

### 4.5 TrustProxies

`com.weacsoft.jaravel.vendor.middleware.TrustProxies`

对齐 Laravel `TrustProxies`。当请求来自受信任的代理时，从 `X-Forwarded-*` 等头中还原真实客户端信息。`@Component`，无状态、不可变。

| 构造器 | 说明 |
| --- | --- |
| `TrustProxies()` | 默认信任 `127.0.0.1`、`::1` |
| `TrustProxies(List<String> trustedProxies)` | 指定信任代理 IP 列表 |
| `TrustProxies(String[] trustedProxies)` | 指定信任代理 IP 数组 |

| 方法 | 说明 |
| --- | --- |
| `Response handle(Request, NextFunction)` | 若来自受信任代理则设置真实头，再传递给下一层 |
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

```java
TrustProxies mw = new TrustProxies(List.of("127.0.0.1", "10.0.0.1", "::1"));
```

### 4.6 VerifyCsrfToken

`com.weacsoft.jaravel.vendor.middleware.VerifyCsrfToken`

对齐 Laravel `VerifyCsrfToken`。对非安全方法（非 GET/HEAD/OPTIONS/TRACE）的请求校验 CSRF 令牌。`@Component`，无状态、不可变。

| 常量 | 值 | 说明 |
| --- | --- | --- |
| `CSRF_TOKEN_COOKIE_NAME` | `XSRF-TOKEN` | CSRF Cookie 名 |
| `CSRF_TOKEN_HEADER_NAME` | `X-XSRF-TOKEN` | CSRF 请求头名 |
| `CSRF_TOKEN_INPUT_NAME` | `_token` | 表单字段名 |
| `CSRF_SESSION_KEY` | `csrf_token` | Session 中存储 token 的 key |
| `SAFE_METHODS` | GET, HEAD, OPTIONS, TRACE | 不校验 CSRF 的安全方法 |

| 构造器 | 说明 |
| --- | --- |
| `VerifyCsrfToken()` | 无排除 URI |
| `VerifyCsrfToken(String[] except)` | 指定排除校验的 URI 数组 |

| 方法 | 说明 |
| --- | --- |
| `Response handle(Request, NextFunction)` | 安全校验或排除则放行并附加 token Cookie；否则校验 token |
| `protected boolean isSafeMethod(String method)` | 是否为安全方法 |
| `protected boolean isExcluded(Request)` | URI 是否在排除列表中 |
| `protected boolean verifyCsrfToken(Request)` | 校验 session token 与请求 token 是否一致 |
| `protected String getSessionToken(Request)` | 获取/生成 session 中的 CSRF token |
| `protected String getRequestToken(Request)` | 从头/表单/Cookie 中提取请求 token |
| `protected void addCsrfTokenCookie(Request, Response)` | 向响应附加 XSRF-TOKEN Cookie |
| `protected String generateToken()` | 用 SecureRandom 生成 32 字节 Base64URL token |

token 查找顺序：`X-XSRF-TOKEN` 请求头 -> `_token` 表单字段 -> `XSRF-TOKEN` Cookie。校验失败抛 `RuntimeException("CSRF token validation failed")`。

```java
// 排除 API 路径
VerifyCsrfToken mw = new VerifyCsrfToken(new String[]{"/api/webhook"});
```

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

## 6. 响应（Response / ResponseBuilder / JSONResponseResolver）

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
| `default String getContentType()` | 从响应头提取 Content-Type |
| `default Object getBody()` | 获取响应体（默认返回 getContent） |

### 6.2 ResponseBuilder

`com.weacsoft.jaravel.vendor.http.response.ResponseBuilder`

响应构建器，提供静态工厂方法快速构建各类响应。内部 `AbstractResponse` 抽象类提供 headers / cookies 的默认实现。

| 方法签名 | 说明 |
| --- | --- |
| `static Response ok()` | 200 状态，内容 `"ok"` |
| `static Response json(Object data)` | 200 状态，JSON 序列化，Content-Type: application/json |
| `static Response content(String content)` | 200 状态，纯文本，Content-Type: text/plain |
| `static Response view(String templateName, Map<String,Object> data)` | 200 状态，渲染 Blade 模板，Content-Type: text/html |
| `static Response file(byte[] data, String filename)` | 200 状态，文件下载，Content-Type: application/octet-stream |
| `static Response staticFile(byte[] data, String mimeType, int cacheMaxAge)` | 200 状态，静态文件响应，设置 Content-Type / Cache-Control / Content-Length |
| `static Response redirect(String url)` | 302 重定向，设置 Location 头 |
| `static Response unauthorized(String message)` | 401 未授权 |
| `static Response forbidden(String message)` | 403 禁止访问 |
| `static Response error(int status, String message)` | 自定义错误状态，JSON 格式 `{"message": "..."}` |
| `static String toJson(Object data)` | 将对象序列化为 JSON 字符串 |
| `static void setBladeEngine(Object engine)` | 注入 Blade 模板引擎实例（用于 `view`） |

```java
// JSON 响应
Response r1 = ResponseBuilder.json(Map.of("id", 1, "name", "Alice"));

// 视图响应（需先注入 BladeEngine）
Response r2 = ResponseBuilder.view("user.profile", Map.of("user", user));

// 文件下载
Response r3 = ResponseBuilder.file(fileBytes, "report.pdf");

// 重定向
Response r4 = ResponseBuilder.redirect("/login");

// 错误响应
Response r5 = ResponseBuilder.error(404, "资源不存在");
Response r6 = ResponseBuilder.unauthorized("请先登录");
```

> `view` 方法依赖 jblade 模块。若未通过 `setBladeEngine` 注入引擎，调用时会抛 `RuntimeException("jblade 模块未引入")`。

### 6.3 JSONResponseResolver

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
| `Route get(String uri, Controllers.Runner action)` | 注册 GET 路由 |
| `Route post(String uri, Controllers.Runner action)` | 注册 POST 路由 |
| `Route put(String uri, Controllers.Runner action)` | 注册 PUT 路由 |
| `Route delete(String uri, Controllers.Runner action)` | 注册 DELETE 路由 |
| `Route patch(String uri, Controllers.Runner action)` | 注册 PATCH 路由 |
| `Router all(String uri, Controllers.Runner action)` | 注册匹配所有方法的路由组 |
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
```

### 7.2 Route

`com.weacsoft.jaravel.vendor.route.Route`

单条路由，记录方法、URI、action、中间件、name、namespace、prefix，并能计算完整 URI / 名称 / 命名空间。

| 方法签名 | 说明 |
| --- | --- |
| `Route(String method, String uri, Controllers.Runner action)` | 构造器 |
| `Route middleware(Middleware... middleware)` | 添加路由级中间件，返回 this |
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

## 8. 控制器契约（Controllers）

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

---

## 9. 配置选项

`http` 模块本身不读取外部配置文件，各中间件的行为通过构造器参数配置：

| 中间件 | 可配置项 | 默认值 |
| --- | --- | --- |
| `TrimStrings` | `except`（排除字段） | 空数组 |
| `ConvertEmptyStringsToNull` | `except`（排除字段） | `password`, `password_confirmation`, `current_password` |
| `EncryptCookies` | `encryptionKey`（密钥）、`except`（排除 Cookie） | 硬编码演示密钥 / 空数组 |
| `TrustProxies` | `trustedProxies`（信任代理 IP） | `127.0.0.1`, `::1` |
| `VerifyCsrfToken` | `except`（排除 URI） | 空数组 |

全局中间件的注册由 `springboot` 模块的 `GlobalMiddlewareRegistry` 负责，详见对应模块文档。

---

## 10. 线程安全说明

| 类 | 线程安全性 | 说明 |
| --- | --- | --- |
| 所有中间件（`TrimStrings` / `ConvertEmptyStringsToNull` / `EncryptCookies` / `TrustProxies` / `VerifyCsrfToken`） | **线程安全** | `@Component` 单例，无状态、不可变（所有字段 `final`，无 setter），可安全在并发请求间复用 |
| `Middleware` / `NextFunction` | 线程安全 | 函数式接口，无状态 |
| `Request` | **单请求隔离** | 每次请求新建实例，内部 Map 非线程安全。`RequestFactory` 通过 `ThreadLocal` 维护当前请求，确保请求间隔离。不应跨请求共享同一个 `Request` |
| `RequestFactory` | 线程安全 | 静态方法无共享可变状态；`currentRequest` 为 `ThreadLocal`，天然线程隔离 |
| `ResponseBuilder` | 线程安全 | 静态工厂方法，每次返回新的 `AbstractResponse` 实例。`ObjectMapper` 为静态 final 线程安全。`bladeEngine` 静态字段在启动阶段单次写入后只读 |
| `JSONResponseResolver` | 线程安全 | 静态方法，`ObjectMapper` 为静态 final |
| `Router` / `Route` | 启动期安全 | 内部使用 `CopyOnWriteArrayList`，适合启动阶段注册、运行时只读。运行时动态增删路由虽线程安全但开销较大 |
| `RouteService` | 线程安全 | 纯静态无状态方法 |
| `StaticResourceHandler` | 线程安全 | 字段 `final`（location/cacheMaxAge），MIME 表为静态 final 只读 Map，多请求只读复用 |
| `StaticResourceRoute` | 启动期安全 | 通过 `Router.serveStatic()` 在启动阶段构造，handlers 列表构造后只读；运行时仅读 |
| `StaticResourceProperties` | 线程安全 | 配置 POJO，启动阶段注入后只读 |
| `Controllers.Runner` | 取决于实现 | 函数式接口，线程安全性取决于具体 action 实现 |

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
