# wechat-sdk 模块（微信 SDK）

> 包名：`com.weacsoft.jaravel.vendor.wechat`
> 对齐 PHP 扩展包：`overtrue/laravel-wechat`（EasyWeChat 5.x）
> 状态：**类型化消息模型（Typed Message Model）已全量实现** —— 旧 Map 裸接口已移除，不再保留。

## 模块概述

`wechat-sdk` 为 jaravel-vendor 提供微信公众号与小程序的完整能力：API 调用、消息收发、被动回复与加解密、菜单、模板/订阅通知、素材、JSSDK、小程序登录/订阅消息/码与链接。

设计原则（对齐 jaravel-vendor 约定）：

1. **声明 > yml > 兜底默认**：配置优先用 `vendor:publish --tag=wechat-sdk` 输出的 `@RegisterWechatOfficialAccount` / `@RegisterWechatMiniApp` 声明类（`WechatPublishableConfig` 模板），yml 作为回退，两者都没有时服务层显式报 `IllegalStateException`，不做静默兜底。
2. **响应类型化**：所有 API 收敛为 `WeChatResponse`（`isSuccess()` / `requireSuccess()` / `as(Class)` / 类型化取值器），微信业务错误（`errcode != 0`）不再被 warn 日志吞掉。
3. **消息类族**：11 类客服消息 + 被动回复 6 类共享 `message.Message` 基类（双序列化 `toJsonBody()` / `toXmlArray()` + 构造时快速失败），对齐 EasyWeChat `Kernel/Messages`。
4. **接收侧完整**：XML 解析（`server.MessageParser`）+ 签名/SHA1 + AES-ECB 加解密（`crypto.WxBizMsgCrypt`，JDK 实现，无三方依赖）+ **洋葱内核** `kernel.WechatKernel`（`WechatRequest` 组装/提取 + `WechatResponse` 组装/返回 + `WechatMiddleware` 洋葱层；`WeChatServer` 为其薄壳）。
5. **网页授权（OAuth）**：`oauth.WeChatOAuth`（授权 URL 组装 + code 换 openid/用户 + EasyWeChat 兼容会话键）+ `oauth.WeChatOAuthMiddleware`（自动重定向，路由别名 `wechat.oauth`，对齐 overtrue `OAuthAuthenticate`）。
6. **Token/Ticket 缓存**：`accessTokenManager` 与 jsapi_ticket 均走 core + cache 模块（`CacheManager` / `CacheStore`），多实例可共享 redis store。
7. **测试不联网**：单测一律 mock OkHttp，覆盖 195 个用例。

## Maven 依赖

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>wechat-sdk</artifactId>
    <version>0.1.3</version>
</dependency>
```

传递依赖：`cache`、`okhttp`、`jackson-databind`、`spring-boot-starter`。

## 配置（三层）

### 第 1 级（推荐）：声明式注册

`vendor:publish --tag=wechat-sdk` 会发布声明类模板（`WechatPublishableConfig`），在你的业务包里启用即可：

```java
import com.weacsoft.jaravel.vendor.wechat.RegisterWechatOfficialAccount;
import com.weacsoft.jaravel.vendor.wechat.RegisterWechatMiniApp;
import com.weacsoft.jaravel.vendor.wechat.WechatProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WechatSdkConfig {

    @RegisterWechatOfficialAccount(value = "default", alias = {"snsapi_base"})
    public WechatProperties.OfficialAccountConfig defaultAccount() {
        WechatProperties.OfficialAccountConfig cfg = new WechatProperties.OfficialAccountConfig();
        cfg.setAppId("wx1234567890abcdef");
        cfg.setSecret("your-official-secret");
        cfg.setToken("your-token");
        cfg.setAesKey("your-aes-key");             // safe 模式（被动回复加密）必须
        cfg.setMessageMode("plain");               // 或 "safe"
        return cfg;
    }

    @RegisterWechatMiniApp("default")
    public WechatProperties.MiniAppConfig defaultMiniApp() {
        WechatProperties.MiniAppConfig cfg = new WechatProperties.MiniAppConfig();
        cfg.setAppId("wx7051c4a2a779d651");
        cfg.setSecret("your-mini-secret");
        cfg.setType(2);                            // 2=客服小程序 / 3=管理端
        return cfg;
    }
}
```

注册器启动时校验产物（公众号缺 `appId/secret` 直接 `RegistrarException`），并回填到共享的 `WechatProperties`——**声明永远覆盖 yml**。

### 第 2 级（回退）：yml

```yaml
jaravel:
  wechat:
    enabled: true
    token-mode: legacy            # legacy（默认，GET cgi-bin/token）| stable（POST cgi-bin/stable_token）
    cache-store:                  # 留空用 cache 模块默认 store；多实例共享可设 redis
    official-accounts:
      default:
        app-id: wx1234567890abcdef
        secret: your-official-secret
        token: your-token
        aes-key: your-aes-key
        message-mode: plain       # plain（默认）| safe
        oauth:
          scopes: snsapi_base
          callback: /oauth_callback
          enforce-https: true
    mini-apps:
      default:
        app-id: wx7051c4a2a779d651
        secret: your-mini-secret
        type: 2
    http:
      timeout: 5.0
      retry: true
