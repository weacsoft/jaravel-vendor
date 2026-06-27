# artisan AI-API Reference

> Module: `artisan` | Package: `com.weacsoft.jaravel.vendor.artisan` | Version: 0.1.0

## Overview

artisan 模块提供了 Laravel 风格的命令行工具框架。`ArtisanApplication` 负责命令注册、查找和执行；`ArtisanCommand` 是所有自定义命令的抽象基类，子类实现 `handle()` 方法完成业务逻辑；`ArtisanRunner` 实现 Spring Boot `CommandLineRunner`，在应用启动时检测 `--artisan` 参数并派发到对应命令。支持参数解析（`{arg}` 位置参数、`{--option}` 选项参数）和帮助信息输出。

## Classes & Interfaces

### ArtisanCommand
- **Type**: abstract class
- **Package**: `com.weacsoft.jaravel.vendor.artisan`
- **Description**: 所有 Artisan 命令的抽象基类。子类需实现 `handle()` 方法，可通过 `argument()` 和 `option()` 获取命令行参数。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `getSignature` | - | `String` | 获取命令签名，如 `make:migration {name} {--force}` |
| `setSignature` | `String signature` | `void` | 设置命令签名 |
| `getDescription` | - | `String` | 获取命令描述 |
| `setDescription` | `String description` | `void` | 设置命令描述 |
| `handle` | `String[] args` | `int` | 命令处理逻辑（抽象方法），返回退出码，0 表示成功 |
| `argument` | `String name` | `String` | 获取位置参数值，不存在返回 null |
| `option` | `String name` | `boolean` | 获取选项参数是否设置（`--name` 形式） |
| `hasArgument` | `String name` | `boolean` | 检查位置参数是否存在 |
| `hasOption` | `String name` | `boolean` | 检查选项参数是否存在 |

#### Usage Example
```java
@Component
public class MakeMigrationCommand extends ArtisanCommand {

    public MakeMigrationCommand() {
        setSignature("make:migration {name} {--force}");
        setDescription("Create a new migration file");
    }

    @Override
    public int handle(String[] args) {
        String name = argument("name");        // 获取位置参数
        boolean force = option("force");       // 获取选项参数
        // ... 创建迁移文件逻辑
        System.out.println("Created migration: " + name);
        return 0;
    }
}
```

### ArtisanApplication
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.artisan`
- **Annotations**: `@Component`
- **Description**: Artisan 命令注册中心与执行器。自动收集所有 `ArtisanCommand` Bean，支持按签名查找命令、解析参数并执行。提供 `list` 命令列出所有已注册命令。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `register` | `ArtisanCommand command` | `void` | 注册命令到应用 |
| `find` | `String name` | `ArtisanCommand` | 按命令名查找已注册命令，不存在返回 null |
| `execute` | `String[] args` | `int` | 解析参数并执行对应命令，返回退出码 |
| `listCommands` | - | `Map<String, ArtisanCommand>` | 返回所有已注册命令（命令名 -> 命令实例） |
| `hasCommand` | `String name` | `boolean` | 检查命令是否已注册 |
| `getCommandCount` | - | `int` | 获取已注册命令数量 |

#### Usage Example
```java
@Autowired
private ArtisanApplication artisan;

// 执行命令
int exitCode = artisan.execute(new String[]{"make:migration", "create_users_table", "--force"});

// 列出所有命令
artisan.listCommands().forEach((name, cmd) -> {
    System.out.println(name + " - " + cmd.getDescription());
});
```

### ArtisanRunner
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.artisan`
- **Implements**: `org.springframework.boot.CommandLineRunner`
- **Annotations**: `@Component`
- **Description**: Spring Boot 启动钩子。在应用启动时检测启动参数中是否包含 `--artisan`，若存在则将后续参数传递给 `ArtisanApplication.execute()` 执行对应命令，执行完毕后调用 `System.exit()` 退出 JVM。无 `--artisan` 参数时不做任何操作。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `run` | `String... args` | `void` | CommandLineRunner 入口，检测 `--artisan` 并派发命令 |

