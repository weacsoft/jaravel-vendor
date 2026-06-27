# http AI-API Reference

> Module: `http` | Package: `com.weacsoft.jaravel.vendor` | Version: 0.1.0

## Overview
http 模块提供 Laravel 风格的 HTTP 处理层，包含中间件管道（Middleware）、请求（Request）与响应（Response）抽象、路由系统（Route/Router）、控制器接口（Controllers）、静态资源目录服务（StaticResource）以及一组内置中间件（VerifyCsrfToken、TrustProxies、EncryptCookies、TrimStrings、ConvertEmptyStringsToNull）。它将 Servlet API 封装为对齐 Laravel 的 Request/Response 对象，支持链式中间件管道、路由分组，并通过 `Router.serveStatic()` 提供对齐 Laravel `public` 目录与 `asset()` 辅助函数的静态资源服务。

## Classes & Interfaces

### Middleware
- **Type**: interface (functional)
- **Package**: `com.weacsoft.jaravel.vendor.middleware`
- **Description**: 中间件契约，对齐 Laravel Middleware。每个中间件在请求到达控制器前/响应返回后执行逻辑，通过 `next.apply(request)` 将请求传递给下一层。
- **Annotations**: `@FunctionalInterface`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `handle` | `Request request, NextFunction next` | `Response` | 处理请求并返回响应 |

#### Nested Types
- **Middleware.NextFunction** (interface, functional): `Response apply(Request request)` - 传递给下一层中间件或控制器的函数

#### Usage Example
```java
Middleware authMiddleware = (request, next) -> {
    if (!request.hasSession("user_id")) {
        return ResponseBuilder.unauthorized("请先登录");
    }
    return next.apply(request);
};

// 注册到路由
router.middleware(authMiddleware).get("/profile", req -> {
    return ResponseBuilder.json(Map.of("user", req.session("user_id")));
});
```

---

### Route
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.route`
- **Description**: 路由定义，包含 HTTP 方法、URI、控制器动作和中间件链。支持链式调用设置 name、prefix、middleware。
- **Annotations**: Lombok `@Getter`/`@Setter`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `Route` | `String method, String uri, Controllers.Runner action` | 构造方法 | 创建路由 |
| `middleware` | `Middleware... middleware` | `Route` | 添加中间件（链式） |
| `name` | `String name` | `Route` | 设置路由名称（链式） |
| `prefix` | `String prefix` | `Route` | 设置 URI 前缀（链式） |
| `generateFullUri` | 无 | `String` | 生成完整 URI（含父路由前缀） |
| `getFullUri` | 无 | `String` | 获取完整 URI |
| `generateFullNamespace` | 无 | `String` | 生成完整命名空间 |
| `getFullName` | 无 | `String` | 获取完整路由名 |
| `getFullNamespace` | 无 | `String` | 获取完整命名空间 |
| `getMiddlewares` | 无 | `List<Middleware>` | 获取所有中间件（含父路由的） |
| `getMethod` | 无 | `String` | 获取 HTTP 方法 |
| `getAction` | 无 | `Controllers.Runner` | 获取控制器动作 |
| `getName` / `setName` | `String` | `String`/`void` | 路由名称 |
| `getNamespace` / `setNamespace` | `String` | `String`/`void` | 命名空间 |
| `getPrefix` / `setPrefix` | `String` | `String`/`void` | URI 前缀 |

#### Nested Types
- **Route.Group** (enum): `NAMESPACE`, `PREFIX`, `NAME` - 路由分组属性类型

#### Usage Example
```java
Route route = router.get("/users/{id}", req -> {
    Long id = req.routeParam("id", Long.class);
    return ResponseBuilder.json(userService.find(id));
});
route.name("users.show").middleware(authMiddleware);
```

---

### Router
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.route`
- **Description**: 路由器，管理路由集合和子路由器。支持 HTTP 动词方法（get/post/put/delete/patch/all）、路由分组（group）、中间件链，以及静态资源目录服务（serveStatic，对齐 Laravel `public` 目录）。
- **Annotations**: Lombok `@Getter`/`@Setter`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `middleware` | `Middleware... middleware` | `Router` | 添加中间件（链式） |
| `get` | `String uri, Controllers.Runner action` | `Route` | 注册 GET 路由 |
| `post` | `String uri, Controllers.Runner action` | `Route` | 注册 POST 路由 |
| `put` | `String uri, Controllers.Runner action` | `Route` | 注册 PUT 路由 |
| `delete` | `String uri, Controllers.Runner action` | `Route` | 注册 DELETE 路由 |
| `patch` | `String uri, Controllers.Runner action` | `Route` | 注册 PATCH 路由 |
| `all` | `String uri, Controllers.Runner action` | `Router` | 注册多方法路由（GET/POST/PUT/DELETE/PATCH） |
| `addRoute` | `String method, String uri, Controllers.Runner action` | `Route` | 添加单条路由 |
| `addMultiRoute` | `String[] method, String uri, Controllers.Runner action` | `Router` | 添加多方法路由 |
| `group` | `Map<Route.Group, String> params, Consumer<Router> router` | `Router` | 创建路由分组（设置 namespace/prefix/name） |
| `serveStatic` | `String urlPrefix, String location, int cacheMaxAge` | `StaticResourceRoute` | 注册静态资源目录（指定缓存时间），注册 GET `{urlPrefix}/{path}` 路由 |
| `serveStatic` | `String urlPrefix, String location` | `StaticResourceRoute` | 注册静态资源目录（默认缓存 1 小时） |
| `serveStatic` | `String urlPrefix, List<String> locations, int cacheMaxAge` | `StaticResourceRoute` | 注册多目录静态资源（按顺序回退查找） |
| `getAllRoutes` | 无 | `List<Route>` | 获取所有路由（含子路由器的） |
| `getAllMiddlewares` | 无 | `List<Middleware>` | 获取所有中间件（含父路由器的） |

