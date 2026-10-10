# Changelog

本项目所有显著变更都记录在此文件。
格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，语义化版本基于 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [Unreleased]（目标版本 0.1.3 · 开发中）

- **M16 缓存命中值的类型还原（已完成实现 + 用例）**：`find`/`findAll` 命中后做元素级还原（`Map → 实体`，按**元素嗅探**判断而非按 store 名），`query` 的「任意 Object」契约**不做还原**；array（内存）store 命中的 gaarason **托管实体**经 `isInstance` 短路**原样返回同一实例**（不会被 JSON 转成游离 POJO）。**不可还原时驱逐该键并回源 loader**（绝不返回错类型；「返回原值 + WARN」会让类型契约变成「有时实体、有时 Map」）。**注意**：JSON 往返会丢失懒加载/派生字段 —— 还原成功也可能有损，含派生/关联字段的实体不建议放进多机序列化 store（写进 javadoc 与告警文案）。新增 `ModelCacheTypeCoercionTest`（6 例：Map→实体、List&lt;Map&gt;→实体列表、不可还原→驱逐+回源、托管实体同一实例、query 不转换、未命中回填）。
- **N4 交付协议（定稿为文档化，不引入响应头）**：`JwtService.refreshPair(...)` 返回 `record TokenPair(access, refresh)` 并拉黑旧 refresh；**框架不经响应头下发新 refresh token** ——

  由业务在自己的 refresh 端点返回它。理由（三位专家一致）：让框架级过滤器按配置下发长期凭证，等于替应用决定「凭证交付策略」（HttpOnly Cookie / body / 移动端安全存储），且任何能读响应头的脚本（含 XSS）都能取走 7 天期凭证；`refreshPair` 已是 public API，应用侧完全可控，零新增配置与长期兼容负担。
- **N1 密钥形态校验补充与残余说明**：
  - `jaravel.key` 未配置时在 **AppKey 生产点**（`CoreSpringConfiguration.appKey`）打一次 ERROR，列出**全部 4 个消费方**的影响（验证码校验、加密 Cookie 解密、JWT 验签、wire 快照签名）—— 临时随机密钥「每次启动都不同」，多实例/重启后这四类凭证全部失效；此前只在 captcha 局部可见。
  - **残余（必须知晓）**：`fail-fast-on-invalid-key` **默认 false**，因此 RSA 误配（只给公钥/只给私钥）**仍表现为每请求 500**，只是启动多一条 ERROR；生产环境请显式置 `true` 以在启动期中止。
  - 核心 `CaptchaCrypto.create` 对「出厂默认 AES 密钥/空密钥」加一次性 WARN，覆盖非 Spring 直连路径。
- **O3 凭证响应禁止缓存（安全，本轮新发现）**：`JwtTokenResponseFilter` 写 `X-New-Token` 时同时下发 `Cache-Control: no-store` 与 `Pragma: no-cache` —— RFC 6749 §5.1 要求令牌响应不可缓存，否则浏览器私有缓存/配置不当的共享缓存会留存凭证。
- **O5 删除绕过签名的公开入口（0.1.3 未发布，破坏性窗口）**：`WireRequest.getData()` / `getMergedData()` 直接调用不验签的 `WireManager.decodeSnapshot`；仓库内**无任何调用方**（主流程走 `WireController` 的「先验签、后解析」），因此在破坏性版本内**直接删除**，而不是保留 `@Deprecated` 警告。
- **R1 修正（本轮引入的高危缺陷）**：`BaseModel.PATCHED_ENTITY_MEMBERS` 换成 `WeakHashMap` 后**必须包 `synchronizedMap`** —— `WeakHashMap` 非线程安全，且 `contains` 内部会 `expungeStaleEntries()` 做结构性修改，锁外快路径与锁内 `add` 并发会丢条目/抛异常（等于把 M20 的故障类别搬到登记表上）。同时把「登记」移到「变更完成之后」（不变式：在集合中 ⇒ 已修补完成）。
- **R5/R6 可观测性**：类型还原失败的告警由「进程级一次」改为**按模型类各一次**（第二个坏模型不再永久静默）；非内存 store 的告警文案改写为「还原可能**有损**（丢懒加载/派生字段）+ 缓存键不含租户维度 + `query` 不做还原」，不再声称「find/findAll 类型契约不成立」（已修）。

### Fixed（修复 · 第十一轮：内存与大文件（O1/O2/O4）——直接修复，不留登记）

- **O1 数据库磁盘改为真正的流式（可用性，高）**：`storage-database/DatabaseFilesystem`
  - `putStream`：由 `input.readAllBytes()`（整份上传文件进堆）改为**按分片边读边写**（内存 O(chunkSize)），全部写入仍在同一事务内，失败整体回滚；新增 `readFully` 处理 `InputStream.read` 提前返回。
  - `readStream`：由 `new ByteArrayInputStream(read(path))`（整份文件进堆）改为**按分片惰性读取**的流（每次查询只取一片），`available()` 不再一次性暴露整文件长度。
  - `writeTo`：由全量 `read` 改为 `readStream(...).transferTo(output)`（逐片转发）。
  - **效果**：`disk: <数据库磁盘>` 下上传/下载大文件不再整份进内存；`AetherUploadManager.moveToDisk` 的「内存占用恒定」注释现在成立。
- **O2 并发追加不再丢更新（正确性）**：`DatabaseFilesystem.append` 由「全量读 → 拼接 → 全量写」的跨事务读改写，改为**只重写最后一个分片**（内存 O(chunkSize + 追加长度)）并更新元信息；元信息更新用**大小 CAS**（`WHERE size = 读到的旧值`，失败重读重试），并对同一路径加**分段锁**串行化（H2 MVStore 快照语义下仅靠 CAS 实测仍会丢更新：并发追加 8000 字节只留下 1900）。
- **O4 队列前缀跟随全局 redis 前缀（配置口径）**：`RedisQueueDriver` 不再硬编码 `jaravel:queue`，改为 `RedisManager.getPrefix() + "queue"`（前缀为空时回退默认值）。运维按 `jaravel.redis.options.prefix` 做命名空间隔离时，队列不再落在隔离之外。
- **aether 临时文件暂存开关（PHP `upload_tmp_dir` 风格，用户要求）**：新增
  ```yaml
  jaravel:
    aether-upload:
      spool:
        enabled: false        # 开启后所有组的 .part 临时文件统一切到 spool.dir
        dir: /data/aether-tmp # 为空则用系统临时目录下的 jaravel-aether-uploads
  ```
  默认关闭时行为不变（沿用各组 `temp-dir`）；开启后可把大文件临时数据放到缓存盘/独立数据盘，避免写满应用目录或系统盘。
- **新增用例（实跑通过）**：`DatabaseFilesystemStreamingTest`（6 例：写入按分片读取（断言单次请求不超过 chunkSize）、读取流一次只驻留一片（断言 `available() <= chunkSize`）、`writeTo` 流式、追加（跨分片末片不满）内容正确、追加自动创建文件、**8 线程并发追加 8000 字节不丢更新**）、`AetherUploadSpoolConfigTest`（3 例：默认不写 spool、开启后写入指定目录、`dir` 为空回退系统临时目录）、`RedisQueueDriverTest`（既有 8 例回归通过）。
- **仍未闭环（唯一一项，非缺陷）**：M20「并发首查/稳态零写入」的**自动化用例**未写（构造可查询的 `BaseModel` 夹具需要 gaarason 模型初始化 + 受保护成员访问链，容易写成脆弱用例）；实现已在位（同步化 `WeakHashMap` + 登记后移）。`DefaultAppKey.isTemporary()` 因该文件被系统拒绝写入未能添加，已用 AppKey 生产点读原始配置的等价实现覆盖同一判定。
### Changed（变更 · 第十轮：0.1.3 收口 + 剩余 P0/P1 项 + 第三/四轮专家纠正）

