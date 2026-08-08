# 模块注册、依赖回退与发布配置

本文档说明 jaravel-vendor 各模块的**注解式注册机制**、**模块间依赖与回退策略**、
**artisan / migration 使用要求**以及**数据库表要求**。

> 本文档内容全部依据**实际代码**核对，与代码不一致处以代码为准。

---

## 一、注解式注册机制

### 1.1 设计目标

对齐 Laravel 的 `ServiceProvider` 注册体验：在配置类中用注解声明组件，
框架在启动时扫描并注册。**注册产物不进入 Spring BeanFactory**。

### 1.2 为什么产物不进 Spring 容器

Spring 的 bean name 全局唯一。若用 `@Bean("admin")` 注册名为 `admin` 的守卫，
另一个模块也想注册名为 `admin` 的缓存 store，就会触发
`BeanDefinitionOverrideException`。

注解机制把**组件名称**与 **bean name** 解耦：

- 注解方法本身写在 Spring 配置类上（可正常注入其他 Bean 作为方法参数）
- 方法**返回的产物**只存入各模块自己的 Manager（如 `AuthManager`、`CacheManager`）

因此 `@RegisterGuard("admin")` 与 `@RegisterCacheStore("admin")` 可以共存。

> **注意**：扫描机制本身依赖 Spring 容器（遍历 `getBeanDefinitionNames()`、
> 用 `getBean(type)` 解析方法参数）。所谓"不放进 Spring Bean"指的是**产物**，
> 而非注解方法所在的配置类。

### 1.3 注解一览

| 注解 | 所属模块 | 产物类型 | 多实例 | 说明 |
|------|---------|---------|-------|------|
| `@RegisterGuard` | auth | `GuardDefinition` | ✅ 命名多实例 | 守卫，`defaultGuard = true` 设为默认 |
| `@RegisterProvider` | auth | `UserProvider` | ✅ 命名多实例 | 用户提供者 |
| `@RegisterSessionStore` | http | `SessionStore` | ❌ **全局唯一** | Session 存储（归属 http 模块） |
| `@RegisterCacheStore` | cache | `CacheStore` | ✅ 命名多实例 | 缓存 store，`defaultStore = true` 设为默认 |
| `@RegisterDisk` | storage | `DiskDefinition` / `Filesystem` | ✅ 命名多实例 | 文件磁盘，`defaultDisk = true` 设为默认 |
| `@RegisterConnection` | database | `GaarasonDataSource` | ✅ 命名多实例 | 数据源连接 |
| `@RegisterQueueDriver` | queue-database | `QueueDriver` | ❌ **全局唯一** | 队列驱动 |
| `@RegisterDirective` | jblade | `Handler` / `Condition` | ✅ 命名多实例 | Blade 自定义指令 |
| `@RegisterView` | jblade | `View` | ✅ 命名多实例 | 自定义视图 |

**jwt 模块没有自己的注解**：它提供 `JwtGuardDriver`，通过 auth 的
`@RegisterGuard("api")` + `GuardDefinition.of("jwt", "users")` 接入，
避免同一概念存在两套注册表。

### 1.4 命名多实例 vs 全局唯一

**命名多实例**（guard / provider / cache store / disk / directive / view）：
一个应用可注册任意多个不同名字的实例，通过名称选用，同名后注册者覆盖先注册者。

**全局唯一**（SessionStore / QueueDriver）：这类组件语义上只能有一个
（"登录态存哪里"、"任务推到哪个队列"）。框架**强制唯一性**，
扫描到多个时**启动直接报错**，避免隐式歧义。

需要覆盖框架默认时，使用 `override = true`：

```java
@RegisterSessionStore(override = true)   // 优先生效
public SessionStore mySessionStore() { ... }
```

同时存在多个 `override = true` 仍会报错。

### 1.5 注册优先级

以 SessionStore 为例（其他单实例组件同理）：

1. `@RegisterSessionStore(override = true)` 注解方法
2. `@RegisterSessionStore` 注解方法
3. 容器中已有的 `SessionStore` Bean（兼容旧的 `@Bean` 写法）
4. 回退默认：`CookieSessionStore`（Servlet HttpSession）

对命名多实例组件，**注解式注册优先于配置式注册**（如 `jaravel.storage.disks`），
同名时注解覆盖配置。

### 1.6 底层实现

位于 `core` 模块 `com.weacsoft.jaravel.vendor.core.registrar` 包：

| 类 | 用途 |
|----|------|
| `AnnotationDrivenRegistrar<A>` | 单注解扫描注册基类，提供 `beforeScan()` / `register()` / `afterScan()` 钩子 |
| `SingletonRegistrar<A, T>` | 单实例注册基类，在上者基础上增加唯一性校验与 `applyFallback()` 回退 |
| `AnnotationScanner` | 扫描工具，供需扫描**多种注解**或**控制扫描顺序**的场景使用 |
| `RegistrarException` | 注册异常（重复注册、返回类型不匹配、方法调用失败） |

