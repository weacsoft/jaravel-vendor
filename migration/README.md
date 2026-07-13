# migration 模块

> Jaravel-Vendor 的数据库迁移模块，提供 Laravel 风格的 Blueprint 流式建表、`up()` / `down()` 迁移引擎、`migrate` / `rollback` / `reset` / `refresh` / `status` 命令，以及 MySQL、SQLite、H2、SQL Server、PostgreSQL、Oracle 六种数据库方言自动适配。支持 DIRECTORY / DIRECTORY_CLASSES / PACKAGED / JAR / CLASSPATH 五种迁移源模式，适配开发与生产部署。内置 `MigrationPrecompiler` 预编译工具，支持开发阶段将 `.java` 迁移文件预编译为 zip 包或散乱 class，生产环境仅需 JRE 即可加载。**核心逻辑独立于 SpringBoot，可通过 `MigrationCLI` 在纯 Java 环境中运行**。内置 `TableMigrator` 跨库表迁移工具，支持 1 步直连迁移和 2 步导出导入迁移。包名统一为 `com.weacsoft.jaravel.vendor.migration`。

---

## 目录

- [1. 模块概述](#1-模块概述)
- [2. 依赖信息](#2-依赖信息)
- [3. 类总览](#3-类总览)
- [4. 迁移源模式](#4-迁移源模式)
- [5. JDK/JRE 构建配置](#5-jdkjre-构建配置)
- [6. 独立运行（无 SpringBoot）](#6-独立运行无-springboot)
- [7. Migration —— 迁移接口](#7-migration--迁移接口)
- [8. MigrationAnnotation —— 迁移标记注解](#8-migrationannotation--迁移标记注解)
- [9. MigrationScanner —— 迁移源扫描器](#9-migrationscanner--迁移源扫描器)
- [10. MigrationGenerator —— 迁移类源码生成器](#10-migrationgenerator--迁移类源码生成器)
- [11. Schema —— 表结构构建器](#11-schema--表结构构建器)
- [12. Blueprint —— 表结构蓝图](#12-blueprint--表结构蓝图)
- [13. ColumnDefinition —— 列定义](#13-columndefinition--列定义)
- [14. ForeignKeyDefinition —— 外键定义](#14-foreignkeydefinition--外键定义)
- [15. Migrator —— 迁移引擎](#15-migrator--迁移引擎)
- [16. MigrationRepository —— 迁移记录仓库](#16-migrationrepository--迁移记录仓库)
- [17. MigrationExecutor —— 迁移执行器](#17-migrationexecutor--迁移执行器)
- [18. MigrationRunner —— SpringBoot 适配器](#18-migrationrunner--springboot-适配器)
- [19. MigrationCLI —— 独立命令行入口](#19-migrationcli--独立命令行入口)
- [20. JdbcExecutor —— 轻量 JDBC 执行器](#20-jdbcexecutor--轻量-jdbc-执行器)
- [21. Dialect —— 数据库方言接口](#21-dialect--数据库方言接口)
- [22. TableMigrator —— 跨库表迁移工具](#22-tablemigrator--跨库表迁移工具)
- [23. MigrationAutoConfiguration —— 自动装配](#23-migrationautoconfiguration--自动装配)
- [24. 配置选项](#24-配置选项)
- [25. 预编译 —— 开发阶段预编译工具](#25-预编译--开发阶段预编译工具)
- [26. 线程安全说明](#26-线程安全说明)

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

通过 `DialectFactory` 自动检测数据库方言（基于 `Connection.getMetaData().getDatabaseProductName()`），支持：

| 数据库 | 标识符引用 | 自增语法 | 建表选项 | 系统目录 |
| --- | --- | --- | --- | --- |
| MySQL | 反引号 `` ` `` | `AUTO_INCREMENT` | `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4` | `information_schema` |
| SQLite | 反引号 `` ` `` | `AUTOINCREMENT` | 无 | `sqlite_master` |
| H2 | 反引号 `` ` `` | `AUTO_INCREMENT` | 无 | `INFORMATION_SCHEMA` |
| SQL Server | 方括号 `[]` | `IDENTITY(1,1)` | 无 | `sys.tables` / `sys.columns` |
| PostgreSQL | 双引号 `""` | `GENERATED ALWAYS AS IDENTITY` | 无 | `information_schema` |
| Oracle | 双引号 `""` | `GENERATED ALWAYS AS IDENTITY` | 无 | `user_tables` / `user_tab_columns` |

方言采用策略模式实现，所有方言差异（标识符引用、类型映射、自增语法、系统目录查询、ALTER TABLE 语法等）集中在 `Dialect` 接口实现类中，`Schema`、`Blueprint`、`ColumnDefinition`、`MigrationRepository` 通过委托 `Dialect` 完成方言适配，上层 API 无需感知具体方言。新增数据库只需添加一个 `Dialect` 实现类，无需修改现有代码。

### 多表支持

单次 `up()` 可连续调用多次 `Schema.create()` 或 `Schema.table()` 处理多张表，`down()` 应对称地删除/回滚所有在 `up()` 中创建或修改的表。

### 五种迁移源模式

模块支持五种迁移来源（`MigrationSource` 枚举），适配不同的部署环境：

- **DIRECTORY**：从目录读取 `.java` 文件，运行时内存编译（需要 JDK）
- **DIRECTORY_CLASSES**：从目录加载预编译 `.class` 文件（只需要 JRE）
- **PACKAGED**：从预编译 zip 包加载迁移类（只需要 JRE）
- **JAR**：从 `.jar` 文件加载预编译迁移类（只需要 JRE）
- **CLASSPATH**：从 classpath 扫描内置迁移类（只需要 JRE）

详见 [第 4 节：迁移源模式](#4-迁移源模式)。

---

## 2. 依赖信息

### Maven 坐标

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>migration</artifactId>
    <version>0.1.2</version>
</dependency>
```

### 传递依赖

| 依赖 | 用途 |
| --- | --- |
| `com.weacsoft:core` | Facade 基础设施 |
| `com.weacsoft:utils` | 内存编译基础设施（`MemoryClassLoader` / `MemoryFileManager` / `SourceCodeJavaFileObject`） |
| `org.springframework:spring-jdbc` | `JdbcTemplate` 执行 SQL DDL |
| `org.springframework.boot:spring-boot-autoconfigure` | 自动装配 |
| `org.slf4j:slf4j-api` | 日志门面 |

> 运行环境要求：JDK 17+（DIRECTORY 模式）或 JRE 17+（DIRECTORY_CLASSES / PACKAGED / JAR / CLASSPATH 模式），Spring Boot 3.2.5（Spring 6.x），容器中需存在 `javax.sql.DataSource` Bean。

---

## 3. 类总览

```
com.weacsoft.jaravel.vendor.migration
├── Migration                    // 迁移接口（up/down/getName）
├── MigrationAnnotation          // 迁移类标记注解（替代 @Component）
├── MigrationSource              // 迁移源类型枚举（DIRECTORY / DIRECTORY_CLASSES / PACKAGED / JAR / CLASSPATH）
├── MigrationScanner             // 迁移源扫描器与加载器（支持五种模式）
├── MigrationPrecompiler         // 预编译工具（开发阶段将 .java 预编译为 zip/class）
├── MigrationPrecompilerMain     // 预编译命令行工具
├── MigrationGenerator           // 迁移类源码生成器（make:migration）
├── Schema                       // 表结构构建器（create/table/drop/rename/hasTable/hasColumn）
├── Blueprint                    // 表结构蓝图（流式 API，CREATE 与 ALTER 模式）
├── ColumnDefinition             // 列定义（类型映射 + 链式修饰）
├── ForeignKeyDefinition         // 外键定义（references/on/onDelete/onUpdate）
├── Dialect                      // 数据库方言接口（策略模式，封装各数据库 DDL 差异）
├── AbstractDialect              // 方言抽象基类（通用默认实现）
├── DialectFactory               // 方言工厂（自动检测数据库产品名并返回对应实现）
├── MysqlDialect                 // MySQL 方言
├── SqliteDialect                // SQLite 方言
├── H2Dialect                    // H2 方言
├── SqlServerDialect             // SQL Server 方言
├── PostgresqlDialect            // PostgreSQL 方言
├── OracleDialect                // Oracle 方言
├── TableMigrator                // 跨库表迁移工具（1步直连/2步导出导入）
├── Migrator                     // 迁移引擎（run/rollback/reset/refresh/status/pending）
├── MigrationRepository          // 迁移记录仓库（migrations 表 CRUD）
├── MigrationRunner              // 命令行运行器（--jaravel.migrate 等参数）
├── MigrationProperties          // 配置属性（jaravel.migration.*）
└── MigrationAutoConfiguration   // 自动装配
```

### MigrationSource —— 迁移源类型枚举

`com.weacsoft.jaravel.vendor.migration.MigrationSource`

定义五种迁移来源，适配不同的部署场景：

| 枚举值 | 说明 | 运行环境 | 加载方法 |
| --- | --- | --- | --- |
| `DIRECTORY` | 从目录读取 `.java` 文件，运行时内存编译 | JDK | `MigrationScanner.compileFromDirectory()` |
| `DIRECTORY_CLASSES` | 从目录加载预编译的 `.class` 文件 | JRE | `MigrationScanner.loadFromDirectoryClasses()` |
| `PACKAGED` | 从预编译 zip 包加载迁移类 | JRE | `MigrationScanner.loadFromZip()` |
| `JAR` | 从 `.jar` 文件加载预编译的迁移类 | JRE | `MigrationScanner.loadFromJar()` |
| `CLASSPATH` | 从当前 classpath 扫描迁移类（内置迁移） | JRE | `MigrationScanner.loadFromClasspath()` |

五种模式均通过 `@MigrationAnnotation` 注解自动识别迁移类，无需手动指定包名。

---

## 4. 迁移源模式

`migration` 模块支持五种迁移来源模式，通过 `jaravel.migration.source` 配置项切换，适配不同的部署环境（开发 / 生产 / 内置迁移）。

### 模式总览

| 模式 | 说明 | 需要 | 配置 |
| --- | --- | --- | --- |
| `DIRECTORY` | 从目录读取 `.java` 文件，运行时内存编译 | JDK | `jaravel.migration.source=DIRECTORY` |
| `DIRECTORY_CLASSES` | 从目录加载预编译 `.class` 文件 | JRE | `jaravel.migration.source=DIRECTORY_CLASSES` |
| `PACKAGED` | 从预编译 zip 包加载迁移类 | JRE | `jaravel.migration.source=PACKAGED` |
| `JAR` | 从 `.jar` 文件加载预编译迁移类 | JRE | `jaravel.migration.source=JAR` |
| `CLASSPATH` | 从 classpath 扫描内置迁移类 | JRE | `jaravel.migration.source=CLASSPATH` |

五种模式均通过 `@MigrationAnnotation` 注解自动识别迁移类，无需手动指定包名。

### DIRECTORY 模式（目录模式）

从指定目录读取 `.java` 源文件，在运行时通过 `javax.tools.JavaCompiler` 进行内存编译。需要完整的 JDK 环境（JRE 中不包含编译器）。

- 适用场景：开发阶段，迁移文件以源码形式存在，修改后即时生效
- 配置项：`jaravel.migration.directory`（默认 `migrations`）
- 加载方法：`MigrationScanner.compileFromDirectory()`
- 编译产物存储于内存（`compiledClasses`），通过 `MemoryClassLoader` 加载，执行后自动释放

```yaml
jaravel:
  migration:
    source: DIRECTORY
    directory: migrations
```

### DIRECTORY_CLASSES 模式（预编译 class 目录模式）

从指定目录加载预编译的 `.class` 文件，通过 `URLClassLoader` 或 `MemoryClassLoader` 加载。只需要 JRE 环境，无需 JDK。

- 适用场景：生产部署，迁移类已在开发阶段预编译为 `.class` 文件（通过 `MigrationPrecompiler` 或 `javac` 编译）
- 配置项：`jaravel.migration.classes-dir`（指向包含 `.class` 文件的目录）
- 加载方法：`MigrationScanner.loadFromDirectoryClasses()`
- 扫描目录下所有 `.class` 文件（递归），通过 `@MigrationAnnotation` 识别迁移类

```yaml
jaravel:
  migration:
    source: DIRECTORY_CLASSES
    classes-dir: precompiled/migrations
```

预编译步骤（开发阶段，需 JDK）：

```bash
# 方式一：使用 MigrationPrecompilerMain 命令行工具（推荐）
java -cp migration.jar:utils.jar \
  com.weacsoft.jaravel.vendor.migration.engine.MigrationPrecompilerMain \
  --source-dir=migrations \
  --output-dir=precompiled/migrations \
  --mode=classes

# 方式二：使用 javac 手动编译
javac -d precompiled/migrations -cp migration.jar:utils.jar \
  src/main/java/com/example/migrations/*.java

# 部署 precompiled/migrations 目录到生产环境，配置 source=DIRECTORY_CLASSES
```

### PACKAGED 模式（预编译打包模式）

从预编译的 zip 包加载迁移类，通过 `MemoryClassLoader` 从 zip 读取字节码后直接定义类。只需要 JRE 环境，无需 JDK。zip 包通过 `MigrationPrecompiler` 在开发阶段生成，包含 `manifest.txt`（迁移类名列表）和按包结构存储的 `.class` 文件。

- 适用场景：生产部署，迁移类已在开发阶段预编译打包为单个 zip 文件，便于分发与管理
- 配置项：`jaravel.migration.package-path`（指向 `.jmigration.zip` 文件）
- 加载方法：`MigrationScanner.loadFromZip()`
- zip 包格式：`manifest.txt`（迁移类名列表，每行一个类全限定名）+ `.class` 文件按包结构存储

```yaml
jaravel:
  migration:
    source: PACKAGED
    package-path: precompiled/migrations.jmigration.zip
```

预编译步骤（开发阶段，需 JDK）：

```bash
# 使用 MigrationPrecompilerMain 命令行工具打包为 zip
java -cp migration.jar:utils.jar \
  com.weacsoft.jaravel.vendor.migration.engine.MigrationPrecompilerMain \
  --source-dir=migrations \
  --output-dir=precompiled \
  --mode=packaged \
  --file-name=migrations.jmigration.zip

# 部署 precompiled/migrations.jmigration.zip 到生产环境，配置 source=PACKAGED
```

### JAR 模式

从独立的 `.jar` 文件加载预编译的迁移类，通过 `URLClassLoader` 加载。只需要 JRE 环境，无需 JDK。

- 适用场景：生产部署，迁移类已预编译打包为独立 jar
- 配置项：`jaravel.migration.jar-path`
- 加载方法：`MigrationScanner.loadFromJar()`
- 扫描 JAR 中所有 `.class` 文件（跳过 `META-INF` 与内部类），通过 `@MigrationAnnotation` 识别迁移类

```yaml
jaravel:
  migration:
    source: JAR
    jar-path: /path/to/migrations.jar
```

### CLASSPATH 模式

从当前 classpath 扫描所有 `.class` 文件，通过 `@MigrationAnnotation` 识别迁移类。只需要 JRE 环境。迁移类与框架打包在同一 jar 中（内置迁移）。

- 适用场景：内置迁移，迁移类随框架一起打包发布
- 加载方法：`MigrationScanner.loadFromClasspath()`
- classpath 条目可以是目录（递归扫描 `.class`）或 JAR 文件（扫描 jar 内条目）

```yaml
jaravel:
  migration:
    source: CLASSPATH
```

### 自动包名检测

五种迁移源模式均支持**自动包名检测**：迁移文件可以使用任意包名，`MigrationScanner` 会自动识别并加载。无需手动配置包扫描路径。

- DIRECTORY 模式：从 `.java` 源文件中解析 `package` 声明提取包名
- DIRECTORY_CLASSES / PACKAGED / JAR / CLASSPATH 模式：扫描所有 `.class` 文件，通过 `@MigrationAnnotation` 注解判断是否为迁移类，自动识别任意包名

### 模式选择建议

| 场景 | 推荐模式 | 运行环境 |
| --- | --- | --- |
| 本地开发 | `DIRECTORY` | JDK |
| 生产部署（迁移类预编译为散乱 class） | `DIRECTORY_CLASSES` | JRE |
| 生产部署（迁移类预编译打包为 zip） | `PACKAGED` | JRE |
| 生产部署（迁移类独立 jar） | `JAR` | JRE |
| 框架内置迁移 | `CLASSPATH` | JRE |

---

## 5. JDK/JRE 构建配置

`migration` 模块通过 Maven Profile 适配不同的运行环境。`pom.xml` 中定义了两个 profile：

### Profile 说明

| Profile | 激活方式 | 运行环境 | 支持的迁移源模式 |
| --- | --- | --- | --- |
| `jdk-mode` | 默认（未指定 `-Djre-only` 时激活） | 完整 JDK | DIRECTORY / DIRECTORY_CLASSES / PACKAGED / JAR / CLASSPATH |
| `jre-mode` | `-Djre-only` | 仅 JRE | DIRECTORY_CLASSES / PACKAGED / JAR / CLASSPATH |

### jdk-mode（默认）

完整 JDK 环境，`javax.tools.JavaCompiler` 由 JDK 提供，支持全部五种迁移源模式（包括 DIRECTORY 内存编译）。

```bash
# 默认构建，使用 JDK
mvn clean package
```

### jre-mode

仅 JRE 环境，不包含编译器，因此不支持 DIRECTORY 内存编译模式，仅支持 DIRECTORY_CLASSES、PACKAGED、JAR 与 CLASSPATH 模式。

```bash
# JRE 模式构建
mvn clean package -Djre-only
```

> 注意：使用 `jre-mode` 构建时，若配置 `jaravel.migration.source=DIRECTORY`，运行时会因无法获取 `JavaCompiler` 而抛出 `IllegalStateException`，错误信息提供 4 种解决方案：
> 1. 切换为 `DIRECTORY_CLASSES` 模式，使用预编译 `.class` 文件
> 2. 切换为 `PACKAGED` 模式，使用预编译 zip 包
> 3. 切换为 `JAR` 模式，使用预编译 jar 包
> 4. 安装完整 JDK 环境

### 构建时打包迁移目录（CLASSPATH 模式）

`pom.xml` 的 `<resources>` 中可选地将迁移目录打包进 jar，用于 CLASSPATH 模式。通过 `jaravel.migration.package-in-jar=true` 配合构建激活：

```xml
<resource>
    <directory>${project.basedir}/../../database/migration</directory>
    <targetPath>migrations</targetPath>
    <includes>
        <include>**/*.java</include>
    </includes>
</resource>
```

---

## 6. 独立运行（无 SpringBoot）

migration 模块的核心逻辑完全独立于 SpringBoot，可在纯 Java 环境中运行。

### 架构分层

```
┌─────────────────────────────────────────────┐
│         MigrationCLI (独立入口)               │  ← 无 SpringBoot
│         MigrationExecutor (核心执行器)         │  ← 无 SpringBoot
├─────────────────────────────────────────────┤
│  Migrator / Schema / MigrationRepository    │  ← 无 SpringBoot
│  MigrationScanner / Blueprint / JdbcExecutor │  ← 无 SpringBoot
├─────────────────────────────────────────────┤
│         MigrationRunner (SpringBoot 适配器)   │  ← 仅此类需要 SpringBoot
│         MigrationAutoConfiguration            │
└─────────────────────────────────────────────┘
```

### SpringBoot 环境（默认）

SpringBoot 应用中通过 `application.yml` 配置，启动时自动执行迁移：

```yaml
jaravel:
  migration:
    enabled: true
    source: DIRECTORY          # DIRECTORY / DIRECTORY_CLASSES / PACKAGED / JAR / CLASSPATH
    directory: migrations
    table: migrations
    auto-run: true
```

### 独立运行（CI/CD 或无 SpringBoot 环境）

通过 `MigrationCLI` 直接执行迁移，只需 migration.jar + utils.jar + slf4j + JDBC 驱动：

```bash
# 编译迁移模块
mvn clean package -pl migration,utils -am

# 执行迁移
java -cp migration/target/migration-0.1.2.jar:utils/target/utils-0.1.2.jar:mysql-connector.jar \
  com.weacsoft.jaravel.vendor.migration.MigrationCLI \
  --db-url=jdbc:mysql://localhost:3306/mydb \
  --db-user=root \
  --db-password=secret \
  --source=DIRECTORY \
  --directory=/path/to/migrations \
  migrate
```

### 编程式调用

```java
// 创建配置
MigrationProperties properties = new MigrationProperties();
properties.setEnabled(true);
properties.setSource(MigrationSource.DIRECTORY);
properties.setDirectory("/path/to/migrations");
properties.setTable("migrations");

// 创建数据源（任意 DataSource 实现）
DataSource dataSource = ...; // HikariDataSource, DriverManagerDataSource, etc.

// 执行迁移
MigrationExecutor executor = new MigrationExecutor(dataSource, properties);
executor.execute("migrate");           // 执行迁移
executor.execute("rollback=5");        // 回滚 5 批
executor.execute("status");            // 查看状态
```

### pom.xml 依赖说明

| 依赖 | 类型 | 说明 |
| --- | --- | --- |
| `utils` | 必须 | MemoryClassLoader（JDK 编译迁移类） |
| `slf4j-api` | 必须 | 日志接口 |
| `spring-boot-autoconfigure` | **optional** | 仅 MigrationAutoConfiguration/MigrationRunner 需要 |
| `spring-boot-configuration-processor` | **optional** | 配置元数据生成 |

独立运行时只需引入 `utils` + `slf4j-api` + JDBC 驱动，不需要 SpringBoot。

---

## 7. Migration —— 迁移接口

`com.weacsoft.jaravel.vendor.migration.Migration`

每个迁移类实现此接口，提供 `up()` 与 `down()` 两个方法，分别表示正向执行与回滚。

> **重要**：迁移类使用 `@MigrationAnnotation` 标记（非 Spring `@Component`），在运行时由 `MigrationScanner` 加载（DIRECTORY 内存编译 / DIRECTORY_CLASSES 加载 / PACKAGED 加载 / JAR 加载 / CLASSPATH 扫描）、反射实例化、执行后自动释放。

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `void up(Schema schema)` | 正向迁移：建表、加字段、加索引等。一次可处理多张表 |
| `void down(Schema schema)` | 回滚迁移：删表、删字段等，应与 `up()` 对称 |
| `default String getName()` | 迁移名称，默认返回类名。用于排序与记录到 migrations 表 |

### 使用示例

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

多表迁移示例：

```java
@MigrationAnnotation
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

---

## 7. MigrationAnnotation —— 迁移标记注解

`com.weacsoft.jaravel.vendor.migration.MigrationAnnotation`

迁移类标记注解，用于在运行时识别哪些类是迁移类。**替代 Spring `@Component`**：迁移文件不再作为 Spring Bean 注册到容器，而是通过 `MigrationScanner` 在运行时加载（DIRECTORY 编译 / DIRECTORY_CLASSES 加载 / PACKAGED 加载 / JAR 加载 / CLASSPATH 扫描）、反射实例化、执行后自动释放。

### 注解定义

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MigrationAnnotation {
    String name() default "";
}
```

### 属性

| 属性 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `name` | `String` | `""` | 迁移名称，用于排序与记录到 migrations 表。为空时使用类名 |

### 使用示例

```java
@MigrationAnnotation
public class Migration_2024_01_01_CreateUsersTable implements Migration {
    // ...
}

// 指定迁移名称
@MigrationAnnotation(name = "custom_migration_name")
public class Migration_2024_01_02_CustomName implements Migration {
    // ...
}
```

> 五种迁移源模式均通过 `@MigrationAnnotation` 自动识别迁移类，迁移文件可使用任意包名，无需手动配置包扫描路径。

---

## 8. MigrationScanner —— 迁移源扫描器

`com.weacsoft.jaravel.vendor.migration.MigrationScanner`

迁移源扫描器与加载器，支持五种迁移来源模式（DIRECTORY / DIRECTORY_CLASSES / PACKAGED / JAR / CLASSPATH）。五种模式均通过 `@MigrationAnnotation` 注解自动识别迁移类，无需手动指定包名。

### 核心数据结构

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `compiledClasses` | `Map<String, byte[]>` | DIRECTORY 模式编译后的字节码（类名 -> 字节码） |
| `loadedClasses` | `Map<String, Class<?>>` | DIRECTORY_CLASSES / PACKAGED / JAR / CLASSPATH 模式加载的类（类名 -> Class） |
| `memoryClassLoader` | `MemoryClassLoader` | 内存类加载器，懒加载（DIRECTORY 模式使用） |
| `jarClassLoader` | `URLClassLoader` | JAR 模式的类加载器，用于资源释放 |

### 方法文档

| 方法签名 | 适用模式 | 说明 |
| --- | --- | --- |
| `void compileFromDirectory(String directory)` | DIRECTORY | 编译指定目录下所有 `.java` 文件（内存编译），扫描仅一级不递归 |
| `void compileFromDirectory(File dir)` | DIRECTORY | 同上，接收 `File` 参数 |
| `void loadFromDirectoryClasses(String dirPath)` | DIRECTORY_CLASSES | 从目录加载预编译 `.class` 文件（仅需 JRE），递归扫描所有 `.class` |
| `void loadFromDirectoryClasses(File dir)` | DIRECTORY_CLASSES | 同上，接收 `File` 参数 |
| `void loadFromZip(String zipPath)` | PACKAGED | **新增**：从预编译 zip 包加载迁移类（仅需 JRE），使用 `MemoryClassLoader` 从 zip 读取字节码后直接定义类 |
| `void loadFromJar(File jarFile)` | JAR | 从 JAR 文件加载预编译迁移类，使用 `URLClassLoader`，扫描所有 `.class`（跳过 `META-INF` 与内部类） |
| `void loadFromClasspath()` | CLASSPATH | 从当前 classpath 扫描迁移类，支持目录（递归）与 JAR 文件 |
| `Class<?> getCompiledClass(String className)` | 通用 | 获取已编译或已加载的类，优先从 `compiledClasses` 查找，其次 `loadedClasses` |
| `Map<String, byte[]> getCompiledClasses()` | 通用 | **新增**：获取编译后的字节码（类名 -> 字节码），供 `MigrationPrecompiler` 预编译器使用 |
| `List<String> getAllMigrationClassNames()` | 通用 | 获取所有迁移类全限定名列表（含 DIRECTORY 编译的与 DIRECTORY_CLASSES/PACKAGED/JAR/CLASSPATH 加载的） |
| `void finish()` | 通用 | 释放所有资源：清除类字节码、已加载类、关闭 `URLClassLoader`、释放 `MemoryClassLoader` |
| `MemoryClassLoader getMemoryClassLoader()` | DIRECTORY | 获取内存类加载器（懒加载，每次返回同一实例） |
| `void removeMemoryClassLoader()` | DIRECTORY | 释放内存类加载器，置为 null 使已加载类可被 GC 回收 |
| `void removeAll()` | 通用 | 清除所有已编译与已加载的类 |

### 各模式加载流程

**DIRECTORY 模式（`compileFromDirectory`）**：

```
1. 扫描目录下所有 .java 文件（仅一级，不递归）
2. 逐个文件：
   a. 读取源码
   b. 移除注释与字符串，提取 package 声明得到包名
   c. 移除 @Component 注解与 import（兼容旧迁移文件）
   d. 使用 JavaCompiler + MemoryFileManager 内存编译
   e. 将字节码存入 compiledClasses
```

**DIRECTORY_CLASSES 模式（`loadFromDirectoryClasses`）**：

```
1. 递归扫描目录下所有 .class 文件
2. 逐个文件：
   a. 读取 .class 字节码
   b. 通过 URLClassLoader 或 MemoryClassLoader 加载类
   c. 检查 @MigrationAnnotation 注解与 Migration 接口
   d. 符合条件的类存入 loadedClasses
```

**PACKAGED 模式（`loadFromZip`）**：

```
1. 打开 zip 文件，读取 manifest.txt 获取迁移类名列表
2. 逐个类名：
   a. 按 包结构路径 从 zip 中读取对应的 .class 字节码
   b. 通过 MemoryClassLoader 直接定义类（defineClass）
   c. 检查 @MigrationAnnotation 注解与 Migration 接口
   d. 符合条件的类存入 loadedClasses
```

**JAR 模式（`loadFromJar`）**：

```
1. 创建 URLClassLoader 加载 jar
2. 遍历 jar 中所有 .class 条目（跳过 META-INF 与内部类）
3. 通过 URLClassLoader 加载类
4. 检查 @MigrationAnnotation 注解与 Migration 接口
5. 符合条件的类存入 loadedClasses
```

**CLASSPATH 模式（`loadFromClasspath`）**：

```
1. 读取 java.class.path，按路径分隔符拆分
2. 对每个 classpath 条目：
   a. 目录：递归扫描 .class 文件
   b. JAR 文件：扫描 jar 内 .class 条目
3. 加载类并检查 @MigrationAnnotation 注解与 Migration 接口
4. 符合条件的类存入 loadedClasses
```

### 使用示例

```java
MigrationScanner scanner = new MigrationScanner();
try {
    // DIRECTORY 模式
    scanner.compileFromDirectory("migrations");

    // 或 DIRECTORY_CLASSES 模式（仅需 JRE）
    // scanner.loadFromDirectoryClasses("precompiled/migrations");

    // 或 PACKAGED 模式（仅需 JRE）
    // scanner.loadFromZip("precompiled/migrations.jmigration.zip");

    // 或 JAR 模式
    // scanner.loadFromJar(new File("migrations.jar"));

    // 或 CLASSPATH 模式
    // scanner.loadFromClasspath();

    // 获取所有迁移类
    List<String> classNames = scanner.getAllMigrationClassNames();
    for (String name : classNames) {
        Class<?> clazz = scanner.getCompiledClass(name);
        Migration migration = (Migration) clazz.getDeclaredConstructor().newInstance();
        // ...
    }
} finally {
    scanner.finish();  // 释放资源
}
```

---

## 9. MigrationGenerator —— 迁移类源码生成器

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
import com.weacsoft.jaravel.vendor.migration.MigrationAnnotation;

@MigrationAnnotation
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

## 10. Schema —— 表结构构建器

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
| PostgreSQL / Oracle | `ALTER TABLE "old" RENAME TO "new"` |

**修改字段**：

| 数据库 | SQL |
| --- | --- |
| MySQL | `ALTER TABLE \`t\` MODIFY \`col\` def` |
| H2 | `ALTER TABLE \`t\` ALTER COLUMN \`col\` def` |
| SQL Server | `ALTER TABLE [t] ALTER COLUMN [col] type [NULL\|NOT NULL]`（仅类型+可空性） |
| PostgreSQL | `ALTER TABLE "t" ALTER COLUMN "col" TYPE type` |
| Oracle | `ALTER TABLE "t" MODIFY ("col" type [NULL\|NOT NULL] [DEFAULT ...])` |
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

## 11. Blueprint —— 表结构蓝图

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
| `boolean isPostgresql()` | 是否为 PostgreSQL 方言 |
| `boolean isOracle()` | 是否为 Oracle 方言 |
| `String quote(String identifier)` | 按方言对标识符加引号（MySQL/SQLite/H2 用反引号，SQL Server 用方括号，PostgreSQL/Oracle 用双引号） |

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

## 12. ColumnDefinition —— 列定义

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

| 逻辑类型 | MySQL / SQLite / H2 | SQL Server | PostgreSQL | Oracle |
| --- | --- | --- | --- | --- |
| string | `VARCHAR(n)` | `VARCHAR(n)` | `VARCHAR(n)` | `VARCHAR2(n)` |
| integer | `INT` / `INT UNSIGNED` | `INT` | `INT` | `NUMBER(10)` |
| bigInteger | `BIGINT` / `BIGINT UNSIGNED` | `BIGINT` | `BIGINT` | `NUMBER(19)` |
| text | `TEXT` | `VARCHAR(MAX)` | `TEXT` | `CLOB` |
| boolean | `TINYINT(1)` | `BIT` | `BOOLEAN` | `NUMBER(1)` |
| decimal | `DECIMAL(p,s)` | `DECIMAL(p,s)` | `DECIMAL(p,s)` | `NUMBER(p,s)` |
| dateTime | `DATETIME` | `DATETIME2` | `TIMESTAMP` | `TIMESTAMP` |
| timestamp | `TIMESTAMP` | `DATETIME2` | `TIMESTAMP` | `TIMESTAMP` |
| year | `YEAR` | `SMALLINT` | `SMALLINT` | `NUMBER(4)` |
| json | `JSON` | `NVARCHAR(MAX)` | `JSONB` | `CLOB` |
| binary | `LONGBLOB` | `VARBINARY(MAX)` | `BYTEA` | `BLOB` |

### 自增语法（按方言）

| 数据库 | 自增主键语法 |
| --- | --- |
| MySQL / H2 | `AUTO_INCREMENT` |
| SQLite | `INTEGER PRIMARY KEY AUTOINCREMENT` |
| SQL Server | `IDENTITY(1,1) PRIMARY KEY` |
| PostgreSQL | `BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY` |
| Oracle | `NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY` |

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

## 13. ForeignKeyDefinition —— 外键定义

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

## 14. Migrator —— 迁移引擎

`com.weacsoft.jaravel.vendor.migration.Migrator`

对齐 Laravel `Illuminate\Database\Migrations\Migrator`。通过 `MigrationScanner` 在运行时加载迁移类（DIRECTORY 编译 / DIRECTORY_CLASSES 加载 / PACKAGED 加载 / JAR 加载 / CLASSPATH 扫描）、反射实例化，按名称排序，与 `MigrationRepository` 记录比对，执行待运行的迁移或回滚。所有操作完成后调用 `finish()` 释放资源。

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `List<String> run()` | 执行所有待运行迁移，返回已执行的迁移名称列表 |
| `List<String> rollback(int steps)` | 回滚指定步数（批次），返回已回滚的迁移名称列表 |
| `List<String> reset()` | 回滚所有迁移（倒序） |
| `List<String> refresh()` | 回滚所有并重新迁移（reset + run） |
| `void status()` | 输出迁移状态（[Y]/[N] 标记是否已执行） |
| `List<String> pending()` | 获取待运行迁移名称列表 |
| `void finish()` | 释放资源：调用 `MigrationScanner.finish()` 清除编译产物与类加载器 |

### 执行流程

**run()（正向迁移）**：

```
1. createRepository()  -- 确保 migrations 表存在
2. getRan()            -- 查询已执行迁移列表
3. getNextBatchNumber()-- 获取下一批次号
4. 遍历 sortedMigrations()（按名称字典序）：
   - 若该迁移不在 ran 列表中：
     a. 调用 migration.up(schema)
     b. repository.log(name, batch)  -- 记录到 migrations 表
5. 返回已执行列表
```

**rollback(steps)（回滚）**：

```
1. createRepository()
2. getLast()  -- 获取最后一批执行的迁移（按 id 倒序）
3. 遍历最后一批迁移（最多 steps 个）：
   a. 调用 migration.down(schema)
   b. repository.delete(name)  -- 从 migrations 表删除
4. 返回已回滚列表
```

### 使用示例

```java
// MigrationRunner 在运行时创建 MigrationScanner、Migrator，执行后自动释放
// 也可手动使用：
MigrationScanner scanner = new MigrationScanner();
try {
    scanner.compileFromDirectory("migrations");
    Schema schema = new Schema(dataSource);
    MigrationRepository repository = new MigrationRepository(dataSource, "migrations");
    Migrator migrator = new Migrator(repository, schema, scanner);

    // 执行迁移
    List<String> executed = migrator.run();

    // 回滚最近 1 批
    List<String> rolledBack = migrator.rollback(1);

    // 查看状态
    migrator.status();
} finally {
    scanner.finish();  // 释放资源
}
```

---

## 15. MigrationRepository —— 迁移记录仓库

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
| PostgreSQL | `SERIAL PRIMARY KEY`（`CREATE TABLE IF NOT EXISTS`） |
| Oracle | `NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY`（先查 `user_tables` 判断是否存在） |

---

## 16. MigrationRunner —— 命令行运行器

`com.weacsoft.jaravel.vendor.migration.MigrationRunner`

对齐 Laravel artisan 的 migrate 系列命令。实现 `CommandLineRunner`，通过启动参数触发迁移命令。

> **重要变更**：不再通过 Spring DI 注入 `List<Migration>`，而是在运行时创建 `MigrationScanner`，根据 `jaravel.migration.source` 配置选择加载方式（DIRECTORY 编译 / DIRECTORY_CLASSES 加载 / PACKAGED 加载 / JAR 加载 / CLASSPATH 扫描），通过 `Migrator` 执行迁移，完成后调用 `scanner.finish()` 释放资源。

### 执行流程

```
1. 从 MigrationProperties 获取迁移源类型与相关配置
2. 创建 MigrationScanner，根据 source 选择加载方式：
   - DIRECTORY：编译目录下所有 .java 文件（需要 JDK）
   - DIRECTORY_CLASSES：从目录加载预编译 .class 文件（只需要 JRE）
   - PACKAGED：从预编译 zip 包加载迁移类（只需要 JRE）
   - JAR：从 jar 文件加载预编译迁移类（只需要 JRE）
   - CLASSPATH：从 classpath 扫描迁移类（只需要 JRE）
3. 创建 Schema、MigrationRepository、Migrator
4. 执行命令（migrate/rollback/reset/refresh/status）
5. scanner.finish() 释放资源（finally 块）
```

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

## 21. Dialect —— 数据库方言接口

`com.weacsoft.jaravel.vendor.migration.Dialect`

数据库方言策略接口，封装各数据库之间的 SQL DDL 差异。采用策略模式，所有方言相关逻辑（标识符引用、类型映射、自增语法、系统目录查询、ALTER TABLE 语法等）集中在此接口的实现类中。

### 方言实现类

| 实现类 | 数据库 | 标识符引用 | 自增语法 |
| --- | --- | --- | --- |
| `MysqlDialect` | MySQL | 反引号 `` ` `` | `AUTO_INCREMENT` |
| `SqliteDialect` | SQLite | 反引号 `` ` `` | `AUTOINCREMENT` |
| `H2Dialect` | H2 | 反引号 `` ` `` | `AUTO_INCREMENT` |
| `SqlServerDialect` | SQL Server | 方括号 `[]` | `IDENTITY(1,1)` |
| `PostgresqlDialect` | PostgreSQL | 双引号 `""` | `GENERATED ALWAYS AS IDENTITY` |
| `OracleDialect` | Oracle | 双引号 `""` | `GENERATED ALWAYS AS IDENTITY` |

### DialectFactory —— 方言工厂

`DialectFactory` 根据数据源自动检测数据库产品名并返回对应的 `Dialect` 实现：

```java
// 自动检测
Dialect dialect = DialectFactory.detect(dataSource);

// 按名称创建
Dialect dialect = DialectFactory.create("postgresql");
```

未识别的数据库默认使用 MySQL 方言。

### 核心方法

| 方法签名 | 说明 |
| --- | --- |
| `String quote(String identifier)` | 按方言对标识符加引号 |
| `String tableOptions()` | 返回 CREATE TABLE 附加选项（如 MySQL 的 ENGINE=InnoDB） |
| `String renameTableSql(String from, String to)` | 生成重命名表 SQL |
| `String hasTableSql()` | 返回判断表是否存在的 SQL 模板 |
| `String hasColumnSql()` | 返回判断列是否存在的 SQL 模板 |
| `String mapType(String, Integer, Integer, Integer, boolean)` | 将逻辑类型映射为数据库特定类型 |
| `String autoIncrementPrimaryKeyTypeClause(String)` | 返回自增主键类型子句（特殊格式返回，标准格式返回 null） |
| `String autoIncrementClause()` | 返回自增关键字 |
| `String modifyColumnSql(String, ColumnDefinition)` | 生成修改字段 ALTER TABLE SQL |
| `String createRepositoryTableSql(String)` | 生成创建迁移记录表 SQL |
| `String dropIndexSql(String, String)` | 生成删除索引 SQL |
| `boolean supportsColumnComment()` | 是否支持列注释 |
| `boolean supportsAfterColumn()` | 是否支持 AFTER 子句 |

### 扩展新数据库

新增数据库支持只需添加一个 `Dialect` 实现类，并在 `DialectFactory.create()` 中添加检测分支：

```java
public class DmDialect extends AbstractDialect {
    public DmDialect() { super("dm"); }
    // 实现所有抽象方法...
}

// DialectFactory.create() 中添加：
if (lower.contains("dm")) return new DmDialect();
```

无需修改 `Schema`、`Blueprint`、`ColumnDefinition`、`MigrationRepository` 的任何代码。

---

## 22. TableMigrator —— 跨库表迁移工具

`com.weacsoft.jaravel.vendor.migration.TableMigrator`

跨数据库表迁移工具，支持将表结构和数据从源数据库迁移到目标数据库。不依赖 migration 文件，通过 JDBC `DatabaseMetaData` 直接读取数据库中实际存在的表结构。

### 两种迁移模式

#### 1 步迁移（直连）

源库和目标库同时在线，通过两个独立的 JDBC 连接直接复制：

```java
TableMigrator migrator = new TableMigrator(sourceDs, targetDs);
migrator.migrateAll();                     // 迁移所有表（含 migrations 记录表）
migrator.migrateAll(false);                // 迁移所有用户表（不含 migrations 记录表）
migrator.migrateTables("users", "orders"); // 迁移指定表
migrator.migrateTable("users");            // 迁移单表
```

#### 2 步迁移（导出 → 导入）

两步分离，可在不同时间、不同实例调用：

```java
// 第一步：导出（只需源数据源）
TableMigrator exporter = new TableMigrator(sourceDs, null);
exporter.exportAll(new File("dump.jvd"));                       // 导出所有表
exporter.exportAll(new File("dump.jvd"), false);                // 不含 migrations 表
exporter.exportTables(new File("dump.jvd"), "users", "orders"); // 导出指定表

// 第二步：导入（只需目标数据源，可在不同时间调用）
TableMigrator importer = new TableMigrator(null, targetDs);
importer.importAll(new File("dump.jvd"));                       // 导入所有表
importer.importTables(new File("dump.jvd"), "users", "tags");   // 选择性导入
```

### 方法文档

| 方法签名 | 模式 | 说明 |
| --- | --- | --- |
| `int migrateAll()` | 1步 | 迁移所有表（含 migrations） |
| `int migrateAll(boolean)` | 1步 | 迁移所有表，可控制是否含 migrations |
| `int migrateTables(String...)` | 1步 | 迁移指定表 |
| `void migrateTable(String)` | 1步 | 迁移单表 |
| `int exportAll(File)` | 2步导出 | 导出所有表到 .jvd 文件 |
| `int exportAll(File, boolean)` | 2步导出 | 导出所有表，可控制是否含 migrations |
| `int exportTables(File, String...)` | 2步导出 | 导出指定表到 .jvd 文件 |
| `int importAll(File)` | 2步导入 | 从 .jvd 文件导入所有表 |
| `int importTables(File, String...)` | 2步导入 | 从 .jvd 文件导入指定表 |

### .jvd 二进制文件格式

自定义私有格式（魔数 `JAVD` + 版本号），包含表结构和数据：

```
Header: magic(4) + version(4) + tableCount(4)
Per table: tableName + columnDefs + primaryKey + rowCount + rowData
```

### 数据类型支持

以 SQLite 兼容基本类型为标准，额外支持：

| 类别 | 支持类型 |
| --- | --- |
| 基本 | NULL、String/Text、Integer、Long、Boolean |
| 浮点 | Float、Double、BigDecimal（字符串中转） |
| 日期 | Date、Time、Timestamp（ISO 字符串存储） |
| 二进制 | byte[]（BLOB，额外支持） |

不支持 JSON、自定义对象等高级类型。未知类型自动回退为字符串。

### 兼容性

| 数据库 | 源库读取 | 目标库写入 |
| --- | --- | --- |
| MySQL | 完全兼容 | 完全兼容 |
| H2 | 完全兼容 | 完全兼容 |
| SQLite | 完全兼容 | 完全兼容 |
| PostgreSQL | 完全兼容 | 完全兼容 |
| Oracle | 完全兼容 | 完全兼容 |
| SQL Server | 完全兼容 | 完全兼容 |

源库读取基于标准 JDBC `DatabaseMetaData`，任何提供 JDBC 驱动的数据库都能作为源库。目标库写入依赖 `Dialect` 实现做类型映射，需已支持的 6 种之一。

### 连接管理

1 步迁移使用两个独立的 JDBC 连接（源、目标分别从各自 DataSource 获取），不依赖跨数据库链接或 dblink。2 步迁移各阶段只需一个数据源。

---

## 23. MigrationAutoConfiguration —— 自动装配

`com.weacsoft.jaravel.vendor.migration.MigrationAutoConfiguration`

Spring Boot 自动装配类。当容器中存在 `DataSource` 且 `jaravel.migration.enabled=true`（默认）时，注册 `MigrationRunner` Bean。

> **重要变更**：不再注入 `List<Migration>`，也不再注册 `Schema`、`MigrationRepository`、`Migrator` 为 Bean。迁移文件不再是 Spring 组件，而是通过 `MigrationScanner` 在运行时加载（DIRECTORY 编译 / DIRECTORY_CLASSES 加载 / PACKAGED 加载 / JAR 加载 / CLASSPATH 扫描）、反射实例化、执行后自动释放。本类仅注册 `MigrationRunner` 一个 Bean，注入 `DataSource` 与 `MigrationProperties`。

| Bean | 类型 | 说明 |
| --- | --- | --- |
| `jaravelMigrationRunner` | `MigrationRunner` | 命令行运行器（`@Order(HIGHEST_PRECEDENCE)`），运行时创建 `MigrationScanner` 等组件 |

Bean 带 `@ConditionalOnMissingBean`，允许业务方自定义替换。通过 `@AutoConfigureAfter(DataSourceAutoConfiguration.class)` 确保数据源已就绪。

---

## 24. 配置选项

配置前缀为 `jaravel.migration`，对应 `MigrationProperties` 类。

```yaml
jaravel:
  migration:
    source: DIRECTORY        # 迁移源类型（DIRECTORY / DIRECTORY_CLASSES / PACKAGED / JAR / CLASSPATH）
    directory: migrations   # 迁移 .java 文件目录（DIRECTORY 模式）
    classes-dir: ""         # 预编译 .class 文件目录（DIRECTORY_CLASSES 模式）
    package-path: ""        # 预编译打包文件路径（PACKAGED 模式，如 migrations.jmigration.zip）
    jar-path: ""            # JAR 文件路径（JAR 模式）
    package-in-jar: false   # 构建时是否将迁移目录打包进 jar（CLASSPATH 模式）
    enabled: true           # 是否启用迁移模块
    table: migrations       # 迁移记录表名
    auto-run: false         # 启动时是否自动执行 migrate
```

| 属性 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `jaravel.migration.source` | `MigrationSource` | `DIRECTORY` | 迁移源类型，可选 `DIRECTORY` / `DIRECTORY_CLASSES` / `PACKAGED` / `JAR` / `CLASSPATH` |
| `jaravel.migration.directory` | `String` | `migrations` | 迁移 `.java` 文件目录（DIRECTORY 模式），由 `MigrationScanner` 扫描并内存编译 |
| `jaravel.migration.classes-dir` | `String` | `""` | 预编译 `.class` 文件目录（DIRECTORY_CLASSES 模式），由 `MigrationScanner` 加载预编译类 |
| `jaravel.migration.package-path` | `String` | `""` | 预编译打包文件路径（PACKAGED 模式，如 `migrations.jmigration.zip`），由 `MigrationScanner` 从 zip 包加载迁移类 |
| `jaravel.migration.jar-path` | `String` | `""` | JAR 文件路径，JAR 模式下加载预编译迁移类的 jar |
| `jaravel.migration.package-in-jar` | `boolean` | `false` | 构建时是否将迁移目录打包进 jar，用于 CLASSPATH 模式 |
| `jaravel.migration.enabled` | `boolean` | `true` | 是否启用迁移模块 |
| `jaravel.migration.table` | `String` | `migrations` | 迁移记录表名 |
| `jaravel.migration.auto-run` | `boolean` | `false` | 启动时是否自动执行 migrate |

### 各模式配置示例

**DIRECTORY 模式**：

```yaml
jaravel:
  migration:
    source: DIRECTORY
    directory: migrations
```

**DIRECTORY_CLASSES 模式**：

```yaml
jaravel:
  migration:
    source: DIRECTORY_CLASSES
    classes-dir: precompiled/migrations
```

**PACKAGED 模式**：

```yaml
jaravel:
  migration:
    source: PACKAGED
    package-path: precompiled/migrations.jmigration.zip
```

**JAR 模式**：

```yaml
jaravel:
  migration:
    source: JAR
    jar-path: /path/to/migrations.jar
```

**CLASSPATH 模式**：

```yaml
jaravel:
  migration:
    source: CLASSPATH
```

---

## 25. 预编译 —— 开发阶段预编译工具

`migration` 模块提供预编译工具，在开发阶段（有 JDK）将迁移 `.java` 文件预编译为字节码，生成 zip 包或散乱 class 文件。预编译产物部署到生产环境（仅需 JRE）后，通过 `PACKAGED` 或 `DIRECTORY_CLASSES` 模式加载，避免生产环境依赖 JDK。

### 预编译流程

```
开发阶段（有 JDK）                         生产阶段（仅需 JRE）
┌──────────────────────┐                ┌──────────────────────┐
│  migrations/*.java    │                │                      │
│         │             │                │  MigrationScanner     │
│         ▼             │                │  ├─ loadFromZip()     │── PACKAGED 模式
│  MigrationPrecompiler │ ── 预编译 ──▶  │  ├─ loadFromDirectory │── DIRECTORY_CLASSES 模式
│         │             │                │  │  Classes()         │
│         ▼             │                │  └─ ...               │
│  .jmigration.zip      │                │                      │
│  或散乱 .class 文件   │                │  Migrator 执行迁移    │
└──────────────────────┘                └──────────────────────┘
```

### MigrationPrecompiler —— 预编译工具类

`com.weacsoft.jaravel.vendor.migration.engine.MigrationPrecompiler`

预编译工具类，在开发阶段（有 JDK）将迁移 `.java` 文件预编译为字节码。支持两种输出模式：打包为单个 zip 文件（PACKAGED）或散乱 class 文件到目录（CLASSES）。

#### 内部枚举

```java
public enum CompileMode {
    PACKAGED,  // 打包为单个 zip 文件
    CLASSES    // 散乱 class 文件到目录
}
```

#### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `MigrationPrecompiler(String sourceDir)` | 构造方法，创建预编译器，指定迁移 `.java` 文件目录 |
| `int compileAll(String outputDir, CompileMode mode, String fileName)` | 预编译所有迁移文件，返回编译成功的文件数。`outputDir` 为输出目录，`mode` 为 `PACKAGED` 或 `CLASSES`，`fileName` 为打包文件名（仅 `PACKAGED` 模式，默认 `migrations.jmigration.zip`） |
| `int compileAllToZip(String outputDir, String fileName)` | 便利方法：打包为 zip（等同 `compileAll(outputDir, PACKAGED, fileName)`） |
| `int compileAllToClasses(String outputDir)` | 便利方法：输出散乱 class（等同 `compileAll(outputDir, CLASSES, null)`） |

#### zip 包格式

`PACKAGED` 模式生成的 zip 包结构：

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

#### 使用示例

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

### MigrationPrecompilerMain —— 预编译命令行工具

`com.weacsoft.jaravel.vendor.migration.engine.MigrationPrecompilerMain`

预编译命令行工具，在开发阶段（有 JDK）通过命令行将迁移 `.java` 文件预编译为字节码。支持打包为 zip（`packaged`）或散乱 class（`classes`）两种输出模式。

#### CLI 参数

| 参数 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `--source-dir=<path>` | 是 | - | 迁移 `.java` 文件源目录 |
| `--output-dir=<path>` | 是 | - | 输出目录 |
| `--mode=<mode>` | 否 | `packaged` | 预编译模式：`packaged`（打包为 zip）或 `classes`（散乱 class） |
| `--file-name=<name>` | 否 | `migrations.jmigration.zip` | 打包文件名（仅 `packaged` 模式） |

#### 使用示例

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

#### 完整预编译部署流程

```bash
# 1. 开发阶段：预编译迁移文件（需 JDK）
java -cp migration.jar:utils.jar \
  com.weacsoft.jaravel.vendor.migration.engine.MigrationPrecompilerMain \
  --source-dir=migrations \
  --output-dir=precompiled \
  --mode=packaged \
  --file-name=migrations.jmigration.zip

# 2. 部署 precompiled/migrations.jmigration.zip 到生产环境

# 3. 生产环境配置（仅需 JRE）
# application.yml:
# jaravel:
#   migration:
#     source: PACKAGED
#     package-path: precompiled/migrations.jmigration.zip

# 4. 或通过 MigrationCLI 执行（仅需 JRE）
java -cp migration.jar:utils.jar:mysql-connector.jar \
  com.weacsoft.jaravel.vendor.migration.MigrationCLI \
  --db-url=jdbc:mysql://localhost:3306/mydb \
  --db-user=root \
  --db-password=secret \
  --source=PACKAGED \
  --package-path=precompiled/migrations.jmigration.zip \
  migrate
```

---

## 26. 线程安全说明

| 类 | 线程安全性 | 说明 |
| --- | --- | --- |
| `MigrationSource` | 线程安全 | 枚举类型，天然不可变 |
| `Schema` | 单线程使用 | 内部 `JdbcTemplate` 线程安全，但 `databaseProductName` 在构造时一次性确定。`Schema` 实例本身无共享可变状态，可在多线程中使用，但迁移操作通常串行执行 |
| `Blueprint` | 非线程安全 | 使用 `ArrayList` 存储列与命令，应在单线程内构建并消费。每个 `create()` / `table()` 调用创建独立的 `Blueprint` 实例 |
| `ColumnDefinition` | 非线程安全 | 链式调用修改内部字段，应在单线程内使用。由 `Blueprint` 在单线程内创建 |
| `ForeignKeyDefinition` | 非线程安全 | 同 `ColumnDefinition`，链式调用构建 |
| `Migrator` | 需外部同步 | `run()` / `rollback()` 等方法操作 `MigrationRepository`，并发调用可能导致批次号冲突。应在单线程或加锁环境下调用 |
| `MigrationRepository` | 需外部同步 | 基于 `JdbcTemplate`，SQL 操作本身原子，但 `getNextBatchNumber()` 与 `log()` 之间存在竞态，并发迁移需外部同步 |
| `MigrationRunner` | 单次执行 | `CommandLineRunner.run()` 在启动时单线程调用，无需考虑并发 |
| `MigrationScanner` | 单次使用 | 内部 `MemoryClassLoader` 懒加载，`finish()` 后释放。设计为单次加载-执行-释放生命周期，非线程安全 |
| `MigrationPrecompiler` | 单次使用 | 预编译工具类，设计为单次编译生命周期。内部使用 `MigrationScanner` 编译并获取字节码，非线程安全 |
| `MigrationPrecompilerMain` | 单次执行 | 命令行入口，`main()` 方法单线程调用，无需考虑并发 |
| `MigrationGenerator` | 线程安全 | 静态方法，无共享可变状态，可安全并发调用（但同一文件路径并发写入会失败） |
| `Dialect` / `AbstractDialect` | 线程安全 | 无状态策略对象，各方法无副作用 |
| `DialectFactory` | 线程安全 | 静态工厂方法，每次返回新的 Dialect 实例 |
| `MysqlDialect` 等 | 线程安全 | 不可变对象，构造后状态不变 |
| `TableMigrator` | 单次使用 | 设计为单次迁移生命周期，内部不保持跨方法可变状态，但并发操作同一目标表需外部同步 |
