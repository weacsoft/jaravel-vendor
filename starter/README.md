# starter 模块

> Jaravel-Vendor 的 Spring Boot Starter，一键引入基础必选的 Laravel 风格组件。只需添加一个 Maven 依赖，即可获得中间件、Auth、Validation、Config、Eloquent ORM、迁移、缓存、事件等全套能力（Blade 模板引擎 `jblade` 为可选模块，按需引入）。包名统一为 `com.weacsoft.jaravel.vendor.springboot`。

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
- [9. 使用注意](#9-使用注意)

---

## 1. 模块概述

`starter` 模块是 Jaravel-Vendor 框架的聚合入口，对齐 Spring Boot Starter 的设计理念：**约定优于配置，开箱即用**。

引入 `jaravel-starter` 后，框架自动完成以下工作：

1. **注册核心基础设施**：`ConfigRepository`（配置仓库，外部配置层经 `Environment::getProperty` 函数注入）、`ConfigDefinitionRegistrar`（代码级配置注册器）、`ProviderRegistry`（服务提供者注册器）——P3 起均为 core 纯类，由本装配在「单例就绪后」时机触发引导；core 静态门面的宿主支持由 jaravel-springboot 的 `CoreSpringConfiguration` 提供。
2. **聚合基础必选模块自动装配**：通过传递依赖引入 `core`、`http`、`springboot`、`auth`、`database`、`migration`、`cache`、`storage`、`event`、`artisan`、`schedule` 共 11 个基础模块，各模块的 `@AutoConfiguration` 类由 Spring Boot 自动加载。
3. **启用 Laravel 风格基础开发**：中间件管道、路由系统、Form Request 校验、门面（Facade）、配置仓库、Eloquent ORM、数据库迁移、缓存、事件分发全部就绪（Blade 模板渲染由可选的 `jblade` 模板引擎模块提供，按需引入）。

> 设计对齐 Laravel：**starter 只聚合基础必选组件，不假设用户一定有 Redis 或一定使用微信/队列**。Redis、微信、Wire、数据库队列、JWT 等均为**可选扩展模块**，由用户按需单独引入。
>
> - **`storage`（文件存储）**：Laravel 的 `Storage` 门面（local/public/framework 磁盘）属于框架基础能力，已默认聚合进 starter，无需单独引入。
> - **Redis 相关**（`redis` / `redis-cache` / `session-redis`）：Laravel 中 Redis 同样属于额外驱动，本框架不内建，需用户显式引入并选用驱动。
> - **`wechat-sdk`**：对齐 `overtrue/laravel-wechat`，属于业务扩展，非框架基础。
> - **`wire`**：对齐 `laravel-livewire`，属于 UI 部分更新扩展，非框架基础。
> - **`queue-database`**：Laravel 因无原生多线程而强制依赖数据库或 sync 队列；Java 拥有原生多线程，队列驱动为可选扩展，用户按需启用。
> - **`jwt`**：`auth` 的可选 Guard 扩展，按需引入。

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

`starter` 仅通过传递依赖聚合**基础必选** Jaravel-Vendor 模块；Redis、微信、Wire、数据库队列、JWT 等属于**可选扩展**，不纳入聚合，由用户按需单独引入（对齐 Laravel 的设计理念）。

| 模块 | artifactId | 提供能力 | 是否聚合进 starter |
| --- | --- | --- | --- |
| 核心 | `core` | 门面、配置仓库、服务提供者、校验器、Str/Arr 工具 | ✅ 是 |
| HTTP | `http` | 中间件管道、Request/Response、路由系统 | ✅ 是 |
| Spring Boot 集成 | `springboot` | RouterFunction 桥接、Request 注入、Response 处理 | ✅ 是 |
| 认证 | `auth` | Auth 门面、Session Guard、UserProvider | ✅ 是 |
| 数据库 | `database` | Eloquent ORM（基于 gaarason/database）、BaseModel、DataSource | ✅ 是 |
| 迁移 | `migration` | 数据库迁移（5 种源模式）、Schema 构建器、Blueprint | ✅ 是 |
| 缓存 | `cache` | 缓存管理器、Array/File 驱动 | ✅ 是 |
| 文件存储 | `storage` | Storage 门面、local/public/framework 磁盘驱动 | ✅ 是 |
| 模板引擎 | `jblade` | Blade 模板编译与渲染（表达式编译） | ❌ 可选，按需引入 |
| 事件 | `event` | 事件分发器、监听器注册、队列支持 | ✅ 是 |
| 命令行工具 | `artisan` | Artisan CLI 命令框架 | ✅ 是 |
| 定时任务 | `schedule` | 定时任务调度器 | ✅ 是 |
| Redis 配置 | `redis` | Redis 连接管理（多机 session/缓存同步基础） | ❌ 可选，按需引入 |
| Redis 缓存 | `redis-cache` | Redis 缓存驱动（多机缓存同步） | ❌ 可选，按需引入 |
| Redis Session | `session-redis` | Redis Session 守卫（多机 Session 同步） | ❌ 可选，按需引入 |
| 部分更新 | `wire` | Laravel Livewire 风格的部分更新模块 | ❌ 可选，按需引入 |
| 数据库队列 | `queue-database` | 数据库队列驱动（Java 有原生多线程，队列非强制，按需启用） | ❌ 可选，按需引入 |
| 微信 SDK | `wechat-sdk` | 微信公众号 / 小程序 API（对齐 overtrue/laravel-wechat） | ❌ 可选，按需引入 |
| JWT | `jwt` | JWT 认证（auth 的可选 Guard 扩展） | ❌ 可选，按需引入 |

### 外部依赖

| 依赖 | scope | 说明 |
| --- | --- | --- |
| `org.springframework.boot:spring-boot-starter-web` | optional | Web 应用支持（用户应用通常已引入） |

> 运行环境要求：JDK 17+，Spring Boot 3.2.12（Spring 6.x）。

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

Jaravel 核心自动装配类，注册配置仓库与引导组件，聚合各模块的自动装配。标注 `@AutoConfiguration` 与 `@ConditionalOnClass(ConfigRepository.class)`，确保仅在 core 模块存在时生效。

> **P3 说明**：core 模块已零 Spring 依赖（`@Component` 注解不再可能存在），
> 本类成为这些组件在 Spring 宿主中**唯一**的注册点（不再有两重注册问题）；
> 原 `springContext()` Bean 已移除——core 静态门面的宿主能力改由
> jaravel-springboot 的 `CoreSpringConfiguration` 安装 `GlobalBeanProvider` 提供，
> 行为与 P3 前完全一致（见 springboot 模块 README「P3 解耦适配层」）。

### 注册的 Bean

| Bean 方法 | 类型 | 条件 | 说明 |
| --- | --- | --- | --- |
| `configRepository(Environment)` | `ConfigRepository` | `@ConditionalOnMissingBean` | 配置仓库，外部配置层经 `environment::getProperty` 函数注入（P3 起 core 纯类） |
| `configDefinitionRegistrar(ConfigRepository, ObjectProvider<List<ConfigDefinition>>)` | `ConfigDefinitionRegistrar` | `@ConditionalOnMissingBean` | 代码级配置注册器（纯类），收集所有 `ConfigDefinition` Bean |
| `configDefinitionBoot(...)` | `SmartInitializingSingleton` | — | 单例就绪后触发 `registrar.boot()`（保持 P3 前时序） |
| `providerRegistry(ObjectProvider<List<ServiceProvider>>)` | `ProviderRegistry` | `@ConditionalOnMissingBean` | 服务提供者注册器（纯类） |
| `providerRegistryBoot(...)` | `SmartInitializingSingleton` | — | 单例就绪后触发 `registry.boot()`（保持 P3 前时序） |

### 设计说明

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
    public Router router(ApplicationContext context) {
        Router router = new Router();

        // 注册全局中间件
        router.middleware(context.getBean(TrimStrings.class));
        router.middleware(context.getBean(ConvertEmptyStringsToNull.class));

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
| `ConfigRepository` | 三层配置仓库（运行时覆盖 > 代码级 > 外部配置源，P3 起纯类 + 函数注入） |
| `ConfigDefinitionRegistrar` | 收集并注册 `ConfigDefinition` Bean（P3 起纯类，`SmartInitializingSingleton` 触发 `boot()`） |
| `ProviderRegistry` | 服务提供者两阶段引导（register → boot；P3 起纯类，触发同上） |
| *（门面宿主能力）* | core `SpringContext`/`Facade`/`App` 的宿主支持由 jaravel-springboot `CoreSpringConfiguration` 安装 `GlobalBeanProvider` 提供 |

### HTTP 层（由 springboot 模块自动装配）

| 组件 | 作用 |
| --- | --- |
| `SpringBootRouteAutoConfiguration` | Router → RouterFunction 桥接，中间件管道执行，内置 `@MiddlewareAlias` 别名扫描注册 |
| `ResponseAutoConfiguration` | 注入 ResponseReturnValueHandler |
| `SpringBootRequestMVCResolver` | Controller 方法 Request 参数注入 |
| `SpringBootResponseMVCResolver` | Response 响应处理 + 安全响应头 |

### 基础模块（已随 starter 聚合，自动装配）

| 模块 | 自动装配内容 |
| --- | --- |
| auth | AuthManager、AuthGuard、SessionGuard、认证中间件 |
| database | DataSource、Eloquent ORM（gaarason/database） |
| migration | MigrationRunner、MigrationRepository、Schema（5 种源模式） |
| cache | CacheManager、Cache 驱动（Array/File） |
| storage | StorageManager、Disk 驱动（local/public/framework）、StorageConfig 发布 |
| event | EventDispatcher、EventListenerRegistrar、QueueManager |
| artisan | Artisan CLI 命令注册与调度 |
| schedule | 定时任务调度器、Cron 表达式解析 |

### 可选扩展模块（不在 starter 中聚合，按需引入）

| 模块 | 自动装配内容 |
| --- | --- |
| redis | Redis 连接配置、连接池管理（多机同步基础） |
| jblade | BladeEngine、BladeCompiler（模板渲染，表达式编译） |
| redis-cache | Redis 缓存驱动（多机缓存同步） |
| session-redis | Redis Session 守卫（多机 Session 同步） |
| wire | Laravel Livewire 风格的部分更新组件 |
| queue-database | 数据库队列驱动（持久化 + 多实例消费） |
| wechat-sdk | 微信公众号 / 小程序 API 封装 |
| jwt | JWT 认证（auth 的可选 Guard 扩展，详见第 7 节） |

---

## 7. JWT 可选模块说明

**JWT 模块（`io.github.lijialong1313:jwt`）不在 starter 中聚合**，属于可选模块。这是有意设计：

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

## 9. 使用注意

- `Config.set` 是运行时的配置写入入口；高并发场景建议所有配置写入在启动阶段完成，或对外部调用加锁。starter 装配的其余组件均为「启动写入、运行时只读」。
