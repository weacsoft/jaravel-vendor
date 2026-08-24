# DESIGN: 洋葱内核 与 公众号网页授权（OAuth）

> 模块：`wechat-sdk` · 日期：2026-07-14
> 参照系：`overtrue/laravel-wechat`（EasyWeChat 5.x）+ `D:\0code\deepseekharness\PHP系统迁移到Java系统计划\manage8.0`
> 契约文档：`README.md` §8（洋葱）/ §9（网页授权）

## 1. 需求拆解（用户指令）

1. **消息 Request/Response 的静态组装/提取/返回**（"类似 Laravel"）；
2. **洋葱模型**（"和 easywechat 一样"）；
3. **网页认证中间件**（或等价的替代办法）；
4. **用户拍板分工**：
   - Auth guard 的注册（`AuthManager.register` / `wechat` guard）→ **业务侧自己写**，SDK 不做；
   - **取 openid + 在那里自动重定向** → SDK 提供（本设计）。

## 2. 洋葱内核（kernel 包）

### 2.1 层序与语义

```
WechatKernel.handleGet(query)
  └─ VerifySignatureMiddleware        内置① 验签（plain: signature / safe: msg_signature）
       └─ DecryptParseMiddleware       内置② 解密（safe）+ 解析（WechatRequest.message()）
            └─ 用户 middleware(1..n)    WechatKernel.middleware(...) 追加；**先注册先执行**
                 └─ 默认沉默层           不回复（空串退避 / ECHO 回包）
```

- **`WechatRequest`**：组装 + 提取一体。静态工厂 `ofVerify(query, name, account)` /
  `ofMessage(query, rawXml, name, account)`；提取器覆盖 query 字段、`crypt()`、
  `plainXml()`（lazy 解密）、`message()`（lazy 解析为 `ServerMessage`）、
  `openid()/toOpenid()`、`isText()/textContent()/isScan()/eventKey()` 等。
- **`WechatResponse`**：组装 + 返回一体。Kind 枚举 `ECHO | MESSAGE | EMPTY | RAW`，
  工厂 `echostr()/text()/image()/empty()/rawXml()/message(Message)`；
  `toReplyXml(userOpenid, account)` 负责**方向互换**（ToUserName=用户）与
  不支持被动回复的类（`WeChatCard` 等）的 `WechatCryptoException` 拒绝。
- **`WechatMiddleware`**：`@FunctionalInterface WechatResponse handle(WechatRequest, Next)`，
  短路 = 不调 `next.handle(...)`（对齐 Laravel `Next` / EasyWeChat `Kernel/ServerGuard`）。

### 2.2 兼容性契约

- `WeChatServer`（plain/safe 薄壳）的 13 个历史行为用例**逐字保留**：
  验签失败文案（`GET/POST 验签失败（signature|msg_signature 不匹配）`）、
  responder 返回 null / 抛异常 → 空串、`WeChatCard` → 异常类名提示、safe 全链路 round-trip、
  缺配置 `IllegalStateException(未找到公众号配置: ...)` 文案。
- 内核层异常**直接上抛**（洋葱由开发者控制）；「吞异常→空串」由 `ResponderMiddleware`
  承载（仅 `WeChatServer.handlePost(responder)` 旧签名路径），两套语义不混淆。

### 2.3 与 EasyWeChat Kernel 的对照

| EasyWeChat (PHP) | 本项目 (Java) |
|---|---|
| `Kernel->server()->handleRequest($message)` | `WechatKernel.handlePost(query, rawXml)` |
| `Event/Message` 对象 | `WechatRequest`（内嵌 `ServerMessage`） |
| `Kernel/Messages` 回复对象 | `WechatResponse`（Kind 判别） |
| `$app->push(function($message,$app){ return $app->reply('...'); })` | `kernel.middleware((req,next) -> WechatResponse.text(...))` |
| `Server/Event/Message` guard | `VerifySignatureMiddleware` + `DecryptParseMiddleware` |

## 3. 网页授权（oauth 包）

### 3.1 流程（对齐 overtrue `OAuthAuthenticate`）

```
请求 → [会话已有用户?] ──是──▶ next() 放行
        │否
        ├──[?code= 存在?]
        │   │ 是：state 校验 ──失败──▶ 403 forbidden("OAuth state 校验失败（疑似 CSRF，请重新发起授权）")
        │   │      通过：userFromCode → 写会话 → 清 state → redirect(intendedUrl)
        │   │         intendedUrl = 当前 URL 剔除 code/state 参数（保留其余），
        │   │                        enforce-https 时 http:// → https://
        │   └─ 否：清旧用户值 → 生成随机 state 存会话 → redirect(authorizeUrl(fullUrl))
```

- **authorizeUrl**：`https://open.weixin.qq.com/connect/oauth2/authorize?appid=&redirect_uri=&response_type=code&scope=&state=#wechat_redirect`
  （scope 取范围列表第一项；redirect_uri 必须已编码；与小程序 `jscode2session` 是两套协议，勿混用。）
