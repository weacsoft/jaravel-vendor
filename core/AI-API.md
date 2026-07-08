# core AI-API Reference

> Module: `core` | Package: `com.weacsoft.jaravel.vendor.core` | Version: 0.1.1

## Overview
core 模块是 jaravel-vendor 框架的基础设施层，提供 Facade 门面机制、Spring 上下文访问、配置仓库（Config）、服务提供者（ServiceProvider）两阶段引导、Laravel 风格的表单校验（Validator/FormRequest/Rules）以及 Str/Arr 字符串与数组工具。它模仿 Laravel 的核心设计理念，在 Spring Boot 之上提供一套静态门面 API。

## Classes & Interfaces

### Facade
- **Type**: class (final)
- **Package**: `com.weacsoft.jaravel.vendor.core`
- **Description**: 门面工具基类，模仿 Laravel Facade。提供静态方法从 Spring 容器解析被代理 Bean，供各具体门面在静态方法中调用。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `resolve` | `Class<T> beanClass` | `<T> T` | 从 Spring 容器按类型解析 Bean |
| `resolve` | `String name, Class<T> beanClass` | `<T> T` | 从 Spring 容器按名称解析 Bean |

#### Usage Example
```java
public final class Auth {
    private static AuthManager inst() {
        return Facade.resolve(AuthManager.class);
    }
    public static boolean check() {
        return inst().check();
    }
}

// 按名称解析
CacheManager mgr = Facade.resolve("cacheManager", CacheManager.class);
```

---

### SpringContext
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.core`
- **Description**: Spring 上下文持有器，实现 `ApplicationContextAware`。供 Facade 门面静态访问容器中的 Bean。
- **Annotations**: `@Component`
- **Implements**: `org.springframework.context.ApplicationContextAware`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `setApplicationContext` | `ApplicationContext applicationContext` | `void` | Spring 注入上下文（由框架调用） |
| `get` | 无 | `ApplicationContext` | 获取当前 ApplicationContext，未初始化时抛 IllegalStateException |
| `bean` | `Class<T> type` | `<T> T` | 按类型获取 Bean |
| `bean` | `String name, Class<T> type` | `<T> T` | 按名称+类型获取 Bean |
| `bean` | `String name` | `<T> T` | 按名称获取 Bean（泛型推断） |
| `contains` | `String name` | `boolean` | 判断容器中是否包含指定名称的 Bean |

#### Usage Example
```java
// 静态获取容器中的 Bean
AuthManager authManager = SpringContext.bean(AuthManager.class);
Object namedBean = SpringContext.bean("myService");
boolean exists = SpringContext.contains("cacheManager");
```

---

### Config
- **Type**: class (final)
- **Package**: `com.weacsoft.jaravel.vendor.core.config`
- **Description**: 配置门面，对齐 Laravel `config('app.name')`。所有方法为静态方法，内部通过 Facade 解析 ConfigRepository。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `get` | `String key, T defaultValue` | `<T> T` | 读取配置，支持点号路径，带默认值 |
| `get` | `String key` | `<T> T` | 读取配置，无默认值 |
| `string` | `String key, String defaultValue` | `String` | 读取字符串配置 |
| `string` | `String key` | `String` | 读取字符串配置 |
| `getInt` | `String key, int defaultValue` | `int` | 读取整型配置 |
| `getBool` | `String key, boolean defaultValue` | `boolean` | 读取布尔配置 |
| `set` | `String key, Object value` | `void` | 运行时设置配置（覆盖 Environment） |
| `has` | `String key` | `boolean` | 判断配置是否存在 |

#### Usage Example
```java
String name = Config.get("app.name", "Jaravel");
int port = Config.getInt("server.port", 8080);
boolean debug = Config.getBool("app.debug", false);
Config.set("app.debug", true);
if (Config.has("database.default")) { /* ... */ }
```

---

### ConfigDefinition
- **Type**: interface
- **Package**: `com.weacsoft.jaravel.vendor.core.config`
- **Description**: 代码级配置定义接口，对齐 Laravel 的 config/*.php 数组配置。用户实现此接口在 `values()` 中返回配置数组，框架自动合并到 ConfigRepository。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `namespace` | 无 | `String` | 配置命名空间，如 "app"、"database" |
| `values` | 无 | `Map<String, Object>` | 配置内容，返回嵌套 Map 结构 |

#### Usage Example
```java
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
// 读取: Config.get("app.name") -> "Jaravel"
```

---

### ConfigDefinitionRegistrar
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.core.config`
- **Description**: 代码级配置自动注册器。在所有单例 Bean 初始化完成后，自动发现容器中所有 ConfigDefinition Bean，逐个注册到 ConfigRepository。
- **Annotations**: `@Component`
- **Implements**: `org.springframework.beans.factory.SmartInitializingSingleton`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `afterSingletonsInstantiated` | 无 | `void` | 所有单例就绪后自动注册 ConfigDefinition（由框架调用） |

