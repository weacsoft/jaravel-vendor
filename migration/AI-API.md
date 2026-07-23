# migration AI-API Reference

> Module: `migration` | Package: `com.weacsoft.jaravel.vendor.migration` | Version: 0.1.2

## Overview
migration 模块提供 Laravel 风格的数据库迁移系统，包含 Blueprint（流式建表蓝图）、Schema（DDL 执行器，支持 MySQL/SQLite/H2/SQL Server 多方言）、Migrator（迁移引擎，支持 migrate/rollback/reset/refresh/status）、MigrationScanner（五种迁移源加载：DIRECTORY 内存编译/DIRECTORY_CLASSES 预编译 class 加载/PACKAGED 预编译 zip 包加载/JAR 加载/CLASSPATH 扫描）、MigrationPrecompiler（预编译工具，开发阶段将 .java 迁移文件预编译为字节码，支持打包为 zip 或散乱 class 输出）、MigrationRepository（迁移记录仓库）、MigrationExecutor（核心执行器，无 SpringBoot 依赖，SpringBoot 环境下注册为 Bean 供 Artisan 命令共享）、MigrationCLI（独立命令行入口）、MigrationPrecompilerMain（预编译命令行工具）、JdbcExecutor（轻量 JDBC 执行器，替代 JdbcTemplate）和 MigrationRunner（SpringBoot 适配器）。当 classpath 中同时存在 artisan 模块时，MigrationArtisanAutoConfiguration 自动注册 5 个迁移命令（migrate、migrate:rollback、migrate:reset、migrate:refresh、migrate:status）为 ArtisanCommand Bean，使开发者可通过 `artisan.call("migrate")` 在代码中调用迁移命令，或通过 `java -jar app.jar artisan migrate` 在命令行执行。迁移文件通过 @MigrationAnnotation 标记，运行时编译/加载、反射实例化、执行后自动释放。核心逻辑完全独立于 SpringBoot，可通过 MigrationCLI 在纯 Java 环境中运行。JDK 不可用时提供 4 种解决方案的错误提示。

## Package Structure

模块按职责拆分为以下子包：

| 子包 | 类 |
|------|-----|
| `com.weacsoft.jaravel.vendor.migration`（根包） | `Migration`, `Schema`, `MigrationAnnotation`, `Blueprint`, `ColumnDefinition`, `ForeignKeyDefinition`, `JdbcExecutor`, `MigrationGenerator`, `MigrationCLI`, `TableMigrator` |
| `com.weacsoft.jaravel.vendor.migration.dialect` | `Dialect`, `AbstractDialect`, `DialectFactory`, `MysqlDialect`, `H2Dialect`, `SqliteDialect`, `SqlServerDialect`, `PostgresqlDialect`, `OracleDialect` |
| `com.weacsoft.jaravel.vendor.migration.engine` | `Migrator`, `MigrationExecutor`, `MigrationRunner`, `MigrationScanner`, `MigrationPrecompiler`, `MigrationPrecompilerMain`, `MigrationRepository`, `MigrationSource` |
| `com.weacsoft.jaravel.vendor.migration.autoconfigure` | `MigrationAutoConfiguration`, `MigrationArtisanAutoConfiguration`, `MigrationProperties` |
| `com.weacsoft.jaravel.vendor.migration.artisan` | `MigrateCommand`, `MigrateRollbackCommand`, `MigrateResetCommand`, `MigrateRefreshCommand`, `MigrateStatusCommand` |

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
- **Package**: `com.weacsoft.jaravel.vendor.migration.engine`
- **Description**: 迁移源类型，支持五种迁移来源。

#### Constants

| Constant | Description |
|----------|-------------|
| `DIRECTORY` | 目录模式：从目录读取 .java 文件，运行时内存编译（需要 JDK） |
| `DIRECTORY_CLASSES` | 预编译 class 目录模式：从目录加载预编译 .class 文件（只需要 JRE） |
| `PACKAGED` | 预编译打包模式：从预编译 zip 包加载迁移类（只需要 JRE） |
| `JAR` | JAR 模式：从 .jar 文件加载预编译的迁移类（只需要 JRE） |
| `CLASSPATH` | Classpath 模式：从当前 classpath 扫描迁移类（内置迁移） |

---

