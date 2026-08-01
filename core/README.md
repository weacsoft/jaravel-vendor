# core 模块

> Jaravel-Vendor 的核心基础模块，提供门面（Facade）、配置仓库（Config）、服务提供者（ServiceProvider）、校验器（Validator）以及 `Str` / `Arr` 字符串与数组工具。所有 vendor 包的基础依赖，包名统一为 `com.weacsoft.jaravel.vendor.core`。

---

## 目录

- [1. 模块概述](#1-模块概述)
- [2. 依赖信息](#2-依赖信息)
- [3. 类总览](#3-类总览)
- [4. Application / App —— 应用容器（替代 Facade）](#4-application--app--应用容器替代-facade)
- [5. Facade —— 门面基类（传统方式）](#5-facade--门面基类传统方式)
- [6. SpringContext —— Spring 上下文持有器](#6-springcontext--spring-上下文持有器)
- [7. 配置体系（Config / ConfigRepository / ConfigDefinition / ConfigDefinitionRegistrar）](#7-配置体系config--configrepository--configdefinition--configdefinitionregistrar)
- [8. 服务提供者（ServiceProvider / ProviderRegistry）](#8-服务提供者serviceprovider--providerregistry)
- [9. 工具类（Str / Arr）](#9-工具类str--arr)
- [10. 校验体系（FormRequest / Validator / Rule / Rules）](#10-校验体系formrequest--validator--rule--rules)
- [11. 异常类（ValidationException / UnauthorizedException）](#11-异常类validationexception--unauthorizedexception)
- [12. OnDriverInUseCondition —— 驱动按需装配条件](#12-ondriverinusecondition--驱动按需装配条件)
- [13. 线程安全说明](#13-线程安全说明)

---

## 1. 模块概述

`core` 模块是整个 Jaravel-Vendor 框架的基石，对齐 Laravel 的以下核心特性：

| Laravel 特性 | core 对应实现 | 说明 |
| --- | --- | --- |
| `app()` 应用容器 | `Application` + `App` | 继承式服务定位器，替代 Facade 静态代理 |
| Facade 门面 | `Facade` + `SpringContext` | 静态代理（传统方式，推荐改用 `App.app()`） |
| `config()` 配置仓库 | `Config` / `ConfigRepository` | 三层配置来源，支持点号取值 |
| `config/*.php` 代码级配置 | `ConfigDefinition` / `ConfigDefinitionRegistrar` | 以 Java 接口形式定义配置数组 |
| Service Provider | `ServiceProvider` / `ProviderRegistry` | 两阶段引导（register → boot） |
| `Str::` / `Arr::` | `Str` / `Arr` | 字符串与数组/集合工具 |
| Form Request / Validation | `FormRequest` / `Validator` / `Rule` / `Rules` | Laravel 风格校验器与规则集 |

本模块不依赖 Servlet、Web 等运行时环境，可在任意 Spring 应用中使用。

---

## 2. 依赖信息

### Maven 坐标

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>core</artifactId>
    <version>0.1.2</version>
</dependency>
```

### 传递依赖

| 依赖 | 用途 |
| --- | --- |
| `org.springframework:spring-context` | `ApplicationContext` 注入、Bean 解析 |
| `com.fasterxml.jackson.core:jackson-databind` | JSON 序列化支持 |
| `jakarta.annotation:jakarta.annotation-api` | Jakarta 注解基础 |
| `org.slf4j:slf4j-api` | 日志门面 |

> 运行环境要求：JDK 17+，Spring Boot 3.2.5（Spring 6.x）。

---

## 3. 类总览

```
com.weacsoft.jaravel.vendor.core
├── Application                   // 应用容器基类（服务定位器，替代 Facade）
├── App                           // 静态入口：App.app() 获取 Application 实例
├── Facade                        // 门面基类（传统静态代理，推荐改用 App.app()）
├── SpringContext                 // ApplicationContext 持有器
├── config
│   ├── Config                     // Config 门面（静态 API）
│   ├── ConfigRepository           // 配置仓库（三层来源）
│   ├── ConfigDefinition           // 代码级配置定义接口
│   └── ConfigDefinitionRegistrar  // 代码级配置自动注册器
├── condition
│   └── OnDriverInUseCondition     // 驱动按需装配条件基类（安装 != 启用）
├── provider
│   ├── ServiceProvider            // 服务提供者基类
│   └── ProviderRegistry           // 服务提供者注册器（两阶段引导）
├── support
│   ├── Str                        // 字符串工具
│   └── Arr                        // 数组/集合工具
└── validation
    ├── FormRequest                // Laravel 风格 Form Request 基类
    ├── Validator                  // 校验器
    ├── Rule                       // 校验规则契约（函数式接口）
    ├── Rules                      // 内置规则集合
    ├── ValidationException        // 校验失败异常
    └── UnauthorizedException      // 授权失败异常
```

---

## 4. Application / App —— 应用容器（替代 Facade）

`Application` 和 `App` 提供继承式服务定位器，对齐 Laravel 的 `app()` / `Application` / `Container`。
推荐使用此方式替代 Facade 静态代理，更面向对象、可测试、可扩展。

### 4.1 Application —— 应用容器基类

`com.weacsoft.jaravel.vendor.core.Application`

应用配置类继承本类后，既保持 `@Configuration` 功能，又获得服务定位器能力。

#### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `<T> T make(Class<T> type)` | 从 Spring 容器按类型解析 Bean（等价于 `SpringContext.bean(type)`） |
| `<T> T make(String name, Class<T> type)` | 从 Spring 容器按名称 + 类型解析 Bean |
| `<T> T make(String name)` | 按名称解析自定义注册的服务，未注册返回 `null` |
| `void singleton(String name, Supplier<Object> factory)` | 注册单例工厂，首次 `make(name)` 时创建并缓存（对齐 `App::singleton`） |
| `void bind(String name, Supplier<Object> factory)` | 注册工厂，每次 `make(name)` 创建新实例（对齐 `App::bind`） |
| `void register(String name, Object instance)` | 直接注册现成实例（对齐 `App::instance`） |
| `static void registerDefaultBinding(String name, Class<?> type)` | 注册别名到 Spring Bean 类型的映射（对齐 Laravel aliases 数组） |
| `boolean bound(String name)` | 检查指定名称的服务是否已注册 |
| `void forget(String name)` | 注销指定名称的服务 |
| `boolean publishToSpring(String name)` | 发布单个服务到 Spring 容器（如已存在则替换），别名不会被发布 |
| `int publishAllToSpring()` | 批量发布所有自定义服务到 Spring 容器，返回发布数量 |

#### CGLIB 兼容性

`@Configuration` 类会被 Spring CGLIB 代理子类化。本类的服务注册表使用 `static` 字段，确保代理对象与原始实例共享同一份注册表，不会因 CGLIB 代理导致字段未初始化的问题。

#### 发布到 Spring 容器

默认情况下，`singleton` / `bind` / `register` 注册的服务仅存在于 Application 的 static Map 中，**不会**进入 Spring 的 BeanFactory。这意味着 `@Autowired` 无法注入这些服务，它们只能通过 `make(name)` 获取。

当需要让 Spring 管理（如 `@Autowired` 注入）时，可手动发布：

```java
// 发布单个服务到 Spring
AppConfig.app().publishToSpring("myService");

// 批量发布所有自定义服务到 Spring
int count = AppConfig.app().publishAllToSpring();
```

**注意**：`registerDefaultBinding` 注册的别名（如 `"auth" -> AuthManager.class`）不会被发布，因为它们指向的 Bean 已经存在于 Spring 容器中。只有通过 `singleton` / `bind` / `register` 注册的自定义服务才会被发布。

### 4.2 App —— 静态入口

`com.weacsoft.jaravel.vendor.core.App`

`final` 工具类，提供 `app()` 静态方法获取 `Application` 实例。

| 方法签名 | 说明 |
| --- | --- |
| `static Application app()` | 获取 Spring 容器中唯一的 `Application` Bean |

### 使用示例

**基本用法**（任何模块均可使用）：

```java
import static com.weacsoft.jaravel.vendor.core.App.app;

// 通过 make(Class) 获取 Spring Bean
AuthManager auth = app().make(AuthManager.class);
CacheManager cache = app().make(CacheManager.class);
Dispatcher event = app().make(Dispatcher.class);

// 自定义服务注册
app().singleton("myService", () -> new MyService(dependency));
MyService svc = app().make("myService");

// 检查是否已注册
if (app().bound("myService")) { /* ... */ }
```

**typed 访问器**（继承扩展）：

应用配置类继承 `Application`，添加 typed 方法：

```java
@Configuration
public class AppConfig extends Application {
    public AuthManager auth()       { return make(AuthManager.class); }
    public CacheManager cache()     { return make(CacheManager.class); }
    public ConfigRepository config() { return make(ConfigRepository.class); }
    public Dispatcher event()       { return make(Dispatcher.class); }
    public SessionStore session()   { return make(SessionStore.class); }
    public Router router()          { return make(Router.class); }
}
```

在应用代码中使用（需强转为 `AppConfig`，或在应用模块自定义 `App` 类返回具体子类）：

```java
// 方式一：强转
AppConfig app = (AppConfig) App.app();
app.auth().check();
app.cache().get("key");

// 方式二：在应用模块自定义 App 类（免去强转）
public final class App {
    public static AppConfig app() {
        return SpringContext.bean(AppConfig.class);
    }
}
// 使用：App.app().auth().check();
```

**自定义扩展**：

```java
@Configuration
public class MyAppConfig extends AppConfig {
    public MyService myService() { return make(MyService.class); }
}
```

### Application vs Facade 对比

| 维度 | `App.app()` (Application) | Facade (Auth/Cache/Config...) |
| --- | --- | --- |
| 调用方式 | 实例方法 `app().make(Type)` | 静态方法 `Auth.check()` |
| 可扩展性 | 继承添加 typed 方法 | 需新建 Facade 类 |
| 自定义注册 | `bind()` / `singleton()` / `register()` | 不支持 |
| 发布到 Spring | `publishToSpring()` / `publishAllToSpring()` | 不支持 |
| 可测试性 | 可 mock 实例 | 静态方法难以 mock |
| 推荐度 | **推荐** | 传统方式，保持兼容 |

---

## 5. Facade —— 门面基类（传统方式）

`com.weacsoft.jaravel.vendor.core.Facade`

模仿 Laravel 的 Facade 机制：门面是一个静态代理，背后真正干活的是 Spring 容器里解析出的实例。本类是 `final` 工具类，不可实例化，提供静态方法供各具体门面在静态方法中解析被代理 Bean。

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `static <T> T resolve(Class<T> beanClass)` | 从 Spring 容器按类型解析 Bean |
| `static <T> T resolve(String name, Class<T> beanClass)` | 从 Spring 容器按名称解析 Bean |

### 使用示例

定义一个具体门面时，在静态方法中调用 `Facade.resolve(...)` 解析被代理的 Bean：

```java
public final class Auth {
    // 每次调用都从容器解析 AuthManager 实例
    private static AuthManager inst() {
        return Facade.resolve(AuthManager.class);
    }

    public static boolean check() {
        return inst().check();
    }

    public static Object user() {
        return inst().user();
    }
}
```

调用方即可像 Laravel 一样以静态方式使用：

```java
if (Auth.check()) {
    Object user = Auth.user();
}
```

---

## 6. SpringContext —— Spring 上下文持有器

`com.weacsoft.jaravel.vendor.core.SpringContext`

标注 `@Component`，实现 `ApplicationContextAware`，在容器启动时由 Spring 注入 `ApplicationContext` 并保存到静态字段，供 `Facade` 及其它需要静态访问容器的场景使用。

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `static ApplicationContext get()` | 获取当前 `ApplicationContext`，未初始化时抛 `IllegalStateException` |
| `static <T> T bean(Class<T> type)` | 按类型获取 Bean |
| `static <T> T bean(String name, Class<T> type)` | 按名称 + 类型获取 Bean |
| `static <T> T bean(String name)` | 按名称获取 Bean（无类型检查） |
| `static boolean contains(String name)` | 判断容器中是否存在指定名称的 Bean |
| `static void registerSingleton(String name, Object bean)` | 运行时注册/替换单例 Bean。如已存在同名 Bean，先销毁旧实例再注册新实例 |

### 使用示例

```java
// 直接通过 SpringContext 获取 Bean
AuthManager authManager = SpringContext.bean(AuthManager.class);

// 按名称获取
Object dataSource = SpringContext.bean("dataSource");

// 判断 Bean 是否存在
if (SpringContext.contains("myService")) {
    // ...
}

// 运行时注册单例到 Spring
SpringContext.registerSingleton("myService", new MyService());

// 替换已存在的 Bean
SpringContext.registerSingleton("authManager", newCustomAuthManager());
```

> 注意：`SpringContext` 必须在 ApplicationContext 初始化后才能使用。在单元测试等未启动 Spring 的场景下调用 `get()` 会抛出 `IllegalStateException`。

---

## 7. 配置体系（Config / ConfigRepository / ConfigDefinition / ConfigDefinitionRegistrar）

### 6.1 Config —— 配置门面

`com.weacsoft.jaravel.vendor.core.config.Config`

对齐 Laravel 的 `config('app.name')`，提供静态 API，内部通过 `Facade.resolve(ConfigRepository.class)` 解析 `ConfigRepository` 实例。

| 方法签名 | 说明 |
| --- | --- |
| `static <T> T get(String key, T defaultValue)` | 读取配置，支持点号路径，返回默认值 |
| `static <T> T get(String key)` | 读取配置，不存在返回 `null` |
| `static String string(String key, String defaultValue)` | 读取字符串配置 |
| `static String string(String key)` | 读取字符串配置，不存在返回 `null` |
| `static int getInt(String key, int defaultValue)` | 读取整型配置，解析失败返回默认值 |
| `static boolean getBool(String key, boolean defaultValue)` | 读取布尔配置（`true`/`1` 视为真） |
| `static void set(String key, Object value)` | 运行时设置配置（最高优先级覆盖） |
| `static boolean has(String key)` | 判断配置是否存在 |

```java
String name = Config.get("app.name", "Jaravel");
int port = Config.getInt("server.port", 8080);
boolean debug = Config.getBool("app.debug", false);

Config.set("app.debug", true);   // 运行时覆盖
if (Config.has("app.timezone")) { /* ... */ }
```

### 6.2 ConfigRepository —— 配置仓库

`com.weacsoft.jaravel.vendor.core.config.ConfigRepository`

配置来源有三层，优先级从高到低：

1. **运行时覆盖**（`set` 内存写入）—— 最高
2. **代码级配置**（`ConfigDefinition`，对齐 Laravel 的 `config/*.php`）
3. **Spring `Environment`**（`application.yml` 等外部配置）—— 最低

| 方法签名 | 说明 |
| --- | --- |
| `ConfigRepository(Environment environment)` | 构造器，传入 Spring `Environment` |
| `void registerConfigDefinition(ConfigDefinition definition)` | 注册代码级配置定义，按命名空间合并 |
| `<T> T get(String key, T defaultValue)` | 读取配置（按上述三层优先级查找） |
| `<T> T get(String key)` | 读取配置，不存在返回 `null` |
| `String string(String key, String defaultValue)` | 读取字符串 |
| `String string(String key)` | 读取字符串 |
| `int getInt(String key, int defaultValue)` | 读取整型 |
| `boolean getBool(String key, boolean defaultValue)` | 读取布尔 |
| `void set(String key, Object value)` | 运行时覆盖配置 |
| `boolean has(String key)` | 判断配置是否存在 |

查找逻辑（`get` 方法）：

```
1. 运行时覆盖 overrides 中是否包含 key？ -> 命中返回
2. 代码级配置 codeConfig 中是否包含 key？ -> 命中返回
3. Spring Environment 中是否有该 property？ -> 命中返回（转为字符串）
4. 返回 defaultValue
```

### 6.3 ConfigDefinition —— 代码级配置定义接口

`com.weacsoft.jaravel.vendor.core.config.ConfigDefinition`

对齐 Laravel 的 `config/*.php` 数组配置。用户实现此接口，在 `values()` 中返回配置数组，框架自动合并到 `ConfigRepository`。命名空间对应 Laravel 的配置文件名。

| 方法签名 | 说明 |
| --- | --- |
| `String namespace()` | 配置命名空间，如 `"app"`、`"database"`、`"auth"`，不可为 `null` |
| `Map<String, Object> values()` | 配置内容，返回嵌套 Map 结构，`null` 时被忽略 |

```java
// config/App.java
@Component
public class App implements ConfigDefinition {
    @Override
    public String namespace() { return "app"; }

    @Override
    public Map<String, Object> values() {
        return Map.of(
            "name", "Jaravel",
            "env", "production",
            "debug", false,
            "timezone", "Asia/Shanghai"
        );
    }
}
```

读取：`Config.get("app.name")` -> `"Jaravel"`。

多级嵌套配置：

```java
@Component
public class Database implements ConfigDefinition {
    @Override
    public String namespace() { return "database"; }

    @Override
    public Map<String, Object> values() {
        return Map.of(
            "connections", Map.of(
                "sqlite", Map.of("driver", "sqlite", "database", ":memory:")
            )
        );
    }
}
// Config.get("database.connections.sqlite.driver") -> "sqlite"
```

### 6.4 ConfigDefinitionRegistrar —— 代码级配置自动注册器

`com.weacsoft.jaravel.vendor.core.config.ConfigDefinitionRegistrar`

标注 `@Component`，实现 `SmartInitializingSingleton`。在所有单例 Bean 初始化完成后，自动发现容器中所有 `ConfigDefinition` Bean，逐个注册到 `ConfigRepository`，对齐 Laravel 在引导阶段加载 `config/*.php` 的行为。

- 通过 `@Autowired(required = false)` 注入 `List<ConfigDefinition>`，用户未定义任何代码级配置时安全跳过。
- 单个定义注册失败时记录错误日志，不影响其它定义。

---

## 8. 服务提供者（ServiceProvider / ProviderRegistry）

### 7.1 ServiceProvider —— 服务提供者基类

`com.weacsoft.jaravel.vendor.core.provider.ServiceProvider`

对齐 Laravel Service Provider。在 Spring 中以 `@Component` 注册，容器刷新时由 `ProviderRegistry` 依次调用 `register()` 与 `boot()`。

| 方法签名 | 说明 |
| --- | --- |
| `void register()` | 注册阶段：用于注册/绑定服务，此时其它 Bean 可能尚未就绪。默认空实现 |
| `void boot()` | 启动阶段：所有 Bean 就绪后执行，可安全注入并使用其它服务。默认空实现 |

```java
@Component
public class AppServiceProvider extends ServiceProvider {
    @Override
    public void register() {
        // 绑定轻量服务（此时其它 Bean 可能尚未就绪）
    }

    @Override
    public void boot() {
        // 注册事件监听、配置回调（所有 Bean 已就绪）
    }
}
```

### 7.2 ProviderRegistry —— 服务提供者注册器

`com.weacsoft.jaravel.vendor.core.provider.ProviderRegistry`

标注 `@Component`，实现 `SmartInitializingSingleton`。收集容器中所有 `ServiceProvider`，在所有单例 Bean 初始化完成后，先统一执行 `register()`，再统一执行 `boot()`，模仿 Laravel 的两阶段引导。

| 方法签名 | 说明 |
| --- | --- |
| `ProviderRegistry(List<ServiceProvider> providers)` | 构造器，注入所有 `ServiceProvider` |
| `void afterSingletonsInstantiated()` | 单例就绪后回调：先全部 `register()`，再全部 `boot()` |

引导流程：

```
所有单例 Bean 初始化完成
        │
        ▼
  遍历所有 providers 调用 register()   ← 第一阶段（注册）
        │
        ▼
  遍历所有 providers 调用 boot()       ← 第二阶段（启动）
        │
        ▼
  日志：Jaravel 服务提供者引导完成
```

单个 Provider 的 `register()` 或 `boot()` 抛异常时会被捕获并记录错误日志，不会中断整体引导流程。

---

## 9. 工具类（Str / Arr）

### 8.1 Str —— 字符串工具

`com.weacsoft.jaravel.vendor.core.support.Str`

对齐 Laravel `Str::` 常用方法。`final` 工具类，不可实例化。

| 方法签名 | 说明 |
| --- | --- |
| `static boolean startsWith(String s, String prefix)` | 是否以指定前缀开头 |
| `static boolean startsWith(String s, String... prefixes)` | 是否以任一前缀开头 |
| `static boolean endsWith(String s, String suffix)` | 是否以指定后缀结尾 |
| `static boolean contains(String s, CharSequence needle)` | 是否包含子串 |
| `static boolean contains(String s, String... needles)` | 是否包含任一子串 |
| `static boolean is(String pattern, String value)` | Laravel `Str::is()` 通配符匹配，支持 `*` |
| `static String camel(String value)` | 转为驼峰命名（camelCase） |
| `static String studly(String value)` | 转为 StudlyCaps 命名（PascalCase） |
| `static String snake(String value)` | 转为下划线命名（snake_case），默认 `_` 分隔 |
| `static String snake(String value, String delimiter)` | 转为指定分隔符的 snake 命名 |
| `static String ucwords(String value, String delimiter)` | 按分隔符拆分后首字母大写再拼接 |
| `static String random(int length)` | 生成指定长度的随机字符串（字母+数字） |
| `static String uuid()` | 生成无连字符的 UUID（32 位） |
| `static String replaceFirst(String s, String regex, Function<String,String> replacer)` | 用函数替换首个匹配 |

```java
Str.startsWith("HelloWorld", "Hello");          // true
Str.is("api/*", "api/users");                   // true
Str.camel("hello_world");                       // "helloWorld"
Str.studly("hello_world");                      // "HelloWorld"
Str.snake("HelloWorld");                        // "hello_world"
Str.snake("HelloWorld", "-");                   // "hello-world"
Str.random(16);                                 // 如 "aB3xK9mN2pQr7sT4"
Str.uuid();                                     // 如 "550e8400e29b41d4a716446655440000"
```

### 8.2 Arr —— 数组/集合工具

`com.weacsoft.jaravel.vendor.core.support.Arr`

对齐 Laravel `Arr::` 常用方法。`final` 工具类，不可实例化。

| 方法签名 | 说明 |
| --- | --- |
| `static <T> T get(Map<String,Object> map, String key, T defaultValue)` | 点号取值，如 `get(map, "user.profile.name")` |
| `static <T> T get(Map<String,Object> map, String key)` | 点号取值，默认 `null` |
| `static void set(Map<String,Object> map, String key, Object value)` | 点号设值，自动创建中间 Map |
| `static boolean has(Map<String,Object> map, String key)` | 点号判断键是否存在（显式遍历路径） |
| `static <T> List<T> pluck(Collection<Map<String,Object>> list, String key)` | 从集合中提取指定键的值列表 |
| `static <T,R> List<R> map(Collection<T> list, Function<T,R> mapper)` | 对集合元素做映射转换 |
| `static Map<String,Object> only(Map<String,Object> map, String... keys)` | 仅保留指定键 |
| `static Map<String,Object> except(Map<String,Object> map, String... keys)` | 排除指定键 |

```java
Map<String, Object> data = new LinkedHashMap<>();
Arr.set(data, "user.profile.name", "Alice");
Arr.get(data, "user.profile.name");          // "Alice"
Arr.has(data, "user.profile.name");          // true
Arr.has(data, "user.profile.age");           // false

List<Map<String, Object>> users = List.of(
    Map.of("id", 1, "name", "Alice"),
    Map.of("id", 2, "name", "Bob")
);
Arr.pluck(users, "name");                    // ["Alice", "Bob"]

Map<String, Object> filtered = Arr.only(data, "user");   // 仅保留 user
Map<String, Object> rest = Arr.except(data, "user");      // 排除 user
```

> 注意 `has` 方法的实现：不能用 `get(map, key, new Object()) != null` 判断，因为键不存在时 `get` 会返回传入的默认值（非 null）导致永远为 true。`has` 显式遍历点号路径，仅当每一级都存在时才返回 true。

---

## 10. 校验体系（FormRequest / Validator / Rule / Rules）

### 9.1 Rule —— 校验规则契约

`com.weacsoft.jaravel.vendor.core.validation.Rule`

函数式接口，对齐 Laravel 的 Rule。每个规则判断给定值是否通过，并返回错误消息模板。

| 方法签名 | 说明 |
| --- | --- |
| `boolean passes(String field, Object value, String[] params, Map<String,Object> data)` | 判断是否通过校验。`params` 为规则参数（如 `min:1` 中的 `["1"]`） |
| `default String message()` | 错误消息模板，可用 `:field` `:value` `:param0` 占位。默认 `"The :field field is invalid."` |

自定义规则示例：

```java
Rule evenRule = (field, value, params, data) ->
    value instanceof Number n && n.intValue() % 2 == 0;
// message() 使用默认值
```

### 9.2 Rules —— 内置规则集合

`com.weacsoft.jaravel.vendor.core.validation.Rules`

内置校验规则集合，对齐 Laravel 常用规则。`final` 工具类，通过 `Rules.get(name, params)` 按名称获取规则实例。

#### 预定义常量

| 常量 | 说明 |
| --- | --- |
| `REQUIRED` | 必填（非 null 且非空串/空集合/空 Map） |
| `NULLABLE` | 可为 null（仅占位，校验恒通过） |
| `STRING` | 必须为字符串 |
| `INTEGER` | 必须为整数（`Number` 或可解析为 `Long`） |
| `NUMERIC` | 必须为数字（可解析为 `Double`） |
| `BOOLEAN` | 必须为布尔（`true`/`false`/`0`/`1`） |
| `EMAIL` | 必须为合法邮箱 |
| `ARRAY` | 必须为数组（`Collection` 或 `Map`） |

#### 工厂方法

| 方法签名 | 说明 |
| --- | --- |
| `static Rule min(int min)` | 最小值/最小长度规则 |
| `static Rule max(int max)` | 最大值/最大长度规则 |
| `static Rule in(String... values)` | 值必须在给定集合内 |
| `static Rule notIn(String... values)` | 值不能在给定集合内 |
| `static Rule get(String name, String[] params)` | 按名称与参数构造规则 |

`get` 支持的规则名：`required` / `nullable` / `string` / `integer`(或`int`) / `numeric` / `boolean`(或`bool`) / `email` / `array`，以及带参数的 `min:N` / `max:N` / `in:a,b,c` / `not_in:a,b`。

#### 规则行为说明

- `Required`：`null` 返回 false；空字符串、空集合、空 Map 返回 false；其余返回 true。
- `Min` / `Max`：对字符串按长度、对集合按 size、对数字按数值大小判断；`null` 视为通过。
- `In` / `NotIn`：将值转为字符串比较；`null` 视为通过。
- `Email`：正则 `^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$`；`null` 视为通过。

```java
Rule r1 = Rules.get("required", new String[0]);
Rule r2 = Rules.get("min", new String[]{"2"});
Rule r3 = Rules.in("active", "inactive");
```

### 9.3 Validator —— 校验器

`com.weacsoft.jaravel.vendor.core.validation.Validator`

Laravel 风格校验器。规则串格式如 `"required|string|min:2"`，以 `|` 分隔，带参数的规则以 `:` 分隔参数，多参数以 `,` 分隔。

| 方法签名 | 说明 |
| --- | --- |
| `static Validator make(Map<String,Object> data, Map<String,String> rules, Map<String,String> messages)` | 创建校验器，`messages` 的 key 形如 `"field.rule"`，可为 `null` |
| `static Validator make(Map<String,Object> data, Map<String,String> rules)` | 创建校验器，无自定义消息 |
| `boolean fails()` | 是否校验失败 |
| `boolean passes()` | 是否校验通过 |
| `Map<String,List<String>> errors()` | 获取错误信息（字段 -> 错误消息列表） |
| `Map<String,Object> validate()` | 执行校验，失败抛 `ValidationException`，成功返回已校验数据 |

校验逻辑要点：

- `null` 值：仅 `required` 规则生效并报错，其余规则跳过。
- 错误消息模板支持 `:field`、`:value`、`:param0` 占位符替换。
- 自定义消息优先于规则默认消息（按 `field.rule` 匹配）。
- `validate()` 返回的已校验数据仅包含 `rules` 中定义的字段。

```java
Map<String, Object> data = Map.of("name", "A", "age", "200");

Validator v = Validator.make(data, Map.of(
    "name", "required|string|min:2",
    "age", "integer|min:1|max:150"
));

if (v.fails()) {
    Map<String, List<String>> errors = v.errors();
    // errors: {"name": ["The name must be at least 2 characters."],
    //          "age":  ["The age may not be greater than 150 characters."]}
} else {
    Map<String, Object> validated = v.validate();
}
```

带自定义消息：

```java
Map<String, String> messages = Map.of(
    "name.required", "姓名不能为空",
    "name.min", "姓名至少 :param0 个字符"
);
Validator v = Validator.make(data, rules, messages);
```

### 9.4 FormRequest —— Laravel 风格 Form Request 基类

`com.weacsoft.jaravel.vendor.core.validation.FormRequest`

抽象基类，子类定义 `rules()` 与可选的 `messages()`、`authorize()`，调用 `validate(Map)` 完成校验。

| 方法签名 | 说明 |
| --- | --- |
| `abstract Map<String,String> rules()` | 校验规则，key 为字段名，value 为规则串 |
| `Map<String,String> messages()` | 自定义错误消息，默认空 Map |
| `boolean authorize()` | 授权检查，返回 false 时校验抛 `UnauthorizedException`。默认 true |
| `Map<String,Object> validate(Map<String,Object> data)` | 执行校验，成功返回已校验数据，失败抛 `ValidationException` |
| `boolean isValid(Map<String,Object> data)` | 仅校验不抛异常，返回是否通过 |
| `Map<String,Object> prepare(Map<String,Object> data)` | 预处理/过滤数据，默认原样返回，子类可重写 |

```java
public class StoreUserRequest extends FormRequest {
    @Override
    public Map<String, String> rules() {
        return Map.of(
            "name", "required|string|min:2",
            "age",  "required|integer|min:1|max:150",
            "email", "required|email"
        );
    }

    @Override
    public Map<String, String> messages() {
        return Map.of("email.email", "邮箱格式不正确");
    }

    @Override
    public boolean authorize() {
        // 仅管理员可创建用户
        return Auth.check() && "admin".equals(Auth.user().role());
    }
}

// 使用
StoreUserRequest req = new StoreUserRequest();
try {
    Map<String, Object> validated = req.validate(inputData);
    // 校验通过，validated 仅包含 rules 中定义的字段
} catch (ValidationException e) {
    Map<String, List<String>> errors = e.errors();
} catch (UnauthorizedException e) {
    // 授权失败
}
```

---

## 11. 异常类（ValidationException / UnauthorizedException）

### ValidationException

`com.weacsoft.jaravel.vendor.core.validation.ValidationException`

校验失败异常，继承 `RuntimeException`，携带字段 -> 错误消息列表。

| 方法签名 | 说明 |
| --- | --- |
| `ValidationException(Map<String,List<String>> errors)` | 构造器，默认消息 `"The given data was invalid."` |
| `Map<String,List<String>> errors()` | 获取字段错误信息 |

### UnauthorizedException

`com.weacsoft.jaravel.vendor.core.validation.UnauthorizedException`

授权失败异常（`FormRequest.authorize()` 返回 false 时抛出），继承 `RuntimeException`。

| 方法签名 | 说明 |
| --- | --- |
| `UnauthorizedException(String message)` | 构造器 |

---

## 12. OnDriverInUseCondition —— 驱动按需装配条件

`com.weacsoft.jaravel.vendor.core.condition.OnDriverInUseCondition`

整个 vendor 模块组的**统一装配原则**：**安装 ≠ 启用**。

对齐 Laravel 的心智模型——把依赖放进 classpath 只表示"这个驱动可用"，
不表示"要启用它"。凡是需要**外部资源**的驱动型模块（redis 缓存、redis session、
database 缓存、redis/database 队列……），只有在用户**显式选用**时才注册与配置；
否则完全静默：不创建任何 Bean、不连接任何外部服务、不影响应用启动。

> **典型反例**：项目引入了 `session-redis` 依赖但 `jaravel.session.driver` 配的是
> `file`。若 session-redis 仍自动装配并连接 Redis，就会导致无 Redis 环境启动失败。

### 与 Spring 条件注解的分工

| 场景 | 用什么 |
| --- | --- |
| 判断**驱动是否被选用** | `OnDriverInUseCondition` 子类 |
| 判断**类是否在 classpath** | `@ConditionalOnClass`（防 `NoClassDefFoundError`） |
| 允许业务方**覆盖框架 Bean** | `@ConditionalOnMissingBean` |
| 判断**功能模块开关** | `@ConditionalOnProperty(name = "enabled")` |
| ~~判断驱动资源是否可用~~ | ~~`@ConditionalOnBean(DataSource.class)`~~ ← **不要用** |

`@ConditionalOnBean` 把模块与 Spring 的 Bean 图强绑定，时序脆弱，
且感知不到 jaravel 自己的注册表（如 `@RegisterConnection` 的连接）。
正确做法是**运行时惰性解析**：先查框架注册表，再回退 Spring 容器。

### 判定优先级

1. **覆盖开关**（`enableKey`）—— `true` 强制启用，`false` 强制关闭；
2. **单值配置键**（`singleKeys`）—— 如 `jaravel.session.driver`；
3. **映射式配置键**（`mapKeyPrefix` + `mapKeySuffix`）—— 如 `jaravel.cache.stores.*.driver`。

任意一处的值等于驱动名（忽略大小写）即判定为"被用上"。

### 构造器

| 构造器签名 | 说明 |
| --- | --- |
| `OnDriverInUseCondition(String driverName, String mapKeyPrefix, String mapKeySuffix, String... singleKeys)` | `driverName` 为本模块驱动名；`mapKeyPrefix`/`mapKeySuffix` 描述映射式配置（无则传 `null`）；`singleKeys` 为单值配置键 |

| 方法签名 | 说明 |
| --- | --- |
| `protected OnDriverInUseCondition enableKey(String key)` | 设置覆盖开关键，优先级最高，返回 `this` 便于链式调用 |

> 本类只实现 `spring-context` 的 `Condition` 接口，**不引入 `spring-boot-autoconfigure`**，
> 以保持 core 模块的依赖足迹不变。

### 使用示例

```java
public class OnRedisSessionDriverCondition extends OnDriverInUseCondition {
    public OnRedisSessionDriverCondition() {
        super("redis", "jaravel.session.stores.", ".driver", "jaravel.session.driver");
        enableKey("jaravel.session.redis.auto-register");
    }
}
```

```java
@AutoConfiguration
@ConditionalOnClass({RedisSessionStore.class, RedisManager.class})
@Conditional(OnRedisSessionDriverCondition.class)   // 用上了才装配
@ConditionalOnBean(RedisManager.class)              // 兜底保护
public class SessionRedisAutoConfiguration { ... }
```

### 已应用本条件的模块

| 模块 | 驱动名 | 启用配置 |
| --- | --- | --- |
| `session-redis` | `redis` | `jaravel.session.driver: redis` |
| `redis-cache` | `redis` | `jaravel.cache.stores.*.driver: redis` |
| `cache`（database 驱动） | `database` | `jaravel.cache.stores.*.driver: database` |

> **功能型模块**（wire、storage 的 local、schedule、captcha、plugin-* 等）
> 不需要外部资源，默认启用，通过 `jaravel.<模块>.enabled: false` 关闭，不适用本条件。

---

## 13. 线程安全说明

| 类 | 线程安全性 | 说明 |
| --- | --- | --- |
| `Application` | 线程安全 | `singletons` 和 `factories` 使用 `ConcurrentHashMap`，支持并发读写。注册阶段（启动时）与解析阶段（运行时）可安全并发 |
| `App` | 线程安全 | 无状态静态方法，委托给 `SpringContext` |
| `SpringContext` | 需注意 | `context` 为静态字段，Spring 启动时单次写入，之后只读。写入与读取非原子，但 Spring 容器刷新完成后并发读取是安全的 |
| `ConfigRepository` | 非线程安全 | `overrides` 与 `codeConfig` 使用 `LinkedHashMap`，`set` / `registerConfigDefinition` 写入与 `get` 并发读取时需外部同步。配置通常在启动阶段写入、运行时只读，实际使用中风险较低 |
| `Validator` | 非线程安全 | `errors` 字段懒初始化且非同步，单个 `Validator` 实例应在单线程内使用（每次校验新建实例） |
| `Rules` 内部规则 | 线程安全 | 所有规则实现为无状态对象（`EmailRule` 的 `Pattern` 为静态 final），可安全跨线程复用 |
| `Str` / `Arr` / `Facade` / `Config` | 线程安全 | 均为无状态静态方法，可安全并发调用 |
| `FormRequest` | 单线程使用 | 子类通常为每次请求新建实例，不应跨请求共享 |
