# http AI-API Reference

> Module: `http` | Package: `com.weacsoft.jaravel.vendor` | Version: 0.1.2

## Overview
http 模块提供 Laravel 风格的 HTTP 处理层，包含中间件管道（Middleware）、请求（Request）与响应（Response）抽象、路由系统（Route/Router）、控制器接口（Controllers）与控制器注册解析（ControllerRegistry / ControllerActionResolver）、静态资源目录服务（StaticResource）以及一组内置中间件（VerifyCsrfToken、TrustProxies、EncryptCookies、TrimStrings、ConvertEmptyStringsToNull）。它将 Servlet API 封装为对齐 Laravel 的 Request/Response 对象，支持链式中间件管道、路由分组、控制器动作路由（`"Controller::action"` 字符串或 `Class + 方法名`，懒解析），并通过 `Router.serveStatic()` 提供对齐 Laravel `public` 目录与 `asset()` 辅助函数的静态资源服务。内置中间件为普通类（不再是 Spring `@Component`），通过继承并标注 `@MiddlewareAlias` 注册，配置通过重写受保护方法完成。

## Classes & Interfaces

### Middleware
- **Type**: interface (functional)
- **Package**: `com.weacsoft.jaravel.vendor.middleware`
- **Description**: 中间件契约，对齐 Laravel Middleware。每个中间件在请求到达控制器前/响应返回后执行逻辑，通过 `next.apply(request)` 将请求传递给下一层。
- **Annotations**: `@FunctionalInterface`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `handle` | `Request request, NextFunction next, String... params` | `Response` | 处理请求并返回响应；params 来自别名表达式 |

#### Nested Types
- **Middleware.NextFunction** (interface, functional): `Response apply(Request request)` - 传递给下一层中间件或控制器的函数

#### Usage Example
```java
Middleware authMiddleware = (request, next, params) -> {
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

### MiddlewareAliasRegistry
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.http.middleware`
- **Description**: 中间件别名注册表，对齐 Laravel `App\Http\Kernel::$routeMiddleware` 别名机制。内部维护**两张映射表**：`Map<String, Middleware>`（别名→`Middleware`）与 `Map<Class<?>, Middleware>`（Class→`Middleware`）。支持解析 `auth:api,admin` 形式的别名/类名表达式。`resolve(String)` 在解析时先查别名表，未命中则回退类名查找（依次尝试简单名与全限定名）；解析时将表达式中的参数通过闭包烘焙（bake）到返回的 `Middleware` 包装 lambda 中（`(request, next, ignored) -> original.handle(request, next, bakedParams)`）。`register(String, Middleware)` 注册别名时会**自动**同时注册 Class 映射；`alias` 为 null/空时仅注册 Class 映射。通过 `getGlobal()` 获取全局静态实例，供 `Route` / `Router` 在解析中间件引用时使用。
- **Annotations**: 无

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `getGlobal` (static) | 无 | `MiddlewareAliasRegistry` | 获取全局静态实例 |
| `register` | `String alias, Middleware middleware` | `void` | 注册中间件别名（引用时参数由别名表达式烘焙注入）；**同时自动注册 Class 映射**；`alias` 为 null/空时仅注册 Class 映射 |
| `register` | `Middleware middleware` | `void` | 仅按 Class 注册中间件（不设别名），写入 Class→`Middleware` 表 |
| `resolve` | `String expression` | `Middleware` | 解析别名/类名表达式为中间件实例（参数烘焙到返回的包装 lambda 中）；先查别名表，未命中回退类名查找（简单名、全限定名）；均未注册抛 `IllegalArgumentException` |
| `resolve` | `Class<?> clazz` | `Middleware` | 按 Class 解析中间件实例（无参数）；未注册抛 `IllegalArgumentException` |
| `resolve` | `Class<?> clazz, String... params` | `Middleware` | 按 Class 解析中间件实例并烘焙参数；未注册抛 `IllegalArgumentException` |
| `resolveAll` | `List<String> expressions` | `List<Middleware>` | 批量解析别名/类名表达式（保持顺序） |
| `isRegistered` | `String alias` | `boolean` | 别名是否已注册 |
| `isClassRegistered` | `Class<?> clazz` | `boolean` | Class 是否已注册 |
| `getRegisteredAliases` | 无 | `Set<String>` | 获取所有已注册别名（不可修改视图） |
| `getRegisteredClasses` | 无 | `Set<Class<?>>` | 获取所有已注册 Class（不可修改视图） |
| `clear` | 无 | `void` | 清除所有别名与 Class 映射（主要用于测试） |
| `registerGlobal` (static) | `String alias, Middleware middleware` | `void` | 向全局注册表注册别名 |
| `resolveGlobal` (static) | `String expression` | `Middleware` | 通过全局注册表解析别名/类名表达式 |

