# plugin-jar-core AI-API Reference

> Module: `plugin-jar-core` | Package: `com.weacsoft.jaravel.vendor.plugin.jar` | Version: 0.1.1

## Overview

plugin-jar-core 模块是 JAR 插件系统的核心，提供 JAR 包形式插件的热加载、卸载、启用和禁用能力。插件通过独立的 `PluginClassLoader` 加载（与主应用类加载器隔离），共享依赖通过 `SharedClassLoader` 暴露。插件内使用 `@PluginComponent` 标注组件类、`@PluginMapping` 标注路由方法，加载时由 `PluginBeanRegistrar` 注册为 Spring Bean、`PluginRouteRegistrar` 注册到路由系统。插件元数据通过 `MetadataPersistence` 接口持久化（默认 JSON 文件实现），支持重启后恢复插件状态。

## Classes & Interfaces

### HotPluginManager
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.manager`
- **Description**: 插件热管理器，负责 JAR 插件的完整生命周期管理。加载时创建 `PluginClassLoader`，通过 `SharedDependencyScanner` 扫描共享依赖，通过 `PluginIntegration` 集成到主应用（注册 Bean、路由）。卸载时关闭 ClassLoader、注销 Bean 和路由。支持运行时动态加载/卸载，无需重启应用。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `HotPluginManager` | `PluginIntegration integration, MetadataPersistence persistence, PluginJarProperties properties` | - | 构造插件管理器 |
| `load` | `Path jarPath` | `String` | 加载 JAR 插件，返回插件 ID（JAR 文件名） |
| `unload` | `String pluginId` | `boolean` | 卸载指定插件，释放 ClassLoader 和资源 |
| `enable` | `String pluginId` | `boolean` | 启用插件（注册 Bean 和路由） |
| `disable` | `String pluginId` | `boolean` | 禁用插件（注销 Bean 和路由，保留 ClassLoader） |
| `reload` | `String pluginId` | `boolean` | 重载插件（先卸载再加载） |
| `reloadPluginFromBytes` | `String pluginId, byte[] jarBytes` | `boolean` | 从字节数组热重载插件（先禁用旧版本，再用新字节数组重新加载并启用，保持 pluginId 不变） |
| `getPlugin` | `String pluginId` | `PluginInfo` | 获取插件信息 |
| `getPlugins` | - | `List<PluginInfo>` | 获取所有已加载插件信息 |
| `getEnabledPlugins` | - | `List<PluginInfo>` | 获取所有已启用插件信息 |
| `isLoaded` | `String pluginId` | `boolean` | 检查插件是否已加载 |
| `isEnabled` | `String pluginId` | `boolean` | 检查插件是否已启用 |

#### Usage Example
```java
@Autowired
private HotPluginManager pluginManager;

// 加载插件
String pluginId = pluginManager.load(Paths.get("/plugins/my-plugin-1.0.0.jar"));

// 启用插件（注册 Bean 和路由）
pluginManager.enable(pluginId);

// 运行时重载
pluginManager.reload(pluginId);

// 卸载插件
pluginManager.unload(pluginId);
```

### PluginIntegration
- **Type**: interface
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.integration`
- **Description**: 插件集成接口，定义插件与主应用集成的回调方法。由 `DefaultPluginIntegration` 提供默认实现，业务方可自定义实现以扩展集成行为。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `onEnable` | `PluginInfo pluginInfo, ClassLoader classLoader` | `void` | 插件启用时回调，注册 Bean 和路由 |
| `onDisable` | `PluginInfo pluginInfo` | `void` | 插件禁用时回调，注销 Bean 和路由 |
| `onUnload` | `PluginInfo pluginInfo` | `void` | 插件卸载时回调，清理资源 |

#### Usage Example
```java
public class CustomPluginIntegration implements PluginIntegration {
    @Override
    public void onEnable(PluginInfo info, ClassLoader classLoader) {
        // 自定义启用逻辑
    }
    @Override
    public void onDisable(PluginInfo info) {
        // 自定义禁用逻辑
    }
    @Override
    public void onUnload(PluginInfo info) {
        // 自定义卸载逻辑
    }
}
```

