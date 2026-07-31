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
| `@RegisterSessionStore` | auth | `SessionStore` | ❌ **全局唯一** | Session 存储 |
| `@RegisterCacheStore` | cache | `CacheStore` | ✅ 命名多实例 | 缓存 store，`defaultStore = true` 设为默认 |
| `@RegisterDisk` | storage | `DiskDefinition` / `Filesystem` | ✅ 命名多实例 | 文件磁盘，`defaultDisk = true` 设为默认 |
| `@RegisterQueueDriver` | queue-database | `QueueDriver` | ❌ **全局唯一** | 队列驱动 |
| `@RegisterDirective` | jblade | `Handler` / `Condition` | ✅ 命名多实例 | Blade 自定义指令 |

**jwt 模块没有自己的注解**：它提供 `JwtGuardDriver`，通过 auth 的
`@RegisterGuard("api")` + `GuardDefinition.of("jwt", "users")` 接入，
避免同一概念存在两套注册表。

### 1.4 命名多实例 vs 全局唯一

**命名多实例**（guard / provider / cache store / disk / directive）：
一个应用可注册任意多个不同名字的实例，通过名称选用，同名后注册者覆盖先注册者。

```java
@RegisterCacheStore(value = "array", defaultStore = true)
public CacheStore arrayStore() { ... }

@RegisterCacheStore("file")          // 可共存
public CacheStore fileStore() { ... }
```

**全局唯一**（SessionStore / QueueDriver）：这类组件语义上只能有一个
（"登录态存哪里"、"任务推到哪个队列"）。框架**强制唯一性**，
扫描到多个时**启动直接报错**，避免隐式歧义：

```
SessionStore 只允许注册一个，但发现多个：AConfig#a 与 BConfig#b。
如需覆盖，请在其中一个注解上设置 override = true。
```

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
| `AnnotationScanner` | 扫描工具，供需扫描**多种注解**或**控制扫描顺序**的场景使用（如 auth 需先扫 provider 再扫 guard） |
| `RegistrarException` | 注册异常（重复注册、返回类型不匹配、方法调用失败） |

扫描使用 `getMethods()` 而非 `getDeclaredMethods()`，
以正确处理 CGLIB 代理的 `@Configuration` 类与继承自父类的注解方法。

---

## 二、模块依赖与回退策略

### 2.1 总原则

**core 是唯一强依赖**，其余模块之间遵循"**有则使用，无则回退默认**"。
默认实现通常采用**内存方式**（如 queue 的 sync）或**文件方式**（如 storage 的 local）。

实现手段是 Maven `<optional>true</optional>` + Spring 的
`@ConditionalOnClass` / `@ConditionalOnBean` / `@ConditionalOnMissingBean` /
`@ConditionalOnProperty`。

### 2.2 各模块回退矩阵

| 模块 | 有依赖时 | 无依赖时的回退 |
|------|---------|--------------|
| **cache** | `redis`（引入 redis-cache）/ `database`（有 `DataSource`）/ `file` | **array 内存驱动**（进程内，重启丢失） |
| **storage** | `database` 磁盘（有 `DataSource`，自动建表） | **local 文件驱动**，根目录 `storage/app` |
| **queue** | `driver=redis` + redis / `driver=database` + `DataSource` | **sync 同步模式**，任务在当前线程立即执行 |
| **session** | 引入 session-redis → `RedisSessionStore`（多机同步） | **CookieSessionStore**（Servlet HttpSession） |
| **auth** | 引入 jwt → `jwt` 守卫驱动可用 | 仅 `session` 守卫驱动 |
| **artisan** | 引入 artisan → 各模块注册各自命令 | **不注册任何命令**，不影响 HTTP 服务 |
| **migration** | 引入 migration → `migrate` 系列命令可用 | 需**手动建表**（见第四节） |
| **schedule** | 引入 redis-config → Redis 分布式锁防多机重复执行 | 单机执行，无锁 |
| **model-cache** | 引入 cache → 模型查询缓存生效 | 不缓存，直接查库 |

### 2.3 artisan 的可选性

artisan 在各模块的 pom 中均为 `optional`。模块注册命令的方式是
`@ConditionalOnClass(ArtisanCommand.class)`：

- **引入了 artisan** → 注册该模块的命令（如 `queue:work`、`migrate`）
- **没引入 artisan** → 相关 `@Bean` 不装配，模块其余功能照常工作

因此**完全可以不使用 artisan**，只是失去命令行能力。

### 2.4 vendor:publish 的可选依赖处理

`PublishableConfig` 接口定义在 **core** 而非 artisan，因此各模块声明可发布配置
**无需依赖 artisan**。`vendor:publish` 命令通过
`ObjectProvider<PublishableConfig>` 收集，收集不到时提示"没有可发布的配置"而非报错。

---

## 三、artisan vendor:publish 发布配置类

### 3.1 用途

对齐 Laravel `php artisan vendor:publish`，把框架内置的配置类模板
**发布为业务工程中的 Java 源码**，之后用户可自由修改。

### 3.2 用法