- **版本保持 0.1.3（尚未发布，破坏性变更随该版本一起发布）**：自 `484c396` 起本仓库含破坏性变更（`auth`/`session` 模块拆分、公开 FQN 迁移，且**未保留兼容壳**），全部 36 个 pom、文档依赖片段与 demo 均保持 **0.1.3**（用户确认：0.1.2 已发布，0.1.3 尚未发布，破坏性变更随 0.1.3 一起发布即可；**不额外升 minor**）。为避免「demo 固定依赖 0.1.3 却命中本地仓库旧产物」的陷阱，收口后重新 `install` 覆盖本地 0.1.3 产物，并实测 demo 依赖树全部为本仓库新构建、demo 用例全绿。
- **N4 refresh token 轮换（可选增量 API，不改既有语义）**：新增 `JwtService.refreshPair(...)`，返回 `record TokenPair(access, refresh)`，用旧 refresh 换取**新令牌对**并把旧 refresh 拉黑；`refresh()` **语义保持原样** —— 现有协议只通过 `X-New-Token` 下发 access，**没有通道下发新 refresh**，若在此拉黑旧 refresh，客户端刷新一次后就永久无法刷新（功能性破坏，专家明确否决先前方案）。黑名单不可用（默认 `blacklistEnabled=false`）时打一次性 WARN，明确「轮换未生效」而不假装已轮换；收到**已在黑名单中的** refresh token 记 ERROR（凭证被盗重放的强信号）。
- **M19 迁移解析失败（按专家意见收窄）**：`MigrationFileParser` 仅把「文件确实存在但编译/实例化/`up()` 失败」计入失败清单并 `log.error`；**目录缺失 / 目录内无 `.java` → 回退 classpath 仍视为合法成功**；目录内确有 `.java` 却编译失败时改 ERROR（仍允许回退，不打断「只带已编译类运行」的部署）；命令打印失败清单并 `return 1` 且**不生成模型**（缺列的 Model 比失败更危险）；**不新增 `--allow-parse-errors` 逃生口**（验收与安全专家均判既有行为更安全，与架构师的分歧按更安全一侧裁决）；删除无调用方的 `parseAllStrict`；`MigrationScanner` 中「跳过无法加载的类」恢复 `debug`（该循环遍历目录下所有 class，非迁移类加载失败属正常，避免日志放大）。
- **N1 验证码密钥形态校验（分档 + 打在解析后配置上）**：校验作用于**经 `jaravel.key` 兜底后的有效配置**（避免对已兜底部署误报）；**RSA 必须 `公钥|私钥`**（只给公钥时服务端无法解密用户输入 → 所有验证永远失败；只给私钥旧实现按公钥解析 → 每请求 500）；AES 仍为出厂默认密钥（源码公开常量）或空 → 等价于无密钥；新增 `jaravel.captcha.fail-fast-on-invalid-key`（**默认 false**：直接 fail-fast 会让既有部署升级后无法启动；置 true 可中止启动）。核心 `CaptchaCrypto.create` 对默认/空 AES 密钥加一次性 WARN，覆盖非 Spring 直连场景。
- **M17 验证码参数脚枪（撤销弃用，改语义护栏）**：三参 `verify(key, userInput, encryptionKey)` 是合法能力（多场景/多租户需要），**不弃用**；改为在 `CaptchaManager.verifyDetailed` 入口加护栏 —— 首参命中已注册类型名且不含 `.`（即旧 README 的 `verify(type, key, input)` 写法）时抛 `IllegalArgumentException`（附正确用法），不再静默返回 false。README 全量修正：`generate(type, key)` → `generate(type)`、`verify(type, key, input)` → `verify(captchaKey, userInput)`、删除 `verifyToken` 段与「token 可在有效期内重复使用」的错误表述（与「一次性」语义矛盾）。
- **M20 修正（已落地代码里的新问题）**：`patchShadowColumn` 的登记从「变更之前」移到**变更完成之后**，使不变式「在集合中 ⇒ 已修补完成」成立（否则并发线程可能看到已登记却仍用含 `model_shadow` 的列集拼 SELECT）；`PATCHED_ENTITY_MEMBERS` 改用 `WeakHashMap` 承载（gaarason 可能为同一实体反复创建新 `EntityMember`，强引用 Set 会无界增长）。
- **L2 性能修正**：`LocalFilesystem` 的 canonical 根路径改为**构造后缓存**（`realRoot()`），不再每次 `resolve()` 都做 `toRealPath()`（热路径 syscall 放大）。
- **M16/N7**：`ModelCacheService` 解析到**非内存** store 时一次性 WARN —— ①`find/findAll/query` 命中后是直接强转，非内存 store 取回 `LinkedHashMap`/`ArrayList`，类型契约不成立；②缓存键不含租户维度，多租户共用 store 会互相命中对方数据（要求 `queryKey` 自含租户标识）。契约写入 javadoc。
- **R9 验证补齐**：新增 `StaticRoutePathPatternTest`（springboot 测试域补 `spring-webflux` 测试依赖）：`{*path}` 匹配多段与 `/static` 前缀、**`/static/{*path}/` 确实解析失败**（证明「跳过尾斜杠变体」守卫的必要性）、旧 `{path}` 确实漏多段路径。
- **`MODULES.md` 新增 §2.8**：starter 聚合清单（**13 个**模块）+ 依赖方向（`auth-session → auth + session`、`session → core + http`、`auth` 不依赖 `session`）+ 对未跟踪文档 `Spring优化方案.md` 的「已失效、以本文件为准」声明（该文件顶部亦已加失效横幅）。
- **`wechat-sdk-demo`**：依赖保持 0.1.3（重新 install 后即为新代码）；`plainConfig()` 显式开启 `verifyPostSignature`（SDK 默认已改为 `false`，严格用例必须自行开启，否则测的是另一条语义）。
- **本轮已验证**：`StorageResponseVisibilityTest`（4 例：private→`no-store`、public→`max-age`、可见性异常→仍 `no-store`、缺失→404）、`LocalFilesystemRootGuardTest`（7 例：根别名拒绝（`""`/`"/"`/`"."`/`"a/.."`）× delete/copy/move/deleteDirectory、父目录穿越、正常操作不受影响、**root 自身为 junction 时正常读写**、**根内 junction 指向外部被拒** —— junction 用例本机实跑通过）、`ModelCacheTypeCoercionTest`（6 例）、`CaptchaVerifyMisuseGuardTest`（4 例：**2 参裸类型名不抛异常**（终局评审复现的可用性缺陷已修）、3/4 参写反抛可操作异常、畸形 key 只失败不抛、正常两参可用）、`MigrationParserFailureTest`（4 例：目录缺失/无 `.java` 为合法回退不误报、`up()` 抛异常被记录且含类名、失败清单每次重置）。
- **aether 末片重放防护已实测**：新增 `AetherUploadReplayGuardTest`（2 例，**0 跳过**）—— 用「`put` 抛异常」的 `CacheStore` 替身在**末片写入前**武装，精确复现「成品已 move、`saveHeader` 失败」；断言重试末片被 `UploadException` 拒绝、且**成品 SHA-256 不变**（M6 的零填充覆盖路径已被真实拦住）；另断言随机 resourceId 循环只抛「header 不存在」（定长分段锁不无界增长）。
- **唯一剩余验证项（已登记）**：**M20 并发首查/稳态零写入的自动化用例**未落地 —— 构造可查询的 `BaseModel` 夹具需要 gaarason 模型初始化与受保护成员的访问链，容易写成脆弱用例；实现侧（同步化 `WeakHashMap` + 登记后移）已由三位专家逐行复核确认在位，残余首次并发窗口亦已在 javadoc 与 CHANGELOG 如实记录。
- **`DefaultAppKey.isTemporary()`**：该文件被系统拒绝写入（`fchmod EPERM` / Access denied），未加标记方法；改由 AppKey 生产点读原始 `jaravel.key` 判定（架构师确认机制等价且无误报，并指出影响面覆盖 captcha/wire/cookie/jwt 四处）。

### Fixed（修复 · 第九轮：审计剩余项 + 专家团第二轮新发现）