#### Alias / Class Name Expression Syntax

| 表达式 | 标识 | 参数 | 说明 |
|--------|------|------|------|
| `"auth"` | `auth` | `[]` | 无参数 |
| `"auth:api"` | `auth` | `["api"]` | 单参数 |
| `"auth:api,admin"` | `auth` | `["api", "admin"]` | 多参数 |
| `"AuthMiddleware:api"` | `AuthMiddleware` | `["api"]` | 类名 + 参数 |

冒号分隔标识（别名或类名）与参数，逗号分隔多个参数，标识两端空格自动裁剪。`resolve(String)` 先查别名表，未命中则回退类名查找（依次尝试简单名与全限定名）。

#### Reference Modes

`Route` / `Router` 的 `middleware(...)` 支持三种中间件引用模式：

| 模式 | 调用方式 | 说明 |
|--------|----------|------|
| **别名** | `middleware("auth:api")` | 字符串别名 + 参数；需通过 `@MiddlewareAlias` 或 `register(alias, mw)` 注册 |
| **Class 对象** | `middleware(AuthMiddleware.class)` 或 `middleware(AuthMiddleware.class, "api", "admin")` | Class + 可选参数；内部创建 `ClassMiddlewareSpec` |
| **类名字符串** | `middleware("AuthMiddleware:api")` | 类名字符串，语法与别名一致 |

> Class 对象与类名字符串模式均依赖注册表中已注册的 Class 映射；`register(alias, middleware)` 会自动注册 Class 映射。

#### Usage Example
```java
// 注册别名（同时自动注册 Class 映射）
Middleware logMiddleware = (request, next, params) -> {
    System.out.println(request.getRequest().getRequestURI());
    return next.apply(request);
};
MiddlewareAliasRegistry.registerGlobal("log", logMiddleware);

// 注册参数化中间件别名（params 来自别名表达式，由 handle 的 String... params 接收）
Middleware authMiddleware = (request, next, params) -> {
    String guard = params.length > 0 ? params[0] : "web";
    if (!isAuthorized(request, guard)) {
        return ResponseBuilder.unauthorized("未授权");
    }
    return next.apply(request);
};
MiddlewareAliasRegistry.registerGlobal("auth", authMiddleware);

// 仅按 Class 注册（无别名）
MiddlewareAliasRegistry.getGlobal().register(rateLimitMiddleware);

// 解析别名
Middleware auth = MiddlewareAliasRegistry.resolveGlobal("auth:api");   // 参数 ["api"] 烘焙到返回的包装实例
Middleware log  = MiddlewareAliasRegistry.resolveGlobal("log");        // 无参数

// 按 Class 解析
Middleware rl = MiddlewareAliasRegistry.getGlobal().resolve(RateLimitMiddleware.class);
Middleware rl2 = MiddlewareAliasRegistry.getGlobal().resolve(RateLimitMiddleware.class, "100", "60");

// 路由中使用别名（Route / Router 的 middleware(String...) 重载）
router.get("/api/users", req -> ResponseBuilder.json(users))
      .middleware("auth:api", "log");
// 执行顺序：auth(guard=api) → log

// 路由中通过 Class 对象引用（middleware(Class<?>, String...) 重载）
router.get("/api/orders", req -> ResponseBuilder.json(orders))
      .middleware(AuthMiddleware.class);                  // Class 无参数
router.get("/api/admin", req -> ResponseBuilder.json(adminData))
      .middleware(AuthMiddleware.class, "api", "admin");  // Class + 参数（内部创建 ClassMiddlewareSpec）

// 路由中通过类名字符串引用（语法同别名）
router.get("/api/logs", req -> ResponseBuilder.json(logs))
      .middleware("AuthMiddleware:api");

// 混合使用直接中间件、别名与类引用（保持插入顺序）
router.get("/mixed", req -> ResponseBuilder.ok())
      .middleware(corsMiddleware)                    // 直接中间件
      .middleware("auth:api")                        // 别名
      .middleware(AuthMiddleware.class, "admin")     // Class + 参数
      .middleware("log");                            // 别名
```

