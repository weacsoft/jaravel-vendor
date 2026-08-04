# PROJECT_SUMMARY — jaravel / jaravel-vendor 项目总览

> 本文档是项目的完整总结，涵盖设计思路、架构、模块、文件夹分类、功能等全部内容。
> 最后更新：2026-08-05 | 版本：0.1.2 | 模块数：31

---

## 一、项目定位

**jaravel-vendor** 是一个 Java 版 Laravel 框架核心库，基于 Spring Boot 3.2.5（Spring Framework 6.1 / Java 17），在 Spring Boot 之上近乎 100% 模拟 Laravel 的开发体验——门面（Facade）、配置（Config）、路由（Router）、中间件（Middleware）、Eloquent ORM、迁移（Migration）、认证（Auth）、事件（Event）、缓存（Cache）、Blade 模板、Artisan CLI、定时任务、队列，全部一一对应。

**jaravel** 是各模块的演示项目（多租户 Jar/Java 热更新在线运行平台），基于 jaravel-vendor 构建，采用 Laravel 风格目录结构。

### 核心设计思路

1. **原生 Java 表达，非 PHP 转换层**：jblade 模板引擎使用原生 `compile*` 方法直接编译 Blade 表达式，不使用 `convertPhpExpression` 之类的 PHP→Java 转换方法。
2. **迁移系统参考 gaarason/database-all-support**：迁移文件是独立的 Java 文件，编译运行时去掉注释，自动发布。需要 JDK 的功能（DIRECTORY 源模式）与只需 JRE 的功能（JAR/CLASSPATH 源模式）分离。
3. **JRE 与 JDK 能力分离**：迁移功能需要 JDK 的部分设为可选模块，核心框架只需 JRE 即可运行。
4. **安装 ≠ 启用**：引依赖不代表驱动装配。三层优先级：**声明(@RegisterXxx) > yml 配置 > 兜底默认**。驱动型模块通过 `OnDriverInUseCondition` 按需装配，仅用户显式选用驱动时才生效。
5. **启发式注册（懒加载）**：所有模块的驱动/Store 等组件仅在首次使用时才创建对象（`computeIfAbsent`），未使用不创建，节省内存。
6. **Facade → Spring → 注册 → 报错**：模块解析顺序为先 Facade（包含所有 @Register 注解注册的组件），找不到再从 Spring 容器找，再找不到尝试注册，注册不成功才报错。
7. **support 方法匹配**：驱动发现逻辑统一使用 `support(String driver)` 方法判断兼容性（类似 database-all 的数据库访问模块），遍历所有驱动工厂找到第一个匹配的。
8. **无第三方依赖偏好**：前端追求单文件交付，尽量不引入第三方库；如必须引入则直接内联。jaravel-captcha.js 为自包含单文件，内联全部 CSS，无需 mdui/jQuery。
9. **Maven groupId 与 Java 包名分离**：groupId 为 `io.github.lijialong1313`（Maven Central 命名空间），Java 包名统一为 `com.weacsoft.jaravel.vendor.*`。

---

## 二、项目结构

### 目录布局

