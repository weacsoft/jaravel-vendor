# cache AI-API Reference

> Module: `cache` | Package: `com.weacsoft.jaravel.vendor.cache` | Version: 0.1.1

## Overview
cache 模块提供 Laravel 风格的缓存系统，包含 CacheManager（多仓库管理器）、CacheStore（高级缓存操作契约）、CacheDriver（底层存储契约）、DefaultCacheStore（默认仓库实现，带前缀隔离）、ArrayCacheDriver（内存驱动）、FileCacheDriver（文件驱动）、DatabaseCacheDriver（数据库驱动）和 Cache 门面。支持 put/get/has/forget/flush/pull/add/increment/decrement/putMany/getMany/remember/rememberForever 等完整语义，TTL 统一为秒。redis 驱动位于独立的 `redis-cache` 模块。

## Package Structure

模块按职责拆分为以下子包：

| 子包 | 类 |
|------|-----|
| `com.weacsoft.jaravel.vendor.cache`（根包） | `CacheDriver`(接口), `CacheStore`(接口), `CacheManager`, `Cache` |
| `com.weacsoft.jaravel.vendor.cache.driver` | `ArrayCacheDriver`, `FileCacheDriver`, `DatabaseCacheDriver` |
| `com.weacsoft.jaravel.vendor.cache.store` | `DefaultCacheStore` |
| `com.weacsoft.jaravel.vendor.cache.autoconfigure` | `CacheAutoConfiguration`, `CacheProperties` |

## Classes & Interfaces

### CacheManager
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.cache`
- **Description**: 缓存管理器，对齐 Laravel `Illuminate\Cache\CacheManager`。维护多个命名 CacheStore（如 array、file、redis 等），按名称解析 store 并提供默认 store。线程安全。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `CacheManager` | 无 | 构造方法 | 创建缓存管理器（默认 store 为 "array"） |
| `store` | 无 | `CacheStore` | 返回默认 store |
| `store` | `String name` | `CacheStore` | 按名称返回指定 store，未注册则抛异常 |
| `addStore` | `String name, CacheStore store` | `void` | 注册一个命名 store |
| `setDefaultStore` | `String name` | `void` | 设置默认 store 名称 |
| `getDefaultStore` | 无 | `String` | 获取默认 store 名称 |

#### Usage Example
```java
@Autowired
private CacheManager cacheManager;

// 使用默认 store
cacheManager.store().put("key", "value", 60);

// 使用指定 store
cacheManager.store("file").put("key", "value", 3600);