```

### 配置项汇总

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `jaravel.wechat.enabled` | boolean | `true` | 是否启用 |
| `jaravel.wechat.token-mode` | string | `legacy` | `legacy` / `stable` |
| `jaravel.wechat.cache-store` | string | 空 | token/ticket 首选缓存 store；空=cache 默认 store |
| `...official-accounts.{name}.app-id / secret` | string | - | 公众号凭据 |
| `...official-accounts.{name}.token / aes-key` | string | - | 消息校验与加解密密钥（safe 模式必填） |
| `...official-accounts.{name}.message-mode` | string | `plain` | 回调模式：`plain`/`safe` |
| `...official-accounts.{name}.oauth.scopes/callback/enforce-https` | - | - | OAuth 配置 |
| `...mini-apps.{name}.app-id / secret / type` | - | - | 小程序配置 |
| `...http.timeout / retry` | - | - | HTTP 配置 |

## 类总览

```
com.weacsoft.jaravel.vendor.wechat
├── WechatProperties                # 共享配置容器（含 OfficialAccountConfig / MiniAppConfig）
├── RegisterWechatOfficialAccount   # 公众号声明注解    (@Register*，注册器驱动)
├── RegisterWechatMiniApp           # 小程序声明注解
├── WechatPublishableConfig         # vendor:publish --tag=wechat-sdk 声明模板
├── registrar/
│   ├── WechatOfficialAccountRegistrar
│   └── WechatMiniAppRegistrar
├── AccessTokenManager              # access_token（legacy/stable 双模式 + cache 模块缓存）
├── OfficialAccountService          # 公众号服务（64 个类型化 API）
├── MiniProgramService              # 小程序服务
├── WeChatServer                    # 被动回复服务端薄壳（委托 WechatKernel）
├── kernel/  WechatKernel · WechatMiddleware · WechatRequest（组装/提取）· WechatResponse（组装/返回）
│            VerifySignatureMiddleware（内置①验签）· DecryptParseMiddleware（内置②解密+解析）
│            ResponderMiddleware（旧式 responder 桥接）
├── oauth/   WeChatOAuth（授权 URL + code 换 openid + 会话契约）· WeChatOAuthUser（会话值）
│            WeChatOAuthMiddleware（自动重定向，别名 wechat.oauth）
├── response/  WeChatResponse · WechatApiException
├── message/   Message + 11 类（Text/Image/Voice/Video/Music/News/NewsItem/MpNews/MpNewsArticle/MenuMessage/WeChatCard/MiniProgramPage）
├── server/    ServerMessage + 10 类（TextMessage/ImageMessage/.../EventMessage/两个订阅通知事件...）
│              + MessageParser · MessageType · UnsupportedMessageException
├── crypto/    WxBizMsgCrypt · WechatCryptoException
├── xml/       XmlUtil（JDK StAX，防 XXE）
├── menu/      Menu · MenuItem
├── template/  TemplateMessage · TemplateDataItem · SubscriptionNotice · MiniProgramTarget
├── user/      WeChatUser · Tag · MaterialItem · KfAccount · ChatRecord
├── jsdk/      JssdkConfig
├── mini/      MiniProgramSession · MiniSubscribeMessage · PhoneNumberResult
├── transport/ WechatTransport · RequestJsonEncoder · JacksonJsonEncoder
├── WechatCacheResolver             # core+cache 的 CacheStore 解析（共享）
└── WechatAutoConfiguration
```

## 使用示例

### 1. 用户 / 标签（类型化）

```java
@Autowired private OfficialAccountService official;