---

### ConfigRepository
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.core.config`
- **Description**: 配置仓库，对齐 Laravel `config()`。配置来源有三层，优先级从高到低：运行时覆盖 > 代码级配置(ConfigDefinition) > Spring Environment。支持点号取值与默认值。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `ConfigRepository` | `Environment environment` | 构造方法 | 构造配置仓库 |
| `registerConfigDefinition` | `ConfigDefinition definition` | `void` | 注册代码级配置定义，将其 values 合并到命名空间下 |
| `get` | `String key, T defaultValue` | `<T> T` | 读取配置，查找顺序：运行时覆盖 -> 代码级配置 -> Spring Environment |
| `get` | `String key` | `<T> T` | 读取配置，无默认值 |
| `string` | `String key, String defaultValue` | `String` | 读取字符串配置 |
| `string` | `String key` | `String` | 读取字符串配置 |
| `getInt` | `String key, int defaultValue` | `int` | 读取整型配置 |
| `getBool` | `String key, boolean defaultValue` | `boolean` | 读取布尔配置 |
| `set` | `String key, Object value` | `void` | 运行时设置配置（覆盖 Environment） |
| `has` | `String key` | `boolean` | 判断配置是否存在 |

#### Usage Example
```java
@Autowired
private ConfigRepository config;

config.set("app.debug", true);
String name = config.string("app.name", "default");
boolean has = config.has("database.connections.mysql");
```

---

### ServiceProvider
- **Type**: abstract class
- **Package**: `com.weacsoft.jaravel.vendor.core.provider`
- **Description**: 服务提供者基类，对齐 Laravel Service Provider。在 Spring 中以 @Component 注册，容器刷新时由 ProviderRegistry 依次调用 register()（注册阶段）与 boot()（启动阶段）。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `register` | 无 | `void` | 注册阶段：用于注册/绑定服务，此时其它 Bean 可能尚未就绪 |
| `boot` | 无 | `void` | 启动阶段：所有 Bean 就绪后执行，可安全注入并使用其它服务 |

#### Usage Example
```java
@Component
public class AppServiceProvider extends ServiceProvider {
    @Override
    public void register() {
        // 绑定轻量服务
    }
    @Override
    public void boot() {
        // 注册事件监听、配置回调
    }
}
```

---

### ProviderRegistry
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.core.provider`
- **Description**: 服务提供者注册器。收集容器中所有 ServiceProvider，在所有单例 Bean 初始化完成后，先统一执行 register()，再统一执行 boot()，模仿 Laravel 的两阶段引导。
- **Annotations**: `@Component`
- **Implements**: `org.springframework.beans.factory.SmartInitializingSingleton`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `ProviderRegistry` | `List<ServiceProvider> providers` | 构造方法 | 注入所有 ServiceProvider |
| `afterSingletonsInstantiated` | 无 | `void` | 所有单例就绪后依次执行 register() 和 boot()（由框架调用） |

---

### Arr
- **Type**: class (final)
- **Package**: `com.weacsoft.jaravel.vendor.core.support`
- **Description**: 数组/集合工具，对齐 Laravel `Arr::` 常用方法。支持点号路径取值/设值/判断存在。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `get` | `Map<String, Object> map, String key, T defaultValue` | `<T> T` | 点号取值，如 `get(map, "user.profile.name")` |
| `get` | `Map<String, Object> map, String key` | `<T> T` | 点号取值，默认 null |
| `set` | `Map<String, Object> map, String key, Object value` | `void` | 点号设值，自动创建中间 Map |
| `has` | `Map<String, Object> map, String key` | `boolean` | 点号判断键是否存在 |
| `pluck` | `Collection<Map<String, Object>> list, String key` | `<T> List<T>` | 从集合中提取指定键的值列表 |
| `map` | `Collection<T> list, Function<T, R> mapper` | `<T, R> List<R>` | 对集合元素做映射转换 |
| `only` | `Map<String, Object> map, String... keys` | `Map<String, Object>` | 仅保留指定键 |
| `except` | `Map<String, Object> map, String... keys` | `Map<String, Object>` | 排除指定键 |