```bash
artisan vendor:publish                      # 列出可发布项并提示用法
artisan vendor:publish --list               # 仅列出可发布项
artisan vendor:publish --all                # 发布全部
artisan vendor:publish --tag=cache          # 只发布 cache 模块
artisan vendor:publish --tag=cache --force  # 覆盖已存在文件
```

**默认不覆盖**已存在的文件（避免冲掉用户修改），需显式 `--force`。
未指定 `--all` / `--tag` 时只列清单不写文件。

### 3.3 发布目标目录

发布到 `<outputDir>/<基础包>/config/`，即与 `app/` 包**同级**的 `config/` 包。

复用 `make:*` 系列命令的配置项：

```yaml
jaravel:
  artisan:
    make:
      base-package: com.example.demo    # 基础包名
      output-dir: src/main/java         # 源码根目录
```

上例发布到 `src/main/java/com/example/demo/config/`。

### 3.4 可发布清单

| tag | 生成文件 | 内容 |
|-----|---------|------|
| `auth` | `AuthConfig.java` | `@RegisterGuard` / `@RegisterProvider` / `@RegisterSessionStore` |
| `cache` | `CacheConfig.java` | `@RegisterCacheStore`（array / file） |
| `storage` | `StorageConfig.java` | `@RegisterDisk`（local / public / database 注释示例） |
| `queue` | `QueueConfig.java` | `@RegisterQueueDriver` 示例与回退说明 |
| `view` | `ViewConfig.java` | `@RegisterDirective`（输出指令 / 条件指令） |

### 3.5 为模块新增可发布配置

实现 `core` 的 `PublishableConfig` 并注册为 Bean 即可：

```java
public class MyPublishableConfig implements PublishableConfig {
    public String tag()       { return "mymodule"; }
    public String className() { return "MyModuleConfig"; }
    public String description() { return "我的模块配置"; }
    public String source(String basePackage) {
        return "package " + basePackage + ".config;\n ...";
    }
}
```

---

## 四、artisan 命令与数据库表要求

> **重要**：以下要求**仅在使用对应功能时才需要满足**。
> 不使用 artisan、不使用 migration 也能正常运行框架，只是需要手动完成相应工作。

### 4.1 storage 模块

**artisan 命令**：无。

**数据库表要求**：仅在使用 `database` 磁盘驱动时需要。
使用 `local`（默认）时**不需要任何数据库**。

`database` 磁盘采用**分片存储**，需要两张表，表名前缀由 `tablePrefix`
配置决定（默认 `storage_`，即 `storage_file` 与 `storage_file_chunk`）。

> **注意**：这两张表由 `DatabaseFilesystem` 在磁盘首次使用时
> **自动幂等创建**（`CREATE TABLE IF NOT EXISTS`），
> 因此**即使没有 migration 也能直接工作**，无需手动建表。

`storage_file`（文件元数据）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `disk` | VARCHAR(64) NOT NULL | 磁盘名，联合主键 |
| `path` | VARCHAR(1024) NOT NULL | 文件路径，联合主键 |
| `visibility` | VARCHAR(16) NOT NULL DEFAULT 'private' | `public` / `private` |
| `mime_type` | VARCHAR(255) | MIME 类型 |
| `size` | BIGINT NOT NULL DEFAULT 0 | 字节数 |
| `chunk_count` | INTEGER NOT NULL DEFAULT 0 | 分片数 |
| `created_at` | BIGINT | 创建时间戳 |
| `updated_at` | BIGINT | 更新时间戳 |

主键：`(disk, path)`

`storage_file_chunk`（文件内容分片）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `disk` | VARCHAR(64) NOT NULL | 磁盘名，联合主键 |
| `path` | VARCHAR(1024) NOT NULL | 文件路径，联合主键 |
| `chunk_index` | INTEGER NOT NULL | 分片序号，联合主键 |
| `content` | LONGBLOB / LONGTEXT | 分片内容，列名由 `contentColumn` 决定（默认 `content`）；类型由 `binary` 决定 |
| `size` | INTEGER NOT NULL DEFAULT 0 | 分片字节数 |
| `created_at` | BIGINT | 创建时间戳 |
| `updated_at` | BIGINT | 更新时间戳 |

主键：`(disk, path, chunk_index)`

### 4.2 queue-database 模块

**artisan 命令**（需引入 artisan，否则不注册）：

| 命令 | 说明 |
|------|------|
| `queue:table` | 创建 `jobs` / `failed_jobs` 表（仅 database 驱动需要） |

> **注意**：队列消费者 `DatabaseQueueWorker` 是**后台 Bean**，
> 由 `jaravel.queue.worker.enabled` 控制随应用启动，
> **不是** artisan 命令（无 `queue:work`）。
> 失败任务的重试/清理通过 `QueueDriver` 的 API 完成。

**数据库表要求**：仅在 `driver=database` 时需要。
使用 `sync`（默认回退）或 `redis` 时**不需要数据库**。

