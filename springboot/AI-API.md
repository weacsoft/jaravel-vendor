# springboot AI-API Reference

> Module: `springboot` | Package: `com.weacsoft.jaravel.vendor.springboot` | Version: 0.1.2

## Overview

springboot 模块是 Jaravel 框架与 Spring Boot Web MVC 的桥接层。全局中间件直接声明在根 `Router` 上，通过 `router.middleware(...)` 注册，在请求处理前统一执行；`SpringBootRouteAutoConfiguration` 在 `jaravelRouterFunction` 构建路由前调用 `scanMiddlewareAliases(applicationContext)` 通过 classpath 扫描（`ClassPathScanningCandidateComponentProvider` + `AnnotationTypeFilter`）发现 `@MiddlewareAlias` 注解的中间件类，反射实例化（无参构造器，非 Spring Bean）后注册为别名中间件，并调用 `scanControllers(applicationContext)` 扫描容器中实现了 `Controllers` 接口的 Bean 注册到 `ControllerRegistry`；`@MiddlewareAlias` 注解（纯注解，`value()` 可选，不组合 `@Component`）标注在中间件类上声明命名别名，使路由可通过别名、Class 对象或类名字符串引用中间件；路由可通过字符串（`"UserController::list"`）或 Class 对象（`UserController.class` + 方法名）引用控制器方法，由 `ControllerActionResolver` 在首次请求时延迟解析；`SpringBootRequestMVCResolver` 和 `SpringBootResponseMVCResolver` 负责在 Spring MVC 的 `HttpServletRequest`/`HttpServletResponse` 与框架内部请求/响应对象之间转换；`ResponseReturnValueHandler` 作为自定义 `HandlerMethodReturnValueHandler`，将 Controller 返回值统一包装为标准响应格式（`{code, message, data}`）。

> 中间件与控制器的扫描差异说明：中间件是纯注解类，由框架通过 classpath 扫描 + 反射实例化（非 Spring Bean，无需 `@Component`）；控制器是 Spring Bean（需要 `@Autowired` 依赖注入），从 Spring 容器中获取（`getBeansOfType(Controllers.class)`）。两者均在 `jaravelRouterFunction` 构建路由前完成注册。

> auth 解耦说明：`SpringBootRouteAutoConfiguration` 通过 `RouteAuthHandler` 接口解耦对 auth 模块的 optional 依赖。当 auth 模块存在于 classpath 时，由 `AuthRouteAuthHandler`（`@ConditionalOnClass` 守卫）在请求处理前设置 `AuthContext`、请求结束后清理认证状态；当 auth 模块不存在时，由 `DefaultRouteAuthHandler` 提供 no-op 实现，避免 `NoClassDefFoundError`。`SpringBootRouteAutoConfiguration` 不再直接 import `AuthContext` 和 `AuthManager`。

## Classes & Interfaces

### 中间件别名扫描（SpringBootRouteAutoConfiguration 内置）
- **Type**: method of `SpringBootRouteAutoConfiguration`（原 `MiddlewareAliasRegistrar` 类已删除，扫描逻辑合并到 `SpringBootRouteAutoConfiguration`）
- **Description**: 别名中间件扫描逻辑。`jaravelRouterFunction` Bean 方法接收 `ApplicationContext` 作为第三个参数，并在构建路由前调用包级可见方法 `scanMiddlewareAliases(applicationContext)`，通过 classpath 扫描（`ClassPathScanningCandidateComponentProvider` + `AnnotationTypeFilter(MiddlewareAlias.class)`）发现所有 `@MiddlewareAlias` 注解的中间件类，反射实例化（无参构造器，非 Spring Bean）后注册到 `MiddlewareAliasRegistry.getGlobal()`，使路由可通过别名、Class 对象或类名字符串引用中间件。基础包由 `AutoConfigurationPackages.get(applicationContext)` 确定（即 `@SpringBootApplication` 类所在包及其子包）。

