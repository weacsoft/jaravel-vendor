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
