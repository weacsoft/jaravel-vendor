# Jaravel-Vendor

Java 版 Laravel 框架核心库，在 Spring Boot 3.2.12 基础上近乎 100% 模拟 Laravel 的开发体验。

**同时支持 Spring Boot 3.x（Jackson 2）和 Spring Boot 4.x（Jackson 3）**，无需消费端额外配置 Jackson 版本。通过内置 `JsonCodec` SPI 自动检测 classpath 中的 Jackson 版本（`com.fasterxml.jackson` 或 `tools.jackson`），选择对应实现。

所有 vendor 模块的包名统一为 `com.weacsoft.jaravel.vendor.*`，与业务项目的 `com.weacsoft.jaravel.*` 分离。

> **注解注册 / 依赖回退 / 发布配置**：各模块的 `@RegisterXxx` 注解式注册机制、
> 模块间"有则使用无则回退"策略、`artisan vendor:publish` 发布配置类、
> 以及各模块的 artisan 命令与**数据库表要求**，
> 请统一参见 **[MODULES.md](MODULES.md)**。

## 模块结构

| 模块 | artifactId | 说明 | README |
|------|-----------|------|-------|
| core | `core` | Facade/Config/ServiceProvider/SpringContext/Validation/Str·Arr | [README](core/README.md) |
| json | `json` | JSON 编解码 SPI（Jackson 2/3 双支持，自动检测 classpath，无 Spring 依赖） | - |
| utils | `utils` | 内存编译基础设施（MemoryClassLoader 等，jblade/migration 复用） | [README](utils/README.md) |
| http | `http` | Middleware管道/Request·Response/路由系统 | [README](http/README.md) |
| cache | `cache` | CacheManager/驱动分包/Cache门面（零 Spring 依赖） | [README](cache/README.md) |
| cache-database | `cache-database` | DatabaseCacheDriver/database驱动工厂（原生 JDBC 走 database 模块，不依赖 spring-jdbc，可选） | [README](cache-database/README.md) |
| jblade | `jblade` | Blade模板引擎（@if/@foreach/@extends等指令，表达式编译） | [README](jblade/README.md) |
| auth | `auth` | AuthManager/Guard(JWT·Session)/UserProvider/Auth门面 | [README](auth/README.md) |
| jwt | `jwt` | JWT认证插件（续期/登出黑名单/Cache集成） | [README](jwt/README.md) |
| database | `database` | BaseModel(Eloquent合并模式)/@DataSource多数据源 | [README](database/README.md) |
| migration | `migration` | Blueprint/Schema/Migrator/方言分包（5种源模式：DIRECTORY/DIRECTORY_CLASSES/PACKAGED/JAR/CLASSPATH；MySQL/SQLite/H2/SQL Server/PostgreSQL/Oracle，跨库表迁移） | [README](migration/README.md) |
| event | `event` | Dispatcher/Listener/QueueManager（多队列+重试） | [README](event/README.md) |
| redis | `redis` | RedisManager/RedisProperties（多命名连接，standalone/sentinel/cluster，分布式锁） | [README](redis/README.md) |
| redis-cache | `redis-cache` | RedisCacheDriver（CacheDriver实现，多机缓存同步） | [README](redis-cache/README.md) |
| session-redis | `session-redis` | RedisSessionStore（多机Session同步，基于 http 的 SessionStore） | [README](session-redis/README.md) |
| artisan | `artisan` | ArtisanApplication/ArtisanCommand/ArtisanRunner（CLI命令框架，签名解析） | [README](artisan/README.md) |
| schedule | `schedule` | Schedule/ScheduleRunner/ScheduledTask（Cron调度，Redis分布式锁） | [README](schedule/README.md) |
| queue-database | `queue-database` | DatabaseQueueDriver/DatabaseQueueWorker（持久化队列，多实例消费，重试） | [README](queue-database/README.md) |
| springboot | `springboot` | RouterFunction桥接/全局中间件注入/MVC解析 | [README](springboot/README.md) |
| wire | `wire` | Wire响应式UI（Livewire风格，wire:model双向绑定/wire:click事件/部分更新/延迟重定向） | [README](wire/README.md) |
| captcha | `captcha` | 验证码生成器/存储/加密分子包（number/arithmetic/slider/rotate，轨迹验证，水印） | [README](captcha/README.md) |
| wechat-sdk | `wechat-sdk` | 微信SDK（公众号/小程序API，对齐overtrue/laravel-wechat） | [README](wechat-sdk/README.md) |
| **model-cache** | `model-cache` | 模型查询缓存（版本化失效，@CachableModel注解按需开启，可选） | [README](model-cache/README.md) |
| starter | `starter` | 聚合Starter（引入即自动装配基础模块；redis/wechat/wire/queue-database/cache-database/storage-database/jwt/model-cache 等为可选扩展，按需引入） | [README](starter/README.md) |
| **plugin-jar-core** | `plugin-jar-core` | JAR插件系统（动态加载/卸载/三级ClassLoader/ASM扫描/动态路由） | [README](plugin-jar-core/README.md) |
| **plugin-jar-database** | `plugin-jar-database` | JAR插件数据库持久化（BaseModel/自动建表/多实例共享） | [README](plugin-jar-database/README.md) |
| **plugin-java-core** | `plugin-java-core` | Java文件插件系统（动态编译.java/热更新/轻量替代JAR） | [README](plugin-java-core/README.md) |
| **plugin-jar-multi-tenant** | `plugin-jar-multi-tenant` | JAR插件多租户支持（租户隔离的Bean/路由前缀化，可选） | [README](plugin-jar-multi-tenant/README.md) |
| **plugin-jar-remote-server** | `plugin-jar-remote-server` | JAR插件远程执行服务端（P2SP子节点，TCP/HTTP） | [README](plugin-jar-remote-server/README.md) |
| **plugin-jar-remote-client** | `plugin-jar-remote-client` | JAR插件远程执行客户端（P2SP主节点，动态代理/协调器） | [README](plugin-jar-remote-client/README.md) |
| **storage** | `storage` | 多磁盘文件存储（Filesystem契约/local驱动/@RegisterDisk注解式注册/驱动SPI，零Spring依赖，对齐Laravel Storage） | [README](storage/README.md) |
| **storage-database** | `storage-database` | 存储 database 磁盘驱动（原生JDBC走database模块连接，不用spring-jdbc，可选；Spring装配在springboot模块） | [README](storage-database/README.md) |
| **aether-upload** | `aether-upload` | 不限大小分片上传（断点·断线续传/base64传输/多组配置/storage落盘，对齐peinhu/AetherUpload） | [README](aether-upload/README.md) |

