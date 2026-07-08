# plugin-java-core

> 轻量级 Java 文件插件系统 —— 无需打包成 JAR，直接加载 `.java` 源文件动态编译执行。

`plugin-java-core` 是 Jaravel 体系下的一个轻量级插件模块。它直接读取 `.java` 源文件，使用 JDK 内置编译器在内存中完成编译与加载，支持热更新（重新编译所有 `.java` 文件完成更新）与动态路由注册。既可以作为 `plugin-jar-core` 的轻量替代方案，也可以作为 JAR 插件系统的范例插件使用。

- **artifactId**：`plugin-java-core`
- **包路径**：`com.weacsoft.jaravel.vendor.plugin.java`
- **定位**：Java 文件插件系统，动态编译 `.java` 文件、热更新、动态路由注册，可作为 JAR 插件的轻量替代

---

## 目录

- [核心特性](#核心特性)
- [依赖引入](#依赖引入)
- [配置](#配置)
- [工作原理](#工作原理)
- [核心 API](#核心-api)
- [路由注册模式](#路由注册模式)
- [插件开发指南](#插件开发指南)
- [热更新](#热更新)
- [字符串源码模式](#字符串源码模式)
- [与 JAR 插件系统的关系](#与-jar-插件系统的关系)
- [限制](#限制)
- [目录结构](#目录结构)

---

## 核心特性

- **动态编译**：使用 JDK 内置 `javax.tools.JavaCompiler` 编译 `.java` 文件，无需引入第三方编译器。
- **内存加载**：编译后的字节码存储在内存中（`Map<String, byte[]>`），无需落盘，由 `DynamicClassLoader` 直接加载。
- **字符串源码模式**：从内存中的 Java 源码字符串直接编译热加载，无需文件系统。
- **热更新**：修改 `.java` 文件后重新编译，刷新所有类，创建新的 `ClassLoader` 实现插件热重载。
- **动态路由**：自动扫描 `@PluginMapping` 注解注册路由，支持 `path`、`method`、`produces` 等属性。
- **双注册模式**：支持自动注册（`@PluginMapping`）与手动注册（`@PluginRoute`）两种路由注册模式，通过 `auto-register` 配置项切换。
- **共享注解**：复用 `plugin-jar-core` 的 `@PluginComponent` / `@PluginMapping` 注解，与 JAR 插件体系保持一致。
- **线程安全**：使用 `ReadWriteLock` 保护所有状态变更操作（注册、启用、禁用、重载），支持并发读。
- **Spring Boot AutoConfiguration**：通过 `PluginJavaAutoConfiguration` 开箱即用，引入依赖并配置即可启用。

---

## 依赖引入

在 `pom.xml` 中引入以下依赖：

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>plugin-java-core</artifactId>
    <version>0.1.1</version>
</dependency>
```

> **注意**：此模块依赖 `plugin-jar-core`（共享注解和注册器）。`plugin-java-core` 复用了 `plugin-jar-core` 的 `@PluginComponent` / `@PluginMapping` 注解，以及 `PluginBeanRegistrar` / `PluginRouteRegistrar` 注册器，因此运行环境中必须存在 `plugin-jar-core` 提供的 Bean。

---

## 配置

在 `application.yml` 中配置：

```yaml
jaravel:
  plugin-java:
    enabled: true              # 是否启用（默认 true）
    source-dir: plugins-java   # .java 文件插件源目录（默认 plugins-java）
    auto-scan: true            # 启动时自动扫描源目录并注册插件（默认 true）
    auto-register: true        # 是否自动注册插件路由（默认 true=自动注册, false=手动注册）
```

### 配置项说明

| 配置项 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `jaravel.plugin-java.enabled` | `boolean` | `true` | 是否启用 Java 文件插件系统 |
| `jaravel.plugin-java.source-dir` | `String` | `plugins-java` | `.java` 文件插件源目录（相对于工作目录） |
| `jaravel.plugin-java.auto-scan` | `boolean` | `true` | 启动时是否自动扫描源目录并注册插件 |
| `jaravel.plugin-java.auto-register` | `boolean` | `true` | 是否自动注册插件路由；`true`=自动注册 `@PluginMapping`，`false`=手动注册 |

### 自动装配条件

`PluginJavaAutoConfiguration` 在以下条件全部满足时生效：

- Web 应用环境（`@ConditionalOnWebApplication`）
- `jaravel.plugin-java.enabled=true`（默认 `true`，缺失时也生效）
- 存在 `PluginBeanRegistrar` Bean（来自 `plugin-jar-core`）

---

## 工作原理

`plugin-java-core` 的完整工作流程如下：

1. **扫描源目录**：扫描 `source-dir` 下的子目录，每个子目录作为一个插件（目录名即插件 ID）。
2. **读取源文件**：读取目录下所有 `.java` 文件，使用正则解析 `package` 声明和类名，得到类全限定名。
3. **内存编译**：使用 `javax.tools.JavaCompiler` 将所有 `.java` 文件编译到内存，通过 `MemoryJavaFileManager` 拦截 `.class` 输出，字节码存入 `Map<String, byte[]>`。
4. **类加载**：创建 `DynamicClassLoader`（继承 `URLClassLoader`），优先从内存字节码加载类，找不到时委托父 ClassLoader。
5. **注解扫描**：使用反射扫描 `@PluginComponent` 和 `@PluginMapping` 注解，提取组件类与路由映射信息。
6. **注册到容器**：通过 `PluginBeanRegistrar` 注册 Bean，通过 `PluginRouteRegistrar` 注册路由到 Spring 容器。
7. **热更新**：修改 `.java` 文件后，执行「禁用插件 → 重新编译 → 创建新 ClassLoader → 重新注册」流程完成热重载。

```
.java 文件 ──读取──▶ JavaSourceFile ──编译──▶ Map<String, byte[]> ──加载──▶ DynamicClassLoader
                                                                              │
                                                                              ▼
                                              @PluginComponent / @PluginMapping 反射扫描
                                                                              │
                                                                              ▼
                                              PluginBeanRegistrar / PluginRouteRegistrar 注册
```

---

## 核心 API

核心管理类为 `JavaFilePluginManager`，负责 `.java` 文件插件的完整生命周期管理。

### JavaFilePluginManager 方法列表

| 方法签名 | 说明 |
| --- | --- |
| `String registerPlugin(Path pluginDir)` | 从目录注册插件（编译 `.java` 文件），返回插件 ID（目录名） |
| `boolean enablePlugin(String pluginId)` | 启用插件（注册 Bean 和路由） |
| `boolean disablePlugin(String pluginId)` | 禁用插件（注销 Bean 和路由，关闭 ClassLoader） |
| `boolean reloadPlugin(String pluginId)` | 热重载插件（禁用 → 重新编译 → 启用） |
| `boolean hasChanges(String pluginId)` | 检查 `.java` 文件是否有变更（基于最后修改时间） |
| `void reloadAllChanged()` | 重载所有有变更的插件（供 WatchService 调用） |
| `List<JavaFilePluginInfo> getAllPlugins()` | 列出所有插件信息 |
| `JavaFilePluginInfo getPlugin(String pluginId)` | 获取指定插件信息，不存在返回 `null` |
| `Set<RouteInfo> getAvailableRoutes(String pluginId)` | 列出指定插件中所有可注册路由（含 `@PluginMapping` 与 `@PluginRoute` 标记的方法） |
| `boolean registerAvailableRoute(String pluginId, String path, String httpMethod)` | 手动注册指定插件中路径与 HTTP 方法匹配的可注册路由，成功返回 `true` |

### 插件状态机

`JavaFilePluginInfo.State` 定义了插件的三种状态：

| 状态 | 说明 |
| --- | --- |
| `LOADED` | 已加载（编译完成，但未启用） |
| `ENABLED` | 已启用（Bean 和路由已注册） |
| `DISABLED` | 已禁用（Bean 和路由已注销，ClassLoader 已关闭） |

状态流转：`LOADED` → `enablePlugin` → `ENABLED` → `disablePlugin` → `DISABLED`；`reloadPlugin` 会先禁用再重新编译启用。

### 使用示例

```java
@Autowired
private JavaFilePluginManager javaFilePluginManager;

// 注册并启用插件
String pluginId = javaFilePluginManager.registerPlugin(Path.of("plugins-java/my-plugin"));
javaFilePluginManager.enablePlugin(pluginId);

// 热重载
javaFilePluginManager.reloadPlugin("my-plugin");

// 禁用
javaFilePluginManager.disablePlugin("my-plugin");

// 查询所有插件
List<JavaFilePluginInfo> plugins = javaFilePluginManager.getAllPlugins();
```

---

## 路由注册模式

`plugin-java-core` 支持两种路由注册模式：**自动注册**与**手动注册**，通过配置项 `jaravel.plugin-java.auto-register` 切换。

### 配置项

```yaml
jaravel:
  plugin-java:
    auto-register: true   # 默认 true=自动注册, false=手动注册
```

### 两种模式

| 模式 | auto-register | @PluginMapping | @PluginRoute | 说明 |
|------|--------------|----------------|--------------|------|
| 自动注册 | `true`（默认） | 自动注册 | 列为可用 | 插件启用时自动注册 `@PluginMapping` 路由 |
| 手动注册 | `false` | 列为可用 | 列为可用 | 所有路由都需手动注册 |

### @PluginMapping vs @PluginRoute

- `@PluginMapping` —— 用于**自动注册模式**。插件启用时自动注册路由。
- `@PluginRoute` —— 用于**手动注册模式**。仅标记方法为"可注册路由"，不会自动注册，需要通过手动注册 API 注册后才会生效。

> 无论处于哪种模式，`@PluginMapping` 与 `@PluginRoute` 标记的方法都会被扫描并列入"可注册路由"集合，区别仅在于是否在插件启用时自动注册。

### 手动注册 API

在手动注册模式（`auto-register=false`）下，可以通过以下 API 列出并注册路由：

```java
@Autowired
private JavaFilePluginManager manager;

// 列出可注册路由
Set<RouteInfo> available = manager.getAvailableRoutes("my-plugin");

// 手动注册指定路由
boolean ok = manager.registerAvailableRoute("my-plugin", "/api/hello", "GET");
```

- `getAvailableRoutes(pluginId)`：返回该插件下所有被 `@PluginMapping` / `@PluginRoute` 标记的可注册路由信息。
- `registerAvailableRoute(pluginId, path, httpMethod)`：根据路径与 HTTP 方法匹配并注册对应的可注册路由，注册成功返回 `true`，未匹配到则返回 `false`。

---

## 插件开发指南

### 目录结构

每个插件是 `source-dir` 下的一个子目录，**目录名即插件 ID**：

```
plugins-java/
└── my-plugin/              # 目录名即插件 ID
    ├── HelloController.java
    └── UserService.java
```

### 编写 .java 文件

```java
package demo.greeting;

import com.weacsoft.jaravel.vendor.plugin.jar.annotation.HttpMethod;
import com.weacsoft.jaravel.vendor.plugin.jar.annotation.PluginComponent;
import com.weacsoft.jaravel.vendor.plugin.jar.annotation.PluginMapping;

@PluginComponent("demoGreetingService")
public class GreetingPlugin {

    @PluginMapping(path = "/api/plugin/greeting", method = HttpMethod.GET)
    public String greeting(String name) {
        return "Hello, " + (name != null ? name : "World") + "! (from java-file-plugin)";
    }

    @PluginMapping(path = "/api/plugin/time", method = HttpMethod.GET)
    public String time() {
        return "Current time: " + java.time.LocalDateTime.now().toString();
    }
}
```

### 自动注册与手动注册示例

下面示例同时使用 `@PluginMapping`（自动注册）与 `@PluginRoute`（手动注册）两种注解，适用于需要混合注册场景的插件开发：

```java
package demo.greeting;

import com.weacsoft.jaravel.vendor.plugin.jar.annotation.HttpMethod;
import com.weacsoft.jaravel.vendor.plugin.jar.annotation.PluginComponent;
import com.weacsoft.jaravel.vendor.plugin.jar.annotation.PluginMapping;
import com.weacsoft.jaravel.vendor.plugin.jar.annotation.PluginRoute;

@PluginComponent("demoGreetingService")
public class GreetingPlugin {

    // 自动注册路由（auto-register=true 时自动注册）
    @PluginMapping(path = "/api/plugin/greeting", method = HttpMethod.GET)
    public String greeting(String name) {
        return "Hello, " + name + "!";
    }

    // 可注册路由（需要手动注册）
    @PluginRoute(path = "/api/plugin/manual-greeting", method = HttpMethod.GET)
    public String manualGreeting(String name) {
        return "Manual Hello, " + name + "!";
    }
}
```

- 当 `auto-register=true`（默认）时，`greeting` 方法会在插件启用时自动注册为路由；`manualGreeting` 方法仅被列为"可注册路由"，需通过 `registerAvailableRoute` 手动注册后才能访问。
- 当 `auto-register=false` 时，所有路由（包括 `@PluginMapping` 标记的）都不会自动注册，均需通过手动注册 API 注册。

### 注意事项

- `@PluginMapping` 使用 `path` 属性（不是 `value`）。
- 同一目录下的所有 `.java` 文件会被编译到同一个 `ClassLoader`。
- `.java` 文件可以互相引用（同目录内的类）。
- 可以引用 `plugin-jar-core` 的注解类（在父 ClassLoader 中）。
- `@PluginComponent` 的 `value` 用作 Bean 名称；若未指定，则按类名首字母小写派生。
- 编译时通过 `-classpath` 传入当前 `java.class.path`，因此源文件可引用主程序的所有依赖。

---

## 热更新

### 手动热更新

调用 `reloadPlugin` 即可对单个插件执行热重载（禁用 → 重新编译 → 启用）：

```java
javaFilePluginManager.reloadPlugin("my-plugin");
```

### 自动检测变更

通过 `hasChanges` 检查文件变更（基于源文件的最后修改时间），通过 `reloadAllChanged` 重载所有有变更的插件：

```java
// 检查是否有变更
boolean hasChanges = javaFilePluginManager.hasChanges("my-plugin");

// 重载所有有变更的插件
javaFilePluginManager.reloadAllChanged();
```

可配合文件监听服务（`WatchService`）实现自动热更新，示例思路：

```java
// 伪代码：配合 WatchService 实现自动热更新
WatchService watchService = FileSystems.getDefault().newWatchService();
Path sourceDir = Path.of("plugins-java");
sourceDir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

new Thread(() -> {
    while (true) {
        WatchKey key = watchService.take();
        // 检测到变更后重载所有有变更的插件
        javaFilePluginManager.reloadAllChanged();
        key.reset();
    }
}).start();
```

---

## 字符串源码模式

除了从文件系统目录加载 `.java` 文件外，`plugin-java-core` 还支持**字符串源码模式**：直接将内存中的 Java 源码字符串交给编译器编译并热加载，整个过程不依赖文件系统。该模式适合从数据库、网络、配置中心等任意来源获取源码后编译热加载。

### API 方法

| 方法签名 | 说明 |
| --- | --- |
| `registerPluginFromSource(String pluginId, String sourceCode)` | 从单个源码字符串注册插件（便捷方法） |
| `registerPluginFromSource(String pluginId, Map<String, String> sources)` | 从多个源码字符串注册插件，Map 的 key 为文件名（如 `HelloController.java`），value 为源码内容 |
| `reloadPluginFromSource(String pluginId, String sourceCode)` | 从新的单个源码字符串热重载插件 |
| `reloadPluginFromSource(String pluginId, Map<String, String> sources)` | 从多个新的源码字符串热重载插件 |

> 注册成功后返回插件 ID（即传入的 `pluginId`），随后仍需调用 `enablePlugin` 启用插件；`reloadPluginFromSource` 会自动完成「禁用 → 重新编译 → 启用」的完整热重载流程。

### 代码示例

```java
// 从数据库获取源码字符串
String sourceCode = repository.findPluginSource("my-plugin");

// 直接编译并注册
pluginManager.registerPluginFromSource("my-plugin", sourceCode);
pluginManager.enablePlugin("my-plugin");

// 后续从数据库获取新版本源码，热重载
String newSourceCode = repository.findPluginSource("my-plugin");
pluginManager.reloadPluginFromSource("my-plugin", newSourceCode);
```

### 文件模式与字符串模式的区别

| 模式 | 注册方法 | 源码来源 | 变更检测 |
| --- | --- | --- | --- |
| 文件模式 | `registerPlugin(Path pluginDir)` | 文件系统目录下的 `.java` 文件 | 通过 `WatchService` / `hasChanges` 自动检测文件变更 |
| 字符串模式 | `registerPluginFromSource(...)` | 内存中的源码字符串（数据库、网络、配置中心等） | 无文件系统依赖，由调用方负责获取新版本源码并调用 `reloadPluginFromSource` |

- **文件模式**：`registerPlugin(Path)`，通过 WatchService 自动检测文件变更。
- **字符串模式**：`registerPluginFromSource`，无文件系统依赖，调用方负责获取源码。

两种模式在编译、加载、扫描、注册的后续流程完全一致，均复用 `DynamicJavaCompiler`、`DynamicClassLoader`、`JavaFileScanner` 与 `plugin-jar-core` 的注册器。通过 `JavaFilePluginInfo.getSourceMode()` 可区分插件当前的源码模式（`FILE` 或 `STRING`）。

---

## 与 JAR 插件系统的关系

`plugin-java-core` 与 `plugin-jar-core` 既独立又协同：

- **共享注解**：使用 `plugin-jar-core` 的 `@PluginComponent` / `@PluginMapping`，插件开发体验与 JAR 插件一致。
- **共享注册器**：复用 `plugin-jar-core` 的 `PluginBeanRegistrar` / `PluginRouteRegistrar`，Bean 和路由的注册逻辑完全一致。
- **可作为 JAR 插件**：`plugin-java-core` 本身可以打包为 JAR，作为 `plugin-jar-core` 的插件加载。
- **轻量替代**：对于简单业务，无需打包 JAR，直接使用 `.java` 文件即可快速开发和验证。

| 对比维度 | plugin-jar-core | plugin-java-core |
| --- | --- | --- |
| 插件形态 | 打包好的 JAR 文件 | `.java` 源文件目录 |
| 编译时机 | 预先编译 | 运行时动态编译 |
| 运行依赖 | JRE 即可 | 需要 JDK（`javax.tools.JavaCompiler`） |
| 适用场景 | 生产环境、正式分发 | 快速开发、原型验证、轻量业务 |

---

## 限制

- **编译需要 JDK**：由于使用 `javax.tools.JavaCompiler`（通过 `ToolProvider.getSystemJavaCompiler()` 获取），运行环境必须是 JDK 而非 JRE，否则会抛出 `IllegalStateException`。
- **import 依赖需在 classpath**：`.java` 文件中的 `import` 必须在父 ClassLoader 的 classpath 中可用，编译时通过 `java.class.path` 解析依赖。
- **不支持多模块编译**：每个插件目录独立编译，不同插件目录之间的类无法互相引用。
- **不支持增量编译**：热更新时重新编译目录下的所有 `.java` 文件，而非仅编译变更的文件。

---

## 目录结构

模块内的 Java 包和关键类如下：

```
com.weacsoft.jaravel.vendor.plugin.java
├── autoconfigure                      # Spring Boot 自动装配
│   ├── PluginJavaAutoConfiguration    # 自动装配类，创建 JavaFilePluginManager Bean 并自动扫描
│   └── PluginJavaProperties           # 配置属性类，前缀 jaravel.plugin-java
├── classloader                        # 动态类加载
│   └── DynamicClassLoader             # 动态类加载器，优先从内存字节码加载，支持 close 释放
├── compiler                           # 动态编译
│   └── DynamicJavaCompiler            # 动态 Java 编译器，编译 .java 到内存字节码
│       ├── JavaSourceFile             # 内部类：Java 源文件描述（类名、源码、文件名）
│       ├── SourceCodeJavaFileObject   # 内部类：内存中的源代码文件对象
│       ├── ClassFileJavaFileObject    # 内部类：内存中的 class 文件对象
│       └── MemoryJavaFileManager      # 内部类：内存文件管理器，拦截 .class 输出
├── manager                            # 插件管理
│   └── JavaFilePluginManager          # 核心管理器，负责插件注册、启用、禁用、热重载
├── model                              # 数据模型
│   └── JavaFilePluginInfo             # 插件信息模型（含 State 枚举：LOADED/ENABLED/DISABLED；SourceMode 枚举：FILE/STRING）
└── scanner                            # 注解扫描
    └── JavaFileScanner                # 反射扫描 @PluginComponent / @PluginMapping
        ├── ScanResult                 # 内部类：扫描结果（组件类集合 + 路由映射列表）
        └── RouteScanInfo              # 内部类：路由扫描信息（类名、方法名、路径、HTTP 方法、produces）
```

### 资源文件

```
src/main/resources/META-INF/spring/
└── org.springframework.boot.autoconfigure.AutoConfiguration.imports
    # 注册 PluginJavaAutoConfiguration 为 Spring Boot 自动装配类
```
