# plugin-java-core AI-API Reference

> Module: `plugin-java-core` | Package: `com.weacsoft.jaravel.vendor.plugin.java` | Version: 0.1.1

## Overview

plugin-java-core 模块提供了基于 `.java` 源文件的动态插件系统。与 `plugin-jar-core` 不同，本模块直接处理源码文件：`DynamicJavaCompiler` 使用 JDK 内置的 `javax.tools.JavaCompiler` 在运行时编译 `.java` 文件为字节码，`DynamicClassLoader` 加载编译后的类，`JavaFileScanner` 通过反射扫描 `@PluginComponent` 和 `@PluginMapping` 注解提取组件和路由信息。`JavaFilePluginManager` 管理完整的插件生命周期（注册、编译、启用、禁用、重载），复用 `plugin-jar-core` 的 `PluginBeanRegistrar` 和 `PluginRouteRegistrar` 完成与主应用的集成。适用于开发阶段快速迭代、无需打包 JAR 的场景。

## Classes & Interfaces

### JavaFilePluginManager
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.java.manager`
- **Description**: Java 文件插件管理器，负责 `.java` 源文件插件的完整生命周期管理。注册时扫描源目录下的 `.java` 文件，通过 `DynamicJavaCompiler` 编译为字节码，通过 `DynamicClassLoader` 加载，通过 `JavaFileScanner` 扫描组件和路由。启用时复用 `plugin-jar-core` 的 `PluginBeanRegistrar` 和 `PluginRouteRegistrar` 注册到主应用。支持文件变更检测和热重载。除文件模式外，还提供字符串源码模式（`registerPluginFromSource` / `reloadPluginFromSource`），可直接从内存中的源码字符串编译加载，无需文件系统依赖。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `JavaFilePluginManager` | `Path sourceDir, PluginBeanRegistrar beanRegistrar, PluginRouteRegistrar routeRegistrar, boolean autoRegister` | - | 构造插件管理器，指定源目录和注册器 |
| `registerPlugin` | `Path pluginDir` | `String` | 注册插件（扫描 .java 文件并编译），返回插件 ID（目录名） |
| `registerPluginFromSource` | `String pluginId, Map<String, String> sources` | `String` | 从源码字符串映射注册插件（key 为文件名，value 为源码内容），返回插件 ID |
| `registerPluginFromSource` | `String pluginId, String sourceCode` | `String` | 从单个源码字符串注册插件（便捷方法），返回插件 ID |
| `reloadPluginFromSource` | `String pluginId, Map<String, String> sources` | `boolean` | 从源码字符串热重载插件（禁用 → 重新编译 → 启用） |
| `reloadPluginFromSource` | `String pluginId, String sourceCode` | `boolean` | 从单个源码字符串热重载插件（便捷方法） |
| `enablePlugin` | `String pluginId` | `boolean` | 启用插件（注册 Bean 和路由） |
| `disablePlugin` | `String pluginId` | `boolean` | 禁用插件（注销 Bean 和路由，关闭 ClassLoader） |
| `reloadPlugin` | `String pluginId` | `boolean` | 重载插件（重新编译并注册） |
| `unregisterPlugin` | `String pluginId` | `boolean` | 注销插件（禁用并移除所有记录） |
| `getPlugin` | `String pluginId` | `JavaFilePluginInfo` | 获取插件信息 |
| `getPlugins` | - | `List<JavaFilePluginInfo>` | 获取所有已注册插件信息 |
| `getEnabledPlugins` | - | `List<JavaFilePluginInfo>` | 获取所有已启用插件信息 |
| `isRegistered` | `String pluginId` | `boolean` | 检查插件是否已注册 |
| `isEnabled` | `String pluginId` | `boolean` | 检查插件是否已启用 |
| `checkForChanges` | - | `List<String>` | 检测源文件变更，返回需要重载的插件 ID 列表 |

#### Usage Example
```java
@Autowired
private JavaFilePluginManager pluginManager;

// 注册插件目录（目录下包含 .java 文件）
String pluginId = pluginManager.registerPlugin(Paths.get("plugins-java/my-plugin"));

// 启用插件
pluginManager.enablePlugin(pluginId);