#### scanMiddlewareAliases 方法

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `scanMiddlewareAliases` | `ApplicationContext applicationContext` | `void` | 包级可见方法。通过 classpath 扫描发现 `@MiddlewareAlias` 注解的中间件类，反射实例化后注册到 `MiddlewareAliasRegistry.getGlobal()`。基础包由 `AutoConfigurationPackages.get(applicationContext)` 确定。对 `null` 上下文安全处理（直接返回，不抛异常）。在 `jaravelRouterFunction` 构建路由前一次性调用 |
| `scanMiddlewareAliases` | `ApplicationContext applicationContext, List<String> basePackages` | `void` | 包级可见重载方法，供测试直接指定基础包。扫描指定基础包中的 `@MiddlewareAlias` 注解中间件类并反射实例化注册。对 `null` 上下文或空包列表安全处理（直接返回，不抛异常） |

#### 扫描流程

```
jaravelRouterFunction(Router, RouteAuthHandler, ApplicationContext)
   │
   ├── scanMiddlewareAliases(applicationContext)   // classpath 扫描中间件别名
   │     ├── 通过 AutoConfigurationPackages.get() 确定基础包
   │     ├── ClassPathScanningCandidateComponentProvider + AnnotationTypeFilter 扫描
   │     ├── 反射实例化（无参构造器，非 Spring Bean）
   │     └── 注册到 MiddlewareAliasRegistry.getGlobal()
   │
   ├── scanControllers(applicationContext)          // 扫描控制器 Bean
   │     ├── 通过 getBeansOfType(Controllers.class) 获取容器中的控制器 Bean
   │     └── 注册到 ControllerRegistry.getGlobal()
   │
   └── 构建 RouterFunction（遍历路由、折叠中间件链）
```

#### Usage Example
```java
// 1. 声明中间件并注册别名（纯注解，不组合 @Component，非 Spring Bean）
//    @MiddlewareAlias("auth")：按别名 + 类注册
@MiddlewareAlias("auth")
public class AuthMiddleware implements Middleware {
    @Override
    public Response handle(Request request, NextFunction next, String... params) {
        // 认证逻辑...
        return next.apply(request);
    }
}

//    @MiddlewareAlias（无 value）：仅按类注册
@MiddlewareAlias
public class LogMiddleware implements Middleware {
    @Override
    public Response handle(Request request, NextFunction next, String... params) {
        return next.apply(request);
    }
}

// 2. 继承预定义中间件并自定义参数
//    预定义中间件（如 TrimStrings）不标注 @MiddlewareAlias，由使用者继承后自行标注；
//    通过覆盖方法配置参数而非构造器传参（无参构造器供反射实例化使用）
@MiddlewareAlias
public class AppTrimStrings extends TrimStrings {
    @Override
    protected String[] except() {
        return new String[]{"password", "password_confirmation"};
    }
}

// 3. 路由中引用中间件（三种模式）+ 控制器引用（两种形式）
//    别名：auth:api → AuthMiddleware.handle(request, next, "api")
router.get("/api/users", "UserController::list").middleware("auth:api");

//    Class 对象（可带参数）
router.get("/api/admin", "UserController::admin").middleware(AuthMiddleware.class, "api", "admin");

//    类名字符串（语法同别名）
router.get("/api/posts", "PostController::list").middleware("AuthMiddleware:api");

// 4. 控制器引用（两种形式）
//    字符串形式：ControllerName::methodName（简名或全限定名）
router.get("/users", "UserController::list");
router.get("/users/{id}", "UserController::show");

//    类对象形式（忽略包名，类型安全）
router.get("/users", UserController.class, "list");
router.get("/users/{id}", UserController.class, "show");

// 5. 注册全局中间件（对所有路由生效）—— 直接声明在根 Router 上
@Configuration
public class MiddlewareConfig {

    @Bean
    public Router router() {
        Router router = new Router();
        // 通过别名/类对象引用（需标注 @MiddlewareAlias 并被 classpath 扫描注册）
        router.middleware("cors");
        router.middleware(LoggingMiddleware.class);
        // 直接添加实例（适用于需要构造参数的中间件）
        router.middleware(new EncryptMiddleware("secret-key"));
        return router;
    }
}
```