### Migrator
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.migration.engine`
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
- **Package**: `com.weacsoft.jaravel.vendor.migration.engine`
- **Description**: 迁移源扫描器与加载器，支持五种迁移来源模式（DIRECTORY/DIRECTORY_CLASSES/PACKAGED/JAR/CLASSPATH）。通过 @MigrationAnnotation 注解自动识别迁移类，无需手动指定包名。JDK 不可用时提供 4 种解决方案的错误提示。

#### Methods (主要)

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `compileFromDirectory` | `String directory` | `void` | 编译目录下所有 .java 文件（DIRECTORY 模式） |
| `compileFromDirectory` | `File dir` | `void` | 编译目录下所有 .java 文件 |
| `compileFromFile` | `File file` | `void` | 编译单个 .java 文件 |
| `compileFromFile` | `String filePath` | `void` | 编译单个 .java 文件 |
| `loadFromDirectoryClasses` | `String dirPath` | `void` | 从目录加载预编译 .class 文件（DIRECTORY_CLASSES 模式，仅需 JRE） |
| `loadFromDirectoryClasses` | `File dir` | `void` | 从目录加载预编译 .class 文件 |
| `loadFromZip` | `String zipPath` | `void` | **新增**：从预编译 zip 包加载迁移类（PACKAGED 模式，仅需 JRE），使用 MemoryClassLoader 从 zip 读取字节码后直接定义类 |
| `loadFromJar` | `File jarFile` | `void` | 从 JAR 加载迁移类（JAR 模式） |
| `loadFromClasspath` | 无 | `void` | 从 classpath 扫描迁移类（CLASSPATH 模式） |
| `getAllMigrationClassNames` | 无 | `List<String>` | 获取所有迁移类全限定名 |
| `getCompiledClass` | `String className` | `Class<?>` | 获取已编译/已加载的类 |
| `getCompiledClasses` | 无 | `Map<String, byte[]>` | **新增**：获取编译后的字节码（类名 -> 字节码），供 MigrationPrecompiler 预编译器使用 |
| `getMemoryClassLoader` | 无 | `MemoryClassLoader` | 获取内存类加载器 |
| `removeMemoryClassLoader` | 无 | `void` | 释放内存类加载器 |
| `removeAll` | 无 | `void` | 清除所有已编译/已加载的类 |
| `finish` | 无 | `void` | 释放所有资源 |

---

### MigrationPrecompiler
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.migration.engine`
- **Description**: 预编译工具类，在开发阶段（有 JDK）将迁移 .java 文件预编译为字节码。支持两种输出模式：打包为单个 zip 文件（PACKAGED）或散乱 class 文件到目录（CLASSES）。预编译产物可在生产环境（仅需 JRE）通过 DIRECTORY_CLASSES 或 PACKAGED 模式加载，避免生产环境依赖 JDK。

#### Nested Enum

```java
public enum CompileMode {
    PACKAGED,  // 打包为单个 zip 文件
    CLASSES    // 散乱 class 文件到目录
}
```

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `MigrationPrecompiler` | `String sourceDir` | 构造方法 | 创建预编译器，指定迁移 .java 文件目录 |
| `compileAll` | `String outputDir, CompileMode mode, String fileName` | `int` | 预编译所有迁移文件，返回编译成功的文件数。outputDir 为输出目录，mode 为 PACKAGED 或 CLASSES，fileName 为打包文件名（仅 PACKAGED 模式，默认 migrations.jmigration.zip） |
| `compileAllToZip` | `String outputDir, String fileName` | `int` | 便利方法：打包为 zip（等同 `compileAll(outputDir, PACKAGED, fileName)`） |
| `compileAllToClasses` | `String outputDir` | `int` | 便利方法：输出散乱 class（等同 `compileAll(outputDir, CLASSES, null)`） |

#### zip 包格式

PACKAGED 模式生成的 zip 包结构：

```
migrations.jmigration.zip
├── manifest.txt                                         # 迁移类名列表（每行一个类全限定名）
├── com/
│   └── example/
│       ├── Migration_2024_01_01_CreateUsersTable.class
│       └── Migration_2024_01_02_AddEmailToUsersTable.class
└── ...
```

- `manifest.txt`：迁移类名列表，每行一个类全限定名
- `.class` 文件按包结构存储（如 `com/example/Migration_xxx.class`）