// 检测文件变更并热重载
List<String> changed = pluginManager.checkForChanges();
for (String id : changed) {
    pluginManager.reloadPlugin(id);
}

// 禁用并注销
pluginManager.disablePlugin(pluginId);
pluginManager.unregisterPlugin(pluginId);
```

### DynamicJavaCompiler
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.java.compiler`
- **Description**: 动态 Java 编译器。使用 JDK 内置的 `javax.tools.JavaCompiler`（通常为 `javac`）在运行时编译 `.java` 源文件为字节码。支持指定编译选项（如 classpath、输出目录）和诊断信息收集。编译结果为内存中的字节码或输出到指定目录。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `DynamicJavaCompiler` | - | - | 构造动态编译器，使用系统默认 JavaCompiler |
| `compile` | `Path sourceDir, Path outputDir` | `CompilationResult` | 编译目录下所有 .java 文件，输出到指定目录 |
| `compile` | `Map<String, String> sourceFiles, Path outputDir` | `CompilationResult` | 编译指定的源文件集合（文件名 -> 源码内容） |
| `isAvailable` | - | `boolean` | 检查 JavaCompiler 是否可用（JDK 环境） |
| `addClasspath` | `String path` | `void` | 添加编译 classpath |
| `setCompilerOptions` | `List<String> options` | `void` | 设置编译选项 |

#### Inner Class: DynamicJavaCompiler.CompilationResult
- **Type**: class
- **Description**: 编译结果，包含成功标志、编译的类名集合和诊断信息。

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `isSuccess` | - | `boolean` | 编译是否成功 |
| `getClassNames` | - | `Set<String>` | 获取成功编译的类全限定名集合 |
| `getDiagnostics` | - | `List<String>` | 获取编译诊断信息列表 |
| `getOutputDir` | - | `Path` | 获取编译输出目录 |

#### Usage Example
```java
DynamicJavaCompiler compiler = new DynamicJavaCompiler();
compiler.addClasspath("/app/classes");
compiler.addClasspath(System.getProperty("java.class.path"));

CompilationResult result = compiler.compile(
    Paths.get("plugins-java/my-plugin"),
    Paths.get("plugins-java/my-plugin/target")
);

if (result.isSuccess()) {
    System.out.println("Compiled classes: " + result.getClassNames());
} else {
    result.getDiagnostics().forEach(System.err::println);
}
```

### DynamicClassLoader
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.java.classloader`
- **Extends**: `java.net.URLClassLoader`
- **Description**: 动态类加载器。加载 `DynamicJavaCompiler` 编译输出的 `.class` 文件或目录。每个 Java 文件插件对应一个独立的 `DynamicClassLoader`，实现类隔离。支持从目录和内存中加载字节码。卸载时调用 `close()` 释放资源。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `DynamicClassLoader` | `URL[] urls, ClassLoader parent` | - | 构造动态类加载器，指定字节码目录 URL 和父加载器 |
| `DynamicClassLoader` | `Path classDir, ClassLoader parent` | - | 构造动态类加载器，指定字节码目录路径 |
| `loadClass` | `String name` | `Class<?>` | 加载类（重写，先自身后父类） |
| `addClassDir` | `Path dir` | `void` | 动态添加类文件目录 |
| `defineClass` | `String name, byte[] bytecode` | `Class<?>` | 从内存字节码定义类 |
| `close` | - | `void` | 关闭类加载器，释放资源 |

#### Usage Example
```java
DynamicClassLoader classLoader = new DynamicClassLoader(
    Paths.get("plugins-java/my-plugin/target"),
    Thread.currentThread().getContextClassLoader()
);

Class<?> controllerClass = classLoader.loadClass("com.example.MyController");
Object instance = controllerClass.getDeclaredConstructor().newInstance();
```

### JavaFileScanner
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.java.scanner`
- **Description**: Java 文件插件扫描器。由于 `.java` 文件已编译为字节码，本扫描器直接使用反射（而非 ASM）扫描编译后的类，查找 `@PluginComponent` 和 `@PluginMapping` 注解。使用 `Class.forName(className, false, classLoader)` 加载类（不初始化，避免触发静态代码块）。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `scan` | `ClassLoader classLoader, Set<String> classNames` | `ScanResult` | 扫描编译后的类，查找插件组件和路由映射 |

