# artisan

Artisan CLI 命令框架，对齐 Laravel `Illuminate\Console`。提供命令注册、签名解析（参数/选项）、命令调度，以及 `java -jar app.jar artisan` 模式检测入口，让 Spring Boot 应用同时承载 HTTP 服务与 CLI 命令。

## 依赖

- `core` — 基础设施（Facade、SpringContext）
- `spring-boot-autoconfigure` — 自动装配
- `slf4j-api` — 日志

## 核心接口

### ArtisanCommand

命令抽象基类，对齐 Laravel `Illuminate\Console\Command`。每个命令需定义 `signature()`（命令签名）和 `description()`，并实现 `handle()` 执行具体逻辑。

```java
public abstract class ArtisanCommand {
    // 命令签名，如 "user:score:cacheOne {studentId} {--sync}"
    public abstract String signature();
    public String description();
    public abstract int handle();          // 返回退出码，0 表示成功

    // 参数与选项访问（子类在 handle() 中调用）
    protected String argument(String name);
    protected String argument(String name, String defaultValue);
    protected String option(String name);
    protected String option(String name, String defaultValue);
    protected boolean hasOption(String name);

    // 控制台输出
    protected void info(String message);
    protected void error(String message);
    protected void warn(String message);

    public String commandName();            // 签名中第一个空格前的部分
}
```

签名格式对齐 Laravel signature 语法：

| 签名 | 命令名 | 参数 | 选项 |
|------|--------|------|------|
| `user:score:cacheScore` | user:score:cacheScore | - | - |
| `migrate {--force}` | migrate | - | force(布尔) |
| `user:score:cacheOne {studentId}` | user:score:cacheOne | studentId | - |
| `user:score:cacheOne {studentId} {--sync}` | user:score:cacheOne | studentId | sync(布尔) |

### ArtisanApplication

命令管理器，对齐 Laravel `Illuminate\Console\Application`。维护命令注册表，解析命令签名与命令行参数，调度命令执行。

```java
public class ArtisanApplication {
    public ArtisanApplication(ApplicationContext applicationContext);

    public void register(ArtisanCommand command);          // 手动注册命令
    public synchronized void scanCommands();              // 从 Spring 容器自动发现 ArtisanCommand bean
    public Map<String, ArtisanCommand> all();              // 获取所有已注册命令

    public int call(String commandName, String[] args);    // 调度命令执行，返回退出码
    public int call(String commandName);                  // 无参数便捷方法
    public void listCommands();                            // 列出所有命令及描述
}
```

命令行参数解析支持：
- 位置参数：直接传递，如 `artisan user:score:cacheOne 20210001`
- 长选项：`--force` / `--sync=true`
- 短选项：`-f`（单字母，映射到 force）

### ArtisanRunner

运行入口，对齐 Laravel `php artisan` 命令行入口。在应用主类中检测 artisan 模式并执行命令。

```java
public class ArtisanRunner {
    public static final String ARTISAN_FLAG = "artisan";

    public static boolean isArtisanMode(String[] args);                       // 检测是否为 artisan 模式
    public static int run(ArtisanApplication app, String[] args);             // 运行命令，返回退出码
    public static void runAndExit(ArtisanApplication app, String[] args);     // 运行命令并退出 JVM
}
```

### ArtisanAutoConfiguration

自动装配类，创建 `ArtisanApplication` bean，自动从 Spring 容器发现所有 `ArtisanCommand` bean。

## 配置

本模块无需额外配置，开箱即用。命令通过实现 `ArtisanCommand` 并注册为 Spring bean 即可被自动发现。

## 使用示例

定义命令：

```java
@Component
public class CacheScoreCommand extends ArtisanCommand {

    @Autowired
    private ScoreService scoreService;

    @Override
    public String signature() {
        return "user:score:cacheOne {studentId} {--sync}";
    }

    @Override
    public String description() {
        return "缓存单个学生成绩";
    }

    @Override
    public int handle() {
        String studentId = argument("studentId");
        boolean sync = hasOption("sync");
        scoreService.cacheOne(studentId, sync);
        info("已缓存学生 " + studentId + " 的成绩");
        return 0;
    }
}
```

应用主类中检测 artisan 模式：

```java
public static void main(String[] args) {
    if (ArtisanRunner.isArtisanMode(args)) {
        // artisan 模式：不启动 HTTP 服务，仅运行 CLI 命令
        ConfigurableApplicationContext ctx =
                new SpringApplicationBuilder(Application.class)
                        .web(WebApplicationType.NONE)
                        .run(args);
        int exitCode = ArtisanRunner.run(ctx.getBean(ArtisanApplication.class), args);
        ctx.close();
        System.exit(exitCode);
    } else {
        // 正常 HTTP 模式
        SpringApplication.run(Application.class, args);
    }
}
```