### MiddlewareAlias
- **Type**: annotation
- **Package**: `com.weacsoft.jaravel.vendor.springboot.annotation`
- **Annotations**: `@Target(ElementType.TYPE)`, `@Retention(RetentionPolicy.RUNTIME)`（纯注解，不组合 `@Component`）
- **Description**: 中间件别名注解，对齐 Laravel `App\Http\Kernel::$routeMiddleware` 别名注册机制。标注在实现了 `Middleware` 的类上，由 `SpringBootRouteAutoConfiguration.scanMiddlewareAliases()` 通过 classpath 扫描（`ClassPathScanningCandidateComponentProvider` + `AnnotationTypeFilter`）在 `jaravelRouterFunction` 构建路由前发现并反射实例化（无参构造器）后注册到 `MiddlewareAliasRegistry.getGlobal()`。**本注解是纯注解，不组合 `@Component`**，标注后类不会被注册为 Spring Bean；中间件由框架通过反射实例化，适合无状态或通过继承覆盖方法配置参数的场景。`value()` 现为可选（`default ""`）。注解命名为 `MiddlewareAlias` 而非 `Middleware`，以避免与 `com.weacsoft.jaravel.vendor.http.middleware.Middleware` 接口同名冲突。

#### Elements

| Element | Type | Description |
|---------|------|-------------|
| `value` | `String`（可选，`default ""`） | 中间件别名。为空时仅按类注册；非空时按别名与类同时注册。例如值为 `"auth"` 时，路由中可使用 `middleware("auth:api")` 引用 |

#### 三种场景

| 场景 | 注解形式 | 扫描注册 | 路由引用方式 |
|------|----------|----------|--------------|
| 无注解 | 不标注 | 用户自建中间件，模块与 SpringBoot 均不扫描注册 | 仅能通过实例（`router.middleware(instance)`）或全局中间件声明使用 |
| 仅按类注册 | `@MiddlewareAlias`（无 value） | 被 classpath 扫描并反射实例化，按类注册到 `MiddlewareAliasRegistry` | 通过 Class 对象或类名字符串引用 |
| 按别名 + 类注册 | `@MiddlewareAlias("auth")` | 被 classpath 扫描并反射实例化，按别名与类同时注册到 `MiddlewareAliasRegistry` | 通过别名、Class 对象或类名字符串引用 |

#### 路由引用中间件的三种模式

| 模式 | 示例 | 适用注解 |
|------|------|----------|
| 别名 | `middleware("auth:api")` | `@MiddlewareAlias("auth")` |
| Class 对象 | `middleware(AuthMiddleware.class)` 或 `middleware(AuthMiddleware.class, "api", "admin")` | `@MiddlewareAlias` 或 `@MiddlewareAlias("auth")` |
| 类名字符串 | `middleware("AuthMiddleware:api")`（语法同别名） | `@MiddlewareAlias` 或 `@MiddlewareAlias("auth")` |

#### Usage Example
```java
// 1. 中间件（支持参数）—— 实现 Middleware（纯注解，非 Spring Bean）
//    @MiddlewareAlias("auth")：按别名 + 类注册
@MiddlewareAlias("auth")
public class AuthMiddleware implements Middleware {
    @Override
    public Response handle(Request request, NextFunction next, String... params) {
        String guard = params.length > 0 ? params[0] : "web";
        // 认证逻辑（根据 guard 执行不同策略）...
        return next.apply(request);
    }
}

//    @MiddlewareAlias（无 value）：仅按类注册
@MiddlewareAlias
public class LogMiddleware implements Middleware {
    @Override
    public Response handle(Request request, NextFunction next, String... params) {
        return next.apply(request);
    }
}

// 2. 继承预定义中间件并自定义参数
//    预定义中间件（如 TrimStrings）不标注 @MiddlewareAlias，由使用者继承后自行标注；
//    通过覆盖方法配置参数而非构造器传参（无参构造器供反射实例化使用）
@MiddlewareAlias
public class AppTrimStrings extends TrimStrings {
    @Override
    protected String[] except() {
        return new String[]{"password", "password_confirmation"};
    }
}

// 3. 路由中引用中间件（三种模式）+ 控制器引用（两种形式）
//    别名：auth:api → AuthMiddleware.handle(request, next, "api")
router.get("/api/users", "UserController::list").middleware("auth:api");

//    Class 对象（可带参数）
router.get("/api/admin", "UserController::admin").middleware(AuthMiddleware.class, "api", "admin");

//    类名字符串（语法同别名）
router.get("/api/posts", "PostController::list").middleware("AuthMiddleware:api");

// 或在路由组上使用：
router.group(Map.of(Route.Group.PREFIX, "api"), api -> {
    api.middleware("auth:api", "log");
    api.get("/users", "UserController::list");
});
```