#### Inner Class: JavaFileScanner.ScanResult
- **Type**: class
- **Description**: 扫描结果，包含组件类名集合、路由映射列表和可注册路由列表。

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `getComponentClasses` | - | `Set<String>` | 获取带 @PluginComponent 的类全限定名集合 |
| `getRouteMappings` | - | `List<RouteScanInfo>` | 获取路由映射列表（来自 @PluginMapping，自动注册） |
| `getAvailableRouteMappings` | - | `List<RouteScanInfo>` | 获取可注册路由列表（来自 @PluginRoute，手动注册） |

#### Inner Class: JavaFileScanner.RouteScanInfo
- **Type**: class
- **Description**: 路由扫描信息，从 @PluginMapping 或 @PluginRoute 注解中提取的路由元数据。

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `getClassName` | - | `String` | 获取控制器类全限定名 |
| `getMethodName` | - | `String` | 获取处理方法名 |
| `getPath` | - | `String` | 获取路由路径 |
| `getHttpMethod` | - | `HttpMethod` | 获取 HTTP 方法 |
| `getProduces` | - | `String` | 获取响应内容类型 |

#### Usage Example
```java
JavaFileScanner scanner = new JavaFileScanner();
JavaFileScanner.ScanResult result = scanner.scan(classLoader, classNames);

// 获取组件类
Set<String> components = result.getComponentClasses();

// 获取路由映射
for (JavaFileScanner.RouteScanInfo route : result.getRouteMappings()) {
    System.out.println(route.getPath() + " -> " + route.getClassName() + "." + route.getMethodName());
}
```

### JavaFilePluginInfo
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.java.model`
- **Description**: Java 文件插件信息模型。描述一个 `.java` 文件插件的完整元数据，包括源文件路径、源码模式、组件类、路由映射、已注册 Bean 名称、状态和错误信息等。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `getPluginId` | - | `String` | 获取插件 ID（目录名） |
| `setPluginId` | `String pluginId` | `void` | 设置插件 ID |
| `getSourceDir` | - | `String` | 获取源目录路径 |
| `setSourceDir` | `String sourceDir` | `void` | 设置源目录路径 |
| `getSourceMode` | - | `SourceMode` | 获取源码模式（`FILE` 文件模式 / `STRING` 字符串模式），默认 `FILE` |
| `setSourceMode` | `SourceMode sourceMode` | `void` | 设置源码模式 |
| `getState` | - | `State` | 获取插件状态 |
| `setState` | `State state` | `void` | 设置插件状态 |
| `getSourceFiles` | - | `Set<String>` | 获取 .java 源文件路径集合 |
| `setSourceFiles` | `Set<String> sourceFiles` | `void` | 设置源文件集合 |
| `getComponentClasses` | - | `Set<String>` | 获取组件类全限定名集合 |
| `setComponentClasses` | `Set<String> componentClasses` | `void` | 设置组件类集合 |
| `getRouteMappings` | - | `Set<RouteInfo>` | 获取路由映射集合 |
| `setRouteMappings` | `Set<RouteInfo> routeMappings` | `void` | 设置路由映射集合 |
| `getAvailableRoutes` | - | `Set<RouteInfo>` | 获取可注册但未自动注册的路由集合 |
| `setAvailableRoutes` | `Set<RouteInfo> availableRoutes` | `void` | 设置可注册路由集合 |
| `getRegisteredBeanNames` | - | `Set<String>` | 获取已注册 Bean 名称集合 |
| `setRegisteredBeanNames` | `Set<String> registeredBeanNames` | `void` | 设置已注册 Bean 名称 |
| `getErrorMessage` | - | `String` | 获取错误信息 |
| `setErrorMessage` | `String errorMessage` | `void` | 设置错误信息 |
| `getLastModified` | - | `long` | 获取最后修改时间戳 |
| `setLastModified` | `long lastModified` | `void` | 设置最后修改时间戳 |

#### Inner Enum: JavaFilePluginInfo.SourceMode
- **Type**: enum
- **Description**: 插件源码模式，标识插件源码来自文件系统还是内存字符串。`registerPlugin` 注册的插件为 `FILE` 模式，`registerPluginFromSource` 注册的插件为 `STRING` 模式。默认值为 `FILE`。
- **Values**:
  - `FILE`：文件模式，从文件系统目录读取 `.java` 源文件编译加载
  - `STRING`：字符串模式，从内存中的源码字符串直接编译加载，无文件系统依赖

#### Inner Enum: JavaFilePluginInfo.State
- **Type**: enum
- **Values**: `LOADED`（已加载，编译完成但未启用）, `ENABLED`（已启用，Bean 和路由已注册）, `DISABLED`（已禁用，Bean 和路由已注销，ClassLoader 已关闭）

### PluginJavaProperties
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.java.autoconfigure`
- **Annotations**: `@ConfigurationProperties(prefix = "jaravel.plugin-java")`
- **Description**: Java 文件插件配置属性，前缀 `jaravel.plugin-java`。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `isEnabled` | - | `boolean` | 是否启用 Java 文件插件系统，默认 true |
| `setEnabled` | `boolean enabled` | `void` | 设置是否启用 |
| `getSourceDir` | - | `String` | 获取 .java 文件插件源目录，默认 `plugins-java` |
| `setSourceDir` | `String sourceDir` | `void` | 设置源目录 |
| `isAutoScan` | - | `boolean` | 是否启动时自动扫描源目录并注册插件，默认 true |
| `setAutoScan` | `boolean autoScan` | `void` | 设置是否自动扫描 |
| `isAutoRegister` | - | `boolean` | 是否自动注册插件路由（true=自动注册 @PluginMapping，false=手动注册），默认 true |
| `setAutoRegister` | `boolean autoRegister` | `void` | 设置是否自动注册 |

