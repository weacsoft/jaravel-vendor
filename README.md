# Jaravel-Vendor

Java 版 Laravel 框架核心库，在 Spring Boot 3.2.5 基础上近乎 100% 模拟 Laravel 的开发体验。

所有 vendor 模块的包名统一为 `com.weacsoft.jaravel.vendor.*`，与业务项目的 `com.weacsoft.jaravel.*` 分离。

## 设计理念

- **保留 Spring Boot 底层能力**：`@Controller`、`@Service` 等 Spring 注解全部可用
- **Laravel 风格 API**：门面（Facade）、配置（Config）、迁移（Migration）、认证（Auth）、中间件（Middleware）、事件（Event）、缓存（Cache）等概念与 Laravel 一一对应
- **无状态中间件**：所有中间件为 Spring 管理的不可变单例，线程安全
- **多 Guard / 多 Provider**：Auth 支持 JWT 和 Session 两种 Guard 驱动，可注册多个 Provider
- **多数据库**：`@DataSource` 注解指定 Model 的数据源，迁移支持 MySQL/SQLite/SQL Server
- **高级队列**：Event 系统支持 per-listener 队列、独立线程池、重试机制
- **JWT 续期与登出**：Token 自动续期（可选）、登出黑名单（基于 Cache）

## 模块结构

| 模块 | artifactId | 说明 | 详细文档 |
|------|-----------|------|---------|
| core | `core` | Facade/Config/ServiceProvider/SpringContext/Validation/Str·Arr | [README](core/README.md) |
| http | `http` | Middleware管道/Request·Response/路由系统 | [README](http/README.md) |
| auth | `auth` | AuthManager/Guard(JWT·Session)/UserProvider/Auth门面 | [README](auth/README.md) |
| jwt | `jwt` | JWT认证插件（续期/登出黑名单/Cache集成） | [README](jwt/README.md) |
| database | `database` | BaseModel(Eloquent合并模式)/@DataSource多数据源 | [README](database/README.md) |
| migration | `migration` | Blueprint/Schema/Migrator（MySQL/SQLite/SQL Server） | [README](migration/README.md) |
| cache | `cache` | CacheManager/Array·File驱动/Cache门面 | [README](cache/README.md) |
| event | `event` | Dispatcher/Listener/QueueManager（多队列+重试） | [README](event/README.md) |
| jblade | `jblade` | Blade模板引擎（@if/@foreach/@extends等指令） | [README](jblade/README.md) |
| springboot | `springboot` | RouterFunction桥接/全局中间件注入/MVC解析 | [README](springboot/README.md) |
| starter | `starter` | 聚合Starter（引入即自动装配全部模块，jwt可选） | [README](starter/README.md) |

## 快速集成

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.weacsoft</groupId>
    <artifactId>starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

JWT 为可选模块，按需引入：

```xml
<dependency>
    <groupId>com.weacsoft</groupId>
    <artifactId>jwt</artifactId>
    <version>1.0.0</version>
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
// 类名: Migration_2024_01_01_CreateUsersTable
public class Migration_2024_01_01_CreateUsersTable implements Migration {
    public void up(Schema schema) {
        schema.create("users", t -> {
            t.id();
            t.string("name", 50);
            t.timestamps();
        });
        // 一次 up 可处理多张表
        schema.create("profiles", t -> { ... });
    }
    public void down(Schema schema) {
        schema.dropIfExists("profiles");
        schema.dropIfExists("users");
    }
}
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
    auto-run: true               # 启动时自动迁移
```

## 技术栈

- Java 17 / Spring Boot 3.2.5 / Jakarta EE
- jjwt 0.11.5 (JWT)
- Druid (数据库连接池)
- gaarason/database-core (Eloquent ORM)
- Jackson (JSON)
- Maven (构建)

## 许可证

MIT License
