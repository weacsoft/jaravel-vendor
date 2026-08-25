# Changelog

本项目所有显著变更都记录在此文件。
格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，语义化版本基于 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [Unreleased]（目标版本 0.1.2 · 待发布）

### Added（新增）

- **core · `core.lookup` Bean 提供者 SPI（P3 · Spring 解耦终章）**：`BeanLookup` / `GlobalBeanProvider` / `GlobalLookup` 三个纯 Java 接口/类——Spring 宿主由 `jaravel-springboot` 的 `CoreSpringConfiguration` 自动安装 `ContextBeanProvider`（`ApplicationContext` 适配器），非 Spring 宿主一行 `GlobalLookup.install(...)` 即可让 `SpringContext` / `Facade` / `App` / `Config` / 各 `@Register*` 注解扫描全链路开箱可用（发布模板 stable FQCN 不变）。
- **springboot · `CoreSpringConfiguration` + `ContextBeanProvider`**：P3 解耦适配层（imports 首行注册）；业务方可用自定义 `GlobalBeanProvider` Bean 覆盖（`@ConditionalOnMissingBean`）。
- **wechat-sdk · 类型化消息模型（Typed Message Model）**：旧 Map 裸接口全量移除。
  - `OfficialAccountService` 64 个类型化 API；`MiniProgramService` 全量重写。
  - `message.Message` 消息基类 + 11 类客服消息 / 被动回复消息（双序列化 `toJsonBody()` / `toXmlArray()`，构造即校验）。
  - 接收侧：`server.ServerMessage` 10 类 + `MessageParser`；`crypto.WxBizMsgCrypt`（SHA1 签名 + AES-ECB 加解密，JDK 实现无三方依赖）；`WeChatServer` plain/safe 双回调模式。
  - `menu.Menu`（fluent + 结构校验）、`template.TemplateMessage` / `SubscriptionNotice`、`user.WeChatUser` 等用户域模型、`jsdk.JssdkConfig`、`mini` 小程序域。
  - 响应统一 `WeChatResponse`（`isSuccess()` / `requireSuccess()` / 类型化取值器，业务错误不再被日志吞掉）。
  - Token 双模式（`legacy` GET `cgi-bin/token` / `stable` POST `cgi-bin/stable_token`）+ core+cache 模块缓存（可共享 redis store）。
