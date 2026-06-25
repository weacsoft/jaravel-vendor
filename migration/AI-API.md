# migration AI-API Reference

> Module: `migration` | Package: `com.weacsoft.jaravel.vendor.migration` | Version: 0.1.0

## Overview
migration 模块提供 Laravel 风格的数据库迁移系统，包含 Blueprint（流式建表蓝图）、Schema（DDL 执行器，支持 MySQL/SQLite/H2/SQL Server 多方言）、Migrator（迁移引擎，支持 migrate/rollback/reset/refresh/status）、MigrationScanner（三种迁移源加载：DIRECTORY 内存编译/JAR 加载/CLASSPATH 扫描）、MigrationRepository（迁移记录仓库）和 MigrationRunner（命令行运行器）。迁移文件通过 @MigrationAnnotation 标记，运行时编译/加载、反射实例化、执行后自动释放。

## Classes & Interfaces

### Blueprint
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.migration`
- **Description**: 表结构蓝图，对齐 Laravel `Illuminate\Database\Schema\Blueprint`。通过流式 API 声明表结构，支持 CREATE（建表）和 ALTER（改表）两种模式，自动适配 MySQL/SQLite/H2/SQL Server 方言。

#### Methods (主要)

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `Blueprint` | `String table` | 构造方法 | 创建蓝图 |
| `id` | 无 | `ColumnDefinition` | BIGINT 自增主键 |
| `bigIncrements` | `String column` | `ColumnDefinition` | BIGINT 自增主键 |
| `increments` | `String column` | `ColumnDefinition` | INT 自增主键 |
| `string` | `String column` | `ColumnDefinition` | VARCHAR(255) |
| `string` | `String column, int length` | `ColumnDefinition` | VARCHAR(length) |
| `charColumn` | `String column, int length` | `ColumnDefinition` | CHAR |
| `integer` | `String column` | `ColumnDefinition` | INT |
| `bigInteger` | `String column` | `ColumnDefinition` | BIGINT |
| `tinyInteger` | `String column` | `ColumnDefinition` | TINYINT |
| `smallInteger` | `String column` | `ColumnDefinition` | SMALLINT |
| `text` | `String column` | `ColumnDefinition` | TEXT |
| `mediumText` | `String column` | `ColumnDefinition` | MEDIUMTEXT |
| `longText` | `String column` | `ColumnDefinition` | LONGTEXT |
| `booleanColumn` | `String column` | `ColumnDefinition` | TINYINT(1) |
| `decimal` | `String column` | `ColumnDefinition` | DECIMAL(8,2) |
| `decimal` | `String column, int precision, int scale` | `ColumnDefinition` | DECIMAL(p,s) |
| `floatColumn` | `String column` | `ColumnDefinition` | FLOAT |
| `doubleColumn` | `String column` | `ColumnDefinition` | DOUBLE |
| `date` | `String column` | `ColumnDefinition` | DATE |
| `dateTime` | `String column` | `ColumnDefinition` | DATETIME |
| `timestamp` | `String column` | `ColumnDefinition` | TIMESTAMP |
| `time` | `String column` | `ColumnDefinition` | TIME |
| `year` | `String column` | `ColumnDefinition` | YEAR |
| `binary` | `String column` | `ColumnDefinition` | LONGBLOB |
| `json` | `String column` | `ColumnDefinition` | JSON |
| `enumColumn` | `String column, String... allowed` | `ColumnDefinition` | 通用枚举字段（VARCHAR 模拟） |
| `timestamps` | 无 | `void` | 添加 created_at、updated_at |
| `softDeletes` | 无 | `ColumnDefinition` | 添加 deleted_at 软删除字段 |
| `rememberToken` | 无 | `ColumnDefinition` | remember_token VARCHAR(100) NULL |
| `index` | `String column` | `void` | 单列普通索引 |
| `index` | `String... columns` | `void` | 复合索引 |
| `unique` | `String column` | `void` | 单列唯一索引 |
| `unique` | `String... columns` | `void` | 复合唯一索引 |
| `foreign` | `String column` | `ForeignKeyDefinition` | 外键 |
| `dropColumn` | `String column` | `void` | 删除列 |
| `dropIndex` | `String indexName` | `void` | 删除索引 |
| `renameColumn` | `String from, String to` | `void` | 重命名列 |
| `toCreateSql` | 无 | `String` | 生成 CREATE TABLE 语句 |
| `toIndexSql` | 无 | `List<String>` | 生成索引语句列表 |
| `toForeignKeySql` | 无 | `List<String>` | 生成外键语句列表 |
| `toDropSql` | 无 | `String` | 生成 DROP TABLE 语句 |
| `toAlterSql` | 无 | `List<String>` | 生成 ALTER 语句列表 |
| `quote` | `String identifier` | `String` | 按方言对标识符加引号 |
| `isMysql` / `isSqlite` / `isH2` / `isSqlServer` | 无 | `boolean` | 方言判断 |

#### Usage Example
```java
schema.create("users", table -> {
    table.id();
    table.string("name");
    table.string("email").unique();
    table.string("password");
    table.booleanColumn("is_active").defaultValue(true);
    table.timestamps();
    table.softDeletes();
});