#### Usage Example
```bash
# 在命令行运行 Artisan 命令
java -jar app.jar --artisan make:migration create_users_table --force

# 列出所有可用命令
java -jar app.jar --artisan list
```

### ArtisanAutoConfiguration
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.artisan`
- **Annotations**: `@AutoConfiguration`, `@ConditionalOnClass(ArtisanApplication.class)`
- **Description**: Artisan 自动装配。创建 `ArtisanApplication` Bean（`@ConditionalOnMissingBean`，便于业务方覆盖），并注册 `MakeCodeProperties` 配置 Bean（绑定 `jaravel.artisan.make.*`）以及 8 个 `make:xxx` 代码生成命令 Bean（`make:controller` / `make:middleware` / `make:model` / `make:migration` / `make:command` / `make:event` / `make:listener` / `make:all`）。命令在首次调用 `call()` / `all()` / `listCommands()` 时惰性从 Spring 容器扫描所有 `ArtisanCommand` Bean 并注册。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `artisanApplication` | `ApplicationContext applicationContext` | `ArtisanApplication` | 创建 Artisan 应用 Bean（`@Bean`, `@ConditionalOnMissingBean`） |
| `makeCodeProperties` | - | `MakeCodeProperties` | 创建代码生成配置 Bean，绑定 `jaravel.artisan.make.*`（`@Bean`, `@ConfigurationProperties`） |
| `makeControllerCommand` | `MakeCodeProperties properties` | `MakeControllerCommand` | 创建 `make:controller` 命令 Bean（`@Bean`） |
| `makeMiddlewareCommand` | `MakeCodeProperties properties` | `MakeMiddlewareCommand` | 创建 `make:middleware` 命令 Bean（`@Bean`） |
| `makeModelCommand` | `MakeCodeProperties properties` | `MakeModelCommand` | 创建 `make:model` 命令 Bean（`@Bean`） |
| `makeMigrationCommand` | `MakeCodeProperties properties` | `MakeMigrationCommand` | 创建 `make:migration` 命令 Bean（`@Bean`） |
| `makeCommandCommand` | `MakeCodeProperties properties` | `MakeCommandCommand` | 创建 `make:command` 命令 Bean（`@Bean`） |
| `makeEventCommand` | `MakeCodeProperties properties` | `MakeEventCommand` | 创建 `make:event` 命令 Bean（`@Bean`） |
| `makeListenerCommand` | `MakeCodeProperties properties` | `MakeListenerCommand` | 创建 `make:listener` 命令 Bean（`@Bean`） |
| `makeAllCommand` | `MakeCodeProperties properties` | `MakeAllCommand` | 创建 `make:all` 命令 Bean（`@Bean`） |

#### Usage Example
```yaml
# application.yml
jaravel:
  artisan:
    make:
      base-package: com.example.app
      output-dir: src/main/java
      migration-dir: migrations
```

---

## 代码生成（make 包）

### MakeCodeProperties
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.artisan.make`
- **Annotations**: `@ConfigurationProperties(prefix = "jaravel.artisan.make")`（由 `ArtisanAutoConfiguration` 注册）
- **Description**: 代码生成配置属性，前缀 `jaravel.artisan.make`。控制 `make:xxx` 系列命令生成文件的基包名、输出目录和迁移目录。对齐 Laravel 的目录约定，Controller/Middleware/Model/Migration/Command/Event/Listener 均在 `base-package` 下创建子包。

#### Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `basePackage` | `String` | `com.weacsoft.jaravel` | 基包名（生成类的根包） |
| `outputDir` | `String` | `src/main/java` | 输出根目录（Java 源码根目录） |
| `migrationDir` | `String` | `migrations` | 迁移文件目录（相对路径，不带包名前缀） |

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `getBasePackage` | - | `String` | 获取基包名 |
| `setBasePackage` | `String basePackage` | `void` | 设置基包名 |
| `getOutputDir` | - | `String` | 获取输出根目录 |
| `setOutputDir` | `String outputDir` | `void` | 设置输出根目录 |
| `getMigrationDir` | - | `String` | 获取迁移文件目录 |
| `setMigrationDir` | `String migrationDir` | `void` | 设置迁移文件目录 |