- **L4（按审计原文）+ N2/N3（安全）**：`Storage.response()` 现按 `fs.visibility(path)` 决定缓存语义 —— 私有文件下发 `Cache-Control: private, no-store`（原先一律 `public, max-age=3600`，经 CDN/共享代理会缓存给他人）；内联展示与下载响应统一补 `X-Content-Type-Options: nosniff`（防 `.html`/`.svg` 上传后的同源存储型 XSS）；`Content-Disposition` 增加 CR/LF/引号清洗与 RFC 5987 `filename*`（中文名不再乱码，且无法注入响应头）。
- **M1 CSRF（安全）**：令牌只接受请求头与请求体字段（改用 `Request.input()`，不再用会合并 query 的 `get()`）；移除同名 Cookie 兜底（否则「双提交」退化为「浏览器自动携带即通过」）；比较改 `MessageDigest.isEqual`（常量时间）；query 令牌由显式开关 `allowTokenInQuery()`（默认 false）控制。新增 `VerifyCsrfTokenTest`（8 例）—— 此前本仓库 <b>0 个 CSRF 测试</b>。
- **M5 验证码一次性（安全）**：`CaptchaStore` 新增 `default putIfAbsent(...)`（不使用抽象方法，避免破坏第三方 SPI）；`MemoryCaptchaStore`（`compute` 原子）与 `CacheStoreCaptchaStore`（`CacheStore.add`）提供真原子实现；`AbstractCaptcha` 改为「先原子占用、成功才解密/比对」，占用即不释放（保持「失败也烧 nonce」）。新增 `CaptchaNonceAtomicityTest`（3 例：16 线程并发验证恰好 1 次通过、内存 store 并发占用唯一胜出、失败尝试烧 nonce）。
- **M4/N1/N8 验证码加解密（安全）**：加密失败不再下发 `type.null` 凭证（显式抛错，附可操作提示）；`encryptionType=none` 首次使用 WARN（答案随 token 明文下发、可自造 token）；`properties==null` 不再静默降级为 none。
- **L5 JWT 签发方（安全）**：`parse()` 现校验 `iss` —— 缺失或不匹配都拒绝（未配置 issuer 时跳过校验以兼容外部令牌）。新增 `JwtIssuerValidationTest`（4 例）。说明：框架**没有** `aud` 字段、签发端也从未写入 aud，故本轮不做受众校验（只加校验端会拒掉全部自签令牌）。
- **M15/L7 wire 快照（安全）**：改为**先验签、后解析**（原先先把攻击者可控 base64 交给 JSON 解析器）；会话不可用时不再返回每次随机的 `fallback-key-<uuid>`（那会导致签名恒不通过、全量 403 且无日志），改为回退稳定的全局应用密钥，两者都不可用时抛明确异常。`WireRequest.getData()/getMergedData()` 标记 `@Deprecated`（绕过签名校验，审计 N5）。
- **M6/N6 aether（安全）**：写分片前校验临时文件存在且长度等于声明大小（`prepare` 已 `setLength(size)`）—— 堵住「成品已落盘、`saveHeader` 失败 → 客户端重试末片 → 新建零填充文件覆盖正确成品」的数据损坏路径；锁表由「按 resourceId 的 ConcurrentHashMap」（header 不存在时条目在校验前创建且永不回收 → 匿名随机 resourceId 可致无界内存 DoS）改为**定长 256 桶分段锁**，内存 O(1) 且无需生命周期簿记。
- **M20 BaseModel 共享元数据（安全）**：`patchShadowColumn` 改为「按 `EntityMember` 实例、受锁、只修补一次」，使查询热路径零写入（原先每次 `getModelMember()` 都对 gaarason 容器级共享的 `selectColumnList` 做 `removeIf`，启动后首批并发查询可能 CME）。**已知残余**：首次并发窗口仍在（仓库无模型注册表，无法在启动期单线程枚举所有实体），已在 javadoc 如实记录，不宣称已消除。
- **M3 路由（安全）**：中间件解析顺序改为**父级在外、子级在内**（对齐 Laravel 洋葱模型；原先全局最内层，会让「根挂 `EncryptCookies`、组挂 `VerifyCsrfToken`」变成先验 CSRF 再解密 Cookie → 恒定 419）；静态资源路由由 `{path}`（只匹配单段，嵌套资源恒 404）改为 `{*path}`（capture-the-rest），并同步修正 Spring 谓词：URI 含 `{*` 时不再拼接尾斜杠变体（`/static/{*path}/` 违反 PathPattern「capture-the-rest 必须在末尾」语法，有启动失败风险）。新增 `RouterMiddlewareOrderTest`（5 例：三层顺序、enter/exit 洋葱序、两条静态路由使用 `{*path}`）。
- **L2/L3 路径越界（安全）**：`resolve()` 增加**真实路径**归属校验（与 `root.toRealPath()` 比较，目标不存在时上溯到最近存在的祖先）—— 词法 `normalize` 挡不住「根内软链接指向外部」；`delete/copy/move` 统一拒绝「解析后等于根目录」的路径（`""`/`"."`/`"a/.."` 都会解析成根，原先 `delete("")` 会删掉整个磁盘根）。**残余 TOCTOU 已记录**（校验后仍以词法路径打开），不宣称「穿越已彻底修复」。
- **L11 backUrl（正确性）**：组件渲染循环改用非破坏性 `WireEffects.getBackUrl()`，主流程保持单次 `drainBackUrl()`（原先渲染先 drain → `effects.backUrl` 退化为推断值，多组件时只有一个拿到 backUrl）。
- **auth 装配（架构师建议）**：空守卫兜底只覆盖「默认驱动（`session`）缺实现」这一情形（auth 独立可用）；显式声明了无人支持的 driver（拼写错误/忘引模块）**仍然抛异常** —— 否则会静默变成「恒未登录」，让 `if (Auth.check())` 这类写法静默失去鉴权。
- **该清单项已全部完成**：M19 收窄严格化 + 命令非零退出（保留「目录缺失 → 回退 classpath」合法路径，`MigrationParserFailureTest` 4 例）；N1 启动期密钥形态校验（分档 + `fail-fast-on-invalid-key`，`CaptchaKeyShapeValidationTest` 7 例）；M17 撤销弃用改语义护栏 + README 修正（`CaptchaVerifyMisuseGuardTest` 4 例）；N4 `refreshPair` 可选轮换（`refresh()` 语义不变）；M16/N7 元素级还原 + 驱逐回源 + 非内存 store 告警（`ModelCacheTypeCoercionTest` 6 例）；`Storage.response` visibility（4 例）、aether `.part` 故障注入（2 例，0 跳过）、LocalFilesystem 根目录/链接（7 例）、Spring `{*path}` 谓词（4 例）。

### Fixed（修复 · 第八轮：审计低危项小修）

- **`json`：`Jackson2JsonCodec(ObjectMapper)` 不再修改调用方的 mapper（审计 L9）**：原构造器直接 `configure(FAIL_ON_EMPTY_BEANS,false)`，而传入的通常是 Spring 容器里的<b>共享 Bean</b>，会静默改变整个应用的序列化行为；现改为 `mapper.copy()` 后配置。
- **`database`：`ConnectionManager` 默认连接选举移入锁内（审计 L10）**：原选举赋值在 `synchronized (CONNECTIONS)` 之外，并发注册时两个线程可能都读到 `first=true`（或都读 false）而选错默认连接；同时把 `RAW_DATA_SOURCES` 的写入一并纳入同一临界区（`defaultConnection` / `defaultExplicitlySet` 已是 volatile）。
- **`migration`：`MigrationRepository.getLastBatchNumber()` 不再吞掉所有异常（审计 L8）**：原先 `catch (Exception) { return null; }` 会把权限/连接/语法等真实故障当成「首次运行、批次号 0」继续跑迁移并静默产出错误结果；现只对「表不存在」（按各库措辞识别，含 `no such table` / `invalid object name` / `ORA-00942` 等）返回 null，其余抛出。
- **`migration`：`SqlServerDialect.upsertSql` 的 javadoc 归位（审计 L12）**：原先该方法上方挂的是 `renameTableSql` 的说明（「生成重命名表的 SQL … sp_rename」），现已分别补上正确的 UPSERT 与 RENAME 文档。
- **`wire`：`getWireJsContent()` 改用 `readAllBytes()（审计 L6）`**：原先用 `new byte[is.available()]` 定长读取，而 `available()` 只是估计值（jar/压缩流下可能远小于实际长度），会把 `wire.js` 截断成语法错误的前端脚本且极难定位。

