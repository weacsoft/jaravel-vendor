# Storage — 多磁盘文件存储模块

> Jaravel-Vendor 的文件存储核心模块（**零 Spring 依赖**），对齐 Laravel `Storage` Facade 与 `config/filesystems.php`，提供统一的文件系统抽象。
> 驱动按职责拆分到可选模块：database 磁盘驱动见 **`storage-database`**（走 `database` 模块连接，原生 JDBC，不用 spring-jdbc）。
> Spring 自动装配（`StorageAutoConfiguration` / `StorageProperties` / `StorageRegistrar` / 条件装配 / artisan 集成 / vendor:publish）统一位于 **`springboot`** 模块（`vendor.springboot.storage` 包）。
> 采用与 `auth` 模块一致的**方法注解式注册**（`@RegisterDisk`）+ **驱动工厂 SPI**（`FilesystemDriver`）设计。

## 特性

- **多磁盘（disk）**：按名称解析不同的存储位置，`Storage.disk("public")`
- **注解式注册**：`@RegisterDisk("name")` 声明磁盘，避免 `@Bean` 名称冲突
- **驱动 SPI**：实现 `FilesystemDriver` 并注册为 Bean 即自动接入，可扩展 S3/OSS/FTP
- **流式 IO**：`putStream` / `readStream` / `writeTo` 内存占用恒定，支持任意大小文件
- **路径穿越防护**：所有路径规范化后校验，`../` 逃逸根目录直接抛异常
- **开箱即用**：不做任何配置也会兜底注册 `local` 磁盘（根目录 `storage/app`）

## 引入

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>storage</artifactId>
</dependency>
```

自动配置通过 **`springboot` 模块**的 `vendor.springboot.storage` 装配（`AutoConfiguration.imports` 注册）生效，
引入 `springboot` 或 `starter` 即启用，无需任何注解。

## 配置式注册

```yaml
jaravel:
  storage:
    enabled: true
    default-disk: local
    disks:
      local:
        driver: local
        root: storage/app
      public:
        driver: local
        root: storage/app/public
        url: /storage          # 生成 URL 的前缀
        visibility: public
      uploads:
        driver: local
        root: /data/uploads
        options:               # 传给驱动的自定义参数
          any-key: any-value
      files:
        driver: database        # 把文件存进数据库（storage-database 模块）
        binary: true            # true=LONGBLOB 二进制；false=LONGTEXT base64 文本
        content-column: content # 存放文件内容的列名（默认 content，可自行指定）
        chunk-size: 1048576     # 单条分片字节上限，超过则切分多行；0/负=不切分
        table-prefix: storage_  # 数据表前缀，默认 storage_
        connection: primary     # 可选，@RegisterConnection 连接别名；省略=默认连接
        visibility: private
```

### `driver: database`（数据库存储，`storage-database` 模块）

引入 `io.github.lijialong1313:storage-database` 后，把文件直接存进数据库的两张表：
`<prefix>file`（元信息）与 `<prefix>file_chunk`（内容分片）。
**不会自动建表**：先执行 `artisan storage:table` 生成建表迁移文件，再 `artisan migrate`（或手动建表）。

**内容列名可定制**：文件内容统一落在一列，列名由 `content-column` 指定，默认 `content`。
允许自行指定（如 `file_data`、`blob` 等）。

**二进制 / 文本由 `binary` 开关决定（单列）**：

- `binary: true`（默认）：内容以二进制写入该列（`LONGBLOB`），适合支持二进制列的关系型数据库。
- `binary: false`：内容以 base64 编码写入该列（`LONGTEXT`），以兼容不支持二进制列的数据库。

> 不再区分 `content_binary` / `content_text` 双列，**无论二进制还是文本都使用同一列**。
> 切换 `binary` 开关后，新文件按新方式写入；如需兼容旧数据，请统一开关再执行迁移脚本重建表。

**自定义列名示例**：

```yaml
jaravel:
  storage:
    disks:
      files:
        driver: database
        content-column: file_data   # 使用自定义列名
        binary: false               # 文本列（LONGTEXT）
```

对应的迁移脚本（默认 `content` + 二进制模式）：

```java
schema.create("storage_file_chunk", table -> {
    table.string("disk", 64).notNull().primary();
    table.string("path", 1024).notNull().primary();
    table.integer("chunk_index").notNull().primary();
    table.binary("content").nullable();   // 列名即 content-column，类型由 binary 决定
    table.integer("size").notNull().defaultValue(0);
    table.bigInteger("created_at").nullable();
    table.bigInteger("updated_at").nullable();
});
```

## 注解式注册（推荐）

与 auth 模块的 `@RegisterGuard` / `@RegisterProvider` 完全同构。

```java
@Configuration
public class StorageConfig {

    // 返回 DiskDefinition，由驱动工厂延迟创建
    @RegisterDisk("local")
    public DiskDefinition localDisk() {
        return DiskDefinition.local("storage/app");
    }