### 控制器扫描（SpringBootRouteAutoConfiguration 内置）
- **Type**: method of `SpringBootRouteAutoConfiguration`
- **Description**: 控制器扫描逻辑。`jaravelRouterFunction` 在构建路由前调用包级可见方法 `scanControllers(applicationContext)`，扫描容器中所有实现了 `Controllers` 接口的 Bean 并注册到 `ControllerRegistry.getGlobal()`，使路由可通过字符串（`"ControllerName::method"`）或类对象引用控制器方法。与中间件不同，控制器是 Spring Bean（需要 `@Autowired` 依赖注入），因此从 Spring 容器中获取（`getBeansOfType(Controllers.class)`）而非 classpath 扫描。

#### scanControllers 方法

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `scanControllers` | `ApplicationContext applicationContext` | `void` | 包级可见方法。通过 `applicationContext.getBeansOfType(Controllers.class)` 获取容器中所有实现了 `Controllers` 接口的 Bean，注册到 `ControllerRegistry.getGlobal()`。对 `null` 上下文安全处理（直接返回，不抛异常）。在 `jaravelRouterFunction` 构建路由前一次性调用 |

#### Usage Example
```java
// 控制器实现 Controllers 接口（Spring Bean，支持 @Autowired 依赖注入）
@Component
public class UserController implements Controllers {

    @Autowired
    private UserService userService;

    // 控制器方法签名要求：Response method(Request request)
    public Response list(Request request) {
        return Response.ok(userService.findAll());
    }

    public Response show(Request request) {
        Long id = request.routeParam("id", Long.class);
        return Response.ok(userService.findById(id));
    }
}

// 路由中引用控制器（框架启动时自动扫描注册）
router.get("/users", "UserController::list");
router.get("/users/{id}", "UserController::show");
// 或类对象形式
router.get("/users", UserController.class, "list");
```

### ControllerRegistry
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.http.controller`
- **Description**: 控制器注册表，对齐 Laravel 控制器路由引用机制。内部维护两张映射表：`Class → 实例`（类映射）和 `String → 实例`（名称映射，简名和全限定名都写入）。控制器是 Spring Bean（需要 `@Autowired` 依赖注入），由 `SpringBootRouteAutoConfiguration` 在启动时扫描容器中实现了 `Controllers` 的 Bean 并注册到全局实例。注册后路由可通过字符串（`"ControllerName::method"`）或类对象引用控制器方法。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `getGlobal` | 无 | `ControllerRegistry` | 获取全局静态实例（静态方法） |
| `register` | `Object controller` | `void` | 注册控制器实例。同时写入类映射和名称映射（简名 + 全限定名） |
| `resolve` | `Class<?> clazz` | `Object` | 按 Class 对象解析控制器实例，类未注册时抛 `IllegalArgumentException` |
| `resolve` | `String name` | `Object` | 按名称（简名或全限定名）解析控制器实例，名称未注册时抛 `IllegalArgumentException` |
| `isClassRegistered` | `Class<?> clazz` | `boolean` | 检查类是否已注册 |
| `isNameRegistered` | `String name` | `boolean` | 检查名称是否已注册 |
| `getRegisteredClasses` | 无 | `Set<Class<?>>` | 获取所有已注册的控制器类（不可修改视图） |
| `clear` | 无 | `void` | 清除所有已注册的控制器（主要用于测试） |
| `registerGlobal` | `Object controller` | `void` | 向全局注册表注册控制器（静态便捷方法） |

#### Usage Example
```java
// 注册控制器（通常由框架自动完成）
ControllerRegistry.getGlobal().register(userController);

// 通过类名解析
Object controller = ControllerRegistry.getGlobal().resolve("UserController");