命令行调用：

```bash
java -jar app.jar artisan                          # 列出所有命令
java -jar app.jar artisan list                     # 列出所有命令
java -jar app.jar artisan user:score:cacheScore    # 执行指定命令
java -jar app.jar artisan migrate --force          # 带选项
java -jar app.jar artisan user:score:cacheOne 20210001  # 带参数
```

## 自动装配

`ArtisanAutoConfiguration` 通过 `@AutoConfiguration` 注册，当 classpath 存在 `ArtisanApplication` 时生效，创建 `ArtisanApplication` bean（`@ConditionalOnMissingBean`，便于业务方覆盖）。命令在首次调用 `call()` / `all()` / `listCommands()` 时惰性从 Spring 容器扫描所有 `ArtisanCommand` bean 并注册。

## 代码生成命令（make:xxx）

artisan 模块内置 8 个代码生成命令，对齐 Laravel `php artisan make:xxx` 系列，通过 `MakeGenerator` 根据 `MakeCodeProperties` 配置的基包和输出目录自动生成 Java 源文件并放到对应包目录下。

### 命令一览

| 命令 | 签名 | 说明 | 生成类 | 目标包 |
|------|------|------|--------|--------|
| `make:controller` | `make:controller {name} {--force}` | 生成 Controller 类 | `UserController` | `{base-package}.http.controllers` |
| `make:middleware` | `make:middleware {name} {--force}` | 生成 Middleware 类 | `AuthMiddleware` | `{base-package}.http.middleware` |
| `make:model` | `make:model {name} {--force}` | 生成 Model 类 | `User` | `{base-package}.models` |
| `make:migration` | `make:migration {name} {--force}` | 生成 Migration 类 | `Migration_YYYY_MM_DD_Name` | `database/migrations/` 目录 |
| `make:command` | `make:command {name} {--force}` | 生成 ArtisanCommand 类 | `SyncDataCommand` | `{base-package}.console.commands` |
| `make:event` | `make:event {name} {--force}` | 生成 Event 类 | `UserRegisteredEvent` | `{base-package}.events` |
| `make:listener` | `make:listener {name} {--event=} {--force}` | 生成 Listener 类 | `SendWelcomeEmailListener` | `{base-package}.listeners` |
| `make:all` | `make:all {name} {--force}` | 一键生成以上全部 | 全部 7 类 | 各自目录 |

### 命名约定

- 类名自动转为 PascalCase：`user_profile` → `UserProfile`，`user` → `User`
- 若名称不含 `Controller`/`Middleware` 等后缀，自动补全：`User` + `Controller` → `UserController`
- Migration 类名固定格式 `Migration_YYYY_MM_DD_Name`（自动加当天日期前缀）
- Listener 可通过 `--event=XXX` 指定监听的事件类型，不指定时使用占位类型 `YourEvent`

### 生成代码结构

#### make:controller
生成的 Controller 自动包含 `@Controller` 注解并实现 `Controllers` 接口，包含 `index` 方法：

```java
@Controller
public class UserController implements Controllers {
    public Response index(Request request) {
        Map<String, Object> data = new HashMap<>();
        data.put("message", "UserController ready");
        return ResponseBuilder.json(data);
    }
}
```

#### make:middleware
生成的 Middleware 自动包含 `@MiddlewareAlias` 注解并实现 `Middleware` 接口，`handle` 方法签名包含 `String... params` 参数：

```java
@MiddlewareAlias("auth")
public class AuthMiddleware implements Middleware {
    @Override
    public Response handle(Request request, NextFunction next, String... params) {
        Response response = next.apply(request);
        return response;
    }
}
```

#### make:model
生成的 Model 继承 `BaseModel`，自带 `@Repository`、`@Table`、`@Primary`、`@Column` 注解及 `find()`/`all()`/`query()` 静态查询方法，对齐 Laravel Eloquent：

```java
@Data
@EqualsAndHashCode(callSuper = false)
@Repository
@Table(name = "users")
public class User extends BaseModel<User, Long> {
    @Primary
    @Column(name = "id")
    private Long id;

    @Column(name = "created_at")
    private String createdAt;

    @Column(name = "updated_at")
    private String updatedAt;

    public static User find(Long id) { return BaseModel.find(User.class, id); }
    public static List<User> all() { return BaseModel.all(User.class); }
    public static QueryBuilder<User, Long> query() { return BaseModel.query(User.class); }
}
```

#### make:listener
生成的 Listener 自动包含 `@Component` 和 `@ListensTo` 注解，由 SpringBoot 自动扫描注册：