```
d:\0code\ai\work\
├── jaravel-vendor\              ← 框架核心库（Maven 多模块项目，Git 仓库根）
│   ├── pom.xml                  ← 父 POM（31 个子模块）
│   ├── README.md                ← 项目说明
│   ├── MODULES.md               ← 模块间依赖/回退/发布配置文档
│   ├── CLUSTER.md               ← 集群部署文档
│   ├── LICENSE                  ← MIT
│   ├── .jitpack.yml             ← JitPack 构建配置
│   ├── upload.bat               ← Maven Central 发布脚本
│   ├── core/                    ← 核心基础模块
│   ├── json/                    ← JSON 编解码 SPI
│   ├── utils/                   ← 通用工具（内存编译基础设施）
│   ├── http/                    ← HTTP/路由/中间件
│   ├── auth/                    ← 认证（Guard/Provider）
│   ├── jwt/                     ← JWT 认证
│   ├── database/                ← 数据库 ORM（BaseModel）
│   ├── migration/               ← 数据库迁移
│   ├── cache/                   ← 缓存
│   ├── storage/                 ← 文件存储
│   ├── event/                   ← 事件系统
│   ├── jblade/                  ← Blade 模板引擎
│   ├── wire/                    ← 响应式 UI（Livewire 风格）
│   ├── captcha/                 ← 验证码
│   ├── schedule/                ← 定时任务
│   ├── artisan/                 ← CLI 命令框架
│   ├── springboot/              ← SpringBoot 集成桥接
│   ├── starter/                 ← 聚合 Starter
│   ├── model-cache/             ← 模型缓存
│   ├── redis-config/            ← Redis 连接管理
│   ├── redis-cache/             ← Redis 缓存驱动
│   ├── session-redis/           ← Redis Session 守卫
│   ├── queue-database/          ← 数据库队列驱动
│   ├── wechat-sdk/              ← 微信 SDK
│   ├── aether-upload/           ← 大文件分片上传
│   ├── plugin-jar-core/         ← JAR 插件系统核心
│   ├── plugin-jar-database/     ← JAR 插件数据库持久化
│   ├── plugin-jar-multi-tenant/ ← JAR 插件多租户
│   ├── plugin-jar-remote-server/← 远程执行服务端
│   ├── plugin-jar-remote-client/← 远程执行客户端
│   └── plugin-java-core/        ← Java 文件插件系统
├── jaravel\                     ← 演示项目
│   ├── pom.xml
│   ├── README.md
│   ├── plugins-java\            ← Java 文件插件目录
│   │   └── demo-greeting\       ← 演示插件
│   └── src\main\
│       ├── java\com\weacsoft\jaravel\
│       │   ├── JaravelApplication.java  ← 应用入口（HTTP/Artisan 双模式）
│       │   ├── config\           ← 配置层（对齐 Laravel config/）
│       │   ├── routes\           ← 路由（Api.java / Web.java）
│       │   ├── database\migration\ ← 数据库迁移
│       │   └── app\
│       │       ├── console\      ← 命令行/定时任务
│       │       ├── event\        ← 事件
│       │       ├── http\controller\   ← 15 个控制器
│       │       ├── http\middleware\   ← 6 个中间件
│       │       ├── listener\     ← 事件监听器
│       │       ├── model\        ← Eloquent Model
│       │       ├── provider\     ← 路由服务提供者
│       │       └── service\      ← Service 层（全 static）
│       └── resources\
│           ├── application.yml
│           ├── static\           ← 前端资源（mdui/codemirror/字体/图标）
│           └── templates\        ← jblade 模板（12 个 .blade.java）
└── PROJECT_SUMMARY.md           ← 本文件
```

### Maven 坐标

| 属性 | 值 |
|------|-----|
| groupId | `io.github.lijialong1313` |
| version | `0.1.2` |
| Java 包名前缀 | `com.weacsoft.jaravel.vendor.*` |
| Spring Boot | 3.2.5 |
| Java | 17 |
| ORM | gaarason/database-query 7.0.15（来自 jitpack.io） |
| 模块总数 | 31 |

---

## 三、核心设计模式

### 3.1 Facade 门面模式

`Facade` 是 `final` 工具类（私有构造器），提供静态 `resolve` 方法从 Spring 容器解析 Bean。各门面类（如 `Auth`、`Cache`）在静态方法中调用 `Facade.resolve(XxxManager.class)` 获取实例后委托调用。

**门面类清单**：

| 门面类 | 模块 | 代理的 Manager |
|--------|------|---------------|
| `Auth` | auth | `AuthManager` |
| `Cache` | cache | `CacheManager` |
| `Config` | core | `ConfigRepository` |
| `EventFacade` | event | `Dispatcher` |
| `Storage` | storage | `StorageManager` |
| `ModelCache` | model-cache | `ModelCacheService` |
| `AetherUpload` | aether-upload | `AetherUploadManager` |
| `Json` | json | `JsonCodecHolder` |

### 3.2 Service Locator 服务定位器

`Application` 类对齐 Laravel `app()` 容器，提供 `make()` / `bind()` / `singleton()` / `register()` / `publishToSpring()` 方法。三张 static 注册表（CGLIB 兼容设计）：

| 字段 | 类型 | 用途 |
|------|------|------|
| `singletons` | `Map<String, Object>` | 单例实例缓存 |
| `factories` | `Map<String, Supplier<Object>>` | 工厂注册表 |
| `defaultBindings` | `Map<String, Class<?>>` | 别名→Spring Bean 类型映射 |

**解析链**：`make(name)` → ① 单例缓存 → ② 工厂注册 → ③ 别名映射（从 Spring 解析）