schema.table("users", table -> {
    table.string("phone").nullable();
    table.dropColumn("is_active");
});
```

---

### ColumnDefinition
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.migration`
- **Description**: 列定义，对齐 Laravel Blueprint 列修饰链。由 Blueprint 字段方法创建，通过链式调用追加修饰。支持多数据库方言的类型映射与自增语法。

#### Methods (链式修饰)

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `nullable` | 无 | `ColumnDefinition` | 标记可空 |
| `notNull` | 无 | `ColumnDefinition` | 标记非空 |
| `defaultValue` | `Object value` | `ColumnDefinition` | 设置默认值 |
| `comment` | `String comment` | `ColumnDefinition` | 设置注释 |
| `autoIncrement` | 无 | `ColumnDefinition` | 标记自增 |
| `primary` | 无 | `ColumnDefinition` | 标记主键 |
| `unique` | 无 | `ColumnDefinition` | 标记唯一索引 |
| `index` | 无 | `ColumnDefinition` | 标记普通索引 |
| `unsigned` | 无 | `ColumnDefinition` | 标记无符号（MySQL） |
| `after` | `String column` | `ColumnDefinition` | ALTER 时置于指定列后（MySQL） |
| `change` | 无 | `ColumnDefinition` | 标记为修改已有字段 |
| `toSql` | 无 | `String` | 生成列 SQL 片段 |
| `toModifyColumnFragment` | 无 | `String` | 生成 ALTER COLUMN 修改片段 |

#### Usage Example
```java
schema.create("products", table -> {
    table.id();
    table.string("name").notNull();
    table.string("sku").unique().comment("商品编码");
    table.decimal("price", 10, 2).unsigned().defaultValue(0);
    table.booleanColumn("is_published").defaultValue(false);
});
```

---

### ForeignKeyDefinition
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.migration`
- **Description**: 外键定义，对齐 Laravel Blueprint 的 `foreign()` 链。

#### Methods (链式)

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `references` | `String column` | `ForeignKeyDefinition` | 引用列 |
| `on` | `String table` | `ForeignKeyDefinition` | 引用表 |
| `onDelete` | `String action` | `ForeignKeyDefinition` | 删除时动作（cascade/set null/restrict） |
| `onUpdate` | `String action` | `ForeignKeyDefinition` | 更新时动作 |
| `name` | `String name` | `ForeignKeyDefinition` | 约束名 |
| `toSql` | `String table` | `String` | 生成 ADD CONSTRAINT 子句 |

#### Usage Example
```java
schema.create("posts", table -> {
    table.id();
    table.bigInteger("user_id").unsigned();
    table.foreign("user_id")
        .references("id")
        .on("users")
        .onDelete("cascade");
});
```

---

### Schema
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.migration`
- **Description**: Schema 构建器，对齐 Laravel `Illuminate\Support\Facades\Schema`。通过 Blueprint 声明表结构，执行生成的 SQL DDL。自动适配 MySQL/SQLite/H2/SQL Server 方言。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `Schema` | `DataSource dataSource` | 构造方法 | 创建 Schema（自动检测数据库方言） |
| `create` | `String table, Consumer<Blueprint> definition` | `void` | 创建表 |
| `table` | `String table, Consumer<Blueprint> definition` | `void` | 修改表（加/改/删字段、索引等） |
| `dropIfExists` | `String table` | `void` | 删除表（如存在） |
| `drop` | `String table` | `void` | 删除表 |
| `rename` | `String from, String to` | `void` | 重命名表 |
| `hasTable` | `String table` | `boolean` | 判断表是否存在 |
| `hasColumn` | `String table, String column` | `boolean` | 判断列是否存在 |

#### Usage Example
```java
Schema schema = new Schema(dataSource);

// 建表
schema.create("users", table -> {
    table.id();
    table.string("name");
    table.timestamps();
});

// 改表
schema.table("users", table -> {
    table.string("phone").nullable().after("name");
});

// 检查
if (!schema.hasTable("users")) {
    schema.create("users", table -> table.id());
}
schema.dropIfExists("old_table");
schema.rename("old_table", "new_table");
```

