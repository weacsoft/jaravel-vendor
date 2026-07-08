# plugin-jar-multi-tenant AI-API Reference

> Module: `plugin-jar-multi-tenant` | Package: `com.weacsoft.jaravel.vendor.plugin.jar.multitenant` | Version: 0.1.1

## Overview

plugin-jar-multi-tenant 模块为 JAR 插件系统提供多租户隔离能力。通过引入本模块，系统自动以租户感知版本替换原版（plugin-jar-core）的 `PluginBeanRegistrar`、`PluginRouteRegistrar` 和 `HotPluginManager`，使不同租户可加载同名插件而不发生 Bean 或路由冲突。

核心机制：插件 ID 采用 `tenantId + separator + basePluginId` 格式（如 `studentA@blog`），在启用/禁用插件时由 `TenantAwareHotPluginManager` 从 pluginId 提取租户 ID 并注入 `TenantContext`（ThreadLocal），下游的 `TenantAwarePluginBeanRegistrar` 和 `TenantAwarePluginRouteRegistrar` 读取上下文对 Bean 名称和路由路径自动前缀化。命名规则由 `TenantNaming` 统一封装：Bean 名称形如 `studentA:blogController`，路由路径形如 `/studentA/blog/list`。

向后兼容：当 pluginId 不含分隔符时（如 `blog`），`TenantNaming.extractTenant` 返回 null，不设置租户上下文，行为与原版完全一致（单例插件模式）。当通过 `MultiTenantProperties.enabled=false` 禁用本模块时，多租户自动装配不生效，系统回退到默认 `PluginJarAutoConfiguration`。

## Classes & Interfaces

### MultiTenantAutoConfiguration
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.multitenant`
- **Annotations**: `@AutoConfiguration`, `@AutoConfigureBefore(name = "com.weacsoft.jaravel.vendor.plugin.jar.autoconfigure.PluginJarAutoConfiguration")`, `@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)`, `@ConditionalOnClass(HotPluginManager.class)`, `@ConditionalOnProperty(prefix = "jaravel.plugin-jar.multi-tenant", name = "enabled", havingValue = "true", matchIfMissing = true)`, `@EnableConfigurationProperties(MultiTenantProperties.class)`
- **Description**: 多租户插件自动装配。通过 `@AutoConfigureBefore` 在 `PluginJarAutoConfiguration` 之前执行，提供租户感知版本的 `PluginBeanRegistrar`、`PluginRouteRegistrar` 和 `HotPluginManager` Bean。由于原版使用 `@ConditionalOnMissingBean`，原版自动装配会跳过这些 Bean 的创建，仅创建 `MetadataPersistence` 和 `PluginIntegration` 等未被覆盖的 Bean。激活条件：classpath 中存在 `plugin-jar-multi-tenant`、`jaravel.plugin-jar.multi-tenant.enabled=true`（默认 true）、Web Servlet 环境。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `pluginBeanRegistrar` | `ConfigurableApplicationContext applicationContext` | `PluginBeanRegistrar` | 创建租户感知的 Bean 注册器（`@Bean`, `@ConditionalOnMissingBean`），返回 `TenantAwarePluginBeanRegistrar` 实例 |
| `pluginRouteRegistrar` | `RequestMappingHandlerMapping handlerMapping, PluginBeanRegistrar beanRegistrar, PluginIntegration integration` | `PluginRouteRegistrar` | 创建租户感知的路由注册器（`@Bean`, `@ConditionalOnMissingBean`），返回 `TenantAwarePluginRouteRegistrar` 实例 |
| `hotPluginManager` | `PluginJarProperties properties, PluginBeanRegistrar beanRegistrar, PluginRouteRegistrar routeRegistrar, MetadataPersistence persistence, PluginIntegration integration, MultiTenantProperties mtProperties` | `HotPluginManager` | 创建租户感知的热插件管理器（`@Bean`, `@ConditionalOnMissingBean`），返回 `TenantAwareHotPluginManager` 实例。初始化时解析插件目录、定位核心 JAR、初始化共享 ClassLoader，并在 `autoRestore=true` 时自动恢复已持久化插件 |

### MultiTenantProperties
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.multitenant`
- **Annotations**: `@ConfigurationProperties(prefix = "jaravel.plugin-jar.multi-tenant")`
- **Description**: 多租户插件配置属性，前缀 `jaravel.plugin-jar.multi-tenant`。当 `enabled=false` 时多租户自动装配不生效，系统回退到默认的 `PluginJarAutoConfiguration`（单例插件模式）。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `isEnabled` | - | `boolean` | 是否启用多租户插件模式，默认 true |
| `setEnabled` | `boolean enabled` | `void` | 设置是否启用多租户插件模式 |
| `getSeparator` | - | `String` | 获取 pluginId 中的租户分隔符，默认 `@`（如 `studentA@blog` 中的 `@`） |
| `setSeparator` | `String separator` | `void` | 设置租户分隔符 |