#### Usage Example
```yaml
# application.yml
jaravel:
  artisan:
    make:
      base-package: com.example.app
      output-dir: src/main/java
      migration-dir: migrations
```

```java
// 通过依赖注入获取配置
@Autowired
private MakeCodeProperties properties;

// 或手动构建
MakeCodeProperties properties = new MakeCodeProperties();
properties.setBasePackage("com.example.app");
properties.setOutputDir("/tmp/generated");
```

### MakeGenerator
- **Type**: final class（工具类，不可实例化）
- **Package**: `com.weacsoft.jaravel.vendor.artisan.make`
- **Description**: 代码生成器核心，对齐 Laravel `php artisan make:xxx`。根据 `MakeCodeProperties` 配置的基包和输出目录，生成 Controller、Middleware、Model、Migration、Command、Event、Listener 的 Java 源文件，并自动放到对应包目录下。类名自动转为 PascalCase，缺失后缀时自动补全；文件已存在时抛出 `IllegalStateException`，除非 `force=true`。所有生成方法均为 `static`。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `generateController` | `MakeCodeProperties properties, String name, boolean force` | `String` | 生成 Controller 类（`implements Controllers.Runner`），返回文件绝对路径 |
| `generateMiddleware` | `MakeCodeProperties properties, String name, boolean force` | `String` | 生成 Middleware 类（`implements Middleware`），返回文件绝对路径 |
| `generateModel` | `MakeCodeProperties properties, String name, boolean force` | `String` | 生成 Model 类（含 id/createdAt/updatedAt 字段），返回文件绝对路径 |
| `generateMigration` | `MakeCodeProperties properties, String name, boolean force` | `String` | 生成 Migration 类（类名 `Migration_YYYY_MM_DD_Name`，`implements Migration`），返回文件绝对路径 |
| `generateCommand` | `MakeCodeProperties properties, String name, boolean force` | `String` | 生成 ArtisanCommand 子类（`extends ArtisanCommand`），返回文件绝对路径 |
| `generateEvent` | `MakeCodeProperties properties, String name, boolean force` | `String` | 生成 Event 类（`implements Event`），返回文件绝对路径 |
| `generateListener` | `MakeCodeProperties properties, String name, String eventName, boolean force` | `String` | 生成 Listener 类（`implements Listener<EventType>`），`eventName` 指定监听的事件类型，返回文件绝对路径 |
| `ensureSuffix` | `String name, String suffix` | `String` | （包级可见，static）确保类名以指定后缀结尾，自动转 PascalCase |
| `toPascalCase` | `String input` | `String` | （包级可见，static）将任意字符串转换为 PascalCase |
| `toSnakeCase` | `String input` | `String` | （包级可见，static）将 PascalCase 转换为 snake_case |

> 以上方法均声明 `throws IOException`。`generateListener` 的 `eventName` 为 null/空时使用占位类型 `YourEvent`。

#### Usage Example
```java
MakeCodeProperties properties = new MakeCodeProperties();
properties.setBasePackage("com.example.app");
properties.setOutputDir("src/main/java");

// 生成 Controller（自动补全 Controller 后缀）
String path = MakeGenerator.generateController(properties, "User", false);
// => src/main/java/com/example/app/controller/UserController.java

// 生成 Migration（自动加日期前缀）
path = MakeGenerator.generateMigration(properties, "create_users_table", false);
// => .../migration/Migration_2024_01_01_CreateUsersTable.java

// 生成 Listener（关联指定事件）
path = MakeGenerator.generateListener(properties, "SendWelcomeEmail", "UserRegisteredEvent", false);
// => .../listener/SendWelcomeEmailListener.java

// 强制覆盖已存在文件
path = MakeGenerator.generateController(properties, "User", true);
```

