# cache-database

缓存的 **database 驱动**独立模块：`DatabaseCacheDriver`（数据库缓存驱动，原生 JDBC）+ `DatabaseCacheDriverFactory`（工厂）+ `CacheTableCommand`（`cache:table` artisan 命令）。

设计原则（对齐 `queue-database` 的拆分惯例）：

- **走 database 模块**：数据源解析直接调用 `database` 模块的 `ConnectionManager` 连接注册表
  （`@RegisterConnection` 声明的连接 / `defaultRawDataSource()`），SQL 用**原生 JDBC** 执行——
  **不依赖 spring-jdbc / JdbcTemplate**；
- **零 Spring 依赖**：本模块没有任何 Spring 注解或 Bean，可以在纯 JVM 环境直接使用
  （`new DatabaseCacheDriver(ConnectionManager.defaultRawDataSource())`）；
- **Spring 装配在 `springboot` 模块**：`vendor.springboot.cache.CacheAutoConfiguration` 的
  `DatabaseCacheConfiguration` 内部类负责「配置里声明了 `driver: database` 才装配工厂」的条件装配，
  并注入「先 `ConnectionManager` 注册表、再 Spring 容器 `DataSource` Bean」的回退解析器。

## 依赖

| 依赖 | 用途 |
| --- | --- |
| `cache` | `CacheDriver` / `CacheDriverFactory` 契约（核心，零 Spring） |
| `io.github.lijialong1313:database` | `ConnectionManager` 连接注册表（别名连接解析 / 默认连接） |
| `org.slf4j:slf4j-api` | 日志 |
| `com.fasterxml.jackson.core:jackson-databind` | 缓存值 JSON 序列化（经 cache 传递） |
| `artisan`（**optional**） | `CacheTableCommand` 的基类 `ArtisanCommand` |
| `migration`（**optional**） | `CacheTableCommand` 生成迁移文件 |

## Maven 坐标

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>cache-database</artifactId>
    <version>0.1.2</version>
</dependency>
```

> 引入本模块即传递依赖 `database` 模块。未使用 `driver: database` 的 spring 应用可以完全不引入本模块（`array` / `file` / `redis` 驱动不受影响）。

## 核心类

```
com.weacsoft.jaravel.vendor.cache.database
├── DatabaseCacheDriver           // 数据库缓存驱动（原生 JDBC，方言适配，不自动建表）
├── DatabaseCacheDriverFactory    // database 驱动工厂（Supplier<DataSource> 惰性解析；别名走 ConnectionManager）
└── artisan/
    └── CacheTableCommand         // cache:table 命令（生成建表迁移文件，不直接建表）
```

### DatabaseCacheDriver

| 项 | 说明 |
| --- | --- |
| 构造 | `DatabaseCacheDriver(DataSource)` / `DatabaseCacheDriver(DataSource, String table)` |
| `createTable()` | 创建 `jaravel_cache` 表（`CREATE TABLE IF NOT EXISTS`；SQL Server 走 `sys.tables` 预检），自动适配 MySQL / PostgreSQL / SQLite / H2 / SQL Server 方言 |
| `put/get/exists/remove/removeAll/allKeys` | 标准缓存操作；TTL 单位**秒**（`<= 0` 永不过期）；命中过期记录时返回未命中并**异步删除** |
| `put` 语义 | 各方言 upsert（MySQL `ON DUPLICATE KEY UPDATE` / PG 与 SQLite `ON CONFLICT` / H2 `MERGE ... KEY` / SQL Server `MERGE ... USING`） |

> **重要**：使用 database 缓存驱动前，必须先执行 `artisan cache:table` 或调用 `createTable()` 建表。

### DatabaseCacheDriverFactory

| 构造 | 说明 |
| --- | --- |
| `DatabaseCacheDriverFactory(DataSource)` | 固定数据源（测试 / 显式指定） |
| `DatabaseCacheDriverFactory(Supplier<DataSource>)` | 惰性解析（springboot 自动装配注入「注册表 + 容器回退」解析器） |
| `static fromConnectionManager()` | 标准纯 jaravel 用法：默认走 `ConnectionManager.defaultRawDataSource()` |

- `support("database")` 为 true；
- `create(config)` 读取 `table` / `connection` 配置：`connection` 别名经 `ConnectionManager.rawDataSource(alias)` 解析；
- 最终解析不到数据源时抛出带操作建议的 `IllegalStateException`（提示 `@RegisterConnection` 或改用 `array` / `file` 驱动），而不是在启动期莫名失败。

### CacheTableCommand（artisan）

```bash
java -jar app.jar artisan cache:table
```

生成建表迁移文件到 `database/migrations/`（不直接建表），执行 `artisan migrate` 生效。
命令类本身零 Spring；`springboot` 模块的 `CacheArtisanAutoConfiguration` 在引入 `artisan` 模块且本模块在 classpath 时按 `@RegisterCommand` 扫描注册，且整体受「声明了 `driver: database` 才装配」的条件约束。

## 配置

与 cache 模块共用 `jaravel.cache.*` 前缀（`CacheProperties` 位于 `springboot` 模块）：

```yaml
jaravel:
  cache:
    default-store: database
    prefix: myapp
    stores:
      database:
        driver: database
        table: app_cache        # 可选，默认 jaravel_cache
        connection: primary     # 可选，@RegisterConnection 别名
```

## 测试

`DatabaseCacheDriverTest`（H2 内存库）覆盖：建表、put/get 往返、upsert 覆盖、零 TTL 永不过期、
TTL 过期未命中、exists/remove/removeAll/allKeys、自定义表名、工厂 support/table 配置 / 默认表名 /
缺失数据源报错。**全程无 spring-jdbc、无网络**。