### Fixed（修复 · 第七轮：受信任代理与 wire 请求态）

- **受信任代理（审计 S3，严重）**：`Request.ip()` 原先<b>无条件</b>采信 `X-Forwarded-For` 并取<b>最左侧</b>值 —— 任何客户端自己塞一个 `X-Forwarded-For: 1.2.3.4` 就能冒充来源 IP（限流/审计/白名单全部失真）；`fullUrl()` 同样无条件采信 `X-Forwarded-Proto/Host`（可伪造协议与 Host，影响回调地址、跳转、Cookie 域）。现在：
  - `TrustProxies` 中间件在处理请求时发布<b>全局受信任代理匹配器</b>，并提供 `isTrustedRemote(addr)`；
  - `Request.ip()` / `fullUrl()` <b>仅当直连来源已被声明为受信任代理</b>时才采信转发头，否则一律使用 TCP 直连地址与请求自身的协议/Host；未安装 `TrustProxies` 时<b>恒不采信</b>（安全默认）；
  - 转发链改为<b>从右往左</b>解析：跳过受信任代理、取第一个不受信任的地址（旧实现取最左侧，正是客户端可伪造的那一端）；`setTrustedHeaders` 同步修正。
  - 新增 `TrustedProxyResolutionTest`（6 例：默认忽略转发头、非受信任来源忽略、受信任来源采信、伪造最左侧被拒、协议/Host 还原受控）；`http` 测试域补 Mockito。
  - 行为变更提示：部署在反向代理后的应用需要挂 `TrustProxies`（并声明代理网段），否则 `ip()` 会返回代理地址。
- **`wire` 请求态不再存实例字段（审计 S10，严重）**：`WireController.currentRequest` 原为**实例字段**，而控制器实例会被跨请求复用 → 并发请求互相覆盖，表现为「A 用户的 action 看到 B 用户的请求」（跨用户状态串读）。现改为请求级 `ThreadLocal` + `currentRequest()` 访问器，并在 `index()` / `update()` 的 `finally` 中与 `WireEffects`/`WIRE_LAYOUT_REPLACEMENTS` 一起清理；`isWireRequest()` 等内部读取点全部改用访问器。

### Added（新增 · 第六轮：JdbcExecutor 事务原语与数据库驱动的原子语义）

- **`database`：新增 `JdbcExecutor.inTransaction(...)` 事务原语（用户要求）**。此前 `update`/`queryMapped` 等方法各自从 `DataSource` 取一次连接（连接池下可能是不同连接），因此「先查后改」在多实例并发下不是原子的，`SELECT ... FOR UPDATE` 也无法生效（行锁属于连接/事务）。现在提供：
  - `inTransaction(TransactionWork<T>)`：同一连接、关闭自动提交、成功提交、异常回滚（运行时异常原样抛出，不包装掩盖）、`finally` 恢复 autoCommit 并归还连接；
  - 事务句柄 `Tx`：`execute` / `update` / `insertReturningKey` / `queryMapped` / `queryForList` / `queryForObject` 全部绑定同一连接，并暴露 `connection()` 供 `FOR UPDATE SKIP LOCKED` 等方言特化语句使用；
  - 新增 `JdbcExecutorTransactionTest`（7 例：提交可见、异常回滚、事务内可见自己的未提交写入、返回值、条件更新抢占模式、拿到底层连接、事务后执行器仍可用）。
- **`cache-database`：实现 `AtomicCacheDriver` 真原子原语**（原先 `add`/`pull` 是「先查后写 / 先读后删」两步，并发下会静默失效）：
  - `addIfAbsent`：事务 + **主键唯一约束**兜底并发（并发 add 只有一个成功，恢复互斥门闩语义），已过期条目视为不存在；
  - `pullValue`：事务内 `SELECT ... FOR UPDATE` + `DELETE`（一次性令牌只被一个线程取到；SQLite 不支持 `FOR UPDATE`，退化为同事务内「读+删」并在文档声明）；
  - `incrementAndGet`：**乐观 CAS + 时间预算**（读值→计算→`WHERE value = 读到的原值` 条件更新，失败重读重算），**保留原 `expires_at`**；预算内始终失败时**抛错而不是静默返回 0**（绝不静默丢一次自增）。实测发现 H2 MVStore 下 `FOR UPDATE` 不足以防丢更新，故改用与方言无关的 CAS；
  - 新增 `DatabaseCacheAtomicityTest`（7 例：并发 add 唯一胜出、并发 pull 唯一取到、并发自增不丢更新、过期为不存在、不覆盖已有值、自增保留 TTL、新键带 TTL）。
- **`storage-database`：`put` 事务化**：「清旧分片 + 清旧元信息 + 写全部分片 + 写元信息」收进同一事务，原实现中途失败会留下「元信息已删、分片只写一半」的半截文件。
- **`queue-database`：抢占切换为方言感知的 `SKIP LOCKED`（本轮完成，替代原先登记的「未完成」项）**：`pop` 优先走「`SELECT … FOR UPDATE SKIP LOCKED` + 同一事务内 UPDATE」的无竞争抢占（依赖上一项的 `JdbcExecutor.inTransaction`），候选行被锁住时其他实例直接跳过，不再有竞争重试与空转；按数据库产品名生成方言语句：MySQL/MariaDB/PostgreSQL/H2 用 `LIMIT 1 FOR UPDATE SKIP LOCKED`、Oracle 用 `FETCH FIRST 1 ROWS ONLY FOR UPDATE SKIP LOCKED`、SQL Server 用 `TOP 1 … WITH (UPDLOCK, READPAST)`；**SQLite 与未知方言返回 null 直接走乐观锁**（该路径在任何标准 SQL 库上都正确，宁可少一层优化也不要不可预期报错），方言判断失误时运行期也会降级并告警。新增 `DatabaseQueueClaimSqlTest`（6 例方言矩阵：各方言 SQL 形状 + 未知方言降级 + 自定义表名）；H2 上 8 线程 × 40 任务并发用例实测走 SKIP LOCKED（日志无降级告警）且无重复预约。
- **`core/queue/QueueDriver` 补齐逐方法契约文档**（架构师评审指出「接口逐方法零 javadoc」正是「javadoc 说 SKIP LOCKED 而实现没有」这类文档漂移的根因）：明确 `push`/`pop`/`delete`/`release` 的语义、**投递保证为「至少一次」且消费方必须幂等**、`pop` 返回 null 只应表示「没有可执行任务」而不得混入抢占竞争失败、以及 `size()` 的口径（只统计就绪任务，不含延迟与预约中，驱动间可能略有差异）。

### Changed（变更 · 第五轮：auth/session 模块拆分 + 归属校验 + wechat 语义调整）