### MakeControllerCommand
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.artisan.make`
- **Extends**: `ArtisanCommand`
- **Signature**: `make:controller {name} {--force}`
- **Description**: 生成 Controller 类（对齐 Laravel `php artisan make:controller`）。调用 `MakeGenerator.generateController()` 生成源文件，文件已存在时返回错误码 1，加 `--force` 强制覆盖。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `signature` | - | `String` | 返回 `make:controller {name} {--force}` |
| `description` | - | `String` | 返回命令描述 |
| `setProperties` | `MakeCodeProperties properties` | `void` | 注入代码生成配置 |
| `handle` | - | `int` | 读取 `name` 参数与 `--force` 选项，调用生成器，返回退出码 |

#### Usage Example
```bash
java -jar app.jar artisan make:controller UserController
java -jar app.jar artisan make:controller User --force
```

### MakeMiddlewareCommand
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.artisan.make`
- **Extends**: `ArtisanCommand`
- **Signature**: `make:middleware {name} {--force}`
- **Description**: 生成 Middleware 类（对齐 Laravel `php artisan make:middleware`）。调用 `MakeGenerator.generateMiddleware()` 生成源文件。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `signature` | - | `String` | 返回 `make:middleware {name} {--force}` |
| `description` | - | `String` | 返回命令描述 |
| `setProperties` | `MakeCodeProperties properties` | `void` | 注入代码生成配置 |
| `handle` | - | `int` | 读取 `name` 参数与 `--force` 选项，调用生成器，返回退出码 |

#### Usage Example
```bash
java -jar app.jar artisan make:middleware AuthMiddleware
java -jar app.jar artisan make:middleware Auth --force
```

### MakeModelCommand
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.artisan.make`
- **Extends**: `ArtisanCommand`
- **Signature**: `make:model {name} {--force}`
- **Description**: 生成 Model 类（对齐 Laravel `php artisan make:model`）。调用 `MakeGenerator.generateModel()` 生成源文件。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `signature` | - | `String` | 返回 `make:model {name} {--force}` |
| `description` | - | `String` | 返回命令描述 |
| `setProperties` | `MakeCodeProperties properties` | `void` | 注入代码生成配置 |
| `handle` | - | `int` | 读取 `name` 参数与 `--force` 选项，调用生成器，返回退出码 |

#### Usage Example
```bash
java -jar app.jar artisan make:model User
java -jar app.jar artisan make:model UserModel --force
```

### MakeMigrationCommand
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.artisan.make`
- **Extends**: `ArtisanCommand`
- **Signature**: `make:migration {name} {--force}`
- **Description**: 生成 Migration 类（对齐 Laravel `php artisan make:migration`）。调用 `MakeGenerator.generateMigration()` 生成源文件，类名自动加当天日期前缀 `Migration_YYYY_MM_DD_Name`。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `signature` | - | `String` | 返回 `make:migration {name} {--force}` |
| `description` | - | `String` | 返回命令描述 |
| `setProperties` | `MakeCodeProperties properties` | `void` | 注入代码生成配置 |
| `handle` | - | `int` | 读取 `name` 参数与 `--force` 选项，调用生成器，返回退出码 |

#### Usage Example
```bash
java -jar app.jar artisan make:migration create_users_table
java -jar app.jar artisan make:migration add_status_to_users_table --force
```