#### Usage Example
```java
Router router = new Router();

// 简单路由
router.get("/users", req -> ResponseBuilder.json(userService.all()));

// 路由分组
router.group(Map.of(
    Route.Group.PREFIX, "/api/v1",
    Route.Group.NAMESPACE, "Api\\V1"
), api -> {
    api.get("/posts", req -> ResponseBuilder.json(postService.all()));
    api.post("/posts", req -> {
        String title = req.input("title");
        return ResponseBuilder.json(postService.create(title));
    });
});

// 中间件
router.middleware(authMiddleware).get("/profile", req -> {
    return ResponseBuilder.json(Map.of("user", req.session("user")));
});
```

---

### RouteService
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.route`
- **Description**: 路由工具服务，提供 URI、命名空间和路由名的规范化方法。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `normalizeUri` | `String uri` | `String` | 规范化 URI（去多余斜杠、补前导斜杠、去尾部斜杠） |
| `normalizeNamesapce` | `String namespace` | `String` | 规范化命名空间（去多余点号） |
| `normalizeName` | `String name` | `String` | 规范化路由名（去多余点号） |

---

### Request
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.http.request`
- **Description**: Laravel 风格请求对象，封装 query/input/file/header/cookie/session/routeParams/attributes 等数据。支持链式添加、替换、移除操作，以及类型化获取。