- **wechat-sdk · 洋葱内核（Onion Kernel）**：`kernel.WechatKernel` + `WechatMiddleware` + `WechatRequest`（静态组装/提取一体）+ `WechatResponse`（静态组装/返回一体，Kind 判别 + 方向互换 + 被动回复能力守卫）；内置 `VerifySignatureMiddleware`（验签）→ `DecryptParseMiddleware`（解密/解析）两层，业务层可任意追加、短路；`WeChatServer` 变为其薄壳（历史行为 1:1 保留）。
- **wechat-sdk · 网页授权（公众号 OAuth）**：`oauth.WeChatOAuth`（授权 URL 组装 + code 换 openid/用户 + EasyWeChat 兼容会话键 `easywechat.oauth_user.{account}`）；`oauth.WeChatOAuthMiddleware` 自动重定向（已授权放行 / 回调换码存会话回跳 / state 防 CSRF / `enforce-https`），路由别名 `wechat.oauth`（冒号参数 `account[,scope]`）。
- **cache-database 模块（新）**：数据库缓存驱动独立模块（对齐 `queue-database` 拆分惯例）——`DatabaseCacheDriver`（原生 JDBC，替代 spring-jdbc `JdbcTemplate`，方言适配 MySQL/PostgreSQL/SQLite/H2/SQL Server）、`DatabaseCacheDriverFactory`（数据源走 `database` 模块 `ConnectionManager` 注册表 + 惰性 `Supplier<DataSource>` 可注入 Spring 回退）、`CacheTableCommand`（cache:table 命令）。**零 Spring 依赖**，纯 JVM 可直接使用。
- **wechat-sdk · `vendor:publish --tag=wechat-sdk`**：静态注册 `WechatSdkConfig` 声明式配置模板（`@RegisterWechatOfficialAccount` / `@RegisterWechatMiniApp` + OAuth 配置块），发布不再受运行期条件（OkHttp/`enabled` 开关）牵连。
- **database · Oracle 方言**（`jaravel-oracle`）：schema 限定表名 SQL 生成修复；Oracle 别名去 `AS` 关键字。
- **wire · v2.0 组件系统重构**：`WireController` 声明式契约（fill/mount/render/wireView）、`wire:pagination` / `wire:nav` / `wire:key` / `wire:lazy` 组件级局部刷新、`@WireQuery` 注解与带参 URL 还原（翻页→修改→取消不错位）、URL 状态恢复机制、`Wire.call()` 命名参数、`wire.xsd` 命名空间校验、`refresh()` 生命周期、wire-dialog-close、透明导航事件总线（beforeRequest/afterRequest/beforeUpdate/afterUpdate）、栈式嵌套 section 解析（夜间模式丢失根因修复）。
- **jblade**：完整 Blade 指令集、多重继承、动态扩展、表达式翻译；`ViewCache.recompile()` 启动期全量重编译；`@slot('name', $value)` 标量形式；fat JAR ClassLoader 模板加载。
- **route/http**：中间件别名机制（字符串别名 + `@MiddlewareAlias` 自动注册）、Route 静态门面（`Route`/`RouteDefinition` 重命名）、`RouteHelper` 门面与 `Router.url` 解析（`route()`/`url()` 按别名/路径生成 URL）、路由缓存与处理器链折叠（URL 生成与请求处理加速）、Request null 语义加固（`input/get/query/header/session` 防 NPE）、`Request.fullUrl()`（代理头感知，供 OAuth redirect_uri）。
- **auth/core**：会话能力从 auth 迁移到 http（弱引用）；统一 `Publishable` 契约（`vendor:publish` 合并配置 + 静态资源为单命令单次扫描）；`Application` 基类与 `App` 静态服务定位器入口；`publishToSpring`/`publishAllToSpring`。
- **captcha**：前端自包含、modal 弹层、跨端兼容、场景白名单、端到端测试。
- **storage/aether-upload**：多磁盘文件存储模块 + 分片上传接入。
- **queue/event**：`@RegisterSchedule` / `@RegisterLockProvider` 声明式注册；QueueConfig 发布与驱动装配解耦。
- **database**：`BaseModel` 软删除感知操作、`updateOrCreate`/`firstOrCreate`/`findOrFail`/`create` 帮手、模型影子字段（model_shadow）修复、SQLite COUNT 兼容、非 primary 默认连接名支持。
- **utils**：`Maps` 不可变 Map 构造器、`IpMatcher`（CIDR/区间 IP 匹配）、`TrustProxies`。
- **artisan**：命令注册改注解驱动；`make` 系列命令（迁移/模型/控制器）生成到应用子包；`make:model-from-migration` 反向生成。
- **starter**：storage 纳入基础必选聚合（对齐 Laravel Storage）。
- **json**：JsonCodec SPI（SB3/SB4 双 Jackson 支持）。
- **storage-database 模块（新）**：storage 的 database 磁盘驱动独立模块（对齐 `cache-database` / `queue-database` 拆分惯例）——`DatabaseFilesystem`（原生 JDBC，替代 spring-jdbc `JdbcTemplate`，分片组装/自定义内容列/二进制·base64 双模式）、`DatabaseFilesystemDriver`（纯工厂，`Supplier<DataSource>` 惰性解析 + `connection` 别名走 `database` 模块 `ConnectionManager`，缺失连接时给出可操作提示）、`StorageTableCommand`（`storage:table` 命令，生成建表迁移而非直接建表）。**零 Spring 依赖**，纯 JVM + `database` 模块即可工作；Spring 装配由 `springboot` 模块的 `vendor.springboot.storage` 条件装配（`storage-database` 为 springboot 的 optional 依赖，"jar 在 classpath = 驱动可装配"）。
- **http · 上传落盘助手（UploadFile）**：`com.weacsoft.jaravel.vendor.http.upload.UploadFile` + 函数式 `Target` 接口（`store(MultipartFile, dir, Target)` / `storeAs(...)` / `baseName(...)`），把 `MultipartFile` 落盘能力收敛到 http 模块——storage 契约与门面不再引用 `MultipartFile`，核心层继续零 Spring；`Target (path, bytes) -> ...` 可直接适配 storage `Filesystem::put` 或任意目标（内存/远端），http 不反向依赖 storage。

### Changed（变更）

