# springboot AI-API Reference

> Module: `springboot` | Package: `com.weacsoft.jaravel.vendor.springboot` | Version: 0.1.2

## Overview

springboot 模块是 Jaravel 框架与 Spring Boot Web MVC 的桥接层。`GlobalMiddlewareRegistry` 管理全局中间件列表，在请求处理前统一执行；`SpringBootRequestMVCResolver` 和 `SpringBootResponseMVCResolver` 负责在 Spring MVC 的 `HttpServletRequest`/`HttpServletResponse` 与框架内部请求/响应对象之间转换；`ResponseReturnValueHandler` 作为自定义 `HandlerMethodReturnValueHandler`，将 Controller 返回值统一包装为标准响应格式（`{code, message, data}`）。

> auth 解耦说明：`SpringBootRouteAutoConfiguration` 通过 `RouteAuthHandler` 接口解耦对 auth 模块的 optional 依赖。当 auth 模块存在于 classpath 时，由 `AuthRouteAuthHandler`（`@ConditionalOnClass` 守卫）在请求处理前设置 `AuthContext`、请求结束后清理认证状态；当 auth 模块不存在时，由 `DefaultRouteAuthHandler` 提供 no-op 实现，避免 `NoClassDefFoundError`。`SpringBootRouteAutoConfiguration` 不再直接 import `AuthContext` 和 `AuthManager`。

## Classes & Interfaces

### GlobalMiddlewareRegistry
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.springboot`
- **Annotations**: `@Component`
- **Description**: 全局中间件注册表。维护一个有序的中间件列表，所有 HTTP 请求在路由分发前都会依次执行这些中间件。支持通过 `add()` 动态注册和通过 `getMiddlewares()` 获取列表。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `add` | `Middleware middleware` | `void` | 添加全局中间件到列表末尾 |
| `add` | `int index, Middleware middleware` | `void` | 在指定位置插入全局中间件 |
| `addFirst` | `Middleware middleware` | `void` | 将中间件添加到列表头部（最高优先级） |
| `remove` | `Middleware middleware` | `void` | 移除指定中间件 |
| `getMiddlewares` | - | `List<Middleware>` | 获取所有全局中间件（有序） |
| `clear` | - | `void` | 清空所有全局中间件 |
| `size` | - | `int` | 获取全局中间件数量 |

#### Usage Example
```java
@Component
public class MiddlewareConfig {
    @Autowired
    private GlobalMiddlewareRegistry registry;

    @PostConstruct
    public void setup() {
        registry.add(new CorsMiddleware());
        registry.add(new AuthMiddleware());
        registry.addFirst(new LoggingMiddleware());  // 最高优先级
    }
}
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
- **Description**: 路由桥接自动装配。注册 `Router`、`GlobalMiddlewareRegistry`、`SpringBootRequestMVCResolver`、`SpringBootResponseMVCResolver` Bean，并配置 `RouterFunction` 将所有请求桥接到框架路由系统，在分发前执行全局中间件链。通过 `RouteAuthHandler` 接口解耦对 auth 模块的 optional 依赖：当 auth 模块在 classpath 且 `AuthManager` bean 存在时创建 `AuthRouteAuthHandler`，否则创建 `DefaultRouteAuthHandler`（no-op）。不再直接 import `AuthContext` 和 `AuthManager`，使用 `@ConditionalOnClass(name = ...)` 字符串形式避免触发类加载。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `baseRouter` | 无 | `Router` | 创建框架路由器 Bean（`@Bean`, `@ConditionalOnMissingBean`） |
| `globalMiddlewareRegistry` | - | `GlobalMiddlewareRegistry` | 创建全局中间件注册表 Bean（`@Bean`, `@ConditionalOnMissingBean`） |
| `requestMVCResolver` | - | `SpringBootRequestMVCResolver` | 创建请求解析器 Bean（`@Bean`, `@ConditionalOnMissingBean`） |
| `responseMVCResolver` | - | `SpringBootResponseMVCResolver` | 创建响应解析器 Bean（`@Bean`, `@ConditionalOnMissingBean`） |
| `authRouteAuthHandler` | `com.weacsoft.jaravel.vendor.auth.AuthManager authManager` | `RouteAuthHandler` | 创建基于 auth 模块的认证处理器 Bean（`@Bean`, `@ConditionalOnClass(name = "com.weacsoft.jaravel.vendor.auth.AuthManager")`, `@ConditionalOnBean(type = "com.weacsoft.jaravel.vendor.auth.AuthManager")`, `@ConditionalOnMissingBean(RouteAuthHandler.class)`）；参数使用全限定名，无需 import |
| `defaultRouteAuthHandler` | 无 | `RouteAuthHandler` | 创建 no-op 认证处理器 Bean（`@Bean`, `@ConditionalOnMissingBean(RouteAuthHandler.class)`）；当 auth 模块不在 classpath 时作为 fallback |
| `jaravelRouterFunction` | `Router router, ObjectProvider<GlobalMiddlewareRegistry> globalMiddlewareProvider, RouteAuthHandler routeAuthHandler` | `RouterFunction<ServerResponse>` | 创建 RouterFunction 桥接 Bean（`@Bean`）；请求处理时先通过 `routeAuthHandler.setupAuth()` 设置认证上下文，执行中间件链和路由，结束后 `routeAuthHandler.clearAuth()` 清理 |

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