// 注册自定义 store
cacheManager.addStore("redis", redisStore);
cacheManager.setDefaultStore("redis");
```

---

### CacheStore
- **Type**: interface
- **Package**: `com.weacsoft.jaravel.vendor.cache`
- **Description**: 高级缓存操作契约，对齐 Laravel `Illuminate\Cache\Repository`。在底层 CacheDriver 之上提供完整缓存语义。TTL 单位为秒。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `put` | `String key, Object value, long ttlSeconds` | `boolean` | 写入缓存，指定过期秒数（<=0 永不过期） |
| `put` | `String key, Object value` | `boolean` | 永久写入缓存 |
| `get` | `String key` | `Object` | 读取原始缓存值，不存在返回 null |
| `get` | `String key, Class<T> type` | `<T> T` | 读取并按指定类型转换 |
| `has` | `String key` | `boolean` | 判断缓存键是否存在 |
| `forget` | `String key` | `boolean` | 移除指定缓存键 |
| `flush` | 无 | `void` | 清空当前 store 下所有缓存 |
| `pull` | `String key` | `Object` | 读取后立即删除 |
| `add` | `String key, Object value, long ttlSeconds` | `boolean` | 仅当键不存在时写入 |
| `increment` | `String key` | `long` | 自增 1 |
| `increment` | `String key, long amount` | `long` | 自增指定步长 |
| `decrement` | `String key` | `long` | 自减 1 |
| `decrement` | `String key, long amount` | `long` | 自减指定步长 |
| `putMany` | `Map<String, Object> values, long ttlSeconds` | `void` | 批量写入 |
| `getMany` | `Collection<String> keys` | `Map<String, Object>` | 批量读取 |
| `remember` | `String key, long ttlSeconds, Supplier<Object> loader` | `Object` | 读取或回填（带 TTL） |
| `rememberForever` | `String key, Supplier<Object> loader` | `Object` | 永久读取或回填 |

---

### CacheDriver
- **Type**: interface
- **Package**: `com.weacsoft.jaravel.vendor.cache`
- **Description**: 缓存驱动契约，对齐 Laravel 底层 `Illuminate\Contracts\Cache\Store`。与具体存储介质交互的最底层 CRUD 契约。TTL 单位为秒。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `put` | `String key, Object value, long ttlSeconds` | `boolean` | 写入缓存（<=0 永不过期） |
| `get` | `String key` | `Object` | 读取缓存，不存在/过期返回 null |
| `exists` | `String key` | `boolean` | 判断键是否存在（未过期） |
| `remove` | `String key` | `boolean` | 移除指定键 |
| `removeAll` | 无 | `void` | 清空所有缓存 |
| `allKeys` | 无 | `Collection<String>` | 返回所有未过期的缓存键 |

---

### DefaultCacheStore
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.cache.store`
- **Description**: 默认缓存仓库实现，对齐 Laravel `Illuminate\Cache\Repository`。委托给底层 CacheDriver，所有 key 操作前自动前置 prefix + ":"，用于隔离不同模块的缓存命名空间。
- **Implements**: `CacheStore`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `DefaultCacheStore` | `CacheDriver driver, String prefix` | 构造方法 | 创建缓存仓库（prefix 为 null 视为无前缀） |
| 所有 CacheStore 方法 | 见 CacheStore | - | 委托给底层 driver，自动加前缀 |

#### Usage Example
```java
CacheDriver driver = new ArrayCacheDriver();
CacheStore store = new DefaultCacheStore(driver, "myapp");
store.put("user:1", user, 60);  // 实际键: "myapp:user:1"
User u = store.get("user:1", User.class);
```

---

### ArrayCacheDriver
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.cache.driver`
- **Description**: 基于内存 ConcurrentHashMap 的缓存驱动，对齐 Laravel "array" 缓存驱动。线程安全，进程内有效，重启即失。读取时惰性清理过期条目。
- **Implements**: `CacheDriver`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `ArrayCacheDriver` | 无 | 构造方法 | 创建内存缓存驱动 |
| `put` | `String key, Object value, long ttlSeconds` | `boolean` | 写入缓存 |
| `get` | `String key` | `Object` | 读取缓存（惰性清理过期） |
| `exists` | `String key` | `boolean` | 判断键是否存在 |
| `remove` | `String key` | `boolean` | 移除键 |
| `removeAll` | 无 | `void` | 清空所有缓存 |
| `allKeys` | 无 | `Collection<String>` | 返回所有未过期键（惰性清理） |

---

### FileCacheDriver
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.cache.driver`
- **Description**: 基于文件系统的缓存驱动，对齐 Laravel "file" 缓存驱动。每个 key 对应目录下一个文件，使用 Jackson 序列化为 JSON。缓存键通过 UTF-8 十六进制编码作为文件名，保证可逆且文件系统安全。
- **Implements**: `CacheDriver`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `FileCacheDriver` | 无 | 构造方法 | 默认目录：${java.io.tmpdir}/jaravel-cache |
| `FileCacheDriver` | `String dir` | 构造方法 | 指定缓存目录 |
| `FileCacheDriver` | `File dir` | 构造方法 | 指定缓存目录 |
| `put` | `String key, Object value, long ttlSeconds` | `boolean` | 写入缓存到文件 |
| `get` | `String key` | `Object` | 读取缓存（过期则删除文件） |
| `exists` | `String key` | `boolean` | 判断键是否存在 |
| `remove` | `String key` | `boolean` | 删除缓存文件 |
| `removeAll` | 无 | `void` | 清空所有 .cache 文件 |
| `allKeys` | 无 | `Collection<String>` | 返回所有未过期键 |