    // defaultDisk = true 覆盖 jaravel.storage.default-disk
    @RegisterDisk(value = "public", defaultDisk = true)
    public DiskDefinition publicDisk() {
        return DiskDefinition.local("storage/app/public")
                .url("/storage")
                .visibility(Visibility.PUBLIC);
    }

    // 方法参数按类型自动注入（与 @Bean 一致）
    @RegisterDisk("s3")
    public DiskDefinition s3Disk(AwsProperties aws) {
        return DiskDefinition.of("s3")
                .with("bucket", aws.getBucket())
                .with("region", aws.getRegion());
    }

    // 也可直接返回 Filesystem 实例，完全自定义
    @RegisterDisk("memory")
    public Filesystem memoryDisk() {
        return new InMemoryFilesystem("memory");
    }
}
```

**注册优先级**：驱动收集 → 配置式磁盘 → `@RegisterDisk`（覆盖同名）→ 兜底 `local`。

### 驱动按需装配（安装 ≠ 启用，用上了才注册）

两个磁盘驱动均由 **springboot 模块**（`vendor.springboot.storage` 包）的 `StorageAutoConfiguration`
装配，且通过 core 的 `OnDriverInUseCondition` 判定，**不写配置不进内存**：

- `LocalFilesystemDriver`：受 `@Conditional(OnLocalDiskDriverCondition.class)` 约束。当任一
  `jaravel.storage.disks.*.driver` 取值为 `local` / `public`，**或用户写了 disks 但没写 driver（兜底回退到 `local`）**，
  或完全没写任何 disk 配置（模块自动兜底注册 `local` 默认磁盘）时装配。`local` 是 storage 的**兜底默认驱动**，
  因此本条件调用 `matchIfAbsent()` 认缺省。
- `DatabaseFilesystemDriver`：受 `@Conditional(OnDatabaseDiskDriverCondition.class)` 约束，仅当
  任一 `jaravel.storage.disks.*.driver` 显式取值为 `database` 时装配。**`database` 不在兜底**，
  用户未显式选用时完全不创建，不连数据库。

`StorageAutoConfiguration` 整体仍受 `jaravel.storage.enabled`（默认 true）开关控制（功能型模块），
但模块一旦启用，只有真正被用到的磁盘驱动才会注册 Bean。

> **发布配置与运行开关解耦**：`StoragePublishableConfig`（供 `artisan vendor:publish --tag=storage`
> 发布 `StorageConfig.java`）由独立的 `StoragePublishAutoConfiguration` 注册，**不受
> `jaravel.storage.enabled` 开关影响**。因此即使关闭存储运行能力，仍可在开发期发布配置模板。

### 为什么不用 `@Bean`

`@Bean("public")` 的 bean name 全局唯一，与其他模块同名 bean 冲突时会抛
`BeanDefinitionOverrideException`。`@RegisterDisk` 将磁盘名与 bean name 解耦，
方法不注册为 Spring Bean，因此不存在冲突。

## 使用

```java
// 默认磁盘
Storage.put("notes/todo.txt", "hello");
String text = Storage.get("notes/todo.txt");
boolean ok  = Storage.exists("notes/todo.txt");
Storage.delete("notes/todo.txt");

// 指定磁盘
Storage.disk("public").put("logo.png", bytes);
String url = Storage.disk("public").url("logo.png");   // => /storage/logo.png

// 大文件流式写入，内存占用恒定
try (InputStream in = request.file("video").getInputStream()) {
    Storage.disk("uploads").putStream("videos/a.mp4", in);
}

// 流式下载
Storage.disk("uploads").writeTo("videos/a.mp4", response.getOutputStream());

// 目录
for (FileInfo f : Storage.files("avatars")) {
    log.info("{} {} bytes", f.path(), f.size());
}
List<FileInfo> all = Storage.allFiles("avatars");   // 递归
Storage.makeDirectory("tmp/work");
Storage.deleteDirectory("tmp/work");

// 元信息
long size          = Storage.size("a.png");
Instant modified   = Storage.lastModified("a.png");
String mime        = Storage.mimeType("a.png");
FileInfo info      = Storage.info("a.png");

// 本地绝对路径（仅 local 驱动；大文件随机写入场景需要）
if (Storage.disk("uploads").supportsLocalPath()) {
    String abs = Storage.disk("uploads").path("videos/a.mp4");
}
```

也可注入 `StorageManager` 使用，避免静态门面：

```java
@Service
public class AvatarService {
    private final StorageManager storage;

    public AvatarService(StorageManager storage) {
        this.storage = storage;
    }

    public String save(byte[] data) {
        String path = "avatars/" + UUID.randomUUID() + ".png";
        storage.disk("public").put(path, data);
        return storage.disk("public").url(path);
    }
}
```

## 扩展自定义驱动

```java
@Component
public class S3FilesystemDriver implements FilesystemDriver {

    @Override
    public boolean support(String driver) {
        return "s3".equalsIgnoreCase(driver);
    }

