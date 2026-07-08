# plugin-jar-database

> 包名：`com.weacsoft.jaravel.vendor.plugin.jar.database`
> artifactId：`plugin-jar-database`
> 定位：`plugin-jar-core` 的数据库持久化扩展模块

`plugin-jar-database` 是 plugin-jar-core 的数据库持久化扩展模块。当项目使用 jaravel 的数据库能力时，引入此模块可自动将插件元数据持久化到数据库（而非默认的 JSON 文件），重启后自动恢复插件状态。

默认情况下，plugin-jar-core 通过 `JsonMetadataPersistence` 将插件元数据写入文件系统（`plugins/{id}/metadata.json`）。本模块提供另一套实现 `ModelMetadataPersistence`，基于 jaravel `BaseModel` 的 Eloquent 模型将元数据写入 `plugin_metadata` 表。由于本模块的自动装配通过 `@AutoConfigureBefore(PluginJarAutoConfiguration.class)` 优先加载，并注册一个 `MetadataPersistence` Bean，plugin-jar-core 中带 `@ConditionalOnMissingBean(MetadataPersistence.class)` 的 JSON 持久化会被自动跳过，从而实现「引入依赖即切换到数据库持久化」的零配置体验。

---

## 目录

- [核心特性](#核心特性)
- [依赖引入](#依赖引入)
- [工作原理](#工作原理)
- [数据库表结构](#数据库表结构)
- [模型](#模型)
- [迁移](#迁移)
- [持久化逻辑](#持久化逻辑)
- [配置](#配置)
- [与 JSON 持久化的对比](#与-json-持久化的对比)
- [目录结构](#目录结构)

---

## 核心特性

- **基于 jaravel BaseModel 的 Eloquent 模型持久化**：`PluginMetadataModel` 继承 `BaseModel<PluginMetadataModel, Long>`，遵循 jaravel Eloquent「单一类同时承担实体定义与查询职责」的设计。
- **自动创建 plugin_metadata 表**：通过 jaravel 迁移系统（`Migration_2024_01_03_CreatePluginMetadataTable`）自动建表，无需手动维护 DDL。
- **自动覆盖默认的 JsonMetadataPersistence**：`PluginJarDatabaseAutoConfiguration` 通过 `@AutoConfigureBefore(PluginJarAutoConfiguration.class)` 优先于 JSON 持久化加载，注册的 `ModelMetadataPersistence` Bean 触发 plugin-jar-core 中的 `@ConditionalOnMissingBean(MetadataPersistence.class)` 条件，跳过 JSON 持久化。
- **与 jaravel 迁移系统无缝集成**：迁移类实现 `com.weacsoft.jaravel.vendor.migration.Migration` 接口，遵循 `Migration_YYYY_MM_DD_Description` 命名规范，由 `Migrator` 自动收集并按名称排序执行。
- **零配置：引入依赖即自动生效**：无需任何额外配置项，只要容器中存在 jaravel 数据源与迁移系统，即可自动完成建表与持久化切换。
- **多实例共享**：多个应用实例连接同一数据库时，可共享插件元数据，支持分布式部署场景。

---

## 依赖引入

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>plugin-jar-core</artifactId>
    <version>0.1.1</version>
</dependency>
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>plugin-jar-database</artifactId>
    <version>0.1.1</version>
</dependency>
```

> 注意：此模块依赖 `plugin-jar-core`、`database`、`migration`、`core` 模块。
>
> - `plugin-jar-core`：提供 `MetadataPersistence` 接口、`PluginInfo` / `RouteInfo` 模型、`HotPluginManager` 与 `PluginJarAutoConfiguration`。
> - `database`：提供 `BaseModel`（Eloquent Model 基类）。
> - `migration`：提供 `Migration` 接口、`Schema` / `Blueprint` 表结构构建器与 `Migrator` 迁移引擎。
> - `core`：提供 `SpringContext` 等 Facade 基础设施。

本模块自身不直接依赖 Jackson，而是通过 `plugin-jar-core` 间接引入的 `com.fasterxml.jackson.databind.ObjectMapper` 完成 `Set<String>` 与 `Set<RouteInfo>` 的 JSON 序列化。

---

## 工作原理

引入 `plugin-jar-database` 依赖后，整个切换流程如下：

1. **自动装配优先加载**：`PluginJarDatabaseAutoConfiguration` 标注了 `@AutoConfiguration` 与 `@AutoConfigureBefore(PluginJarAutoConfiguration.class)`，Spring Boot 会将其排在 plugin-jar-core 的自动装配之前处理。同时类上带有 `@ConditionalOnClass({MetadataPersistence.class, PluginMetadataModel.class})`，确保仅当相关类存在时才激活。

2. **注册 ModelMetadataPersistence Bean**：自动装配类通过 `@Bean` 方法 `modelMetadataPersistence()` 创建 `ModelMetadataPersistence` 实例（实现 `MetadataPersistence` 接口），方法上带有 `@ConditionalOnMissingBean(MetadataPersistence.class)`，允许业务方自定义覆盖。

3. **跳过 JSON 持久化**：`PluginJarAutoConfiguration` 中的 `metadataPersistence(...)` Bean 方法同样带有 `@ConditionalOnMissingBean(MetadataPersistence.class)`。由于步骤 2 已注册 `MetadataPersistence` Bean，条件不满足，JSON 持久化（`JsonMetadataPersistence`）被跳过。

4. **自动建表**：jaravel 迁移系统在启动时（`jaravel.migration.auto-run=true`）自动执行 `Migration_2024_01_03_CreatePluginMetadataTable`，创建 `plugin_metadata` 表。

5. **使用数据库持久化**：`HotPluginManager` 注入容器中唯一的 `MetadataPersistence` Bean（即 `ModelMetadataPersistence`），后续的 `save` / `load` / `loadAll` / `delete` 操作均走数据库。

```
引入依赖
  │
  ▼
PluginJarDatabaseAutoConfiguration (@AutoConfigureBefore)
  │  注册 ModelMetadataPersistence Bean
  ▼
PluginJarAutoConfiguration
  │  @ConditionalOnMissingBean(MetadataPersistence.class) 命中已有 Bean
  │  → 跳过 JsonMetadataPersistence
  ▼
HotPluginManager 注入 ModelMetadataPersistence
  │
  ▼
jaravel 迁移系统自动执行 Migration_2024_01_03_CreatePluginMetadataTable
  │  → 创建 plugin_metadata 表
  ▼
插件元数据读写均走数据库
```

---

## 数据库表结构

`plugin_metadata` 表由 `Migration_2024_01_03_CreatePluginMetadataTable` 自动创建：

| 列名 | 类型 | 说明 |
|------|------|------|
| `id` | `BIGINT`（自增主键） | 主键 |
| `plugin_id` | `VARCHAR(100)` | 插件 ID |
| `version` | `VARCHAR(50)` | 插件版本 |
| `jar_path` | `VARCHAR(500)` | JAR 文件路径 |
| `state` | `VARCHAR(20)` | 状态（`UPLOADED` / `ENABLED` / `DISABLED`） |
| `shared_class_dependencies` | `TEXT` | 共享类依赖（JSON 数组） |
| `component_classes` | `TEXT` | 组件类列表（JSON 数组） |
| `route_mappings` | `TEXT` | 路由映射（JSON 数组） |
| `error_message` | `VARCHAR(500)` | 错误信息（可空） |
| `persisted` | `BOOLEAN`（`TINYINT(1)`） | 是否磁盘持久化 |
| `created_at` | `TIMESTAMP` | 创建时间（nullable） |
| `updated_at` | `TIMESTAMP` | 更新时间（nullable） |

建表 DDL（MySQL 方言示例）：

```sql
CREATE TABLE `plugin_metadata` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `plugin_id` VARCHAR(100) NOT NULL,
    `version` VARCHAR(50) NOT NULL,
    `jar_path` VARCHAR(500) NOT NULL,
    `state` VARCHAR(20) NOT NULL,
    `shared_class_dependencies` TEXT NOT NULL,
    `component_classes` TEXT NOT NULL,
    `route_mappings` TEXT NOT NULL,
    `error_message` VARCHAR(500) NULL,
    `persisted` TINYINT(1) NOT NULL,
    `created_at` TIMESTAMP NULL,
    `updated_at` TIMESTAMP NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

> 说明：`state` 列存储 `PluginInfo.State` 枚举的 `name()` 字符串（`UPLOADED` / `ENABLED` / `DISABLED`）；`shared_class_dependencies`、`component_classes`、`route_mappings` 三列存储 JSON 数组字符串；`error_message` 通过 `.nullable()` 标记为可空；`created_at` 与 `updated_at` 由 `Blueprint.timestamps()` 创建，均为 nullable。

---

## 模型

`PluginMetadataModel` 继承 `BaseModel<PluginMetadataModel, Long>`，遵循 jaravel Eloquent 模式，一个类同时承担实体定义与查询职责。

### 类定义

```java
@Repository
@Table(name = "plugin_metadata")
public class PluginMetadataModel extends BaseModel<PluginMetadataModel, Long> {

    @Primary @Column private Long id;
    @Column private String pluginId;
    @Column private String version;
    @Column private String jarPath;
    @Column private String state;
    @Column private String sharedClassDependencies;
    @Column private String componentClasses;
    @Column private String routeMappings;
    @Column private String errorMessage;
    @Column private Boolean persisted;
    @Column private String createdAt;
    @Column private String updatedAt;

    // 静态查询方法（委托给 BaseModel）
    public static PluginMetadataModel find(Long id) { ... }
    public static List<PluginMetadataModel> all() { ... }
    public static QueryBuilder<PluginMetadataModel, Long> query() { ... }

    // getter / setter ...
}
```

### 字段与数据库列映射

| 模型字段（Java） | 数据库列 | 类型 | 说明 |
|---|---|---|---|
| `id` | `id` | `Long` | 主键 |
| `pluginId` | `plugin_id` | `String` | 插件 ID |
| `version` | `version` | `String` | 插件版本 |
| `jarPath` | `jar_path` | `String` | JAR 文件路径 |
| `state` | `state` | `String` | 状态枚举名 |
| `sharedClassDependencies` | `shared_class_dependencies` | `String` | 共享类依赖 JSON |
| `componentClasses` | `component_classes` | `String` | 组件类列表 JSON |
| `routeMappings` | `route_mappings` | `String` | 路由映射 JSON |
| `errorMessage` | `error_message` | `String` | 错误信息 |
| `persisted` | `persisted` | `Boolean` | 是否磁盘持久化 |
| `createdAt` | `created_at` | `String` | 创建时间 |
| `updatedAt` | `updated_at` | `String` | 更新时间 |

### 查询示例

```java
// 查询所有插件元数据
List<PluginMetadataModel> all = PluginMetadataModel.all();

// 按插件 ID 查询
PluginMetadataModel model = PluginMetadataModel.query()
        .where("plugin_id", "my-plugin")
        .first()
        .toObject();

// 静态 find（按主键）
PluginMetadataModel model = PluginMetadataModel.find(1L);
```

> 与所有 jaravel BaseModel 子类一样，`new PluginMetadataModel()` 创建的是普通实例，调用 `save()` 等实例方法或静态查询时，统一通过 `SpringContext.bean(PluginMetadataModel.class)` 取回 Spring 单例来真正执行 gaarason 的查询/写入。

---

## 迁移

`Migration_2024_01_03_CreatePluginMetadataTable` 自动创建 `plugin_metadata` 表。

使用 jaravel 迁移系统，遵循 `Migration_YYYY_MM_DD_Description` 命名规范。由于类名自带 `2024_01_03` 日期前缀，`Migrator` 按类名字典序排序即可获得正确的执行顺序。

### 迁移类定义

```java
@Component
public class Migration_2024_01_03_CreatePluginMetadataTable implements Migration {

    @Override
    public void up(Schema schema) {
        schema.create("plugin_metadata", table -> {
            table.id();                                        // BIGINT 自增主键
            table.string("plugin_id", 100);                    // VARCHAR(100)
            table.string("version", 50);                       // VARCHAR(50)
            table.string("jar_path", 500);                     // VARCHAR(500)
            table.string("state", 20);                         // VARCHAR(20)
            table.text("shared_class_dependencies");           // TEXT
            table.text("component_classes");                   // TEXT
            table.text("route_mappings");                      // TEXT
            table.string("error_message", 500).nullable();     // VARCHAR(500) NULL
            table.booleanColumn("persisted");                  // TINYINT(1)
            table.timestamps();                                // created_at, updated_at
        });
    }

    @Override
    public void down(Schema schema) {
        schema.dropIfExists("plugin_metadata");
    }
}
```

### 执行方式

迁移由 jaravel `Migrator` 自动收集并执行，支持以下触发方式：

```bash
# 启动时自动执行（推荐，需配置 jaravel.migration.auto-run=true）
java -jar app.jar

# 手动触发迁移
java -jar app.jar --jaravel.migrate

# 回滚最近 1 批
java -jar app.jar --jaravel.rollback

# 查看迁移状态
java -jar app.jar --jaravel.migration-status
```

迁移执行记录写入 `migrations` 表，重复启动不会重复建表。

---

## 持久化逻辑

`ModelMetadataPersistence` 实现 `MetadataPersistence` 接口，提供四个核心方法。内部持有 `ObjectMapper` 实例，用于 `Set<String>` 与 `Set<RouteInfo>` 字段的 JSON 序列化/反序列化。

### 接口契约

```java
public interface MetadataPersistence {
    void save(PluginInfo pluginInfo);
    PluginInfo load(String pluginId);
    List<PluginInfo> loadAll();
    void delete(String pluginId);
}
```

### 方法详解

#### `save(PluginInfo pluginInfo)`

将 `PluginInfo` 映射为 `PluginMetadataModel` 并保存到数据库。

执行逻辑：

1. 若 `pluginInfo` 为 `null` 或 `pluginId` 为 `null`，直接返回。
2. 若 `pluginInfo.isPersisted()` 为 `false`，直接返回（仅持久化标记为已持久化的插件）。
3. 先按 `plugin_id` 物理删除已有记录（`query().where("plugin_id", id).forceDelete()`），实现「先删后插」的覆盖语义。
4. 创建新的 `PluginMetadataModel`，逐字段映射：
   - `pluginId`、`version`、`jarPath`、`errorMessage` 直接赋值；
   - `state` 取 `PluginInfo.State.name()`（枚举名转字符串，`null` 时存 `null`）；
   - `sharedClassDependencies`、`componentClasses` 通过 `toJson(Set<String>)` 序列化为 JSON 数组字符串；
   - `routeMappings` 通过 `toJsonRouteInfos(Set<RouteInfo>)` 序列化为 JSON 数组字符串；
   - `persisted` 转为 `Boolean`。
5. 调用 `model.save()` 持久化。
6. 异常被捕获并记录日志 `保存插件元数据失败: {}`，不向外抛出。

#### `load(String pluginId)`

按 `plugin_id` 查询并转换为 `PluginInfo`。

执行逻辑：

1. 若 `pluginId` 为 `null`，返回 `null`。
2. 通过 `findModel(pluginId)` 查询记录，未找到返回 `null`。
3. 调用 `toPluginInfo(model)` 转换为 `PluginInfo`。
4. 若 `pluginInfo.isPersisted()` 为 `true` 则返回，否则返回 `null`（仅恢复标记为已持久化的插件）。

#### `loadAll()`

查询所有记录，仅返回 `persisted=true` 的插件。

执行逻辑：

1. 调用 `PluginMetadataModel.all()` 查询全部记录。
2. 通过流式 `map(toPluginInfo)` 转换为 `PluginInfo` 列表。
3. 过滤保留 `persisted=true` 的插件。
4. 收集为 `List<PluginInfo>` 返回。

#### `delete(String pluginId)`

按 `plugin_id` 物理删除记录。

执行逻辑：

1. 若 `pluginId` 为 `null`，直接返回。
2. 执行 `query().where("plugin_id", pluginId).forceDelete()` 物理删除。
3. 异常被捕获并记录日志 `删除插件元数据失败: {}`，不向外抛出。

### JSON 序列化辅助方法

`Set<String>` 与 `Set<RouteInfo>` 字段通过 Jackson `ObjectMapper` 序列化为 JSON 字符串存储在 `TEXT` 列中：

| 方法 | 作用 | 空值处理 | 异常处理 |
|---|---|---|---|
| `toJson(Set<String>)` | 序列化字符串集合 | `null` 或空集返回 `"[]"` | 记录日志 `序列化 Set<String> 失败`，返回 `"[]"` |
| `fromJson(String)` | 反序列化字符串集合 | `null` 或空串返回空 `HashSet` | 记录日志 `反序列化 Set<String> 失败: {}`，返回空 `HashSet` |
| `toJsonRouteInfos(Set<RouteInfo>)` | 序列化路由集合 | `null` 或空集返回 `"[]"` | 记录日志 `序列化 Set<RouteInfo> 失败`，返回 `"[]"` |
| `fromJsonRouteInfos(String)` | 反序列化路由集合 | `null` 或空串返回空 `HashSet` | 记录日志 `反序列化 Set<RouteInfo> 失败: {}`，返回空 `HashSet` |

### 字段映射关系

`PluginInfo` 与 `PluginMetadataModel` 的字段映射：

| PluginInfo 字段 | 模型字段 | 转换方式 |
|---|---|---|
| `pluginId` | `pluginId` | 直接赋值 |
| `version` | `version` | 直接赋值 |
| `jarPath` | `jarPath` | 直接赋值 |
| `state`（`State` 枚举） | `state`（`String`） | `state.name()` / `State.valueOf(str)` |
| `sharedClassDependencies`（`Set<String>`） | `sharedClassDependencies`（`String`） | JSON 序列化/反序列化 |
| `componentClasses`（`Set<String>`） | `componentClasses`（`String`） | JSON 序列化/反序列化 |
| `routeMappings`（`Set<RouteInfo>`） | `routeMappings`（`String`） | JSON 序列化/反序列化 |
| `errorMessage` | `errorMessage` | 直接赋值 |
| `persisted`（`boolean`） | `persisted`（`Boolean`） | 装箱/拆箱 |

> 注意：`PluginInfo.registeredBeanNames` 为运行时字段，不参与持久化，因此 `plugin_metadata` 表中没有对应列。

---

## 配置

无需额外配置。引入依赖后自动生效。确保 jaravel 的迁移系统已启用：

```yaml
jaravel:
  migration:
    enabled: true       # 是否启用迁移模块（默认 true）
    auto-run: true      # 启动时是否自动执行 migrate（默认 false，建议设为 true）
```

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `jaravel.migration.enabled` | `boolean` | `true` | 是否启用迁移模块 |
| `jaravel.migration.auto-run` | `boolean` | `false` | 启动时是否自动执行 migrate |
| `jaravel.migration.table` | `String` | `migrations` | 迁移记录表名 |

> 此外，容器中需存在 jaravel 数据源（`GaarasonDataSource` Bean）与 `PluginMetadataModel` 所需的 `@Repository` 扫描。通常引入 `database` 模块并配置数据源即可满足。

---

## 与 JSON 持久化的对比

| 特性 | `JsonMetadataPersistence` | `ModelMetadataPersistence` |
|------|---------------------------|----------------------------|
| 存储位置 | 文件系统（`plugins/{id}/metadata.json`） | 数据库（`plugin_metadata` 表） |
| 依赖 | 无额外依赖 | jaravel `database` + `migration` |
| 多实例共享 | 不支持（各实例独立文件） | 支持（多实例连接同一数据库） |
| 查询能力 | 弱（需遍历文件目录） | 强（SQL 查询，可按 `plugin_id` / `state` 等条件检索） |
| 自动加载 | 是 | 是 |
| 切换方式 | 默认启用 | 引入 `plugin-jar-database` 依赖自动替换 |
| 事务支持 | 无 | 随数据库事务 |
| 适用场景 | 单机、轻量插件管理 | 生产环境、多实例、需查询统计 |

两者实现同一个 `MetadataPersistence` 接口，对 `HotPluginManager` 透明，切换持久化方式不影响上层插件管理逻辑。

---

## 目录结构

```
com.weacsoft.jaravel.vendor.plugin.jar.database
├── autoconfigure
│   └── PluginJarDatabaseAutoConfiguration     # 自动装配类（@AutoConfigureBefore 优先于 JSON 持久化）
├── migration
│   └── Migration_2024_01_03_CreatePluginMetadataTable  # 创建 plugin_metadata 表的迁移
├── model
│   └── PluginMetadataModel                    # Eloquent 模型（继承 BaseModel），映射 plugin_metadata 表
└── persistence
    ├── ModelMetadataPersistence               # MetadataPersistence 实现，基于 Eloquent 模型持久化
    ├── ModelMetadataPersistence$1             # Set<String> 反序列化的 TypeReference（内部类）
    └── ModelMetadataPersistence$2             # Set<RouteInfo> 反序列化的 TypeReference（内部类）
```

### 关键类说明

| 类 | 所在包 | 职责 |
|---|---|---|
| `PluginJarDatabaseAutoConfiguration` | `autoconfigure` | Spring Boot 自动装配入口，注册 `ModelMetadataPersistence` Bean，通过 `@AutoConfigureBefore` 优先于 plugin-jar-core 的 JSON 持久化加载 |
| `Migration_2024_01_03_CreatePluginMetadataTable` | `migration` | jaravel 迁移类，`up()` 创建 `plugin_metadata` 表，`down()` 删除该表 |
| `PluginMetadataModel` | `model` | Eloquent 模型，继承 `BaseModel<PluginMetadataModel, Long>`，映射 `plugin_metadata` 表，提供 `find` / `all` / `query` 静态查询方法 |
| `ModelMetadataPersistence` | `persistence` | `MetadataPersistence` 接口实现，将 `PluginInfo` 与 `PluginMetadataModel` 互转，通过 `ObjectMapper` 序列化集合字段 |

### 自动装配注册

本模块通过 Spring Boot 自动装配机制注册，配置文件位于：

```
src/main/resources/META-INF/spring/
└── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

内容为：

```
com.weacsoft.jaravel.vendor.plugin.jar.database.autoconfigure.PluginJarDatabaseAutoConfiguration
```

引入依赖后，Spring Boot 会自动加载该自动装配类，无需手动 `@Import` 或 `@ComponentScan`。