WeChatUser user = official.getUser("o6_bmjrPTlm6_2sgVt7hMZOPfL2M");        // 默认配置
WeChatUser user2 = official.getUser(openid, "snsapi_userinfo");            // 指定别名

official.createTag("VIP用户");
official.batchTagging(101, List.of("openid1", "openid2"));
List<String> openids = official.listUserOpenids();                          // getall 分页自动汇聚
```

### 2. 菜单（fluent + 校验）

```java
Menu menu = new Menu(
    new MenuItem().name("今日歌曲").click("V1001_TODAY_MUSIC"),
    new MenuItem().name("服务").sub(List.of(
        new MenuItem().name("客服").view("https://example.com/kefu")
    )),
    new MenuItem().name("小程序").miniprogram("wxmini", "pages/a")
);
official.setMenu(menu);          // 1~3 个顶层、子级 <=5 项，构造即校验
```

### 3. 模板消息 / 订阅通知

```java
// 经典模板消息（cgi-bin/message/template/send）
TemplateMessage t = new TemplateMessage()
    .toUser("openid")
    .templateId("TPL_ID")
    .url("https://example.com/order/123")
    .miniProgram(new MiniProgramTarget("wxmini", "pages/order/detail"))
    .data("first", TemplateDataItem.colored("您好，新订单", "#173177"))
    .data("keyword1", TemplateDataItem.ofValue("订单号 123456"));
official.sendTemplate(t).requireSuccess();

// 订阅通知（新通道，cgi-bin/message/template/subscribe）
official.sendSubscriptionNotice(new SubscriptionNotice()
    .toUser("openid").templateId("SUB_ID").title("发货通知")
    .scene("订单更新").url("https://example.com/order/1")
    .content("包裹已发出"));
```

### 4. 客服消息（11 类消息族）

```java
official.sendText("openid", "您好，有什么可以帮您？");
official.sendNews("openid", List.of(new NewsItem("标题", "描述", "https://pic.png", "https://url/1")));
official.sendMpNews("openid", "MEDIA_NEWS");
official.sendMusic("openid", new Music("歌名", "描述", "https://m.mp3", null, "https://thumb.jpg"));

// 正交包装：指定客服账号 / AI 标识
official.sendCustomerMessage(
    new Text("内容由第三方 AI 生成")
        .toUser("openid")
        .withKfAccount("kf1@weixin")
        .withAiMsg(true));

official.setTyping("openid", true);     // 正在输入 / 取消
```

### 5. JSSDK

```java
JssdkConfig js = official.buildJsSdkConfig(
    "https://example.com/page",
    List.of("chooseWXPay"),
    List.of("wx-open-launch-app"),
    false);
js.toJsonBody();      // 给前端 wx.config
js.toJavascript();    // 直出 JS 代码
```

### 6. 被动回复（plain 模式）

```java
WeChatServer server = official.server("default");   // 自动绑定消息模式

// GET 校验：验签通过后原样返回 echostr（微信服务器首次访问时调用）
String echostr = server.handleGet(query);

// POST 推送：返回被动回复 XML（5 秒内）
//   reply 中的 ToUserName/FromUserName/CreateTime 由服务端按收到的消息自动回填，无需手工设置
String replyXml = server.handlePost(query, pushXml,
    (msg, srv) -> new Text("你好，" + msg.getFrom() + "！").toUser(msg.getFrom()));

// 不支持的回复类型 / responder 返回 null → 返回 ""（微信视为 5 秒超时处理），绝不抛出
```

### 7. 被动回复（safe 加密模式）

```yaml
jaravel.wechat.official-accounts.default.message-mode: safe
```

```java
WeChatServer server = official.server();           // 自动按 message-mode 选择明文/安全
String replyXml = server.handlePost(query, pushXml, (msg, srv) ->
    new Text("加密回复" + msg.getFrom()).toUser(msg.getFrom()));
