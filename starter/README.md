# starter 模块

> Jaravel-Vendor 的 Spring Boot Starter，一键引入全部 Laravel 风格组件。只需添加一个 Maven 依赖，即可获得中间件、Auth、Validation、Config、Eloquent ORM、迁移、缓存、事件、JBlade 模板引擎全套能力。包名统一为 `com.weacsoft.jaravel.vendor.springboot`。

---

## 目录

- [1. 模块概述](#1-模块概述)
- [2. 依赖信息](#2-依赖信息)
- [3. 类总览](#3-类总览)
- [4. JaravelAutoConfiguration —— 核心自动装配](#4-jaravelautoconfiguration--核心自动装配)
- [5. 快速开始](#5-快速开始)
- [6. 自动装配内容](#6-自动装配内容)
- [7. JWT 可选模块说明](#7-jwt-可选模块说明)
- [8. 配置优先级](#8-配置优先级)
- [9. 线程安全说明](#9-线程安全说明)

---

## 1. 模块概述

`starter` 模块是 Jaravel-Vendor 框架的聚合入口，对齐 Spring Boot Starter 的设计理念：**约定优于配置，开箱即用**。

引入 `jaravel-starter` 后，框架自动完成以下工作：

1. **注册核心基础设施**：`ConfigRepository`（配置仓库）、`SpringContext`（上下文持有器）、`ConfigDefinitionRegistrar`（代码级配置注册器）、`ProviderRegistry`（服务提供者注册器）。
2. **聚合各模块自动装配**：通过传递依赖引入 `core`、`http`、`springboot`、`auth`、`database`、`migration`、`cache`、`jblade`、`event`、`redis-config`、`redis-cache`、`session-redis`、`artisan`、`schedule`、`queue-database`、`wechat-sdk` 共 16 个模块，各模块的 `@AutoConfiguration` 类由 Spring Boot 自动加载。
3. **启用 Laravel 风格开发**：中间件管道、路由系统、Form Request 校验、门面（Facade）、配置仓库、Eloquent ORM、数据库迁移、缓存、事件分发、Blade 模板渲染全部就绪。

> **JWT 为可选模块**，不在 starter 中聚合。需要 JWT 认证时，用户按需单独引入 `com.weacsoft:jwt` 依赖即可。

---

## 2. 依赖信息

### Maven 坐标

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>starter</artifactId>
    <version>0.1.2</version>
</dependency>
```

### 聚合的内部模块

`starter` 通过传递依赖聚合以下 Jaravel-Vendor 模块：

| 模块 | artifactId | 提供能力 |
| --- | --- | --- |
| 核心 | `core` | 门面、配置仓库、服务提供者、校验器、Str/Arr 工具 |
| HTTP | `http` | 中间件管道、Request/Response、路由系统 |
| Spring Boot 集成 | `springboot` | RouterFunction 桥接、Request 注入、Response 处理 |
| 认证 | `auth` | Auth 门面、Session Guard、UserProvider |
| 数据库 | `database` | Eloquent ORM（基于 gaarason/database）、BaseModel、DataSource |
| 迁移 | `migration` | 数据库迁移（运行时编译，3 种源模式）、Schema 构建器、Blueprint |
| 缓存 | `cache` | 缓存管理器、Array/File 驱动 |
| 模板引擎 | `jblade` | Blade 模板编译与渲染（表达式编译） |
| 事件 | `event` | 事件分发器、监听器注册、队列支持 |
| Redis 配置 | `redis-config` | Redis 连接管理（多机 session/缓存同步基础） |
| Redis 缓存 | `redis-cache` | Redis 缓存驱动（多机缓存同步） |
| Redis Session | `session-redis` | Redis Session 守卫（多机 Session 同步） |
| 命令行工具 | `artisan` | Artisan CLI 命令框架 |
| 定时任务 | `schedule` | 定时任务调度器 |
| 数据库队列 | `queue-database` | 数据库队列驱动（持久化 + 多实例消费） |
| 微信 SDK | `wechat-sdk` | 微信公众号 / 小程序 API（对齐 overtrue/laravel-wechat） |

### 外部依赖

| 依赖 | scope | 说明 |
| --- | --- | --- |
| `org.springframework.boot:spring-boot-starter-web` | optional | Web 应用支持（用户应用通常已引入） |

> 运行环境要求：JDK 17+，Spring Boot 3.2.5（Spring 6.x）。

---

## 3. 类总览

```
com.weacsoft.jaravel.vendor.springboot
└── JaravelAutoConfiguration    // 核心自动装配（@AutoConfiguration）
```

自动装配注册文件（`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`）：

```
com.weacsoft.jaravel.vendor.springboot.JaravelAutoConfiguration
```

---

## 4. JaravelAutoConfiguration —— 核心自动装配

`com.weacsoft.jaravel.vendor.springboot.JaravelAutoConfiguration`

Jaravel 核心自动装配类，注册配置仓库与上下文持有器，聚合各模块的自动装配。标注 `@AutoConfiguration` 与 `@ConditionalOnClass(ConfigRepository.class)`，确保仅在 core 模块存在时生效。

### 注册的 Bean

| Bean 方法 | 类型 | 条件 | 说明 |
| --- | --- | --- | --- |
| `configRepository(Environment)` | `ConfigRepository` | `@ConditionalOnMissingBean` | 配置仓库，注入 Spring `Environment` 作为底层配置来源 |
| `configDefinitionRegistrar()` | `ConfigDefinitionRegistrar` | `@ConditionalOnMissingBean` | 代码级配置注册器，收集所有 `ConfigDefinition` Bean |
| `springContext()` | `SpringContext` | `@ConditionalOnMissingBean` | ApplicationContext 持有器，使门面能解析 Bean |
| `providerRegistry(List<ServiceProvider>)` | `ProviderRegistry` | `@ConditionalOnMissingBean` | 服务提供者注册器，执行两阶段引导 |

### 设计说明

#### 双重注册策略

`SpringContext`、`ConfigDefinitionRegistrar`、`ProviderRegistry` 在 core 模块中已标注 `@Component`，本类又通过 `@Bean` 显式声明。这是有意为之的**双重注册策略**：

- 当用户应用的组件扫描能覆盖 `com.weacsoft.jaravel.vendor.core` 包时，`@Component` 生效，`@ConditionalOnMissingBean` 阻止重复创建。
- 当用户应用未扫描到 core 包时，`@AutoConfiguration` 的 `@Bean` 兜底生效，确保核心组件始终可用。

#### 配置来源优先级

```
运行时覆盖（Config.set）
        ▼ 最高
代码级配置（ConfigDefinition）
        ▼
Spring Environment（application.yml）
        ▼ 最低
```

---

## 5. 快速开始

### 第一步：添加依赖

在 `pom.xml` 中添加 starter 依赖：

```xml
<dependencies>
    <dependency>
        <groupId>io.github.lijialong1313</groupId>
        <artifactId>starter</artifactId>
        <version>0.1.2</version>
    </dependency>
</dependencies>
```

### 第二步：编写启动类

```java
@SpringBootApplication
public class MyApp {
    public static void main(String[] args) {
        SpringApplication.run(MyApp.class, args);
    }
}
```

### 第三步：注册路由

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
        router.get("/", request -> ResponseBuilder.json(Map.of("message", "Hello Jaravel!")));

        router.group(Map.of(Route.Group.PREFIX, "api"), api -> {
            api.get("/users", userController::index);
            api.post("/users", userController::store);
        });

        return router;
    }
}
```

### 第四步：使用门面与配置

```java
// 定义代码级配置
@Component
public class AppConfig implements ConfigDefinition {
    @Override
    public String namespace() { return "app"; }

    @Override
    public Map<String, Object> values() {
        return Map.of("name", "My App", "version", "1.0");
    }
}

// 任意位置读取配置
String appName = Config.get("app.name", "default");
```

### 第五步：使用 Form Request 校验

```java
public class CreateUserRequest extends FormRequest {
    @Override
    public Map<String, String> rules() {
        return Map.of(
            "name", "required|string|min:2|max:50",
            "email", "required|email",
            "age", "required|integer|min:1|max:150"
        );
    }
}

// 在路由 action 中使用
router.post("/users", request -> {
    CreateUserRequest formRequest = new CreateUserRequest();
    Map<String, Object> validated = formRequest.validate(request.all());
    return ResponseBuilder.json(userService.create(validated));
});
```

---

## 6. 自动装配内容

引入 starter 后，Spring Boot 自动装配的完整组件清单：

### 核心基础设施（由 JaravelAutoConfiguration 注册）

| 组件 | 作用 |
| --- | --- |
| `ConfigRepository` | 三层配置仓库（运行时覆盖 > 代码级 > Environment） |
| `ConfigDefinitionRegistrar` | 自动发现并注册 `ConfigDefinition` Bean |
| `SpringContext` | ApplicationContext 持有器，支撑门面机制 |
| `ProviderRegistry` | 服务提供者两阶段引导（register → boot） |

### HTTP 层（由 springboot 模块自动装配）

| 组件 | 作用 |
| --- | --- |
| `SpringBootRouteAutoConfiguration` | Router → RouterFunction 桥接，中间件管道执行 |
| `ResponseAutoConfiguration` | 注入 ResponseReturnValueHandler |
| `GlobalMiddlewareRegistry` | 全局中间件注册器 |
| `SpringBootRequestMVCResolver` | Controller 方法 Request 参数注入 |
| `SpringBootResponseMVCResolver` | Response 响应处理 + 安全响应头 |

### 各功能模块（由各模块自身自动装配）

| 模块 | 自动装配内容 |
| --- | --- |
| auth | AuthManager、AuthGuard、SessionGuard、认证中间件 |
| database | DataSource、Eloquent ORM（gaarason/database） |
| migration | MigrationRunner、MigrationRepository、Schema（3 种源模式） |
| cache | CacheManager、Cache 驱动（Array/File） |
| event | EventDispatcher、EventListenerRegistrar、QueueManager |
| jblade | BladeEngine、BladeCompiler（模板渲染，表达式编译） |
| redis-config | Redis 连接配置、连接池管理（多机同步基础） |
| redis-cache | Redis 缓存驱动（多机缓存同步） |
| session-redis | Redis Session 守卫（多机 Session 同步） |
| artisan | Artisan CLI 命令注册与调度 |
| schedule | 定时任务调度器、Cron 表达式解析 |
| queue-database | 数据库队列驱动（持久化 + 多实例消费） |
| wechat-sdk | 微信公众号 / 小程序 API 封装 |

---

## 7. JWT 可选模块说明

**JWT 模块（`com.weacsoft:jwt`）不在 starter 中聚合**，属于可选模块。这是有意设计：

- JWT 依赖 `jjwt` 库，并非所有应用都需要 JWT 认证。
- 不强制引入可减少不必要的依赖体积。
- 用户按需引入，保持灵活性。

### 引入 JWT

需要 JWT 认证时，在 `pom.xml` 中额外添加：

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>jwt</artifactId>
    <version>0.1.2</version>
</dependency>
```

引入后，JWT 模块的自动装配类（`JwtAutoConfiguration`）会自动注册 `JwtService`、`JwtGuard` 等组件，可在 `auth` 模块的 `AuthManager` 中配置使用 JWT Guard。

### JWT 与 Auth 的关系

```
starter（聚合）
├── auth（必选）── 提供 AuthManager、Guard 抽象、SessionGuard
├── ...
└── jwt（可选，用户按需引入）── 提供 JwtGuard、JwtService
```

`auth` 模块是 starter 的必选依赖，提供认证框架；`jwt` 模块是 auth 的可选扩展，提供 JWT Guard 实现。未引入 jwt 时，可使用 SessionGuard 进行认证。

---

## 8. 配置优先级

Jaravel 的配置体系有三层来源，优先级从高到低：

```
┌─────────────────────────────────────┐
│  1. 运行时覆盖（Config.set）         │  最高优先级
│     内存写入，应用重启后失效          │
├─────────────────────────────────────┤
│  2. 代码级配置（ConfigDefinition）   │
│     Java 接口定义，对齐 config/*.php  │
├─────────────────────────────────────┤
│  3. Spring Environment              │  最低优先级
│     application.yml / 环境变量等     │
└─────────────────────────────────────┘
```

### 配置示例

`application.yml`：

```yaml
app:
  name: My Application
  debug: false

server:
  port: 8080
```

代码级配置（覆盖 yml）：

```java
@Component
public class AppConfig implements ConfigDefinition {
    @Override
    public String namespace() { return "app"; }

    @Override
    public Map<String, Object> values() {
        return Map.of("name", "Override Name", "debug", true);
    }
}
```

运行时覆盖（最高优先级）：

```java
Config.set("app.debug", false);  // 临时覆盖，重启失效
```

读取结果：

```java
Config.get("app.name");    // "Override Name"（代码级覆盖 yml）
Config.get("app.debug");   // false（运行时覆盖代码级）
Config.get("server.port"); // 8080（仅 yml 中有）
```

---

## 9. 线程安全说明

| 组件 | 线程安全性 | 说明 |
| --- | --- | --- |
| `JaravelAutoConfiguration` | 线程安全 | `@AutoConfiguration` 在启动阶段创建 Bean，所有 `@Bean` 方法返回的新实例在启动后只读 |
| `ConfigRepository` | 启动期写入，运行时只读 | `overrides` 与 `codeConfig` 使用 `LinkedHashMap`，启动阶段写入（`ConfigDefinitionRegistrar` 注册）、运行时通过 `Config` 门面只读。运行时调用 `Config.set` 并发写入需注意同步 |
| `SpringContext` | 启动后只读 | `context` 静态字段在启动时单次写入，之后并发只读安全 |
| `ConfigDefinitionRegistrar` | 启动期执行 | `SmartInitializingSingleton` 在所有单例就绪后单线程执行，无并发问题 |
| `ProviderRegistry` | 启动期执行 | 同上，两阶段引导在单线程中完成 |

> 总体而言，starter 装配的所有组件均遵循"启动阶段写入、运行时只读"的模式，运行时并发安全。唯一需注意的是 `Config.set` 的运行时写入，若在高并发场景频繁调用，建议外部加锁或在启动阶段完成所有配置写入。
