# model-cache AI-API Reference

> Module: `model-cache` | Package: `com.weacsoft.jaravel.vendor.modelcache` | Version: 0.1.1

## Overview
model-cache 是 Jaravel-Vendor 的可选模型缓存模块，参考 Laravel `laravel-model-caching` 方案。通过 `@CachableModel` 注解在 Model 类上手动开启查询缓存，采用版本号机制实现缓存失效（无需 tag 支持）。核心包含 CachableModel（注解）、ModelCacheProperties（配置）、ModelCacheService（核心服务，版本化缓存读写/失效）、ModelCache（静态门面）和 ModelCacheAutoConfiguration（自动装配）。基于 `cache` 模块的 CacheStore / CacheManager，TTL 统一为秒。

## Classes & Interfaces

### CachableModel
- **Type**: annotation (@interface)
- **Package**: `com.weacsoft.jaravel.vendor.modelcache`
- **Description**: 标注在 Model 类上手动开启查询缓存。`@Target(TYPE)`、`@Retention(RUNTIME)`。参考 Laravel `laravel-model-caching`，仅在标注本注解的类上启用缓存，未标注的类直接回源。
- **Annotations**: `@Target(ElementType.TYPE)`, `@Retention(RetentionPolicy.RUNTIME)`

#### Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `ttl` | `long` | `-1` | 缓存 TTL（秒）。`-1` 表示使用全局默认值 `jaravel.model-cache.default-ttl`；`0` 表示永不过期（仅靠版本号失效）；正数为有效秒数 |
| `prefix` | `String` | `""` | 自定义缓存键前缀（拼在全局 keyPrefix 之后，类名位置）。为空时使用类名（`Class.getSimpleName()`） |

#### Cache Key Structure
```
{keyPrefix}{modelPrefix}:v{version}:find:{id}          # 主键查询
{keyPrefix}{modelPrefix}:v{version}:query:{queryKey}   # 任意查询
{keyPrefix}{modelPrefix}:version                       # 版本号
```
- `keyPrefix`：全局前缀，默认 `model-cache:`
- `modelPrefix`：`prefix()` 非空时用之，否则用类名
- `version`：当前版本号，初始为 1

#### Usage Example
```java
@CachableModel(ttl = 600, prefix = "usr")
public class User extends BaseModel<User, Long> { ... }
// 缓存键：model-cache:usr:v1:find:1
```

---

### ModelCacheProperties
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.modelcache`
- **Description**: 模型缓存配置属性，前缀 `jaravel.model-cache`。TTL 单位为秒。
- **Annotations**: `@ConfigurationProperties(prefix = "jaravel.model-cache")`

#### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `boolean` | `true` | 全局开关，关闭后所有模型缓存不生效（直接回源） |
| `store` | `String` | `"array"` | 缓存 store 名称，需在 CacheManager 中已注册 |
| `defaultTtl` | `long` | `3600` | 默认缓存 TTL（秒），@CachableModel 未指定或为 -1 时使用 |
| `keyPrefix` | `String` | `"model-cache:"` | 缓存键前缀 |

#### Usage Example
```yaml
jaravel:
  model-cache:
    enabled: true
    store: array
    default-ttl: 3600
    key-prefix: "model-cache:"
```

---

### ModelCacheService
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.modelcache`
- **Description**: 模型缓存核心服务，对齐 Laravel `laravel-model-caching` 的查询缓存能力。通过 `@CachableModel` 注解手动开启缓存，采用版本号机制实现缓存失效：每个模型类维护一个版本号，所有缓存键包含当前版本号；失效时递增版本号，旧缓存随 TTL 自然过期清除。由 `ModelCacheAutoConfiguration` 以 `@Bean` 方式注册（非 `@Component`）。

#### Constructor

| Constructor | Parameters | Description |
|-------------|-----------|-------------|
| `ModelCacheService` | `CacheManager cacheManager, ModelCacheProperties properties` | 创建模型缓存服务 |

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `find` | `<T, K> Class<T> modelClass, K id, Supplier<T> loader` | `T` | 缓存按主键查询。未标注注解或全局关闭时直接回源；loader 返回 null 时不缓存 |
| `findAll` | `<T> Class<T> modelClass, String queryKey, Supplier<List<T>> loader` | `List<T>` | 缓存列表查询 |
| `query` | `<T> Class<T> modelClass, String queryKey, Supplier<Object> loader` | `Object` | 缓存任意查询（如聚合 count） |
| `invalidate` | `<T> Class<T> modelClass` | `void` | 失效模型类的所有缓存（递增版本号） |
| `invalidate` | `<T, K> Class<T> modelClass, K id` | `void` | 失效单条记录的主键查询缓存（forget 单键） |
| `getVersion` | `<T> Class<T> modelClass` | `long` | 获取当前版本号，不存在时初始化为 1 并写入缓存 |
| `getTtl` | `<T> Class<T> modelClass` | `long` | 获取模型 TTL（优先注解 ttl，负数时用全局 default-ttl） |
| `isCachable` | `Class<?> modelClass` | `boolean` | 判断是否可缓存（全局开关开启 + 标注 @CachableModel） |