---

### ClassMiddlewareSpec
- **Type**: class (internal)
- **Package**: `com.weacsoft.jaravel.vendor.http.middleware`
- **Description**: 类引用中间件规格，封装一个 `Class<?>` 与一组 `String... params`，表示「按 Class 引用且带参数」的中间件规格。开发者**无需直接构造**该类——它由 `Route.middleware(Class<?> clazz, String... params)` 与 `Router.middleware(Class<?> clazz, String... params)` 在内部创建，并在 `getMiddlewares()` / `getAllMiddlewares()` 解析时通过 `MiddlewareAliasRegistry.resolve(Class, String...)` 解析为 `Middleware` 实例。
- **Annotations**: 无

#### Fields

| Field | Type | Description |
|-------|------|-------------|
| `clazz` | `Class<?>` | 中间件的目标 Class |
| `params` | `String[]` | 烘焙到中间件的参数（可空） |

#### Usage Example
```java
// 开发者无需直接构造 ClassMiddlewareSpec，以下仅说明内部行为：
// 调用 router.middleware(AuthMiddleware.class, "api", "admin")
// 等价于内部创建 new ClassMiddlewareSpec(AuthMiddleware.class, "api", "admin")
// 并在解析时通过 MiddlewareAliasRegistry.resolve(clazz, params) 解析为中间件实例

router.get("/api/admin", req -> ResponseBuilder.json(adminData))
      .middleware(AuthMiddleware.class, "api", "admin");
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
| `middleware` | `String... aliases` | `Route` | 通过别名/类名表达式添加中间件（链式），通过全局 `MiddlewareAliasRegistry` 解析 |
| `middleware` | `Class<?> clazz, String... params` | `Route` | 通过 Class 对象引用中间件（可选参数，链式）；内部创建 `ClassMiddlewareSpec`，由全局 `MiddlewareAliasRegistry` 解析 |
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
- **Description**: 路由器，管理路由集合和子路由器。支持 HTTP 动词方法（get/post/put/delete/patch/all）、路由分组（group）、中间件链，以及静态资源目录服务（serveStatic，对齐 Laravel `public` 目录）。各 HTTP 动词方法除接受 `Controllers.Runner` 外，还提供**控制器动作重载**：字符串形式 `"Controller::action"` 与 Class 形式 `Class<?> + methodName`，内部通过 `ControllerActionResolver` 将控制器引用解析为 `Controllers.Runner`，采用**懒解析**——控制器引用在首次请求时才解析，而非注册路由时。
- **Annotations**: Lombok `@Getter`/`@Setter`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `middleware` | `Middleware... middleware` | `Router` | 添加中间件（链式） |
| `middleware` | `String... aliases` | `Router` | 通过别名/类名表达式添加中间件（链式），通过全局 `MiddlewareAliasRegistry` 解析 |
| `middleware` | `Class<?> clazz, String... params` | `Router` | 通过 Class 对象引用中间件（可选参数，链式）；内部创建 `ClassMiddlewareSpec`，由全局 `MiddlewareAliasRegistry` 解析 |
| `get` | `String uri, Controllers.Runner action` | `Route` | 注册 GET 路由 |
| `get` | `String uri, String controllerAction` | `Route` | 注册 GET 路由（控制器动作字符串，如 `"UserController::list"`）；控制器引用在首次请求时懒解析 |
| `get` | `String uri, Class<?> controllerClass, String methodName` | `Route` | 注册 GET 路由（Class + 方法名，如 `UserController.class, "list"`）；控制器引用在首次请求时懒解析 |
| `post` | `String uri, Controllers.Runner action` | `Route` | 注册 POST 路由 |
| `post` | `String uri, String controllerAction` | `Route` | 注册 POST 路由（控制器动作字符串）；懒解析 |
| `post` | `String uri, Class<?> controllerClass, String methodName` | `Route` | 注册 POST 路由（Class + 方法名）；懒解析 |
| `put` | `String uri, Controllers.Runner action` | `Route` | 注册 PUT 路由 |
| `put` | `String uri, String controllerAction` | `Route` | 注册 PUT 路由（控制器动作字符串）；懒解析 |
| `put` | `String uri, Class<?> controllerClass, String methodName` | `Route` | 注册 PUT 路由（Class + 方法名）；懒解析 |
| `delete` | `String uri, Controllers.Runner action` | `Route` | 注册 DELETE 路由 |
| `delete` | `String uri, String controllerAction` | `Route` | 注册 DELETE 路由（控制器动作字符串）；懒解析 |
| `delete` | `String uri, Class<?> controllerClass, String methodName` | `Route` | 注册 DELETE 路由（Class + 方法名）；懒解析 |
| `patch` | `String uri, Controllers.Runner action` | `Route` | 注册 PATCH 路由 |
| `patch` | `String uri, String controllerAction` | `Route` | 注册 PATCH 路由（控制器动作字符串）；懒解析 |
| `patch` | `String uri, Class<?> controllerClass, String methodName` | `Route` | 注册 PATCH 路由（Class + 方法名）；懒解析 |
| `all` | `String uri, Controllers.Runner action` | `Router` | 注册多方法路由（GET/POST/PUT/DELETE/PATCH） |
| `all` | `String uri, String controllerAction` | `Router` | 注册多方法路由（控制器动作字符串）；懒解析 |
| `all` | `String uri, Class<?> controllerClass, String methodName` | `Router` | 注册多方法路由（Class + 方法名）；懒解析 |
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

// 控制器动作路由（字符串形式）——控制器引用在首次请求时懒解析
router.get("/users", "UserController::list");
router.post("/users", "UserController::create");
router.put("/users/{id}", "com.example.UserController::update");

// 控制器动作路由（Class 形式）
router.get("/users/{id}", UserController.class, "show");
router.delete("/users/{id}", UserController.class, "destroy");
router.all("/posts/{id}", PostController.class, "handle");

// 注意：使用控制器动作路由前，需先在 ControllerRegistry 注册控制器实例
ControllerRegistry.register(new UserController());
ControllerRegistry.register(new PostController());
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
| `getContentType` | 无 | `String` | 从响应头提取 Content-Type（default 方法）；若未设置则返回默认值 `text/plain;charset=utf-8` |
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
| `html` | `String html` | `Response` | HTML 响应（Content-Type: text/html） |
| `file` | `byte[] data, String filename` | `Response` | 创建文件下载响应 |
| `staticFile` | `byte[] data, String mimeType, int cacheMaxAge` | `Response` | 创建静态文件响应（设置 Content-Type / Cache-Control / Content-Length，供静态资源服务使用） |
| `unauthorized` | `String message` | `Response` | 创建 401 未授权响应 |
| `forbidden` | `String message` | `Response` | 创建 403 禁止访问响应 |
| `error` | `int status, String message` | `Response` | 创建错误响应 |
| `redirect` | `String url` | `Response` | 创建 302 重定向响应 |
| `raw` | 无 | `RawResponse` | 创建空响应构建器（不预设 header/status） |
| `toJson` | `Object data` | `String` | 将对象序列化为 JSON 字符串 |

#### Usage Example
```java
// JSON 响应
return ResponseBuilder.json(Map.of("code", 200, "data", users));