- **auth ↔ session 解耦（结构性拆分，用户要求）**：新增两个模块，形成「标准 / 实现」的清晰边界 ——
  - **`session`**（新）：Session 存储<b>标准</b>：`SessionStore` 契约、`CookieSessionStore`（HttpSession 默认实现）、`@RegisterSessionStore` 声明式注册与 `SessionStoreHolder`（全局持有者 + 惰性回退）；原先这些类在 `http` 模块的 `http.session` 包内。
  - **`auth-session`**（新）：把 auth 标准落到 session 存储上的<b>一种实现</b>：`SessionGuard` / `SessionGuardDriver`（原先在 `auth` 模块的 `auth.guard` 包内）。
  - **`auth`**：只保留「认证标准」——`AuthManager`、`AuthGuard`/`AuthGuardDriver`/`UserProvider` 契约、注解声明式注册、以及**自带的空守卫兜底**（`NullGuard` + `NullGuardDriver`）：遍历完所有驱动都没有匹配时不再抛 `IllegalStateException`，而是回退到空守卫（`check()` 恒 false）并告警一次 —— 于是「只依赖 auth、不依赖 session」的应用也能正常装配。`NullGuardDriver.support` 刻意恒为 false，不会与 session/jwt 争抢匹配。
  - **装配**：`springboot` 新增 `SessionGuardAutoConfiguration`（**类级** `@ConditionalOnClass` 守卫 `SessionStoreHolder`/`SessionGuardDriver`：缺这两个模块时整类不加载，auth 仍可用）；`HttpSessionAutoConfiguration` 从 `http` 迁到 `springboot.session` 并**补上注册**（此前它未被任何 imports 文件登记，等于死配置 —— 顺带修复 `@RegisterSessionStore` 扫描从未生效的问题）。
  - **`starter`** 聚合 `auth` + `auth-session` + `session`（与拆分前行为一致）；`session-redis` 去掉 `auth` 依赖（原为零 import 的倒挂边）改为依赖 `session`；`wechat-sdk` 补 `session` 依赖（OAuth 的 session 便捷接口）。
  - **测试去 session 化**：`AuthManagerTest` 改用自带的 `TestGuard`（不再 import SessionStore/SessionGuard），从而「auth 的测试不需要 session」；`SessionGuardTest` 迁至 `auth-session`；新增 `SessionStoreHolderTest`（**重点锁定 holder 必须转发 `rotate()`** —— 否则接口默认空实现会静默吞掉会话固定防护）。
- **`aether-upload` identifier 归属校验（越权修复，专家团标为 High；该模块尚未被业务使用，故直接改契约）**：identifier 由前端按「文件名+大小+mtime」可预测拼出，原先作为全局键使用 → 知道三要素即可读他人进度、向他人 resourceId 写分片、甚至决定受害者成品内容。现在：①identifier 键按**主体作用域**隔离（`i:<group>:<owner>:<identifier>`）；②`UploadHeader` 记录 `ownerId`，`writeChunk`/`progress`/`abort` 对**跨主体**访问一律拒绝；③**匿名默认禁用 identifier 续传**（新配置 `anonymous-resume-enabled`，默认 false），登录用户不受影响；④新增 `setOwnerResolver(...)` SPI，springboot 侧从认证上下文取用户 id（未引入 auth 时按匿名处理，用反射探测避免强依赖）。新增 `AetherUploadOwnershipTest`（5 例：跨主体不共享续传任务、跨主体写分片/读进度/中止被拒、同主体正常）。
- **`wechat-sdk` 明文 POST 验签语义按用户要求改为「开关关闭即完全不验」**：`verify-post-signature=false`（默认）时缺失或错误的 `signature`<b>都放行</b>；`true` 时缺失或不匹配都拒绝。GET 接入校验与 `safe` 模式的 `msg_signature` 仍恒校验（不受本项影响）。默认值与两种语义均有用例锁定。

### Fixed（修复 · 第四轮：会话固定与 Session 存储加固）

- **会话固定防护（CWE-384，安全角色标为 High）**：`SessionStore` 契约新增 `rotate()`（默认空实现以保持第三方实现源码兼容），内置实现全部覆盖：`CookieSessionStore` 使旧会话失效（容器随后下发新的 JSESSIONID）、`RedisSessionStore` 把数据搬到新 ID + 删除旧 ID + **同时更新请求与响应 Cookie**（只更新响应会导致同一请求内后续仍读到旧 ID，登录态被写进刚删除的旧 key）。`SessionGuard.login` 现在**先 `rotate()` 再写入登录态**（顺序颠倒会让登录立刻失效），并对「未覆盖 `rotate()` 的实现」打印一次性告警，避免防护被静默跳过。新增 `SessionGuardTest` 用例固定「rotate → put」顺序与「logout 不轮换」。
- **Session ID 格式白名单**：`RedisSessionStore` 的 Cookie 值会被直接拼进 Redis 键，原先无任何校验（可提交含空白/路径分隔符/超长内容的任意串）。现要求 `[A-Za-z0-9_-]{16,128}`，非法值按「无会话」处理（对齐 Laravel 的 `^[a-zA-Z0-9,-]{22,250}$` 形状约束）。
- **Session Cookie 属性与登出清理**：会话 Cookie 补 `SameSite=Lax`（跨站请求不携带会话 Cookie，降低 CSRF 面）；`destroy()` 除删除服务端数据外，还会下发 `Max-Age=0` 的失效 Cookie 并清空请求侧取值 —— 原先退出后浏览器仍会继续携带同一个 session id。新增白名单正/反用例（含路径穿越字符、空白、换行、非 ASCII、超长）。

### Fixed（修复 · 第三轮：队列与缓存原子性）

- **`RedisQueueDriver` 迁移/领取/释放原子化 + 索引 O(1)（审计 M12/M13，此前零测试）**：①到期延迟任务与超时预约任务的迁移由「`ZREM` 后再 `LPUSH`」两步改为 **Lua 脚本**（每个成员的 `ZREM`+`LPUSH` 在同一脚本内原子完成）—— 原实现若进程死在两步之间，任务已从 ZSET 移除却未进就绪队列 → **永久丢失**；②领取由「`RPOP` 后再 `ZADD`」改为 **Lua 领取脚本**，避免「已取出未预约」时崩溃丢任务，随后用第二个脚本**原子替换**预约成员（写入递增后的 attempts/reservedAt）并刷新索引 —— 崩溃时原始成员仍在预约集合，语义是「至少一次」而非丢失；③`release` 同样改为单个 Lua 脚本（`ZREM` + 入队 + 刷新索引）；④索引值由「只存队列名」改为 **`queue<US>member`**，`delete/release` 变为 O(1)（原实现每次 `ZRANGE 0 -1` 全量扫描并逐条反序列化，是热路径 O(N)），旧格式索引仍回退到扫描以兼容滚动升级；⑤`push` 改为**先写索引再入队**（后者最坏留一条无害孤儿索引，前者会导致定位不到队列）；⑥索引缺失不再静默 debug，改为告警并说明任务将由超时迁移重新投递。新增 `RedisQueueDriverTest`（8 例，Mockito mock `RedisCommands`；含「新格式不再扫描」「旧格式回退扫描」「迁移/领取走 eval 且不再用两步命令」等断言）。**Lua 的原子语义本身需真实 Redis 才能验证，已列为未验证项。**
- **`cache` 原子原语（审计 M9 的原子性部分）**：新增可选能力接口 `AtomicCacheDriver`（`addIfAbsent` / `pullValue` / `incrementAndGet`），内存驱动与 Redis 驱动实现，`DefaultCacheStore` 优先走原子路径：
  - 内存侧：`SimpleMemoryCache.add` 由 `exists→put` 改为 `ConcurrentHashMap.compute`（并发 add 只有一个成功，恢复「互斥门闩」语义）；`increment` 由读改写改为 `compute`（**消除丢失更新**并保留 `expiryAt`），新增带「键不存在时 TTL」的重载；
  - Redis 侧：`addIfAbsent` 用 `SET NX [EX]`、`pullValue` 用 Lua `GET+DEL`（兼容 Redis 6.2 以下，不依赖 `GETDEL`）、`incrementAndGet` 用 `INCRBY`（原子且**原生保留 TTL**），键不存在且要求 TTL 时先 `SET NX EX` 建 0；
  - `add`/`pull` 在无能力驱动上仍是两步实现，已按「尽力而为」写入接口契约，绝不假装原子；数据库驱动的原子 add/pull 依赖 `JdbcExecutor.inTransaction`（待办）。
  - 新增 `CacheAtomicityTest`（6 例：并发 add 唯一胜出、并发 pull 唯一取到、并发自增减不丢更新、原子自增仍保留 TTL），`RedisCacheDriverTest` 增补 5 例。

### Fixed（修复 · 第二轮：专家团评审后落地）