#### Nested Types
- **FileCacheDriver.CacheEntry** (class): 文件缓存条目，含 value 和 expiryAt（0=永不过期）

#### Usage Example
```java
FileCacheDriver driver = new FileCacheDriver("/var/cache/myapp");
driver.put("user:1", user, 3600);  // 1 小时后过期
Object value = driver.get("user:1");
```

---

### DatabaseCacheDriver
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.cache.driver`
- **Description**: 基于关系型数据库的缓存驱动，对齐 Laravel "database" 缓存驱动。使用 Spring `JdbcTemplate` 将缓存条目持久化到 `jaravel_cache` 表，缓存值以 JSON 字符串存储。构造时自动建表（若不存在），自动适配 MySQL / PostgreSQL / SQLite / H2 / SQL Server 方言（建表与 upsert 语义）。读取 / 存在性判断时命中已过期记录会异步删除。
- **Implements**: `CacheDriver`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `DatabaseCacheDriver` | `DataSource dataSource` | 构造方法 | 使用默认表名 `jaravel_cache`，构造时自动建表 |
| `DatabaseCacheDriver` | `DataSource dataSource, String table` | 构造方法 | 指定表名，`null`/空串回退默认表名；构造时自动建表 |
| `put` | `String key, Object value, long ttlSeconds` | `boolean` | 序列化为 JSON，upsert 写入并设置过期时间 |
| `get` | `String key` | `Object` | 查询记录，若已过期则异步删除并返回 `null` |
| `exists` | `String key` | `boolean` | 查询记录是否存在，若已过期则异步删除并返回 `false` |
| `remove` | `String key` | `boolean` | DELETE 单条，返回是否删除 |
| `removeAll` | 无 | `void` | DELETE 全部 |
| `allKeys` | 无 | `Collection<String>` | 批量清理过期记录，返回所有未过期键 |

#### Table Schema
```sql
CREATE TABLE jaravel_cache (
  cache_key   VARCHAR(255) NOT NULL PRIMARY KEY,   -- 缓存键
  cache_value TEXT,                                -- 缓存值（JSON 字符串）
  expires_at  BIGINT NOT NULL DEFAULT 0            -- 过期时间戳（毫秒），0=永不过期
);
```
- 表名可通过 `jaravel.cache.database-table` 配置
- 不同方言下大文本类型自动适配（H2 用 `CLOB`，SQL Server 用 `NVARCHAR(MAX)`）
- `put` 采用各方言 upsert 语义：MySQL `ON DUPLICATE KEY UPDATE`、PostgreSQL/SQLite `ON CONFLICT DO UPDATE`、H2 `MERGE ... KEY`、SQL Server `MERGE ... USING`

#### Usage Example
```java
// 通常由自动装配创建（需容器中存在 DataSource bean，且 classpath 存在 spring-jdbc）
DatabaseCacheDriver driver = new DatabaseCacheDriver(dataSource, "jaravel_cache");
driver.put("user:1", user, 3600);   // 1 小时后过期
Object value = driver.get("user:1");
```

---

### Cache
- **Type**: class (final)
- **Package**: `com.weacsoft.jaravel.vendor.cache`
- **Description**: Cache 门面，对齐 Laravel `Cache::`。所有方法为静态方法，内部通过 Facade 解析 CacheManager，委托给默认 store。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `put` | `String key, Object value, long ttl` | `boolean` | 写入缓存（TTL 秒） |
| `put` | `String key, Object value` | `boolean` | 永久写入 |
| `get` | `String key` | `Object` | 读取缓存 |
| `get` | `String key, Class<T> type` | `<T> T` | 按类型读取 |
| `has` | `String key` | `boolean` | 判断是否存在 |
| `forget` | `String key` | `boolean` | 移除键 |
| `flush` | 无 | `void` | 清空缓存 |
| `pull` | `String key` | `Object` | 读取后删除 |
| `add` | `String key, Object value, long ttl` | `boolean` | 仅当不存在时写入 |
| `increment` | `String key` | `long` | 自增 1 |
| `increment` | `String key, long amount` | `long` | 自增指定步长 |
| `decrement` | `String key` | `long` | 自减 1 |
| `decrement` | `String key, long amount` | `long` | 自减指定步长 |
| `remember` | `String key, long ttl, Supplier<Object> loader` | `Object` | 读取或回填 |
| `rememberForever` | `String key, Supplier<Object> loader` | `Object` | 永久读取或回填 |
| `store` | 无 | `CacheStore` | 返回默认 store |
| `store` | `String name` | `CacheStore` | 返回指定 store |

#### Usage Example
```java
// 基本操作
Cache.put("user:1", user, 60);              // 60 秒后过期
User u = Cache.get("user:1", User.class);   // 类型化读取
boolean has = Cache.has("user:1");
Cache.forget("user:1");                     // 移除
Cache.flush();                              // 清空