// 视图响应
return ResponseBuilder.view("user.profile", Map.of("user", user));

// HTML 响应
return ResponseBuilder.html("<h1>Hello</h1>");

// 错误响应
return ResponseBuilder.unauthorized("请先登录");

// 文件下载
return ResponseBuilder.file(fileBytes, "report.pdf");

// 重定向
return ResponseBuilder.redirect("/login");

// Raw 模式：自定义响应
return ResponseBuilder.raw()
    .status(200)
    .header("Content-Type", "application/xml;charset=utf-8")
    .body("<xml><name>test</name></xml>");
```

#### Nested Types
- **ResponseBuilder.RawResponse** (class, public static): Raw 响应构建器，由 `ResponseBuilder.raw()` 创建，详见下方独立章节。

---

### ResponseBuilder.RawResponse
- **Type**: public static class (`ResponseBuilder.RawResponse`)
- **Package**: `com.weacsoft.jaravel.vendor.http.response`
- **Description**: Raw 响应构建器，由 `ResponseBuilder.raw()` 创建。不预设任何 Content-Type 或状态码，开发者通过链式方法自由组织响应头、状态码、Cookie 与响应体。若最终未设置 Content-Type，框架在写入 HTTP 响应时兜底为 `text/plain;charset=utf-8`。
- **Implements**: `Response`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `status` | `int status` | `RawResponse` | 设置 HTTP 状态码（默认 200） |
| `header` | `String name, String value` | `RawResponse` | 追加响应头（同名可多次添加） |
| `contentType` | `String contentType` | `RawResponse` | 设置 Content-Type（覆盖已有值） |
| `cookie` | `Cookie cookie` | `RawResponse` | 追加 Cookie 对象 |
| `cookie` | `String name, String value` | `RawResponse` | 追加 Cookie（名值对） |
| `body` | `String content` | `Response` | 设置文本响应体（结束链式构建） |
| `body` | `byte[] bytes` | `Response` | 设置二进制响应体（结束链式构建） |

#### Response 接口实现方法

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `getStatus` | 无 | `int` | 获取状态码 |
| `getHeaders` | 无 | `Map<String, List<String>>` | 获取响应头（返回副本） |
| `addHeader` | `String name, String value` | `void` | 追加响应头 |
| `getCookies` | 无 | `Cookie[]` | 获取 Cookie 数组 |
| `addCookie` | `Cookie cookie` | `void` | 追加 Cookie |
| `addCookie` | `String name, String value` | `void` | 追加 Cookie（名值对） |
| `getContent` | 无 | `String` | 获取文本响应体 |
| `getBytes` | 无 | `byte[]` | 获取二进制响应体 |
| `getContentType` | 无 | `String` | 从响应头提取 Content-Type；未设置返回 `text/plain;charset=utf-8` |
| `getBody` | 无 | `Object` | 获取响应体（默认返回 getContent） |

#### Usage Example
```java
// 自定义 XML 响应
return ResponseBuilder.raw()
    .status(200)
    .header("Content-Type", "application/xml;charset=utf-8")
    .header("X-Custom", "hello")
    .body("<xml><name>test</name></xml>");

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