### MakeCommandCommand
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.artisan.make`
- **Extends**: `ArtisanCommand`
- **Signature**: `make:command {name} {--force}`
- **Description**: 生成 ArtisanCommand 子类（对齐 Laravel `php artisan make:command`）。调用 `MakeGenerator.generateCommand()` 生成源文件，生成的类 `extends ArtisanCommand` 并包含 `signature()`/`description()`/`handle()` 模板。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `signature` | - | `String` | 返回 `make:command {name} {--force}` |
| `description` | - | `String` | 返回命令描述 |
| `setProperties` | `MakeCodeProperties properties` | `void` | 注入代码生成配置 |
| `handle` | - | `int` | 读取 `name` 参数与 `--force` 选项，调用生成器，返回退出码 |

#### Usage Example
```bash
java -jar app.jar artisan make:command SyncData
java -jar app.jar artisan make:command SyncDataCommand --force
```

### MakeEventCommand
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.artisan.make`
- **Extends**: `ArtisanCommand`
- **Signature**: `make:event {name} {--force}`
- **Description**: 生成 Event 类（对齐 Laravel `php artisan make:event`）。调用 `MakeGenerator.generateEvent()` 生成源文件，生成的类 `implements Event`。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `signature` | - | `String` | 返回 `make:event {name} {--force}` |
| `description` | - | `String` | 返回命令描述 |
| `setProperties` | `MakeCodeProperties properties` | `void` | 注入代码生成配置 |
| `handle` | - | `int` | 读取 `name` 参数与 `--force` 选项，调用生成器，返回退出码 |

#### Usage Example
```bash
java -jar app.jar artisan make:event UserRegistered
java -jar app.jar artisan make:event UserRegisteredEvent --force
```

### MakeListenerCommand
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.artisan.make`
- **Extends**: `ArtisanCommand`
- **Signature**: `make:listener {name} {--event=} {--force}`
- **Description**: 生成 Listener 类（对齐 Laravel `php artisan make:listener`）。调用 `MakeGenerator.generateListener()` 生成源文件，生成的类 `implements Listener<EventType>`。通过 `--event=` 选项指定监听的事件类型，未指定时使用占位类型 `YourEvent` 并输出提示。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `signature` | - | `String` | 返回 `make:listener {name} {--event=} {--force}` |
| `description` | - | `String` | 返回命令描述 |
| `setProperties` | `MakeCodeProperties properties` | `void` | 注入代码生成配置 |
| `handle` | - | `int` | 读取 `name`/`--event`/`--force`，调用生成器，返回退出码 |

#### Usage Example
```bash
# 关联指定事件
java -jar app.jar artisan make:listener SendWelcomeEmail --event=UserRegisteredEvent

# 不指定事件（使用占位类型）
java -jar app.jar artisan make:listener SendWelcomeEmail

# 强制覆盖
java -jar app.jar artisan make:listener SendWelcomeEmail --event=UserRegisteredEvent --force
```

### MakeAllCommand
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.artisan.make`
- **Extends**: `ArtisanCommand`
- **Signature**: `make:all {name} {--force}`
- **Description**: 一键生成全部（Controller + Middleware + Model + Migration + Command + Event + Listener），对齐 Laravel 批量生成场景。对每个子生成任务独立处理：单个文件已存在时记为「跳过」并继续后续生成，不中断整体流程；带 `--force` 时全部覆盖。Listener 自动关联到本次生成的同名 Event。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `signature` | - | `String` | 返回 `make:all {name} {--force}` |
| `description` | - | `String` | 返回命令描述 |
| `setProperties` | `MakeCodeProperties properties` | `void` | 注入代码生成配置 |
| `handle` | - | `int` | 读取 `name`/`--force`，依次调用 7 个生成方法，输出统计结果，有跳过返回 1 否则 0 |

#### Usage Example
```bash
java -jar app.jar artisan make:all User
java -jar app.jar artisan make:all User --force
```

输出示例：
```
===== make:all User =====

  [+] Controller created: com/example/app/controller/UserController.java
  [+] Middleware created: com/example/app/middleware/UserMiddleware.java
  [+] Model created: com/example/app/model/UserModel.java
  [+] Migration created: com/example/app/migration/Migration_2024_01_01_User.java
  [+] Command created: com/example/app/command/UserCommand.java
  [+] Event created: com/example/app/event/UserEvent.java
  [+] Listener created: com/example/app/listener/UserListener.java

===== 完成: 7 成功, 0 跳过 =====
```