// 自增/自减
long hits = Cache.increment("hits");
Cache.increment("hits", 10);
Cache.decrement("hits");

// 读取或回填
Object config = Cache.remember("app.config", 300, () -> loadConfig());
Object forever = Cache.rememberForever("app.version", () -> "1.0.0");

// 指定 store
Cache.store("file").put("key", "value", 3600);
```

---

### CacheAutoConfiguration
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.cache.autoconfigure`
- **Description**: 缓存自动装配，注册 ArrayCacheDriver、FileCacheDriver 驱动 Bean 和 CacheManager（注册 array/file 两个 store）；database 驱动由内部 `DatabaseCacheConfiguration` 在 `DataSource` bean 存在时装配。
- **Annotations**: `@AutoConfiguration`, `@ConditionalOnClass(CacheManager.class)`, `@AutoConfigureAfter(DataSourceAutoConfiguration)`, `@EnableConfigurationProperties(CacheProperties.class)`

#### Bean Methods

| Bean | Parameters | Return | Description |
|------|-----------|--------|-------------|
| `arrayCacheDriver` | 无 | `ArrayCacheDriver` | 内存缓存驱动（@Bean, @ConditionalOnMissingBean） |
| `fileCacheDriver` | `CacheProperties properties` | `FileCacheDriver` | 文件缓存驱动（@Bean, @ConditionalOnMissingBean） |
| `databaseCacheDriver` | `DataSource, CacheProperties` | `DatabaseCacheDriver` | 数据库缓存驱动（内部配置类，@ConditionalOnClass({DataSource, JdbcTemplate}), @ConditionalOnBean(DataSource), @ConditionalOnMissingBean） |
| `databaseCacheStore` | `CacheManager, DatabaseCacheDriver, CacheProperties` | `DefaultCacheStore` | database store，创建时注册到 CacheManager（仅当 DatabaseCacheDriver 存在时） |
| `cacheManager` | `CacheProperties, ArrayCacheDriver, FileCacheDriver` | `CacheManager` | 缓存管理器，注册 array/file store（@Bean, @ConditionalOnMissingBean）；database store 由 databaseCacheStore 追加注册 |

---

### CacheProperties
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.cache.autoconfigure`
- **Description**: 缓存配置属性，前缀 `jaravel.cache`，对齐 Laravel `config/cache.php`。
- **Annotations**: `@ConfigurationProperties(prefix = "jaravel.cache")`

#### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `defaultStore` | `String` | `"array"` | 默认 store 名称 |
| `prefix` | `String` | `"jaravel"` | 缓存键前缀 |
| `fileDir` | `String` | `""` | file 驱动目录，空则使用系统临时目录 |
| `databaseTable` | `String` | `"jaravel_cache"` | database 驱动表名，构造时自动建表 |

#### Usage Example
```yaml
# application.yml
jaravel:
  cache:
    default-store: database
    prefix: myapp
    file-dir: /var/cache/myapp
    database-table: app_cache
```