### DefaultPluginIntegration
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.integration`
- **Implements**: `com.weacsoft.jaravel.vendor.plugin.jar.integration.PluginIntegration`
- **Description**: 默认插件集成实现。`onEnable` 时通过 `PluginBeanRegistrar` 注册插件组件为 Spring Bean，通过 `PluginRouteRegistrar` 注册路由到路由系统；`onDisable` 时反向注销。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `onEnable` | `PluginInfo pluginInfo, ClassLoader classLoader` | `void` | 注册 Bean 和路由 |
| `onDisable` | `PluginInfo pluginInfo` | `void` | 注销 Bean 和路由 |
| `onUnload` | `PluginInfo pluginInfo` | `void` | 清理插件资源 |

### PluginBeanRegistrar
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.registrar`
- **Description**: 插件 Bean 注册器。扫描插件 JAR 中标注 `@PluginComponent` 的类，通过 `DefaultListableBeanFactory.registerSingleton()` 注册为 Spring Bean。支持注销已注册的 Bean。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `registerBeans` | `PluginInfo pluginInfo, ClassLoader classLoader` | `Set<String>` | 注册插件组件 Bean，返回已注册的 Bean 名称集合 |
| `unregisterBeans` | `PluginInfo pluginInfo` | `void` | 注销插件的所有 Bean |
| `getRegisteredBeanNames` | `String pluginId` | `Set<String>` | 获取插件已注册的 Bean 名称集合 |

#### Usage Example
```java
@Autowired
private PluginBeanRegistrar beanRegistrar;

Set<String> beanNames = beanRegistrar.registerBeans(pluginInfo, classLoader);
// ... 使用后注销
beanRegistrar.unregisterBeans(pluginInfo);
```

### PluginRouteRegistrar
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.registrar`
- **Description**: 插件路由注册器。扫描插件组件类中标注 `@PluginMapping` 的方法，提取路由信息（路径、HTTP 方法、produces），注册到框架路由系统。支持注销已注册的路由。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `registerRoutes` | `PluginInfo pluginInfo, ClassLoader classLoader` | `Set<RouteInfo>` | 注册插件路由，返回已注册的路由信息集合 |
| `unregisterRoutes` | `PluginInfo pluginInfo` | `void` | 注销插件的所有路由 |
| `getRegisteredRoutes` | `String pluginId` | `Set<RouteInfo>` | 获取插件已注册的路由信息集合 |
| `registerRoute` | `RouteInfo routeInfo, Object handler, Method method` | `void` | 注册单个路由 |
| `unregisterRoute` | `RouteInfo routeInfo` | `void` | 注销单个路由 |

#### Usage Example
```java
@Autowired
private PluginRouteRegistrar routeRegistrar;

Set<RouteInfo> routes = routeRegistrar.registerRoutes(pluginInfo, classLoader);
// ... 使用后注销
routeRegistrar.unregisterRoutes(pluginInfo);
```

### PluginRouteHandler
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.registrar`
- **Description**: 插件路由处理器。封装插件路由的请求处理逻辑，持有目标 Bean 实例和 Method，通过反射调用处理方法并返回结果。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `PluginRouteHandler` | `Object bean, Method method` | - | 构造路由处理器 |
| `handle` | `Request request` | `Object` | 处理请求，反射调用目标方法 |
| `getBean` | - | `Object` | 获取目标 Bean 实例 |
| `getMethod` | - | `Method` | 获取目标方法 |

