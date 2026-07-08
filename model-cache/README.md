# model-cache 模块

> Jaravel-Vendor 的模型缓存模块（可选），参考 Laravel `laravel-model-caching` 方案，在 Model 类上通过 `@CachableModel` 注解手动开启查询缓存。采用版本号机制实现缓存失效，基于 `cache` 模块的 `CacheStore` / `CacheManager`。包名统一为 `com.weacsoft.jaravel.vendor.modelcache`。

---

## 目录

- [1. 模块概述](#1-模块概述)
- [2. 设计原理：版本化缓存失效](#2-设计原理版本化缓存失效)
- [3. 依赖信息](#3-依赖信息)
- [4. 类总览](#4-类总览)
- [5. 快速开始](#5-快速开始)
- [6. @CachableModel 注解](#6-cachablemodel-注解)
- [7. ModelCacheService —— 核心服务](#7-modelcacheservice--核心服务)
- [8. ModelCache —— 门面](#8-modelcache--门面)
- [9. 配置选项](#9-配置选项)
- [10. 使用示例](#10-使用示例)
- [11. 与 cache 模块的关系](#11-与-cache-模块的关系)
- [12. 注意事项与局限](#12-注意事项与局限)

---

## 1. 模块概述

`model-cache` 是一个**可选模块**，引入后可在任意 Model 类上手动开启查询缓存，未引入时完全不影响其他模块。核心特性：

| 特性 | 说明 |
| --- | --- |
| 手动开启 | 通过 `@CachableModel` 注解显式标注，未标注的类直接回源，避免无差别缓存 |
| 版本号失效 | 每个模型类维护版本号，失效时递增版本号，旧缓存随 TTL 自然过期 |
| 主键查询缓存 | `find(Class, id, loader)` 缓存按主键查询结果 |
| 任意查询缓存 | `findAll` / `query` 缓存列表或聚合查询结果 |
| 精准失效 | 支持失效整个模型类（版本号递增）或单条记录（forget 单键） |
| 门面调用 | `ModelCache` 静态门面，对齐 Laravel 静态调用风格 |
| 可选 store | 复用 `cache` 模块的 array / file / database / redis 等任意 store |

TTL 单位统一为**秒**（对齐 cache 模块）。

---

## 2. 设计原理：版本化缓存失效

Laravel 的 `laravel-model-caching` 通常依赖 Redis 的 tag 机制实现缓存批量失效。但 Jaravel-Vendor 的 `CacheStore` **不支持 tag**（array / file / database 驱动均无 tag 语义），因此本模块采用**版本号机制**替代 tag：

### 工作流程

1. **版本号键**：每个模型类在缓存中维护一个版本号，键为 `model-cache:{modelPrefix}:version`。
2. **缓存键含版本号**：所有数据缓存键都拼入当前版本号：
   - 主键查询：`model-cache:{modelPrefix}:v{version}:find:{id}`
   - 任意查询：`model-cache:{modelPrefix}:v{version}:query:{queryKey}`
3. **失效时递增版本号**：调用 `invalidate(Class)` 时，`increment` 版本号键。新请求读取到新版本号，生成新的缓存键，旧版本键不再被命中。
4. **旧缓存自然清除**：旧版本缓存键不会被主动删除，而是随自身 TTL 到期后被驱动惰性清理（array 惰性删除、file/database 过期清理）。

### 优势

- 不依赖 tag，兼容 `cache` 模块全部驱动。
- 失效操作为单次 `increment`，开销极低。
- 无需遍历删除旧键，避免缓存抖动。

### 取舍

- 旧版本缓存会占用空间直到 TTL 到期，建议合理设置 TTL。
- 单条记录失效 `invalidate(Class, id)` 仅 forget 主键查询键，不影响查询缓存（查询缓存可能含旧数据，需调用 `invalidate(Class)` 整体失效）。

### 版本号初始化说明

版本号不存在时初始化为 **1** 并写入缓存，确保首次 `invalidate` 调用 `increment` 后版本号变为 2（与初始 1 区分）。若不写入初始值，`increment` 对不存在的键会从 0 起算得到 1，导致版本号未变化、缓存未失效。

---

## 3. 依赖信息

### Maven 坐标

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>model-cache</artifactId>
    <version>0.1.1</version>
</dependency>
```

### 传递依赖

| 依赖 | 是否必需 | 用途 |
| --- | --- | --- |
| `io.github.lijialong1313:cache` | 必需 | `CacheStore` / `CacheManager` 缓存基础设施 |
| `io.github.lijialong1313:core` | 必需 | `Facade` 基础设施（`ModelCache` 门面通过 `Facade.resolve()` 解析 `ModelCacheService`） |
| `io.github.lijialong1313:database` | 可选 | `BaseModel`；仅在业务 Model 继承 `BaseModel` 时需要，`model-cache` 核心不强依赖 |
| `org.springframework.boot:spring-boot-autoconfigure` | 必需 | 自动装配 |
| `com.fasterxml.jackson.core:jackson-databind` | 必需 | 序列化兼容（file/database store 使用） |
| `org.slf4j:slf4j-api` | 必需 | 日志门面 |

> 运行环境要求：JDK 17+，Spring Boot 3.2.5（Spring 6.x）。需同时引入 `cache` 模块并完成其自动装配（容器中存在 `CacheManager` Bean）。

---

## 4. 类总览

```
com.weacsoft.jaravel.vendor.modelcache
├── CachableModel              // 注解：标注在 Model 类上手动开启缓存
├── ModelCacheProperties       // 配置属性（jaravel.model-cache.*）
├── ModelCacheService          // 核心服务（版本化缓存读写/失效，非 @Component）
├── ModelCache                 // 门面（静态 API，对齐 ModelCache::）
└── ModelCacheAutoConfiguration// 自动装配（@Bean 注册 ModelCacheService）
```

---

## 5. 快速开始

### 1) 引入依赖

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>model-cache</artifactId>
    <version>0.1.1</version>
</dependency>
<!-- cache 模块（必需，提供 CacheManager） -->
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>cache</artifactId>
    <version>0.1.1</version>
</dependency>
```

### 2) 在 Model 类上标注注解

```java
import com.weacsoft.jaravel.vendor.modelcache.CachableModel;
import com.weacsoft.jaravel.vendor.database.BaseModel;

@CachableModel(ttl = 600)   // 缓存 600 秒
public class User extends BaseModel<User, Long> {
    // ...
}
```

### 3) 使用门面缓存查询

```java
// 按主键查询（命中缓存直接返回，未命中执行 loader 并回填）
User user = ModelCache.find(User.class, 1L, () -> User.find(1L));

// 缓存列表查询
List<User> all = ModelCache.findAll(User.class, "all", () -> User.all());

// 写入后失效缓存
user.save();
ModelCache.invalidate(User.class);          // 失效整个模型类
// 或仅失效单条：
ModelCache.invalidate(User.class, 1L);
```

---

## 6. @CachableModel 注解

`com.weacsoft.jaravel.vendor.modelcache.CachableModel`

标注在 Model 类上（`@Target(TYPE)`，`@Retention(RUNTIME)`），手动开启查询缓存。未标注的类调用 `ModelCache` 方法时直接回源，不产生任何缓存。

| 属性 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `ttl` | `long` | `-1` | 缓存 TTL（秒）。`-1` 表示使用全局默认值 `jaravel.model-cache.default-ttl`；`0` 表示永不过期（仅靠版本号失效）；正数为有效秒数 |
| `prefix` | `String` | `""` | 自定义缓存键前缀（拼在全局 `keyPrefix` 之后，类名位置）。为空时使用类名（`Class.getSimpleName()`） |

### 缓存键结构

```
{keyPrefix}{modelPrefix}:v{version}:find:{id}      # 主键查询
{keyPrefix}{modelPrefix}:v{version}:query:{queryKey}  # 任意查询
{keyPrefix}{modelPrefix}:version                   # 版本号
```

- `keyPrefix`：全局前缀，默认 `model-cache:`（见 `jaravel.model-cache.key-prefix`）
- `modelPrefix`：`@CachableModel.prefix()` 非空时用之，否则用类名
- `version`：当前版本号，初始为 1

### 示例

```java
// 使用默认 ttl（全局 default-ttl）与类名作为前缀
@CachableModel
public class User extends BaseModel<User, Long> { ... }
// 键：model-cache:User:v1:find:1

// 自定义 ttl 与前缀
@CachableModel(ttl = 120, prefix = "usr")
public class User extends BaseModel<User, Long> { ... }
// 键：model-cache:usr:v1:find:1
```

---

## 7. ModelCacheService —— 核心服务

`com.weacsoft.jaravel.vendor.modelcache.ModelCacheService`

核心服务类，注入 `CacheManager` 与 `ModelCacheProperties`，提供缓存读写与失效能力。由 `ModelCacheAutoConfiguration` 以 `@Bean` 方式注册（非 `@Component`）。

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `<T, K> T find(Class<T> modelClass, K id, Supplier<T> loader)` | 缓存按主键查询。未标注注解或全局关闭时直接回源；loader 返回 `null` 时不缓存 |
| `<T> List<T> findAll(Class<T> modelClass, String queryKey, Supplier<List<T>> loader)` | 缓存列表查询 |
| `<T> Object query(Class<T> modelClass, String queryKey, Supplier<Object> loader)` | 缓存任意查询（如聚合 count） |
| `<T> void invalidate(Class<T> modelClass)` | 失效模型类的所有缓存（递增版本号） |
| `<T, K> void invalidate(Class<T> modelClass, K id)` | 失效单条记录的主键查询缓存（forget 单键） |
| `<T> long getVersion(Class<T> modelClass)` | 获取当前版本号，不存在时初始化为 1 |
| `<T> long getTtl(Class<T> modelClass)` | 获取模型 TTL（优先注解，-1/负数用全局默认） |
| `boolean isCachable(Class<?> modelClass)` | 判断是否可缓存（全局开关开启 + 标注注解） |

### remember 语义与 null 处理

`find` / `findAll` / `query` 内部采用 remember 语义（命中返回、未命中加载并回填），但对 **loader 返回 `null` 的结果不回填**，避免缓存未命中结果（如 `find` 未找到记录）占用空间。这意味着未找到的记录每次都会回源，是预期的“不缓存 null”行为。

---

## 8. ModelCache —— 门面

`com.weacsoft.jaravel.vendor.modelcache.ModelCache`

静态门面，`final` 工具类，不可实例化。通过 `Facade.resolve(ModelCacheService.class)` 从 Spring 容器解析 `ModelCacheService`，委托其全部公共方法。便于业务代码以 `ModelCache::find(...)` 风格调用。

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `static <T, K> T find(Class<T> modelClass, K id, Supplier<T> loader)` | 缓存按主键查询 |
| `static <T> List<T> findAll(Class<T> modelClass, String queryKey, Supplier<List<T>> loader)` | 缓存列表查询 |
| `static <T> Object query(Class<T> modelClass, String queryKey, Supplier<Object> loader)` | 缓存任意查询 |
| `static <T> void invalidate(Class<T> modelClass)` | 失效整个模型类 |
| `static <T, K> void invalidate(Class<T> modelClass, K id)` | 失效单条记录 |
| `static <T> long getVersion(Class<T> modelClass)` | 获取当前版本号 |
| `static <T> long getTtl(Class<T> modelClass)` | 获取模型 TTL |
| `static boolean isCachable(Class<?> modelClass)` | 判断是否可缓存 |

---

## 9. 配置选项

配置前缀为 `jaravel.model-cache`，对应 `ModelCacheProperties` 类。

```yaml
jaravel:
  model-cache:
    enabled: true              # 全局开关
    store: array               # 缓存 store 名称
    default-ttl: 3600          # 默认缓存 TTL（秒）
    key-prefix: "model-cache:" # 缓存键前缀
```

| 属性 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `jaravel.model-cache.enabled` | `boolean` | `true` | 全局开关，关闭后所有模型缓存不生效（直接回源） |
| `jaravel.model-cache.store` | `String` | `array` | 缓存 store 名称，需在 `CacheManager` 中已注册（array / file / database / redis） |
| `jaravel.model-cache.default-ttl` | `long` | `3600` | 默认缓存 TTL（秒），`@CachableModel` 未指定或为 -1 时使用 |
| `jaravel.model-cache.key-prefix` | `String` | `model-cache:` | 缓存键前缀 |

### 使用 redis store 示例

```yaml
jaravel:
  model-cache:
    store: redis        # 需引入 redis-cache 模块并注册 redis store
    default-ttl: 1800
```

---

## 10. 使用示例

### 结合 BaseModel 的完整示例

```java
import com.weacsoft.jaravel.vendor.database.BaseModel;
import com.weacsoft.jaravel.vendor.modelcache.CachableModel;
import com.weacsoft.jaravel.vendor.modelcache.ModelCache;
import org.springframework.stereotype.Repository;

@Repository
@CachableModel(ttl = 600)     // 开启缓存，TTL 600 秒
public class User extends BaseModel<User, Long> {

    public static User find(Long id) {
        // 委托给 ModelCache：命中返回缓存，未命中执行 loader（BaseModel.find）并回填
        return ModelCache.find(User.class, id, () -> BaseModel.find(User.class, id));
    }

    public static List<User> all() {
        return ModelCache.findAll(User.class, "all", () -> BaseModel.all(User.class));
    }

    // 条件查询：用条件字符串作为 queryKey
    public static User findByName(String name) {
        String queryKey = "name:" + name;
        return (User) ModelCache.query(User.class, queryKey,
                () -> BaseModel.query(User.class).where("name", name).first().toObject());
    }
}
```

### 失效缓存

```java
// 新增/更新/删除后，失效整个模型类的缓存（推荐）
User user = new User();
user.setName("alice");
user.save();
ModelCache.invalidate(User.class);

// 或仅失效单条主键查询缓存（不影响查询缓存）
ModelCache.invalidate(User.class, 1L);
```

### 不缓存 null

```java
// find 未找到记录时 loader 返回 null，不会写入缓存
User missing = ModelCache.find(User.class, 999L, () -> User.find(999L));
// 后续再次查询 999L 仍会回源（未缓存 null）
```

### 直接注入服务

```java
@Autowired
private ModelCacheService modelCacheService;

long version = modelCacheService.getVersion(User.class);
boolean cachable = modelCacheService.isCachable(User.class);
```

---

## 11. 与 cache 模块的关系

`model-cache` 是构建在 `cache` 模块之上的**高层封装**，二者关系如下：

```
ModelCache（门面，静态 API）
  └── ModelCacheService（版本号 + 注解驱动的模型缓存语义）
        └── CacheManager（cache 模块，多 store 管理）
              └── CacheStore（cache 模块，remember/put/get/forget/increment...）
                    └── CacheDriver（cache 模块，array/file/database/redis...）
```

| 维度 | cache 模块 | model-cache 模块 |
| --- | --- | --- |
| 定位 | 通用缓存基础设施 | 面向 Model 的查询缓存封装 |
| 调用粒度 | 任意 key-value | 以模型类 + 主键/查询标识为粒度 |
| 失效方式 | `forget` 单键 / `flush` 全部 | 版本号递增失效整个模型类 / `forget` 单条 |
| 开启方式 | 直接使用 | `@CachableModel` 注解手动开启 |
| 依赖关系 | 基础 | 依赖 cache 模块 |

`model-cache` 复用 `cache` 模块的 `CacheStore.remember` / `put` / `get` / `forget` / `increment` 等原语，不重新实现存储。`store` 配置项直接指向 `CacheManager` 中已注册的 store 名称，因此切换 array / file / database / redis 只需改一行配置。

---

## 12. 注意事项与局限

1. **可选模块**：不引入 `model-cache` 依赖时，自动装配不会被加载，不影响其他模块。
2. **需 cache 模块就绪**：自动装配带 `@ConditionalOnBean(CacheManager.class)`，仅当容器中存在 `CacheManager` Bean 时生效。
3. **store 序列化局限**：使用 `file` / `database` store 时，缓存值经 JSON 序列化，复杂对象会还原为 `LinkedHashMap` / `ArrayList`（cache 模块固有特性）。默认 `array` store 存储对象引用，无此问题，推荐用于模型缓存。
4. **不缓存 null**：`find` 等方法对 loader 返回 `null` 的结果不回填，未找到的记录每次回源。如需防止缓存穿透，可在 loader 中返回哨兵对象或使用短 TTL。
5. **版本号并发**：`increment` 为非原子 get-then-put（cache 模块实现），高并发失效时版本号可能跳跃，但不影响正确性（跳跃仍使旧缓存失效）。
6. **查询缓存一致性**：`invalidate(Class, id)` 仅失效主键查询键，查询缓存（`findAll`/`query`）可能含旧数据。需整体失效时调用 `invalidate(Class)`。