- **`wechat-sdk` 明文推送验签语义调整（用户要求「不强制」，但不做成摆设）**：`verify-post-signature` 默认由 `true` 改为 **`false`**（不强制要求携带签名），但**只要请求带了 `signature` 就必须验过**（不匹配即拒绝）。理由：若「默认关闭且带了也不验」，攻击者只需省略签名即可绕过 —— 来源真实性控制形同虚设；而微信自身推送是带签名的（测试号真机联调在 `verify=true` 下通过即为证据），因此该调整对线上流量仍是实际验签，只对未签名的内部调用/调试放行。同步：GET 接入校验与安全模式 `msg_signature` **恒校验**（不受本开关影响，已加回归用例固定）；未携带签名的推送只告警**一次**（原每请求 WARN 会造成日志放大）；`WxBizMsgCrypt.verifyPlainSignature` 改用 `MessageDigest.isEqual` 常量时间比较（原 `equalsIgnoreCase` 短路比较，CWE-208）。
- **`EncryptCookies` 增加旧格式（v1）兼容开关（用户要求），默认仍安全**：新增 `protected boolean allowLegacyFormat()`（默认 `false`）、`protected String[] legacyFormatCookieNames()`（默认空；`"*"` 表示通配）与严格结构校验的 `decryptLegacy(...)`（Base64 + 长度/块对齐 + PKCS#5 + **严格 UTF-8** + 独立的 v1 密钥派生，不能复用 v2 的 SHA-256 派生）。行为：仅当显式开启且命中白名单、且名字非安全敏感（`*session*`/`*token*`/`*login*`/`XSRF-*`/`X-*`/`__Host*`/`__Secure*`/`JSESSIONID` 一律硬拒绝）时才按旧格式读取；**出站一律写 v2**（只读旧、写新）；失败仍然「失败即丢弃」，绝不保留请求原值（兼容模式下同样如此，已用例固定）。安全代价已写明：v1 无 MAC，兼容期**无法检测 CBC 比特翻转**。**本开关不做硬退场**：开启即长期兼容，由使用方按自身迁移节奏决定何时关闭（框架不会因版本升级而自动失效）。
- **`http` 新增 `EncryptCookiesTest`（19 例）**：此前该安全关键中间件<b>零测试覆盖</b>（专家团一致标为阻断项）。覆盖明文冒充必须移除（v2 与兼容模式各一条）、v2 往返（含 UTF-8）、随机 IV、HMAC 篡改检出、非法 Base64/长度、排除名单透传、出站原地替换且一律 v2、加密失败置空不下发明文、兼容白名单/通配/安全敏感名/非 UTF-8/结构非法等分支。
- **`aether-upload` 分片 DoS 与溢出（审计 S8）**：①`UploadHeader.uploadedChunkList()` 把位图提取到循环外（原循环内每次 `new byte[(totalChunks+7)/8]`，`totalChunks=1e6` 实测 21.4 秒、1e8 超过 120 秒不返回；空位图直接 O(位图) 返回）；②`prepare` 用 `long` 计算分片数并校验上限后再收窄（原 `(int)` 在 `size=8e9&chunkSize=1` 时得到负数 → 状态机失效、上传永不完成），且**校验全部通过后才创建临时文件**（拒绝路径不产生 `.part`）；③新增 `max-chunks`（默认 10 万，超限时**自动放大分片**而不是拒绝大文件）与 `min/max-chunk-size`（默认 1 字节 / 10MB，客户端值钳制后**回显**，对既有前端透明）；④`tempDir` 增加机会式清理（限频 10 分钟、按记录头 TTL 判定），原实现 `.part` 无任何回收；⑤新增「既未配 `max-size` 又未挂中间件」的一次性告警（该组合下 prepare/chunk 可被匿名调用并预分配磁盘）。新增 `AetherUploadChunkLimitTest`（6 例，含 8e9 溢出等价路径，并保持既有 4 字节分片契约不变）。
- **`cache` 自增/自减不再抹掉 TTL（审计 M9，已实测复现）**：新增可选能力接口 `TtlAwareCacheDriver`（三态 `OptionalLong`：empty=键不存在或不可判定 / 0=永不过期 / 正数=剩余秒数），`ArrayCacheDriver`（含 `SimpleMemoryCache.remainingTtlSeconds`）与 `RedisCacheDriver`（`TTL` 命令）实现；`DefaultCacheStore.increment/decrement` 按原键剩余 TTL 写回。**明确不做「退化为短 TTL」**：那会让限流计数/缓存版本键中途重置 —— 例如 `model-cache` 版本键过期会让旧版本缓存条目集体复活，属数据正确性问题；驱动不支持该能力时按无 TTL 写入并**每驱动类告警一次**。`CacheStore` 补齐 TTL 与「原子性为尽力而为」的契约文档。新增 `CacheTtlPreservationTest`（7 例，含非能力驱动回退、真实过期、反复自增不延长 TTL）。
- **`auth` 与 `session` 解耦（用户要求「auth 是标准，session 才是其中一种实现」）**：①删除 `session-redis → auth` 的**倒挂依赖**（该模块源码与测试对 `vendor.auth` 的 import 命中数为 0，属纯多余依赖边）；②把 `AuthRegistrar` 里写死的 `"session"` 兜底改为可配置项 `jaravel.auth.fallback-driver`（默认仍 `session`，**默认行为不变**）—— 「auth 默认绑定 session」不再是一段隐式代码而是显式配置；③修正 `SessionStore` 中指向 `auth.AuthGuardDriver` 的失效 `{@link}`（真实类型在 `auth.contract`），并补上「auth 是契约、session 存储只是实现之一」的定位说明。可插拔机制此前已具备（`AuthGuardDriver` SPI + `@RegisterGuard` + 独立 `jwt` 模块以及 `AuthManagerTest` 中不使用任何 Session 存储的自定义驱动用例），本次消除的是「实现归属与默认绑定」上的耦合。
- **`queue-database` 抢占可移植性、竞争语义与背压（审计 M10/M11）**：①`DatabaseQueueDriver.pop` 由 `ORDER BY id ASC LIMIT 1`（Oracle/SQL Server 不合法）改为可移植的 `id = (SELECT MIN(id) ...)`，并**移除 javadoc 中不实的 `FOR UPDATE SKIP LOCKED` 承诺**（该实现要求 SELECT/UPDATE 同事务，而 `JdbcExecutor` 尚无事务 API）—— 文档现在如实写明「乐观锁预约 + 至少一次投递 + 消费方须幂等」；②抢锁失败不再直接 `return null`（上层会当成空队列 sleep → 高并发空转/延迟），改为最多 3 次退避重试；③`DatabaseQueueWorker` 由无界 `newFixedThreadPool` 改为 `ThreadPoolExecutor + ArrayBlockingQueue` 并用 `Semaphore` 先取许可再 `pop`，保证「已 reserve 但未执行」的任务数有上界；④`executeJob` 的 catch 内再调 `release/fail` 时补一层 try/catch + ERROR 日志（原异常会逃进被丢弃返回值的 Future 而被静默吞掉）。新增 `DatabaseQueueConcurrencyTest`（8 线程 × 40 任务：同一 jobId 不得被重复预约、任务最终全部取出）。

### Fixed（修复）

