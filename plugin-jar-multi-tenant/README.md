# plugin-jar-multi-tenant

> 包名：com.weacsoft.jaravel.vendor.plugin.jar.multitenant
> 版本：0.1.1

JAR 插件多租户支持模块。引入后自动激活多租户插件模式，同一 JAR 可按租户隔离地重复加载，Bean 名称和路由路径自动按租户前缀化。

## 核心类

| 类 | 说明 |
|---|------|
| `TenantContext` | ThreadLocal 租户上下文持有器 |
| `TenantNaming` | 租户命名工具（前缀化、提取、拼接） |
| `MultiTenantProperties` | 配置属性（前缀 `jaravel.plugin-jar.multi-tenant`） |
| `TenantAwarePluginBeanRegistrar` | 租户感知 Bean 注册器 |
| `TenantAwarePluginRouteRegistrar` | 租户感知路由注册器 |
| `TenantAwareHotPluginManager` | 租户感知热插件管理器 |
| `MultiTenantAutoConfiguration` | 自动装配（覆盖原版三个 Bean） |

## 使用方式

引入 Maven 依赖后自动激活，无需额外配置：

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>plugin-jar-multi-tenant</artifactId>
    <version>0.1.1</version>
</dependency>
```

## 配置

```yaml
jaravel:
  plugin-jar:
    multi-tenant:
      enabled: true               # 引入模块后默认启用
      separator: "_"              # 租户前缀分隔符，默认下划线
```

## 工作原理

引入本模块后，自动装配会覆盖 `plugin-jar-core` 的三个 Bean（`HotPluginManager`、`PluginBeanRegistrar`、`PluginRouteRegistrar`），替换为租户感知版本：

1. **租户上下文**：通过 `TenantContext.setCurrentTenant(tenantId)` 设置当前租户（基于 ThreadLocal）
2. **Bean 隔离**：同一 JAR 插件为不同租户加载时，Bean 名称自动前缀化（如 `tenantA_userService`）
3. **路由隔离**：路由路径自动添加租户前缀（如 `/tenantA/api/users`）
4. **向后兼容**：未设置租户时退化为普通插件行为

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
