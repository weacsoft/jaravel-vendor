# Jaravel-Vendor

Java 版 Laravel 框架核心库，在 Spring Boot 3.2.5 基础上近乎 100% 模拟 Laravel 的开发体验。

所有 vendor 模块的包名统一为 `com.weacsoft.jaravel.vendor.*`，与业务项目的 `com.weacsoft.jaravel.*` 分离。

## 设计理念

- **保留 Spring Boot 底层能力**：`@Controller`、`@Service` 等 Spring 注解全部可用
- **Laravel 风格 API**：门面（Facade）、配置（Config）、迁移（Migration）、认证（Auth）、中间件（Middleware）、事件（Event）、缓存（Cache）等概念与 Laravel 一一对应
- **无状态中间件**：所有中间件为 Spring 管理的不可变单例，线程安全
- **多 Guard / 多 Provider**：Auth 支持 JWT 和 Session 两种 Guard 驱动，可注册多个 Provider
- **多数据库**：`@DataSource` 注解指定 Model 的数据源，迁移支持 MySQL/SQLite/SQL Server
- **高级队列**：Event 系统支持 per-listener 队列、独立线程池、重试机制；queue-database 提供数据库持久化队列
- **JWT 续期与登出**：Token 自动续期（可选）、登出黑名单（基于 Cache）
- **Artisan CLI**：`java -jar app.jar artisan` 命令行入口，签名解析（参数/选项），与 HTTP 服务共存
- **定时任务**：Cron 调度器，Laravel 风格链式 API（dailyAt/hourly/everyMinute），Redis 分布式锁防多机重复执行
- **Redis 集成**：多命名连接管理（standalone/sentinel/cluster），Redis 缓存驱动、Redis Session 守卫实现多机同步

## 模块结构

| 模块 | artifactId | 说明 | README | AI-API |
|------|-----------|------|-------|--------|
| core | `core` | Facade/Config/ServiceProvider/SpringContext/Validation/Str·Arr | [README](core/README.md) | [AI-API](core/AI-API.md) |
| utils | `utils` | 通用工具（内存编译基础设施 MemoryClassLoader 等） | [README](utils/README.md) | [AI-API](utils/AI-API.md) |
| http | `http` | Middleware管道/Request·Response/路由系统 | [README](http/README.md) | [AI-API](http/AI-API.md) |
| cache | `cache` | CacheManager/Array·File驱动/Cache门面 | [README](cache/README.md) | [AI-API](cache/AI-API.md) |
| jblade | `jblade` | Blade模板引擎（@if/@foreach/@extends等指令，表达式编译） | [README](jblade/README.md) | [AI-API](jblade/AI-API.md) |
| auth | `auth` | AuthManager/Guard(JWT·Session)/UserProvider/Auth门面 | [README](auth/README.md) | [AI-API](auth/AI-API.md) |
| jwt | `jwt` | JWT认证插件（续期/登出黑名单/Cache集成） | [README](jwt/README.md) | [AI-API](jwt/AI-API.md) |
| database | `database` | BaseModel(Eloquent合并模式)/@DataSource多数据源 | [README](database/README.md) | [AI-API](database/AI-API.md) |
| migration | `migration` | Blueprint/Schema/Migrator（运行时编译，3种源模式，MySQL/SQLite/SQL Server） | [README](migration/README.md) | [AI-API](migration/AI-API.md) |
| event | `event` | Dispatcher/Listener/QueueManager（多队列+重试） | [README](event/README.md) | [AI-API](event/AI-API.md) |
| redis-config | `redis-config` | RedisManager/RedisProperties（多命名连接，standalone/sentinel/cluster，分布式锁） | [README](redis-config/README.md) | [AI-API](redis-config/AI-API.md) |
| redis-cache | `redis-cache` | RedisCacheDriver（CacheDriver实现，多机缓存同步） | [README](redis-cache/README.md) | [AI-API](redis-cache/AI-API.md) |
| session-redis | `session-redis` | RedisSessionGuard/RedisSessionStore（多机Session同步） | [README](session-redis/README.md) | [AI-API](session-redis/AI-API.md) |
| artisan | `artisan` | ArtisanApplication/ArtisanCommand/ArtisanRunner（CLI命令框架，签名解析） | [README](artisan/README.md) | [AI-API](artisan/AI-API.md) |
| schedule | `schedule` | Schedule/ScheduleRunner/ScheduledTask（Cron调度，Redis分布式锁） | [README](schedule/README.md) | [AI-API](schedule/AI-API.md) |
| queue-database | `queue-database` | DatabaseQueueDriver/DatabaseQueueWorker（持久化队列，多实例消费，重试） | [README](queue-database/README.md) | [AI-API](queue-database/AI-API.md) |
| springboot | `springboot` | RouterFunction桥接/全局中间件注入/MVC解析 | [README](springboot/README.md) | [AI-API](springboot/AI-API.md) |
| wechat-sdk | `wechat-sdk` | 微信SDK（公众号/小程序API，对齐overtrue/laravel-wechat） | [README](wechat-sdk/README.md) | [AI-API](wechat-sdk/AI-API.md) |
| **model-cache** | `model-cache` | 模型查询缓存（版本化失效，@CachableModel注解按需开启，可选） | [README](model-cache/README.md) | [AI-API](model-cache/AI-API.md) |
| starter | `starter` | 聚合Starter（引入即自动装配全部模块，jwt/model-cache可选） | [README](starter/README.md) | [AI-API](starter/AI-API.md) |
| **plugin-jar-core** | `plugin-jar-core` | JAR插件系统（动态加载/卸载/三级ClassLoader/ASM扫描/动态路由） | [README](plugin-jar-core/README.md) | [AI-API](plugin-jar-core/AI-API.md) |
| **plugin-jar-database** | `plugin-jar-database` | JAR插件数据库持久化（BaseModel/自动建表/多实例共享） | [README](plugin-jar-database/README.md) | [AI-API](plugin-jar-database/AI-API.md) |
| **plugin-java-core** | `plugin-java-core` | Java文件插件系统（动态编译.java/热更新/轻量替代JAR） | [README](plugin-java-core/README.md) | [AI-API](plugin-java-core/AI-API.md) |
| **plugin-jar-multi-tenant** | `plugin-jar-multi-tenant` | JAR插件多租户支持（租户隔离的Bean/路由前缀化，可选） | [README](plugin-jar-multi-tenant/README.md) | [AI-API](plugin-jar-multi-tenant/AI-API.md) |
| **plugin-jar-remote-server** | `plugin-jar-remote-server` | JAR插件远程执行服务端（P2SP子节点，TCP/HTTP） | [README](plugin-jar-remote-server/README.md) | [AI-API](plugin-jar-remote-server/AI-API.md) |
| **plugin-jar-remote-client** | `plugin-jar-remote-client` | JAR插件远程执行客户端（P2SP主节点，动态代理/协调器） | [README](plugin-jar-remote-client/README.md) | [AI-API](plugin-jar-remote-client/AI-API.md) |

