# plugin-jar-multi-tenant

> 包名：com.weacsoft.jaravel.vendor.plugin.jar.multitenant
> 版本：0.1.2

JAR 插件多租户支持模块。引入后自动激活多租户插件模式，同一 JAR 可按租户隔离地重复加载，Bean 名称和路由路径自动按租户前缀化。同时为共享接口提供租户感知能力，多租户场景下共享接口调用自动处理 Bean 名称前缀化。

---

## 目录

- [核心类](#核心类)
- [使用方式](#使用方式)
- [配置](#配置)
- [工作原理](#工作原理)
- [共享接口](#共享接口)
  - [设计理念](#设计理念)
  - [注册共享接口](#注册共享接口)
  - [调用共享接口](#调用共享接口)
  - [模板方法模式说明](#模板方法模式说明)
- [完整示例](#完整示例)

---

## 核心类

| 类 | 说明 |
|---|------|
| `TenantContext` | ThreadLocal 租户上下文持有器 |
| `TenantNaming` | 租户命名工具（前缀化、提取、拼接） |
| `MultiTenantProperties` | 配置属性（前缀 `jaravel.plugin-jar.multi-tenant`） |
| `TenantAwarePluginBeanRegistrar` | 租户感知 Bean 注册器 |
| `TenantAwarePluginRouteRegistrar` | 租户感知路由注册器 |
| `TenantAwareHotPluginManager` | 租户感知热插件管理器（含共享接口租户感知） |
| `MultiTenantAutoConfiguration` | 自动装配（覆盖原版三个 Bean） |

## 使用方式

引入 Maven 依赖后自动激活，无需额外配置：

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>plugin-jar-multi-tenant</artifactId>
    <version>0.1.2</version>
</dependency>
```

## 配置

```yaml
jaravel:
  plugin-jar:
    multi-tenant:
      enabled: true               # 引入模块后默认启用
      separator: "@"              # pluginId 中的租户分隔符，默认 @
```

| 配置项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `jaravel.plugin-jar.multi-tenant.enabled` | `boolean` | `true` | 是否启用多租户插件模式。设为 `false` 则回退到默认单例模式 |
| `jaravel.plugin-jar.multi-tenant.separator` | `String` | `@` | pluginId 中的租户分隔符（如 `studentA@blog` 中的 `@`） |

## 工作原理

引入本模块后，自动装配会覆盖 `plugin-jar-core` 的三个 Bean（`HotPluginManager`、`PluginBeanRegistrar`、`PluginRouteRegistrar`），替换为租户感知版本：

1. **租户上下文**：通过 `TenantContext.setCurrentTenant(tenantId)` 设置当前租户（基于 ThreadLocal）
2. **Bean 隔离**：同一 JAR 插件为不同租户加载时，Bean 名称自动前缀化（如 `studentA:blogController`）
3. **路由隔离**：路由路径自动添加租户前缀（如 `/studentA/blog/list`）
4. **共享接口隔离**：多租户场景下共享接口调用时，Bean 名称自动前缀化查找，调用方无感知
5. **向后兼容**：未设置租户时（pluginId 不含分隔符）退化为普通插件行为

```java
// 设置当前租户上下文
TenantContext.setCurrentTenant("tenantA");
try {
    // 为租户 A 加载插件
    tenantPluginManager.load(Path.of("plugins/billing.jar"), "billing", true);
} finally {
    TenantContext.clear();
}
```

---

## 共享接口

plugin-jar-core 提供的共享接口机制（全手动指定、字符串驱动、反射封装、Map 传参）在多租户场景下完全可用，且由 `TenantAwareHotPluginManager` 自动处理 Bean 名称前缀化，调用方无需感知租户细节。

### 设计理念

共享接口的多租户支持遵循以下设计：

| 设计点 | 说明 |
|--------|------|
| **注册时使用原始 Bean 名** | 注册共享接口时传入原始 Bean 名称（如 `blogController`），与单租户场景完全一致 |
| **调用时自动前缀化** | `TenantAwareHotPluginManager.lookupSharedInterfaceBean()` 从 `descriptor.getPluginId()` 提取租户 ID，将 Bean 名称前缀化（如 `studentA:blogController`）后查找 |
| **模板方法模式** | 反射调用逻辑（参数解析、方法查找、返回值转换）完全复用父类 `invokeSharedInterface`，子类仅重写 Bean 查找步骤 |
| **调用方无感知** | 插件代码通过 `Application.invokeSharedInterface()` 调用时，无需关心租户前缀化，与单租户代码完全相同 |

### 注册共享接口

注册共享接口使用原始 Bean 名称，pluginId 需包含租户前缀（如 `studentA@blog`）：

```java
import com.weacsoft.jaravel.vendor.plugin.jar.annotation.Application;

// 方式一：主程序侧通过 HotPluginManager 注册
@Autowired
private HotPluginManager hotPluginManager; // 实际为 TenantAwareHotPluginManager

hotPluginManager.registerSharedInterface(
    "studentA.blog.list",        // 接口名称（全局唯一）
    "studentA@blog",             // 含租户的 pluginId
    "blogController",            // 原始 Bean 名（调用时自动前缀化为 studentA:blogController）
    "list",                      // 方法名
    "获取 studentA 的博客列表"     // 可选描述
);

// 方式二：插件侧通过 Application 静态方法注册
Application.registerSharedInterface(
    "studentA.blog.list", "studentA@blog", "blogController", "list");

// 不同租户可注册各自的共享接口
hotPluginManager.registerSharedInterface(
    "studentB.blog.list", "studentB@blog", "blogController", "list");
```

注册成功后查询所有共享接口：

```java
List<SharedInterfaceDescriptor> interfaces = hotPluginManager.getSharedInterfaces();
interfaces.forEach(d -> System.out.println(
    d.getInterfaceName() + " -> " + d.getPluginId() + ":" + d.getBeanName() + "." + d.getMethodName()));
// 输出:
// studentA.blog.list -> studentA@blog:blogController.list
// studentB.blog.list -> studentB@blog:blogController.list
```

### 调用共享接口

调用时通过接口名称即可，无需关心租户前缀化细节。参数和返回值均用 Map 表示：

```java
import com.weacsoft.jaravel.vendor.plugin.jar.annotation.Application;

// 调用 studentA 的博客列表接口
Map<String, Object> args = new HashMap<>();
args.put("page", 1);
args.put("size", 10);
Map<String, Object> resultA = Application.invokeSharedInterface("studentA.blog.list", args);

// 调用 studentB 的博客列表接口
Map<String, Object> resultB = Application.invokeSharedInterface(
    "studentB.blog.list", Map.of("page", 1, "size", 10));
```

调用流程（多租户场景下自动处理）：

```
Application.invokeSharedInterface("studentA.blog.list", args)
  │
  ▼
TenantAwareHotPluginManager.invokeSharedInterface()（继承自父类 HotPluginManager）
  │
  ├── 1. 从注册表查找 SharedInterfaceDescriptor（interfaceName="studentA.blog.list"）
  ├── 2. 检查插件 "studentA@blog" 是否已启用
  ├── 3. 调用 lookupSharedInterfaceBean(descriptor)（子类重写）
  │      ├── 从 descriptor.getPluginId()="studentA@blog" 提取租户 ID "studentA"
  │      ├── 将 descriptor.getBeanName()="blogController" 前缀化为 "studentA:blogController"
  │      └── 从 Spring 容器获取 "studentA:blogController" Bean 实例
  ├── 4. 反射查找 Bean 的 "list" 方法
  ├── 5. 按 Map 参数解析规则解析 args
  ├── 6. 反射调用方法
  └── 7. 将返回值转换为 Map 返回
```

### 模板方法模式说明

`TenantAwareHotPluginManager` 使用模板方法模式实现共享接口的租户感知：

- **父类 `HotPluginManager`** 定义了 `invokeSharedInterface()` 的完整调用流程（模板方法），其中获取 Bean 实例的步骤通过 `lookupSharedInterfaceBean()` 实现（钩子方法，`protected`）。
- **子类 `TenantAwareHotPluginManager`** 仅重写 `lookupSharedInterfaceBean()`，在查找 Bean 时添加租户前缀化逻辑，其余步骤（参数解析、反射调用、返回值转换）全部复用父类实现。

这种设计使多租户逻辑最小化，避免重复代码，且未来父类调用流程的优化自动惠及子类。

---

## 完整示例

以下示例展示多租户场景下从插件注册到共享接口调用的完整流程：

```java
// ===== 1. 为两个租户注册并启用相同的 blog 插件 =====
TenantAwareHotPluginManager manager = (TenantAwareHotPluginManager) hotPluginManager;

// 租户 studentA
manager.registerPluginForTenant(Path.of("plugins/blog-1.0.0.jar"), "studentA", "blog", true);
manager.enablePluginForTenant("studentA", "blog");

// 租户 studentB（同一 JAR，不同租户，Bean 隔离）
manager.registerPluginForTenant(Path.of("plugins/blog-1.0.0.jar"), "studentB", "blog", true);
manager.enablePluginForTenant("studentB", "blog");

// ===== 2. 为两个租户分别注册共享接口 =====
manager.registerSharedInterface(
    "studentA.blog.list", "studentA@blog", "blogController", "list", "studentA 的博客列表");
manager.registerSharedInterface(
    "studentB.blog.list", "studentB@blog", "blogController", "list", "studentB 的博客列表");

// ===== 3. 通过 Application 调用（调用方无需感知租户前缀化） =====
Map<String, Object> args = Map.of("page", 1, "size", 10);

Map<String, Object> resultA = Application.invokeSharedInterface("studentA.blog.list", args);
// 内部: lookupSharedInterfaceBean 查找 "studentA:blogController" Bean

Map<String, Object> resultB = Application.invokeSharedInterface("studentB.blog.list", args);
// 内部: lookupSharedInterfaceBean 查找 "studentB:blogController" Bean

// ===== 4. 查询与注销 =====
List<SharedInterfaceDescriptor> all = manager.getSharedInterfaces();
manager.unregisterSharedInterface("studentA.blog.list");
manager.unregisterSharedInterface("studentB.blog.list");
```

> 共享接口的完整 API 说明（参数解析规则、返回值转换规则等）详见 [plugin-jar-core README](../plugin-jar-core/README.md#共享接口)。