// 通过类对象解析
Object controller = ControllerRegistry.getGlobal().resolve(UserController.class);
```

### ControllerActionResolver
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.http.controller`
- **Description**: 控制器动作解析器，将控制器引用解析为 `Controllers.Runner`，对齐 Laravel 的控制器路由引用机制（`Route::get('/users', 'UserController@index')`）。支持两种引用方式：字符串形式（`"ControllerName::methodName"` 或 `"com.example.ControllerName::methodName"`，使用 `::` 分隔）和类对象形式（`Class<?>` + 方法名，忽略包名）。**延迟解析**：控制器引用在路由定义时存储为字符串/类对象，在首次请求时才解析，保证路由注册顺序与控制器扫描顺序无关——即使路由先于控制器注册定义，只要请求到达时控制器已注册即可正常工作。**缓存**：解析结果（Method + 控制器实例）通过内部 `ConcurrentMap<String, Controllers.Runner>` 缓存，后续请求直接复用，避免重复反射查找。控制器方法签名要求为 `Response method(Request request)`。所有方法均为静态方法。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `resolve` | `String controllerAction` | `Controllers.Runner` | 解析字符串形式的控制器引用为 Runner。格式：`"ControllerName::methodName"` 或 `"com.example.ControllerName::methodName"`。解析后缓存，后续调用直接返回缓存的 Runner。格式错误或控制器/方法不存在时抛 `IllegalArgumentException`（静态方法） |
| `resolve` | `Class<?> controllerClass, String methodName` | `Controllers.Runner` | 解析类对象形式的控制器引用为 Runner。通过 `ControllerRegistry` 查找控制器实例，反射查找指定方法。解析后缓存（静态方法） |
| `clearCache` | 无 | `void` | 清除缓存（主要用于测试，静态方法） |

#### Usage Example
```java
// 字符串形式
router.get("/users", "UserController::list");
router.get("/users/{id}", "UserController::show");

// 类对象形式（忽略包名）
router.get("/users", UserController.class, "list");
router.get("/users/{id}", UserController.class, "show");

// 手动解析（通常由 Router 内部调用）
Controllers.Runner runner = ControllerActionResolver.resolve("UserController::list");
Response response = runner.handle(request);
```

### Router 控制器引用重载
- **Type**: method overloads of `Router`
- **Package**: `com.weacsoft.jaravel.vendor.route`
- **Description**: `Router` 的所有 HTTP 方法（`get`/`post`/`put`/`delete`/`patch`/`all`）新增两种控制器引用重载，对齐 Laravel `Route::get('/users', 'UserController@index')`。控制器引用在路由定义时存储为字符串/类对象，通过 `ControllerActionResolver` 在首次请求时延迟解析。每种 HTTP 方法均有两个新重载：字符串形式（`String uri, String controllerAction`）和类对象形式（`String uri, Class<?> controllerClass, String methodName`）。

#### 重载方法

| HTTP 方法 | 字符串形式 | 类对象形式 |
|-----------|-----------|-----------|
| GET | `get(String uri, String controllerAction)` | `get(String uri, Class<?> controllerClass, String methodName)` |
| POST | `post(String uri, String controllerAction)` | `post(String uri, Class<?> controllerClass, String methodName)` |
| PUT | `put(String uri, String controllerAction)` | `put(String uri, Class<?> controllerClass, String methodName)` |
| DELETE | `delete(String uri, String controllerAction)` | `delete(String uri, Class<?> controllerClass, String methodName)` |
| PATCH | `patch(String uri, String controllerAction)` | `patch(String uri, Class<?> controllerClass, String methodName)` |
| ALL | `all(String uri, String controllerAction)` | `all(String uri, Class<?> controllerClass, String methodName)` |

> 延迟解析说明：控制器引用在路由定义时通过 `lazyResolve()` 包装为 `Controllers.Runner`，实际解析推迟到首次请求时执行。`ControllerActionResolver` 内部缓存解析结果，后续请求无额外反射开销。这保证了路由注册顺序与控制器扫描顺序无关。