#### Methods (主要)

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `get` | 无 | `Map<String, Object>` | 获取所有 query+input 数据 |
| `get` | `String key` | `String` | 获取参数值（query+input） |
| `get` | `String key, String defaultValue` | `String` | 获取参数值，带默认值 |
| `get` | `String key, T defaultValue` | `<T> T` | 获取参数值（泛型默认值） |
| `get` | `String key, Class<T> clazz` | `<T> T` | 按类型获取参数值 |
| `all` | 无 | `Map<String, Object>` | 获取所有数据（query+input） |
| `input` | `String key` | `String` | 获取 input 参数 |
| `input` | `String key, Class<T> clazz` | `<T> T` | 按类型获取 input 参数 |
| `query` | `String key` | `String` | 获取 query 参数 |
| `query` | `String key, Class<T> clazz` | `<T> T` | 按类型获取 query 参数 |
| `header` | `String key` | `String` | 获取请求头 |
| `cookie` | `String key` | `String` | 获取 Cookie 值 |
| `session` | `String key` | `String` | 获取 Session 值 |
| `file` | `String key` | `MultipartFile` | 获取上传文件 |
| `files` | `String key` | `List<MultipartFile>` | 获取上传文件列表 |
| `has` | `String key` | `boolean` | 判断参数是否存在 |
| `hasFile` | `String key` | `boolean` | 判断文件是否存在 |
| `hasHeader` | `String key` | `boolean` | 判断请求头是否存在 |
| `hasCookie` | `String key` | `boolean` | 判断 Cookie 是否存在 |
| `hasSession` | `String key` | `boolean` | 判断 Session 是否存在 |
| `routeParam` | `String key` | `String` | 获取路由参数 |
| `routeParam` | `String key, Class<T> clazz` | `<T> T` | 按类型获取路由参数 |
| `routeParams` | 无 | `Map<String, Object>` | 获取所有路由参数 |
| `ip` | 无 | `String` | 获取客户端 IP（优先 X-Forwarded-For） |
| `setAttribute` / `getAttribute` | `String key, Object value` | `void`/`Object` | 中间件间传递属性 |
| `addInput` / `addQuery` / `addHeader` / `addFile` / `addCookie` / `addSession` | `String key, Object value` | `void` | 添加数据 |
| `replaceInput` / `replaceQuery` / `replaceHeader` / `replaceFile` / `replaceCookie` / `replaceSession` | `String key, Object value` | `void` | 替换数据 |
| `removeInput` / `removeQuery` / `removeHeader` / `removeFile` / `removeCookie` / `removeSession` | `String key` | `void` | 移除数据 |
| `getRequest` | 无 | `HttpServletRequest` | 获取底层 Servlet 请求 |
| `setRequest` | `HttpServletRequest request` | `void` | 设置底层 Servlet 请求并自动解析 headers/cookies/session |

#### Usage Example
```java
// 在控制器中使用
Response handle(Request request) {
    String name = request.input("name", "");
    int age = request.input("age", Integer.class);
    String token = request.header("Authorization");
    String clientIp = request.ip();
    Long userId = request.routeParam("id", Long.class);

    if (request.hasFile("avatar")) {
        MultipartFile avatar = request.file("avatar");
        // 处理文件上传
    }
    return ResponseBuilder.json(Map.of("name", name, "age", age));
}
```

---

