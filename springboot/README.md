# springboot 模块

> Jaravel-Vendor 的 Spring Boot 集成模块，将 `http` 模块的 Laravel 风格路由、中间件、Request / Response 与 Spring MVC（`RouterFunction`、`HandlerMethodArgumentResolver`、`HandlerMethodReturnValueHandler`、`ResponseBodyAdvice`）桥接。包名统一为 `com.weacsoft.jaravel.vendor.springboot`。

---

## 目录

- [1. 模块概述](#1-模块概述)
- [2. 依赖信息](#2-依赖信息)
- [3. 类总览](#3-类总览)
- [4. 中间件别名扫描 —— SpringBootRouteAutoConfiguration 内置扫描](#4-中间件别名扫描--springbootrouteautoconfiguration-内置扫描)
- [5. SpringBootRouteAutoConfiguration —— 路由自动装配](#5-springbootrouteautoconfiguration--路由自动装配)
- [6. ResponseAutoConfiguration —— 响应自动装配](#6-responseautoconfiguration--响应自动装配)
- [7. SpringBootRequestMVCResolver —— 请求参数解析器](#7-springbootrequestmvcresolver--请求参数解析器)
- [8. SpringBootResponseMVCResolver —— 响应体处理器](#8-springbootresponsemvcresolver--响应体处理器)
- [9. ResponseReturnValueHandler —— 返回值处理器](#9-responsereturnvaluehandler--返回值处理器)
- [10. 自动装配清单](#10-自动装配清单)
- [11. 配置选项](#11-配置选项)
- [12. 线程安全说明](#12-线程安全说明)

---

## 1. 模块概述

`springboot` 模块是 Jaravel-Vendor 与 Spring Boot 3.2.5 / Spring 6.x 的适配层，对齐 Laravel 的以下集成特性：

| Laravel 特性 | springboot 对应实现 | 说明 |
| --- | --- | --- |
| `App\Http\Kernel::$middleware` 全局中间件 | 根 `Router.middleware()` | 全局中间件直接在根 Router 上声明，所有路由通过 `getAllMiddlewares()` 继承 |
| `App\Http\Kernel::$routeMiddleware` 别名中间件 | `@MiddlewareAlias` + `SpringBootRouteAutoConfiguration` | 注解声明别名，构建路由前由 `SpringBootRouteAutoConfiguration.scanMiddlewareAliases()` 扫描并注册到 `MiddlewareAliasRegistry` |
| `Route::` 路由注册 | `SpringBootRouteAutoConfiguration` | 将 `Router` 转为 Spring `RouterFunction` |
| Request 注入 | `SpringBootRequestMVCResolver` | Controller 方法可直接声明 `Request` 参数 |
| Response 处理 | `SpringBootResponseMVCResolver` / `ResponseReturnValueHandler` | Controller 可直接返回 `Response` |
| 中间件管道执行 | `SpringBootRouteAutoConfiguration` | 逆序折叠中间件链，洋葱模型 |

本模块通过 Spring Boot 的 `@AutoConfiguration` 机制自动装配，引入依赖后无需额外配置即可生效。自动装配类注册在 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 中。

---

## 2. 依赖信息

### Maven 坐标

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>springboot</artifactId>
    <version>0.1.2</version>
</dependency>
```

### 传递依赖

| 依赖 | scope | 用途 |
| --- | --- | --- |
| `com.weacsoft:http` | compile | 路由、中间件、Request / Response |
| `com.weacsoft:auth` | optional | `AuthContext` / `AuthManager`（认证上下文） |
| `org.springframework:spring-webmvc` | compile | `RouterFunction`、`HandlerMethodArgumentResolver` 等 |
| `org.springframework.boot:spring-boot-autoconfigure` | compile | `@AutoConfiguration` 自动装配支持 |
| `jakarta.servlet:jakarta.servlet-api` | provided | Servlet API |

> `auth` 为可选依赖：当应用引入 auth 模块时，路由处理会设置 `AuthContext` 并在结束时清理；未引入时通过 `ObjectProvider` 安全跳过。

---

## 3. 类总览

```
com.weacsoft.jaravel.vendor.springboot
├── SpringBootRouteAutoConfiguration    // 路由自动装配（@AutoConfiguration，内置中间件别名扫描）
├── ResponseAutoConfiguration           // 响应自动装配（@AutoConfiguration）
├── SpringBootRequestMVCResolver        // Request 参数解析器（@Component）
├── SpringBootResponseMVCResolver       // Response 响应体处理器（@ControllerAdvice）
├── ResponseReturnValueHandler          // Response 返回值处理器
└── annotation
    └── MiddlewareAlias                // 中间件别名注解（纯注解，不组合 @Component）
```

自动装配注册文件（`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`）：

```
com.weacsoft.jaravel.vendor.springboot.SpringBootRouteAutoConfiguration
com.weacsoft.jaravel.vendor.springboot.ResponseAutoConfiguration
```

---

## 4. 中间件别名扫描 —— SpringBootRouteAutoConfiguration 内置扫描

对齐 Laravel `App\Http\Kernel::$routeMiddleware` 别名注册机制。别名中间件扫描逻辑现已合并到 `SpringBootRouteAutoConfiguration` 中（不再使用独立的注册器组件）。`jaravelRouterFunction` Bean 方法现在接收 `ApplicationContext` 作为第三个参数，并在构建路由前调用 `scanMiddlewareAliases(applicationContext)`，通过 **classpath 扫描**（非 Bean 扫描）发现 `@MiddlewareAlias` 注解的中间件类，反射实例化（非 Spring Bean）后注册到 `MiddlewareAliasRegistry.getGlobal()` 全局注册表，使路由可通过别名、Class 对象或类名字符串引用中间件。同时调用 `scanControllers(applicationContext)` 扫描容器中实现了 `Controllers` 的 Bean，注册到 `ControllerRegistry`，使路由可通过字符串（`"ControllerName::method"`）或类对象引用控制器方法。

### scanMiddlewareAliases 方法

`SpringBootRouteAutoConfiguration.scanMiddlewareAliases(ApplicationContext applicationContext)` 为包级可见方法：

- 通过 `ClassPathScanningCandidateComponentProvider` + `AnnotationTypeFilter(MiddlewareAlias.class)` 扫描 classpath（非 Bean 扫描）。
- 基础包由 `AutoConfigurationPackages.get(applicationContext)` 确定（即 `@SpringBootApplication` 类所在包及其子包）。
- 对扫描到的类通过反射实例化（要求有无参构造器），**中间件不是 Spring Bean**。
- 根据注解 `value()` 是否为空，决定按别名 + 类或仅按类注册到 `MiddlewareAliasRegistry.getGlobal()`。
- 对 `null` 上下文安全处理（直接返回，不抛异常）。
- 在 `jaravelRouterFunction` 构建路由前一次性调用，启动阶段完成后注册表只读。
- 另有包级可见重载 `scanMiddlewareAliases(ApplicationContext, List<String> basePackages)` 供测试直接指定扫描包。

### @MiddlewareAlias 注解

`com.weacsoft.jaravel.vendor.springboot.annotation.MiddlewareAlias`（**纯注解，不组合 `@Component`**），`value()` 可选（`default ""`）。标注在实现了 `Middleware` 接口的类上，由 `SpringBootRouteAutoConfiguration` 通过 classpath 扫描发现并反射实例化（非 Spring Bean），其 `handle(Request request, NextFunction next, String... params)` 方法通过 `params` 接收别名表达式中的参数。根据是否标注及 `value()` 取值，存在三种场景：

| 场景 | 注解形式 | 扫描注册 | 路由引用方式 |
| --- | --- | --- | --- |
| 无注解 | 不标注 | 用户自建中间件，模块与 SpringBoot 均不扫描注册 | 仅能通过实例（`router.middleware(instance)`）或全局中间件声明使用 |
| 仅按类注册 | `@MiddlewareAlias`（无 value） | 被扫描并按类注册到 `MiddlewareAliasRegistry` | 通过 Class 对象或类名字符串引用 |
| 按别名 + 类注册 | `@MiddlewareAlias("auth")` | 被扫描并按别名与类同时注册到 `MiddlewareAliasRegistry` | 通过别名、Class 对象或类名字符串引用 |

> 注解命名为 `@MiddlewareAlias` 而非 `@Middleware`，是为了避免与 `com.weacsoft.jaravel.vendor.http.middleware.Middleware` 接口同名冲突。`@MiddlewareAlias` 是纯注解，不组合 `@Component`，标注后类不会被注册为 Spring Bean，而是由框架通过反射实例化。全局中间件（对所有路由生效的中间件）无需别名注册，直接在根 `Router` 上通过类对象声明即可（如 `router.middleware(AppTrimStrings.class)`），路由会通过 `Router.getAllMiddlewares()` 继承。

### 路由中引用中间件的三种模式

别名表达式语法（冒号分隔别名/类名与参数，逗号分隔多个参数）：

| 表达式 | 别名 / 类名 | 参数 |
| --- | --- | --- |
| `"auth"` | `auth` | 无 |
| `"auth:api"` | `auth` | `["api"]` |
| `"auth:api,admin"` | `auth` | `["api", "admin"]` |

三种引用模式：

1. **别名**：`middleware("auth:api")` —— 适用于 `@MiddlewareAlias("auth")` 标注的中间件。
2. **Class 对象**：`middleware(AuthMiddleware.class)` 或带参数 `middleware(AuthMiddleware.class, "api", "admin")` —— 适用于 `@MiddlewareAlias` 或 `@MiddlewareAlias("auth")` 标注的中间件。
3. **类名字符串**：`middleware("AuthMiddleware:api")` —— 语法与别名相同，适用于 `@MiddlewareAlias` 或 `@MiddlewareAlias("auth")` 标注的中间件。

### 使用示例

```java
// 1. 声明中间件并注册别名（纯注解，非 Spring Bean，由 classpath 扫描发现并反射实例化）
//    @MiddlewareAlias("auth")：按别名 + 类注册，路由可用别名/Class/类名引用
@MiddlewareAlias("auth")
public class AuthMiddleware implements Middleware {
    @Override
    public Response handle(Request request, NextFunction next, String... params) {
        // params 来自别名表达式：auth:api → params = ["api"]
        String guard = params.length > 0 ? params[0] : "web";
        // 认证逻辑...
        return next.apply(request);
    }
}

//    @MiddlewareAlias（无 value）：仅按类注册，路由可用 Class/类名引用
@MiddlewareAlias
public class LogMiddleware implements Middleware {
    @Override
    public Response handle(Request request, NextFunction next, String... params) {
        return next.apply(request);
    }
}

// 2. 路由中引用中间件（三种模式）
//    别名：auth:api → AuthMiddleware.handle(request, next, "api")
router.get("/api/users", action).middleware("auth:api");

//    Class 对象（可带参数）
router.get("/api/admin", action).middleware(AuthMiddleware.class, "api", "admin");

//    类名字符串（语法同别名）
router.get("/api/posts", action).middleware("AuthMiddleware:api");

// 或在路由组上使用：
router.group(Map.of(Route.Group.PREFIX, "api"), api -> {
    api.middleware("auth:api", "log");
    api.get("/users", action);
});

// 3. 全局中间件直接在根 Router 上声明（通过类对象引用，无需 getBean 或 new）
router.middleware(AppTrimStrings.class).middleware(AppConvertEmptyStringsToNull.class);
```

> 别名注册流程：用户标注 `@MiddlewareAlias("auth")` → `jaravelRouterFunction` 构建路由前调用 `SpringBootRouteAutoConfiguration.scanMiddlewareAliases(applicationContext)` 通过 classpath 扫描发现 `@MiddlewareAlias` 注解的类 → 反射实例化 → 注册到 `MiddlewareAliasRegistry.getGlobal()` → 路由中 `middleware("auth:api")` 调用 `route.getMiddlewares()` 时解析别名表达式，通过闭包烘焙参数并返回 `Middleware` 实例。

---

## 5. SpringBootRouteAutoConfiguration —— 路由自动装配

`com.weacsoft.jaravel.vendor.springboot.SpringBootRouteAutoConfiguration`

核心自动装配类，将 Jaravel `Router` 中注册的路由转换为 Spring `RouterFunction`，并在请求处理时执行中间件链。适配 Spring Boot 3.2.5 / Spring 6.x（`jakarta.servlet`、`org.springframework.web.servlet.function`）。

### 注册的 Bean

| Bean 方法 | 类型 | 条件 | 说明 |
| --- | --- | --- | --- |
| `baseRouter()` | `Router` | `@ConditionalOnMissingBean` | 基础路由器，用户可覆盖 |
| `jaravelRouterFunction(...)` | `RouterFunction<ServerResponse>` | 无 | 接收 `Router`、`RouteAuthHandler`、`ApplicationContext` 三参；构建路由前先调用 `scanMiddlewareAliases(applicationContext)` 扫描 `@MiddlewareAlias` Bean 注册到 `MiddlewareAliasRegistry.getGlobal()`，再将 `Router` 的所有路由转为 Spring 路由函数 |

### 处理流程

```
0. scanMiddlewareAliases(applicationContext)
   │   扫描 @MiddlewareAlias Bean → 注册到 MiddlewareAliasRegistry.getGlobal()
   │
   ▼
1. 遍历 Router.getAllRoutes()
   │
   ▼ 每条路由
2. 构造 RequestPredicate（HTTP 方法 + 完整 URI）
   │
   ▼
3. 构造 HandlerFunction：
   ├── RequestFactory.buildFromServerRequest 构建 Laravel 风格 Request
   ├── 提取路径参数（如 /api/users/{id} 中的 id）写入 routeParams
   ├── 设置 AuthContext（使认证中间件能读取 Authorization 头）
   ├── 获取路由中间件（含根 Router 全局中间件 + 路由组中间件 + 路由级中间件）
   ├── 逆序折叠中间件链（洋葱模型）
   ├── 终点调用 Route.getAction()::handle
   └── finally 清理 AuthContext / AuthManager
   │
   ▼
4. 将 Response 的状态码、响应头、Cookie、内容转为 Spring ServerResponse
```

### 中间件链折叠逻辑

中间件按 `根 Router 中间件 + 路由组中间件 + 路由级中间件` 顺序排列，然后**逆序折叠**为嵌套调用：

```java
// 伪代码
Middleware.NextFunction finalHandler = route.getAction()::handle;
for (int i = allMiddlewares.size() - 1; i >= 0; i--) {
    Middleware middleware = allMiddlewares.get(i);
    Middleware.NextFunction next = finalHandler;
    finalHandler = request -> middleware.handle(request, next);
}
Response response = finalHandler.apply(customRequest);
```

执行顺序：`根 Router 中间件[0] -> ... -> 路由组中间件[0] -> ... -> 路由级中间件[0] -> action`，响应则反向返回。

### 异常处理

路由处理过程中抛出异常时，记录错误日志并返回 500 状态码：

```json
{
  "error": "异常类简名",
  "message": "异常消息"
}
```

### 使用示例

```java
@Configuration
public class RouteConfig {

    @Bean
    public Router router(ApplicationContext context) {
        Router router = new Router();

        // 全局中间件直接在根 Router 上声明（对齐 Laravel Kernel $middleware）
        router.middleware(
            context.getBean(TrimStrings.class),
            context.getBean(ConvertEmptyStringsToNull.class)
        );

        // 注册路由
        router.get("/api/users", request ->
            ResponseBuilder.json(userService.list()));

        router.group(Map.of(Route.Group.PREFIX, "api"), api -> {
            api.get("/posts", postController::index);
            api.post("/posts", postController::store)
               .middleware("auth:api");  // 通过别名引用中间件
        });

        return router;
    }
}
```

---

## 6. ResponseAutoConfiguration —— 响应自动装配

`com.weacsoft.jaravel.vendor.springboot.ResponseAutoConfiguration`

响应自动装配类，将 `ResponseReturnValueHandler` 前置注入到 `RequestMappingHandlerAdapter` 的返回值处理器链，使传统 `@RequestMapping` 风格的 Controller 方法可直接返回 `Response` 类型。

### 装配条件

- `@AutoConfiguration`
- `@ConditionalOnWebApplication(type = SERVLET)`：仅在 Servlet Web 应用中生效

### 装配逻辑

```java
// 构造器中执行
List<HandlerMethodReturnValueHandler> handlers = new ArrayList<>();
handlers.addAll(originalHandlers);   // 保留原有处理器
handlers.add(0, new ResponseReturnValueHandler());  // 前置插入
requestMappingHandlerAdapter.setReturnValueHandlers(handlers);
```

将 `ResponseReturnValueHandler` 插入到处理器链的**最前面**（索引 0），确保 `Response` 类型返回值优先被本框架处理，而非被默认的 `@ResponseBody` 处理器接管。

### 与 SpringBootResponseMVCResolver 的关系

两者协同工作，覆盖两种 Controller 写法：

| Controller 风格 | 生效的处理器 | 说明 |
| --- | --- | --- |
| `@RequestMapping` + 返回 `Response` | `ResponseReturnValueHandler` | 由本装配注入 |
| `@RestController` / `@ResponseBody` + 返回 `Response` | `SpringBootResponseMVCResolver` | `@ControllerAdvice` 全局拦截 |

---

## 7. SpringBootRequestMVCResolver —— 请求参数解析器

`com.weacsoft.jaravel.vendor.springboot.SpringBootRequestMVCResolver`

Spring MVC `HandlerMethodArgumentResolver` 实现，使 Controller 方法可直接声明 `Request` 类型参数并自动注入。标注 `@Component`。

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `boolean supportsParameter(MethodParameter parameter)` | 参数类型为 `Request` 时返回 true |
| `Object resolveArgument(MethodParameter, ModelAndViewContainer, NativeWebRequest, WebDataBinderFactory)` | 通过 `RequestFactory.buildFromHttpServletRequest` 构建并返回 `Request` |

### 使用示例

无需任何注解，直接在 Controller 方法签名中声明 `Request` 参数：

```java
@RestController
@RequestMapping("/api")
public class UserController {

    @PostMapping("/users")
    public Response create(Request request) {   // 自动注入
        String name = request.input("name");
        MultipartFile avatar = request.file("avatar");
        return ResponseBuilder.json(userService.create(name, avatar));
    }

    @GetMapping("/users/{id}")
    public Response show(Request request) {
        Long id = request.routeParam("id", Long.class);
        return ResponseBuilder.json(userService.find(id));
    }
}
```

> 注意：此解析器用于传统 `@RequestMapping` 风格的 Controller。`RouterFunction` 风格的路由由 `SpringBootRouteAutoConfiguration` 内部直接构建 `Request`，不经过此解析器。

---

## 8. SpringBootResponseMVCResolver —— 响应体处理器

`com.weacsoft.jaravel.vendor.springboot.SpringBootResponseMVCResolver`

Spring MVC `ResponseBodyAdvice<Object>` 实现，标注 `@ControllerAdvice`。当 Controller 方法返回 `Response` 类型时，应用其状态码、响应头、Cookie 与内容；对其它返回值补充安全响应头。

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType)` | 始终返回 true（拦截所有响应） |
| `Object beforeBodyWrite(...)` | 响应体写入前的处理 |

### 处理逻辑

```
body 是 Response 类型？
├── 是：应用 Response 的响应头、Cookie、状态码，返回 Response.getContent()
│
└── 否：
    ├── body == null：状态码设为 200，返回 ""
    └── body 非 null：
        ├── Content-Type 为 null 时设为 text/html
        └── 补充安全响应头：
            ├── X-Content-Type-Options: nosniff
            ├── X-Frame-Options: SAMEORIGIN
            └── X-XSS-Protection: 1; mode=block
```

### 安全响应头说明

对非 `Response` 类型的返回值，自动补充以下安全头：

| 响应头 | 值 | 作用 |
| --- | --- | --- |
| `X-Content-Type-Options` | `nosniff` | 禁止浏览器 MIME 嗅探 |
| `X-Frame-Options` | `SAMEORIGIN` | 防止点击劫持（仅同源可嵌套） |
| `X-XSS-Protection` | `1; mode=block` | 启用浏览器 XSS 过滤器 |

---

## 9. ResponseReturnValueHandler —— 返回值处理器

`com.weacsoft.jaravel.vendor.springboot.ResponseReturnValueHandler`

Spring MVC `HandlerMethodReturnValueHandler` 实现，处理 Controller 方法直接返回 `Response` 类型的返回值，将状态码、响应头、Cookie 与响应体写入 Servlet 响应。适配 Spring Boot 3.2.5 / Jakarta Servlet。

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `boolean supportsReturnType(MethodParameter returnType)` | 返回值类型为 `Response` 或其子类时返回 true |
| `void handleReturnValue(Object returnValue, MethodParameter, ModelAndViewContainer, NativeWebRequest)` | 处理返回值 |

### 处理逻辑

```
returnValue == null？
├── 是：标记请求已处理（setRequestHandled(true)），返回
└── 否：
    ├── 设置 HTTP 状态码
    ├── 写入所有响应头
    ├── 写入所有 Cookie（复制 path/domain/maxAge/secure/httpOnly/SameSite 属性）
    ├── 写入响应体：
    │   ├── bytes 非 null：写入 OutputStream
    │   └── content 非 null：写入 Writer
    └── 标记请求已处理
```

### Cookie 属性复制

处理 Cookie 时完整复制以下属性到 Servlet Cookie：

- `path`、`domain`、`maxAge`、`secure`、`httpOnly`
- `SameSite` 属性（通过 `setAttribute("SameSite", ...)`）

---

## 10. 自动装配清单

引入 `springboot` 模块后，Spring Boot 自动装配以下组件：

| 组件 | 类型 | 来源 | 作用 |
| --- | --- | --- | --- |
| `SpringBootRouteAutoConfiguration` | `@AutoConfiguration` | imports 文件 | 路由桥接 + 中间件执行 + `@MiddlewareAlias` 别名扫描注册 |
| `ResponseAutoConfiguration` | `@AutoConfiguration` | imports 文件 | 注入返回值处理器 |
| `SpringBootRequestMVCResolver` | `@Component` | 组件扫描 | Request 参数注入 |
| `SpringBootResponseMVCResolver` | `@ControllerAdvice` | 组件扫描 | Response 响应处理 + 安全头 |
| `@MiddlewareAlias` 标注的中间件类 | 纯注解（非 Spring Bean） | classpath 扫描 | 用户中间件类，启动时由 `SpringBootRouteAutoConfiguration.scanMiddlewareAliases()` 通过 classpath 扫描发现，反射实例化后注册到 `MiddlewareAliasRegistry.getGlobal()` |
| `baseRouter` (Router) | `@Bean` | `SpringBootRouteAutoConfiguration` | 基础路由器（可覆盖） |
| `jaravelRouterFunction` (RouterFunction) | `@Bean` | `SpringBootRouteAutoConfiguration` | Spring 路由函数 |

> 注意：`@Component` / `@ControllerAdvice` 标注的类需要应用的主类包路径能扫描到 `com.weacsoft.jaravel.vendor.springboot`。若应用包名不同，需手动 `@ComponentScan` 或通过 `@AutoConfiguration` 的 imports 文件确保 `@AutoConfiguration` 类被加载（`@AutoConfiguration` 类内部的 `@Bean` 不受组件扫描限制）。

---

## 11. 配置选项

`springboot` 模块通过 Spring Boot 自动装配机制工作，无需额外配置项。以下为可定制点：

| 定制点 | 方式 | 说明 |
| --- | --- | --- |
| 覆盖基础路由器 | 自定义 `Router` `@Bean` | `baseRouter()` 标注 `@ConditionalOnMissingBean`，用户定义的同类型 Bean 优先 |
| 注册全局中间件 | 在根 `Router` 上调用 `middleware()` | 全局中间件直接在根 Router 上声明，所有路由通过 `getAllMiddlewares()` 继承 |
| 注册别名中间件 | 在中间件类上标注 `@MiddlewareAlias`（value 可选） | 启动时由 `SpringBootRouteAutoConfiguration.scanMiddlewareAliases()` 扫描并注册到 `MiddlewareAliasRegistry`，路由中可通过别名 / Class / 类名引用 |
| 关闭响应自动装配 | 排除 `ResponseAutoConfiguration` | `@SpringBootApplication(exclude = ResponseAutoConfiguration.class)` |
| 关闭路由自动装配 | 排除 `SpringBootRouteAutoConfiguration` | 同上 |

### 排除自动装配示例

```java
@SpringBootApplication(exclude = {
    SpringBootRouteAutoConfiguration.class,
    ResponseAutoConfiguration.class
})
public class MyApp { ... }
```

---

## 12. 线程安全说明

| 类 | 线程安全性 | 说明 |
| --- | --- | --- |
| `SpringBootRouteAutoConfiguration` | 线程安全 | `@Bean` 在启动阶段创建；`jaravelRouterFunction` 构建路由前调用 `scanMiddlewareAliases(applicationContext)` 在启动阶段一次性扫描注册 `@MiddlewareAlias` Bean 到 `MiddlewareAliasRegistry.getGlobal()`，之后只读（注册表内部使用 `ConcurrentHashMap`）；构建的 `HandlerFunction` 为每次请求新建 `Request`，无共享可变状态。`RouteAuthHandler` 使用 ThreadLocal 或请求级清理，确保请求间隔离 |
| `ResponseAutoConfiguration` | 线程安全 | 构造器在启动阶段一次性修改 `RequestMappingHandlerAdapter` 的处理器链，之后只读 |
| `SpringBootRequestMVCResolver` | 线程安全 | `@Component` 单例，`resolveArgument` 每次调用构建新的 `Request`，无共享可变状态 |
| `SpringBootResponseMVCResolver` | 线程安全 | `@ControllerAdvice` 单例，`beforeBodyWrite` 无状态，仅操作方法参数（每请求独立） |
| `ResponseReturnValueHandler` | 线程安全 | 无实例字段，`handleReturnValue` 仅操作方法参数，每请求独立 |