### PluginInfo
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.model`
- **Description**: 插件信息模型。封装插件 ID、JAR 路径、版本、状态、组件类名、路由信息、已注册 Bean 名称等元数据。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `getPluginId` | - | `String` | 获取插件 ID |
| `setPluginId` | `String pluginId` | `void` | 设置插件 ID |
| `getJarPath` | - | `String` | 获取 JAR 文件路径 |
| `setJarPath` | `String jarPath` | `void` | 设置 JAR 文件路径 |
| `getVersion` | - | `String` | 获取插件版本 |
| `setVersion` | `String version` | `void` | 设置插件版本 |
| `getState` | - | `State` | 获取插件状态（LOADED/ENABLED/DISABLED） |
| `setState` | `State state` | `void` | 设置插件状态 |
| `getComponentClasses` | - | `Set<String>` | 获取组件类全限定名集合 |
| `setComponentClasses` | `Set<String> componentClasses` | `void` | 设置组件类集合 |
| `getRouteMappings` | - | `Set<RouteInfo>` | 获取路由映射集合 |
| `setRouteMappings` | `Set<RouteInfo> routeMappings` | `void` | 设置路由映射集合 |
| `getRegisteredBeanNames` | - | `Set<String>` | 获取已注册 Bean 名称集合 |
| `setRegisteredBeanNames` | `Set<String> registeredBeanNames` | `void` | 设置已注册 Bean 名称 |
| `getErrorMessage` | - | `String` | 获取错误信息 |
| `setErrorMessage` | `String errorMessage` | `void` | 设置错误信息 |
| `getLastModified` | - | `long` | 获取最后修改时间戳 |
| `setLastModified` | `long lastModified` | `void` | 设置最后修改时间戳 |

#### Inner Enum: PluginInfo.State
- **Type**: enum
- **Values**: `LOADED`（已加载）, `ENABLED`（已启用）, `DISABLED`（已禁用）

### RouteInfo
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.model`
- **Description**: 路由信息模型。封装路由路径、HTTP 方法、控制器类名、方法名和 produces 内容类型。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `getClassName` | - | `String` | 获取控制器类全限定名 |
| `getMethodName` | - | `String` | 获取处理方法名 |
| `getPath` | - | `String` | 获取路由路径 |
| `getHttpMethod` | - | `HttpMethod` | 获取 HTTP 方法 |
| `getProduces` | - | `String` | 获取响应内容类型 |

### PluginClassLoader
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.classloader`
- **Extends**: `java.net.URLClassLoader`
- **Description**: 插件类加载器。每个插件 JAR 对应一个独立的 `PluginClassLoader`，实现类隔离。加载策略：先尝试从共享类加载器加载（共享依赖），再从插件 JAR 自身加载（插件私有类），最后委托父类加载器（JDK 类）。卸载时调用 `close()` 释放资源。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `PluginClassLoader` | `URL[] urls, ClassLoader parent, SharedClassLoader sharedClassLoader` | - | 构造插件类加载器 |
| `loadClass` | `String name` | `Class<?>` | 加载类（重写，先共享后私有） |
| `close` | - | `void` | 关闭类加载器，释放 JAR 文件句柄 |

### SharedClassLoader
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.classloader`
- **Extends**: `java.net.URLClassLoader`
- **Description**: 共享类加载器。加载所有插件共享的依赖 JAR（如 Spring、Jackson 等），供所有 `PluginClassLoader` 委托加载，避免每个插件重复加载共享依赖。通过 `SharedDependencyScanner` 扫描确定共享 JAR 列表。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `SharedClassLoader` | `URL[] sharedUrls, ClassLoader parent` | - | 构造共享类加载器 |
| `addURL` | `URL url` | `void` | 动态添加共享 JAR URL |
| `getSharedUrls` | - | `List<URL>` | 获取所有共享 JAR URL |

### SharedDependencyScanner
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.scanner`
- **Description**: 共享依赖扫描器。分析插件 JAR 的 MANIFEST.MF 或 pom 信息，识别插件声明的共享依赖，与主应用已有依赖取交集，确定哪些依赖应由 `SharedClassLoader` 加载。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `scan` | `Path pluginJar` | `Set<String>` | 扫描插件 JAR 的共享依赖，返回共享依赖坐标集合 |
| `scan` | `byte[] jarBytes, Set<String> sharedPackagePrefixes` | `ScanResult` | 从字节数组扫描 JAR（静态方法）。使用 `JarInputStream` 从内存读取，无需文件系统 |
| `getSharedJars` | - | `List<Path>` | 获取所有共享依赖 JAR 路径 |
| `isShared` | `String className` | `boolean` | 检查指定类是否属于共享依赖 |

### MetadataPersistence
- **Type**: interface
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.persistence`
- **Description**: 插件元数据持久化接口。定义插件信息的保存、加载、删除方法，支持不同存储后端实现（JSON 文件、数据库等）。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `save` | `PluginInfo pluginInfo` | `void` | 保存插件元数据 |
| `load` | `String pluginId` | `PluginInfo` | 加载插件元数据，不存在返回 null |
| `loadAll` | - | `List<PluginInfo>` | 加载所有插件元数据 |
| `delete` | `String pluginId` | `void` | 删除插件元数据 |
| `exists` | `String pluginId` | `boolean` | 检查插件元数据是否存在 |

#### Usage Example
```java
MetadataPersistence persistence = new JsonMetadataPersistence(Paths.get("plugins/metadata"));
persistence.save(pluginInfo);
PluginInfo loaded = persistence.load("my-plugin");
```

