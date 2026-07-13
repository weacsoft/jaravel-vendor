# core 模块

> Jaravel-Vendor 的核心基础模块，提供门面（Facade）、配置仓库（Config）、服务提供者（ServiceProvider）、校验器（Validator）以及 `Str` / `Arr` 字符串与数组工具。所有 vendor 包的基础依赖，包名统一为 `com.weacsoft.jaravel.vendor.core`。

---

## 目录

- [1. 模块概述](#1-模块概述)
- [2. 依赖信息](#2-依赖信息)
- [3. 类总览](#3-类总览)
- [4. Facade —— 门面基类](#4-facade--门面基类)
- [5. SpringContext —— Spring 上下文持有器](#5-springcontext--spring-上下文持有器)
- [6. 配置体系（Config / ConfigRepository / ConfigDefinition / ConfigDefinitionRegistrar）](#6-配置体系config--configrepository--configdefinition--configdefinitionregistrar)
- [7. 服务提供者（ServiceProvider / ProviderRegistry）](#7-服务提供者serviceprovider--providerregistry)
- [8. 工具类（Str / Arr）](#8-工具类str--arr)
- [9. 校验体系（FormRequest / Validator / Rule / Rules）](#9-校验体系formrequest--validator--rule--rules)
- [10. 异常类（ValidationException / UnauthorizedException）](#10-异常类validationexception--unauthorizedexception)
- [11. 线程安全说明](#11-线程安全说明)

---

## 1. 模块概述

`core` 模块是整个 Jaravel-Vendor 框架的基石，对齐 Laravel 的以下核心特性：

| Laravel 特性 | core 对应实现 | 说明 |
| --- | --- | --- |
| Facade 门面 | `Facade` + `SpringContext` | 静态代理，背后从 Spring 容器解析真实 Bean |
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
├── Facade                         // 门面基类（静态代理工具）
├── SpringContext                  // ApplicationContext 持有器
├── config
│   ├── Config                     // Config 门面（静态 API）
│   ├── ConfigRepository           // 配置仓库（三层来源）
│   ├── ConfigDefinition           // 代码级配置定义接口
│   └── ConfigDefinitionRegistrar  // 代码级配置自动注册器
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

## 4. Facade —— 门面基类

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

## 5. SpringContext —— Spring 上下文持有器

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
```

> 注意：`SpringContext` 必须在 ApplicationContext 初始化后才能使用。在单元测试等未启动 Spring 的场景下调用 `get()` 会抛出 `IllegalStateException`。

---

## 6. 配置体系（Config / ConfigRepository / ConfigDefinition / ConfigDefinitionRegistrar）

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

## 7. 服务提供者（ServiceProvider / ProviderRegistry）

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

## 8. 工具类（Str / Arr）

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

## 9. 校验体系（FormRequest / Validator / Rule / Rules）

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

## 10. 异常类（ValidationException / UnauthorizedException）

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

## 11. 线程安全说明

| 类 | 线程安全性 | 说明 |
| --- | --- | --- |
| `SpringContext` | 需注意 | `context` 为静态字段，Spring 启动时单次写入，之后只读。写入与读取非原子，但 Spring 容器刷新完成后并发读取是安全的 |
| `ConfigRepository` | 非线程安全 | `overrides` 与 `codeConfig` 使用 `LinkedHashMap`，`set` / `registerConfigDefinition` 写入与 `get` 并发读取时需外部同步。配置通常在启动阶段写入、运行时只读，实际使用中风险较低 |
| `Validator` | 非线程安全 | `errors` 字段懒初始化且非同步，单个 `Validator` 实例应在单线程内使用（每次校验新建实例） |
| `Rules` 内部规则 | 线程安全 | 所有规则实现为无状态对象（`EmailRule` 的 `Pattern` 为静态 final），可安全跨线程复用 |
| `Str` / `Arr` / `Facade` / `Config` | 线程安全 | 均为无状态静态方法，可安全并发调用 |
| `FormRequest` | 单线程使用 | 子类通常为每次请求新建实例，不应跨请求共享 |