`AppConfig` 继承 `Application`，提供 typed 访问器 `auth()` / `cache()` / `config()` / `event()` / `session()` / `route()`，`app()` 静态方法返回 `AppConfig` 类型（免强转）。

### 3.3 工厂模式 + support 方法匹配 + 懒加载

所有 Manager 类（CacheManager、StorageManager 等）统一采用双层注册表 + 工厂匹配 + 延迟创建：

| 字段 | 用途 |
|------|------|
| `definitions` / `storeConfigs` | 定义层（延迟创建的配置信息） |
| `instances` / `stores` | 实例缓存（已创建的对象） |
| `drivers` / `driverFactories` | 驱动工厂列表 |

**懒加载核心流程**：
1. 先查实例缓存，命中直接返回
2. 未命中则 `computeIfAbsent` 原子创建
3. 创建时遍历所有驱动工厂，找到第一个 `support(driverName)` 返回 `true` 的工厂
4. 调用工厂 `create(config)` 创建实例并缓存

### 3.4 注解驱动注册

框架提供通用的注解驱动注册基础设施（`core.registrar` 包），由三个基类组成：

- **`AnnotationDrivenRegistrar<A>`**：单注解扫描基类，实现 `SmartInitializingSingleton`，在所有单例就绪后扫描。子类只需实现 `register()` 登记产物。
- **`SingletonRegistrar<A, T>`**：单实例注册器，增加唯一性约束（`override` 机制）。
- **`AnnotationScanner`**：多注解扫描工具，控制扫描顺序。

**核心设计**：`@Register*` 方法写在 `@Configuration` 类上，可注入其他 Bean 作参数，但返回产物只存入各模块自己的 Manager，**不注册为 Spring Bean**，避免 `BeanDefinitionOverrideException`，实现组件名称与 bean name 的解耦。

### 3.5 Active Record（Eloquent 合并模式）

`BaseModel` 对齐 Laravel Eloquent，单一类同时承担实体定义 + 查询能力，无需分离 Entity/Repository。

---

## 四、@Register 注解体系

框架共 9 个 `@Register*` 注解，分布在 8 个模块中：

| 注解 | 模块 | 产物类型 | 实例类型 | 关键属性 |
|------|------|---------|---------|---------|
| `@RegisterGuard` | auth | `GuardDefinition` | 命名多实例 | `value`, `defaultGuard` |
| `@RegisterProvider` | auth | `UserProvider` | 命名多实例 | `value` |
| `@RegisterCacheStore` | cache | `CacheStore` | 命名多实例 | `value`, `defaultStore` |
| `@RegisterDisk` | storage | `Filesystem`/`DiskDefinition` | 命名多实例 | `value`, `defaultDisk` |
| `@RegisterConnection` | database | `GaarasonDataSource` | 命名多实例 | `value`, `defaultConnection` |
| `@RegisterDirective` | jblade | `Handler`/`Condition` | 命名多实例 | `value`, `condition` |
| `@RegisterView` | jblade | `View` | 命名多实例 | `name`, `defaultView` |
| `@RegisterSessionStore` | http | `SessionStore` | 全局唯一 | `override` |
| `@RegisterQueueDriver` | queue-database | `QueueDriver` | 全局唯一 | `override` |

**三种注册方式优先级**（从高到低）：
1. **注解声明式**：`@RegisterXxx` 标注方法 → Registrar 扫描 → 直接放入实例缓存
2. **配置式**：YAML 配置 → 初始化时注册定义到 definitions Map（延迟创建）
3. **手动调用**：`manager.addXxx(name, instance)` 编程式注册

---

## 五、模块清单（31 个）

### 5.1 基础必选模块（starter 聚合，12 个）