- **core 模块纯化（P3 · Spring 解耦终章）**：core 移除 `spring-context` 依赖，成为**零 Spring** 的纯 Java 核心（至此 vendor 基础依赖中仅 springboot/starter/保留 driver 层的模块持有 Spring）：
  - **新增 `core.lookup` SPI**：`BeanLookup`（bean/contains/beanNames + `beanQuiet`/`beanOrNull`/`targetClass`/`findAnnotation`/`beansOfType` 默认桥）；`GlobalBeanProvider extends BeanLookup`（+ `registerSingleton`）；`GlobalLookup`（`install`/`uninstall`/`getIfInstalled`/`require()`——未安装时给出含 `install` 指引的明确异常）。
  - **`SpringContext` 保留 FQCN（对外 stable API，publish 模板代码引用它）**，改为纯 Java 静态门面：全部操作委托已安装 `GlobalBeanProvider`；`bean/beanOrNull/contains/registerSingleton` 行为与 P3 前一致，**移除直接暴露容器的 `get()` API**。
  - **纯化清单**：`AnnotationDrivenRegistrar`/`AnnotationScanner`/`SingletonRegistrar`/`LockProviderRegistrar`（构造器不再接收 `ApplicationContext`）；`ProviderRegistry.boot()`/`ConfigRepository`（外部配置层改 `Function<String,Object>` 注入，Spring 宿主传 `environment::getProperty`）/`ConfigDefinitionRegistrar`（`setDefinitions` + `boot()`）/`QueueProperties`（去 `@ConfigurationProperties`，纯 POJO 留原位，FQCN 不变——queue-database 发布模板安全）；各 `@Bean` 扫描时机由 springboot/starter/artisan/http/database 装配类的 `SmartInitializingSingleton` 显式触发（保持原「所有单例就绪后扫描」时序）。
  - **Spring 宿主入口**：springboot 新增 `core.CoreSpringConfiguration`（imports 首行注册）——安装 `ContextBeanProvider`（`ApplicationContext` 适配器，含 `destroySingleton`+`registerSingleton` 更新语义；`AopUtils`/`AnnotatedElementUtils` 桥接）+ 平移原 core 自动装配的 `AppKey` Bean（`@ConditionalOnMissingBean`，业务可覆盖）。
  - **装配条件基类迁移**：`OnDriverInUseCondition`（329 行，`Condition` 实现）自 core 迁 **`springboot`** `vendor.springboot.condition`，7 个子类（auth×1/jwt×1/cache×1/rediscache×1/sessionredis×1/storage×2）更新基类引用；queue 侧 `OnDatabaseQueueDriverCondition`/`OnRedisQueueDriverCondition` 自 queue-database 迁 **`springboot.queuedatabase`**（避免循环依赖）；`QueueDriverRegistrar`（`@RegisterQueueDriver` 扫描器）自 core.queue 迁 **`springboot.queuedatabase`** 纯类化。
  - **测试同步**：core `SpringContextTest`/`ConfigRepositoryTest`/`SingletonRegistrarTest` 改为安装 Map 版 provider 的纯 JVM 语义（断言语义保留）；`OnDriverInUseConditionTest` 迁入 springboot 条件包（断言逐行保留）；**新增 `NonSpringAvailabilitySmokeTest`**（零 Spring import 全链路冒烟：Facade/App/Application 三种注册 + `registerSingleton` 更新语义 + 未安装降级不抛 Spring 类加载异常，§5.4 门禁）；database 4 个测试改经 `GlobalLookup.install/uninstall` + 测试态 `CtxProvider` 适配。
- **database 模块 Spring 装配外移（D2 · 用户确认后执行）**：database 成为**零 Spring import** 模块（pom 移除 `spring-boot-starter-jdbc` / `spring-boot-starter-aop` 编译依赖）：
  - **Spring 装配类迁入 springboot `vendor.springboot.database`**：`DatabaseAutoConfiguration`（含 `connectionRegistrar` + SmartInitializingSingleton 扫描触发、`@Primary` `JaravelDataSource`、`ModelShadowPatcher`（`@ConditionalOnClass(ModelShadowProvider)`）、`database` 发布 tag 静态注册（模板类 `DatabasePublishableConfig` 留 database 模块）、**新增 `BaseModelDataSourceBindingPostProcessor`**——为所有 `BaseModel` Bean 绑定 `GaarasonDataSource`，承接 D2 前字段 `@Autowired @Lazy` 注入语义）/ `EloquentUserProviderAutoConfiguration` + `EloquentUserProviderDriver` / `ModelShadowPatcher`；springboot imports 文件增补 2 项（39 项）。
  - **`BaseModel` 纯化**：数据源字段去 `@Autowired @Lazy`，保留 `@Column(inDatabase=false) @JsonIgnore` + 新增公开 setter `setGaarasonDataSource(...)`；`getGaarasonDataSource()` 的别名/注册表/默认连接解析顺序不变。
  - **行为保真**：原 `DatabaseAutoConfiguration` / `EloquentUserProviderAutoConfiguration` 的 `@AutoConfigureAfter`/`@ConditionalOnClass`/`@ConditionalOnMissingBean` 语义逐条保留（两配置类均无 before/after 字符串，全仓库无 FQCN 字符串引用——迁移前已核查）；gaarason 运行期所需的 Spring JDBC/AOP 由宿主（starter/springboot）提供。
