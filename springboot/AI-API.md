# springboot AI-API Reference

> Module: `springboot` | Package: `com.weacsoft.jaravel.vendor.springboot` | Version: 0.1.1

## Overview

springboot 模块是 Jaravel 框架与 Spring Boot Web MVC 的桥接层。`GlobalMiddlewareRegistry` 管理全局中间件列表，在请求处理前统一执行；`SpringBootRequestMVCResolver` 和 `SpringBootResponseMVCResolver` 负责在 Spring MVC 的 `HttpServletRequest`/`HttpServletResponse` 与框架内部请求/响应对象之间转换；`ResponseReturnValueHandler` 作为自定义 `HandlerMethodReturnValueHandler`，将 Controller 返回值统一包装为标准响应格式（`{code, message, data}`）。

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

### SpringBootRouteAutoConfiguration
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.springboot`
- **Annotations**: `@AutoConfiguration`, `@ConditionalOnWebApplication`, `@ConditionalOnClass({RouterFunction, GlobalMiddlewareRegistry})`
- **Description**: 路由桥接自动装配。注册 `GlobalMiddlewareRegistry`、`SpringBootRequestMVCResolver`、`SpringBootResponseMVCResolver` Bean，并配置 `RouterFunction` 将所有请求桥接到框架路由系统，在分发前执行全局中间件链。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `globalMiddlewareRegistry` | - | `GlobalMiddlewareRegistry` | 创建全局中间件注册表 Bean（`@Bean`, `@ConditionalOnMissingBean`） |
| `requestMVCResolver` | - | `SpringBootRequestMVCResolver` | 创建请求解析器 Bean（`@Bean`, `@ConditionalOnMissingBean`） |
| `responseMVCResolver` | - | `SpringBootResponseMVCResolver` | 创建响应解析器 Bean（`@Bean`, `@ConditionalOnMissingBean`） |
| `jaravelRouterFunction` | `GlobalMiddlewareRegistry registry, SpringBootRequestMVCResolver requestResolver, SpringBootResponseMVCResolver responseResolver, Router router` | `RouterFunction<ServerResponse>` | 创建 RouterFunction 桥接 Bean（`@Bean`） |

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