| 模块 | artifactId | 说明 |
|------|-----------|------|
| **core** | core | Facade 门面、Config 配置仓库、ServiceProvider、Validation 校验、Str/Arr 工具、分页器、注解注册器基类、驱动按需装配条件基类。所有模块的唯一强依赖 |
| **json** | json | JSON 编解码 SPI，Jackson 2/3 双支持，自动检测 classpath，无 Spring 依赖 |
| **utils** | utils | 内存编译基础设施（MemoryClassLoader 等），供 jblade/migration 复用 |
| **http** | http | 中间件管道（洋葱模型）、Laravel 风格 Request/Response、路由系统、Session 存储 |
| **auth** | auth | AuthManager（多 Guard/多 Provider）、Guard(Session)、Auth 门面、认证中间件 |
| **database** | database | gaarason 集成、BaseModel（Eloquent 合并模式）、@DataSource 多数据源、EloquentUserProvider |
| **migration** | migration | Blueprint 流式建表、up/down 迁移引擎、3 种源模式（DIRECTORY/JAR/CLASSPATH）、6 种数据库支持 |
| **cache** | cache | CacheManager 多 store 管理、Array/File/Database 驱动、Cache 门面 |
| **storage** | storage | 多磁盘文件存储、local/database 驱动、@RegisterDisk 注解式注册 |
| **jblade** | jblade | Blade 模板引擎，编译期生成、继承、组件、自定义指令、PHP 表达式翻译器 |
| **event** | event | Event/Listener/Dispatcher、@ListensTo 自动注册、异步多队列分发+重试 |
| **springboot** | springboot | RouterFunction 路由桥接、Request 注入、Response 处理、JSON 编解码装配 |

### 5.2 基础工具模块（starter 聚合，2 个）

| 模块 | artifactId | 说明 |
|------|-----------|------|
| **artisan** | artisan | Artisan CLI 命令框架，`java -jar app.jar artisan` 模式检测 |
| **schedule** | schedule | Cron 调度器，Laravel 风格链式 API，Redis 分布式锁 |

### 5.3 可选扩展模块（不在 starter 中，17 个）

| 模块 | artifactId | 说明 |
|------|-----------|------|
| **jwt** | jwt | JWT 认证插件，Token 自动续期、登出黑名单（基于 Cache） |
| **wire** | wire | Laravel Livewire 风格部分更新，wire:model 双向绑定、wire:click、wire:section、延迟重定向 |
| **captcha** | captcha | 验证码（数字/算术/滑动/旋转/文字点选），轨迹验证，零依赖前端库 jaravel-captcha.js，全屏弹层模式，桌面/移动端双端兼容，场景白名单权限边界。核心层零 SpringBoot 依赖 |
| **model-cache** | model-cache | 基于版本号的查询缓存，@CachableModel 注解手动开启 |
| **redis-config** | redis-config | Lettuce 客户端、多命名连接管理、standalone/sentinel/cluster、分布式锁 |
| **redis-cache** | redis-cache | 基于 redis-config 的 CacheDriver 实现 |
| **session-redis** | session-redis | 基于 Redis 的 Session 存储 |
| **queue-database** | queue-database | 持久化任务存储、多实例消费、重试机制 |
| **wechat-sdk** | wechat-sdk | 微信公众号/小程序 API（对齐 EasyWeChat） |
| **aether-upload** | aether-upload | 不限大小分片上传/断点续传/base64 分片传输 |
| **plugin-jar-core** | plugin-jar-core | JAR 插件动态加载/卸载、三级 ClassLoader 隔离、ASM 字节码扫描、动态路由注册 |
| **plugin-jar-database** | plugin-jar-database | JAR 插件元数据数据库持久化 |
| **plugin-jar-multi-tenant** | plugin-jar-multi-tenant | 同一 JAR 按租户隔离重复加载 |
| **plugin-jar-remote-server** | plugin-jar-remote-server | P2SP 远程执行服务端（TCP/HTTP） |
| **plugin-jar-remote-client** | plugin-jar-remote-client | P2SP 远程执行客户端（树形拓扑） |
| **plugin-java-core** | plugin-java-core | Java 文件动态编译、热更新、动态路由注册 |
| **starter** | starter | 聚合 Starter，引入即自动装配 12 个基础必选模块 |

---

## 六、模块间依赖关系

```
starter（聚合入口）
├── core ← 所有模块的强依赖
│   └── json（Jackson 2/3 SPI）
├── http → core, utils, jblade(optional)
├── springboot → http, jblade, json, auth(optional)
├── auth → core, http
├── database → core, auth, gaarason/database-query, druid
├── migration → utils, artisan(optional)
├── cache → core, utils, spring-jdbc(optional), artisan(optional)
├── storage → core, http, spring-web, spring-jdbc
├── jblade → utils, core, cache(optional)
├── event → core
├── artisan → core
└── schedule → core, artisan, redis-config

可选扩展模块：
├── jwt → auth, cache, jjwt
├── wire → jblade, http
├── captcha → cache(optional)
├── wechat-sdk → cache, utils, okhttp
├── model-cache → cache, core, database(optional)
├── aether-upload → core, http, event, cache, storage
├── redis-config → core, lettuce
├── redis-cache → core, cache, redis-config
├── session-redis → core, http, auth, redis-config
├── queue-database → core, event, spring-jdbc, redis-config(optional)
├── plugin-jar-core → json, asm (Spring/SB provided)
├── plugin-jar-database → plugin-jar-core, database, migration
├── plugin-jar-multi-tenant → plugin-jar-core
├── plugin-jar-remote-server → plugin-jar-core(optional)
├── plugin-jar-remote-client → plugin-jar-remote-server
└── plugin-java-core → plugin-jar-core
```