- **queue-database 驱动本体 JDBC 化（D3 · 经用户确认执行）**：queue-database 成为**零 Spring import** 模块（§4.4 验收清单的最后一个例外项消除；pom 的 `spring-jdbc` / `spring-context` 编译依赖移除，仅测试态保留 `spring-jdbc`）：
  - **`DatabaseQueueDriver`**：JdbcTemplate SQL 全改为原生 JDBC（`executeUpdate` / `executeSql` / `queryRows` + `RowMapper<T>` 四件套，完全复刻 `cache-database` 驱动模板；`RETURN_GENERATED_KEYS` 取自增键；DDL/索引降级逻辑与日志、push/pop/release/fail 语义逐条保持）。
  - **`DatabaseQueueDispatcher` / `DatabaseQueueWorker`**：监听器 bean 解析改经 **`core.lookup.BeanLookup` SPI**（core 纯接口）——`beansOfType` 枚举 bean 名 / `contains` + `bean(name|class)` 回退；SPI 传 `null` 时 worker 走"找不到监听器→归档失败队列"原路径（语义安全）。
  - **springboot 侧接线**：`QueueDatabaseAutoConfiguration` 的 `databaseQueueWorker` / `databaseQueueDispatcher` Bean 改传 `new ContextBeanProvider(applicationContext)`（`ContextBeanProvider` 即 P3 的 `ApplicationContext` 适配器），行为与 D3 前一致。
  - **测试同步**：`DatabaseQueueDispatcherTest` 的 `GenericApplicationContext` 桩改为空 Map 版 `BeanLookup` 适配器（断言逐行保留）；`DatabaseQueueDriverTest` 构造签名未变（仍为 `DataSource` 入参）零改动通过。
  - **模板不变**：`QueueDatabasePublishableConfig` 字节级未动（V5 核验）。
- **core·ACL 锁定遗留文件清理（P3 收尾 · 用户已删除源码文件）**：`core/pom.xml` 移除 maven-compiler / maven-jar 的 `<excludes>` 排除配置（`CoreAutoConfiguration.java` 与 core imports 资源文件已由用户以更高权限删除）；`core/README.md` 对应遗留注记移除。至此 core 模块**编译与打包无任何排外逻辑**，P3 完全落地。
- **springboot 装配守护缺陷修复（P2 遗留跨 jar 缺陷 · 由 jaravel 应用启动暴露）**：5 个引用**可选依赖类**的自动配置在类上补 `@ConditionalOnClass`——未在 classpath 上引入对应可选模块（aether-upload / redis / redis-cache / session-redis）的应用启动时不再因 `@ConditionalOnMissingBean` 类型推断 `ClassNotFoundException` 而崩溃（此前 P2 将这些装配从可选模块收敛至 springboot 后，springboot 恒在 classpath 上，缺失守护直接启动失败；全模块测试因 optional 依赖齐备未能暴露）：
  - `AetherUploadAutoConfiguration` → `@ConditionalOnClass({AetherUploadManager, AetherUploadProperties})`
  - `AetherUploadPublishAutoConfiguration` → `@ConditionalOnClass({AetherUploadPublishableConfig, AetherUploadStaticPublishable})`
  - `RedisPublishAutoConfiguration` → `@ConditionalOnClass(RedisPublishableConfig)`
  - `RedisCachePublishAutoConfiguration` → `@ConditionalOnClass(RedisCachePublishableConfig)`
  - `SessionRedisPublishAutoConfiguration` → `@ConditionalOnClass(SessionRedisPublishableConfig)`
  - 其余引用可选模块的装配（captcha / jblade / jwt / modelcache / queue / wechat / wire / redis-cache 主装配等）此前均已有守护（逐文件审计确认）；修复后全仓 34 模块 `clean install`（含测试）BUILD SUCCESS。