### ControllerRegistry
- **Type**: class（静态全局）
- **Package**: `com.weacsoft.jaravel.vendor.http.controller`
- **Description**: 控制器全局注册表。内部维护**两张映射表**：`Map<Class<?>, Object>`（Class→控制器实例）与 `Map<String, Object>`（名称→控制器实例）。`register(Object)` 注册时会以 `obj.getClass()` 为 Class key、以 `obj.getClass().getSimpleName()` 为名称 key 同时写入两张表。供 `ControllerActionResolver` 在请求时按 Class 或名称懒解析控制器实例；通常在应用启动时注册所有控制器。所有方法均为静态方法，直接通过类名访问全局注册表。
- **Annotations**: 无

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `register` (static) | `Object controller` | `void` | 注册控制器实例；同时写入 Class→实例 与 名称→实例 两张表（名称取 `getClass().getSimpleName()`） |
| `resolve` (static) | `Class<?> clazz` | `Object` | 按 Class 解析控制器实例；未注册返回 `null` |
| `resolve` (static) | `String name` | `Object` | 按简单类名解析控制器实例；未注册返回 `null` |
| `isClassRegistered` (static) | `Class<?> clazz` | `boolean` | Class 是否已注册 |
| `isNameRegistered` (static) | `String name` | `boolean` | 名称是否已注册 |
| `getRegisteredClasses` (static) | 无 | `Set<Class<?>>` | 获取所有已注册 Class |
| `clear` (static) | 无 | `void` | 清除所有注册（主要用于测试） |

