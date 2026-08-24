# Changelog

本项目所有显著变更都记录在此文件。
格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，语义化版本基于 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [0.1.3] - 2026-07-14

### Added（新增）

- **wechat-sdk · 类型化消息模型（Typed Message Model）**：旧 Map 裸接口全量移除。
  - `OfficialAccountService` 64 个类型化 API；`MiniProgramService` 全量重写。
  - `message.Message` 消息基类 + 11 类客服消息 / 被动回复消息（双序列化 `toJsonBody()` / `toXmlArray()`，构造即校验）。
  - 接收侧：`server.ServerMessage` 10 类 + `MessageParser`；`crypto.WxBizMsgCrypt`（SHA1 签名 + AES-ECB 加解密，JDK 实现无三方依赖）；`WeChatServer` plain/safe 双回调模式。
  - `menu.Menu`（fluent + 结构校验）、`template.TemplateMessage` / `SubscriptionNotice`、`user.WeChatUser` 等用户域模型、`jsdk.JssdkConfig`、`mini` 小程序域。
  - 响应统一 `WeChatResponse`（`isSuccess()` / `requireSuccess()` / 类型化取值器，业务错误不再被日志吞掉）。
  - Token 双模式（`legacy` GET `cgi-bin/token` / `stable` POST `cgi-bin/stable_token`）+ core+cache 模块缓存（可共享 redis store）。
- **wechat-sdk · 洋葱内核（Onion Kernel）**：`kernel.WechatKernel` + `WechatMiddleware` + `WechatRequest`（静态组装/提取一体）+ `WechatResponse`（静态组装/返回一体，Kind 判别 + 方向互换 + 被动回复能力守卫）；内置 `VerifySignatureMiddleware`（验签）→ `DecryptParseMiddleware`（解密/解析）两层，业务层可任意追加、短路；`WeChatServer` 变为其薄壳（历史行为 1:1 保留）。
- **wechat-sdk · 网页授权（公众号 OAuth）**：`oauth.WeChatOAuth`（授权 URL 组装 + code 换 openid/用户 + EasyWeChat 兼容会话键 `easywechat.oauth_user.{account}`）；`oauth.WeChatOAuthMiddleware` 自动重定向（已授权放行 / 回调换码存会话回跳 / state 防 CSRF / `enforce-https`），路由别名 `wechat.oauth`（冒号参数 `account[,scope]`）。
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

### Changed（变更）

- 全仓库版本 `0.1.2` → `0.1.3`（pom + 全部文档版本引用）。
- 中间件不再注册为 Spring Bean：classpath 扫描 + 继承式配置，支持 Class 对象/类名/字符串别名三种引用；自动扫描跳过已手动注册的实例。
- `csrf_field`/`@csrf`/`csrf_token`/`@csor`… 改为框架开箱即用内置注册（注册后自检，失败可见而非静默空值）；`VerifyCsrfToken` 未启用时输出空串。
- `asset()` 与 `url()` 语义一致（移除 `/assets` 前缀）；`@route` 指令编译目标修正为 `route`。
- 驱动型模块统一按需装配 + 兜底默认值（cache/queue/database/auth/jwt 工厂模式改造）。
- SessionStore 全局配置化（移除 `support()` 与 session-store guard 配置）。
- 分页参数 `pageNum` → `page`（全站统一）。
- 模块解耦：database↔jblade 解耦（分页/视图标准上提 core）；queue 发布配置移至 event 基础模块；auth 弱引用 http。

### Fixed（修复·摘录）

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