**回退策略**：core 是唯一强依赖，其余模块遵循"有则使用，无则回退默认"。默认实现采用内存方式（cache 的 array、queue 的 sync）或文件方式（storage 的 local）。

---

## 七、AutoConfiguration 装配

共 37 个 AutoConfiguration 类通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册，分布在 25 个模块中。

**装配条件机制**：
- **功能型模块**（wire、captcha、plugin-* 等）：默认启用，通过 `jaravel.<模块>.enabled: false` 关闭
- **驱动型模块**（redis-cache、session-redis 等）：通过 `OnDriverInUseCondition` 按需装配（仅用户显式选用驱动时才生效）
- **PublishableConfig**：注册在独立的 `*PublishAutoConfiguration` 中，不受 `enabled` 控制

---

## 八、关键类实现详解

### 8.1 BaseModel（Eloquent ORM 基类）

文件：`database/src/main/java/com/weacsoft/jaravel/vendor/database/BaseModel.java`

**核心能力**：
- **`fill(Map<String, Object> data)`**：1 参数版本，从 Map 批量赋值到对象属性
- **`fill(Map<String, Object> data, String... fields)`**：2 参数版本，第二个参数指定只赋值哪些属性名（白名单过滤），未列出的属性跳过。支持丰富的类型转换（基本类型、BigDecimal、LocalDate/LocalDateTime/Date 等）
- **`static getPropertyNames(Class<?> modelClass)`**：静态方法，通过反射获取 Model 类的所有业务属性名（不含属性值）。遍历类继承链到 BaseModel 为止，跳过 static、transient 字段和 `@Column(inDatabase = false)` 标注的字段
- **`getPropertyNames()`**：实例方法，委托给静态版本
- **`self(Class)`**：从 Spring 容器获取 Model 管理 Bean
- **`save()`**：反射检测主键是否已设值，有值则 UPDATE，无值则 INSERT
- **`replicate()`**：反射创建同类新实例，拷贝业务字段但排除主键
- **数据源解析**：四级回退 —— 别名查 ConnectionManager → 非默认别名回退 Spring → Spring 注入默认 → ConnectionManager 解析默认连接
- **软删除作用域**：`withTrash()` / `onlyTrash()` / `withoutTrash()`

**fill 方法类型转换支持**：String, char, boolean(支持1/0/true/false/yes/no), int, long, double, float, short, byte, BigDecimal, LocalDate, LocalDateTime, LocalTime, Date, 以及兜底的 `valueOf(String)` / String 构造函数。

### 8.2 CacheManager（懒加载模式）

文件：`cache/src/main/java/com/weacsoft/jaravel/vendor/cache/CacheManager.java`

**双层注册表**：
- `storeConfigs`（`Map<String, StoreDefinition>`）：store 定义（延迟创建）
- `stores`（`Map<String, CacheStore>`）：已创建的实例缓存
- `driverFactories`（`List<CacheDriverFactory>`）：驱动工厂列表

**懒加载流程**：
1. `store(name)` 先查 `stores` 缓存，命中直接返回
2. 未命中则 `stores.computeIfAbsent(storeName, this::createStore)` 原子创建
3. `createStore` 从 `storeConfigs` 取定义，遍历驱动工厂找到第一个 `support(driver)` 匹配的，创建并包装返回

### 8.3 StorageManager（support 方法匹配模式）

文件：`storage/src/main/java/com/weacsoft/jaravel/vendor/storage/StorageManager.java`

与 CacheManager 完全对齐的双层注册表 + 工厂匹配 + 延迟创建模式。`createDisk` 遍历所有 `FilesystemDriver`，调用 `driver.support(definition.driver())` 匹配。

### 8.4 SpringContext

`@Component`，实现 `ApplicationContextAware`，将 Spring 上下文存入 static 字段。提供 `bean(Class)` / `beanOrNull(Class)` / `registerSingleton(name, bean)` 等方法，是所有静态访问到 Spring 容器的唯一桥梁。

