# cache 模块

> Jaravel-Vendor 的缓存模块，提供 Laravel 风格的 `Cache` 门面、`CacheStore` 仓库抽象、`ArrayCacheDriver`（内存）、`FileCacheDriver`（文件）、`DatabaseCacheDriver`（数据库）三种驱动，以及 `CacheManager` 多仓库管理。redis 驱动位于独立的 `redis-cache` 模块。包名统一为 `com.weacsoft.jaravel.vendor.cache`。

---

## 目录

- [1. 模块概述](#1-模块概述)
- [2. 依赖信息](#2-依赖信息)
- [3. 类总览](#3-类总览)
- [4. Cache —— 缓存门面](#4-cache--缓存门面)
- [5. CacheManager —— 缓存管理器](#5-cachemanager--缓存管理器)
- [6. CacheStore —— 高级缓存操作契约](#6-cachestore--高级缓存操作契约)
- [7. DefaultCacheStore —— 默认缓存仓库实现](#7-defaultcachestore--默认缓存仓库实现)
- [8. CacheDriver —— 缓存驱动契约](#8-cachedriver--缓存驱动契约)
- [9. ArrayCacheDriver —— 内存缓存驱动](#9-arraycachedriver--内存缓存驱动)
- [10. FileCacheDriver —— 文件缓存驱动](#10-filecachedriver--文件缓存驱动)
- [11. DatabaseCacheDriver —— 数据库缓存驱动](#11-databasecachedriver--数据库缓存驱动)
- [12. CacheAutoConfiguration —— 自动装配](#12-cacheautoconfiguration--自动装配)
- [13. 配置选项](#13-配置选项)
- [14. 线程安全说明](#14-线程安全说明)

---

## 1. 模块概述

`cache` 模块对齐 Laravel 的缓存体系，核心特性如下：

| Laravel 特性 | cache 对应实现 | 说明 |
| --- | --- | --- |
| `Cache::` 门面 | `Cache` | 静态代理 `CacheManager`，委托给默认 store |
| `Illuminate\Cache\CacheManager` | `CacheManager` | 多命名 store 管理，按名称解析 |
| `Illuminate\Cache\Repository` | `CacheStore` / `DefaultCacheStore` | 高级缓存操作（put/get/has/forget/remember 等） |
| `Illuminate\Contracts\Cache\Store` | `CacheDriver` | 底层 CRUD 契约（与存储介质交互） |
| `"array"` 驱动 | `ArrayCacheDriver` | 基于 `ConcurrentHashMap` 的内存缓存 |
| `"file"` 驱动 | `FileCacheDriver` | 基于文件系统的缓存（Jackson 序列化） |
| `"database"` 驱动 | `DatabaseCacheDriver` | 基于关系型数据库的缓存（JdbcTemplate + 自动建表） |
| `config/cache.php` | `CacheProperties` | `jaravel.cache.*` 配置 |

### 架构分层

```
Cache（门面，静态 API）
  └── CacheManager（多 store 管理）
        └── CacheStore / DefaultCacheStore（高级语义：remember/increment/add...）
              └── CacheDriver（底层 CRUD：put/get/exists/remove...）
                    ├── ArrayCacheDriver（内存）
                    ├── FileCacheDriver（文件）
                    └── DatabaseCacheDriver（数据库）
```

TTL 单位统一为**秒**（对齐 Laravel），`ttl <= 0` 表示永不过期。

---

## 2. 依赖信息

### Maven 坐标

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>cache</artifactId>
    <version>0.1.0</version>
</dependency>
```

### 传递依赖

| 依赖 | 用途 |
| --- | --- |
| `com.weacsoft:core` | `Facade` 基础设施（`Cache` 门面通过 `Facade.resolve()` 解析 `CacheManager`） |
| `org.springframework.boot:spring-boot-autoconfigure` | 自动装配 |
| `com.fasterxml.jackson.core:jackson-databind` | `FileCacheDriver` / `DatabaseCacheDriver` 的 JSON 序列化 |
| `org.springframework:spring-jdbc`（optional） | `DatabaseCacheDriver` 的数据库操作（`JdbcTemplate`）；仅使用 array/file 驱动时无需引入 |
| `org.slf4j:slf4j-api` | 日志门面 |

> 运行环境要求：JDK 17+，Spring Boot 3.2.5（Spring 6.x）。
>
> **使用 database 驱动**时，应用需自行引入数据源与 `spring-jdbc`（如 `spring-boot-starter-jdbc` 或 `spring-boot-starter-data-jpa`），并配置 `DataSource`。`DatabaseCacheDriver` 仅在容器中存在 `DataSource` bean 时自动装配。

---

## 3. 类总览

```
com.weacsoft.jaravel.vendor.cache
├── Cache                        // 缓存门面（静态 API，对齐 Cache::）
├── CacheManager                 // 缓存管理器（多 store 管理，非 @Component）
├── CacheStore                   // 高级缓存操作契约（接口）
├── DefaultCacheStore            // 默认缓存仓库实现（带前缀，委托 CacheDriver）
├── CacheDriver                  // 缓存驱动契约（底层 CRUD 接口）
├── ArrayCacheDriver             // 内存缓存驱动（ConcurrentHashMap + TTL）
├── FileCacheDriver              // 文件缓存驱动（Jackson JSON 序列化）
├── DatabaseCacheDriver          // 数据库缓存驱动（JdbcTemplate + 自动建表）
├── CacheProperties              // 配置属性（jaravel.cache.*）
└── CacheAutoConfiguration       // 自动装配（@Bean 注册驱动与管理器）
```

---

## 4. Cache —— 缓存门面

`com.weacsoft.jaravel.vendor.cache.Cache`

对齐 Laravel `Cache::` 静态调用。`final` 工具类，不可实例化。通过 `Facade.resolve(CacheManager.class)` 从 Spring 容器解析 `CacheManager`，所有方法委托给默认 store。

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `static boolean put(String key, Object value, long ttl)` | 写入缓存，指定过期秒数（`<= 0` 永不过期） |
| `static boolean put(String key, Object value)` | 永久写入缓存 |
| `static Object get(String key)` | 读取原始缓存值，不存在返回 `null` |
| `static <T> T get(String key, Class<T> type)` | 读取并按指定类型转换 |
| `static boolean has(String key)` | 判断缓存键是否存在 |
| `static boolean forget(String key)` | 移除指定缓存键 |
| `static void flush()` | 清空当前 store 下所有缓存 |
| `static Object pull(String key)` | 读取后立即删除 |
| `static boolean add(String key, Object value, long ttl)` | 仅当键不存在时写入，返回是否实际写入 |
| `static long increment(String key)` | 自增 1 |
| `static long increment(String key, long amount)` | 自增指定步长 |
| `static long decrement(String key)` | 自减 1 |
| `static long decrement(String key, long amount)` | 自减指定步长 |
| `static Object remember(String key, long ttl, Supplier<Object> loader)` | 读取或回填（命中返回，未命中则加载并写入） |
| `static Object rememberForever(String key, Supplier<Object> loader)` | 永久读取或回填 |
| `static CacheStore store()` | 返回默认 store |
| `static CacheStore store(String name)` | 按名称返回指定 store |

### 使用示例

```java
// 基本读写
Cache.put("user:1", user, 60);              // 60 秒后过期
Object v = Cache.get("user:1");
User u = Cache.get("user:1", User.class);   // 类型化读取

// 判断与删除
if (Cache.has("user:1")) {
    Cache.forget("user:1");
}

// 读取后删除
Object token = Cache.pull("otp:12345");

// 仅当不存在时写入
boolean written = Cache.add("lock:job", "running", 30);

// 自增 / 自减
long hits = Cache.increment("hits");
long hits2 = Cache.increment("hits", 5);
long remaining = Cache.decrement("stock:100", 1);

// 读取或回填
Object config = Cache.remember("app:config", 300, () -> loadConfigFromDb());
Object forever = Cache.rememberForever("app:version", () -> "0.1.0");

// 清空
Cache.flush();

// 指定 store
Cache.store("file").put("report", reportData, 3600);
```

---

## 5. CacheManager —— 缓存管理器

`com.weacsoft.jaravel.vendor.cache.CacheManager`

对齐 Laravel `Illuminate\Cache\CacheManager`。维护多个命名 `CacheStore`，按名称解析 store 并提供默认 store。

> **注意**：本类**不使用 `@Component`** 注解，而是由 `CacheAutoConfiguration` 以 `@Bean @ConditionalOnMissingBean` 方式注册。这是为了避免组件扫描创建空实例（构造时需注入各 store）。

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `CacheStore store()` | 返回默认 store |
| `CacheStore store(String name)` | 按名称返回指定 store，未注册则抛 `IllegalStateException` |
| `void addStore(String name, CacheStore store)` | 注册一个命名 store |
| `void setDefaultStore(String name)` | 设置默认 store 名称 |
| `String getDefaultStore()` | 获取默认 store 名称 |

### 使用示例

```java
// CacheManager 由自动装配注册，可直接注入
@Autowired
private CacheManager cacheManager;

// 获取默认 store
CacheStore store = cacheManager.store();

// 按名称获取 store
CacheStore fileStore = cacheManager.store("file");

// 注册自定义 store
cacheManager.addStore("redis", new DefaultCacheStore(redisDriver, "jaravel"));
cacheManager.setDefaultStore("redis");
```

---

## 6. CacheStore —— 高级缓存操作契约

`com.weacsoft.jaravel.vendor.cache.CacheStore`

对齐 Laravel `Illuminate\Cache\Repository`（即 `Cache::` 背后的仓库）。在底层 `CacheDriver` 之上提供高级语义。TTL 单位统一为**秒**。

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `boolean put(String key, Object value, long ttlSeconds)` | 写入缓存，指定过期秒数 |
| `boolean put(String key, Object value)` | 永久写入缓存 |
| `Object get(String key)` | 读取原始缓存值，不存在返回 `null` |
| `<T> T get(String key, Class<T> type)` | 读取并按指定类型转换 |
| `boolean has(String key)` | 判断缓存键是否存在 |
| `boolean forget(String key)` | 移除指定缓存键 |
| `void flush()` | 清空当前 store 下所有缓存 |
| `Object pull(String key)` | 读取后立即删除 |
| `boolean add(String key, Object value, long ttlSeconds)` | 仅当键不存在时写入 |
| `long increment(String key)` | 自增 1 |
| `long increment(String key, long amount)` | 自增指定步长 |
| `long decrement(String key)` | 自减 1 |
| `long decrement(String key, long amount)` | 自减指定步长 |
| `void putMany(Map<String, Object> values, long ttlSeconds)` | 批量写入 |
| `Map<String, Object> getMany(Collection<String> keys)` | 批量读取，缺失键对应 null |
| `Object remember(String key, long ttlSeconds, Supplier<Object> loader)` | 读取或回填 |
| `Object rememberForever(String key, Supplier<Object> loader)` | 永久读取或回填 |

---

## 7. DefaultCacheStore —— 默认缓存仓库实现

`com.weacsoft.jaravel.vendor.cache.DefaultCacheStore`

对齐 Laravel `Illuminate\Cache\Repository`。委托给底层 `CacheDriver`，所有 key 操作前自动前置 `prefix + ":"`，用于隔离不同模块/应用的缓存命名空间。

### 构造器

| 构造器签名 | 说明 |
| --- | --- |
| `DefaultCacheStore(CacheDriver driver, String prefix)` | `driver` 为底层缓存驱动，`prefix` 为键前缀（`null` 视为无前缀） |

### 类型转换逻辑

`get(String key, Class<T> type)` 方法的转换规则：

1. 值为 `null` -> 返回 `null`
2. 值已是 `type` 实例 -> 直接返回
3. 值为 `Number` -> 按目标类型（Integer/Long/Double/Float/Short/Byte）转换
4. 目标类型为 `String` -> 调用 `toString()`
5. 目标类型为 `Boolean` 且值为 `Boolean` -> 直接返回
6. 兜底：尝试以 `String` 构造器反射创建（如 `new URI(value.toString())`），失败抛 `ClassCastException`

### increment / decrement 说明

采用 get-then-put 实现（非原子，但简单直观）。当键不存在或值非数字时按 0 起算。

### remember / rememberForever 说明

实现"命中即返回、未命中则加载并回填"的常规模式：

```java
// remember 内部逻辑
Object value = get(key);
if (value != null) {
    return value;       // 命中
}
value = loader.get();   // 未命中，调用 loader
put(key, value, ttl);   // 回填
return value;
```

### 使用示例

```java
CacheStore store = new DefaultCacheStore(new ArrayCacheDriver(), "myapp");

store.put("name", "Alice", 60);
String name = store.get("name", String.class);   // "Alice"

// 批量操作
store.putMany(Map.of("k1", "v1", "k2", "v2"), 120);
Map<String, Object> vals = store.getMany(List.of("k1", "k2", "k3"));
// {"k1": "v1", "k2": "v2", "k3": null}

// remember 模式
Object data = store.remember("expensive:query", 300, () -> {
    return db.query("SELECT ...");  // 仅在未命中时执行
});
```

---

## 8. CacheDriver —— 缓存驱动契约

`com.weacsoft.jaravel.vendor.cache.CacheDriver`

对齐 Laravel 底层 `Illuminate\Contracts\Cache\Store`。这是与具体存储介质（内存/文件/Redis 等）交互的最底层 CRUD 契约。TTL 单位统一为**秒**。

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `boolean put(String key, Object value, long ttlSeconds)` | 写入缓存，`ttlSeconds <= 0` 表示永不过期 |
| `Object get(String key)` | 读取缓存，不存在或已过期返回 `null` |
| `boolean exists(String key)` | 判断缓存键是否存在（未过期） |
| `boolean remove(String key)` | 移除指定缓存键，返回是否确实移除了条目 |
| `void removeAll()` | 清空当前驱动下的所有缓存 |
| `Collection<String> allKeys()` | 返回当前驱动下所有未过期的缓存键 |

### 自定义驱动示例

```java
public class RedisCacheDriver implements CacheDriver {
    private final Jedis jedis;

    public RedisCacheDriver(Jedis jedis) {
        this.jedis = jedis;
    }

    @Override
    public boolean put(String key, Object value, long ttlSeconds) {
        String json = toJson(value);
        if (ttlSeconds > 0) {
            jedis.setex(key, ttlSeconds, json);
        } else {
            jedis.set(key, json);
        }
        return true;
    }

    @Override
    public Object get(String key) {
        String json = jedis.get(key);
        return json != null ? fromJson(json) : null;
    }

    // ... 其它方法实现
}
```

---

## 9. ArrayCacheDriver —— 内存缓存驱动

`com.weacsoft.jaravel.vendor.cache.ArrayCacheDriver`

对齐 Laravel `"array"` 缓存驱动。基于 `ConcurrentHashMap` 的内存缓存，线程安全，仅在当前 JVM 进程内有效，进程重启即失。常用于单元测试与本地开发。

### TTL 机制

- `expiryAt = now + ttlSeconds * 1000`（毫秒时间戳）
- `expiryAt == 0` 表示永不过期
- 读取 / 存在性判断时会**惰性清理**过期条目

### 方法文档

继承 `CacheDriver` 接口全部方法。内部使用 `CacheEntry`（含 `value` 与 `expiryAt`）存储条目。

| 方法 | 行为说明 |
| --- | --- |
| `put(key, value, ttl)` | 计算过期时间，存入 `ConcurrentHashMap` |
| `get(key)` | 读取条目，若已过期则惰性删除并返回 `null` |
| `exists(key)` | 判断存在，若已过期则惰性删除并返回 `false` |
| `remove(key)` | 从 Map 中移除 |
| `removeAll()` | 清空 Map |
| `allKeys()` | 惰性清理所有过期条目，返回未过期键集合 |

### 使用示例

```java
ArrayCacheDriver driver = new ArrayCacheDriver();

driver.put("token", "abc123", 30);   // 30 秒后过期
driver.put("config", cfg);           // 永不过期

Object token = driver.get("token");  // "abc123"
// 等待 30 秒后...
driver.get("token");                 // null（已过期，惰性清理）

driver.allKeys();                    // ["config"]
```

---

## 10. FileCacheDriver —— 文件缓存驱动

`com.weacsoft.jaravel.vendor.cache.FileCacheDriver`

对齐 Laravel `"file"` 缓存驱动。每个 key 对应目录下一个文件，使用 Jackson `ObjectMapper` 将 `CacheEntry`（含 `value` 与 `expiryAt`）序列化为 JSON 写入文件；读取时反序列化，过期则删除文件并视为未命中。

### 构造器

| 构造器签名 | 说明 |
| --- | --- |
| `FileCacheDriver()` | 默认目录：`${java.io.tmpdir}/jaravel-cache` |
| `FileCacheDriver(String dir)` | 指定目录，`null` 或空串则回退到默认临时目录 |
| `FileCacheDriver(File dir)` | 指定目录，不存在则自动创建 |

### 文件命名机制

缓存键通过 UTF-8 十六进制编码（`HexFormat`）作为文件名，保证可逆且文件系统安全。因此 `allKeys()` 能还原出原始键。

- 文件后缀：`.cache`
- 编码示例：`"user:1"` -> `757365723a31.cache`

### TTL 机制

- `expiryAt = System.currentTimeMillis() + ttlSeconds * 1000`（毫秒时间戳）
- `ttlSeconds <= 0` 时 `expiryAt = 0`（永不过期）
- 读取 / 存在性判断时若已过期，则删除文件并视为未命中

> 已修复旧版 TTL 单位混乱的 bug（旧实现曾把秒当毫秒或重复乘 1000，现统一为秒）。

### CacheEntry 内部结构

```java
public static class CacheEntry {
    private Object value;     // 缓存值
    private long expiryAt;    // 过期时间戳（毫秒），0 表示永不过期

    // Jackson 反序列化需要的默认构造器
    public CacheEntry() {}
    public CacheEntry(Object value, long expiryAt) { ... }

    // getter / setter
}
```

> 注意：由于 `value` 为 `Object`，Jackson 反序列化时复杂对象会还原为 `LinkedHashMap` / `ArrayList` 等基础类型，这是 JSON 文件缓存的固有特性。

### 使用示例

```java
// 使用默认临时目录
FileCacheDriver driver1 = new FileCacheDriver();

// 指定目录
FileCacheDriver driver2 = new FileCacheDriver("/var/cache/myapp");

driver2.put("user:1", Map.of("name", "Alice", "age", 30), 3600);

Object value = driver2.get("user:1");
// value 为 LinkedHashMap: {name=Alice, age=30}

driver2.exists("user:1");   // true
driver2.remove("user:1");   // true
driver2.allKeys();           // 返回所有未过期键
driver2.removeAll();         // 清空所有 .cache 文件
```

---

## 11. DatabaseCacheDriver —— 数据库缓存驱动

`com.weacsoft.jaravel.vendor.cache.DatabaseCacheDriver`

对齐 Laravel `"database"` 缓存驱动。基于 Spring `JdbcTemplate` 将缓存条目持久化到 `jaravel_cache` 表，缓存值以 JSON 字符串存储。构造时自动建表（若不存在），自动适配 MySQL / PostgreSQL / SQLite / H2 / SQL Server 方言（建表与 upsert 语义）。适合需要跨进程共享、又不想引入 Redis 的场景。

### 构造器

| 构造器签名 | 说明 |
| --- | --- |
| `DatabaseCacheDriver(DataSource dataSource)` | 使用默认表名 `jaravel_cache` |
| `DatabaseCacheDriver(DataSource dataSource, String table)` | 指定表名，`null` 或空串回退到默认表名 |

### 表结构

```sql
CREATE TABLE jaravel_cache (
  cache_key   VARCHAR(255) NOT NULL PRIMARY KEY,   -- 缓存键
  cache_value TEXT,                                -- 缓存值（JSON 字符串）
  expires_at  BIGINT NOT NULL DEFAULT 0            -- 过期时间戳（毫秒），0=永不过期
);
```

> 表名可通过 `jaravel.cache.database-table` 配置。不同方言下大文本类型会自动适配（如 H2 使用 `CLOB`、SQL Server 使用 `NVARCHAR(MAX)`）。

### TTL 机制

- `expires_at = System.currentTimeMillis() + ttlSeconds * 1000`（毫秒时间戳）
- `ttlSeconds <= 0` 时 `expires_at = 0`（永不过期）
- 读取 / 存在性判断时若命中已过期记录，返回未命中并通过后台守护线程**异步删除**该记录，避免阻塞读路径
- `allKeys()` 会顺带批量清理已过期记录

### upsert 语义

`put` 采用各方言的 upsert 语义（MERGE / REPLACE），保证同 key 写入为插入或更新：

| 方言 | upsert 语法 |
| --- | --- |
| MySQL | `INSERT ... ON DUPLICATE KEY UPDATE` |
| PostgreSQL / SQLite | `INSERT ... ON CONFLICT(cache_key) DO UPDATE` |
| H2 | `MERGE INTO ... KEY(cache_key) VALUES (...)` |
| SQL Server | `MERGE ... USING ...` |

### 方法文档

继承 `CacheDriver` 接口全部方法。

| 方法 | 行为说明 |
| --- | --- |
| `put(key, value, ttl)` | 序列化为 JSON，upsert 写入并设置过期时间 |
| `get(key)` | 查询记录，若已过期则异步删除并返回 `null` |
| `exists(key)` | 查询记录是否存在，若已过期则异步删除并返回 `false` |
| `remove(key)` | DELETE 单条，返回是否删除 |
| `removeAll()` | DELETE 全部 |
| `allKeys()` | 批量清理过期记录，返回所有未过期键 |

### 使用示例

```java
// 通常由自动装配创建，无需手动 new
DatabaseCacheDriver driver = new DatabaseCacheDriver(dataSource, "jaravel_cache");

driver.put("user:1", Map.of("name", "Alice", "age", 30), 3600);   // 1 小时后过期
driver.put("config", cfg);                                         // 永不过期

Object value = driver.get("user:1");
// value 为 LinkedHashMap: {name=Alice, age=30}

driver.exists("user:1");   // true
driver.remove("user:1");   // true
driver.allKeys();          // 返回所有未过期键
driver.removeAll();        // 清空整张表
```

> 注意：由于 `cache_value` 以 JSON 存储，`Object` 反序列化时复杂对象会还原为 `LinkedHashMap` / `ArrayList` 等基础类型，这是 JSON 缓存的固有特性。

---

## 12. CacheAutoConfiguration —— 自动装配

`com.weacsoft.jaravel.vendor.cache.CacheAutoConfiguration`

Spring Boot 自动装配类，对齐 Laravel 缓存服务提供者。注册以下 Bean：

| Bean | 类型 | 说明 |
| --- | --- | --- |
| `arrayCacheDriver` | `ArrayCacheDriver` | 内存缓存驱动 |
| `fileCacheDriver` | `FileCacheDriver` | 文件缓存驱动，目录取自 `CacheProperties.getFileDir()` |
| `databaseCacheDriver` | `DatabaseCacheDriver` | 数据库缓存驱动，表名取自 `CacheProperties.getDatabaseTable()`；仅当 classpath 存在 `JdbcTemplate` 且容器中存在 `DataSource` bean 时装配 |
| `databaseCacheStore` | `DefaultCacheStore` | database store，创建时注册到 `CacheManager`（仅当 `DatabaseCacheDriver` 存在时） |
| `cacheManager` | `CacheManager` | 缓存管理器，注册 `array` / `file` 两个 store 并设置默认 store；`database` store 在 `DatabaseCacheDriver` 存在时由内部配置类追加注册 |

### 装配逻辑

```java
@Bean
public CacheManager cacheManager(CacheProperties properties,
                                 ArrayCacheDriver arrayCacheDriver,
                                 FileCacheDriver fileCacheDriver) {
    CacheManager manager = new CacheManager();
    manager.addStore("array", new DefaultCacheStore(arrayCacheDriver, properties.getPrefix()));
    manager.addStore("file", new DefaultCacheStore(fileCacheDriver, properties.getPrefix()));
    manager.setDefaultStore(properties.getDefaultStore());
    return manager;
}
```

`DatabaseCacheDriver` 依赖可选的 `spring-jdbc`，因此独立到内部 `DatabaseCacheConfiguration` 装配（`@ConditionalOnClass({DataSource.class, JdbcTemplate.class})` + `@ConditionalOnBean(DataSource.class)`），避免未引入 `spring-jdbc` 的应用加载该类时抛出 `NoClassDefFoundError`。当 `DatabaseCacheDriver` bean 存在时，`database` store 会被注册到 `CacheManager`：

```java
// 内部 DatabaseCacheConfiguration
@Bean
public DatabaseCacheDriver databaseCacheDriver(DataSource dataSource, CacheProperties properties) {
    return new DatabaseCacheDriver(dataSource, properties.getDatabaseTable());
}

@Bean
public DefaultCacheStore databaseCacheStore(CacheManager cacheManager,
                                            DatabaseCacheDriver databaseCacheDriver,
                                            CacheProperties properties) {
    DefaultCacheStore store = new DefaultCacheStore(databaseCacheDriver, properties.getPrefix());
    cacheManager.addStore("database", store);
    return store;
}
```

所有 Bean 均带 `@ConditionalOnMissingBean`，便于业务方覆盖。例如可自定义 `CacheManager` 注册 Redis store：

```java
@Bean
public CacheManager cacheManager(CacheProperties properties,
                                 ArrayCacheDriver arrayCacheDriver,
                                 FileCacheDriver fileCacheDriver,
                                 RedisCacheDriver redisDriver) {
    CacheManager manager = new CacheManager();
    manager.addStore("array", new DefaultCacheStore(arrayCacheDriver, properties.getPrefix()));
    manager.addStore("file", new DefaultCacheStore(fileCacheDriver, properties.getPrefix()));
    manager.addStore("redis", new DefaultCacheStore(redisDriver, properties.getPrefix()));
    manager.setDefaultStore("redis");
    return manager;
}
```

---

## 13. 配置选项

配置前缀为 `jaravel.cache`，对应 `CacheProperties` 类。

```yaml
jaravel:
  cache:
    default-store: array          # 默认 store 名称：array / file / database
    prefix: jaravel               # 缓存键前缀
    file-dir: /tmp/jaravel        # file 驱动目录，空则使用系统临时目录
    database-table: jaravel_cache # database 驱动表名
```

| 属性 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `jaravel.cache.default-store` | `String` | `array` | 默认 store 名称 |
| `jaravel.cache.prefix` | `String` | `jaravel` | 缓存键前缀（实际键为 `prefix:key`） |
| `jaravel.cache.file-dir` | `String` | `""`（空串） | file 驱动目录，空串表示使用 `${java.io.tmpdir}/jaravel-cache` |
| `jaravel.cache.database-table` | `String` | `jaravel_cache` | database 驱动表名，构造时自动建表 |

### 使用 database 驱动示例

```yaml
jaravel:
  cache:
    default-store: database
    prefix: myapp
    database-table: app_cache
```

```java
// 引入 spring-boot-starter-jdbc 并配置数据源后，database store 自动可用
Cache.put("user:1", user, 3600);                 // 写入默认 store（database）
Object value = Cache.store("database").get("user:1");
```

---

## 14. 线程安全说明

| 类 | 线程安全性 | 说明 |
| --- | --- | --- |
| `CacheManager` | 线程安全 | 使用 `ConcurrentHashMap` 维护 store 注册表，`defaultStore` 为普通 String 字段，建议在启动阶段设置后不再修改 |
| `ArrayCacheDriver` | 线程安全 | 基于 `ConcurrentHashMap`，读写操作原子。惰性清理通过 `removeIf` / `remove` 实现，并发安全 |
| `FileCacheDriver` | 部分线程安全 | 文件操作本身非原子，同一 key 的并发读写可能出现竞态（如同时写入同一文件）。不同 key 之间互不影响。`ObjectMapper` 为静态 final，线程安全 |
| `DatabaseCacheDriver` | 线程安全 | 基于 `JdbcTemplate`（本身线程安全），可作为单例共享。过期记录通过单线程守护执行器异步删除；`ObjectMapper` 为静态 final，线程安全 |
| `DefaultCacheStore` | 部分线程安全 | 委托给底层 `CacheDriver`，线程安全性取决于驱动。`increment` / `decrement` 采用 get-then-put 非原子实现，高并发场景下可能出现计数偏差 |
| `Cache` | 线程安全 | 静态方法，每次调用通过 `Facade.resolve()` 从容器解析 `CacheManager`，无共享可变状态 |
| `CacheProperties` | 配置只读 | Spring Boot 配置属性绑定，启动后只读 |
