# migration 模块

> Jaravel-Vendor 的数据库迁移模块，提供 Laravel 风格的 Blueprint 流式建表、`up()` / `down()` 迁移引擎、`migrate` / `rollback` / `reset` / `refresh` / `status` 命令，以及 MySQL、SQLite、H2、SQL Server 多数据库方言自动适配。包名统一为 `com.weacsoft.jaravel.vendor.migration`。

---

## 目录

- [1. 模块概述](#1-模块概述)
- [2. 依赖信息](#2-依赖信息)
- [3. 类总览](#3-类总览)
- [4. Migration —— 迁移接口](#4-migration--迁移接口)
- [5. MigrationGenerator —— 迁移类源码生成器](#5-migrationgenerator--迁移类源码生成器)
- [6. Schema —— 表结构构建器](#6-schema--表结构构建器)
- [7. Blueprint —— 表结构蓝图](#7-blueprint--表结构蓝图)
- [8. ColumnDefinition —— 列定义](#8-columndefinition--列定义)
- [9. ForeignKeyDefinition —— 外键定义](#9-foreignkeydefinition--外键定义)
- [10. Migrator —— 迁移引擎](#10-migrator--迁移引擎)
- [11. MigrationRepository —— 迁移记录仓库](#11-migrationrepository--迁移记录仓库)
- [12. MigrationRunner —— 命令行运行器](#12-migrationrunner--命令行运行器)
- [13. MigrationAutoConfiguration —— 自动装配](#13-migrationautoconfiguration--自动装配)
- [14. 配置选项](#14-配置选项)
- [15. 线程安全说明](#15-线程安全说明)

---

## 1. 模块概述

`migration` 模块对齐 Laravel 的数据库迁移体系，核心特性如下：

| Laravel 特性 | migration 对应实现 | 说明 |
| --- | --- | --- |
| `Illuminate\Database\Migrations\Migration` | `Migration` 接口 | `up()` / `down()` 正向与回滚迁移 |
| `Illuminate\Database\Schema\Blueprint` | `Blueprint` | 流式 API 声明表结构，支持 CREATE 与 ALTER 两种模式 |
| `Illuminate\Support\Facades\Schema` | `Schema` | 建表 / 改表 / 删表 / 重命名 / 判断存在 |
| `php artisan make:migration` | `MigrationGenerator` | 按命名约定生成迁移 Java 源文件 |
| `Illuminate\Database\Migrations\Migrator` | `Migrator` | 收集迁移、排序、执行、回滚 |
| `DatabaseMigrationRepository` | `MigrationRepository` | 维护 `migrations` 记录表 |
| `php artisan migrate` 系列命令 | `MigrationRunner` | 启动参数触发迁移命令 |

### 命名约定

迁移类名采用 `Migration_YYYY_MM_DD_PascalCaseDescription` 形式，例如：

- `Migration_2024_01_01_CreateUsersTable`
- `Migration_2024_01_02_AddEmailToUsersTable`

由于类名自带 `YYYY_MM_DD` 日期前缀，`Migrator` 按类名（即 `Migration.getName()`）字典序排序即可获得正确的迁移执行顺序，无需额外维护时间戳字段。

### 多数据库支持

通过 `Connection.getMetaData().getDatabaseProductName()` 自动检测数据库方言，支持：

| 数据库 | 标识符引用 | 自增语法 | 建表选项 | 系统目录 |
| --- | --- | --- | --- | --- |
| MySQL | 反引号 `` ` `` | `AUTO_INCREMENT` | `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4` | `information_schema` |
| SQLite | 反引号 `` ` `` | `AUTOINCREMENT` | 无 | `sqlite_master` |
| H2 | 反引号 `` ` `` | `AUTO_INCREMENT` | 无 | `INFORMATION_SCHEMA` |
| SQL Server | 方括号 `[]` | `IDENTITY(1,1)` | 无 | `sys.tables` / `sys.columns` |

### 多表支持

单次 `up()` 可连续调用多次 `Schema.create()` 或 `Schema.table()` 处理多张表，`down()` 应对称地删除/回滚所有在 `up()` 中创建或修改的表。

---

## 2. 依赖信息

### Maven 坐标

```xml
<dependency>
    <groupId>com.weacsoft</groupId>
    <artifactId>migration</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 传递依赖

| 依赖 | 用途 |
| --- | --- |
| `com.weacsoft:core` | Facade 基础设施 |
| `org.springframework:spring-jdbc` | `JdbcTemplate` 执行 SQL DDL |
| `org.springframework.boot:spring-boot-autoconfigure` | 自动装配 |
| `org.slf4j:slf4j-api` | 日志门面 |

> 运行环境要求：JDK 17+，Spring Boot 3.2.5（Spring 6.x），容器中需存在 `javax.sql.DataSource` Bean。

---

## 3. 类总览

```
com.weacsoft.jaravel.vendor.migration
├── Migration                    // 迁移接口（up/down/getName/getDataSourceName）
├── MigrationGenerator           // 迁移类源码生成器（make:migration）
├── Schema                       // 表结构构建器（create/table/drop/rename/hasTable/hasColumn）
├── Blueprint                    // 表结构蓝图（流式 API，CREATE 与 ALTER 模式）
├── ColumnDefinition             // 列定义（类型映射 + 链式修饰）
├── ForeignKeyDefinition         // 外键定义（references/on/onDelete/onUpdate）
├── Migrator                     // 迁移引擎（run/rollback/reset/refresh/status/pending）
├── MigrationRepository          // 迁移记录仓库（migrations 表 CRUD）
├── MigrationRunner              // 命令行运行器（--jaravel.migrate 等参数）
├── MigrationProperties          // 配置属性（jaravel.migration.*）
└── MigrationAutoConfiguration   // 自动装配
```

---

## 4. Migration —— 迁移接口

`com.weacsoft.jaravel.vendor.migration.Migration`

每个迁移类实现此接口，提供 `up()` 与 `down()` 两个方法，分别表示正向执行与回滚。

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `void up(Schema schema)` | 正向迁移：建表、加字段、加索引等。一次可处理多张表 |
| `void down(Schema schema)` | 回滚迁移：删表、删字段等，应与 `up()` 对称 |
| `default String getName()` | 迁移名称，默认返回类名。用于排序与记录到 migrations 表 |
| `default String getDataSourceName()` | 指定此迁移使用的数据源 Bean 名称，返回 null 时使用默认（Primary）数据源 |

### 使用示例

```java
@Component
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

多表迁移示例：

```java
@Component
public class Migration_2024_02_01_CreateBlogTables implements Migration {

    @Override
    public void up(Schema schema) {
        // 第一张表
        schema.create("posts", table -> {
            table.id();
            table.string("title");
            table.longText("content");
            table.timestamps();
        });
        // 第二张表
        schema.create("comments", table -> {
            table.id();
            table.foreign("post_id").references("id").on("posts").onDelete("cascade");
            table.text("body");
            table.timestamps();
        });
    }

    @Override
    public void down(Schema schema) {
        // 按相反顺序删除
        schema.dropIfExists("comments");
        schema.dropIfExists("posts");
    }
}
```

指定数据源示例：

```java
@Component
public class Migration_2024_03_01_CreateLogTable implements Migration {

    @Override
    public String getDataSourceName() {
        return "logDataSource";  // 使用名为 logDataSource 的 DataSource Bean
    }

    @Override
    public void up(Schema schema) {
        schema.create("access_logs", table -> {
            table.id();
            table.string("path");
            table.timestamp("created_at").nullable();
        });
    }

    @Override
    public void down(Schema schema) {
        schema.dropIfExists("access_logs");
    }
}
```

---

## 5. MigrationGenerator —— 迁移类源码生成器

`com.weacsoft.jaravel.vendor.migration.MigrationGenerator`

对齐 Laravel `php artisan make:migration` 命令。按 `Migration_YYYY_MM_DD_PascalCaseDescription` 命名约定生成一个空的迁移 Java 源文件，类名自带日期前缀。`final` 工具类，不可实例化。

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `static String generate(String outputDir, String packageName, String description)` | 生成迁移 Java 源文件，返回文件绝对路径 |
| `static String toPascalCase(String description)` | 将描述字符串转换为 PascalCase（包级可见） |

### 参数说明

| 参数 | 说明 |
| --- | --- |
| `outputDir` | 输出根目录，如 `"src/main/java"` |
| `packageName` | 生成类所属的包名，如 `"com.weacsoft.jaravel.database.migration"` |
| `description` | 迁移描述，如 `"create products table"` 或 `"add email to users table"` |

### 异常

| 异常 | 触发条件 |
| --- | --- |
| `IllegalArgumentException` | 参数为空或包名非法 |
| `IllegalStateException` | 目标文件已存在，拒绝覆盖 |
| `IOException` | 写入文件失败 |

### 使用示例

```java
// 生成文件：src/main/java/com/weacsoft/jaravel/database/migration/
//           Migration_2024_06_20_CreateProductsTable.java
String path = MigrationGenerator.generate(
    "src/main/java",
    "com.weacsoft.jaravel.database.migration",
    "create products table"
);
System.out.println("生成迁移文件: " + path);
```

`toPascalCase` 转换规则示例：

| 输入 | 输出 |
| --- | --- |
| `"create users table"` | `CreateUsersTable` |
| `"add_email_to_users_table"` | `AddEmailToUsersTable` |
| `"create-products-table"` | `CreateProductsTable` |

生成的源文件结构：

```java
package com.weacsoft.jaravel.database.migration;

import com.weacsoft.jaravel.vendor.migration.Migration;
import com.weacsoft.jaravel.vendor.migration.Schema;
import org.springframework.stereotype.Component;

@Component
public class Migration_2024_06_20_CreateProductsTable implements Migration {

    @Override
    public void up(Schema schema) {
        // TODO: 在此编写正向迁移逻辑
    }

    @Override
    public void down(Schema schema) {
        // TODO: 在此编写回滚逻辑
    }
}
```

---

## 6. Schema —— 表结构构建器

`com.weacsoft.jaravel.vendor.migration.Schema`

对齐 Laravel `Illuminate\Support\Facades\Schema`。通过 `Blueprint` 声明表结构，由本类执行生成的 SQL DDL。构造时通过 `Connection.getMetaData()` 检测数据库方言。

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `void create(String table, Consumer<Blueprint> definition)` | 创建表（CREATE 模式），执行建表 + 索引 + 外键 |
| `void table(String table, Consumer<Blueprint> definition)` | 修改已有表（ALTER 模式），支持增/改/删/重命名字段 |
| `void dropIfExists(String table)` | 删除表（如存在），`DROP TABLE IF EXISTS` |
| `void drop(String table)` | 删除表，`DROP TABLE` |
| `void rename(String from, String to)` | 重命名表 |
| `boolean hasTable(String table)` | 判断表是否存在 |
| `boolean hasColumn(String table, String column)` | 判断列是否存在 |
| `JdbcTemplate getJdbcTemplate()` | 获取内部 JdbcTemplate（包级可见） |

### 方言适配细节

**重命名表**：

| 数据库 | SQL |
| --- | --- |
| MySQL | `RENAME TABLE \`old\` TO \`new\`` |
| SQLite / H2 | `ALTER TABLE \`old\` RENAME TO \`new\`` |
| SQL Server | `sp_rename 'old', 'new'` |

**修改字段**：

| 数据库 | SQL |
| --- | --- |
| MySQL | `ALTER TABLE \`t\` MODIFY \`col\` def` |
| H2 | `ALTER TABLE \`t\` ALTER COLUMN \`col\` def` |
| SQL Server | `ALTER TABLE [t] ALTER COLUMN [col] type [NULL\|NOT NULL]`（仅类型+可空性） |
| SQLite | 走重建表流程（见下文） |

**SQLite 修改字段的重建表流程**（`sqliteRecreateTableForModify`）：

1. 通过 `PRAGMA table_info` 读取现有列定义；
2. 创建临时表 `_jaravel_temp`，列定义为「现有列中未被修改的保持原样 + 被修改列使用新定义」；
3. 将原表数据按列名复制到临时表；
4. 删除原表；
5. 将临时表重命名为原表名。

> 注意：此流程会丢失原表上的索引与外键，如需保留请在迁移中重新声明。

### 使用示例

```java
// 建表
schema.create("users", table -> {
    table.id();
    table.string("name");
    table.string("email").unique();
    table.timestamps();
    table.softDeletes();
});

// 改表：新增字段
schema.table("users", table -> {
    table.string("phone").nullable();
});

// 改表：修改字段（SQLite 走重建表）
schema.table("users", table -> {
    table.string("name", 100).change();
});

// 改表：删除字段
schema.table("users", table -> {
    table.dropColumn("phone");
});

// 改表：重命名字段
schema.table("users", table -> {
    table.renameColumn("name", "username");
});

// 判断存在性
if (schema.hasTable("users")) {
    if (schema.hasColumn("users", "email")) {
        // ...
    }
}

// 重命名表
schema.rename("users", "members");

// 删除表
schema.dropIfExists("members");
```

---

## 7. Blueprint —— 表结构蓝图

`com.weacsoft.jaravel.vendor.migration.Blueprint`

对齐 Laravel `Illuminate\Database\Schema\Blueprint`。通过流式 API 声明表结构，最终由 `Schema` 执行生成 SQL DDL。支持 CREATE（建表）与 ALTER（改表）两种模式。

### 模式说明

| 模式 | 触发方式 | 生成方法 |
| --- | --- | --- |
| CREATE | `Schema.create()` | `toCreateSql()` |
| ALTER | `Schema.table()` | `toAlterSql()` + 列的 `isChange()` 标记 |

### 字段类型方法

| 方法签名 | SQL 类型（MySQL） | 说明 |
| --- | --- | --- |
| `id()` | `BIGINT AUTO_INCREMENT PRIMARY KEY` | BIGINT 自增主键 |
| `bigIncrements(String)` | `BIGINT AUTO_INCREMENT PRIMARY KEY` | BIGINT 自增主键 |
| `increments(String)` | `INT AUTO_INCREMENT PRIMARY KEY` | INT 自增主键 |
| `string(String)` | `VARCHAR(255)` | 字符串 |
| `string(String, int)` | `VARCHAR(length)` | 指定长度字符串 |
| `charColumn(String, int)` | `CHAR(length)` | 定长字符串 |
| `integer(String)` | `INT` | 整数 |
| `bigInteger(String)` | `BIGINT` | 大整数 |
| `tinyInteger(String)` | `TINYINT` | 微整数 |
| `smallInteger(String)` | `SMALLINT` | 小整数 |
| `text(String)` | `TEXT` | 文本 |
| `mediumText(String)` | `MEDIUMTEXT` | 中文本 |
| `longText(String)` | `LONGTEXT` | 长文本 |
| `booleanColumn(String)` | `TINYINT(1)` | 布尔 |
| `decimal(String)` | `DECIMAL(8,2)` | 十进制数 |
| `decimal(String, int, int)` | `DECIMAL(p,s)` | 指定精度十进制数 |
| `floatColumn(String)` | `FLOAT` | 浮点数 |
| `doubleColumn(String)` | `DOUBLE` | 双精度浮点数 |
| `date(String)` | `DATE` | 日期 |
| `dateTime(String)` | `DATETIME` | 日期时间 |
| `timestamp(String)` | `TIMESTAMP` | 时间戳 |
| `time(String)` | `TIME` | 时间 |
| `year(String)` | `YEAR` | 年份 |
| `binary(String)` | `LONGBLOB` | 二进制 |
| `json(String)` | `JSON` | JSON |
| `enumColumn(String, String...)` | `VARCHAR(255)` | 枚举（用 VARCHAR 模拟） |

### 时间戳与软删除

| 方法签名 | 说明 |
| --- | --- |
| `void timestamps()` | 添加 `created_at`、`updated_at` 两个时间戳字段（nullable） |
| `ColumnDefinition softDeletes()` | 添加 `deleted_at` 软删除字段（nullable） |
| `ColumnDefinition rememberToken()` | 添加 `remember_token VARCHAR(100) NULL` |

### 索引与外键

| 方法签名 | 说明 |
| --- | --- |
| `void index(String column)` | 单列普通索引 |
| `void index(String... columns)` | 复合索引 |
| `void unique(String column)` | 单列唯一索引 |
| `void unique(String... columns)` | 复合唯一索引 |
| `ForeignKeyDefinition foreign(String column)` | 外键定义 |
| `void dropColumn(String column)` | 删除列（ALTER TABLE DROP COLUMN） |
| `void dropIndex(String indexName)` | 删除索引（按方言生成） |
| `void renameColumn(String from, String to)` | 重命名列（RENAME COLUMN） |

### SQL 生成方法

| 方法签名 | 说明 |
| --- | --- |
| `String toCreateSql()` | 生成 CREATE TABLE 语句（含复合主键） |
| `List<String> toIndexSql()` | 生成建表后的索引语句列表 |
| `List<String> toForeignKeySql()` | 生成外键语句列表 |
| `String toDropSql()` | 生成 DROP TABLE 语句 |
| `List<String> toAlterSql()` | 生成 ALTER 相关语句（dropColumn/renameColumn/dropIndex） |

### 方言判断方法

| 方法签名 | 说明 |
| --- | --- |
| `boolean isMysql()` | 是否为 MySQL 方言 |
| `boolean isSqlite()` | 是否为 SQLite 方言 |
| `boolean isH2()` | 是否为 H2 方言 |
| `boolean isSqlServer()` | 是否为 SQL Server 方言 |
| `String quote(String identifier)` | 按方言对标识符加引号（MySQL/SQLite/H2 用反引号，SQL Server 用方括号） |

### 使用示例

```java
schema.create("orders", table -> {
    table.id();
    table.foreign("user_id").references("id").on("users").onDelete("cascade");
    table.decimal("amount", 10, 2);
    table.string("status").defaultValue("pending");
    table.timestamp("shipped_at").nullable();
    table.timestamps();

    // 复合唯一索引
    table.unique("user_id", "product_id");
});
```

---

## 8. ColumnDefinition —— 列定义

`com.weacsoft.jaravel.vendor.migration.ColumnDefinition`

对齐 Laravel Blueprint 中的列修饰链。由 `Blueprint` 的各类字段方法创建，通过链式调用追加修饰。支持多数据库方言的类型映射与自增语法。

### 链式修饰方法

| 方法签名 | 说明 |
| --- | --- |
| `ColumnDefinition nullable()` | 标记该列为可空 |
| `ColumnDefinition notNull()` | 标记该列为非空 |
| `ColumnDefinition defaultValue(Object value)` | 设置默认值 |
| `ColumnDefinition comment(String comment)` | 设置注释（仅 MySQL 支持） |
| `ColumnDefinition autoIncrement()` | 标记自增 |
| `ColumnDefinition primary()` | 标记为主键 |
| `ColumnDefinition unique()` | 标记唯一索引 |
| `ColumnDefinition index()` | 标记普通索引 |
| `ColumnDefinition unsigned()` | 标记无符号（MySQL 数值类型） |
| `ColumnDefinition after(String column)` | ALTER TABLE 时将字段置于指定字段之后（仅 MySQL） |
| `ColumnDefinition change()` | 标记为修改已有字段（ALTER TABLE MODIFY / ALTER COLUMN） |

### SQL 生成方法

| 方法签名 | 说明 |
| --- | --- |
| `String toSql()` | 返回该列的完整 SQL 片段（含类型、自增、主键、可空、默认值、注释、AFTER） |
| `String toModifyColumnFragment()` | 生成 ALTER COLUMN 修改片段（不含 ALTER TABLE 前缀） |

### 类型映射（按方言）

| 逻辑类型 | MySQL / SQLite / H2 | SQL Server |
| --- | --- | --- |
| string | `VARCHAR(n)` | `VARCHAR(n)` |
| integer | `INT` / `INT UNSIGNED` | `INT` |
| bigInteger | `BIGINT` / `BIGINT UNSIGNED` | `BIGINT` |
| text | `TEXT` | `VARCHAR(MAX)` |
| boolean | `TINYINT(1)` | `BIT` |
| decimal | `DECIMAL(p,s)` | `DECIMAL(p,s)` |
| dateTime | `DATETIME` | `DATETIME2` |
| timestamp | `TIMESTAMP` | `DATETIME2`（SQL Server TIMESTAMP 实为 rowversion） |
| year | `YEAR` | `SMALLINT`（SQL Server 无 YEAR 类型） |
| json | `JSON` | `NVARCHAR(MAX)`（SQL Server 无原生 JSON 类型） |
| binary | `LONGBLOB` | `VARBINARY(MAX)` |

### 自增语法（按方言）

| 数据库 | 自增主键语法 |
| --- | --- |
| MySQL / H2 | `AUTO_INCREMENT` |
| SQLite | `INTEGER PRIMARY KEY AUTOINCREMENT` |
| SQL Server | `IDENTITY(1,1) PRIMARY KEY` |

### 使用示例

```java
schema.create("products", table -> {
    table.id();
    table.string("name").notNull().comment("商品名称");
    table.string("sku").unique();
    table.decimal("price", 10, 2).defaultValue(0);
    table.booleanColumn("active").defaultValue(true);
    table.integer("stock").unsigned().defaultValue(0);
    table.timestamps();
});

// 修改字段
schema.table("products", table -> {
    table.string("name", 200).change();  // 修改 name 列长度为 200
});
```

---

## 9. ForeignKeyDefinition —— 外键定义

`com.weacsoft.jaravel.vendor.migration.ForeignKeyDefinition`

对齐 Laravel Blueprint 的 `foreign()` 链。

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `ForeignKeyDefinition references(String column)` | 引用列 |
| `ForeignKeyDefinition on(String table)` | 引用表 |
| `ForeignKeyDefinition onDelete(String action)` | 删除时动作，如 `"cascade"`、`"set null"`、`"restrict"` |
| `ForeignKeyDefinition onUpdate(String action)` | 更新时动作 |
| `ForeignKeyDefinition name(String name)` | 约束名（默认 `表名_列名_foreign`） |
| `String toSql(String table)` | 生成 ADD CONSTRAINT 子句 |

### 使用示例

```java
schema.create("posts", table -> {
    table.id();
    table.foreign("user_id")
        .references("id")
        .on("users")
        .onDelete("cascade")
        .onUpdate("cascade");
    table.string("title");
});
// 生成：ALTER TABLE `posts` ADD CONSTRAINT `posts_user_id_foreign`
//       FOREIGN KEY (`user_id`) REFERENCES `users`(`id`)
//       ON DELETE CASCADE ON UPDATE CASCADE
```

---

## 10. Migrator —— 迁移引擎

`com.weacsoft.jaravel.vendor.migration.Migrator`

对齐 Laravel `Illuminate\Database\Migrations\Migrator`。收集容器中所有 `Migration` 实例，按名称排序，与 `MigrationRepository` 记录比对，执行待运行的迁移或回滚。

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `List<String> run()` | 执行所有待运行迁移，返回已执行的迁移名称列表 |
| `List<String> rollback(int steps)` | 回滚指定步数（批次），返回已回滚的迁移名称列表 |
| `List<String> reset()` | 回滚所有迁移（倒序） |
| `List<String> refresh()` | 回滚所有并重新迁移（reset + run） |
| `void status()` | 输出迁移状态（[Y]/[N] 标记是否已执行） |
| `List<String> pending()` | 获取待运行迁移名称列表 |

### 执行流程

**run()（正向迁移）**：

```
1. createRepository()  -- 确保 migrations 表存在
2. getRan()            -- 查询已执行迁移列表
3. getNextBatchNumber()-- 获取下一批次号
4. 遍历 sortedMigrations()（按名称字典序）：
   - 若该迁移不在 ran 列表中：
     a. 调用 migration.up(resolveSchema(migration))
     b. repository.log(name, batch)  -- 记录到 migrations 表
5. 返回已执行列表
```

**rollback(steps)（回滚）**：

```
1. createRepository()
2. getLast()  -- 获取最后一批执行的迁移（按 id 倒序）
3. 遍历最后一批迁移（最多 steps 个）：
   a. 调用 migration.down(resolveSchema(migration))
   b. repository.delete(name)  -- 从 migrations 表删除
4. 返回已回滚列表
```

### 每迁移数据源

`resolveSchema(Migration)` 方法根据 `Migration.getDataSourceName()` 决定使用哪个数据源：

- 返回 null 或空字符串：使用默认 Schema（Primary 数据源）
- 返回非空字符串：从 Spring 容器中查找对应名称的 `DataSource` Bean，创建独立的 `Schema` 实例

### 使用示例

```java
@Autowired
private Migrator migrator;

// 执行迁移
List<String> executed = migrator.run();

// 回滚最近 1 批
List<String> rolledBack = migrator.rollback(1);

// 回滚全部
migrator.reset();

// 回滚全部并重新迁移
migrator.refresh();

// 查看状态
migrator.status();
// 输出示例：
// [migration] Status:
//   Ran?  Migration
//   [Y]   Migration_2024_01_01_CreateUsersTable
//   [N]   Migration_2024_02_01_CreateBlogTables
```

---

## 11. MigrationRepository —— 迁移记录仓库

`com.weacsoft.jaravel.vendor.migration.MigrationRepository`

对齐 Laravel `Illuminate\Database\Migrations\DatabaseMigrationRepository`。维护 `migrations` 表，记录已执行的迁移。

### migrations 表结构

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `id` | 自增主键 | 记录 ID |
| `migration` | `VARCHAR(255) NOT NULL` | 迁移名称（类名） |
| `batch` | `INT NOT NULL` | 批次号 |

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `void createRepository()` | 创建 migrations 记录表（如不存在），按方言生成建表语句 |
| `List<String> getRan()` | 获取已执行的迁移名称列表（按 id 正序） |
| `List<String> getLast()` | 获取最后一批执行的迁移（按 id 倒序） |
| `void log(String migration, int batch)` | 记录一条已执行迁移 |
| `void delete(String migration)` | 删除一条迁移记录（回滚时） |
| `int getNextBatchNumber()` | 获取下一批次号（MAX(batch) + 1） |
| `String getTable()` | 获取迁移记录表名 |

### 方言适配

`createRepository()` 按数据库方言生成不同的建表语句：

| 数据库 | 自增语法 |
| --- | --- |
| MySQL | `INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY` + `ENGINE=InnoDB` |
| SQLite | `INTEGER PRIMARY KEY AUTOINCREMENT` |
| H2 | `INT NOT NULL AUTO_INCREMENT PRIMARY KEY` |
| SQL Server | `INT IDENTITY(1,1) PRIMARY KEY`（先查 `sys.tables` 判断是否存在） |

---

## 12. MigrationRunner —— 命令行运行器

`com.weacsoft.jaravel.vendor.migration.MigrationRunner`

对齐 Laravel artisan 的 migrate 系列命令。实现 `CommandLineRunner`，通过启动参数触发迁移命令。

### 支持的命令参数

| 启动参数 | 说明 |
| --- | --- |
| `--jaravel.migrate` | 执行迁移 |
| `--jaravel.rollback` | 回滚最近 1 批 |
| `--jaravel.rollback=N` | 回滚最近 N 批 |
| `--jaravel.reset` | 回滚全部 |
| `--jaravel.refresh` | 回滚全部并重新迁移 |
| `--jaravel.migration-status` | 查看状态 |

当无显式命令且 `jaravel.migration.auto-run=true` 时，启动自动执行 migrate。

### 使用示例

```bash
# 执行迁移
java -jar app.jar --jaravel.migrate

# 回滚最近 3 批
java -jar app.jar --jaravel.rollback=3

# 回滚全部
java -jar app.jar --jaravel.reset

# 回滚全部并重新迁移
java -jar app.jar --jaravel.refresh

# 查看状态
java -jar app.jar --jaravel.migration-status
```

---

## 13. MigrationAutoConfiguration —— 自动装配

`com.weacsoft.jaravel.vendor.migration.MigrationAutoConfiguration`

Spring Boot 自动装配类。当容器中存在 `DataSource` 且 `jaravel.migration.enabled=true`（默认）时，注册以下 Bean：

| Bean | 类型 | 说明 |
| --- | --- | --- |
| `jaravelSchema` | `Schema` | 表结构构建器，绑定 Primary 数据源 |
| `jaravelMigrationRepository` | `MigrationRepository` | 迁移记录仓库 |
| `jaravelMigrator` | `Migrator` | 迁移引擎，注入所有 `Migration` Bean |
| `jaravelMigrationRunner` | `MigrationRunner` | 命令行运行器（`@Order(HIGHEST_PRECEDENCE)`） |

所有 Bean 均带 `@ConditionalOnMissingBean`，允许业务方自定义替换。通过 `@AutoConfigureAfter(DataSourceAutoConfiguration.class)` 确保数据源已就绪。

---

## 14. 配置选项

配置前缀为 `jaravel.migration`，对应 `MigrationProperties` 类。

```yaml
jaravel:
  migration:
    enabled: true          # 是否启用迁移模块（默认 true）
    table: migrations      # 迁移记录表名（默认 migrations）
    auto-run: false        # 启动时是否自动执行 migrate（默认 false）
```

| 属性 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `jaravel.migration.enabled` | `boolean` | `true` | 是否启用迁移模块 |
| `jaravel.migration.table` | `String` | `migrations` | 迁移记录表名 |
| `jaravel.migration.auto-run` | `boolean` | `false` | 启动时是否自动执行 migrate |

---

## 15. 线程安全说明

| 类 | 线程安全性 | 说明 |
| --- | --- | --- |
| `Schema` | 单线程使用 | 内部 `JdbcTemplate` 线程安全，但 `databaseProductName` 在构造时一次性确定。`Schema` 实例本身无共享可变状态，可在多线程中使用，但迁移操作通常串行执行 |
| `Blueprint` | 非线程安全 | 使用 `ArrayList` 存储列与命令，应在单线程内构建并消费。每个 `create()` / `table()` 调用创建独立的 `Blueprint` 实例 |
| `ColumnDefinition` | 非线程安全 | 链式调用修改内部字段，应在单线程内使用。由 `Blueprint` 在单线程内创建 |
| `ForeignKeyDefinition` | 非线程安全 | 同 `ColumnDefinition`，链式调用构建 |
| `Migrator` | 需外部同步 | `run()` / `rollback()` 等方法操作 `MigrationRepository`，并发调用可能导致批次号冲突。应在单线程或加锁环境下调用 |
| `MigrationRepository` | 需外部同步 | 基于 `JdbcTemplate`，SQL 操作本身原子，但 `getNextBatchNumber()` 与 `log()` 之间存在竞态，并发迁移需外部同步 |
| `MigrationRunner` | 单次执行 | `CommandLineRunner.run()` 在启动时单线程调用，无需考虑并发 |
| `MigrationGenerator` | 线程安全 | 静态方法，无共享可变状态，可安全并发调用（但同一文件路径并发写入会失败） |
