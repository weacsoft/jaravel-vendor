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
- **Annotations**: `@AutoConfiguration`, `@ConditionalOnClass(ArtisanApplication.class)`, `@ConditionalOnProperty(prefix = "jaravel.artisan", name = "enabled", havingValue = "true", matchIfMissing = true)`
- **Description**: Artisan 自动装配。创建 `ArtisanApplication` 和 `ArtisanRunner` Bean，并自动收集所有 `ArtisanCommand` 类型的 Bean 注册到 `ArtisanApplication`。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `artisanApplication` | `List<ArtisanCommand> commands` | `ArtisanApplication` | 创建 Artisan 应用 Bean，自动注入所有命令（`@Bean`, `@ConditionalOnMissingBean`） |
| `artisanRunner` | `ArtisanApplication artisanApplication` | `ArtisanRunner` | 创建 Artisan 运行器 Bean（`@Bean`, `@ConditionalOnMissingBean`） |

#### Usage Example
```yaml
# application.yml
jaravel:
  artisan:
    enabled: true
```