#### Usage Example
```yaml
# application.yml
jaravel:
  plugin-java:
    enabled: true
    source-dir: plugins-java
    auto-scan: true
    auto-register: true
```

### PluginJavaAutoConfiguration
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.java.autoconfigure`
- **Annotations**: `@AutoConfiguration`, `@ConditionalOnWebApplication`, `@EnableConfigurationProperties(PluginJavaProperties.class)`, `@ConditionalOnProperty(prefix = "jaravel.plugin-java", name = "enabled", havingValue = "true", matchIfMissing = true)`, `@ConditionalOnBean(PluginBeanRegistrar.class)`
- **Description**: Java 文件插件自动装配。当 Web 应用环境且 `PluginBeanRegistrar`（来自 plugin-jar-core）存在时，创建 `JavaFilePluginManager` Bean。若 `autoScan=true`，启动时扫描源目录下的所有子目录，每个子目录作为一个插件注册并启用。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `javaFilePluginManager` | `PluginJavaProperties properties, PluginBeanRegistrar beanRegistrar, PluginRouteRegistrar routeRegistrar` | `JavaFilePluginManager` | 创建 Java 文件插件管理器 Bean（`@Bean`, `@ConditionalOnMissingBean`） |

#### Usage Example
```java
// 自动装配后，插件管理器自动创建
// 若 autoScan=true，启动时自动扫描 plugins-java/ 目录下的子目录
// 每个子目录作为一个插件，自动编译、注册并启用

// 目录结构示例：
// plugins-java/
//   my-plugin/
//     UserController.java    (标注 @PluginComponent, @PluginMapping)
//     UserService.java       (标注 @PluginComponent)
//   another-plugin/
//     HelloController.java   (标注 @PluginComponent, @PluginMapping)
```

```java
// 插件源码示例 (plugins-java/my-plugin/UserController.java)
@PluginComponent("userController")
public class UserController {

    @PluginMapping(path = "/api/users/{id}", method = HttpMethod.GET)
    public Map<String, Object> getUser(Long id) {
        return Map.of("id", id, "name", "John Doe");
    }

    @PluginMapping(path = "/api/users", method = HttpMethod.POST)
    public Map<String, Object> createUser(String name) {
        return Map.of("success", true, "name", name);
    }
}
```