- **event 模块纯化（去 Spring 化）**：event 核心模块移除 `spring-boot-autoconfigure` / `spring-boot-configuration-processor` 依赖；`EventAutoConfiguration`（含 `queue` 发布 tag 的静态注册块）/ `EventProperties` / `EventListenerRegistrar` / `EventServiceProvider` 迁 **`springboot`**（`vendor.springboot.event`）；`QueueManager` 改收纯 Java `EventConfig`（新增于 event 模块，队列/重试字段与默认值与原 `EventProperties` 一一对应，`EventProperties.toEventConfig()` 映射）。保留于原模块的纯类（`Event`/`Listener`/`Dispatcher`/`EventDispatcher`/`QueueManager`/`QueueDispatcher`/`EventFacade`/`@ListensTo`/`ShouldQueue`/`QueuePublishableConfig`）行为不变。
- **jwt 模块纯化（去 Spring 化）**：jwt 核心模块移除 `spring-boot-autoconfigure` / `spring-boot-configuration-processor` / `spring-web` / `jakarta.servlet-api` 依赖；`JwtAutoConfiguration` / `JwtProperties` / `OnJwtGuardDriverCondition` 迁 **`springboot`**（`vendor.springboot.jwt`），`JwtTokenResponseFilter`（继承 spring-web `OncePerRequestFilter`）同迁。保留于原模块的纯 Java 类（`JwtConfig`/`JwtService`/`JwtGuard`/`JwtGuardDriver`）行为不变；密钥兜底（`jaravel.key`）、黑名单、宽限期语义不变。
- **migration 模块纯化（去 Spring 化）**：migration 核心模块移除 `spring-boot-autoconfigure` / `spring-boot-configuration-processor` 依赖；`MigrationAutoConfiguration` / `MigrationArtisanAutoConfiguration` / `MigrationPublishAutoConfiguration` 与 `MigrationRunner`（实现 `CommandLineRunner`）迁 **`springboot`**（`vendor.springboot.migration`）；`ConnectionAliasResolver`（装配辅助，反射软依赖 database 注册表）随之迁出。migration 模块的 `MigrationProperties` 本就是纯 POJO（`MigrationCLI`/`MigrationExecutor` 直接消费），保留原位；`MigrationPublishableConfig`（纯，含发布模板）同样保留。
- **model-cache 模块纯化（去 Spring 化）**：model-cache 核心模块移除 `spring-boot-autoconfigure` / `spring-boot-configuration-processor` 依赖；`ModelCacheAutoConfiguration` 迁 **`springboot`**（`vendor.springboot.modelcache`，`@AutoConfigureAfter` 改为直接引用 springboot 侧 `CacheAutoConfiguration`），`ModelCacheProperties` 去掉 `@ConfigurationProperties` 注解后以纯 POJO 保留原位（`ModelCacheService` 直接消费），由装配类 `@Bean @ConfigurationProperties` 方法绑定。保留于原模块的纯 Java 类（`CachableModel`/`ModelCache`/`ModelCacheService`/`ModelCacheProperties`/`ModelCachePublishableConfig`）行为不变。
- **captcha 模块纯化（去 Spring 化）**：captcha 核心模块移除 `spring-boot-autoconfigure` / `spring-boot-configuration-processor` 依赖；`CaptchaAutoConfiguration`（含 publish 静态注册块）/ Spring 侧 `CaptchaProperties` 迁 **`springboot`**（`vendor.springboot.captcha`）。保留于原模块的纯 Java 类（`CaptchaManager` / 各生成器 / `CaptchaStore` 存储 / 核心 `CaptchaProperties` / `CaptchaSceneRegistry` / `CaptchaSceneProperties` / `CaptchaPublishableConfig` / `CaptchaStaticPublishable`）行为不变；AppKey 密钥兜底、Store 自动选择、场景白名单语义不变。
- **aether-upload 模块纯化（去 Spring 化）**：aether-upload 核心模块移除 `spring-boot-autoconfigure` / `spring-boot-configuration-processor` 依赖；`AetherUploadAutoConfiguration` / `AetherUploadPublishAutoConfiguration` / `AetherUploadController`（使用 Spring Web `MultipartFile`）迁 **`springboot`**（`vendor.springboot.aetherupload`）；`AetherUploadProperties` 去掉 `@ConfigurationProperties` 注解后以纯 POJO 保留原位（`AetherUploadManager` / `AetherUploadRoutes` 直接消费），由装配类 `@Bean @ConfigurationProperties` 方法绑定；`AetherUploadRoutes` 的控制器 FQCN 字符串更新为 springboot 侧新地址。保留于原模块的纯 Java 类（`AetherUploadManager` / 记录头存储 / 上传事件 / 门面 / `AetherUploadPublishableConfig` / `AetherUploadStaticPublishable`）行为不变。
- **wire 模块纯化（去 Spring 化）**：wire 核心模块移除 `spring-boot-autoconfigure` 依赖；`WireAutoConfiguration` / `WireProperties` 迁 **`springboot`**（`vendor.springboot.wire`）。保留于原模块的纯 Java 类（`WireManager` / `WireController` / `WireStaticPublishable`）行为不变；wire 响应式绑定、部分更新、组件模板映射语义不变。
- **schedule 模块纯化（去 Spring 化）**：schedule 核心模块移除 `spring-boot-autoconfigure` / `spring-context` / `spring-boot-configuration-processor` 依赖；`ScheduleAutoConfiguration` / `ScheduleRegistrar` / `ScheduleRunner`（Spring `@Scheduled` 每分钟扫描）/ `ScheduleProperties` / `SchedulePublishAutoConfiguration` 迁 **`springboot`**（`vendor.springboot.schedule`），`@EnableScheduling` 驱动保留在装配侧。保留于原模块的纯 Java 类（`Schedule` / `ScheduledTask` / `RegisterSchedule` 注解 / `SchedulePublishableConfig`）行为不变；任务注册、分布式锁（LockProvider）、Laravel 风格调度方法语义不变；`ScheduleRunner` 的测试随迁至 springboot 模块 `ScheduleRunnerTest`。
- **queue-database 模块装配外移（D3 豁免范围收窄）**：`QueueDatabaseAutoConfiguration`（+ 新增 `QueueDatabaseProperties` 的 `@Bean @ConfigurationProperties` 绑定方法）/ `RedisQueueAutoConfiguration` / `QueueArtisanAutoConfiguration` 迁 **`springboot`**（`vendor.springboot.queuedatabase`）；移除 `spring-boot-autoconfigure` / `spring-boot-configuration-processor` 依赖；`QueueDatabaseProperties` 去掉 `@ConfigurationProperties` 注解后以纯 POJO 保留原位（`DatabaseQueueDriver`/`Worker` 直接消费）。<b>D3 豁免</b>：driver/worker 层保留 `spring-jdbc` + `spring-context`（`JdbcTemplate` 与 `ApplicationContext` 监听器解析），保留于原模块的纯 Java 类（`DatabaseQueueDriver`/`Worker`/`Dispatcher`/`RedisQueueDriver`/`QueueTableCommand`/`QueueDatabasePublishableConfig`/驱动条件类）行为与语义不变。
- **wechat-sdk 模块纯化（P2-W）**：wechat-sdk 移除 `spring-boot-starter` / `spring-boot-autoconfigure` / `spring-boot-configuration-processor` 依赖，成为零 Spring 依赖的纯 SDK 模块（SDK API `OfficialAccountService` / `MiniProgramService` / `WeChatOAuth` / `WeChatOAuthMiddleware` / `AccessTokenManager` / `WechatProperties` 纯 POJO 均留原位）。`WechatAutoConfiguration` / `WechatPublishAutoConfiguration` / `WechatOfficialAccountRegistrar` / `WechatMiniAppRegistrar` 迁 **`springboot`**（`vendor.springboot.wechat[.registrar]` 包）；`WechatProperties` 去掉 `@ConfigurationProperties` 注解后以纯 POJO 保留原位，由 springboot `WechatAutoConfiguration` 的 `@Bean @ConfigurationProperties(prefix="jaravel.wechat")` 完成绑定（对齐 model-cache/queue-database 属性装配模式）。`springboot/pom.xml` 对 wechat-sdk 的依赖 `compile` → `optional`。测试：`WechatRegistrarTest`（7 例）与 `WechatPublishableConfigTest` 中的 registry 断言随迁至 springboot 测试树，wechat-sdk 剩余测试保持纯 SDK 断言。发布模板 `WechatPublishableConfig`（纯，`tag=wechat-sdk`）行为不变，publish 字节级一致（V5）。
- **jblade 模块纯化（P2-J 落位）**：jblade 模板引擎模块移除 `spring-core` / `spring-boot-autoconfigure` 依赖，成为零 Spring 依赖的纯引擎；`ViewAutoConfiguration` / `JbladeArtisanAutoConfiguration` / `@RegisterView` / `ViewRegistrar` 迁 **`springboot`**（`vendor.springboot.jblade` 包）。springboot 侧 `SpringBootRouteAutoConfiguration` 的 Blade 相关接线（`BladeDirectiveRegistrar` Bean + `csrf_token()/route()/url()` 模板函数注册）拆入新增条件装配类 `BladeIntegrationConfiguration`（`@ConditionalOnClass(BladeFunctions)` 字符串形式，无 jblade 的应用不加载任何 Blade 类）；`springboot/pom.xml` 中 jblade 依赖改为 `optional`。保留于原模块的纯 Java 类（模板引擎内核 / `BladeDirectives` / `view` 核心 / `JbladePublishableConfig` / `view:cache`·`view:clear` 命令）行为不变；路由内核（中间件别名扫描 / 控制器注册 / CSRF 别名）与 Blade 引擎完全解耦。
- **auth 模块纯化（去 Spring 化）**：auth 核心模块移除 `spring-boot-autoconfigure` / `spring-boot-configuration-processor` 依赖；`AuthAutoConfiguration`/`AuthProperties`/`AuthRegistrar`/`OnSessionGuardDriverCondition`/`AuthPublishAutoConfiguration` 迁 **`springboot`**（`vendor.springboot.auth`），`AuthLifecycleFilter`（继承 spring-web `OncePerRequestFilter`）同迁；`Auth` 门面在容器未装配 `AuthManager` 时抛出带初始化指引的明确异常（不再静默 NPE）。保留于原模块的纯 Java 类（`AuthManager`/`AuthContext`/`contract/*`/`facade/Auth`/`guard/*`/`middleware/Authenticate`/`@RegisterGuard`/`@RegisterProvider`/`AuthPublishableConfig`）行为不变。
- **redis / redis-cache / session-redis 模块纯化（去 Spring 化）**：三个模块均移除 `spring-boot-autoconfigure` / `spring-boot-configuration-processor` 依赖：
  - **redis**：`RedisManager` 改收纯 Java `RedisConfig`（新增，字段与默认值与原 `RedisProperties` 一一对应）；`RedisProperties`（`@ConfigurationProperties(prefix="jaravel.redis")` + `toRedisConfig()` 映射）随装配迁入 **`springboot`**（`vendor.springboot.redis`）；`RedisAutoConfiguration`/`RedisPublishAutoConfiguration` 同迁。
  - **redis-cache**：`RedisCacheAutoConfiguration`/`RedisCacheProperties`/`OnRedisCacheStoreCondition`/`RedisCachePublishAutoConfiguration` 迁 **`springboot`**（`vendor.springboot.rediscache`），`@AutoConfigureAfter` 类引用更新为新坐标。
  - **session-redis**：`SessionRedisAutoConfiguration`/`SessionRedisProperties`/`OnRedisSessionDriverCondition`/`SessionRedisPublishAutoConfiguration` 迁 **`springboot`**（`vendor.springboot.sessionredis`），`@AutoConfigureAfter(RedisAutoConfiguration)` 类引用同步更新。
  - 三者均为 springboot 的 **optional** 依赖（不在 starter 基础集）。保留于原模块的纯 Java 类（`RedisManager`/`RedisConfig`/`RedisPublishableConfig`、`RedisCacheDriver`/`RedisCacheDriverFactory`/`RedisCachePublishableConfig`、`RedisSessionStore`/`SessionRedisPublishableConfig`）行为不变。