- **`storage-database` 目录删除会误伤同层其它目录（不可恢复）**：`DatabaseFilesystem` 的 `path LIKE ?` 未转义 LIKE 元字符，目录名里的 `_`/`%` 会被当通配符 —— `deleteDirectory("user_files")` 会连带删掉 `userXfiles/…` 的元信息与分片。现已转义并统一加 `ESCAPE '\'`（`queryPaths`/`deleteDirectory`/`hasAnyUnder` 三处）；同时修掉 `FileMeta.updatedPresent` 把 `null` 传给 `boolean` 形参导致的拆箱 `NullPointerException`（`updated_at` 是可空列），改为可空 `Long updatedAt`。
- **`wire` 请求级效果状态泄漏与静态可变状态竞争**：`WireController.update()` 的 `finally` 原先只清 `WIRE_LAYOUT_REPLACEMENTS`，另外 5 个效果队列（组件/dispatch/redirect/pushUrl/backUrl）只在成功路径 drain —— 异常与早退路径会把它们留给同线程的下一个请求（残留 `redirect` 可把另一个用户的浏览器跳到上一个用户指定的地址）。现在无条件调用 `WireEffects.clear()`，且 `clear()` 由「清空列表」改为 `ThreadLocal.remove()`（连线程上挂着的 List 实例一并释放）；`WireManager.engine` 补 `volatile`、`excludedSections` 改用 `ConcurrentHashMap.newKeySet()`（原先 `LinkedHashSet` 被多请求线程并发读写）。
- **自动装配启动阻断（`springboot` 模块，实测复现）**：`@ConditionalOnClass` 挂在方法上无法保护「声明类自身的类加载」，导致缺 optional 模块时应用直接启动失败。已修：
  - `SpringBootRouteAutoConfiguration` 中形参引用 `auth.AuthManager` 的 bean 移入**带类级条件**的内部配置类（缺 auth 时启动不再 `NoClassDefFoundError`）；
  - `DatabaseAutoConfiguration` / `ViewAutoConfiguration` / `MigrationPublishAutoConfiguration` / `SchedulePublishAutoConfiguration` 补**类级** `@ConditionalOnClass`（FQCN 字符串形式）；`ViewAutoConfiguration`、`RedisCachePublishAutoConfiguration` 由 `@Configuration` 归正为 `@AutoConfiguration`；
  - `jaravelRouterFunction` 在**零路由**时不再调用 `RouterFunctions.Builder#build()`（原会抛 `IllegalStateException("No routes registered.")` 使应用起不来），改为返回空 `RouterFunction`；
  - `AutoConfiguration.imports` 第 22 行类名里的反斜杠改为点号（字节级缺陷，Windows 下侥幸可解析、跨平台会失效）；
  - 新增 `AutoConfigurationGuardSmokeTest`（6 例）：用自定义 ClassLoader 隐藏 optional 包 + 反射断言外层配置类签名不引用 optional 类型。
- **`EncryptCookies` 安全重写（严重）**：①解密/验签失败**移除** Cookie（旧行为保留攻击者提供的原值，发一个明文 Cookie 即被当明文接受）；②IV 改为 `SecureRandom` 随机（旧实现恒为全零 → 确定性密文）；③新增 **HMAC-SHA256（encrypt-then-MAC）** 与先验签后解密（仅有 CBC 时可被比特翻转篡改）；④密钥改为 `SHA-256` 派生、加解密与 MAC 用两把派生密钥；⑤出站**原地替换** Cookie 值，不再同名双下发；⑥线格式升级为 `v2:` 前缀，旧格式一律丢弃（升级后用户会掉一次登录态）。
- **请求上下文 ThreadLocal 泄漏（严重，跨用户会话串读）**：`RequestFactory.buildFromHttpServletRequest` 现在也会 `setCurrentRequest(...)`（原先只有 jaravel 路由路径会 set），并新增 `JaravelRequestContextFilter`（最高优先级）在请求结束时 `clearCurrentRequest()`。
- **验证码（严重）**：①`encKey` 不再下发「能解密 captchaKey 的密钥」——captchaKey 内含答案，拿到它即可绕过；对称模式改为下发**一次性输入密钥**（真实密钥留在 token 内），非对称模式**只下发公钥**；②`RotateCaptcha` 除 NaN 外新增拒绝 `±Infinity`（提交 `1e999` 会让角度差变成 `-Infinity` 从而通过校验）；③新增 `CaptchaSecurityTest`（4 例）锁定以上行为。
- **Redis 缓存 `flush` 清空整库（严重，不可恢复）**：`RedisCacheDriver` 为所有键加命名空间前缀（`jaravel.redis.prefix`，未配置回退 `jaravel:cache:`），`allKeys()`/`removeAll()` 改为 SCAN + `MATCH <prefix>*`；`allKeys()` 按契约返回去掉前缀的逻辑键。
- **Redis 分布式锁属主校验（严重）**：`RedisLockProviderImpl.unlock` 由无条件 `DEL` 改为 **Lua compare-and-delete** —— 只删除本实例加过且值未被替换的锁；未持有锁时直接跳过（原实现会删掉别人的锁，导致同一定时任务多实例并发）。
- **定时任务锁（严重）**：`ScheduleRunner` 抢锁失败时不再在 `finally` 里 `unlock`（原会把获胜者的锁删掉）。
- **迁移预编译产物恒为空（严重）**：`MigrationPrecompiler.compileMigrationFiles` 改为复制 `MigrationScanner#getCompiledClasses()` 的返回值 —— 原先拿到内部 Map 本体，紧接着 `finish()` 会 `clear()` 掉它，导致预编译 zip/目录只有 manifest、编译计数为 0、迁移静默不执行且退出码为 0。
- **wechat-sdk（真机联调暴露）**：①明文模式接入校验按官方**三参数** `sha1(sort(token,timestamp,nonce))` 验签（原把 echostr 计入 sha1 → 微信「服务器配置」必然失败）；②明文模式 POST 也校验 `signature`（原先完全不验签，任何人可伪造推送），并新增 `verify-post-signature` 开关（默认 true）；③`WxBizMsgCrypt.decrypt` 填充非法/长度非法时抛 `WechatCryptoException`（原先抛 `StringIndexOutOfBoundsException`）；④`listUserOpenids` 端点由 `POST cgi-bin/user/getall`（不存在，微信回 40066）改为 `GET cgi-bin/user/get`，响应字段 `data.openid_list` → `data.openid`；⑤`WeChatUser.subscribed`、`ChatRecord.valid` 按微信实际返回的 **0/1 整数**判定（原先 `Boolean.TRUE.equals(...)` 恒为 false，已关注用户被判成未关注）；⑥`AccessTokenManager` 的 `errcode` 改用 `Number` 取值，避免 `(Integer)` 强转掩盖真实错误。

### Added（新增）

- **wechat-sdk：回复额度软限制 + 一次问答自动拆分为多条下发**：`WechatResponse.messages(...)`/`texts(...)` 一次交多条；`ReplyPlan` 纯函数拆分（第 1 条且支持被动回复时走被动回复，其余走客服消息，**不改变消息顺序**）；`AsyncReplyDispatcher` 做额度记账（CacheStore，键 `wechat:reply_quota:{account}:{openid}`，48h 窗口）+ 单线程守护池异步补发 + 微信返回 `45047`/`45015` 时自动清零止血；新增 `passive-reply-limit`(1)、`customer-service-reply-limit`(5)、`reply-quota-reset-per-interaction`、`reply-overflow-policy`(drop\|merge)、`reply-quota-window-seconds` 配置项；单测 9 例覆盖拆分/额度/merge/止血/重置语义。

### Added（新增 · 0.1.3 主线）