#### Usage Example
```yaml
# application.yml
jaravel:
  plugin-jar:
    multi-tenant:
      enabled: true        # 引入本模块后默认启用
      separator: "@"       # pluginId 中的租户分隔符
```

### TenantAwareHotPluginManager
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.multitenant`
- **Extends**: `com.weacsoft.jaravel.vendor.plugin.jar.manager.HotPluginManager`
- **Description**: 租户感知的热插件管理器。继承 `HotPluginManager`，在 `enablePlugin` 和 `disablePlugin` 前后注入 `TenantContext`，使下游的 `TenantAwarePluginBeanRegistrar` 和 `TenantAwarePluginRouteRegistrar` 能感知当前租户并自动前缀化。工作原理：从 pluginId 提取租户 ID，通过 `TenantContext.setCurrentTenant` 注入 ThreadLocal，调用父类方法使内部 Bean 注册和路由注册读取 ThreadLocal 进行前缀化，finally 块中 `TenantContext.clear()` 清理 ThreadLocal。当 pluginId 不含分隔符时，行为与父类完全一致。同时重写 `getServiceFromPlugin` 和 `unregisterRoute`，使用前缀化的名称/路径查找和清理，解决父类用原始名称查找导致的问题。还提供 `registerPluginForTenant`、`enablePluginForTenant` 等便捷方法，自动拼接 `tenantId + separator + pluginId`。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `TenantAwareHotPluginManager` | `Path pluginsDir, PluginBeanRegistrar beanRegistrar, PluginRouteRegistrar routeRegistrar, MetadataPersistence persistence, PluginIntegration integration, boolean autoRegister, String separator` | - | 构造租户感知的热插件管理器，`beanRegistrar` 应为 `TenantAwarePluginBeanRegistrar`，`routeRegistrar` 应为 `TenantAwarePluginRouteRegistrar` |
| `enablePlugin` | `String pluginId` | `boolean` | 启用插件，自动注入租户上下文后委托父类执行（覆写父类） |
| `disablePlugin` | `String pluginId` | `boolean` | 禁用插件，自动注入租户上下文后委托父类执行（覆写父类） |
| `getServiceFromPlugin` | `String pluginId, String beanName` | `Object` | 从指定插件获取服务 Bean，自动使用前缀化的 Bean 名称查找（覆写父类），不存在返回 null |
| `unregisterRoute` | `String pluginId, String path, String httpMethod` | `boolean` | 注销单条路由，额外用前缀化路径清理 routeHandler（覆写父类） |
| `registerRouteAlias` | `String pluginId, String existingPath, String aliasPath, String httpMethod` | `boolean` | 为已注册的路由注册别名路径，自动注入租户上下文（覆写父类） |
| `registerRouteAlias` | `String pluginId, String existingPath, String aliasPath` | `boolean` | 为已注册的路由注册别名路径（自动检测 HTTP 方法），自动注入租户上下文（覆写父类） |
| `registerPluginForTenant` | `Path jarFile, String tenantId, String pluginId, boolean persist` | `String` | 为指定租户注册插件，将 pluginId 拼接为 `tenantId + separator + pluginId` 后委托父类，返回完整 pluginId |
| `enablePluginForTenant` | `String tenantId, String pluginId` | `boolean` | 启用指定租户的插件，自动拼接完整 pluginId 后调用 `enablePlugin` |
| `disablePluginForTenant` | `String tenantId, String pluginId` | `boolean` | 禁用指定租户的插件，自动拼接完整 pluginId 后调用 `disablePlugin` |
| `uninstallPluginForTenant` | `String tenantId, String pluginId` | `boolean` | 卸载指定租户的插件，自动拼接完整 pluginId 后调用父类 `uninstallPlugin` |
| `getPluginForTenant` | `String tenantId, String pluginId` | `PluginInfo` | 获取指定租户的插件信息，自动拼接完整 pluginId 后调用父类 `getPlugin`，不存在返回 null |
| `getPluginsByTenant` | `String tenantId` | `List<PluginInfo>` | 获取指定租户的所有插件，遍历所有插件筛选 pluginId 以 `tenantId + separator` 开头的插件 |
| `getSeparator` | - | `String` | 返回租户分隔符 |

#### Usage Example
```java
@Autowired
private HotPluginManager pluginManager; // 实际注入 TenantAwareHotPluginManager