#### Usage Example
```java
// 字符串形式（简名或全限定名）
router.get("/users", "UserController::list");
router.post("/users", "UserController::create");
router.put("/users/{id}", "UserController::update");
router.delete("/users/{id}", "UserController::delete");

// 类对象形式（忽略包名，类型安全）
router.get("/users", UserController.class, "list");
router.post("/users", UserController.class, "create");
router.put("/users/{id}", UserController.class, "update");
router.delete("/users/{id}", UserController.class, "delete");

// 多方法路由
router.all("/ping", "SystemController::ping");
```

### SpringBootRequestMVCResolver
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.springboot`
- **Description**: 请求解析器。将 Spring MVC 的 `HttpServletRequest` 转换为框架内部的 `Request` 对象，提取请求方法、URI、Header、参数和请求体，供路由分发和中间件使用。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `resolve` | `HttpServletRequest servletRequest` | `Request` | 将 Servlet 请求转换为框架内部请求对象 |
| `resolveMethod` | `HttpServletRequest request` | `String` | 提取 HTTP 方法（GET/POST/PUT/DELETE 等） |
| `resolveUri` | `HttpServletRequest request` | `String` | 提取请求 URI（去除 context path） |
| `resolveHeaders` | `HttpServletRequest request` | `Map<String, String>` | 提取所有请求头 |
| `resolveParameters` | `HttpServletRequest request` | `Map<String, String>` | 提取所有请求参数 |
| `resolveBody` | `HttpServletRequest request` | `String` | 提取请求体（JSON 字符串） |

#### Usage Example
```java
@Autowired
private SpringBootRequestMVCResolver requestResolver;

Request internalRequest = requestResolver.resolve(servletRequest);
String method = internalRequest.getMethod();
String uri = internalRequest.getUri();
```

### SpringBootResponseMVCResolver
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.springboot`
- **Description**: 响应解析器。将框架内部的 `Response` 对象转换为 Spring MVC 的 `HttpServletResponse`，设置状态码、响应头和响应体。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `resolve` | `Response internalResponse, HttpServletResponse servletResponse` | `void` | 将框架响应写入 Servlet 响应 |
| `resolveStatus` | `Response response` | `int` | 提取 HTTP 状态码 |
| `resolveHeaders` | `Response response` | `Map<String, String>` | 提取响应头 |
| `resolveBody` | `Response response` | `String` | 提取响应体（JSON 字符串） |

#### Usage Example
```java
@Autowired
private SpringBootResponseMVCResolver responseResolver;

Response internalResponse = Response.ok(result);
responseResolver.resolve(internalResponse, servletResponse);
```

### ResponseReturnValueHandler
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.springboot`
- **Implements**: `org.springframework.web.method.support.HandlerMethodReturnValueHandler`
- **Description**: 自定义返回值处理器。拦截 Controller 方法返回值，将非 `Response` 类型的返回值自动包装为统一响应格式 `{"code": 200, "message": "success", "data": ...}`。对于已返回 `Response` 类型的值直接透传。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `supportsReturnType` | `HandlerMethodReturnType returnType` | `boolean` | 支持所有返回类型（返回 true） |
| `handleReturnValue` | `Object returnValue, HandlerMethodReturnType returnType, ModelAndViewContainer mavContainer, NativeWebRequest webRequest` | `void` | 处理返回值，包装为统一响应格式写入响应 |

#### Usage Example
```java
// Controller 方法直接返回业务对象，自动包装为统一响应
@RestController
@RequestMapping("/api")
public class UserController {

    @GetMapping("/users/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.findById(id);
        // 实际响应: {"code":200,"message":"success","data":{"id":1,"name":"..."}}
    }
}
```

### RouteAuthHandler
- **Type**: interface
- **Package**: `com.weacsoft.jaravel.vendor.springboot`
- **Description**: 路由认证处理器接口，用于解耦 springboot 模块对 auth 模块的 optional 依赖。当 auth 模块存在于 classpath 时，由 `AuthRouteAuthHandler` 提供实现，在请求处理前设置 `AuthContext`，请求结束后清理认证状态。当 auth 模块不存在时，由 `DefaultRouteAuthHandler` 提供 no-op 实现，所有操作变为空操作，避免 `NoClassDefFoundError`。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `setupAuth` | `Request request` | `void` | 设置当前请求的认证上下文 |
| `clearAuth` | 无 | `void` | 清理认证上下文（请求结束后调用） |

---

### DefaultRouteAuthHandler
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.springboot`
- **Implements**: `com.weacsoft.jaravel.vendor.springboot.RouteAuthHandler`
- **Description**: 默认路由认证处理器（no-op 实现）。当 auth 模块不在 classpath 时使用此实现，所有操作为空操作。由 `@ConditionalOnMissingBean(RouteAuthHandler.class)` 守卫，仅在没有其他 `RouteAuthHandler` 实现时创建。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `setupAuth` | `Request request` | `void` | no-op：auth 模块不在 classpath |
| `clearAuth` | 无 | `void` | no-op：auth 模块不在 classpath |