### JsonMetadataPersistence
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.persistence`
- **Implements**: `com.weacsoft.jaravel.vendor.plugin.jar.persistence.MetadataPersistence`
- **Description**: 基于 JSON 文件的元数据持久化实现。每个插件的元数据保存为 `{metadataDir}/{pluginId}.json` 文件，通过 Jackson `ObjectMapper` 序列化/反序列化。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `JsonMetadataPersistence` | `Path metadataDir` | - | 构造 JSON 持久化器，指定元数据目录 |
| `save` | `PluginInfo pluginInfo` | `void` | 保存为 `{pluginId}.json` 文件 |
| `load` | `String pluginId` | `PluginInfo` | 从 JSON 文件加载 |
| `loadAll` | - | `List<PluginInfo>` | 加载目录下所有 `.json` 文件 |
| `delete` | `String pluginId` | `void` | 删除 JSON 文件 |
| `exists` | `String pluginId` | `boolean` | 检查 JSON 文件是否存在 |

### PluginJarProperties
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.autoconfigure`
- **Annotations**: `@ConfigurationProperties(prefix = "jaravel.plugin-jar")`
- **Description**: JAR 插件配置属性，前缀 `jaravel.plugin-jar`。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `isEnabled` | - | `boolean` | 是否启用 JAR 插件系统，默认 true |
| `setEnabled` | `boolean enabled` | `void` | 设置是否启用 |
| `getPluginDir` | - | `String` | 获取插件目录，默认 `plugins` |
| `setPluginDir` | `String pluginDir` | `void` | 设置插件目录 |
| `getMetadataDir` | - | `String` | 获取元数据目录，默认 `plugins/metadata` |
| `setMetadataDir` | `String metadataDir` | `void` | 设置元数据目录 |
| `isAutoScan` | - | `boolean` | 是否启动时自动扫描插件目录，默认 true |
| `setAutoScan` | `boolean autoScan` | `void` | 设置是否自动扫描 |
| `isAutoEnable` | - | `boolean` | 是否自动启用已加载的插件，默认 true |
| `setAutoEnable` | `boolean autoEnable` | `void` | 设置是否自动启用 |
| `getSharedDeps` | - | `List<String>` | 获取共享依赖列表 |
| `setSharedDeps` | `List<String> sharedDeps` | `void` | 设置共享依赖列表 |

#### Usage Example
```yaml
# application.yml
jaravel:
  plugin-jar:
    enabled: true
    plugin-dir: plugins
    metadata-dir: plugins/metadata
    auto-scan: true
    auto-enable: true
    shared-deps:
      - org.springframework:spring-context
      - com.fasterxml.jackson.core:jackson-databind
```

### PluginJarAutoConfiguration
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.autoconfigure`
- **Annotations**: `@AutoConfiguration`, `@ConditionalOnWebApplication`, `@EnableConfigurationProperties(PluginJarProperties.class)`, `@ConditionalOnProperty(prefix = "jaravel.plugin-jar", name = "enabled", havingValue = "true", matchIfMissing = true)`
- **Description**: JAR 插件自动装配。创建 `PluginBeanRegistrar`、`PluginRouteRegistrar`、`SharedDependencyScanner`、`MetadataPersistence`、`PluginIntegration`、`HotPluginManager` Bean。若 `autoScan=true`，启动时扫描插件目录并自动加载/启用插件。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `pluginBeanRegistrar` | `ConfigurableListableBeanFactory beanFactory` | `PluginBeanRegistrar` | 创建 Bean 注册器（`@Bean`, `@ConditionalOnMissingBean`） |
| `pluginRouteRegistrar` | `Router router` | `PluginRouteRegistrar` | 创建路由注册器（`@Bean`, `@ConditionalOnMissingBean`） |
| `sharedDependencyScanner` | - | `SharedDependencyScanner` | 创建共享依赖扫描器（`@Bean`, `@ConditionalOnMissingBean`） |
| `metadataPersistence` | `PluginJarProperties properties` | `MetadataPersistence` | 创建 JSON 元数据持久化器（`@Bean`, `@ConditionalOnMissingBean`） |
| `pluginIntegration` | `PluginBeanRegistrar beanRegistrar, PluginRouteRegistrar routeRegistrar` | `PluginIntegration` | 创建默认插件集成器（`@Bean`, `@ConditionalOnMissingBean`） |
| `hotPluginManager` | `PluginIntegration integration, MetadataPersistence persistence, PluginJarProperties properties` | `HotPluginManager` | 创建插件管理器（`@Bean`, `@ConditionalOnMissingBean`） |

## Annotations

### @Application
- **Type**: annotation
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.annotation`
- **Target**: `TYPE`
- **Retention**: `RUNTIME`
- **Description**: 标注插件主应用类。插件 JAR 中标注此注解的类作为插件入口，加载时被识别为插件主类。可指定插件 ID 和版本。

#### Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `id` | `String` | `""` | 插件 ID，为空时使用 JAR 文件名 |
| `version` | `String` | `"1.0.0"` | 插件版本 |

#### Usage Example
```java
@Application(id = "my-plugin", version = "1.0.0")
public class MyPluginApplication {
    // 插件主类
}
```

### @PluginComponent
- **Type**: annotation
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.annotation`
- **Target**: `TYPE`
- **Retention**: `RUNTIME`
- **Description**: 标注插件组件类。标注此注解的类在插件启用时被 `PluginBeanRegistrar` 注册为 Spring Bean，可被其他组件注入。

#### Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `value` | `String` | `""` | Bean 名称，为空时使用类名首字母小写 |

#### Usage Example
```java
@PluginComponent("userService")
public class UserService {
    public User findById(Long id) { ... }
}
```

### @PluginMapping
- **Type**: annotation
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.annotation`
- **Target**: `METHOD`
- **Retention**: `RUNTIME`
- **Description**: 标注插件路由映射方法。标注此注解的方法在插件启用时被 `PluginRouteRegistrar` 注册到路由系统，自动处理对应 HTTP 请求。

#### Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `path` | `String` | `""` | 路由路径，如 `/api/users/{id}` |
| `method` | `HttpMethod` | `HttpMethod.GET` | HTTP 方法 |
| `produces` | `String` | `"application/json"` | 响应内容类型 |

#### Usage Example
```java
@PluginComponent("userController")
public class UserController {

    @PluginMapping(path = "/api/users/{id}", method = HttpMethod.GET)
    public User getUser(@PathVariable Long id) {
        return userService.findById(id);
    }

    @PluginMapping(path = "/api/users", method = HttpMethod.POST)
    public User createUser(@RequestBody User user) {
        return userService.save(user);
    }
}
```

### @PluginRoute
- **Type**: annotation
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.annotation`
- **Target**: `METHOD`
- **Retention**: `RUNTIME`
- **Description**: 标注可注册但非自动注册的路由方法。与 `@PluginMapping` 类似，但路由不会在插件启用时自动注册，需手动调用 `PluginRouteRegistrar.registerRoute()` 注册。适用于需要条件注册的场景。

#### Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `path` | `String` | `""` | 路由路径 |
| `method` | `HttpMethod` | `HttpMethod.GET` | HTTP 方法 |
| `produces` | `String` | `"application/json"` | 响应内容类型 |

### @SharedService
- **Type**: annotation
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.annotation`
- **Target**: `TYPE`
- **Retention**: `RUNTIME`
- **Description**: 标注插件对外暴露的共享服务。标注此注解的接口/类会被注册到共享服务注册表，其他插件可通过共享类加载器引用并调用，实现插件间通信。

#### Usage Example
```java
@SharedService
public interface PaymentService {
    boolean pay(String orderId, BigDecimal amount);
}

// 插件 A 中实现
@PluginComponent
public class AlipayService implements PaymentService {
    public boolean pay(String orderId, BigDecimal amount) { ... }
}

// 插件 B 中通过共享服务调用
```

### HttpMethod
- **Type**: enum
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.annotation`
- **Description**: HTTP 方法枚举，用于 `@PluginMapping` 和 `@PluginRoute` 注解。

#### Values

| Value | Description |
|-------|-------------|
| `GET` | HTTP GET 方法 |
| `POST` | HTTP POST 方法 |
| `PUT` | HTTP PUT 方法 |
| `DELETE` | HTTP DELETE 方法 |
| `PATCH` | HTTP PATCH 方法 |
| `HEAD` | HTTP HEAD 方法 |
| `OPTIONS` | HTTP OPTIONS 方法 |

#### Usage Example
```java
@PluginMapping(path = "/api/data", method = HttpMethod.POST)
public String createData(@RequestBody String data) { ... }
```