## 快速集成

### 1. 添加依赖

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>starter</artifactId>
    <version>0.1.2</version>
</dependency>
```

JWT 为可选模块，按需引入：

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>jwt</artifactId>
    <version>0.1.2</version>
</dependency>
```

插件系统为可选模块，按需引入：

```xml
<!-- JAR 插件系统核心（动态加载/卸载 JAR 插件） -->
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>plugin-jar-core</artifactId>
    <version>0.1.2</version>
</dependency>
<!-- JAR 插件数据库持久化（可选，引入后自动从 JSON 文件切换为数据库持久化） -->
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>plugin-jar-database</artifactId>
    <version>0.1.2</version>
</dependency>
<!-- Java 文件插件系统（可选，动态编译 .java 文件，轻量替代 JAR） -->
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>plugin-java-core</artifactId>
    <version>0.1.2</version>
</dependency>
```

### 2. 简单示例

#### 路由 + 中间件

```java
@Component
public class Api {
    public void register(Router router, ApplicationContext ctx) {
        router.group(Map.of(Route.Group.PREFIX, "api"), api -> {
            // 公开路由
            api.get("/hello", controller::hello);

            // 认证路由（默认 guard）
            api.get("/me", controller::me).middleware(new Authenticate());

            // 指定 guard（对齐 Laravel auth:api）
            api.get("/profile", controller::profile).middleware(new Authenticate("api"));

            // 中间件链（洋葱模型）
            api.get("/test", handler)
               .middleware(new MyMiddleware("A"))
               .middleware(new MyMiddleware("B"));
        });
    }
}
```

#### Eloquent Model（合并模式）

