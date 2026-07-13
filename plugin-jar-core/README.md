# plugin-jar-core

> 包名：`com.weacsoft.jaravel.vendor.plugin.jar`
> artifactId：`plugin-jar-core`

JAR 插件系统核心库，提供动态加载/卸载 JAR 插件、三级 ClassLoader 隔离、ASM 字节码扫描依赖与路由、动态路由注册/注销、元数据持久化等能力。基于 Spring Boot AutoConfiguration 开箱即用，引入依赖即可自动装配完整的插件管理基础设施。

本模块是 Jaravel 插件体系的核心，不包含任何 REST API 控制器（出于安全考虑），所有插件管理操作通过 `HotPluginManager` 的公开方法完成。如需对外暴露 HTTP 管理接口，由应用层自行实现并做好鉴权。

---

## 目录

- [核心特性](#核心特性)
- [依赖引入](#依赖引入)
- [配置](#配置)
- [架构设计](#架构设计)
  - [三级类加载器](#三级类加载器)
  - [路由代理机制](#路由代理机制)
  - [插件间调用](#插件间调用)
- [路由注册模式](#路由注册模式)
- [核心 API](#核心-api)
  - [HotPluginManager 方法列表](#hotpluginmanager-方法列表)
- [共享接口](#共享接口)
  - [设计理念](#设计理念)
  - [注册共享接口](#注册共享接口)
  - [调用共享接口](#调用共享接口)
  - [共享接口 API 一览](#共享接口-api-一览)
  - [多租户场景](#多租户场景)
- [字节数组模式（内存插件）](#字节数组模式内存插件)
- [插件开发指南](#插件开发指南)
  - [添加 Maven 依赖](#添加-maven-依赖)
  - [编写插件类](#编写插件类)
  - [跨插件调用](#跨插件调用)
- [核心注解](#核心注解)
- [持久化](#持久化)
  - [MetadataPersistence 接口](#metadatapersistence-接口)
  - [默认实现：JsonMetadataPersistence](#默认实现jsonmetadatapersistence)
  - [数据库实现：plugin-jar-database 模块](#数据库实现plugin-jar-database-模块)
- [Jaravel 集成](#jaravel-集成)
  - [PluginIntegration 接口](#pluginintegration-接口)
  - [Request/Response 集成](#requestresponse-集成)
- [安全说明](#安全说明)
- [目录结构](#目录结构)

---

## 核心特性

- **三级 ClassLoader 隔离 + 共享接口热更新**：主 ClassLoader / SharedClassLoader（可热替换）/ PluginClassLoader（每插件一个）三层隔离，插件间互不干扰，共享接口 JAR 可热更新而无需重启应用。
- **插件自动注册 Spring Bean + 动态路由映射**：通过 `@PluginComponent` 标注的类自动注册为 Spring Bean，通过 `@PluginMapping` 标注的方法自动注册为 HTTP 路由，无需手动配置。
- **插件间依赖注入**：通过 `Application.getService()` 代理，动态从 Spring 容器获取目标插件的服务实例，实现跨插件调用。
- **共享接口（字符串驱动反射调用）**：通过 `Application.registerSharedInterface()` / `invokeSharedInterface()`，插件可将以全字符串指定的 Bean 方法注册为全局共享接口，其他模块通过接口名称即可反射调用，参数和返回值统一用 Map 表示，无需编译期依赖目标类。
- **双路由注册模式**：支持自动注册（`auto-register=true`，插件启用时自动注册 `@PluginMapping` 路由）与手动注册（`auto-register=false`，通过 `@PluginRoute` 标记可用路由并使用 `registerAvailableRoute()` 按需注册）两种模式。
- **手动路由注册/注销**：提供 `registerRoute()` / `unregisterRoute()` 方法，以及 `getAvailableRoutes()` / `registerAvailableRoute()` 手动注册 API，支持以方法调用方式（非 REST API）动态注册和注销路由。
- **Jaravel Request/Response 集成**：当 jaravel 可用时，插件方法可直接使用 jaravel 的 `Request`/`Response` 而非 Servlet API，通过 `PluginIntegration` 接口自动检测并适配，同一份插件代码可同时运行于 jaravel 与纯 Spring Boot 环境。
- **ASM 字节码扫描依赖与路由**：使用 ASM 9.7 在不加载类的前提下扫描插件 JAR 的字节码，提取 `@PluginComponent` 组件类、`@SharedService` 共享依赖和 `@PluginMapping` / `@PluginRoute` 路由映射。
- **元数据持久化**：重启后自动恢复插件状态。支持 JSON 文件（默认）和数据库两种持久化模式，仅落盘文件可自动持久化恢复。
- **内存 JAR 加载**：支持从内存字节（`byte[]`）直接加载插件 JAR，不要求 `.jar` 落盘。但仅落盘文件（`persist=true`）可自动持久化恢复。
- **字节数组模式**：从内存中的 JAR 二进制数据直接热加载，无需落盘到插件目录。
- **Spring Boot AutoConfiguration 开箱即用**：引入依赖后自动装配 `HotPluginManager`、`PluginBeanRegistrar`、`PluginRouteRegistrar`、`MetadataPersistence` 等核心组件。
- **线程安全**：核心管理器使用 `ReadWriteLock` + `ConcurrentHashMap` 保证并发安全，插件注册/卸载与路由查询可安全并发。

---

## 依赖引入

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>plugin-jar-core</artifactId>
    <version>0.1.2</version>
</dependency>
```

该模块传递依赖 Spring Boot Web、Spring Boot AutoConfigure、Jackson（JSON 序列化）与 ASM（字节码扫描）。

---

## 配置

```yaml
jaravel:
  plugin-jar:
    enabled: true           # 是否启用（默认 true）
    plugins-dir: plugins    # 插件目录（默认 plugins）
    auto-restore: true      # 启动时自动恢复已启用的插件（默认 true）
    auto-register: true     # 路由自动注册（默认 true=自动注册, false=手动注册）
```

| 配置项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `jaravel.plugin-jar.enabled` | `boolean` | `true` | 是否启用插件系统。设为 `false` 则不自动装配 `HotPluginManager` |
| `jaravel.plugin-jar.plugins-dir` | `String` | `plugins` | 插件存放目录，相对路径基于工作目录，也支持绝对路径 |
| `jaravel.plugin-jar.auto-restore` | `boolean` | `true` | 启动时是否自动从持久化存储恢复已启用的插件 |
| `jaravel.plugin-jar.auto-register` | `boolean` | `true` | 路由注册模式。`true`=自动注册（插件启用时自动注册 `@PluginMapping` 路由）；`false`=手动注册（所有路由都需通过 `registerAvailableRoute()` 手动注册）。详见 [路由注册模式](#路由注册模式) |

> **Config 访问**：当 jaravel 可用时，所有配置可通过 `Config.get()` 访问：
>
> ```java
> boolean autoRegister = Config.getBool("jaravel.plugin-jar.auto-register", true);
> ```
>
> `PluginIntegration.getConfigValue()` 方法在 jaravel 可用时委托给 `Config.get()`，在纯 Spring Boot 环境下从 Spring `Environment` 读取。

---

## 架构设计

### 三级类加载器

本模块采用三级 ClassLoader 隔离架构，确保插件之间、插件与主应用之间的类隔离，同时支持共享接口的热更新：

```
┌─────────────────────────────────────────────────────────────────┐
│  主 ClassLoader（Spring Boot 应用 ClassLoader）                   │
│  ─────────────────────────────────────────────────────────────  │
│  · Spring Boot 基础设施（Spring MVC / Bean 容器 / 自动装配）       │
│  · Application 代理类（主 ClassLoader 中的单例）                   │
│  · plugin-jar-core 核心类（HotPluginManager / 注解类等）           │
└──────────────────────────────┬──────────────────────────────────┘
                               │ parent
┌──────────────────────────────▼──────────────────────────────────┐
│  SharedClassLoader（可热替换，extends URLClassLoader）             │
│  ─────────────────────────────────────────────────────────────  │
│  · 共享接口 JAR（@SharedService 标注的接口定义）                    │
│  · plugin-jar-core JAR（注解类 @PluginComponent / @PluginMapping） │
│  · 版本号 version，可通过 updateSharedJar() 整体热替换              │
└──────────────────────────────┬──────────────────────────────────┘
                               │ parent
        ┌──────────────────────┼──────────────────────┐
        │                      │                      │
┌───────▼────────┐    ┌────────▼───────┐    ┌────────▼───────┐
│ PluginClassLoader│    │ PluginClassLoader│    │ PluginClassLoader│
│ (插件 A)         │    │ (插件 B)         │    │ (插件 C)         │
│ ────────────── │    │ ────────────── │    │ ────────────── │
│ · 插件 A 的 JAR  │    │ · 插件 B 的 JAR  │    │ · 插件 C 的 JAR  │
│ · 独立类空间     │    │ · 独立类空间     │    │ · 独立类空间     │
└────────────────┘    └────────────────┘    └────────────────┘
```

**类加载顺序**（`PluginClassLoader.loadClass()`）：

1. **Application 代理类**：以 `com.weacsoft.jaravel.vendor.plugin.jar.annotation.Application` 开头的类始终从主 ClassLoader 加载，确保插件通过统一代理访问主应用服务。
2. **共享包前缀**：匹配 `sharedPackagePrefixes` 的类从 `SharedClassLoader` 加载（共享接口 + 注解类）。
3. **插件自身类**：其余类由 `PluginClassLoader` 自身加载（插件 JAR 内的类）。
4. **父类委托**：以上均未命中时委托父 ClassLoader（`SharedClassLoader` → 主 ClassLoader）加载。

**热更新机制**：`updateSharedJar()` 会创建新的 `SharedClassLoader`，并逐个调用所有已启用插件的 `PluginClassLoader.updateSharedClassLoader()` 替换其父加载器引用。旧 `SharedClassLoader` 随后关闭，实现共享接口的热更新而无需重启应用或卸载插件。

### 路由代理机制

插件路由不直接注册到 Spring MVC 的 `@RequestMapping`，而是通过统一的 `PluginRouteHandler` 代理处理：

```
HTTP 请求
  │
  ▼
Spring MVC (RequestMappingHandlerMapping)
  │  匹配到插件路由 → 委托给 PluginRouteHandler Bean
  ▼
PluginRouteHandler.handleRequest(HttpServletRequest, HttpServletResponse)
  │
  ├── 1. 从请求中提取路径变量（path variables）
  ├── 2. 在 routeRegistry（ConcurrentHashMap）中查找 RouteInfo
  │      RouteInfo 包含：path / method / beanName / methodName / produces
  ├── 3. 通过 beanRegistrar 从 Spring 容器获取插件 Bean 实例
  ├── 4. 反射查找 Bean 的目标方法（findMethod）
  ├── 5. 解析方法参数（resolveArguments：路径变量 / 请求参数 / 默认值）
  ├── 6. 反射调用插件方法
  └── 7. 将返回值按 produces 类型写入 HTTP 响应（writeResponse）
```

`PluginRouteRegistrar` 负责将插件路由注册到 Spring MVC 的 `RequestMappingHandlerMapping`，注册时创建 `RequestMappingInfo`（路径 + HTTP 方法），并映射到统一的 `PluginRouteHandler` Bean。注销时移除对应的 `RequestMappingInfo`。

### 插件间调用

插件间调用通过 `Application` 代理实现。`Application` 是位于主 ClassLoader 中的单例类，持有 `HotPluginManager` 的引用（通过 `HotPluginManagerRef` 接口注入）：

```
插件 A                          主 ClassLoader                    Spring 容器
  │                                │                                │
  │  Application.getService(       │                                │
  │    "plugin-b",                 │                                │
  │    ServiceB.class,             │                                │
  │    "serviceB")                 │                                │
  │ ─────────────────────────────► │                                │
  │                                │  getServiceFromPlugin(         │
  │                                │    "plugin-b", "serviceB")     │
  │                                │ ─────────────────────────────► │
  │                                │                                │ 从容器获取
  │                                │                                │ "serviceB" Bean
  │                                │ ◄───────────────────────────── │ 返回实例
  │ ◄───────────────────────────── │ 返回服务实例                     │
  │  得到 ServiceB 实例             │                                │
```

`Application.getService()` 是静态方法，签名如下：

```java
public static Object getService(String pluginId, Class<?> serviceType, String beanName)
```

由于 `Application` 类始终从主 ClassLoader 加载（`PluginClassLoader.isApplicationProxyClass()` 判断），插件代码引用的 `Application` 类与主应用中的是同一个类实例，从而保证了代理引用的一致性。

---

## 路由注册模式

插件系统支持两种路由注册模式，通过配置项 `jaravel.plugin-jar.auto-register` 切换：

**配置项**：

```yaml
jaravel:
  plugin-jar:
    auto-register: true   # 默认 true=自动注册, false=手动注册
```

**两种模式**：

| 模式 | auto-register | @PluginMapping | @PluginRoute | 说明 |
|------|--------------|----------------|--------------|------|
| 自动注册 | `true`（默认） | 自动注册 | 列为可用 | 插件启用时自动注册 `@PluginMapping` 路由 |
| 手动注册 | `false` | 列为可用 | 列为可用 | 所有路由都需手动注册 |

### @PluginMapping vs @PluginRoute

- `@PluginMapping` - 用于自动注册模式。插件启用时自动注册路由。
- `@PluginRoute` - 用于手动注册模式。仅标记方法为"可注册路由"，不自动注册。

两个注解属性相同：`path()`、`method()`（默认 `GET`）、`produces()`（默认 `application/json`）。

> 在自动注册模式下，`@PluginMapping` 标注的方法会在插件启用时自动注册为 HTTP 路由；`@PluginRoute` 标注的方法仅被列为"可用路由"，不会自动注册。在手动注册模式下，`@PluginMapping` 与 `@PluginRoute` 标注的方法都会被列为"可用路由"，需通过手动注册 API 显式注册后才能访问。

### 手动注册 API

```java
// 列出可注册路由
Set<RouteInfo> available = manager.getAvailableRoutes("my-plugin");

// 手动注册指定路由
boolean ok = manager.registerAvailableRoute("my-plugin", "/api/hello", "GET");
```

- `getAvailableRoutes(pluginId)` 返回该插件下所有被 `@PluginMapping` / `@PluginRoute` 标注但尚未注册的路由集合。
- `registerAvailableRoute(pluginId, path, httpMethod)` 按 path + HTTP 方法精确匹配，将可用路由注册到 Spring MVC。返回是否注册成功。

**手动注册完整示例**：

```java
// 1. 启用手动注册模式（application.yml 中设置 auto-register: false）
// 2. 注册并启用插件
String pluginId = hotPluginManager.registerPluginFromPath(
    Path.of("plugins/my-plugin-1.0.0.jar"), "my-plugin", true);
hotPluginManager.enablePlugin(pluginId);

// 3. 此时 @PluginMapping / @PluginRoute 标注的路由不会自动注册
//    查看可注册路由
Set<RouteInfo> available = hotPluginManager.getAvailableRoutes(pluginId);
available.forEach(r -> System.out.println(r.getMethod() + " " + r.getPath()));

// 4. 按需手动注册路由
hotPluginManager.registerAvailableRoute(pluginId, "/api/hello", "GET");
hotPluginManager.registerAvailableRoute(pluginId, "/api/users/{id}", "GET");
```

---

## 核心 API

### HotPluginManager 方法列表

`HotPluginManager` 是插件系统的核心管理器，实现 `Application.HotPluginManagerRef` 接口。由 `PluginJarAutoConfiguration` 自动装配为 Spring Bean，可直接注入使用。

```java
@Autowired
private HotPluginManager hotPluginManager;
```

#### 初始化与共享 ClassLoader

| 方法签名 | 说明 |
|---|---|
| `void initSharedClassLoader(Path sharedJar, String version)` | 初始化共享 ClassLoader，加载共享接口 JAR 并设置版本号 |
| `void setCoreJarPath(Path coreJarPath)` | 设置 plugin-jar-core 自身 JAR 路径（用于共享注解类） |
| `Path getPluginsDir()` | 获取插件目录路径 |
| `PluginRouteRegistrar getRouteRegistrar()` | 获取路由注册器实例 |

#### 插件注册与卸载

| 方法签名 | 说明 |
|---|---|
| `String registerPluginFromPath(Path jarFile, String pluginId, boolean persist)` | 从文件路径注册插件。`persist=true` 时落盘持久化，重启后可自动恢复。返回实际使用的 pluginId |
| `String registerPluginFromBytes(byte[] jarBytes, String pluginId)` | 从内存字节注册插件，不落盘、不持久化。返回实际使用的 pluginId |
| `boolean enablePlugin(String pluginId)` | 启用插件：注册 Bean + 注册路由 + 持久化元数据。返回是否操作成功 |
| `boolean disablePlugin(String pluginId)` | 禁用插件：注销路由 + 注销 Bean + 更新元数据状态。返回是否操作成功 |
| `boolean uninstallPlugin(String pluginId)` | 卸载插件：注销路由 + 注销 Bean + 关闭 ClassLoader + 删除元数据。返回是否操作成功 |

#### 共享 JAR 热更新

| 方法签名 | 说明 |
|---|---|
| `List<String> updateSharedJar(Path newSharedJar, String version)` | 更新共享 JAR（热更新）。创建新 SharedClassLoader，替换所有已启用插件的父加载器，关闭旧加载器。返回受影响的插件 ID 列表 |

#### 路由管理

| 方法签名 | 说明 |
|---|---|
| `boolean registerRoute(String pluginId, RouteInfo route)` | 手动注册路由。将 RouteInfo 注册到路由注册器并映射到 Spring MVC。返回是否注册成功 |
| `boolean unregisterRoute(String pluginId, String path, String httpMethod)` | 手动注销路由。按插件 ID、路径、HTTP 方法精确匹配注销。返回是否注销成功 |
| `Set<RouteInfo> getAvailableRoutes(String pluginId)` | 列出指定插件下所有可注册但尚未注册的路由（被 `@PluginMapping` / `@PluginRoute` 标注的方法）。用于手动注册模式 |
| `boolean registerAvailableRoute(String pluginId, String path, String httpMethod)` | 手动注册可用路由。按插件 ID、路径、HTTP 方法精确匹配，将可用路由注册到 Spring MVC。返回是否注册成功 |

#### 跨插件服务获取

| 方法签名 | 说明 |
|---|---|
| `Object getServiceFromPlugin(String pluginId, String beanName)` | 跨插件获取服务。从 Spring 容器中按 beanName 获取指定插件注册的 Bean 实例 |

#### 共享接口

| 方法签名 | 说明 |
|---|---|
| `boolean registerSharedInterface(String interfaceName, String pluginId, String beanName, String methodName)` | 注册共享接口（全手动指定，全部字符串）。要求插件已启用且 Bean 已注册。详见 [共享接口](#共享接口) |
| `boolean registerSharedInterface(String interfaceName, String pluginId, String beanName, String methodName, String description)` | 注册共享接口（带可选描述） |
| `boolean unregisterSharedInterface(String interfaceName)` | 注销共享接口。接口存在并移除返回 true |
| `List<SharedInterfaceDescriptor> getSharedInterfaces()` | 获取所有已注册的共享接口描述列表 |
| `SharedInterfaceDescriptor getSharedInterface(String interfaceName)` | 获取指定共享接口描述符，不存在返回 null |
| `Map<String, Object> invokeSharedInterface(String interfaceName, Map<String, Object> args)` | 通过共享接口名称反射调用目标方法，参数和返回值均用 Map 表示 |

#### 查询与持久化恢复

| 方法签名 | 说明 |
|---|---|
| `List<PluginInfo> getAllPlugins()` | 列出所有已注册插件的信息 |
| `PluginInfo getPlugin(String pluginId)` | 获取指定插件的详细信息（状态、依赖、路由等） |
| `void loadPersistedPlugins()` | 加载持久化的插件元数据，自动恢复已启用的插件 |

#### 使用示例

```java
// 1. 初始化共享 ClassLoader（通常在启动时由自动装配完成）
hotPluginManager.initSharedClassLoader(Path.of("shared-api.jar"), "0.1.2");

// 2. 从文件注册插件（持久化）
String pluginId = hotPluginManager.registerPluginFromPath(
    Path.of("plugins/my-plugin-1.0.0.jar"), "my-plugin", true);

// 3. 启用插件
hotPluginManager.enablePlugin(pluginId);

// 4. 从内存字节注册插件（不持久化）
hotPluginManager.registerPluginFromBytes(jarBytes, "temp-plugin");
hotPluginManager.enablePlugin("temp-plugin");

// 5. 手动注册路由
RouteInfo route = new RouteInfo(
    "/api/custom", HttpMethod.GET, "myBean", "customMethod", "application/json");
hotPluginManager.registerRoute("my-plugin", route);

// 6. 跨插件获取服务
Object service = hotPluginManager.getServiceFromPlugin("my-plugin", "myBean");

// 7. 热更新共享 JAR
List<String> affected = hotPluginManager.updateSharedJar(
    Path.of("shared-api-2.0.0.jar"), "2.0.0");

// 8. 禁用 / 卸载
hotPluginManager.disablePlugin("my-plugin");
hotPluginManager.uninstallPlugin("my-plugin");

// 9. 查询插件信息
List<PluginInfo> all = hotPluginManager.getAllPlugins();
PluginInfo info = hotPluginManager.getPlugin("my-plugin");

// 10. 共享接口（详见下方"共享接口"章节）
hotPluginManager.registerSharedInterface(
    "admin.service.list", "my-plugin", "blogController", "list", "获取博客列表");
Map<String, Object> result = hotPluginManager.invokeSharedInterface(
    "admin.service.list", Map.of("page", 1, "size", 10));
```

---

## 共享接口

共享接口（Shared Interface）是一种轻量级的插件间调用机制。插件可以将自身某个 Bean 的某个方法注册为"共享接口"，其他模块（插件或主程序）通过全局唯一的接口名称即可反射调用，而无需在编译期依赖目标插件的具体类。

### 设计理念

共享接口的设计围绕以下四个核心原则：

| 原则 | 说明 |
|------|------|
| **全手动指定** | 注册时全部使用字符串指定（接口名、插件 ID、Bean 名、方法名），不需要注解扫描，不需要接口预定义。开发时无需包含目标类 |
| **字符串驱动** | 调用方只需知道接口名称字符串（如 `"admin.service.list"`），不依赖任何具体类型，实现真正的解耦 |
| **反射封装** | 运行时由 `HotPluginManager` 通过反射查找 Bean 和方法并调用，调用方无需处理反射细节 |
| **Map 传参** | 请求参数和返回值统一用 `Map<String, Object>` 表示，避免跨插件/跨模块的类型依赖问题，适合 JSON 友好的场景 |

与 `Application.getService()` 的区别：

| 维度 | `Application.getService()` | 共享接口 |
|------|---------------------------|---------|
| 依赖 | 编译期需要目标服务接口类 | 完全无依赖，仅靠字符串名称 |
| 调用方式 | 获取 Bean 实例后直接调用方法 | 通过接口名称反射调用 |
| 参数/返回 | 强类型 | `Map<String, Object>` |
| 适用场景 | 同一团队、可共享接口 JAR | 跨团队、松耦合、动态发现 |

### 注册共享接口

注册共享接口使用 `HotPluginManager.registerSharedInterface()` 或 `Application.registerSharedInterface()`。注册前要求目标插件已启用且对应 Bean 已注册到 Spring 容器。

```java
import com.weacsoft.jaravel.vendor.plugin.jar.annotation.Application;

// ===== 方式一：主程序侧通过 HotPluginManager 注册 =====
@Autowired
private HotPluginManager hotPluginManager;

// 前提：插件 "blog-plugin" 已启用，其中 blogController Bean 已注册
hotPluginManager.registerSharedInterface(
    "admin.service.list",        // 接口名称（全局唯一）
    "blog-plugin",               // 提供方插件 ID
    "blogController",            // Bean 名称
    "list",                      // 方法名
    "获取博客列表"                 // 可选描述
);

// ===== 方式二：插件侧通过 Application 静态方法注册 =====
// 插件代码中无需注入 HotPluginManager，直接调用 Application
Application.registerSharedInterface(
    "admin.service.list", "blog-plugin", "blogController", "list");
```

注册成功后，可通过 `getSharedInterfaces()` 查看所有已注册的共享接口：

```java
List<SharedInterfaceDescriptor> interfaces = hotPluginManager.getSharedInterfaces();
interfaces.forEach(d -> System.out.println(
    d.getInterfaceName() + " -> " + d.getPluginId() + ":" + d.getBeanName() + "." + d.getMethodName()));
// 输出: admin.service.list -> blog-plugin:blogController.list
```

### 调用共享接口

调用共享接口使用 `HotPluginManager.invokeSharedInterface()` 或 `Application.invokeSharedInterface()`，传入接口名称和参数 Map，返回值也是 Map。

```java
import com.weacsoft.jaravel.vendor.plugin.jar.annotation.Application;

// ===== 方式一：主程序侧通过 HotPluginManager 调用 =====
Map<String, Object> args = new HashMap<>();
args.put("page", 1);
args.put("size", 10);
Map<String, Object> result = hotPluginManager.invokeSharedInterface("admin.service.list", args);

// ===== 方式二：插件侧通过 Application 静态方法调用 =====
Map<String, Object> result2 = Application.invokeSharedInterface(
    "admin.service.list", Map.of("page", 1, "size", 10));
```

**参数解析规则**（根据目标方法的参数数量自动适配）：

| 方法参数情况 | 解析规则 | 示例 |
|-------------|---------|------|
| 0 参数 | 直接调用，忽略 args | `public Map list()` |
| 1 个 `Map` 参数 | 将整个 args Map 传入 | `public Map list(Map<String, Object> params)` |
| 1 个其他类型参数 | 从 args 中取 `"data"` 键；不存在则取第一个值，按目标类型转换 | `public String getTitle(String data)` |
| 多参数 | 按参数名（`Parameter.getName()`）从 args 中匹配取值 | `public Map list(int page, int size)` |

**返回值转换规则**：

| 方法返回值 | 转换结果 |
|-----------|---------|
| `null` 或 `void` | 返回空 Map `{}` |
| `Map` | 直接返回该 Map |
| 其他类型 | 包装为 `{"data": result}` |

> 调用过程中发生异常时返回 `{"error": message}`，不向外抛出异常。

**完整示例**：目标插件中有一个如下方法：

```java
@PluginComponent("blogController")
public class BlogController {

    // 多参数方法：按参数名 page、size 匹配
    public Map<String, Object> list(int page, int size) {
        Map<String, Object> data = new HashMap<>();
        data.put("page", page);
        data.put("size", size);
        data.put("items", List.of("post-1", "post-2"));
        return data;
    }

    // 接收 Map 参数的方法
    public Map<String, Object> search(Map<String, Object> params) {
        return Map.of("keyword", params.get("keyword"), "results", List.of());
    }

    // 无参方法
    public String ping() {
        return "pong";
    }
}
```

注册并调用：

```java
// 注册
hotPluginManager.registerSharedInterface("blog.list", "blog-plugin", "blogController", "list");
hotPluginManager.registerSharedInterface("blog.search", "blog-plugin", "blogController", "search");
hotPluginManager.registerSharedInterface("blog.ping", "blog-plugin", "blogController", "ping");

// 调用 list（多参数，按参数名匹配）
Map<String, Object> r1 = Application.invokeSharedInterface(
    "blog.list", Map.of("page", 1, "size", 10));
// r1 = {page=1, size=10, items=[post-1, post-2]}  （方法返回 Map，直接返回）

// 调用 search（1 个 Map 参数，传入整个 args）
Map<String, Object> r2 = Application.invokeSharedInterface(
    "blog.search", Map.of("keyword", "hello"));
// r2 = {keyword=hello, results=[]}

// 调用 ping（0 参数）
Map<String, Object> r3 = Application.invokeSharedInterface("blog.ping", new HashMap<>());
// r3 = {data=pong}  （返回 String，包装为 {"data": "pong"}）
```

### 共享接口 API 一览

| API | 所属 | 说明 |
|-----|------|------|
| `HotPluginManager.registerSharedInterface(name, pluginId, bean, method)` | HotPluginManager | 注册共享接口 |
| `HotPluginManager.registerSharedInterface(name, pluginId, bean, method, desc)` | HotPluginManager | 注册共享接口（带描述） |
| `HotPluginManager.unregisterSharedInterface(name)` | HotPluginManager | 注销共享接口 |
| `HotPluginManager.getSharedInterfaces()` | HotPluginManager | 获取所有共享接口 |
| `HotPluginManager.getSharedInterface(name)` | HotPluginManager | 获取指定共享接口描述 |
| `HotPluginManager.invokeSharedInterface(name, args)` | HotPluginManager | 反射调用共享接口 |
| `Application.registerSharedInterface(name, pluginId, bean, method)` | Application（static） | 插件侧注册入口 |
| `Application.invokeSharedInterface(name, args)` | Application（static） | 插件侧调用入口 |
| `Application.getSharedInterfaces()` | Application（static） | 插件侧查询入口 |

`SharedInterfaceDescriptor` 字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `interfaceName` | `String` | 共享接口名称（全局唯一，如 `"admin.service.list"`） |
| `pluginId` | `String` | 提供方插件 ID（如 `"studentA@blog"`） |
| `beanName` | `String` | Bean 名称（如 `"blogController"`） |
| `methodName` | `String` | 方法名（如 `"list"`） |
| `description` | `String` | 可选描述 |

### 多租户场景

引入 `plugin-jar-multi-tenant` 模块后，`TenantAwareHotPluginManager` 会重写 `lookupSharedInterfaceBean()` 方法，在多租户模式下自动将 Bean 名称前缀化（如 `blogController` → `studentA:blogController`），反射调用逻辑复用父类。注册共享接口时使用原始 Bean 名称，调用时由多租户管理器自动处理前缀化，调用方无感知。

```java
// 多租户场景下注册共享接口（pluginId 含租户前缀）
hotPluginManager.registerSharedInterface(
    "studentA.blog.list",       // 接口名称
    "studentA@blog",            // 含租户的 pluginId
    "blogController",           // 原始 Bean 名（查找时自动前缀化为 studentA:blogController）
    "list");

// 调用时无需关心租户前缀化细节
Map<String, Object> result = Application.invokeSharedInterface(
    "studentA.blog.list", Map.of("page", 1));
```

详见 [plugin-jar-multi-tenant](../plugin-jar-multi-tenant/README.md) 模块文档。

---

## 字节数组模式（内存插件）

字节数组模式适合从数据库、网络等获取 JAR 二进制数据后直接热加载的场景。JAR 数据会写入临时文件（JVM 退出时自动删除），不持久化到插件目录，因此重启后不会自动恢复。

### API 方法

| 方法签名 | 说明 |
|---|---|
| `String registerPluginFromBytes(byte[] jarBytes, String pluginId)` | 从字节数组注册插件（不持久化）。将 JAR 写入临时文件后加载，返回实际使用的 pluginId |
| `boolean reloadPluginFromBytes(String pluginId, byte[] jarBytes)` | 从新的字节数组热重载插件。先禁用旧版本（若已启用），再用新字节数组重新加载并启用，保持 pluginId 不变 |

### 代码示例

```java
// 从数据库获取 JAR 二进制
byte[] jarBytes = repository.findPluginJar("my-plugin");

// 直接注册并启用
manager.registerPluginFromBytes(jarBytes, "my-plugin");
manager.enablePlugin("my-plugin");

// 后续从数据库获取新版本，热重载
byte[] newJarBytes = repository.findPluginJar("my-plugin");
manager.reloadPluginFromBytes("my-plugin", newJarBytes);
```

### 三种注册模式对比

插件系统支持三种注册模式，对应不同的 JAR 来源与持久化策略：

| 模式 | API | JAR 处理方式 | 是否持久化 | 自动恢复 |
|------|-----|-------------|-----------|---------|
| 磁盘模式 | `registerPluginFromPath(jarFile, pluginId, true)` | 复制 JAR 到插件目录 | 是 | 支持 |
| 路径模式 | `registerPluginFromPath(jarFile, pluginId, false)` | 仅记录路径，JAR 不复制 | 否 | 不支持 |
| 字节数组模式 | `registerPluginFromBytes(jarBytes, pluginId)` | 写入临时文件，JVM 退出自动删除 | 否 | 不支持 |

---

## 插件开发指南

### 添加 Maven 依赖

插件项目的 `pom.xml` 需要依赖 `plugin-jar-core`，使用 `provided` scope（运行时由宿主应用提供）：

```xml
<dependencies>
    <dependency>
        <groupId>io.github.lijialong1313</groupId>
        <artifactId>plugin-jar-core</artifactId>
        <version>0.1.2</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

> 共享接口 JAR（包含 `@SharedService` 标注的接口）同样以 `provided` scope 引入，由宿主应用的 `SharedClassLoader` 在运行时加载。

### 编写插件类

使用 `@PluginComponent` 标注 Bean 类，使用 `@PluginMapping` 标注路由方法：

```java
import com.weacsoft.jaravel.vendor.plugin.jar.annotation.PluginComponent;
import com.weacsoft.jaravel.vendor.plugin.jar.annotation.PluginMapping;
import com.weacsoft.jaravel.vendor.plugin.jar.annotation.HttpMethod;

@PluginComponent("greetingService")
public class GreetingServiceImpl implements GreetingService {

    @PluginMapping(path = "/api/greeting", method = HttpMethod.GET)
    public String greetingEndpoint(String name) {
        return "Hello, " + name + "!";
    }
}
```

**注意事项**：

- `@PluginMapping` 使用 `path` 属性指定路由路径（不是 `value`）。
- `method` 属性默认为 `HttpMethod.GET`，可省略。
- `produces` 属性默认为 `"application/json"`，控制响应的 Content-Type。
- `@PluginComponent` 的 `value` 属性为 Bean 名称，可选（省略时使用类名首字母小写）。
- 一个 `@PluginComponent` 类中可包含多个 `@PluginMapping` 方法。

打包为 JAR 后，放入宿主应用的 `plugins` 目录，或通过 `registerPluginFromPath()` / `registerPluginFromBytes()` 注册。

### 跨插件调用

通过 `Application` 代理动态从 Spring 容器获取目标插件的服务实例：

```java
import com.weacsoft.jaravel.vendor.plugin.jar.annotation.Application;

// 跨插件获取服务：pluginId + 服务类型 + beanName
GreetingService service = Application.getService(
    "plugin-greeting", GreetingService.class, "greetingService");

// 调用目标插件的方法
String result = service.greet("World");
```

`Application.getService()` 内部委托给 `HotPluginManager.getServiceFromPlugin()`，从 Spring 容器中按 beanName 获取目标插件注册的 Bean 实例。由于 `Application` 类始终从主 ClassLoader 加载，插件代码与主应用引用的是同一个代理实例。

---

## 核心注解

| 注解 | 作用 | 属性 |
|---|---|---|
| `@PluginComponent` | 标注插件组件类，被标注的类将自动注册为 Spring Bean | `value`（Bean 名称，可选，默认空串） |
| `@PluginMapping` | 标注方法为 HTTP 路由，被标注的方法将自动注册为可访问的 HTTP 端点 | `path`（路径，必填）、`method`（HTTP 方法，默认 `GET`）、`produces`（Content-Type，默认 `application/json`） |
| `@PluginRoute` | 标记方法为可注册路由（手动注册模式）。仅将方法列为"可用路由"，不自动注册，需通过 `registerAvailableRoute()` 手动注册 | `path`（路径）、`method`（HTTP 方法，默认 `GET`）、`produces`（Content-Type，默认 `application/json`） |
| `@SharedService` | 标记共享服务接口，被标注的接口将被 ASM 扫描器识别为共享依赖 | 无（标记注解） |

### 注解定义详情

```java
// 插件组件注解
@PluginComponent("greetingService")
public class GreetingServiceImpl implements GreetingService { ... }

// 路由映射注解（自动注册模式）
@PluginMapping(path = "/api/greeting", method = HttpMethod.GET, produces = "application/json")
public String greetingEndpoint(String name) { ... }

// 可注册路由注解（手动注册模式，不自动注册）
@PluginRoute(path = "/api/hello", method = HttpMethod.GET, produces = "application/json")
public String helloEndpoint(String name) { ... }

// 共享服务接口注解（标记接口）
@SharedService
public interface GreetingService {
    String greet(String name);
}
```

### HttpMethod 枚举

`@PluginMapping` 的 `method` 属性使用 `HttpMethod` 枚举，支持以下值：

| 枚举值 | 对应 HTTP 方法 |
|---|---|
| `HttpMethod.GET` | GET |
| `HttpMethod.POST` | POST |
| `HttpMethod.PUT` | PUT |
| `HttpMethod.DELETE` | DELETE |
| `HttpMethod.PATCH` | PATCH |
| `HttpMethod.HEAD` | HEAD |
| `HttpMethod.OPTIONS` | OPTIONS |

---

## 持久化

### MetadataPersistence 接口

`MetadataPersistence` 是插件元数据持久化的抽象接口，定义了元数据的增删查操作：

```java
public interface MetadataPersistence {

    /** 保存插件元数据 */
    void save(PluginInfo pluginInfo);

    /** 加载指定插件的元数据 */
    PluginInfo load(String pluginId);

    /** 加载所有持久化的插件元数据 */
    List<PluginInfo> loadAll();

    /** 删除指定插件的元数据 */
    void delete(String pluginId);
}
```

| 方法签名 | 说明 |
|---|---|
| `void save(PluginInfo pluginInfo)` | 保存插件元数据（包括 pluginId、version、jarPath、state、依赖、路由映射等） |
| `PluginInfo load(String pluginId)` | 按 pluginId 加载单个插件的元数据，不存在返回 `null` |
| `List<PluginInfo> loadAll()` | 加载所有持久化的插件元数据，供启动时自动恢复使用 |
| `void delete(String pluginId)` | 按 pluginId 删除插件元数据，卸载插件时调用 |

### 默认实现：JsonMetadataPersistence

`JsonMetadataPersistence` 是 `MetadataPersistence` 的默认实现，基于 Jackson 将插件元数据序列化为 JSON 文件存储。

**存储路径**：`{plugins-dir}/{pluginId}/metadata.json`

**持久化规则**：
- 仅持久化 `persisted=true` 的插件（即通过 `registerPluginFromPath()` 且 `persist=true` 注册的磁盘落盘插件）。
- 通过 `registerPluginFromBytes()` 注册的内存插件不持久化，重启后不恢复。
- 插件启用/禁用时自动更新元数据状态。
- 插件卸载时自动删除元数据文件。

**自动装配**：`PluginJarAutoConfiguration` 通过 `ObjectProvider` 注入 `ObjectMapper`，若容器中无 `ObjectMapper` 则创建默认实例。当 `plugin-jar-database` 模块存在时，数据库实现优先于 JSON 实现。

### 数据库实现：plugin-jar-database 模块

`plugin-jar-database` 模块提供了基于 jaravel `BaseModel` 的数据库持久化实现，引入该依赖即可自动切换为数据库模式：

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>plugin-jar-database</artifactId>
    <version>0.1.2</version>
</dependency>
```

该模块包含：

- `PluginMetadataModel`：基于 `BaseModel` 的插件元数据数据模型，对应数据库表。
- `ModelMetadataPersistence`：实现 `MetadataPersistence` 接口，通过 `BaseModel` 进行数据库 CRUD。
- `Migration_2024_01_03_CreatePluginMetadataTable`：自动迁移脚本，创建插件元数据表。
- `PluginJarDatabaseAutoConfiguration`：自动装配类，注册 `ModelMetadataPersistence` Bean（`@ConditionalOnMissingBean(MetadataPersistence.class)`）。

引入 `plugin-jar-database` 后，`PluginJarAutoConfiguration` 中的 `metadataPersistence()` 方法会优先使用数据库实现（通过 `ObjectProvider` 检测），JSON 文件实现作为兜底。

---

## Jaravel 集成

### PluginIntegration 接口

`PluginIntegration` 是插件与 Jaravel 框架集成的扩展点接口，允许宿主应用在插件启用/禁用时注入自定义服务（如 auth、cache、database 等）：

```java
public interface PluginIntegration {

    /** 插件启用时回调，可向插件上下文注入服务 */
    void onPluginEnabled(String pluginId, ConfigurableApplicationContext context);

    /** 插件禁用时回调，可清理插件相关资源 */
    void onPluginDisabled(String pluginId);

    /** 返回插件可见的共享包前缀集合 */
    Set<String> getSharedPackagePrefixes();

    /** 返回额外的共享 JAR 路径列表 */
    List<Path> getAdditionalSharedJars();

    /** 创建 jaravel Request（jaravel 不可用时返回 null） */
    Object createPluginRequest(HttpServletRequest servletRequest);

    /** 写入 jaravel Response（返回 false 表示使用默认逻辑） */
    boolean writePluginResponse(Object result, HttpServletResponse response, String produces);

    /** 读取配置值（jaravel 可用时委托 Config.get()，否则从 Spring Environment 读取） */
    <T> T getConfigValue(String key, T defaultValue);
}
```

| 方法签名 | 说明 |
|---|---|
| `void onPluginEnabled(String pluginId, ConfigurableApplicationContext context)` | 插件启用时回调。`context` 为 Spring 应用上下文，可在此向插件注入 auth、cache、database 等服务 |
| `void onPluginDisabled(String pluginId)` | 插件禁用时回调。可在此清理插件持有的资源引用 |
| `Set<String> getSharedPackagePrefixes()` | 返回插件 ClassLoader 可见的共享包前缀集合。匹配这些前缀的类将从 `SharedClassLoader` 加载 |
| `List<Path> getAdditionalSharedJars()` | 返回额外的共享 JAR 路径列表，这些 JAR 将被加入 `SharedClassLoader` |
| `Object createPluginRequest(HttpServletRequest servletRequest)` | 创建 jaravel `Request` 对象。当 jaravel 可用时将 Servlet 请求包装为 jaravel `Request` 返回；jaravel 不可用时返回 `null`，此时插件方法参数解析回退到默认逻辑 |
| `boolean writePluginResponse(Object result, HttpServletResponse response, String produces)` | 写入 jaravel `Response` 返回值。当插件方法返回 jaravel `Response` 且 jaravel 可用时，由该方法处理响应写入并返回 `true`；返回 `false` 表示使用默认响应写入逻辑 |
| `<T> T getConfigValue(String key, T defaultValue)` | 读取配置值。jaravel 可用时委托给 `Config.get()`，纯 Spring Boot 环境下从 Spring `Environment` 读取。`defaultValue` 在配置不存在时返回 |

**默认实现**：`DefaultPluginIntegration` 为空操作实现（所有方法均为空体），`getSharedPackagePrefixes()` 返回空集合，`getAdditionalSharedJars()` 返回空列表，`createPluginRequest()` 返回 `null`，`writePluginResponse()` 返回 `false`，`getConfigValue()` 从 Spring `Environment` 读取。`PluginJarAutoConfiguration` 默认注册此实现。

**自定义实现**：引入 jaravel 框架后，可提供具体的 `PluginIntegration` 实现，向插件注入 auth、cache、database 等服务：

```java
@Bean
public PluginIntegration pluginIntegration(AuthManager authManager,
                                            CacheManager cacheManager) {
    return new PluginIntegration() {
        @Override
        public void onPluginEnabled(String pluginId, ConfigurableApplicationContext context) {
            // 向插件注入认证、缓存等服务
            context.getBeanFactory().registerSingleton("authManager", authManager);
            context.getBeanFactory().registerSingleton("cacheManager", cacheManager);
        }

        @Override
        public void onPluginDisabled(String pluginId) {
            // 清理资源
        }

        @Override
        public Set<String> getSharedPackagePrefixes() {
            return Set.of(
                "com.weacsoft.jaravel.vendor.auth",
                "com.weacsoft.jaravel.vendor.cache",
                "com.weacsoft.jaravel.vendor.database"
            );
        }

        @Override
        public List<Path> getAdditionalSharedJars() {
            return List.of(Path.of("libs/auth-1.0.0.jar"));
        }
    };
}
```

### Request/Response 集成

当 jaravel 可用时，插件方法可以使用 jaravel 的 `Request`/`Response` 而非 `HttpServletRequest`/`HttpServletResponse`：

```java
@PluginComponent("userService")
public class UserService {

    // 使用 jaravel Request/Response（jaravel 可用时）
    @PluginMapping(path = "/api/users/{id}", method = HttpMethod.GET)
    public Response getUser(Request request) {
        String id = request.routeParam("id");
        return ResponseBuilder.json(Map.of("id", id));
    }

    // 不使用 jaravel（纯 Spring Boot 模式）
    @PluginMapping(path = "/api/hello", method = HttpMethod.GET)
    public String hello(String name) {
        return "Hello, " + name;
    }
}
```

系统通过 `PluginIntegration` 接口自动检测 jaravel 是否可用：

- `createPluginRequest()` - 当 jaravel 可用时创建 `Request` 对象（返回 `null` 表示 jaravel 不可用）。
- `writePluginResponse()` - 当 jaravel 可用时处理 `Response` 返回值（返回 `false` 表示使用默认响应写入逻辑）。

**插件方法参数解析顺序**：

1. 参数类型为 `HttpServletRequest` → 直接传入 Servlet 请求对象。
2. 参数类型为 jaravel `Request` 且 integration 可用 → 调用 `createPluginRequest()` 创建并传入。
3. 简单类型（`String`/`int`/`long`/`boolean` 等）→ 从请求参数解析。

**返回值处理**：

- 当插件方法返回 jaravel `Response` 且 `writePluginResponse()` 返回 `true` 时，由 integration 接管响应写入。
- 否则按 `produces` 类型使用默认逻辑写入响应（如 JSON 序列化、字符串直写等）。

> 这种设计使同一份插件代码既能在 jaravel 环境下使用 `Request`/`Response` 的高级 API，也能在纯 Spring Boot 环境下以简单类型运行，无需修改代码。

---

## 安全说明

本模块**不提供任何 REST API 控制器**，这是出于安全考虑的设计决策。所有插件管理方法（注册、启用、禁用、卸载、路由注册/注销等）均通过 `HotPluginManager` 的公开方法调用。

**原因**：

- 插件管理操作（尤其是动态加载 JAR、注册路由）属于高危操作，若直接暴露为无鉴权的 REST API，将导致任意代码执行漏洞。
- 不同应用对管理接口的鉴权方式不同（Session / JWT / API Key 等），由应用层自行实现更灵活。

**如需 REST API**：由应用层自行实现 Controller，调用 `HotPluginManager` 的方法，并做好鉴权：

```java
@RestController
@RequestMapping("/admin/plugins")
public class PluginAdminController {

    @Autowired
    private HotPluginManager hotPluginManager;

    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")  // 应用层自行鉴权
    public String register(@RequestParam String path) {
        return hotPluginManager.registerPluginFromPath(Path.of(path), null, true);
    }

    @PostMapping("/{pluginId}/enable")
    @PreAuthorize("hasRole('ADMIN')")
    public boolean enable(@PathVariable String pluginId) {
        return hotPluginManager.enablePlugin(pluginId);
    }
}
```

---

## 目录结构

```
com.weacsoft.jaravel.vendor.plugin.jar
│
├── annotation                          # 注解与代理类
│   ├── Application                     # 插件间调用代理（主 ClassLoader 单例）
│   ├── Application.HotPluginManagerRef # 代理引用接口（内部接口）
│   ├── HttpMethod                      # HTTP 方法枚举（GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS）
│   ├── PluginComponent                 # 插件组件注解（标注 Bean 类）
│   ├── PluginMapping                   # 插件路由注解（自动注册模式，标注 HTTP 端点方法）
│   ├── PluginRoute                     # 可注册路由注解（手动注册模式，标记可用路由）
│   └── SharedService                   # 共享服务标记注解（标注共享接口）
│
├── autoconfigure                       # Spring Boot 自动装配
│   ├── PluginJarAutoConfiguration      # 自动装配类（注册核心 Bean）
│   └── PluginJarProperties             # 配置属性（jaravel.plugin-jar.*）
│
├── classloader                         # 三级类加载器
│   ├── PluginClassLoader               # 插件类加载器（每插件一个，extends URLClassLoader）
│   └── SharedClassLoader               # 共享类加载器（可热替换，extends URLClassLoader）
│
├── integration                         # Jaravel 框架集成扩展点
│   ├── PluginIntegration               # 集成接口（插件启用/禁用回调、共享包配置）
│   └── DefaultPluginIntegration        # 默认空操作实现
│
├── manager                             # 核心管理器
│   └── HotPluginManager                # 插件管理器（注册/卸载/启用/禁用/路由/热更新）
│
├── model                               # 数据模型
│   ├── PluginInfo                      # 插件元数据（pluginId/version/state/依赖/路由等）
│   ├── PluginInfo.State                # 插件状态枚举（UPLOADED/ENABLED/DISABLED）
│   ├── RouteInfo                       # 路由信息（path/method/beanName/methodName/produces）
│   └── SharedInterfaceDescriptor       # 共享接口描述符（interfaceName/pluginId/beanName/methodName/description）
│
├── persistence                         # 元数据持久化
│   ├── MetadataPersistence             # 持久化接口（save/load/loadAll/delete）
│   └── JsonMetadataPersistence         # JSON 文件持久化实现（默认）
│
├── registrar                           # Bean 与路由注册器
│   ├── PluginBeanRegistrar             # 插件 Bean 注册器（注册/注销 Spring Bean）
│   ├── PluginRouteHandler              # 路由处理器（代理处理 HTTP 请求，反射调用插件方法）
│   └── PluginRouteRegistrar            # 路由注册器（注册/注销 Spring MVC 路由映射）
│
└── scanner                             # ASM 字节码扫描器
    ├── SharedDependencyScanner          # 字节码扫描器（扫描组件/依赖/路由）
    ├── SharedDependencyScanner.ScanResult        # 扫描结果（组件类/共享依赖/路由信息）
    ├── SharedDependencyScanner.RouteScanInfo     # 路由扫描信息（path/method/beanName/methodName/produces）
    ├── SharedDependencyScanner.ClassScannerVisitor       # 类访问器（扫描 @PluginComponent/@SharedService）
    ├── SharedDependencyScanner.MethodScannerVisitor      # 方法访问器（扫描 @PluginMapping/@PluginRoute）
    └── SharedDependencyScanner.PluginMappingAnnotationVisitor  # 注解访问器（解析 @PluginMapping/@PluginRoute 属性）
```

### 关键类说明

| 类 | 职责 |
|---|---|
| `HotPluginManager` | 插件系统核心管理器，协调 ClassLoader、Bean 注册器、路由注册器、持久化等组件，提供全部插件管理 API 与共享接口注册/调用能力 |
| `PluginClassLoader` | 插件级类加载器，实现三级隔离的类加载顺序（Application 代理 → 共享包 → 插件自身 → 父委托） |
| `SharedClassLoader` | 共享类加载器，加载共享接口 JAR 和注解类，支持整体热替换 |
| `SharedDependencyScanner` | ASM 字节码扫描器，在不加载类的前提下提取组件类、共享依赖和路由映射 |
| `PluginBeanRegistrar` | 插件 Bean 注册器，通过 `DefaultListableBeanFactory` 动态注册/注销插件 Bean |
| `PluginRouteRegistrar` | 路由注册器，通过 `RequestMappingHandlerMapping` 动态注册/注销 Spring MVC 路由 |
| `PluginRouteHandler` | 路由处理器，代理处理所有插件路由的 HTTP 请求，反射调用插件方法并写入响应 |
| `JsonMetadataPersistence` | JSON 文件持久化实现，将插件元数据存储为 `metadata.json` |
| `SharedInterfaceDescriptor` | 共享接口描述符，封装接口名、插件 ID、Bean 名、方法名和描述 |
| `Application` | 插件间调用代理，通过静态方法 `getService()` 跨插件获取服务实例，通过 `registerSharedInterface()` / `invokeSharedInterface()` 注册和调用共享接口 |