    @Override
    public Filesystem create(String name, Map<String, Object> config) {
        return new S3Filesystem(name, (String) config.get("bucket"));
    }
}
```

注册为 Spring Bean 后由 `StorageRegistrar` 自动收集，无需手动注册。
磁盘实例**延迟创建**（首次 `disk(name)` 时），因此远程存储不可用不会阻断应用启动。

## 核心 API

| 类型 | 说明 |
| --- | --- |
| `Filesystem` | 文件系统契约，一个实例代表一个磁盘 |
| `FilesystemDriver` | 驱动工厂 SPI，`support(driver)` + `create(name, config)` |
| `DiskDefinition` | 磁盘定义（driver + config），不可变，链式构造 |
| `FileInfo` | 文件/目录元信息 record |
| `Visibility` | `PUBLIC` / `PRIVATE`，local 驱动映射为 POSIX 权限 |
| `StorageManager` | 多磁盘管理器，延迟创建 + 进程级缓存 |
| `Storage` | 静态门面，无 disk 参数的方法作用于默认磁盘 |
| `@RegisterDisk` | 方法注解式注册磁盘 |
| `StorageException` | 统一非受检异常 |

### `Filesystem` 方法一览

所有方法的 `path` 均为磁盘内相对路径，越界访问抛 `StorageException`。

| 分类 | 方法 | 说明 |
| --- | --- | --- |
| 存在性 | `boolean exists(String path)` | 文件或目录是否存在 |
| | `boolean missing(String path)` | `exists` 取反（default 方法） |
| 读取 | `byte[] read(String path)` | 全量读为字节数组 |
| | `String get(String path)` | 全量读为 UTF-8 字符串（default） |
| | `InputStream readStream(String path)` | 流式读，调用方负责关闭 |
| | `long writeTo(String path, OutputStream out)` | 流式写出到目标流，返回字节数 |
| 写入 | `void put(String path, byte[] contents)` | 覆盖写入，自动创建父目录 |
| | `void put(String path, String contents)` | UTF-8 覆盖写入（default） |
| | `long putStream(String path, InputStream in)` | 流式写入，返回字节数，内存恒定 |
| | `void append(String path, byte[]/String)` | 追加写入 |
| 删除/移动 | `boolean delete(String path)` | 删除单个文件，返回是否真实删除 |
| | `int delete(List<String> paths)` | 批量删除，返回成功数（default） |
| | `void copy(String from, String to)` | 复制 |
| | `void move(String from, String to)` | 移动/重命名 |
| 元信息 | `long size(String path)` | 字节数 |
| | `Instant lastModified(String path)` | 最后修改时间 |
| | `String mimeType(String path)` | MIME 类型，探测失败返回 `null` |
| | `FileInfo info(String path)` | 一次性返回全部元信息 |
| 可见性 | `Visibility visibility(String path)` | 读取可见性 |
| | `void setVisibility(String path, Visibility v)` | 设置可见性（非 POSIX 平台静默忽略） |
| 目录 | `List<FileInfo> files(String dir)` | 一级文件 |
| | `List<FileInfo> allFiles(String dir)` | 递归所有文件 |
| | `List<FileInfo> directories(String dir)` | 一级子目录 |
| | `void makeDirectory(String dir)` | 递归创建目录 |
| | `boolean deleteDirectory(String dir)` | 递归删除目录 |
| 定位 | `String url(String path)` | 访问 URL，未配置 `url` 前缀时返回 `null` |
| | `String path(String path)` | 本地绝对路径，不支持时抛异常 |
| | `boolean supportsLocalPath()` | 是否支持 `path()`，默认 `false` |
| | `String name()` | 磁盘名 |

## 设计说明

- **可见性跨平台**：Windows 等非 POSIX 系统上 `setVisibility` 静默忽略，
  `visibility()` 返回磁盘配置的默认值。
- **`local` 与 `public` 是同一驱动**：`public` 只是语义别名，
  区别在于是否配置了 `url` 前缀与 `visibility: public`。

## 与 aether-upload 集成

[aether-upload](../aether-upload/README.md) 的组配置 `disk` 指定磁盘名后，
上传完成的文件会自动落到该磁盘：

```yaml
jaravel:
  storage:
    disks:
      media:
        driver: local
        root: /data/media
        url: /media
  aether-upload:
    groups:
      file:
        disk: media       # 落盘到 media 磁盘
        save-dir: uploads # 此时为磁盘内的相对子目录
```

分片临时文件始终留在本地（合并依赖 `RandomAccessFile` 随机偏移写入，
这是 `Filesystem` 刻意不暴露的能力），只有合并完成的成品才转存到目标磁盘：

| 磁盘类型 | 转存策略 |
| --- | --- |
| 本地（`supportsLocalPath()` 为 true） | `Files.move`，同分区为原子 rename、零拷贝 |
| 远程（S3/OSS 等） | 流式 `putStream`，内存占用恒定 |