```java
@Data @Repository @Table(name = "users")
public class User extends BaseModel<User, Long> implements Authenticatable {
    @Primary @Column(name = "id") private Long id;
    @Column(name = "name")  private String name;

    // 静态访问统一入口（每个业务 Model 声明一次）
    public static User self() { return BaseModel.self(User.class); }

    // 静态快捷方法由业务 Model 按需声明，委托给 self()
    public static User find(Long id) { return self().find(id).toObject(); }
    public static List<User> all()  { return self().findAll().toObjectList(); }
}

// 多数据库
@DataSource("secondaryGaarasonDataSource")
public class Product extends BaseModel<Product, Long> { ... }
```

#### 认证（主键比对，不涉及密码）

```java
// 应用层查询 + 校验密码
User user = UserService.login(number, password);
// Auth 以主键登入
Auth.login(user);
String token = Auth.token();  // JWT token
```

#### 事件 + 队列

```java
// 异步监听器（实现 ShouldQueue）
public class SendEmailListener implements Listener<UserEvent>, ShouldQueue {
    public String queue() { return "email"; }  // 独立队列
    public void handle(UserEvent e) { sendEmail(e); }
}

// 分发
EventFacade.dispatch(new UserEvent(user));
```

#### 缓存

```java
Cache.put("key", value, 60);           // 60秒TTL
String v = Cache.get("key", String.class);
long n = Cache.increment("hits");
Object r = Cache.remember("cfg", 300, () -> loadCfg());
```

#### 迁移（新命名规范）

```java
import com.weacsoft.jaravel.vendor.migration.MigrationAnnotation;
import com.weacsoft.jaravel.vendor.migration.Migration;
import com.weacsoft.jaravel.vendor.migration.Schema;

@MigrationAnnotation
public class Migration_2024_01_01_CreateUsersTable implements Migration {
    @Override
    public String getName() {
        return "2024_01_01_CreateUsersTable";
    }

    @Override
    public void up(Schema schema) {
        schema.create("users", table -> {
            table.increments("id");
            table.string("name", 100);
            table.string("email", 150).unique();
            table.timestamps();
        });
    }

    @Override
    public void down(Schema schema) {
        schema.drop("users");
    }
}
```

#### 插件系统

JAR 插件和 Java 文件插件均为可选模块，引入依赖后自动装配：

```java
// JAR 插件：编写插件类
@PluginComponent("greetingService")
public class GreetingServiceImpl implements GreetingService {
    @PluginMapping(path = "/api/greeting", method = HttpMethod.GET)
    public String greeting(String name) {
        return "Hello, " + name + "!";
    }
}

// 主程序：管理插件
@Autowired
HotPluginManager jarPluginManager;
jarPluginManager.registerPluginFromPath(Path.of("plugins/my-plugin.jar"), "my-plugin", true);
jarPluginManager.enablePlugin("my-plugin");  // 路由 /api/greeting 自动注册

// Java 文件插件：放置 .java 文件到 plugins-java/my-plugin/ 目录
// 启动时自动扫描编译，无需手动操作
```

## JSON 编解码（json 模块）

库内部通过 `json` 模块（`com.weacsoft.jaravel.vendor.json`）抽象 JSON 编解码，不直接依赖任何 Jackson 版本：

- **`JsonCodec` 接口**：定义 `toJson` / `fromJson` / `fromJsonToMap` / `convertValue` / `writeToFile` / `readFromFile` 等方法
- **`Jackson2JsonCodec`**：SB3 运行时自动选择（classpath 有 `com.fasterxml.jackson.databind.ObjectMapper`）
- **`Jackson3JsonCodec`**：SB4 运行时自动选择（classpath 有 `tools.jackson.databind.ObjectMapper`）
- **`JsonCodecHolder`**：全局持有器，Spring 环境下由 `JsonCodecAutoConfiguration` 注入，非 Spring 环境（如单元测试）自动检测 classpath
- **`Json` 静态门面**：`Json.stringify(obj)` / `Json.parse(json, MyClass.class)` / `Json.parseToMap(json)` 等一行替换原 `ObjectMapper` 用法

## 配置参考

