# plugin-jar-database AI-API Reference

> Module: `plugin-jar-database` | Package: `com.weacsoft.jaravel.vendor.plugin.jar.database` | Version: 0.1.1

## Overview

plugin-jar-database 模块为 JAR 插件系统提供基于数据库的元数据持久化实现。`ModelMetadataPersistence` 实现 `MetadataPersistence` 接口，将插件信息（ID、JAR 路径、版本、状态、组件类、路由映射等）存储到数据库表 `jaravel_plugin_metadata`，替代默认的 JSON 文件持久化方案。适用于多实例部署场景，确保所有实例共享同一份插件元数据。`Migration_2024_01_03_CreatePluginMetadataTable` 自动创建所需数据库表。

## Classes & Interfaces

### ModelMetadataPersistence
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.database.persistence`
- **Implements**: `com.weacsoft.jaravel.vendor.plugin.jar.persistence.MetadataPersistence`
- **Description**: 基于数据库的插件元数据持久化实现。使用 `JdbcTemplate` 操作 `jaravel_plugin_metadata` 表，`PluginInfo` 对象通过 Jackson `ObjectMapper` 序列化为 JSON 存储在 `metadata` 列。支持 CRUD 操作和批量加载。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `ModelMetadataPersistence` | `JdbcTemplate jdbcTemplate, ObjectMapper objectMapper` | - | 构造数据库元数据持久化器 |
| `save` | `PluginInfo pluginInfo` | `void` | 保存插件元数据（INSERT ON DUPLICATE KEY UPDATE） |
| `load` | `String pluginId` | `PluginInfo` | 按 ID 加载插件元数据，不存在返回 null |
| `loadAll` | - | `List<PluginInfo>` | 加载所有插件元数据记录 |
| `delete` | `String pluginId` | `void` | 按 ID 删除插件元数据 |
| `exists` | `String pluginId` | `boolean` | 检查插件元数据是否存在 |

#### Usage Example
```java
@Autowired
private MetadataPersistence persistence;  // 自动注入 ModelMetadataPersistence

// 保存插件信息
PluginInfo info = new PluginInfo();
info.setPluginId("my-plugin");
info.setJarPath("/plugins/my-plugin-1.0.0.jar");
info.setVersion("1.0.0");
info.setState(PluginInfo.State.ENABLED);
persistence.save(info);

// 加载插件信息
PluginInfo loaded = persistence.load("my-plugin");

// 加载所有插件
List<PluginInfo> all = persistence.loadAll();

// 删除
persistence.delete("my-plugin");
```

### PluginMetadataModel
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.database.model`
- **Description**: 插件元数据数据库模型，映射 `jaravel_plugin_metadata` 表结构。作为 `ModelMetadataPersistence` 的行映射器（RowMapper）目标类。

#### Fields

| Field | Type | Column | Description |
|-------|------|--------|-------------|
| `id` | `Long` | `id` | 自增主键 |
| `pluginId` | `String` | `plugin_id` | 插件 ID（唯一索引） |
| `jarPath` | `String` | `jar_path` | JAR 文件路径 |
| `version` | `String` | `version` | 插件版本 |
| `state` | `String` | `state` | 插件状态（LOADED/ENABLED/DISABLED） |
| `metadata` | `String` | `metadata` | 完整 PluginInfo JSON |
| `createdAt` | `Long` | `created_at` | 创建时间戳 |
| `updatedAt` | `Long` | `updated_at` | 更新时间戳 |

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `getId` | - | `Long` | 获取主键 ID |
| `setId` | `Long id` | `void` | 设置主键 ID |
| `getPluginId` | - | `String` | 获取插件 ID |
| `setPluginId` | `String pluginId` | `void` | 设置插件 ID |
| `getJarPath` | - | `String` | 获取 JAR 路径 |
| `setJarPath` | `String jarPath` | `void` | 设置 JAR 路径 |
| `getVersion` | - | `String` | 获取版本 |
| `setVersion` | `String version` | `void` | 设置版本 |
| `getState` | - | `String` | 获取状态 |
| `setState` | `String state` | `void` | 设置状态 |
| `getMetadata` | - | `String` | 获取 metadata JSON |
| `setMetadata` | `String metadata` | `void` | 设置 metadata JSON |
| `getCreatedAt` | - | `Long` | 获取创建时间 |
| `setCreatedAt` | `Long createdAt` | `void` | 设置创建时间 |
| `getUpdatedAt` | - | `Long` | 获取更新时间 |
| `setUpdatedAt` | `Long updatedAt` | `void` | 设置更新时间 |

### Migration_2024_01_03_CreatePluginMetadataTable
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.database.migration`
- **Extends**: `com.weacsoft.jaravel.vendor.database.migration.Migration`
- **Description**: 数据库迁移脚本，创建 `jaravel_plugin_metadata` 表。在应用启动时由迁移系统自动执行，确保表结构存在。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `up` | `JdbcTemplate jdbcTemplate` | `void` | 执行迁移：创建 `jaravel_plugin_metadata` 表 |
| `down` | `JdbcTemplate jdbcTemplate` | `void` | 回滚迁移：删除 `jaravel_plugin_metadata` 表 |
| `getId` | - | `String` | 获取迁移 ID：`2024_01_03_create_plugin_metadata_table` |

#### Usage Example
```sql
-- 迁移创建的表结构
CREATE TABLE jaravel_plugin_metadata (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    plugin_id   VARCHAR(255) NOT NULL UNIQUE,
    jar_path    VARCHAR(1024) NOT NULL,
    version     VARCHAR(50),
    state       VARCHAR(20) NOT NULL,
    metadata    TEXT,
    created_at  BIGINT,
    updated_at  BIGINT,
    INDEX idx_plugin_id (plugin_id)
);
```

### PluginJarDatabaseAutoConfiguration
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.database.autoconfigure`
- **Annotations**: `@AutoConfiguration`, `@AutoConfigureAfter(PluginJarAutoConfiguration)`, `@ConditionalOnClass({ModelMetadataPersistence, JdbcTemplate})`, `@ConditionalOnBean(JdbcTemplate)`, `@ConditionalOnProperty(prefix = "jaravel.plugin-jar.database", name = "enabled", havingValue = "true", matchIfMissing = true)`
- **Description**: 数据库元数据持久化自动装配。当 `JdbcTemplate` 存在时，创建 `ModelMetadataPersistence` Bean 并覆盖 `plugin-jar-core` 中的默认 `JsonMetadataPersistence`（通过 `@Primary` 或 `BeanPostProcessor` 替换）。同时注册迁移脚本。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `modelMetadataPersistence` | `JdbcTemplate jdbcTemplate, ObjectMapper objectMapper` | `ModelMetadataPersistence` | 创建数据库元数据持久化器 Bean（`@Bean`, `@Primary`, `@ConditionalOnMissingBean`） |

#### Usage Example
```yaml
# application.yml
jaravel:
  plugin-jar:
    enabled: true
    database:
      enabled: true  # 使用数据库持久化（默认 true，当 JdbcTemplate 存在时）
```

```java
// 自动装配后，HotPluginManager 自动使用 ModelMetadataPersistence
// 无需额外配置，插件元数据存储在数据库中
@Autowired
private HotPluginManager pluginManager;

// 所有操作自动持久化到数据库
pluginManager.load(Paths.get("/plugins/my-plugin.jar"));
pluginManager.enable("my-plugin");
```