- **cache 模块纯化（去 Spring 化）**：cache 核心模块移除 `spring-boot-autoconfigure` / `spring-jdbc` / `spring-boot-configuration-processor` 全部直接依赖与 `autoconfigure` 装配类——Spring 装配（`CacheAutoConfiguration` / `CacheProperties` / `CacheStoreRegistrar` / `OnDatabaseCacheStoreCondition` / `CacheArtisanAutoConfiguration`）统一迁入 **`springboot` 模块**（`vendor.springboot.cache` 包）；database 驱动迁入 **`cache-database` 模块**（走 `database` 模块连接）。`CacheManager.initFromConfig` 改收纯 Java `CacheConfig`（Spring `CacheProperties` 经 `toCacheConfig()` 映射）。`vendor:publish` 注册（`CachePublishableConfig`）、`@RegisterCacheStore` 契约、`artisan cache:table` 行为不变。
- **storage 模块纯化（去 Spring 化）**：storage 核心模块移除 `spring-web` / `spring-jdbc` / `spring-boot-autoconfigure` / `spring-boot-configuration-processor` 全部直接依赖与 `autoconfigure` 装配类、`database` 驱动子类、`artisan` 命令——Spring 装配（`StorageAutoConfiguration` / `StorageProperties` / `StorageRegistrar` / `OnLocalDiskDriverCondition` / `OnDatabaseDiskDriverCondition` / `StorageArtisanAutoConfiguration` / `StoragePublishAutoConfiguration`）统一迁入 **`springboot` 模块**（`vendor.springboot.storage` 包，`storage-database` 为 optional 依赖）；database 驱动迁入 **`storage-database` 模块**（原生 JDBC 走 `database` 模块连接）；`MultipartFile` 上传落盘能力拆入 **`http` 模块**（`UploadFile`）。保留于 storage 的 `StoragePublishableConfig`（`vendor:publish` 发布声明，纯 core 契约）、`@RegisterDisk` 契约、`Filesystem` 契约、local 驱动 SPI 与 `Storage` 门面行为不变。`Filesystem` 不再出现 `MultipartFile`（上传落盘改用 `UploadFile.store(file, dir, fs::put)`）。
- 中间件不再注册为 Spring Bean：classpath 扫描 + 继承式配置，支持 Class 对象/类名/字符串别名三种引用；自动扫描跳过已手动注册的实例。
- `csrf_field`/`@csrf`/`csrf_token`/`@csor`… 改为框架开箱即用内置注册（注册后自检，失败可见而非静默空值）；`VerifyCsrfToken` 未启用时输出空串。
- `asset()` 与 `url()` 语义一致（移除 `/assets` 前缀）；`@route` 指令编译目标修正为 `route`。
- 驱动型模块统一按需装配 + 兜底默认值（cache/queue/database/auth/jwt 工厂模式改造）。
- SessionStore 全局配置化（移除 `support()` 与 session-store guard 配置）。
- 分页参数 `pageNum` → `page`（全站统一）。
- 模块解耦：database↔jblade 解耦（分页/视图标准上提 core）；queue 发布配置移至 event 基础模块；auth 弱引用 http。