#### Usage Example
```java
Map<String, Object> data = Map.of("user", Map.of("name", "Alice"));
String name = Arr.get(data, "user.name", "unknown");
Arr.set(data, "user.age", 30);
boolean has = Arr.has(data, "user.name");
List<String> names = Arr.pluck(userList, "name");
```

---

### Str
- **Type**: class (final)
- **Package**: `com.weacsoft.jaravel.vendor.core.support`
- **Description**: 字符串工具，对齐 Laravel `Str::` 常用方法。提供大小写转换、通配符匹配、随机串生成等。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `startsWith` | `String s, String prefix` | `boolean` | 判断是否以指定前缀开头 |
| `startsWith` | `String s, String... prefixes` | `boolean` | 判断是否以任一前缀开头 |
| `endsWith` | `String s, String suffix` | `boolean` | 判断是否以指定后缀结尾 |
| `contains` | `String s, CharSequence needle` | `boolean` | 判断是否包含子串 |
| `contains` | `String s, String... needles` | `boolean` | 判断是否包含任一子串 |
| `is` | `String pattern, String value` | `boolean` | 通配符匹配，支持 `*` |
| `camel` | `String value` | `String` | 转为 camelCase |
| `studly` | `String value` | `String` | 转为 StudlyCase (PascalCase) |
| `snake` | `String value` | `String` | 转为 snake_case（默认下划线分隔） |
| `snake` | `String value, String delimiter` | `String` | 转为 snake_case（自定义分隔符） |
| `ucwords` | `String value, String delimiter` | `String` | 将各部分首字母大写 |
| `random` | `int length` | `String` | 生成指定长度的随机字符串 |
| `uuid` | 无 | `String` | 生成无连字符的 UUID |
| `replaceFirst` | `String s, String regex, Function<String, String> replacer` | `String` | 替换第一个匹配项 |

#### Usage Example
```java
boolean match = Str.is("user.*", "user.profile");
String camel = Str.camel("user_name"); // "userName"
String studly = Str.studly("user_name"); // "UserName"
String snake = Str.snake("UserName"); // "user_name"
String token = Str.random(32);
String id = Str.uuid();
```

---

### FormRequest
- **Type**: abstract class
- **Package**: `com.weacsoft.jaravel.vendor.core.validation`
- **Description**: Laravel 风格 Form Request 基类。子类定义 rules() 与可选的 messages()、authorize()，调用 validate() 完成校验。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `rules` | 无 | `Map<String, String>` | 校验规则，key 为字段名，value 为规则串（抽象方法） |
| `messages` | 无 | `Map<String, String>` | 自定义错误消息，key 形如 "field.rule" |
| `authorize` | 无 | `boolean` | 授权检查，返回 false 时校验将抛出 UnauthorizedException |
| `validate` | `Map<String, Object> data` | `Map<String, Object>` | 执行校验，成功返回已校验数据，失败抛 ValidationException |
| `isValid` | `Map<String, Object> data` | `boolean` | 仅校验不抛异常，返回是否通过 |
| `prepare` | `Map<String, Object> data` | `Map<String, Object>` | 预处理/过滤数据，默认原样返回 |

#### Usage Example
```java
public class StoreUserRequest extends FormRequest {
    @Override
    public Map<String, String> rules() {
        return Map.of(
            "name", "required|string|min:2",
            "age", "required|integer|min:1|max:150"
        );
    }
    @Override
    public boolean authorize() {
        return true; // 授权逻辑
    }
}

StoreUserRequest req = new StoreUserRequest();
Map<String, Object> validated = req.validate(inputData);
```

---