#### Usage Example
```java
// 应用启动时注册所有控制器
ControllerRegistry.register(new UserController());
ControllerRegistry.register(new PostController());
ControllerRegistry.register(new OrderController());

// 按 Class 解析
Object userController = ControllerRegistry.resolve(UserController.class);

// 按简单类名解析
Object ctrl = ControllerRegistry.resolve("PostController");

// 判断是否已注册
if (ControllerRegistry.isClassRegistered(UserController.class)) {
    // 已注册，可安全使用控制器动作路由
}

// 获取所有已注册 Class
Set<Class<?>> registered = ControllerRegistry.getRegisteredClasses();

// 测试后清理
ControllerRegistry.clear();
```

---

### ControllerActionResolver
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.http.controller`
- **Description**: 控制器动作解析器，将控制器引用解析为 `Controllers.Runner`。支持两种引用形式：**字符串形式** `"ControllerName::methodName"`（或 `"com.example.ControllerName::methodName"` 全限定名）与 **Class 形式** `Class<?> + methodName`。`::` 之前为控制器引用（简单类名或全限定名），之后为方法名；解析时从 `ControllerRegistry` 取得控制器实例并反射调用指定方法。内部通过 `ConcurrentMap` 缓存已解析的 `Controllers.Runner`，相同引用二次解析直接返回缓存结果。采用**懒解析**策略——控制器引用在**首次请求时**才被解析为 `Controllers.Runner`，而非注册路由时；供 `Router` 的控制器动作重载（`get(uri, "Controller::action")` 等）使用。若控制器未在 `ControllerRegistry` 注册，解析失败将在请求时抛出异常。
- **Annotations**: 无

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `resolve` (static) | `String controllerAction` | `Controllers.Runner` | 解析 `"ControllerName::methodName"` 或 `"com.example.ControllerName::methodName"` 为 Runner；`::` 之前为控制器引用（从 `ControllerRegistry` 解析实例），之后为方法名；反射调用并返回 Runner；结果缓存 |
| `resolve` (static) | `Class<?> controllerClass, String methodName` | `Controllers.Runner` | 解析 Class + 方法名为 Runner；从 `ControllerRegistry` 按 Class 解析控制器实例，反射调用方法；结果缓存 |

#### String Expression Syntax

| 表达式 | 控制器引用 | 方法名 | 说明 |
|--------|-----------|--------|------|
| `"UserController::list"` | `UserController`（简单类名） | `list` | 简单类名形式 |
| `"UserController::show"` | `UserController` | `show` | 简单类名 + 方法 |
| `"com.example.UserController::list"` | `com.example.UserController`（全限定名） | `list` | 全限定名形式 |

`::` 分隔控制器引用与方法名；控制器引用可为简单类名或全限定名，由 `ControllerRegistry` 解析为实例。

#### Usage Example
```java
// 字符串形式解析（简单类名）
Controllers.Runner r1 = ControllerActionResolver.resolve("UserController::list");