---

### Migration
- **Type**: interface
- **Package**: `com.weacsoft.jaravel.vendor.migration`
- **Description**: 迁移接口，对齐 Laravel `Illuminate\Database\Migrations\Migration`。每个迁移类实现 up/down 两个方法。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `up` | `Schema schema` | `void` | 正向迁移：建表、加字段等 |
| `down` | `Schema schema` | `void` | 回滚迁移：删表、删字段等 |
| `getName` | 无 | `String` | 迁移名称（default 返回类名） |

#### Usage Example
```java
@MigrationAnnotation
public class Migration_2024_01_01_CreateUsersTable implements Migration {
    @Override
    public void up(Schema schema) {
        schema.create("users", table -> {
            table.id();
            table.string("name");
            table.string("email").unique();
            table.timestamps();
        });
    }
    @Override
    public void down(Schema schema) {
        schema.dropIfExists("users");
    }
}
```

---

### @MigrationAnnotation
- **Type**: annotation
- **Package**: `com.weacsoft.jaravel.vendor.migration`
- **Description**: 迁移类标记注解，替代 @Component。用于在运行时内存编译后识别哪些类是迁移类。
- **Target**: `ElementType.TYPE`
- **Retention**: `RetentionPolicy.RUNTIME`

#### Elements

| Element | Type | Default | Description |
|---------|------|---------|-------------|
| `name` | `String` | `""` | 迁移名称，空字符串表示使用类名 |

---

### MigrationSource
- **Type**: enum
- **Package**: `com.weacsoft.jaravel.vendor.migration`
- **Description**: 迁移源类型，支持三种迁移来源。

#### Constants

| Constant | Description |
|----------|-------------|
| `DIRECTORY` | 目录模式：从目录读取 .java 文件，运行时内存编译（需要 JDK） |
| `JAR` | JAR 模式：从 .jar 文件加载预编译的迁移类（只需要 JRE） |
| `CLASSPATH` | Classpath 模式：从当前 classpath 扫描迁移类（内置迁移） |

---

### Migrator
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.migration`
- **Description**: 迁移引擎，对齐 Laravel `Illuminate\Database\Migrations\Migrator`。通过 MigrationScanner 获取已编译的迁移类，反射实例化并执行 up/down，维护 migrations 记录表。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `Migrator` | `MigrationRepository repository, Schema schema, MigrationScanner scanner` | 构造方法 | 创建迁移引擎 |
| `run` | 无 | `List<String>` | 执行所有待运行迁移，返回已执行的迁移名称列表 |
| `rollback` | `int steps` | `List<String>` | 回滚指定步数，返回已回滚的迁移名称列表 |
| `reset` | 无 | `List<String>` | 回滚所有迁移 |
| `refresh` | 无 | `List<String>` | 回滚所有并重新迁移 |
| `status` | 无 | `void` | 输出迁移状态 |
| `pending` | 无 | `List<String>` | 获取待运行迁移名称列表 |
| `finish` | 无 | `void` | 释放资源（清除编译产物与类加载器） |

#### Usage Example
```java
Migrator migrator = new Migrator(repository, schema, scanner);
migrator.run();                    // 执行迁移
migrator.rollback(1);              // 回滚 1 批
migrator.reset();                  // 回滚全部
migrator.refresh();                // 回滚并重新迁移
migrator.status();                 // 查看状态
List<String> pending = migrator.pending(); // 待运行迁移
migrator.finish();                 // 释放资源
```

---

### MigrationScanner
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.migration`
- **Description**: 迁移源扫描器与加载器，支持三种迁移来源模式（DIRECTORY/JAR/CLASSPATH）。通过 @MigrationAnnotation 注解自动识别迁移类，无需手动指定包名。

#### Methods (主要)

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `compileFromDirectory` | `String directory` | `void` | 编译目录下所有 .java 文件（DIRECTORY 模式） |
| `compileFromDirectory` | `File dir` | `void` | 编译目录下所有 .java 文件 |
| `compileFromFile` | `File file` | `void` | 编译单个 .java 文件 |
| `compileFromFile` | `String filePath` | `void` | 编译单个 .java 文件 |
| `loadFromJar` | `File jarFile` | `void` | 从 JAR 加载迁移类（JAR 模式） |
| `loadFromClasspath` | 无 | `void` | 从 classpath 扫描迁移类（CLASSPATH 模式） |
| `getAllMigrationClassNames` | 无 | `List<String>` | 获取所有迁移类全限定名 |
| `getCompiledClass` | `String className` | `Class<?>` | 获取已编译/已加载的类 |
| `getMemoryClassLoader` | 无 | `MemoryClassLoader` | 获取内存类加载器 |
| `removeMemoryClassLoader` | 无 | `void` | 释放内存类加载器 |
| `removeAll` | 无 | `void` | 清除所有已编译/已加载的类 |
| `finish` | 无 | `void` | 释放所有资源 |