#### Usage Example
```java
// 开发阶段预编译（需 JDK）
MigrationPrecompiler precompiler = new MigrationPrecompiler("migrations");

// 方式一：打包为 zip（PACKAGED 模式使用）
int count = precompiler.compileAllToZip("precompiled", "migrations.jmigration.zip");
System.out.println("编译了 " + count + " 个迁移文件");

// 方式二：输出散乱 class（DIRECTORY_CLASSES 模式使用）
count = precompiler.compileAllToClasses("precompiled/migrations");

// 或使用 compileAll 统一方法
precompiler.compileAll("precompiled", CompileMode.PACKAGED, "migrations.jmigration.zip");
precompiler.compileAll("precompiled/migrations", CompileMode.CLASSES, null);
```

---

### MigrationRepository
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.migration.engine`
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
- **Package**: `com.weacsoft.jaravel.vendor.migration.engine`
- **Description**: SpringBoot 命令行运行器适配层。实现 CommandLineRunner，内部委托给 MigrationExecutor。仅此类需要 SpringBoot 依赖。支持通过构造器注入已创建的 MigrationExecutor（与 Artisan 命令共享同一实例）。
- **Implements**: `org.springframework.boot.CommandLineRunner`

#### Constructors

| Constructor | Parameters | Description |
|-------------|-----------|-------------|
| `MigrationRunner` | `DataSource dataSource, MigrationProperties properties` | 内部创建 MigrationExecutor |
| `MigrationRunner` | `MigrationExecutor executor` | 注入已创建的 MigrationExecutor（SpringBoot 自动装配使用） |

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
- **Package**: `com.weacsoft.jaravel.vendor.migration.autoconfigure`
- **Description**: 迁移模块自动装配（SpringBoot 适配层）。通过 @Bean @ConfigurationProperties 绑定配置，注册 MigrationExecutor 和 MigrationRunner Bean。核心逻辑（MigrationExecutor）已独立于 SpringBoot，注册为 Bean 后可供 MigrationRunner 和 Artisan 迁移命令共享。
- **Annotations**: `@AutoConfiguration`, `@AutoConfigureAfter(DataSourceAutoConfiguration.class)`, `@ConditionalOnClass(MigrationExecutor.class)`, `@ConditionalOnBean(DataSource.class)`, `@ConditionalOnProperty(prefix = "jaravel.migration", name = "enabled", havingValue = "true", matchIfMissing = true)`

#### Bean Methods

| Bean | Parameters | Return | Description |
|------|-----------|--------|-------------|
| `jaravelMigrationProperties` | 无 | `MigrationProperties` | 创建迁移配置（@Bean, @ConfigurationProperties, @ConditionalOnMissingBean） |
| `migrationExecutor` | `DataSource dataSource, MigrationProperties properties` | `MigrationExecutor` | 创建迁移执行器（@Bean, @ConditionalOnMissingBean），供 MigrationRunner 和 Artisan 命令共享 |
| `jaravelMigrationRunner` | `MigrationExecutor executor` | `MigrationRunner` | 创建迁移运行器（@Bean, @ConditionalOnMissingBean, @Order(HIGHEST_PRECEDENCE)），注入共享的 MigrationExecutor |

---

### MigrationProperties
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.migration.autoconfigure`
- **Description**: 迁移配置属性，前缀 `jaravel.migration`。纯 POJO，无 Spring 注解。在 SpringBoot 中通过 MigrationAutoConfiguration 的 @Bean @ConfigurationProperties 绑定；独立运行时手动设置。

#### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `boolean` | `true` | 是否启用迁移模块 |
| `table` | `String` | `"migrations"` | 迁移记录表名 |
| `source` | `MigrationSource` | `DIRECTORY` | 迁移源类型（DIRECTORY/DIRECTORY_CLASSES/PACKAGED/JAR/CLASSPATH） |
| `directory` | `String` | `"migrations"` | 迁移 .java 文件目录（DIRECTORY 模式） |
| `classesDir` | `String` | `""` | 预编译 .class 文件目录（DIRECTORY_CLASSES 模式） |
| `packagePath` | `String` | `""` | 预编译打包文件路径（PACKAGED 模式，如 migrations.jmigration.zip） |
| `jarPath` | `String` | `""` | JAR 文件路径（JAR 模式） |
| `packageInJar` | `boolean` | `false` | 构建时是否将迁移目录打包进 jar |
| `autoRun` | `boolean` | `false` | 启动时是否自动执行 migrate |

