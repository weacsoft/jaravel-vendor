# springboot 模块

> Jaravel-Vendor 的 Spring Boot 集成模块，将 `http` 模块的 Laravel 风格路由、中间件、Request / Response 与 Spring MVC（`RouterFunction`、`HandlerMethodArgumentResolver`、`HandlerMethodReturnValueHandler`、`ResponseBodyAdvice`）桥接。包名统一为 `com.weacsoft.jaravel.vendor.springboot`。

---

## 目录

- [1. 模块概述](#1-模块概述)
- [2. 依赖信息](#2-依赖信息)
- [3. 类总览](#3-类总览)
- [4. GlobalMiddlewareRegistry —— 全局中间件注册器](#4-globalmiddlewareregistry--全局中间件注册器)
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
| `App\Http\Kernel::$middleware` 全局中间件 | `GlobalMiddlewareRegistry` | 全局中间件栈，支持按类型注册 |
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
    <version>0.1.0</version>
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
├── GlobalMiddlewareRegistry            // 全局中间件注册器（@Component）
├── SpringBootRouteAutoConfiguration    // 路由自动装配（@AutoConfiguration）
├── ResponseAutoConfiguration           // 响应自动装配（@AutoConfiguration）
├── SpringBootRequestMVCResolver        // Request 参数解析器（@Component）
├── SpringBootResponseMVCResolver       // Response 响应体处理器（@ControllerAdvice）
└── ResponseReturnValueHandler          // Response 返回值处理器
```

自动装配注册文件（`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`）：

```
com.weacsoft.jaravel.vendor.springboot.SpringBootRouteAutoConfiguration
com.weacsoft.jaravel.vendor.springboot.ResponseAutoConfiguration
```

---

## 4. GlobalMiddlewareRegistry —— 全局中间件注册器

`com.weacsoft.jaravel.vendor.springboot.GlobalMiddlewareRegistry`

对齐 Laravel `App\Http\Kernel::$middleware` 全局中间件栈。作为 Spring `@Component` 管理，全局中间件对所有路由生效，由 `SpringBootRouteAutoConfiguration` 合并到每条路由的中间件链最外层。

### 设计要点

- 持有 `ApplicationContext`，支持按 Spring Bean 类型从容器获取中间件。
- 支持两种注册方式：直接传实例（向后兼容）与按类型注册（推荐用于无状态单例中间件）。

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `GlobalMiddlewareRegistry(ApplicationContext applicationContext)` | 构造器，注入 Spring 上下文 |
| `void add(Middleware middleware)` | 直接添加中间件实例（适用于需要构造参数的不可变中间件） |
| `void addAll(List<Middleware> middlewares)` | 批量添加中间件实例 |
| `void addByType(Class<? extends Middleware> middlewareClass)` | 按 Spring Bean 类型注册中间件（从容器获取无状态单例 Bean） |
| `void addAllByType(List<Class<? extends Middleware>> middlewareClasses)` | 批量按类型注册中间件 |
| `List<Middleware> getMiddlewares()` | 返回已注册的全局中间件列表（不可修改视图） |

### 使用示例

```java
@Component
public class KernelConfig {

    private final GlobalMiddlewareRegistry registry;

    public KernelConfig(GlobalMiddlewareRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    public void registerGlobalMiddlewares() {
        // 方式一：按类型注册容器中的无状态单例中间件（推荐）
        registry.addByType(TrimStrings.class);
        registry.addByType(ConvertEmptyStringsToNull.class);
        registry.addByType(TrustProxies.class);
        registry.addByType(VerifyCsrfToken.class);

        // 方式二：直接添加实例（适用于需要构造参数的中间件）
        registry.add(new EncryptCookies("my-secret-key-32bytes-long!"));
        registry.add(new Authenticate("api"));

        // 方式三：批量按类型注册
        registry.addAllByType(List.of(TrimStrings.class, TrustProxies.class));
    }
}
```

> `getMiddlewares()` 返回 `Collections.unmodifiableList` 不可修改视图，防止外部代码意外修改中间件链。

---

## 5. SpringBootRouteAutoConfiguration —— 路由自动装配

`com.weacsoft.jaravel.vendor.springboot.SpringBootRouteAutoConfiguration`

核心自动装配类，将 Jaravel `Router` 中注册的路由转换为 Spring `RouterFunction`，并在请求处理时执行中间件链。适配 Spring Boot 3.2.5 / Spring 6.x（`jakarta.servlet`、`org.springframework.web.servlet.function`）。

### 注册的 Bean

| Bean 方法 | 类型 | 条件 | 说明 |
| --- | --- | --- | --- |
| `baseRouter()` | `Router` | `@ConditionalOnMissingBean` | 基础路由器，用户可覆盖 |
| `jaravelRouterFunction(...)` | `RouterFunction<ServerResponse>` | 无 | 将 `Router` 的所有路由转为 Spring 路由函数 |

### 处理流程

```
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
   ├── 合并中间件：全局中间件 + 路由中间件
   ├── 逆序折叠中间件链（洋葱模型）
   ├── 终点调用 Route.getAction()::handle
   └── finally 清理 AuthContext / AuthManager
   │
   ▼
4. 将 Response 的状态码、响应头、Cookie、内容转为 Spring ServerResponse
```

### 中间件链折叠逻辑

中间件按 `全局中间件 + 路由中间件` 顺序排列，然后**逆序折叠**为嵌套调用：

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

执行顺序：`全局中间件[0] -> 全局中间件[1] -> ... -> 路由中间件[0] -> action`，响应则反向返回。

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
    public Router router(GlobalMiddlewareRegistry registry) {
        Router router = new Router();

        // 注册全局中间件
        registry.addByType(TrimStrings.class);
        registry.addByType(ConvertEmptyStringsToNull.class);

        // 注册路由
        router.get("/api/users", request ->
            ResponseBuilder.json(userService.list()));

        router.group(Map.of(Route.Group.PREFIX, "api"), api -> {
            api.get("/posts", postController::index);
            api.post("/posts", postController::store)
               .middleware(new Authenticate("api"));
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
| `SpringBootRouteAutoConfiguration` | `@AutoConfiguration` | imports 文件 | 路由桥接 + 中间件执行 |
| `ResponseAutoConfiguration` | `@AutoConfiguration` | imports 文件 | 注入返回值处理器 |
| `GlobalMiddlewareRegistry` | `@Component` | 组件扫描 | 全局中间件注册 |
| `SpringBootRequestMVCResolver` | `@Component` | 组件扫描 | Request 参数注入 |
| `SpringBootResponseMVCResolver` | `@ControllerAdvice` | 组件扫描 | Response 响应处理 + 安全头 |
| `baseRouter` (Router) | `@Bean` | `SpringBootRouteAutoConfiguration` | 基础路由器（可覆盖） |
| `jaravelRouterFunction` (RouterFunction) | `@Bean` | `SpringBootRouteAutoConfiguration` | Spring 路由函数 |

> 注意：`@Component` / `@ControllerAdvice` 标注的类需要应用的主类包路径能扫描到 `com.weacsoft.jaravel.vendor.springboot`。若应用包名不同，需手动 `@ComponentScan` 或通过 `@AutoConfiguration` 的 imports 文件确保 `@AutoConfiguration` 类被加载（`@AutoConfiguration` 类内部的 `@Bean` 不受组件扫描限制）。

---

## 11. 配置选项

`springboot` 模块通过 Spring Boot 自动装配机制工作，无需额外配置项。以下为可定制点：

| 定制点 | 方式 | 说明 |
| --- | --- | --- |
| 覆盖基础路由器 | 自定义 `Router` `@Bean` | `baseRouter()` 标注 `@ConditionalOnMissingBean`，用户定义的同类型 Bean 优先 |
| 注册全局中间件 | 注入 `GlobalMiddlewareRegistry` 调用 `add` / `addByType` | 在 `@PostConstruct` 或配置类中注册 |
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
| `GlobalMiddlewareRegistry` | 启动期安全 | `middlewares` 为普通 `ArrayList`，适合启动阶段注册、运行时只读。`getMiddlewares()` 返回不可修改视图。运行时并发注册非线程安全，应在应用启动阶段完成注册 |
| `SpringBootRouteAutoConfiguration` | 线程安全 | `@Bean` 在启动阶段创建；`jaravelRouterFunction` 构建的 `HandlerFunction` 为每次请求新建 `Request`，无共享可变状态。`AuthContext` / `AuthManager` 使用 ThreadLocal 或请求级清理，确保请求间隔离 |
| `ResponseAutoConfiguration` | 线程安全 | 构造器在启动阶段一次性修改 `RequestMappingHandlerAdapter` 的处理器链，之后只读 |
| `SpringBootRequestMVCResolver` | 线程安全 | `@Component` 单例，`resolveArgument` 每次调用构建新的 `Request`，无共享可变状态 |
| `SpringBootResponseMVCResolver` | 线程安全 | `@ControllerAdvice` 单例，`beforeBodyWrite` 无状态，仅操作方法参数（每请求独立） |
| `ResponseReturnValueHandler` | 线程安全 | 无实例字段，`handleReturnValue` 仅操作方法参数，每请求独立 |