// 字符串形式解析（全限定名）
Controllers.Runner r2 = ControllerActionResolver.resolve("com.example.UserController::list");

// Class 形式解析
Controllers.Runner r3 = ControllerActionResolver.resolve(UserController.class, "list");

// 结果会被缓存：相同引用二次解析直接返回缓存的 Runner，
// 因此控制器引用在首次请求时才被解析（懒解析），而非注册路由时
```

---

### VerifyCsrfToken
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.middleware`
- **Description**: CSRF 令牌校验中间件，对齐 Laravel VerifyCsrfToken。对安全方法（GET/HEAD/OPTIONS/TRACE）和排除 URI 跳过校验，其余请求验证 CSRF 令牌。该类为普通类（**不再是 Spring `@Component`**），开发者通过继承并标注 `@MiddlewareAlias` 注册到全局别名表；配置通过重写受保护方法而非构造参数完成。
- **Annotations**: 无（建议在子类上标注 `@MiddlewareAlias`）
- **Implements**: `Middleware`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `except` (protected) | 无 | `String[]` | 排除 CSRF 校验的 URI 数组，默认返回空数组；子类可重写以自定义排除列表 |
| `handle` | `Request request, NextFunction next, String... params` | `Response` | 执行 CSRF 校验 |

#### Usage Example
```java
// 继承 VerifyCsrfToken 并通过 @MiddlewareAlias 注册别名
@MiddlewareAlias("csrf")
public class AppVerifyCsrfToken extends VerifyCsrfToken {
    @Override
    protected String[] except() {
        return new String[]{ "/api/webhook/*", "/api/callback" };
    }
}

// 默认行为（无排除 URI）：直接注册基类实例或无重写的子类
MiddlewareAliasRegistry.getGlobal().register(new VerifyCsrfToken());
```

---

### TrustProxies
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.middleware`
- **Description**: 信任代理中间件，对齐 Laravel TrustProxies。配置可信代理 IP，从 X-Forwarded-* 头提取真实客户端信息。该类为普通类（**不再是 Spring `@Component`**），开发者通过继承并标注 `@MiddlewareAlias` 注册到全局别名表；配置通过重写受保护方法而非构造参数完成。
- **Annotations**: 无（建议在子类上标注 `@MiddlewareAlias`）
- **Implements**: `Middleware`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `trustedProxies` (protected) | 无 | `List<String>` | 可信代理 IP 列表，默认返回 `Arrays.asList("127.0.0.1", "::1")`；子类可重写以自定义 |
| `handle` | `Request request, NextFunction next, String... params` | `Response` | 处理信任代理头 |

#### Usage Example
```java
// 继承 TrustProxies 并通过 @MiddlewareAlias 注册别名
@MiddlewareAlias("trustProxies")
public class AppTrustProxies extends TrustProxies {
    @Override
    protected List<String> trustedProxies() {
        return Arrays.asList("127.0.0.1", "::1", "10.0.0.0/8");
    }
}