`jobs` 表（表名由 `jaravel.queue.database.table` 配置，默认 `jobs`）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT AUTO_INCREMENT PRIMARY KEY | 主键 |
| `queue` | VARCHAR(255) NOT NULL | 队列名，另建索引 `jobs_queue_index` |
| `payload` | TEXT NOT NULL | 任务载荷（JSON） |
| `attempts` | INT NOT NULL DEFAULT 0 | 已尝试次数 |
| `reserved_at` | BIGINT NULL | 保留时间戳，NULL 表示可消费 |
| `available_at` | BIGINT NOT NULL | 可执行时间戳（延迟任务） |
| `created_at` | BIGINT NOT NULL | 创建时间戳 |

`failed_jobs` 表：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT AUTO_INCREMENT PRIMARY KEY | 主键 |
| `queue` | VARCHAR(255) NOT NULL | 队列名，另建索引 |
| `payload` | TEXT NOT NULL | 任务载荷 |
| `exception` | TEXT | 异常堆栈 |
| `attempts` | INT NOT NULL DEFAULT 0 | 尝试次数 |
| `failed_at` | BIGINT NOT NULL | 失败时间戳 |

**有 artisan 时**：`artisan queue:table` 直接建表（内部调用
`DatabaseQueueDriver.createTable()`，不依赖 migration）。
**无 artisan 时**：手动执行上述建表 SQL。

### 4.3 auth 模块

**artisan 命令**：无。

**数据库表要求**：取决于所用的 `UserProvider` 实现。
使用内存或自定义 Provider 时**不需要数据库**。
使用数据库 Provider 时，用户表需包含标识列与密码列（列名可配置）。

### 4.4 cache 模块

**artisan 命令**（需引入 artisan，否则不注册）：

| 命令 | 说明 |
|------|------|
| `cache:table` | 创建缓存表（仅 database 驱动需要） |

**数据库表要求**：仅在使用 `database` 缓存驱动时需要。
`array`（默认回退）、`file`、`redis` 驱动**均不需要数据库**。

缓存表（默认 `jaravel_cache`，表名可配置）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `cache_key` | VARCHAR(255) NOT NULL PRIMARY KEY | 缓存键 |
| `cache_value` | TEXT | 缓存值（JSON 字符串） |
| `expires_at` | BIGINT NOT NULL DEFAULT 0 | 过期时间戳（毫秒），`0` = 永不过期 |

自动适配 MySQL / PostgreSQL / SQLite / H2 / SQL Server 方言。

### 4.5 session 模块

**artisan 命令**：无。

**数据库表要求**：无。默认 `CookieSessionStore` 基于 Servlet HttpSession；
`session-redis` 基于 Redis。**均不需要数据库表**。

### 4.6 migration 模块

**artisan 命令**：

| 命令 | 说明 |
|------|------|
| `migrate {--force}` | 执行待运行迁移 |
| `migrate:rollback {--step=1}` | 回滚最近 N 批 |
| `migrate:reset` | 回滚全部 |
| `migrate:refresh` | 回滚全部后重新执行 |
| `migrate:status` | 查看迁移状态 |
| `make:model-from-table {table}` | 由现有表反向生成 Model |
| `make:model-from-migration {table?} {--all}` | 由迁移文件生成 Model |

> 无 `migrate:fresh` 命令；如需重建请使用 `migrate:refresh`。

**数据库表要求**：迁移记录表 `migrations`，由
`MigrationRepository.createRepository()` **自动创建**，无需手动建表。

| 字段 | 说明 |
|------|------|
| `id` | 自增主键 |
| `migration` | 迁移名称 |
| `batch` | 批次号，用于按批回滚 |

建表 SQL 由 `Dialect.createRepositoryTableSql()` 按方言生成，
支持 MySQL / SQLite / H2 / SQL Server / PostgreSQL / Oracle。

### 4.7 完全不使用 artisan / migration 的场景

框架**不强制**使用 artisan 与 migration。若不引入：

- 所有 `@ConditionalOnClass(ArtisanCommand.class)` 的命令 Bean 不装配
- 队列、存储等功能若需要数据库表，需**手动建表**（表结构见上文）
- 其余功能（路由、认证、缓存、模板、Wire 等）完全不受影响

最小可用组合：仅 `core` + `http` + `springboot`，
缓存走内存、存储走本地文件、队列走同步执行、Session 走 Cookie，
**无需数据库、无需 Redis、无需 artisan**。

---

## 五、快速对照：Laravel → Jaravel

| Laravel | Jaravel |
|---------|---------|
| `config/auth.php` guards | `@RegisterGuard` |
| `config/auth.php` providers | `@RegisterProvider` |
| `config/cache.php` stores | `@RegisterCacheStore` |
| `config/filesystems.php` disks | `@RegisterDisk` |
| `config/queue.php` connections | `@RegisterQueueDriver` |
| `Blade::directive()` | `@RegisterDirective` |
| `Blade::if()` | `@RegisterDirective(condition = true)` |
| `php artisan vendor:publish` | `artisan vendor:publish` |
| `php artisan migrate` | `artisan migrate` |
| `php artisan queue:work` | `artisan queue:work` |