扫描使用 `getMethods()` 而非 `getDeclaredMethods()`，
以正确处理 CGLIB 代理的 `@Configuration` 类与继承自父类的注解方法。

---

## 二、模块依赖与回退策略

### 2.1 总原则

**core 是唯一强依赖**，其余模块之间遵循"**有则使用，无则回退默认**"。
默认实现通常采用**内存方式**（如 queue 的 sync）或**文件方式**（如 storage 的 local）。

### 2.2 驱动型模块：安装 ≠ 启用

把依赖放进 classpath 只表示"可用"，不表示"启用"。
只有用户显式选用（或按兜底默认选用）了该驱动，才进行注册和配置；否则完全静默。

以下**所有驱动**统一通过 core 的 `OnDriverInUseCondition`（仅依赖 `spring-context`）
判定，子类声明"驱动名 + 配置键"即可：

| 驱动模块 | 驱动名 | 启用条件（满足其一） | 未启用时 |
|---------|-------|-------------------|---------|
| **session-redis** | `redis` | `jaravel.session.driver: redis` | 不装配，Session 回退 `CookieSessionStore` |
| **redis-cache** | `redis` | 任一 `jaravel.cache.stores.*.driver: redis` | 不装配，不连接 Redis |
| **cache 的 database 驱动** | `database` | 任一 `jaravel.cache.stores.*.driver: database` | 不装配，无需数据源 |
| **queue-database (redis)** | `redis` | `jaravel.queue.driver: redis` | 不装配，回退 `sync` |
| **queue-database (database)** | `database` | `jaravel.queue.driver: database` | 不装配，回退 `sync` |
| **auth (session 守卫)** | `session` | 任一 `jaravel.auth.guards.*.driver: session` 或**未写 driver（兜底）** | —（自身即兜底） |
| **auth (jwt 守卫)** | `jwt` | 任一 `jaravel.auth.guards.*.driver: jwt` | 不装配（**JWT 不在兜底**） |
| **storage (local 磁盘)** | `local` / `public` | 任一 `jaravel.storage.disks.*.driver: local` 或**未写 driver（兜底）** | —（自身即兜底） |
| **storage (database 磁盘)** | `database` | 任一 `jaravel.storage.disks.*.driver: database` | 不装配 |

每个驱动模块还提供一个**覆盖开关**（优先级最高）：

```yaml
jaravel:
  session:
    redis:
      auto-register: true     # true=强制启用；false=强制关闭；不配置=按 driver 自动判定
  cache:
    redis:
      auto-register: false
```

> **不要用 `@ConditionalOnBean(DataSource.class)` 之类判断驱动是否可用。**
> 正确做法是**运行时惰性解析**：先查 jaravel 的注册表，再回退 Spring 容器。

### 2.3 功能型模块：默认启用

不需要外部资源的**功能模块**（wire、storage 的 local、schedule、captcha、
plugin-* 等）默认启用，通过 `jaravel.<模块>.enabled: false` 关闭。

> **发布配置独立于运行开关**：各模块的 `PublishableConfig` 注册在独立的
> `*PublishAutoConfiguration` 中，**不受 `jaravel.<模块>.enabled` 控制**。

### 2.4 数据源解析顺序：先框架，后 Spring

任何需要数据源的模块（cache 的 database 驱动、migration、storage 的 database 磁盘）
都**不直接依赖 Spring 的 `DataSource` Bean**，而是按以下顺序在**运行时**解析：

1. **jaravel `ConnectionManager` 注册表** —— 由 `@RegisterConnection` 声明的连接；
2. **Spring 容器** —— 同名 Bean → 主 `DataSource` Bean。

默认连接：标记了 `@RegisterConnection(defaultConnection = true)` 的连接即为默认连接；
若一个都没标记，则第一个注册的连接自动成为默认连接。
该默认连接会以 `JaravelDataSource`（惰性委托）的形式注册为 Spring Bean。

### 2.5 各模块回退矩阵

| 模块 | 有依赖时 | 无依赖时的回退 |
|------|---------|--------------|
| **cache** | `redis`（引入 redis-cache 并选用）/ `database`（选用且有连接）/ `file` | **array 内存驱动**（进程内，重启丢失） |
| **storage** | `database` 磁盘（有连接，自动建表） | **local 文件驱动**，根目录 `storage/app` |
| **queue** | `driver=redis` + redis / `driver=database` + 数据库连接 | **sync 同步模式**，任务在当前线程立即执行 |
| **session** | 引入 session-redis **且** `driver=redis` → `RedisSessionStore`（多机同步） | **CookieSessionStore**（Servlet HttpSession） |
| **auth** | 引入 jwt → `jwt` 守卫驱动可用（严格按需） | 仅 `session` 守卫驱动（写了 guards 但未写 driver 时兜底为 session） |
| **artisan** | 引入 artisan → 各模块注册各自命令 | **不注册任何命令**，不影响 HTTP 服务 |
| **migration** | 引入 migration → `migrate` 系列命令可用 | 无连接时仅告警，**不阻断启动** |
| **schedule** | 引入 redis-config → Redis 分布式锁防多机重复执行 | 单机执行，无锁 |
| **model-cache** | 引入 cache → 模型查询缓存生效 | 不缓存，直接查库 |