### 8.5 Application（服务定位器）

三张 static 注册表实现 Laravel Container 语义。`make(name)` 三级查找：单例缓存 → 工厂注册 → 别名映射（从 Spring 解析）。`publishToSpring(name)` 将注册表中的服务发布到 Spring 容器使 `@Autowired` 可注入。

---

## 九、jaravel 演示项目详解

### 9.1 配置（application.yml）

核心配置原则：**安装 ≠ 启用**，三层优先级：声明 > yml > 兜底默认。

| 配置段 | 关键内容 |
|--------|---------|
| 数据库 | SQLite（`jdbc:sqlite:database1.sqlite`），多数据源用 `@RegisterConnection` |
| view | jblade，`template-dir: templates`，`suffix: .blade.java`，支持预编译模式 |
| storage | `default-disk: db`，配 db/local/public 三磁盘（BLOB 分片 1048576 字节） |
| wire | `auto-inject-js: true`，`js-path: /static/js/wire-lib.js?v=2` |
| auth | `default-guard: api`；guards/providers 声明式写在 AuthConfig.java |
| cache | `default-store: array`，stores 配 array/file/database |
| jwt | secret/ttl=3600000/refresh-ttl=604800000/refresh-enabled/blacklist-store=array |
| queue | `driver: sync`（兜底），database 可选（default/emails/payments/invoices 多队列） |
| event | `queue-enabled: true`，默认池 4，retry 3 次 |
| plugin-jar | `auto-restore: false`，multi-tenant(separator=@)，remote(server/client) |
| captcha | 300x150，length 4，AES 加密，interference-level 3 |
| schedule | enabled |
| redis | 仅连接信息登记，不连不装配 |

### 9.2 Controller / Model / Service 模式

**Controller** — 实现 `Controllers` 接口，方法签名统一为 `Response xxx(Request request)`。路由字符串引用 `"UserController::list"`（对齐 Laravel `UserController@index`）。

**Model** — `@Data @Repository @Table`，extends `BaseModel<T, ID>` implements `Authenticatable`。单一类同时承担实体定义 + 查询能力（`self().newQuery().where().first()`、`paginate()`、`save()`），无需分离 Entity/Repository。

**Service** — 全 `public static` 无状态方法，对齐 Laravel `app/Services`。

### 9.3 路由系统

两种分组写法：
- Map 参数式：`Route.group(Map.of(Route.Group.PREFIX, "api"), ...)`
- 流式构建器：`Route.middleware("auth:api","permission:api").group(...)`

四组路由：公开（登录/注册/验证码）、Admin（`auth:admin` + `permission:admin`）、User（`auth:api` + `permission:api`）、Session（`auth:web`）。

### 9.4 模板系统

12 个 `.blade.java` 模板文件：

| 文件 | 作用 |
|------|------|
| `layout.blade.java` | 根布局：mdui 顶栏 + 抽屉 + `@yield` |
| `admin.blade.java` | 管理后台单页应用（69KB，9 个列表区块） |
| `user.blade.java` | 用户中心 |
| `blade-demo.blade.java` | jblade 三层继承演示 |
| `captcha-demo.blade.java` | 验证码演示 |
| `wire-demo.blade.java` | Wire 基础部分更新演示 |
| `wire-list-demo.blade.java` | Wire 列表 CRUD + 分页 + 精准刷新（list/item 核心范例） |
| `wire-spa-demo.blade.java` | Wire SPA（左菜单+右内容+懒加载） |
| `demo/base.blade.java` | 三层继承祖父模板 |
| `demo/two-col.blade.java` | 双栏布局中间层 |
| `layouts/mdui/pageinator.blade.java` | 分页器局部模板 |
| `index.blade.java` | 平台首页 |

### 9.5 插件系统演示

| 类型 | 演示位置 | 内容 |
|------|---------|------|
| Jar 插件 | PluginController + PluginRunController | 上传/启用/禁用/路由注册、反射调用 |
| Java 文件插件 | plugins-java/demo-greeting/ | `@PluginComponent` + `@PluginMapping` 自动注册路由 |
| 多租户 | TenantController | 租户隔离加载、Bean/路由前缀化 |
| 远程执行 | RemoteController | P2SP 树形拓扑、子节点注册/连接/relay 转发 |

---

## 十、工程约定

### 代码约定