### RequestFactory
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.http.request`
- **Description**: 请求工厂，从 HttpServletRequest 或 Spring ServerRequest 构建 Request 对象。自动解析 query、JSON body、form-urlencoded、multipart 等数据。使用 ThreadLocal 维护当前请求。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `getCurrentRequest` | 无 | `Request` | 获取当前线程的 Request |
| `setCurrentRequest` | `Request request` | `void` | 设置当前线程的 Request |
| `clearCurrentRequest` | 无 | `void` | 清除当前线程的 Request |
| `buildFromHttpServletRequest` | `HttpServletRequest baseRequest` | `Request` | 从 Servlet 请求构建 Request |
| `buildFromServerRequest` | `ServerRequest baseRequest` | `Request` | 从 Spring ServerRequest 构建 Request |

#### Usage Example
```java
Request request = RequestFactory.buildFromHttpServletRequest(servletRequest);
String name = request.input("name");
Request current = RequestFactory.getCurrentRequest();
```

---

### Response
- **Type**: interface
- **Package**: `com.weacsoft.jaravel.vendor.http.response`
- **Description**: 响应抽象接口，定义状态码、响应头、Cookie 和内容体的标准方法。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `getStatus` | 无 | `int` | 获取 HTTP 状态码 |
| `getHeaders` | 无 | `Map<String, List<String>>` | 获取响应头 |
| `addHeader` | `String name, String value` | `void` | 添加响应头 |
| `getCookies` | 无 | `Cookie[]` | 获取 Cookie 数组 |
| `addCookie` | `Cookie cookie` | `void` | 添加 Cookie |
| `addCookie` | `String name, String value` | `void` | 添加 Cookie（名值对） |
| `getContent` | 无 | `String` | 获取响应内容 |
| `getBytes` | 无 | `byte[]` | 获取字节响应体（default null） |
| `getContentType` | 无 | `String` | 从响应头提取 Content-Type（default 方法） |
| `getBody` | 无 | `Object` | 获取响应体（default 返回 getContent） |

---

### ResponseBuilder
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.http.response`
- **Description**: 响应构建器，提供静态工厂方法创建各类响应（JSON、视图、文本、文件下载、静态文件、错误、重定向等）。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `setBladeEngine` | `Object engine` | `void` | 设置 Blade 模板引擎实例 |
| `ok` | 无 | `Response` | 创建 200 "ok" 响应 |
| `view` | `String templateName, Map<String, Object> data` | `Response` | 渲染 Blade 模板返回 HTML 响应 |
| `json` | `Object data` | `Response` | 创建 JSON 响应 |
| `content` | `String content` | `Response` | 创建纯文本响应 |
| `file` | `byte[] data, String filename` | `Response` | 创建文件下载响应 |
| `staticFile` | `byte[] data, String mimeType, int cacheMaxAge` | `Response` | 创建静态文件响应（设置 Content-Type / Cache-Control / Content-Length，供静态资源服务使用） |
| `unauthorized` | `String message` | `Response` | 创建 401 未授权响应 |
| `forbidden` | `String message` | `Response` | 创建 403 禁止访问响应 |
| `error` | `int status, String message` | `Response` | 创建错误响应 |
| `redirect` | `String url` | `Response` | 创建 302 重定向响应 |
| `toJson` | `Object data` | `String` | 将对象序列化为 JSON 字符串 |

#### Usage Example
```java
// JSON 响应
return ResponseBuilder.json(Map.of("code", 200, "data", users));

// 视图响应
return ResponseBuilder.view("user.profile", Map.of("user", user));

// 错误响应
return ResponseBuilder.unauthorized("请先登录");

// 文件下载
return ResponseBuilder.file(fileBytes, "report.pdf");

// 重定向
return ResponseBuilder.redirect("/login");
```

---

### JSONResponseResolver
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.http.response`
- **Description**: JSON 响应解析器，提供标准化的成功/错误响应 Map 构造方法。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `createErrorResponse` | `String message` | `Map<String, Object>` | 创建错误响应 Map |
| `createSuccessResponse` | 无 | `Map<String, Object>` | 创建成功响应 Map（无数据） |
| `createSuccessResponse` | `Object[] data` | `Map<String, Object>` | 创建成功响应 Map（带数据） |
| `createResponse` | `boolean success, String message, Object[] data` | `Map<String, Object>` | 创建标准响应 Map |

#### Usage Example
```java
Map<String, Object> success = JSONResponseResolver.createSuccessResponse(new Object[]{user});
Map<String, Object> error = JSONResponseResolver.createErrorResponse("参数错误");
```

---

### Controllers
- **Type**: interface
- **Package**: `com.weacsoft.jaravel.vendor.controller`
- **Description**: 控制器接口，定义路由处理函数契约。

#### Nested Types
- **Controllers.Runner** (interface, functional): `Response handle(Request request)` - 路由处理函数

#### Usage Example
```java
Controllers.Runner handler = request -> {
    String name = request.input("name");
    return ResponseBuilder.json(Map.of("message", "Hello, " + name));
};
```

---

### VerifyCsrfToken
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.middleware`
- **Description**: CSRF 令牌校验中间件，对齐 Laravel VerifyCsrfToken。对安全方法（GET/HEAD/OPTIONS/TRACE）和排除 URI 跳过校验，其余请求验证 CSRF 令牌。
- **Annotations**: `@Component`
- **Implements**: `Middleware`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `VerifyCsrfToken` | 无 | 构造方法 | 默认构造（无排除 URI） |
| `VerifyCsrfToken` | `String[] except` | 构造方法 | 指定排除校验的 URI |
| `handle` | `Request request, NextFunction next` | `Response` | 执行 CSRF 校验 |