### Rule
- **Type**: interface (functional)
- **Package**: `com.weacsoft.jaravel.vendor.core.validation`
- **Description**: 校验规则契约，对齐 Laravel 的 Rule。每个规则判断给定值是否通过，并返回错误消息模板。
- **Annotations**: `@FunctionalInterface`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `passes` | `String field, Object value, String[] params, Map<String, Object> data` | `boolean` | 判断是否通过校验 |
| `message` | 无 | `String` | 错误消息模板，可用 `:field` `:value` `:param0` 占位（default 方法） |

#### Usage Example
```java
Rule customRule = (field, value, params, data) -> {
    return value != null && value.toString().length() >= 5;
};
// 或实现接口
public class UniqueEmail implements Rule {
    @Override
    public boolean passes(String field, Object value, String[] params, Map<String, Object> data) {
        // 检查邮箱唯一性
        return !emailExists(String.valueOf(value));
    }
    @Override
    public String message() {
        return "The :field has already been taken.";
    }
}
```

---

### Rules
- **Type**: class (final)
- **Package**: `com.weacsoft.jaravel.vendor.core.validation`
- **Description**: 内置校验规则集合，对齐 Laravel 常用规则。通过 `Rules.get(name, params)` 按名称获取规则实例。包含 Required、StringRule、IntegerRule、NumericRule、BooleanRule、EmailRule、ArrayRule、Min、Max、In、NotIn 等内部类。

#### Fields

| Field | Type | Description |
|-------|------|-------------|
| `REQUIRED` | `Rule` | 必填规则（非 null 且非空串） |
| `NULLABLE` | `Rule` | 可为 null（占位，校验恒通过） |
| `STRING` | `Rule` | 字符串类型规则 |
| `INTEGER` | `Rule` | 整数类型规则 |
| `NUMERIC` | `Rule` | 数值类型规则 |
| `BOOLEAN` | `Rule` | 布尔类型规则 |
| `EMAIL` | `Rule` | 邮箱格式规则 |
| `ARRAY` | `Rule` | 数组类型规则 |

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `min` | `int min` | `Rule` | 构造最小值规则 |
| `max` | `int max` | `Rule` | 构造最大值规则 |
| `in` | `String... values` | `Rule` | 构造枚举值规则 |
| `notIn` | `String... values` | `Rule` | 构造排除值规则 |
| `get` | `String name, String[] params` | `Rule` | 按规则名与参数构造规则 |

#### Usage Example
```java
Rule minRule = Rules.min(3);
Rule emailRule = Rules.EMAIL;
Rule inRule = Rules.in("active", "pending", "closed");
Rule parsed = Rules.get("min", new String[]{"5"});
```

---

### Validator
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.core.validation`
- **Description**: Laravel 风格校验器。解析规则串（如 `required|string|min:2`），逐字段逐规则校验，支持自定义错误消息。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `make` | `Map<String, Object> data, Map<String, String> rules, Map<String, String> messages` | `Validator` | 创建校验器（带自定义消息） |
| `make` | `Map<String, Object> data, Map<String, String> rules` | `Validator` | 创建校验器（无自定义消息） |
| `fails` | 无 | `boolean` | 判断校验是否失败 |
| `passes` | 无 | `boolean` | 判断校验是否通过 |
| `errors` | 无 | `Map<String, List<String>>` | 获取校验错误（字段 -> 错误消息列表） |
| `validate` | 无 | `Map<String, Object>` | 执行校验，失败抛 ValidationException，成功返回已校验数据 |

#### Usage Example
```java
Validator v = Validator.make(data, Map.of(
    "name", "required|string|min:2",
    "age", "integer|min:1"
));
if (v.fails()) {
    Map<String, List<String>> errors = v.errors();
    // 处理错误
}
Map<String, Object> valid = v.validate(); // 失败抛 ValidationException
```

---

### ValidationException
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.core.validation`
- **Description**: 校验失败异常，携带字段 -> 错误消息列表。
- **Extends**: `RuntimeException`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `ValidationException` | `Map<String, List<String>> errors` | 构造方法 | 构造校验异常 |
| `errors` | 无 | `Map<String, List<String>>` | 获取校验错误信息 |

---

### UnauthorizedException
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.core.validation`
- **Description**: 授权失败异常（FormRequest.authorize() 返回 false 时抛出）。
- **Extends**: `RuntimeException`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `UnauthorizedException` | `String message` | 构造方法 | 构造授权异常 |