- jblade 编译器方法使用 `compile*` 命名（非 PHP 转换）
- 迁移文件使用 `@MigrationAnnotation`，类名约定 `Migration_YYYY_MM_DD_PascalCaseDescription`
- 迁移支持 3 种源模式：DIRECTORY（需 JDK）、JAR（需 JRE）、CLASSPATH（需 JRE）
- 所有事件监听器实现 `ShouldQueue` 接口并指定命名队列
- Artisan 命令使用 jaravel-vendor Schedule 模块 + 分布式锁
- 便捷构造方法：支持 null 参数的构造器应提供合理默认值（如 `new QueueManager()` 内部创建默认 EventProperties）
- UI 框架：mdui.org Material Design 1 (MD1)
- 按钮样式：统一图标和文字对齐，使用 mdui 1.0 兼容的 Material Icons
- JavaFX 版本与 Swing 版本保持相同逻辑操作

### gaarason ORM 关键模式

- `.get().toObjectList()`（不是 `.get()`，后者返回 RecordList）
- `.orderBy("col", OrderBy.DESC)`（不是 `.orderByDesc()`）
- `Model.query().where("id", id).delete()`（不是 `model.delete()`）
- `.first().toObject()`
- `.paginate(list -> list.toObjectList(), page, 15, true)`

### Maven 约定

- 默认 build 禁用 deploy（`maven-deploy-plugin` skip=true）
- 发布使用 `-P release-central` profile
- 发布命令：`mvn clean deploy -P release-central`（或 `upload.bat`）
- Lombok 在 vendor 中为 optional，不传递
- 所有模块使用相同的版本号（0.1.2）

### 文档约定

- 不引用未上传 GitHub 的文件（settings-template、maven-central-guide、handoff、PROJECT_SUMMARY 等）
- AI-API 详细内容应集成到 index.html（gh-pages）
- 文档结构按模块组织：Introduction、Usage Examples、Specific Classes and Methods

---

## 十一、发布状态

### Maven Central

- **版本**：0.1.2
- **发布命令**：`upload.bat` = `mvn clean deploy -P release-central`
- **发布 profile**：`release-central`，包含 central-publishing-maven-plugin、maven-source-plugin、maven-javadoc-plugin、maven-gpg-plugin
- **默认 deploy 已禁用**：根 pom.xml 中 `maven-deploy-plugin` 配置 `<skip>true</skip>`

### GitHub

- **仓库**：https://github.com/weacsoft/jaravel-vendor
- **master 分支**：源代码
- **gh-pages 分支**：前端介绍文档，地址 https://weacsoft.github.io/jaravel-vendor/

---

## 十二、历史变更记录

### 从 manage8.0（PHP Laravel）到 manage8（Java jaravel）的迁移

完整的 PHP→Java 项目迁移，9 个阶段：框架搭建 → 业务项目骨架 → 控制器/服务/模型/事件迁移 → Artisan 命令 + 定时任务 → 402 个业务 TODO 全部迁移完成。

### 2026-06-26~29：captcha 模块 + 热加载增强

- 新增 captcha 验证码模块（第 27 个模块），48 个测试通过
- plugin-java-core 字符串源码模式、plugin-jar-core 字节数组热重载
- 修复 gh-pages 文档子项导航跳转

### 2026-08-02：Wire 模块完善

- 组件级局部刷新（wire:section）、列表 key 支持（data-wire-key）
- SPA 导航（wire:nav）、懒加载（wire:lazy）、CRUD 绑定数据库
- 原生 Laravel Paginator 分页集成

### 2026-08-04：模块统一化 + BaseModel 增强

#### 1. 全模块注册模式排查与修正

排查了所有模块的注册模式，发现 `CacheManager` 不符合启发式注册要求（启动时创建所有 store 实例）。重构为懒加载模式：
- 将 store 定义与实例分离，使用 `ConcurrentHashMap` 存储已创建的实例
- 通过 `computeIfAbsent` 实现按需创建，只有真正访问某个 store 时才 new 出对应对象
- 与 `StorageManager` 的模式保持一致

其余模块（AuthManager、StorageManager、CaptchaManager 等）已符合 Facade 优先 → Spring → 尝试注册 → 报错 的解析顺序，以及 support 方法匹配的驱动发现逻辑。

#### 2. 便捷构造方法补充

为以下类添加了支持 null 参数的便捷构造器：