---

### TrustProxies
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.middleware`
- **Description**: 信任代理中间件，对齐 Laravel TrustProxies。配置可信代理 IP，从 X-Forwarded-* 头提取真实客户端信息。
- **Annotations**: `@Component`
- **Implements**: `Middleware`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `TrustProxies` | 无 | 构造方法 | 默认信任 127.0.0.1 和 ::1 |
| `TrustProxies` | `List<String> trustedProxies` | 构造方法 | 指定可信代理列表 |
| `TrustProxies` | `String[] trustedProxies` | 构造方法 | 指定可信代理数组 |
| `handle` | `Request request, NextFunction next` | `Response` | 处理信任代理头 |

---

### EncryptCookies
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.middleware`
- **Description**: Cookie 加密中间件，对齐 Laravel EncryptCookies。使用 AES/CBC/PKCS5Padding 加密 Cookie 值，请求时解密，响应时加密。
- **Annotations**: `@Component`
- **Implements**: `Middleware`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `EncryptCookies` | 无 | 构造方法 | 默认加密密钥 |
| `EncryptCookies` | `String encryptionKey` | 构造方法 | 指定加密密钥 |
| `EncryptCookies` | `String encryptionKey, String[] except` | 构造方法 | 指定密钥和排除 Cookie |
| `handle` | `Request request, NextFunction next` | `Response` | 解密请求 Cookie，加密响应 Cookie |

---

### TrimStrings
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.middleware`
- **Description**: 字符串裁剪中间件，对齐 Laravel TrimStrings。自动裁剪 input 和 query 参数的首尾空白。
- **Annotations**: `@Component`
- **Implements**: `Middleware`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `TrimStrings` | 无 | 构造方法 | 默认无排除字段 |
| `TrimStrings` | `String[] except` | 构造方法 | 指定排除裁剪的字段 |
| `handle` | `Request request, NextFunction next` | `Response` | 裁剪请求参数 |

---

### ConvertEmptyStringsToNull
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.middleware`
- **Description**: 空字符串转 Null 中间件，对齐 Laravel ConvertEmptyStringsToNull。将空字符串参数转为 null，默认排除 password 相关字段。
- **Annotations**: `@Component`
- **Implements**: `Middleware`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `ConvertEmptyStringsToNull` | 无 | 构造方法 | 默认排除 password/password_confirmation/current_password |
| `ConvertEmptyStringsToNull` | `String... except` | 构造方法 | 指定排除字段 |
| `handle` | `Request request, NextFunction next` | `Response` | 转换空字符串为 null |

---