// 方式一：使用带租户前缀的 pluginId
String pluginId = "studentA@blog";
((TenantAwareHotPluginManager) pluginManager).enablePlugin(pluginId);

// 方式二：使用便捷 API（自动拼接 pluginId）
String fullId = ((TenantAwareHotPluginManager) pluginManager)
        .registerPluginForTenant(Paths.get("/plugins/blog-1.0.0.jar"), "studentA", "blog", true);
((TenantAwareHotPluginManager) pluginManager).enablePluginForTenant("studentA", "blog");

// 获取该租户所有插件
List<PluginInfo> tenantPlugins = ((TenantAwareHotPluginManager) pluginManager)
        .getPluginsByTenant("studentA");
```

### TenantAwarePluginBeanRegistrar
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.multitenant`
- **Extends**: `com.weacsoft.jaravel.vendor.plugin.jar.registrar.PluginBeanRegistrar`
- **Description**: 租户感知的插件 Bean 注册器。继承 `PluginBeanRegistrar`，在注册/注销 Bean 时根据 `TenantContext` 自动对 Bean 名称添加租户前缀（`tenantId:originalBeanName`，如 `studentA:blogController`），避免不同租户的同名 Bean 互相覆盖。当 `TenantContext.getCurrentTenant()` 返回 null 时（非多租户插件），行为与父类完全一致，保持向后兼容。注意：父类将原始 Bean 名称存入 `PluginInfo.registeredBeanNames`，注销时传入原始名称，本类再次读取租户 ID 前缀化后委托父类注销。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `TenantAwarePluginBeanRegistrar` | `ConfigurableApplicationContext applicationContext` | - | 构造租户感知的 Bean 注册器 |
| `registerBean` | `String beanName, Class<?> beanClass` | `boolean` | 注册 Bean，自动添加租户前缀后委托父类注册（覆写父类），注册成功返回 true |
| `unregisterBean` | `String beanName` | `void` | 注销 Bean，根据当前租户上下文前缀化后委托父类注销（覆写父类） |
| `unregisterBeans` | `Set<String> beanNames` | `void` | 批量注销 Bean，逐个调用 `unregisterBean`（覆写父类） |

#### Usage Example
```java
// 通常不直接使用，由 TenantAwareHotPluginManager 在启用/禁用插件时内部调用
// 启用插件前由 TenantAwareHotPluginManager 注入 TenantContext
TenantContext.setCurrentTenant("studentA");
try {
    beanRegistrar.registerBean("blogController", BlogController.class);
} finally {
    TenantContext.clear();
}
// 实际注册的 Bean 名称为 studentA:blogController
```

### TenantAwarePluginRouteRegistrar
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.multitenant`
- **Extends**: `com.weacsoft.jaravel.vendor.plugin.jar.registrar.PluginRouteRegistrar`
- **Description**: 租户感知的插件路由注册器。继承 `PluginRouteRegistrar`，在注册路由时根据 `TenantContext` 自动对路由路径和 Bean 名称添加租户前缀，避免不同租户的同路径路由冲突。命名规则：路由路径形如 `/studentA/blog/list`，Bean 名称形如 `studentA:blogController`。当 `TenantContext.getCurrentTenant()` 返回 null 时，行为与父类完全一致。额外维护 `tenantRouteInfos` 映射表（pluginId -> 前缀化 RouteInfo 列表），在 `unregisterRoutes` 时同步清理 `PluginRouteHandler` 的路由注册表（原版未实现此清理）。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `TenantAwarePluginRouteRegistrar` | `RequestMappingHandlerMapping handlerMapping, PluginBeanRegistrar beanRegistrar, PluginIntegration integration` | - | 构造租户感知的路由注册器 |
| `registerRoute` | `String pluginId, RouteInfo route` | `boolean` | 注册单条路由，自动添加租户前缀（路径和 Bean 名称均前缀化）后委托父类注册，并记录到 `tenantRouteInfos`（覆写父类）。route 为 null 或路径/方法为 null 时返回 false |
| `unregisterRoutes` | `String pluginId` | `void` | 注销插件的所有路由，先委托父类注销 Spring MVC 映射，再从 `tenantRouteInfos` 取出前缀化路由逐条清理 `PluginRouteHandler` 的路由注册表（覆写父类） |
| `registerRouteAlias` | `String pluginId, String existingPath, String aliasPath, String httpMethod` | `boolean` | 为已注册的路由注册别名路径，自动添加租户前缀（原路径和别名路径均前缀化）后委托父类（覆写父类） |
| `registerRouteAlias` | `String pluginId, String existingPath, String aliasPath` | `boolean` | 为已注册的路由注册别名路径（自动检测 HTTP 方法），自动添加租户前缀（覆写父类） |

#### Usage Example
```java
// 通常不直接使用，由 TenantAwareHotPluginManager 在启用/禁用插件时内部调用
// 启用插件前由 TenantAwareHotPluginManager 注入 TenantContext
TenantContext.setCurrentTenant("studentA");
try {
    RouteInfo route = new RouteInfo("/blog/list", HttpMethod.GET, "blogController", "list", "application/json");
    routeRegistrar.registerRoute("studentA@blog", route);
} finally {
    TenantContext.clear();
}
// 实际注册的路由路径为 /studentA/blog/list，Bean 名称为 studentA:blogController
```

### TenantContext
- **Type**: final class（工具类，私有构造，静态方法）
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.multitenant`
- **Description**: 租户上下文（ThreadLocal）。在插件启用/禁用期间，由 `TenantAwareHotPluginManager` 将当前租户 ID 注入 ThreadLocal，使 `TenantAwarePluginBeanRegistrar` 和 `TenantAwarePluginRouteRegistrar` 能够感知当前正在处理的租户，从而对 Bean 名称和路由路径进行前缀化。生命周期：`enablePlugin(pluginId)` 调用前注入 → 注册 Bean/路由期间读取 → finally 块中清理。线程安全：基于 `ThreadLocal`，每个线程独立。由于 `HotPluginManager` 使用写锁串行化插件状态变更，同一时间只有一个线程在启用/禁用插件，不存在并发问题。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `setCurrentTenant` | `String tenantId` | `void` | 设置当前租户 ID，为 null 表示无租户（非多租户模式） |
| `getCurrentTenant` | - | `String` | 获取当前租户 ID，无租户时返回 null |
| `clear` | - | `void` | 清除当前线程的租户上下文，必须在 finally 块中调用，避免 ThreadLocal 泄漏 |