#### Usage Example
```yaml
# application.yml - 目录模式（开发，需 JDK）
jaravel:
  migration:
    source: DIRECTORY
    directory: migrations
    auto-run: false

# 预编译 class 目录模式（生产，仅需 JRE）
jaravel:
  migration:
    source: DIRECTORY_CLASSES
    classes-dir: precompiled/migrations

# 预编译打包模式（生产，仅需 JRE）
jaravel:
  migration:
    source: PACKAGED
    package-path: precompiled/migrations.jmigration.zip

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

---

### MigrationExecutor
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.migration.engine`
- **Description**: 迁移执行器，核心逻辑无 SpringBoot 依赖。根据 MigrationProperties 配置的源模式加载迁移，执行 migrate/rollback/reset/refresh/status 命令。可被 MigrationRunner（SpringBoot）、MigrationCLI（独立）或 Artisan 迁移命令调用。SpringBoot 环境下注册为 Bean，供 MigrationRunner 和 Artisan 命令共享同一实例。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `MigrationExecutor` | `DataSource dataSource, MigrationProperties properties` | 构造方法 | 创建执行器 |
| `execute` | `String... args` | `void` | 执行迁移命令（migrate/rollback/reset/refresh/status） |
| `getProperties` | 无 | `MigrationProperties` | 获取迁移配置 |

#### Usage Example
```java
MigrationProperties properties = new MigrationProperties();
properties.setEnabled(true);
properties.setSource(MigrationSource.DIRECTORY);
properties.setDirectory("/path/to/migrations");

DataSource dataSource = ...;
MigrationExecutor executor = new MigrationExecutor(dataSource, properties);
executor.execute("migrate");
```

---

### MigrationArtisanAutoConfiguration
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.migration.autoconfigure`
- **Description**: 迁移模块与 Artisan CLI 的集成自动装配。当 classpath 中同时存在 `ArtisanCommand`（artisan 模块）和 `MigrationExecutor`（migration 模块）时，自动注册 5 个迁移命令为 Artisan 命令 Bean，使开发者可通过 `artisan.call("migrate")` 在代码中调用迁移命令，或通过 `java -jar app.jar artisan migrate` 在命令行执行。这些命令委托给 `MigrationExecutor` 执行，与 `MigrationRunner` 共享同一个 `MigrationExecutor` Bean 实例。
- **Annotations**: `@AutoConfiguration`, `@AutoConfigureAfter(MigrationAutoConfiguration.class)`, `@ConditionalOnClass(ArtisanCommand.class)`, `@ConditionalOnBean(MigrationExecutor.class)`

#### Registered Commands

| Command | Signature | Description |
|---------|-----------|-------------|
| `migrate` | `migrate {--force}` | 执行数据库迁移 |
| `migrate:rollback` | `migrate:rollback {--step=1}` | 回滚最近一批（或指定批数）迁移 |
| `migrate:reset` | `migrate:reset` | 回滚所有迁移 |
| `migrate:refresh` | `migrate:refresh` | 回滚所有迁移并重新执行 |
| `migrate:status` | `migrate:status` | 查看迁移状态 |

#### Usage Example
```java
// 方式一：在代码中通过 ArtisanApplication 调用
@Autowired
ArtisanApplication artisan;

public void runMigrations() {
    artisan.call("migrate");
    // 或带参数
    artisan.call("migrate:rollback", new String[]{"--step=3"});
}

// 方式二：命令行调用
// java -jar app.jar artisan migrate
// java -jar app.jar artisan migrate:rollback --step=5
// java -jar app.jar artisan migrate:status
```

---

### MigrateCommand / MigrateRollbackCommand / MigrateResetCommand / MigrateRefreshCommand / MigrateStatusCommand
- **Type**: class (extends `ArtisanCommand`)
- **Package**: `com.weacsoft.jaravel.vendor.migration.artisan`
- **Description**: 5 个迁移 Artisan 命令类，分别对齐 Laravel `php artisan migrate` 系列命令。每个命令委托给 `MigrationExecutor` 执行对应的迁移操作。由 `MigrationArtisanAutoConfiguration` 在 artisan 和 migration 模块同时存在时自动注册为 Spring Bean。

| Class | Signature | Description | Delegate Args |
|-------|-----------|-------------|---------------|
| `MigrateCommand` | `migrate {--force}` | 执行迁移 | `execute("migrate")` |
| `MigrateRollbackCommand` | `migrate:rollback {--step=1}` | 回滚迁移 | `execute("rollback=" + step)` |
| `MigrateResetCommand` | `migrate:reset` | 回滚所有迁移 | `execute("reset")` |
| `MigrateRefreshCommand` | `migrate:refresh` | 回滚并重新迁移 | `execute("refresh")` |
| `MigrateStatusCommand` | `migrate:status` | 查看迁移状态 | `execute("status")` |

---

### MigrationCLI
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.migration`
- **Description**: 独立命令行入口，使 migration 可在无 SpringBoot 环境下通过 main() 直接运行。内置 SimpleDataSource（基于 DriverManager），支持 --db-url/--db-user/--db-password 参数。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `main` | `String[] args` | `void` | 主入口，解析参数并执行迁移 |