```yaml
jaravel:
  auth:
    default-guard: api
  jwt:
    secret: your-secret-key
    ttl: 3600000                   # Token 有效期（毫秒，默认 3600000）
    refresh-enabled: true        # JWT自动续期（默认启用）
    blacklist-enabled: false     # 登出黑名单开关（默认 false，需显式开启才生效）
    blacklist-store: ""          # 登出黑名单缓存 store（默认空=使用 cache 默认 store，可选 file 等）
  event:
    queue:
      default:
        pool-size: 16            # 默认队列线程池大小
    retry:
      max-attempts: 3            # 最大重试次数
      delay-ms: 1000             # 重试间隔
  cache:
    default-store: array         # 默认缓存驱动
  migration:
    source: DIRECTORY            # 迁移源模式：DIRECTORY / DIRECTORY_CLASSES / PACKAGED / JAR / CLASSPATH
    auto-run: true               # 启动时自动迁移
  schedule:
    enabled: true                # 启用定时任务调度
  redis:
    options:
      cluster: redis             # 集群模式：redis/cluster/sentinel
      prefix: "myapp_"
    connections:
      default:
        host: 127.0.0.1
        port: 6379
      cache:
        host: 127.0.0.1
        port: 6379
        database: 1
      session:
        host: 127.0.0.1
        port: 6379
        database: 2
  cache:
    redis:
      connection: cache           # Redis 缓存连接名
      auto-register: true
  session:
    redis:
      connection: session         # Redis Session 连接名
      lifetime: 30                # Session 生命周期（分钟）
      cookie: manage_session
  queue:
    database:
      table: jobs                # 任务表名（默认 jobs）
      retry-after: 1800          # 可重试延迟（秒，默认 1800）
      retry-delay-ms: 1000       # 重试间隔（毫秒，默认 1000）
      poll-interval-ms: 1000     # 轮询间隔（毫秒，默认 1000）
      worker-threads: 1          # 每队列工作线程数（默认 1）
      auto-start: false          # 应用启动时自动启动 worker（默认 false）
      max-attempts: 3            # 最大重试次数
      queues:
        - default
        - score
  plugin-jar:
    enabled: true                # 启用 JAR 插件系统
    plugins-dir: plugins         # 插件目录
    auto-restore: true           # 启动时自动恢复已启用的插件
    auto-register: true          # true=自动注册@PluginMapping, false=手动注册
  plugin-java:
    enabled: true                # 启用 Java 文件插件系统
    source-dir: plugins-java     # .java 文件插件源目录
    auto-scan: true              # 启动时自动扫描并注册
    auto-register: true          # true=自动注册@PluginMapping, false=手动注册
```

### 迁移源模式

迁移模块支持 5 种源模式，适应不同的部署环境：

```yaml
# 目录模式（需要 JDK）
jaravel:
  migration:
    source: DIRECTORY
    directory: migrations
    auto-run: false

# 预编译目录模式（只需要 JRE）
jaravel:
  migration:
    source: DIRECTORY_CLASSES
    classes-dir: precompiled/migrations

# 打包模式（只需要 JRE，zip 包）
jaravel:
  migration:
    source: PACKAGED
    package-path: /path/to/migrations.jmigration.zip

# JAR 模式（只需要 JRE）
jaravel:
  migration:
    source: JAR
    jar-path: /path/to/migrations.jar

# Classpath 模式（内置迁移）
jaravel:
  migration:
    source: CLASSPATH
```

## 技术栈

- Java 17 / Spring Boot 3.2.12 / Jakarta EE
- jjwt 0.12.6 (JWT)
- Druid (数据库连接池)
- gaarason/database-core (Eloquent ORM)
- Jackson (JSON)
- Maven (构建)

## 相关资源

| 资源 | 路径 | 说明 |
|------|------|------|
| API 文档站点 | [https://weacsoft.github.io/jaravel-vendor/](https://weacsoft.github.io/jaravel-vendor/) | 31 个模块完整 API 参考（GitHub Pages） |
| Demo 项目 | `../jaravel/` | 展示全部 jaravel 能力的前后端分离示例项目（Laravel 文档风格） |

## 版本

当前版本：**0.1.2**（Maven Central 发布版本）

## 许可证

[MIT License](LICENSE)