#### Usage Example
```java
// 注入租户上下文
TenantContext.setCurrentTenant("studentA");
try {
    // 在此期间注册的 Bean/路由会自动前缀化
    doPluginWork();
} finally {
    TenantContext.clear();
}
```

### TenantNaming
- **Type**: final class（工具类，私有构造，静态方法）
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.multitenant`
- **Description**: 租户命名工具。提供统一的 Bean 名称前缀化和路由路径前缀化逻辑，供 `TenantAwarePluginBeanRegistrar` 和 `TenantAwarePluginRouteRegistrar` 共用，确保两者使用一致的命名规则。命名规则：pluginId 格式为 `tenantId + separator + basePluginId`（如 `studentA@blog`）；Bean 名称前缀为 `tenantId + ":" + beanName`（如 `studentA:blogController`）；路由路径前缀为 `"/" + tenantId + path`（如 `/studentA/blog/list`）。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `extractTenant` | `String pluginId, String separator` | `String` | 从 pluginId 中提取租户 ID。若 pluginId 不包含分隔符或参数非法，返回 null（表示非多租户插件） |
| `extractBasePluginId` | `String pluginId, String separator` | `String` | 从 pluginId 中提取基础插件 ID（去除租户前缀）。无租户前缀时返回原值 |
| `prefixBeanName` | `String tenantId, String beanName` | `String` | 构造租户前缀化的 Bean 名称，如 `studentA:blogController`。tenantId 为空时返回原 beanName |
| `prefixRoutePath` | `String tenantId, String path` | `String` | 构造租户前缀化的路由路径，如 `/studentA/blog/list`。tenantId 为空时返回原 path；path 为空时返回 `/{tenantId}`；path 以 `/` 开头时返回 `/{tenantId}{path}`，否则返回 `/{tenantId}/{path}` |
| `buildPluginId` | `String tenantId, String basePluginId, String separator` | `String` | 构造完整的 pluginId（租户 + 分隔符 + 基础插件 ID）。tenantId 为空时返回 basePluginId |

#### Usage Example
```java
// 提取租户 ID
String tenantId = TenantNaming.extractTenant("studentA@blog", "@"); // "studentA"
String baseId = TenantNaming.extractBasePluginId("studentA@blog", "@"); // "blog"

// 前缀化
String beanName = TenantNaming.prefixBeanName("studentA", "blogController"); // "studentA:blogController"
String routePath = TenantNaming.prefixRoutePath("studentA", "/blog/list"); // "/studentA/blog/list"

// 拼接完整 pluginId
String fullId = TenantNaming.buildPluginId("studentA", "blog", "@"); // "studentA@blog"
```

## Annotations & Enums

本模块不包含任何注解或枚举类型。所有类型均为普通类，复用 plugin-jar-core 模块中的 `@PluginComponent`、`@PluginMapping` 等注解与 `HttpMethod`、`PluginInfo.State` 等枚举。