// replyXml 形如 <xml><Encrypt>..</Encrypt><MsgSignature>..</MsgSignature><TimeStamp>..</TimeStamp><Nonce>..</Nonce></xml>
```

### 8. 被动回复 —— 洋葱模型（推荐，对齐 EasyWeChat Kernel / Laravel 中间件）

`WechatKernel` 是洋葱内核：外层验签 → 解密/解析 → **你的业务层** → 默认沉默层。
每层拿到强类型 `WechatRequest`（组装/提取一体），返回 `WechatResponse`（组装/返回一体），可以短路、可以追加任意层。

```java
@Autowired private WeChatOAuth oauth;          // 服务容器直接注入
@Autowired private WechatProperties props;

// 方式一：直接用 kernel（洋葱是主 API）
WechatKernel kernel = new WechatKernel("default", props.getOfficialAccount("default"));
//   内置两层不可移除：VerifySignatureMiddleware（验签）→ DecryptParseMiddleware（解密+解析）
//   下面追加业务层（洋葱：先注册的先执行）：
WechatKernel app = kernel
    .middleware((req, next) -> {
        // 审计/限流/上下文增强等横切关注点
        return next.handle(req);
    })
    .middleware((req, next) -> {
        if (req.isText() && "你好".equals(req.textContent())) {
            return WechatResponse.text("您好，" + req.openid());   // 短路：内层不再执行
        }
        if (req.isScan() && "QR".equals(req.eventKey())) {
            return WechatResponse.empty();                          // 主动沉默
        }
        return next.handle(req);
    });

String echostr = app.handleGet(query);                     // GET 校验（回包 echostr 解密后明文）
String replyXml = app.handlePost(query, pushXml);          // POST 推送（safe 自动加密回包）
Object msg = kernel.parse(query, pushXml);                 // 只要消息体（ServerMessage）

// 方式二：旧式 responder 签名（WeChatServer 薄壳，行为与 153 个历史用例一致）
WeChatServer server = new WeChatServer(props, "default");
server.handlePost(query, pushXml, (msg, srv) -> new Text("hi").toUser(msg.getFrom()));
```

行为约定：
- **验签/解密失败** → `WechatCryptoException`（错误消息指明 `signature` / `msg_signature` 失败原因）。
- **默认层不回复** → `handlePost` 返回空串 `""`（对齐微信「5 秒超时」语义，不重试）。
- **旧式 responder 路径**返回 `null` 或抛异常 → 同样按空串应答（`ResponderMiddleware` 承接历史语义）。
- **不支持被动回复的消息类**（如 `WeChatCard`）经 `WechatResponse` 组包时 → `WechatCryptoException` 指明类名。
- `WechatKernel.middleware(...)` 不可变：返回新内核，原内核行为不变。

### 9. 网页授权（公众号 OAuth，openid 获取 + 自动重定向）

> 对齐 overtrue `wechat.auth` 中间件 + EasyWeChat `officialAccount->oauth`。
> SDK 只做「取 openid + 自动跳转」；**Auth guard 由业务侧注册**（会话键已固定，见下）。

```java
// 路由挂中间件：别名 wechat.oauth，冒号参数 (account[,scope])
route.get("/weapp", WeappAppController.class)
     .middleware("wechat.oauth");                       // 默认账号 default
     .middleware("wechat.oauth:sns,snsapi_userinfo");   // 指定账号 + 授权范围
```

中间件行为（每次请求判定）：

| 条件 | 动作 |
|---|---|
| 会话已有用户（`easywechat.oauth_user.{account}`） | **放行**到业务层 |
| 回调带回 `?code=&state=` 且 state 匹配 | code 换 openid → **写入会话** → 清除 state → **302 回原地址**（剔除 code/state，保留其余 query） |
| 回调 state 不匹配 | **403**（防 CSRF），不写会话 |
| 未授权 | 302 → 微信 `connect/oauth2/authorize`（自带随机 state 已记入会话；`enforce-https=true` 时回跳地址升级为 https） |

业务侧集成（guard 由你注册，读固定会话键即可）：

```java
// 1) guard：从 EasyWeChat 兼容会话键取用户（WeChatOAuthUser.getId() == openid）
public class WechatGuard implements Guard {
    private final SessionStore session;
    public WechatGuard(SessionStore session) { this.session = session; }
    @Override public String id(String account) {
        Object v = session.get("easywechat.oauth_user." + (account == null ? "default" : account));
        return v instanceof com.weacsoft.jaravel.vendor.wechat.oauth.WeChatOAuthUser u ? u.getId() : null;
    }
    // authenticate/validate 照 auth 模块接口实现即可
}