### Fixed（修复·摘录）

- **springboot · 自动装配注册补齐**：`org.springframework.boot.autoconfigure.AutoConfiguration.imports` 补齐 cache 两条（`CacheAutoConfiguration` / `CacheArtisanAutoConfiguration`）与 storage 三条（`StorageAutoConfiguration` / `StoragePublishAutoConfiguration` / `StorageArtisanAutoConfiguration`）。
- **aether-upload · 装配顺序字符串**：`@AutoConfiguration(afterName=...)` 中 storage 自动装配类 FQCN 更新为 `springboot` 侧新坐标（`vendor.springboot.storage.StorageAutoConfiguration`）。
- **model-cache · 装配顺序字符串**：`@AutoConfigureAfter(name=...)` 中 cache 自动装配类 FQCN 更新为 `springboot` 侧新坐标（`vendor.springboot.cache.CacheAutoConfiguration`）。
- **springboot · Blade 自动装配类名拼写（P2 遗留缺陷，P3 修复）**：`AutoConfiguration.imports` 中的 `...springboot.BlaeIntegrationConfiguration` 缺 `d`，导致 `BladeIntegrationConfiguration` 从未被 Spring Boot 自动装配（jblade 模板指令注册实际未生效但无报错）。P3 重写 imports 时修正为 `...springboot.BladeIntegrationConfiguration`。