```java
@Component
@ListensTo(UserRegisteredEvent.class)
public class SendWelcomeEmailListener implements Listener<UserRegisteredEvent> {
    @Override
    public void handle(UserRegisteredEvent event) {
        // TODO: 实现事件处理逻辑
    }
}
```

### 使用示例

```bash
# 生成 Controller（自动补全 Controller 后缀）
java -jar app.jar artisan make:controller User
# => UserController.java → {base-package}.http.controllers

# 生成 Middleware
java -jar app.jar artisan make:middleware Auth
# => AuthMiddleware.java → {base-package}.http.middleware

# 生成 Model（继承 BaseModel，自带 @Repository/@Table/@Primary/@Column 注解及静态查询方法）
java -jar app.jar artisan make:model User
# => User.java → {base-package}.models

# 生成 Migration（自动加日期前缀）
java -jar app.jar artisan make:migration create_users_table
# => Migration_2024_01_01_CreateUsersTable.java → database/migrations/

# 生成自定义 Artisan 命令
java -jar app.jar artisan make:command SyncData
# => SyncDataCommand.java → {base-package}.console.commands

# 生成 Event
java -jar app.jar artisan make:event UserRegistered
# => UserRegisteredEvent.java → {base-package}.events

# 生成 Listener（关联指定事件）
java -jar app.jar artisan make:listener SendWelcomeEmail --event=UserRegisteredEvent
# => SendWelcomeEmailListener.java → {base-package}.listeners

# 一键生成全部
java -jar app.jar artisan make:all User
```

### 其他 artisan 命令

除 `make:xxx` 系列外，以下模块也注册了 artisan 命令：

| 命令 | 模块 | 说明 |
|------|------|------|
| `migrate` | migration | 执行数据库迁移 |
| `migrate:rollback` | migration | 回滚最近一批迁移 |
| `migrate:reset` | migration | 回滚所有迁移 |
| `migrate:refresh` | migration | 回滚所有并重新迁移 |
| `migrate:status` | migration | 查看迁移状态 |
| `cache:table` | cache | 创建数据库缓存表（使用 database 缓存驱动前需执行） |
| `queue:table` | queue-database | 创建队列任务表和失败任务表（使用 database 队列驱动前需执行） |

> **注意**：cache 和 queue 模块**不会自动创建数据库表**。使用 database 缓存驱动或 database 队列驱动前，必须先执行对应的 `cache:table` 或 `queue:table` 命令建表，或手动调用 `createTable()` 方法。

### 配置说明

通过 `jaravel.artisan.make` 前缀配置生成行为，对应 `MakeCodeProperties`：

```yaml
jaravel:
  artisan:
    make:
      base-package: com.example.app      # 基包名（生成类的根包，默认 com.weacsoft.jaravel）
      output-dir: src/main/java          # 输出根目录（Java 源码根目录，默认 src/main/java）
      migration-dir: database/migrations # 迁移文件目录（默认 database/migrations）
```

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `jaravel.artisan.make.base-package` | `com.weacsoft.jaravel` | 生成类的基包名，Controller/Middleware/Model/Command/Event/Listener 均在此包下创建子包（Migration 生成到 `migration-dir` 指定目录） |
| `jaravel.artisan.make.output-dir` | `src/main/java` | Java 源文件输出根目录，生成文件会按包路径写入此目录下 |
| `jaravel.artisan.make.migration-dir` | `database/migrations` | 迁移文件目录（相对路径，Migration 类生成到此目录而非 Java 包路径下） |

### make:all 输出示例

```bash
$ java -jar app.jar artisan make:all User

===== make:all User =====

  [+] Controller created: com/example/app/http/controllers/UserController.java
  [+] Middleware created: com/example/app/http/middleware/UserMiddleware.java
  [+] Model created: com/example/app/models/User.java
  [+] Migration created: database/migrations/Migration_2024_01_01_User.java
  [+] Command created: com/example/app/console/commands/UserCommand.java
  [+] Event created: com/example/app/events/UserEvent.java
  [+] Listener created: com/example/app/listeners/UserListener.java

===== 完成: 7 成功, 0 跳过 =====
```

### --force 选项

默认情况下，当目标文件已存在时命令会报错拒绝覆盖：

```bash
$ java -jar app.jar artisan make:controller User
[ERROR] 文件已存在，拒绝覆盖: .../UserController.java（使用 --force 强制覆盖）
```

添加 `--force` 选项可强制覆盖已存在文件：

```bash
java -jar app.jar artisan make:controller User --force
# => 覆盖现有 UserController.java

java -jar app.jar artisan make:all User --force
# => 覆盖所有已存在的生成文件
```

`make:all` 命令对每个子生成任务独立处理：单个文件已存在时记为「跳过」并继续后续生成，不会中断整体流程；带 `--force` 时全部覆盖。