---

### AuthRouteAuthHandler
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.springboot`
- **Implements**: `com.weacsoft.jaravel.vendor.springboot.RouteAuthHandler`
- **Annotations**: `@ConditionalOnClass(AuthManager.class)`
- **Description**: 基于 auth 模块的路由认证处理器。当 `AuthManager` 存在于 classpath 时启用，在请求处理前通过 `AuthContext.set(request)` 设置认证上下文，请求结束后通过 `AuthContext.clear()` 和 `AuthManager.clear()` 清理 ThreadLocal 状态。`@ConditionalOnClass` 通过 ASM 读取字节码注解，不会触发类加载；当 auth 模块不在 classpath 时，Spring 不会加载此类，从而避免 `NoClassDefFoundError`。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `AuthRouteAuthHandler` | `AuthManager authManager` | 构造方法 | 创建基于 auth 模块的认证处理器 |
| `setupAuth` | `Request request` | `void` | 通过 `AuthContext.set(request)` 设置认证上下文 |
| `clearAuth` | 无 | `void` | 通过 `AuthContext.clear()` 和 `authManager.clear()` 清理 ThreadLocal 状态 |

#### Usage Example
```java
// 自动装配：当 auth 模块在 classpath 时，Spring 自动创建 AuthRouteAuthHandler
// 当 auth 模块不在 classpath 时，自动创建 DefaultRouteAuthHandler（no-op）
// 业务代码无需关心 auth 模块是否存在，统一通过 RouteAuthHandler 接口调用
```

### SpringBootRouteAutoConfiguration
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.springboot`
- **Annotations**: `@AutoConfiguration`
- **Description**: 路由桥接自动装配。注册 `Router`、`SpringBootRequestMVCResolver`、`SpringBootResponseMVCResolver` Bean，并配置 `RouterFunction` 将所有请求桥接到框架路由系统，在分发前执行全局中间件链（全局中间件直接声明在根 `Router` 上）。`jaravelRouterFunction` 接收 `ApplicationContext` 作为第三个参数，构建路由前调用包级可见方法 `scanMiddlewareAliases(applicationContext)` 通过 classpath 扫描 `@MiddlewareAlias` 注解的中间件类（反射实例化，非 Spring Bean）并注册到 `MiddlewareAliasRegistry.getGlobal()`，并调用 `scanControllers(applicationContext)` 扫描容器中实现了 `Controllers` 接口的 Bean 注册到 `ControllerRegistry.getGlobal()`（原独立的 `MiddlewareAliasRegistrar` 已删除，扫描逻辑合并到本类）。通过 `RouteAuthHandler` 接口解耦对 auth 模块的 optional 依赖：当 auth 模块在 classpath 且 `AuthManager` bean 存在时创建 `AuthRouteAuthHandler`，否则创建 `DefaultRouteAuthHandler`（no-op）。不再直接 import `AuthContext` 和 `AuthManager`，使用 `@ConditionalOnClass(name = ...)` 字符串形式避免触发类加载。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `baseRouter` | 无 | `Router` | 创建框架路由器 Bean（`@Bean`, `@ConditionalOnMissingBean`） |
| `requestMVCResolver` | - | `SpringBootRequestMVCResolver` | 创建请求解析器 Bean（`@Bean`, `@ConditionalOnMissingBean`） |
| `responseMVCResolver` | - | `SpringBootResponseMVCResolver` | 创建响应解析器 Bean（`@Bean`, `@ConditionalOnMissingBean`） |
| `authRouteAuthHandler` | `com.weacsoft.jaravel.vendor.auth.AuthManager authManager` | `RouteAuthHandler` | 创建基于 auth 模块的认证处理器 Bean（`@Bean`, `@ConditionalOnClass(name = "com.weacsoft.jaravel.vendor.auth.AuthManager")`, `@ConditionalOnBean(type = "com.weacsoft.jaravel.vendor.auth.AuthManager")`, `@ConditionalOnMissingBean(RouteAuthHandler.class)`）；参数使用全限定名，无需 import |
| `defaultRouteAuthHandler` | 无 | `RouteAuthHandler` | 创建 no-op 认证处理器 Bean（`@Bean`, `@ConditionalOnMissingBean(RouteAuthHandler.class)`）；当 auth 模块不在 classpath 时作为 fallback |
| `jaravelRouterFunction` | `Router router, RouteAuthHandler routeAuthHandler, ApplicationContext applicationContext` | `RouterFunction<ServerResponse>` | 创建 RouterFunction 桥接 Bean（`@Bean`）；构建路由前先调用 `scanMiddlewareAliases(applicationContext)` 通过 classpath 扫描 `@MiddlewareAlias` 中间件类注册到 `MiddlewareAliasRegistry.getGlobal()`，再调用 `scanControllers(applicationContext)` 扫描容器中 `Controllers` Bean 注册到 `ControllerRegistry.getGlobal()`；请求处理时先通过 `routeAuthHandler.setupAuth()` 设置认证上下文，执行中间件链和路由，结束后 `routeAuthHandler.clearAuth()` 清理 |
| `scanMiddlewareAliases` | `ApplicationContext applicationContext` | `void` | 包级可见方法。通过 classpath 扫描（`ClassPathScanningCandidateComponentProvider` + `AnnotationTypeFilter`）发现 `@MiddlewareAlias` 注解的中间件类，反射实例化（无参构造器）后注册到 `MiddlewareAliasRegistry.getGlobal()`；基础包由 `AutoConfigurationPackages.get(applicationContext)` 确定；对 `null` 上下文安全处理（直接返回，不抛异常） |
| `scanMiddlewareAliases` | `ApplicationContext applicationContext, List<String> basePackages` | `void` | 包级可见重载方法，供测试直接指定基础包。扫描指定基础包中的 `@MiddlewareAlias` 注解中间件类并反射实例化注册；对 `null` 上下文或空包列表安全处理（直接返回，不抛异常） |
| `scanControllers` | `ApplicationContext applicationContext` | `void` | 包级可见方法。通过 `applicationContext.getBeansOfType(Controllers.class)` 获取容器中所有实现了 `Controllers` 接口的 Bean，注册到 `ControllerRegistry.getGlobal()`；控制器是 Spring Bean（需要 `@Autowired`），因此从容器获取而非 classpath 扫描；对 `null` 上下文安全处理（直接返回，不抛异常） |

#### Usage Example
```java
// 自动装配后，所有 HTTP 请求经 RouterFunction 桥接到 Jaravel 路由系统
// 全局中间件在路由分发前自动执行
```

### ResponseAutoConfiguration
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.springboot`
- **Annotations**: `@AutoConfiguration`, `@ConditionalOnWebApplication`, `@ConditionalOnClass({ResponseBodyAdvice, ResponseReturnValueHandler})`
- **Description**: 统一响应自动装配。注册 `ResponseReturnValueHandler` 到 Spring MVC 的 `RequestMappingHandlerAdapter`，使所有 Controller 返回值自动包装为统一响应格式。同时注册 `ResponseBodyAdvice` 作为兜底处理。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `responseReturnValueHandler` | - | `ResponseReturnValueHandler` | 创建返回值处理器 Bean（`@Bean`, `@ConditionalOnMissingBean`） |
| `responseBodyAdvice` | - | `ResponseBodyAdvice<Object>` | 创建响应体 Advice Bean（`@Bean`） |

#### Usage Example
```yaml
# application.yml - 统一响应默认启用，无需额外配置
# 可通过 jaravel.response.uniform=false 关闭
jaravel:
  response:
    uniform: true
```