---

### MigrationRepository
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.migration`
- **Description**: 迁移记录仓库，对齐 Laravel `DatabaseMigrationRepository`。维护 migrations 表，记录已执行的迁移。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `MigrationRepository` | `DataSource dataSource, String table` | 构造方法 | 创建迁移记录仓库 |
| `createRepository` | 无 | `void` | 创建 migrations 记录表（如不存在） |
| `getRan` | 无 | `List<String>` | 获取已执行的迁移名称列表（按时间正序） |
| `getLast` | 无 | `List<String>` | 获取最后一批执行的迁移（按 id 倒序） |
| `log` | `String migration, int batch` | `void` | 记录一条已执行迁移 |
| `delete` | `String migration` | `void` | 删除一条迁移记录（回滚时） |
| `getNextBatchNumber` | 无 | `int` | 获取下一批次号 |
| `getTable` | 无 | `String` | 获取迁移记录表名 |

---

### MigrationRunner
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.migration`
- **Description**: 迁移命令运行器，对齐 Laravel artisan migrate 系列命令。实现 CommandLineRunner，通过启动参数触发迁移操作。
- **Implements**: `org.springframework.boot.CommandLineRunner`

#### Supported Commands

| Command | Description |
|---------|-------------|
| `--jaravel.migrate` | 执行迁移 |
| `--jaravel.rollback[=N]` | 回滚最近 N 批（默认 1） |
| `--jaravel.reset` | 回滚全部 |
| `--jaravel.refresh` | 回滚全部并重新迁移 |
| `--jaravel.migration-status` | 查看状态 |

#### Usage Example
```bash
# 执行迁移
java -jar app.jar --jaravel.migrate

# 回滚 3 批
java -jar app.jar --jaravel.rollback=3

# 回滚全部
java -jar app.jar --jaravel.reset

# 回滚并重新迁移
java -jar app.jar --jaravel.refresh

# 查看状态
java -jar app.jar --jaravel.migration-status
```

---

### MigrationAutoConfiguration
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.migration`
- **Description**: 迁移模块自动装配，注册 MigrationRunner Bean。
- **Annotations**: `@AutoConfiguration`, `@AutoConfigureAfter(DataSourceAutoConfiguration.class)`, `@ConditionalOnClass(Migrator.class)`, `@ConditionalOnBean(DataSource.class)`, `@ConditionalOnProperty(prefix = "jaravel.migration", name = "enabled", havingValue = "true", matchIfMissing = true)`, `@EnableConfigurationProperties(MigrationProperties.class)`

#### Bean Methods

| Bean | Parameters | Return | Description |
|------|-----------|--------|-------------|
| `jaravelMigrationRunner` | `DataSource dataSource, MigrationProperties properties` | `MigrationRunner` | 创建迁移运行器（@Bean, @ConditionalOnMissingBean, @Order(HIGHEST_PRECEDENCE)） |

---

### MigrationProperties
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.migration`
- **Description**: 迁移配置属性，前缀 `jaravel.migration`。
- **Annotations**: `@ConfigurationProperties(prefix = "jaravel.migration")`

#### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `boolean` | `true` | 是否启用迁移模块 |
| `table` | `String` | `"migrations"` | 迁移记录表名 |
| `source` | `MigrationSource` | `DIRECTORY` | 迁移源类型 |
| `directory` | `String` | `"migrations"` | 迁移 .java 文件目录（DIRECTORY 模式） |
| `jarPath` | `String` | `""` | JAR 文件路径（JAR 模式） |
| `packageInJar` | `boolean` | `false` | 构建时是否将迁移目录打包进 jar |
| `autoRun` | `boolean` | `false` | 启动时是否自动执行 migrate |

#### Usage Example
```yaml
# application.yml - 目录模式（开发）
jaravel:
  migration:
    source: DIRECTORY
    directory: migrations
    auto-run: false

# JAR 模式（生产）
jaravel:
  migration:
    source: JAR
    jar-path: /opt/app/migrations.jar

# Classpath 模式（内置迁移）
jaravel:
  migration:
    source: CLASSPATH
    auto-run: true
```