| 类 | 模块 | 便捷构造器 |
|---|---|---|
| `QueueManager` | event | 无参构造器，内部创建默认 EventProperties |
| `EventDispatcher` | event | 无参构造器，内部创建默认 QueueManager |
| `RedisManager` | redis-config | 单参数构造器，properties 使用默认值 |
| `JwtService` | jwt | 简化参数构造器，config 使用默认值 |
| `JwtGuardDriver` | jwt | 单参数构造器，jwtConfig 使用默认值 |
| `ModelCacheService` | model-cache | 单参数构造器，properties 使用默认值 |
| `OfficialAccountService` | wechat-sdk | 三参数构造器，cacheManager 为 null 时回退内存缓存 |

#### 3. BaseModel 增强

在 `BaseModel` 中添加了：
- `fill(Map<String, Object> data)` — 1 参数版本，与原有行为一致
- `fill(Map<String, Object> data, String... fields)` — 2 参数版本，第二个参数指定只赋值哪些属性名
- `static getPropertyNames(Class<?> modelClass)` — 静态方法，反射获取 Model 类所有业务属性名
- `getPropertyNames()` — 实例方法，委托给静态版本

#### 测试验证

所有 9 个修改模块编译通过，共 123 个测试全部通过（0 失败、0 错误）：

| 模块 | 测试数 |
|------|--------|
| cache | 23 |
| database | 60 |
| event | 6 |
| storage | 42 |
| auth | 21 |
| jwt | 15 |
| model-cache | 8 |
| wechat-sdk | 19 |
| redis-config | 12 |

### 2026-08-04：Request 类增强（jaravel-vendor master 分支）

- `Request.java`：修复 NPE，新增 9 个方法
- `RequestFactory.java`：修复 wire_body 参数丢失
- `wire.js` / `wire-lib.js`：修复重复 getInputValue，新增 credentials
- 多个安全/认证相关类修复（VerifyCsrfToken、TrustProxies、CookieSessionStore、Authenticate）
- gh-pages index.html 更新 Request 类客户端信息

---

## 十三、给下一个 AI 的操作指南

1. **环境准备**：确保 JDK 17+ 和 Maven 3.6+，先在 `jaravel-vendor` 执行 `mvn clean install -DskipTests` 安装 31 个模块到本地仓库
2. **编译 demo**：在 `jaravel` 目录执行 `mvn clean compile`
3. **启动 demo**：`mvn spring-boot:run`，访问 `http://localhost:8080/`
4. **运行测试**：`mvn test -pl <模块名>` 运行单个模块测试
5. **新增功能**：遵循模块化约定（pom.xml + AutoConfiguration + Properties + README）
6. **版本迭代**：修改 pom.xml 版本号后执行 `mvn clean deploy -P release-central`

### 关键文件路径

| 用途 | 路径 |
|------|------|
| vendor 父 POM | `d:\0code\ai\work\jaravel-vendor\pom.xml` |
| vendor README | `d:\0code\ai\work\jaravel-vendor\README.md` |
| vendor MODULES | `d:\0code\ai\work\jaravel-vendor\MODULES.md` |
| 发布脚本 | `d:\0code\ai\work\jaravel-vendor\upload.bat` |
| demo POM | `d:\0code\ai\work\jaravel\pom.xml` |
| BaseModel | `d:\0code\ai\work\jaravel-vendor\database\src\main\java\com\weacsoft\jaravel\vendor\database\BaseModel.java` |
| Facade | `d:\0code\ai\work\jaravel-vendor\core\src\main\java\com\weacsoft\jaravel\vendor\core\Facade.java` |
| Application | `d:\0code\ai\work\jaravel-vendor\core\src\main\java\com\weacsoft\jaravel\vendor\core\Application.java` |
| SpringContext | `d:\0code\ai\work\jaravel-vendor\core\src\main\java\com\weacsoft\jaravel\vendor\core\SpringContext.java` |
| CacheManager | `d:\0code\ai\work\jaravel-vendor\cache\src\main\java\com\weacsoft\jaravel\vendor\cache\CacheManager.java` |
| StorageManager | `d:\0code\ai\work\jaravel-vendor\storage\src\main\java\com\weacsoft\jaravel\vendor\storage\StorageManager.java` |
| 项目 memory | `c:\Users\Useradmin\.trae-cn\memory\projects\-d-0code-ai-work--p2-ee44ded9b40c723ba9cc\project_memory.md` |
| 用户 profile | `c:\Users\Useradmin\.trae-cn\memory\user_profile.md` |