### 2.6 artisan 的可选性

artisan 在各模块的 pom 中均为 `optional`。模块注册命令的方式是
`@ConditionalOnClass(ArtisanCommand.class)`：

- **引入了 artisan** → 注册该模块的命令
- **没引入 artisan** → 相关 `@Bean` 不装配，模块其余功能照常工作

### 2.7 vendor:publish 的可选依赖处理

`PublishableConfig` 接口定义在 **core** 而非 artisan，因此各模块声明可发布配置
**无需依赖 artisan**。`vendor:publish` 命令通过
`ObjectProvider<PublishableConfig>` 收集，收集不到时提示"没有可发布的配置"而非报错。

---

## 三、artisan 命令与数据库表要求

> **重要**：以下要求**仅在使用对应功能时才需要满足**。
> 不使用 artisan、不使用 migration 也能正常运行框架。

### 3.1 storage 模块

**artisan 命令**：无。

**数据库表要求**：仅在使用 `database` 磁盘驱动时需要。
使用 `local`（默认）时**不需要任何数据库**。

`database` 磁盘采用**分片存储**，需要两张表，表名前缀由 `tablePrefix`
配置决定（默认 `storage_`，即 `storage_file` 与 `storage_file_chunk`）。

> **注意**：这两张表由 `DatabaseFilesystem` 在磁盘首次使用时
> **自动幂等创建**（`CREATE TABLE IF NOT EXISTS`），
> 因此**即使没有 migration 也能直接工作**。

### 3.2 queue-database 模块

**artisan 命令**（需引入 artisan）：`queue:table`

**数据库表要求**：仅在 `driver=database` 时需要。
使用 `sync`（默认回退）或 `redis` 时**不需要数据库**。

队列消费者 `DatabaseQueueWorker` 是**后台 Bean**，
由 `jaravel.queue.worker.enabled` 控制随应用启动，**不是** artisan 命令。

### 3.3 auth 模块

**artisan 命令**：无。**数据库表要求**取决于所用的 `UserProvider` 实现。
使用内存或自定义 Provider 时**不需要数据库**。

### 3.4 cache 模块

**artisan 命令**（需引入 artisan）：`cache:table`

**数据库表要求**：仅在使用 `database` 缓存驱动时需要。
`array`（默认回退）、`file`、`redis` 驱动**均不需要数据库**。

数据源在真正创建驱动时才解析，顺序为「jaravel 连接注册表 → Spring 容器」。

### 3.5 session 模块

**artisan 命令**：无。**数据库表要求**：无。
默认 `CookieSessionStore` 基于 Servlet HttpSession；`session-redis` 基于 Redis。

### 3.6 migration 模块

**artisan 命令**：`migrate` / `migrate:rollback` / `migrate:reset` / `migrate:refresh` / `migrate:status` / `make:model-from-table` / `make:model-from-migration`

**数据库表要求**：迁移记录表 `migrations` 由
`MigrationRepository.createRepository()` **自动创建**，无需手动建表。

### 3.7 完全不使用 artisan / migration 的场景

框架**不强制**使用 artisan 与 migration。若不引入：

- 所有 `@ConditionalOnClass(ArtisanCommand.class)` 的命令 Bean 不装配
- 队列、存储等功能若需要数据库表，需**手动建表**
- 其余功能（路由、认证、缓存、模板、Wire 等）完全不受影响

最小可用组合：仅 `core` + `http` + `springboot`，
缓存走内存、存储走本地文件、队列走同步执行、Session 走 Cookie，
**无需数据库、无需 Redis、无需 artisan**。

---

## 四、快速对照：Laravel → Jaravel

| Laravel | Jaravel |
|---------|---------|
| `config/auth.php` guards | `@RegisterGuard` |
| `config/auth.php` providers | `@RegisterProvider` |
| `config/cache.php` stores | `@RegisterCacheStore` |
| `config/filesystems.php` disks | `@RegisterDisk` |
| `config/queue.php` connections | `@RegisterQueueDriver` |
| `config/database.php` connections | `@RegisterConnection` |
| `Blade::directive()` | `@RegisterDirective` |
| `Blade::if()` | `@RegisterDirective(condition = true)` |
| `php artisan vendor:publish` | `artisan vendor:publish` |
| `php artisan migrate` | `artisan migrate` |
| `php artisan queue:work` | 无对应命令：消费由后台 Bean `DatabaseQueueWorker` 承担 |