#### CLI Parameters

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `--db-url=<url>` | 是 | - | 数据库 JDBC URL |
| `--db-user=<user>` | 是 | - | 数据库用户名 |
| `--db-password=<pwd>` | 否 | 空 | 数据库密码 |
| `--source=<type>` | 否 | DIRECTORY | 迁移源：DIRECTORY/DIRECTORY_CLASSES/PACKAGED/JAR/CLASSPATH |
| `--directory=<path>` | 否 | migrations | 迁移 .java 文件目录（DIRECTORY 模式） |
| `--classes-dir=<path>` | 否 | 空 | 预编译 .class 文件目录（DIRECTORY_CLASSES 模式） |
| `--package-path=<path>` | 否 | 空 | 预编译打包文件路径（PACKAGED 模式） |
| `--jar-path=<path>` | 否 | 空 | JAR 路径（JAR 模式） |
| `--table=<name>` | 否 | migrations | 迁移记录表名 |

#### Usage Example
```bash
java -cp migration.jar:utils.jar:mysql-connector.jar \
  com.weacsoft.jaravel.vendor.migration.MigrationCLI \
  --db-url=jdbc:mysql://localhost:3306/mydb \
  --db-user=root \
  --db-password=secret \
  --source=DIRECTORY \
  --directory=/path/to/migrations \
  migrate
```

---

### MigrationPrecompilerMain
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.migration.engine`
- **Description**: 预编译命令行工具，在开发阶段（有 JDK）通过命令行将迁移 .java 文件预编译为字节码。支持打包为 zip（PACKAGED）或散乱 class（CLASSES）两种输出模式。预编译产物部署到生产环境（仅需 JRE）后，配合 PACKAGED 或 DIRECTORY_CLASSES 模式加载。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `main` | `String[] args` | `void` | 主入口，解析参数并执行预编译 |

#### CLI Parameters

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `--source-dir=<path>` | 是 | - | 迁移 .java 文件源目录 |
| `--output-dir=<path>` | 是 | - | 输出目录 |
| `--mode=<mode>` | 否 | packaged | 预编译模式：packaged（打包为 zip）或 classes（散乱 class） |
| `--file-name=<name>` | 否 | migrations.jmigration.zip | 打包文件名（仅 packaged 模式） |

#### Usage Example
```bash
# 打包为 zip（PACKAGED 模式使用）
java -cp migration.jar:utils.jar \
  com.weacsoft.jaravel.vendor.migration.engine.MigrationPrecompilerMain \
  --source-dir=migrations \
  --output-dir=precompiled \
  --mode=packaged \
  --file-name=migrations.jmigration.zip

# 输出散乱 class（DIRECTORY_CLASSES 模式使用）
java -cp migration.jar:utils.jar \
  com.weacsoft.jaravel.vendor.migration.engine.MigrationPrecompilerMain \
  --source-dir=migrations \
  --output-dir=precompiled/migrations \
  --mode=classes
```

---

### JdbcExecutor
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.migration`
- **Description**: 轻量级 JDBC 执行器，替代 Spring JdbcTemplate，使 migration 模块可独立于 SpringBoot 运行。封装 execute（DDL）、update（DML）、queryForObject（单值）、queryForList（列表）、queryForMapList（多列结果集）操作。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `JdbcExecutor` | `DataSource dataSource` | 构造方法 | 创建执行器 |
| `execute` | `String sql` | `void` | 执行 DDL 语句 |
| `update` | `String sql, Object... args` | `int` | 执行参数化 UPDATE/INSERT/DELETE |
| `queryForObject` | `String sql, Class<T> requiredType, Object... args` | `<T> T` | 查询单个值 |
| `queryForList` | `String sql, Class<T> elementType, Object... args` | `<T> List<T>` | 查询单列值列表 |
| `queryForMapList` | `String sql, Object... args` | `List<Map<String, Object>>` | 查询多列结果集 |