- wire：局部更新内容重复追加（翻页/改名多出一份列表）、对话框关闭致遮罩滞留（白屏）与 DOM 泄漏、init() 属性选择器失效致组件批量不加载、行级参数与 input value 同步、`hideLoading` 先清除触发按钮再隐藏、注释锚点非法位置失效、fat-jar 下 wire.js 双加载/重复 toast。
- jblade：并发渲染模板 `ConcurrentModificationException`（点击后页面直接蹦）、组件插槽双重 HTML 转义、布局名继承链被清空导致 PJAX 退化为整页刷新、序列化模板渲染。
- http/wire：form-urlencoded body 被消费致 wire_body 丢失、翻页/改名参数失效；wire 翻页后取消/提交丢失 page 参数。
- database：model_shadow 字段误入 SELECT 列表、`model_shadow` 双 guard 移除、`OnDriverInUseCondition` 声明式注册场景支持。
- captcha：点击强制最小画布尺寸（`nextInt` 负数崩溃）、fat-jar 模板/插件编译、多模块 PublishableConfig 一并修复。
- core：`Paginator.getItems()` + `BladeTemplate` 并发修复；`WireResponse` 类型兼容（Jackson 空对象解析为 ArrayList）。
- 其它：迁移生成到 Java 源码树、时间戳自动填充（created_at/updated_at/deleted_at）、wire:param-id off-by-one、CSRF/route 注册后自检。

### Docs / Meta

- `wechat-sdk/README.md`：洋葱内核（§8）与网页授权（§9）章节 + `WechatSdkConfig` 发布说明。
- `wechat-sdk/DESIGN-message-model.md`、`wechat-sdk/DESIGN-web-oauth.md`：设计文档（PHP↔Java 对照表 + 测试矩阵 + guard 对接清单）。
- 全量清理文档中兼容性说明/设计思路/流程详解类内容（保持 README 聚焦「怎么用」）。
- 新增 `CHANGELOG.md`（本文件）。

### Tests（测试）

- 全模块单测保持全绿；wechat-sdk 由历史 19 个锁定用例扩展到 **198 个**（类型化消息模型 134 + 洋葱内核/网页授权 42 + 发布模板回归 3），无一联网（mock OkHttp/Servlet）。
- **P3 · 非 Spring 可用性冒烟（§5.4 新增门禁）**：core 新增 `NonSpringAvailabilitySmokeTest`——不 import 任何 Spring 类型，手动安装 Map 版 `GlobalBeanProvider` 后验证 `Facade`/`App.app()`/`Application` 三种注册（bind/single/default）/`publishToSpring` 全链路可用；移除提供者后 `beanOrNull` 空安全降级、强依赖路径给出含 `GlobalLookup.install` 指引的异常（不再抛 Spring 类加载异常）。