#### Invalidation Mechanism
- `invalidate(Class)`：递增版本号键 `{keyPrefix}{modelPrefix}:version`，使旧版本缓存键不再被命中，旧缓存随 TTL 自然过期。失效前确保版本键已初始化（不存在时写入 1），避免首次 `increment` 从 0 起算导致版本号未变化。
- `invalidate(Class, id)`：直接 `forget` 主键查询键 `{keyPrefix}{modelPrefix}:v{version}:find:{id}`，不影响版本号与查询缓存。

#### Remember Semantics & Null Handling
`find` / `findAll` / `query` 采用 remember 语义（命中返回、未命中加载并回填），但对 loader 返回 `null` 的结果**不回填**，避免缓存未命中结果（如 find 未找到记录）占用空间。

#### Usage Example
```java
@Autowired
private ModelCacheService modelCacheService;

// 缓存按主键查询
User user = modelCacheService.find(User.class, 1L, () -> User.find(1L));

// 缓存列表查询
List<User> all = modelCacheService.findAll(User.class, "all", () -> User.all());

// 失效整个模型类
modelCacheService.invalidate(User.class);

// 失效单条记录
modelCacheService.invalidate(User.class, 1L);

// 查询版本号与 TTL
long version = modelCacheService.getVersion(User.class);
long ttl = modelCacheService.getTtl(User.class);
boolean cachable = modelCacheService.isCachable(User.class);
```

---

### ModelCache
- **Type**: class (final)
- **Package**: `com.weacsoft.jaravel.vendor.modelcache`
- **Description**: ModelCache 门面，对齐 Laravel `laravel-model-caching` 的静态调用风格。所有方法为静态方法，内部通过 `Facade.resolve(ModelCacheService.class)` 从 Spring 容器解析 `ModelCacheService`，委托其全部公共方法。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `find` | `static <T, K> Class<T> modelClass, K id, Supplier<T> loader` | `T` | 缓存按主键查询 |
| `findAll` | `static <T> Class<T> modelClass, String queryKey, Supplier<List<T>> loader` | `List<T>` | 缓存列表查询 |
| `query` | `static <T> Class<T> modelClass, String queryKey, Supplier<Object> loader` | `Object` | 缓存任意查询 |
| `invalidate` | `static <T> Class<T> modelClass` | `void` | 失效整个模型类（递增版本号） |
| `invalidate` | `static <T, K> Class<T> modelClass, K id` | `void` | 失效单条记录 |
| `getVersion` | `static <T> Class<T> modelClass` | `long` | 获取当前版本号 |
| `getTtl` | `static <T> Class<T> modelClass` | `long` | 获取模型 TTL |
| `isCachable` | `static Class<?> modelClass` | `boolean` | 判断是否可缓存 |

#### Usage Example
```java
// 按主键查询
User user = ModelCache.find(User.class, 1L, () -> User.find(1L));

// 列表查询
List<User> all = ModelCache.findAll(User.class, "all", () -> User.all());

// 任意查询
Object count = ModelCache.query(User.class, "count", () -> User.query().count());

// 失效缓存
ModelCache.invalidate(User.class);          // 整个模型类
ModelCache.invalidate(User.class, 1L);      // 单条记录

// 元信息
long version = ModelCache.getVersion(User.class);
long ttl = ModelCache.getTtl(User.class);
boolean cachable = ModelCache.isCachable(User.class);
```

---

### ModelCacheAutoConfiguration
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.modelcache`
- **Description**: 模型缓存自动装配。仅当 classpath 存在 `CacheManager` 与 `ModelCacheService`，且容器中已存在 `CacheManager` Bean 时生效（即 cache 模块已装配）。注册 `ModelCacheService` Bean，从 `CacheManager` 按配置的 store 名称解析缓存仓库。本模块为可选模块，不引入依赖时不影响其他模块。
- **Annotations**: `@AutoConfiguration`, `@ConditionalOnClass({CacheManager.class, ModelCacheService.class})`, `@AutoConfigureAfter(name = "com.weacsoft.jaravel.vendor.cache.CacheAutoConfiguration")`, `@EnableConfigurationProperties(ModelCacheProperties.class)`

#### Bean Methods

| Bean | Parameters | Return | Description |
|------|-----------|--------|-------------|
| `modelCacheService` | `CacheManager cacheManager, ModelCacheProperties properties` | `ModelCacheService` | 模型缓存服务（@Bean, @ConditionalOnMissingBean, @ConditionalOnBean(CacheManager.class)）。store 在运行时按 `ModelCacheProperties.getStore()` 从 CacheManager 解析 |

#### AutoConfiguration Registration
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:
```
com.weacsoft.jaravel.vendor.modelcache.ModelCacheAutoConfiguration
```

#### Usage Example
引入 `model-cache` 与 `cache` 依赖后，自动装配生效，无需手动配置：
```java
// 直接使用门面或注入服务
User user = ModelCache.find(User.class, 1L, () -> User.find(1L));
```

覆盖默认 Bean：
```java
@Bean
public ModelCacheService modelCacheService(CacheManager cacheManager, ModelCacheProperties properties) {
    return new CustomModelCacheService(cacheManager, properties);
}
```
