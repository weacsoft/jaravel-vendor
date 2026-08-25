# storage-database

文件存储的 **database 驱动**独立模块：`DatabaseFilesystem`（数据库文件存储，原生 JDBC）+ `DatabaseFilesystemDriver`（驱动工厂）+ `StorageTableCommand`（`storage:table` artisan 命令）。

设计原则（对齐 `cache-database` / `queue-database` 的拆分惯例）：

- **走 database 模块**：数据源解析直接调用 `database` 模块的 `ConnectionManager` 连接注册表
  （`@RegisterConnection` 声明的连接 / `defaultRawDataSource()`），SQL 用**原生 JDBC** 执行——
  **不依赖 spring-jdbc / JdbcTemplate**；
- **零 Spring 依赖**：本模块没有任何 Spring 注解或 Bean，可以在纯 JVM 环境直接使用
  （`new DatabaseFilesystem(ConnectionManager.defaultRawDataSource())`）；
- **Spring 装配在 `springboot` 模块**：`vendor.springboot.storage.StorageAutoConfiguration` 的
  `DatabaseStorageConfiguration` 内部类负责「配置里声明了 `driver: database` 的磁盘才装配驱动」的条件装配，
  并注入「先 `ConnectionManager` 默认连接、再 Spring 容器 `DataSource` Bean」的回退解析器。

## 依赖

| 依赖 | 用途 |
| --- | --- |
| `storage` | `Filesystem` / `FilesystemDriver` 契约 + `StorageManager`（核心，零 Spring 依赖） |
| `io.github.lijialong1313:database` | `ConnectionManager` 连接注册表（别名连接解析 / 默认连接） |
| `org.slf4j:slf4j-api` | 日志 |
| `artisan`（**optional**） | `StorageTableCommand` 的基类 `ArtisanCommand` |
| `migration`（**optional**） | `StorageTableCommand` 生成迁移文件 |

## Maven 坐标

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>storage-database</artifactId>
    <version>0.1.2</version>
</dependency>
```

> 引入本模块即传递依赖 `database` 模块。未使用 `driver: database` 磁盘的 spring 应用可以完全不引入本模块
> （`local` / `public` 磁盘不受影响）。

## 核心类

```
com.weacsoft.jaravel.vendor.storage.database
├── DatabaseFilesystem          // 数据库文件存储（原生 JDBC，分片组装，不自动建表）
├── DatabaseFilesystemDriver    // database 磁盘驱动工厂（Supplier<DataSource> 惰性解析；别名走 ConnectionManager）
└── artisan/
    └── StorageTableCommand     // storage:table 命令（生成建表迁移文件，不直接建表）
```

### DatabaseFilesystem

| 项 | 说明 |
| --- | --- |
| 构造 | `DatabaseFilesystem(name, DataSource)` / `DatabaseFilesystem(name, DataSource, binary, contentColumn, chunkSize, tablePrefix, defaultVisibility)` |
| 表结构 | `<prefix>file`（元信息）+ `<prefix>file_chunk`（内容分片），**不自动建表** |
| `put/readStream/writeTo/append/copy/move` | 标准文件操作；大文件按 `chunkSize` 分片写入、按 `chunk_index` 顺序拼接还原 |
| 二进制/文本 | `binary=true` 内容直接写 BLOB 列；`binary=false` base64 后写 LONGTEXT 列（单列方案，列名由 `content-column` 决定） |
| 目录语义 | 目录是路径前缀，无真实目录：`makeDirectory` 无操作，列举由文件路径推导 |
| `url()/path()` | 数据库磁盘不支持，抛 `StorageException`（请通过接口提供下载） |

> **重要**：使用 database 磁盘驱动前，必须先执行 `artisan storage:table` 生成迁移并 `artisan migrate`（或手动建表）。

### DatabaseFilesystemDriver

| 构造 | 说明 |
| --- | --- |
| `DatabaseFilesystemDriver(DataSource)` | 固定数据源（测试 / 显式指定） |
| `DatabaseFilesystemDriver(Supplier<DataSource>)` | 惰性解析（springboot 自动装配注入「注册表 + 容器回退」解析器） |
| `static fromConnectionManager()` | 标准纯 jaravel 用法：默认走 `ConnectionManager.defaultRawDataSource()` |

- `support("database")` 为 true；
- `create(name, config)` 读取 `binary` / `content-column` / `chunk-size` / `table-prefix` / `visibility`，
  以及可选 `connection`（旧键 `datasource` 兼容）：别名经 `ConnectionManager.rawDataSource(alias)` 解析；
- 最终解析不到数据源时抛出带操作建议的 `StorageException`（提示 `@RegisterConnection` 或改用 `local` 驱动），
  而不是在启动期莫名失败。

### StorageTableCommand（artisan）

```bash
java -jar app.jar artisan storage:table
```

生成建表迁移文件到 `database/migrations/`（不直接建表），执行 `artisan migrate` 生效。
命令类本身零 Spring；`springboot` 模块的 `StorageArtisanAutoConfiguration` 在引入 `artisan` 模块且本模块在
classpath 时按 `@RegisterCommand` 扫描注册。

## 配置

与 storage 模块共用 `jaravel.storage.*` 前缀（`StorageProperties` 位于 `springboot` 模块）：

```yaml
jaravel:
  storage:
    default-disk: files
    disks:
      files:
        driver: database
        binary: true            # true=BLOB；false=LONGTEXT(base64)
        content-column: content # 内容列名（默认 content）
        chunk-size: 1048576     # 分片上限（默认 1MB）
        table-prefix: storage_  # 表前缀（默认 storage_）
        connection: primary     # 可选，@RegisterConnection 别名；省略=默认连接
```

## 测试

`DatabaseFilesystemTest`（H2 内存库）覆盖：自定义列名 + 二进制/base64 双模式写读、默认列名、
工厂 `support` / 配置解析 / 默认配置、缺失数据源或别名时的可操作报错、`fromConnectionManager()` 纯入口。
**全程无 spring-jdbc、无网络**。