// 默认行为（信任 127.0.0.1 与 ::1）：直接注册基类实例或无重写的子类
MiddlewareAliasRegistry.getGlobal().register(new TrustProxies());
```

---

### EncryptCookies
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.middleware`
- **Description**: Cookie 加密中间件，对齐 Laravel EncryptCookies。使用 AES/CBC/PKCS5Padding 加密 Cookie 值，请求时解密，响应时加密。该类为普通类（**不再是 Spring `@Component`**），开发者通过继承并标注 `@MiddlewareAlias` 注册到全局别名表；配置通过重写受保护方法而非构造参数完成。
- **Annotations**: 无（建议在子类上标注 `@MiddlewareAlias`）
- **Implements**: `Middleware`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `encryptionKey` (protected) | 无 | `String` | Cookie 加密密钥，默认返回内置默认密钥；子类可重写以自定义密钥 |
| `except` (protected) | 无 | `String[]` | 排除加密的 Cookie 名称数组，默认返回空数组；子类可重写以自定义排除列表 |
| `handle` | `Request request, NextFunction next, String... params` | `Response` | 解密请求 Cookie，加密响应 Cookie |

#### Usage Example
```java
// 继承 EncryptCookies 并通过 @MiddlewareAlias 注册别名
@MiddlewareAlias("encryptCookies")
public class AppEncryptCookies extends EncryptCookies {
    @Override
    protected String encryptionKey() {
        return System.getenv("APP_COOKIE_KEY");
    }

    @Override
    protected String[] except() {
        return new String[]{ "theme", "locale" };
    }
}

// 默认行为（内置密钥、无排除 Cookie）：直接注册基类实例或无重写的子类
MiddlewareAliasRegistry.getGlobal().register(new EncryptCookies());
```

---

### TrimStrings
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.middleware`
- **Description**: 字符串裁剪中间件，对齐 Laravel TrimStrings。自动裁剪 input 和 query 参数的首尾空白。该类为普通类（**不再是 Spring `@Component`**），开发者通过继承并标注 `@MiddlewareAlias` 注册到全局别名表；配置通过重写受保护方法而非构造参数完成。
- **Annotations**: 无（建议在子类上标注 `@MiddlewareAlias`）
- **Implements**: `Middleware`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `except` (protected) | 无 | `String[]` | 排除裁剪的字段数组，默认返回 `new String[0]`（即裁剪所有字段）；子类可重写以自定义排除列表 |
| `handle` | `Request request, NextFunction next, String... params` | `Response` | 裁剪请求参数 |

#### Usage Example
```java
// 继承 TrimStrings 并通过 @MiddlewareAlias 注册别名
@MiddlewareAlias("trimStrings")
public class AppTrimStrings extends TrimStrings {
    @Override
    protected String[] except() {
        return new String[]{ "raw_content", "markdown" };
    }
}

// 默认行为（裁剪所有字段）：直接注册基类实例或无重写的子类
MiddlewareAliasRegistry.getGlobal().register(new TrimStrings());
```

---

### ConvertEmptyStringsToNull
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.middleware`
- **Description**: 空字符串转 Null 中间件，对齐 Laravel ConvertEmptyStringsToNull。将空字符串参数转为 null，默认排除 password 相关字段。该类为普通类（**不再是 Spring `@Component`**），开发者通过继承并标注 `@MiddlewareAlias` 注册到全局别名表；配置通过重写受保护方法而非构造参数完成。
- **Annotations**: 无（建议在子类上标注 `@MiddlewareAlias`）
- **Implements**: `Middleware`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `except` (protected) | 无 | `String[]` | 排除转换的字段数组，默认返回 `{"password", "password_confirmation", "current_password"}`；子类可重写以自定义排除列表 |
| `handle` | `Request request, NextFunction next, String... params` | `Response` | 转换空字符串为 null |

#### Usage Example
```java
// 继承 ConvertEmptyStringsToNull 并通过 @MiddlewareAlias 注册别名
@MiddlewareAlias("convertEmpty")
public class AppConvertEmptyStringsToNull extends ConvertEmptyStringsToNull {
    @Override
    protected String[] except() {
        return new String[]{ "password", "password_confirmation", "current_password", "description" };
    }
}

// 默认行为（排除 password 相关字段）：直接注册基类实例或无重写的子类
MiddlewareAliasRegistry.getGlobal().register(new ConvertEmptyStringsToNull());
```

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