- **架构对齐：数据库操作统一收口 database 模块 + 建表统一走迁移能力 + `vendor:publish --tag=migrations`（0.1.3 主线）**：
  - **database 模块新增 `JdbcExecutor`**（连接 + 参数化 SQL 执行底座：execute/update/queryMapped/queryFor*List/Map/queryForObject/insertReturningKey）——驱动类模块不再各自维护一套私有「JDBC 四件套（executeUpdate/queryRows/executeSql/bind）+ 方言判断 + 建表 DDL」；`migration` 模块原 `JdbcExecutor` 保留为等价兼容子类（标注 `@Deprecated`），既有代码与外部引用不受影响（`migration` 由此新增对 `database` 的依赖，无循环）。
  - **migration 模块新增 `Schema.createIfAbsent(table, def)`**：方言感知的存在性检查 + DDL 生成，作为驱动「幂等建表」标准入口（SQL Server / Oracle 不支持 `CREATE TABLE IF NOT EXISTS`，旧驱动内置 DDL 在这些库上直接失败；SQLite 的 AUTO_INCREMENT 硬编码同样消除）。
  - **migration 模块新增 `Dialect#upsertSql(...)`**（default = MySQL 变体）+ `AbstractDialect` 五个静态变体（MySQL `ON DUPLICATE KEY UPDATE` / PostgreSQL·SQLite `ON CONFLICT` / H2 `MERGE ... KEY` / SQL Server·Oracle `MERGE ... USING`）；六个方言类全部重写。`cache-database` 的驱动级 upsert 方言判断（`isMysql()/upsertSql()/textType()/quote()` 私有套）整体移除，改由 `DialectFactory.detect` + `Dialect` 统一提供。
  - **三个数据库驱动收敛**：`DatabaseCacheDriver`（cache-database）、`DatabaseFilesystem`（storage-database）、`DatabaseQueueDriver`（queue-database）全部改为经由 `JdbcExecutor` 执行 SQL；`createTable()` 全部改为 `Schema.createIfAbsent` + Blueprint DSL（`cache-database` 的私有建表 DDL 与 `queue-database` 的 `AUTO_INCREMENT` 硬编码 DDL 移除，queue-database 由此新增 `database` 模块依赖）；驱动暴露单一事实来源的 `defineXxxTable(Blueprint)` 定义，与内置迁移文件一致。
  - **core 模块新增 `PublishableMigration` SPI + `PublishType.MIGRATION`**：与 `PublishableConfig`/`PublishableStatic` 平行的第三种可发布项——发布模块自带的**迁移 Java 源文件**到业务工程迁移源代码目录（`MakeCodeProperties#getMigrationSourceDir`，默认 `src/main/java/<basePackage>/database/migrations`，包名自动重写为 `<basePackage>.database.migrations`，发布后直接可编译，含目录穿越防护）。
  - **三个模块自带建表迁移并声明发布**：`cache-database`（`jaravel_cache`）、`storage-database`（`storage_file`/`storage_file_chunk`）、`queue-database`（`jobs`/`failed_jobs`）各打包一份内置迁移 Java 源（`jaravel/migrations/Migration_20240101_*.java`，与驱动 `defineXxxTable` 一致）+ `XxxDatabaseMigrationPublishable` 声明；springboot 侧以 `@ConditionalOnClass` 守卫的 `XxxDatabasePublishAutoConfiguration` 注册（queue 侧并入既有 `QueueDatabaseAutoConfiguration` 静态块）——未引入可选模块的应用不受影响。
  - **`vendor:publish` 命令扩展**：新保留标签 `--tag=migrations`（一键发布**所有模块**的建表迁移，对齐 Laravel `vendor:publish --tag=migrations`）；各模块 tag（`--tag=cache-database` 等）同时发布其配置与迁移；`--all` 包含迁移文件；`--list` 展示迁移清单（类型 migration + 文件名列出）。
  - **`xxx:table` 生成命令对齐**：`cache:table` / `storage:table` / `queue:table` 的生成目录与包名改为注入 `MakeCodeProperties`（与 `vendor:publish` 落点一致：`getMigrationSourceDir()` / `getMigrationPackage()`），消除「命令写 `database/migrations` 包 `database.migrations`、发布写源码树包 `<basePackage>.database.migrations`」的不一致。
  - **推荐工作流（新）**：`artisan vendor:publish --tag=migrations` → `artisan migrate`，一条链完成所有模块建表；原有 `xxx:table` + `migrate` 工作流继续可用。
  - springboot 装配对齐：`QueueDatabaseAutoConfiguration.databaseQueueDriver` 数据源解析顺序改为「先 `ConnectionManager` 注册表、后 Spring `DataSource` Bean」，与 cache/storage 驱动一致（装配条件与 sync 回退语义不变）。

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
- **storage-database · `allFiles`/`files` 递归列表缺陷修复（jaravel 应用浏览器验证暴露）**：`DatabaseFilesystem.listEntries` 此前仅收集「顶层无斜杠路径」的文件，**任何子目录下的文件在递归文件列表中全部丢失**（`allFiles("")` 对 `uploads/x.txt` 一律空白；目录列表不受影响）——补 `recursive + 文件` 分支后行为正确；新增回归测试 `testRecursiveListingIncludesSubdirectoryFiles`（顶层/二级/三级子目录文件 + 第一级目录断言）。原存储类测试未覆盖 `files/allFiles/directories` 路径，故未暴露。
- **springboot 装配守护缺陷修复（P2 遗留跨 jar 缺陷 · 由 jaravel 应用启动暴露）**：5 个引用**可选依赖类**的自动配置在类上补 `@ConditionalOnClass`——未在 classpath 上引入对应可选模块（aether-upload / redis / redis-cache / session-redis）的应用启动时不再因 `@ConditionalOnMissingBean` 类型推断 `ClassNotFoundException` 而崩溃（此前 P2 将这些装配从可选模块收敛至 springboot 后，springboot 恒在 classpath 上，缺失守护直接启动失败；全模块测试因 optional 依赖齐备未能暴露）：
  - `AetherUploadAutoConfiguration` → `@ConditionalOnClass({AetherUploadManager, AetherUploadProperties})`
  - `AetherUploadPublishAutoConfiguration` → `@ConditionalOnClass({AetherUploadPublishableConfig, AetherUploadStaticPublishable})`
  - `RedisPublishAutoConfiguration` → `@ConditionalOnClass(RedisPublishableConfig)`
  - `RedisCachePublishAutoConfiguration` → `@ConditionalOnClass(RedisCachePublishableConfig)`
  - `SessionRedisPublishAutoConfiguration` → `@ConditionalOnClass(SessionRedisPublishableConfig)`
- **（续）守护指向错误类的装配修复（jaravel 应用二次启动暴露）**：守护必须指向**真正可选的模块类**而非恒在 core/starter 的类，否则守护形同虚设，`@ConditionalOnMissingBean` 类型推断仍会 CNFE：
  - `QueueDatabaseAutoConfiguration`：原 `@ConditionalOnClass(QueueDriver.class)`（`QueueDriver` 在 **core**，恒在 classpath）→ 补 `{QueueDriver, QueueDatabaseProperties, DatabaseQueueDriver}`（queue-database）
  - `QueueArtisanAutoConfiguration`：原 `@ConditionalOnClass(ArtisanCommand.class)`（artisan 恒在）→ 补 `{ArtisanCommand, QueueTableCommand}`（queue-database）
  - `WechatPublishAutoConfiguration`：静态块引用 wechat-sdk 的 `WechatPublishableConfig` → 补守护（原类无任何守护，类加载即触发静态注册）
- **（续）组合缺口加固**：`RedisQueueAutoConfiguration` → `{RedisManager, RedisQueueDriver, QueueDatabaseProperties}`（防止"redis 在 / queue-database 缺"组合崩溃）；`WechatAutoConfiguration` → `{OkHttpClient, WechatProperties}`（防止"okhttp 在 / wechat-sdk 缺"组合崩溃）；captcha/jblade/jwt/modelcache/session-redis 主装配逐一核对，守护已正确指向各自模块类，无需改动；修复后全仓 34 模块 `clean install`（含测试）BUILD SUCCESS。
- （附）`DatabaseAutoConfiguration.baseModelDataSourceBindingPostProcessor` 改为 `static` 工厂方法（消除 Spring 对 BeanPostProcessor 非静态工厂的 WARN；行为不变，随 jaravel 应用启动日志核验）。
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

- 0.1.3 架构对齐新增用例：`DialectUpsertTest`（6 方言 × upsert SQL 形状 + default 兜底 + 未知产品名回退）、`VendorPublishCommandTest` 迁移发布 4 例（`--tag=migrations` / 模块 tag / 包名重写 / skip + `--force` / `--all` 含迁移）、`CacheDatabaseMigrationPublishableTest`（内置资源打包核对）、`BundledMigrationE2ETest`（内置迁移 → 内存编译 → H2 上 migrate → 驱动读写 → rollback 全链路）。
- 全模块单测保持全绿；wechat-sdk 由历史 19 个锁定用例扩展到 **198 个**（类型化消息模型 134 + 洋葱内核/网页授权 42 + 发布模板回归 3），无一联网（mock OkHttp/Servlet）。
- **P3 · 非 Spring 可用性冒烟（§5.4 新增门禁）**：core 新增 `NonSpringAvailabilitySmokeTest`——不 import 任何 Spring 类型，手动安装 Map 版 `GlobalBeanProvider` 后验证 `Facade`/`App.app()`/`Application` 三种注册（bind/single/default）/`publishToSpring` 全链路可用；移除提供者后 `beanOrNull` 空安全降级、强依赖路径给出含 `GlobalLookup.install` 指引的异常（不再抛 Spring 类加载异常）。