## 快速集成

### 1. 添加依赖

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>starter</artifactId>
    <version>0.1.1</version>
</dependency>
```

JWT 为可选模块，按需引入：

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>jwt</artifactId>
    <version>0.1.1</version>
</dependency>
```

插件系统为可选模块，按需引入：

```xml
<!-- JAR 插件系统核心（动态加载/卸载 JAR 插件） -->
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>plugin-jar-core</artifactId>
    <version>0.1.1</version>
</dependency>
<!-- JAR 插件数据库持久化（可选，引入后自动从 JSON 文件切换为数据库持久化） -->
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>plugin-jar-database</artifactId>
    <version>0.1.1</version>
</dependency>
<!-- Java 文件插件系统（可选，动态编译 .java 文件，轻量替代 JAR） -->
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>plugin-java-core</artifactId>
    <version>0.1.1</version>
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

    public static User find(Long id) { return BaseModel.find(User.class, id); }
    public static List<User> all()   { return BaseModel.all(User.class); }
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

## 配置参考

```yaml
jaravel:
  auth:
    default-guard: api
  jwt:
    secret: your-secret-key
    ttl: 3600
    refresh-enabled: true        # JWT自动续期（默认启用）
    blacklist-store: array       # 登出黑名单缓存（默认array，可选file）
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
    source: DIRECTORY            # 迁移源模式：DIRECTORY/JAR/CLASSPATH
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
      table: jobs
      max-attempts: 3
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

迁移模块支持 3 种源模式，适应不同的部署环境：

```yaml
# 目录模式（需要 JDK）
jaravel:
  migration:
    source: DIRECTORY
    directory: migrations
    auto-run: false

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

- Java 17 / Spring Boot 3.2.5 / Jakarta EE
- jjwt 0.11.5 (JWT)
- Druid (数据库连接池)
- gaarason/database-core (Eloquent ORM)
- Jackson (JSON)
- Maven (构建)

## 相关资源

| 资源 | 路径 | 说明 |
|------|------|------|
| AI 接口文档 | 各模块目录下 `AI-API.md` | 25 个模块的结构化接口文档，适合 AI 读取 |
| API 文档站点 | [https://weacsoft.github.io/jaravel-vendor/](https://weacsoft.github.io/jaravel-vendor/) | 25 个模块完整 API 参考（GitHub Pages） |
| Demo 项目 | `../jaravel/` | 展示全部 jaravel 能力的前后端分离示例项目（Laravel 文档风格） |

## 版本

当前版本：**0.1.1**（Maven Central 发布版本）

遵循语义化版本规范（SemVer）：
- `0.x.x`：初始开发阶段，API 可能变化
- `1.0.0`：首个稳定版本
- `1.x.0`：向后兼容的新功能
- `1.0.x`：向后兼容的 bug 修复

## 许可证

[MIT License](LICENSE)