### StaticResourceProperties
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.http.staticresource`
- **Description**: 静态资源配置属性，对齐 Laravel 的 `public` 目录和 `asset()` 辅助函数。通过 `jaravel.http.static-resource` 前缀在 `application.yml` 中配置。

#### Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `enabled` | `boolean` | `true` | 是否启用静态资源服务 |
| `urlPrefix` | `String` | `/static` | URL 前缀（对齐 Laravel mix） |
| `defaultLocation` | `String` | `classpath:/static/` | 默认资源目录（classpath 或文件系统） |
| `cacheMaxAge` | `int` | `3600` | 缓存时间（秒），写入 `Cache-Control: max-age=N` |
| `directoryListing` | `boolean` | `false` | 是否允许目录列表（默认关闭） |

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `isEnabled` / `setEnabled` | `boolean enabled` | `boolean` / `void` | 启用状态 |
| `getUrlPrefix` / `setUrlPrefix` | `String urlPrefix` | `String` / `void` | URL 前缀 |
| `getDefaultLocation` / `setDefaultLocation` | `String defaultLocation` | `String` / `void` | 默认资源目录 |
| `getCacheMaxAge` / `setCacheMaxAge` | `int cacheMaxAge` | `int` / `void` | 缓存时间（秒） |
| `isDirectoryListing` / `setDirectoryListing` | `boolean directoryListing` | `boolean` / `void` | 目录列表开关 |

#### Usage Example
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

---

### StaticResourceHandler
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.http.staticresource`
- **Description**: 静态资源处理器，负责从 classpath 或文件系统读取静态文件，自动推断 MIME 类型，设置缓存头，并防范路径穿越攻击。支持 `classpath:`（打包在 JAR 内）和 `file:`（外部文件系统）两种资源位置前缀。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `StaticResourceHandler` | `String location, int cacheMaxAge` | 构造方法 | 创建处理器（location 如 `classpath:/static/` 或 `file:./public/`，自动补全尾部 `/`） |
| `load` | `String relativePath` | `ResourceResult` | 加载资源；不存在或路径不安全返回 `null` |
| `sanitizePath` (static) | `String path` | `String` | 路径安全处理，拒绝包含 `..` 或 `\` 的路径穿越，规范化多余 `/`；不安全返回 `null` |
| `guessMimeType` (static) | `String path` | `String` | 根据文件扩展名推断 MIME 类型，未知返回 `application/octet-stream` |
| `getLocation` | 无 | `String` | 返回资源位置（用于日志和调试） |

#### Nested Types
- **StaticResourceHandler.ResourceResult** (class): 静态资源加载结果
  - `byte[] getContent()` — 资源字节内容
  - `String getMimeType()` — MIME 类型
  - `int getCacheMaxAge()` — 缓存时间（秒）
  - `int getContentLength()` — 内容长度（字节）

#### Usage Example
```java
StaticResourceHandler handler = new StaticResourceHandler("classpath:/static/", 3600);
StaticResourceHandler.ResourceResult result = handler.load("css/app.css");
if (result != null) {
    byte[] content = result.getContent();        // 文件字节
    String mime    = result.getMimeType();       // text/css; charset=utf-8
    int maxAge     = result.getCacheMaxAge();    // 3600
}

// 静态方法
String mime = StaticResourceHandler.guessMimeType("app.css");   // text/css; charset=utf-8
String safe = StaticResourceHandler.sanitizePath("../etc/passwd"); // null（路径穿越被拦截）
```

---

### StaticResourceRoute
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.http.staticresource`
- **Description**: 静态资源路由处理器。注册到 Router 后，匹配 URL 前缀的 GET 请求会被此处理器拦截，从配置的资源目录加载文件并返回。支持多目录按顺序回退查找，全部未命中返回 404。
- **Implements**: `Controllers.Runner`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `StaticResourceRoute` | `String urlPrefix, String location, int cacheMaxAge` | 构造方法 | 单目录静态资源路由 |
| `StaticResourceRoute` | `String urlPrefix, List<String> locations, int cacheMaxAge` | 构造方法 | 多目录静态资源路由（按顺序回退查找） |
| `handle` | `Request request` | `Response` | 从 `routeParam("path")` 提取相对路径，按目录顺序查找；命中返回 `ResponseBuilder.staticFile(...)`，未命中返回 404 |
| `getUrlPrefix` | 无 | `String` | 返回 URL 前缀 |

#### Usage Example
```java
// 单目录
StaticResourceRoute route = new StaticResourceRoute("/static", "classpath:/static/", 3600);

// 多目录回退（先文件系统，再 classpath）
StaticResourceRoute route2 = new StaticResourceRoute("/static",
        List.of("file:./public/", "classpath:/static/"), 3600);

// 通常通过 Router.serveStatic() 注册，无需手动构造
router.serveStatic("/static", "classpath:/static/", 3600);
router.serveStatic("/static", List.of("file:./public/", "classpath:/static/"), 3600);
```