- **code 换用户**：`GET sns/oauth2/access_token?appid&secret&code&grant_type=authorization_code`
  → `{openid, access_token, unionid?}`；`scope=snsapi_userinfo` 时再 `GET sns/userinfo`
  （失败降级为仅 openid，warn 日志，不致命）。
- **state**：32 位随机 hex，只存会话不加密（防 CSRF 用）；使用后立即清除。

### 3.2 会话契约（业务 guard 对接的唯一硬约定）

| 键 | 值 | 写者 | 读者 |
|---|---|---|---|
| `easywechat.oauth_user.{account}` | `WeChatOAuthUser`（`getId()` == openid） | `WeChatOAuthMiddleware` / `WeChatOAuth.saveToSession` | **业务侧 wechat guard** |
| `easywechat.oauth_state.{account}` | 随机 hex | `WeChatOAuthMiddleware` | `WeChatOAuthMiddleware`（自用） |

- `{account}` 归一：null/空 → `"default"`。
- 键名与 PHP EasyWeChat **完全一致** → 用户按 PHP 习惯写 guard 不需要改认知。
- `WeChatOAuthUser` 是 `Serializable`（JSESSIONID 持久化兼容），字段：
  `openid`(=id) / `unionId` / `nickname` / `headimgUrl` / `accessToken` / `scope` / `raw`。

### 3.3 注册形态（SDK 侧已就绪的部分）

- Bean：`WeChatOAuth`（`WechatAutoConfiguration` 自动装配）、
  `WeChatOAuthMiddleware`（默认账号 `default`，注入 `SessionStoreHolder`（可空））。
- 路由别名：`wechat.oauth`（`MiddlewareAliasRegistry` 全局注册），
  参数语法 `wechat.oauth` / `wechat.oauth:{account}` / `wechat.oauth:{account},{scope}`。
- **未做的事（按分工留给业务）**：`AuthManager` 里 `wechat` guard 的注册、
  登录态的登出（业务侧删除会话键即可）。

## 4. 依赖与装配注意

- `wechat-sdk` 新增编译依赖 `io.github.lijialong1313:http`（`Middleware`/`Request`/`ResponseBuilder`/`SessionStore`），
  无循环依赖（http 只依赖 core/utils/spring-boot）。
- 会话 bean 来源：`http` 的 `HttpSessionAutoConfiguration → SessionStoreHolder`
  （`@ConditionalOnWebApplication(SERVLET)`）。`WechatAutoConfiguration` 用
  `ObjectProvider<SessionStoreHolder>` 优雅降级 → **无 HTTP 容器的环境**里中间件退化为
  「无状态模式」（始终未授权、不持久化，纯 `WeChatOAuth` 服务调用不受影响）——已在测试覆盖。
- 测试类路径：`jakarta.servlet-api`（test scope，mock `HttpServletRequest` 驱动 `Request.fullUrl()`）。

## 5. 测试矩阵（42 个新增用例，全部不联网）

| 套件 | 用例 | 覆盖点 |
|---|---|---|
| `kernel.WeChatKernelTest` | 19 | Request 提取/verify 拒绝消息访问 / Response Kind 守卫 / 方向互换 / `WeChatCard` 拒绝 / 验签失败文案 / 洋葱出入顺序（A-in→B-in→B-out→A-out）/ 短路 / 不可变 / 异常传播 vs ResponderMiddleware 吞异常 / parse / safe GET+POST 全链路 round-trip |
| `oauth.WeChatOAuthTest` | 13 | authorizeUrl 形状 + scope 默认 + 编码 + 未知账号快败 / resolveScopes 优先级 / code 换 openid（mock transport）/ snsapi_userinfo 合并昵称头像 / userinfo 失败降级 / 会话契约（含 null store）/ `fromStored` Map/非 Map/null / state 形态 |
| `oauth.WeChatOAuthMiddlewareTest` | 10 | 已授权放行 / 回调换码→存会话→302 原路（code/state 剔除、其余 query 保留）/ state 失配 403 / 未授权 302 授权页（state 落会话）/ enforceHttps 升级 / 路由参数 account+scope / null store 容忍 / intendedUrl 边界 |

## 6. 业务侧对接清单（留给用户）

1. 路由：回调路径挂 `.middleware("wechat.oauth")`（微信「授权回调域」配置 = 该路径所在域）。
2. Guard：实现 `Guard#id(String account)` 读 `easywechat.oauth_user.{account}` → `WeChatOAuthUser#getId()`；
   `@AuthenticationPrincipal` 注入即拿到 `WeChatOAuthUser`。
3. 登出：`session.remove("easywechat.oauth_user.default")`（或直接 `destroy()`）。
4. 多公众号：别名区分（`wechat.oauth:sns`），会话键随之带后缀。
5. `snsapi_userinfo` 场景：路由参数或 yml `oauth.scopes` 指定，用户昵称/头像进入会话值。