// 2) 需要显式控制时直接用服务对象
WeChatOAuthUser user = oauth.userFromCode("default", code);   // code 换用户（snsapi_userinfo 时含昵称头像）
String page = oauth.authorizeUrl("default", "https://example.com/weapp");   // 手动构造授权地址
String key = WeChatOAuth.sessionKey("default");               // = "easywechat.oauth_user.default"
WeChatOAuth.saveToSession(session, "default", user);          // 手工写会话（如登录后绑定）
```

配置（`...official-accounts.{account}.oauth`）：

| 属性 | 默认 | 说明 |
|---|---|---|
| `scopes` | `snsapi_base` | 授权范围；逗号分隔。`snsapi_base` 只取 openid，`snsapi_userinfo` 附带昵称/头像 |
| `callback` | 空 | 仅供你在路由里绑定的回调路径提示（中间件默认把「当前 URL」当 redirect_uri，overtrue 式） |
| `enforce-https` | `true` | 回跳/redirect_uri 自动 http → https 升级 |

> **会话存储**：中间件使用 `http` 模块的 `SessionStoreHolder`（`auth` 同源 bean）；
> 未引入会话自动装配时自动降级为「无状态模式」（放行判定恒为未授权，回调不持久化——纯 API 场景仍可用 `WeChatOAuth` 直调）。

### 10. 小程序

```java
@Autowired private MiniProgramService mini;

MiniProgramSession session = mini.code2Session("default", "js_code");   // 登录（失败即抛 WechatApiException）
mini.sendSubscribeMessage(new MiniSubscribeMessage()
    .touser("openid").templateId("SUB_T").data("thing1", "已发货"), "default");
byte[] wxacode = mini.getMiniProgramCode("default", "SCENE_1");          // 小程序码（≤1280）
String link = mini.generateUrlLink("default", "pages/a", "id=1");
String scheme = mini.generateScheme("default", "pages/a", "id=1", 1, 86400);
PhoneNumberResult phone = mini.getPhoneNumber("default", "phonenumber_code");
WeChatResponse up = mini.uploadMedia("default", "D:/a.png", "image", true);  // permanent=true → 永久素材
```

## Access Token 缓存机制

`accessTokenManager` 负责 token 获取与缓存，缓存能力委托 cache 模块：

1. **基于 cache 模块**：`CacheManager` → `CacheStore`，首选 store 由 `jaravel.wechat.cache-store` 指定（默认为空，用 cache 默认 store，由 `jaravel.cache.default-store` 决定）；显式 store 未注册时回退默认 store。
2. **缓存键**：`wechat:access_token:{appId}`；jsapi_ticket 为 `wechat:jsapi_ticket:{appId}`。
3. **TTL 缓冲**：缓存 TTL = `expires_in - 300`（提前 5 分钟过期，最小 60s）。
4. **双模式**：`token-mode: legacy`（GET `cgi-bin/token`）/ `stable`（POST `cgi-bin/stable_token`，官方推荐的高频控凭据接口）。
5. **强制刷新 / 清理**：`refreshToken(appId, secret)`、`invalidateToken(appId)`、`invalidateAllTokens()`。

> 微信 access_token 每天获取限额 2000 次，务必走缓存。多实例共享可显式 `cache-store: redis` 并引入 `redis-cache` 模块。

## 版本与契约

- **195 个单测全绿**（含 19 个历史锁定用例 + 42 个洋葱内核/网页授权用例；旧 153 个用例全保留）。
- **旧 Map 裸接口（`getUserData` / `sendMessage(Map)` / `sendTyping(openid, int)` / `jscode2session` / `sendTemplateMessage` / `getMenu→Map` 等）已全部移除**，不再保留过渡期。
- 设计文档：消息模型 [DESIGN-message-model.md](./DESIGN-message-model.md) · 洋葱内核与网页授权 [DESIGN-web-oauth.md](./DESIGN-web-oauth.md)。
