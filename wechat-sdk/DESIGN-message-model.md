# wechat-sdk 消息类封装设计（Typed Message Model）

> 状态：**已实现**（2026-08，153 单测全绿；见 README「版本与契约」）
> 范围：`com.weacsoft.jaravel.vendor.wechat` 模块
> 参照：微信官方服务端文档（developers.weixin.qq.com，2026-08 快照）、EasyWeChat 5.x（w7corp/easywechat）消息模型
> 关联：`README.md`（PHP 对齐关系）、`AccessTokenManager.java`、`OfficialAccountService.java`、`MiniProgramService.java`

> **实现落地说明（对本文档的两处修订）**：
> 1. **旧 Map 接口直接移除**（不保留、不标 `@Deprecated`）——由项目所有者明确指示（"还没开始用，直接去掉"），覆盖下方 §1 的"兼容性承诺"。
> 2. **配置面采用「声明 > yml > 兜底」**：新增 `@RegisterWechatOfficialAccount` / `@RegisterWechatMiniApp` + 注册器 + `vendor:publish --tag=wechat-sdk` 声明模板（`WechatPublishableConfig`），对齐 jaravel-vendor 既有 vendor 装配模式；token/jsapi_ticket 缓存明确走 core + cache 模块（`CacheManager`/`CacheStore`，`WechatCacheResolver` 共享解析）。

---

## 1. 摘要

当前 `wechat-sdk` 对"消息"的表达方式全部是 `Map<String, Object>`：

- 发送侧：`sendMessage(Map)`、`sendTemplate(..., Map data, Map miniprogram)`、`setMenu(Object)` —— 消息结构靠文档与字符串键约定；
- 接收侧：**完全缺失** —— 没有解析微信推送 XML 的能力，没有签名校验/消息加解密，没有被动回复；
- 响应侧：所有 API 返回原始 `Map`，`errcode != 0` 只打 warn 日志，**调用方拿到的"成功" Map 里可能藏着业务失败**，且 `msgid` 等关键字段需要手工强转提取。

本设计引入三类核心构件，把 Map 用法收敛为强类型：

| 构件 | 作用 | 对应 EasyWeChat 5.x |
|---|---|---|
| `message.Message` 体系（11 种客服消息 + 基础类） | 发送侧类型化：客服消息 / 被动回复 / 菜单消息 / 小程序卡片 | `Kernel/Messages/*` |
| `server.*` 体系（7 种接收消息 + 事件 + `MessageParser`） | 接收侧类型化：解析微信推送 XML 为对象 | `OfficialAccount/Events/*`（5.x 由 `MessageParser` 分发） |
| `response.WeChatResponse` | 响应侧统一：`errcode/errmsg/msgid` + `isSuccess()` + 异常 | `Kernel/Response` |

配套补齐：`menu.Menu/MenuItem`、`template.TemplateMessage / SubscriptionNotice / TemplateDataItem`、`mini.MiniProgramSession / MiniSubscribeMessage`、`crypto.WxBizMsgCrypt`（消息加解密）、`WeChatServer`（验签/解密/解析/被动回复一体）。

**兼容性承诺**（已被实现指令修订，见文首）：~~现有公开方法签名与返回类型全部保留（标记 `@Deprecated`，内部改为委托新实现），~~ **实际落地：旧 Map 裸接口全部移除，无过渡期**——本项目为 vendor 组件、尚无外部消费方，按"直接去掉"处理。

---

## 2. 现状与问题

### 2.1 现状清单

现有 5 个主类：

| 类 | 职责 | 问题 |
|---|---|---|
| `WechatProperties` | `jaravel.wechat.*` 配置（已含 `token`/`aes-key` 字段，但目前没有任何代码使用） | 配置位已占好，能力未落地 |
| `AccessTokenManager` | token 获取/缓存/刷新（`/cgi-bin/token`） | 仅 legacy token 接口；`errcode!=0` 有抛异常（此点合格） |
| `OfficialAccountService` | 用户/模板/菜单/标签/素材/客服/JSSDK | 全部 Map 化；`setMenu(Object)` 甚至不声明类型；`errcode` warn 后仍返回；无接收消息能力 |
| `MiniProgramService` | `jscode2session` / token / 订阅消息 | Map 化；失败时（返回 `errcode/errmsg`，无 `session_key`）返回的 Map "看起来成功了" |
| `WechatAutoConfiguration` | Bean 装配 | 无问题 |

### 2.2 对微信官方文档的缺口扫描（2026-08 文档快照）

**公众号（subscription/service account）：**

| 官方能力 | 端点 / 形态 | SDK 现状 | 结论 |
|---|---|---|---|
| 接收普通消息（text/image/voice/video/shortvideo/location/link） | 推送 XML（含 MsgId/MsgDataId/Idx） | ❌ 无 | **缺失** |
| 接收事件推送（subscribe/unsubscribe/SCAN/LOCATION/CLICK/VIEW + EventKey/Ticket） | 推送 XML | ❌ 无 | **缺失** |
| 订阅通知事件（subscribe_msg_sent_event / subscribe_msg_change_event） | 推送 XML（嵌套 List） | ❌ 无 | **缺失** |
| 被动回复用户消息 | 返回 XML，5 秒时限，可"回复空串"退避 | ❌ 无 | **缺失** |
| 消息加解密（Encrypt/MsgSignature，AES-256-ECB） | GET 验签 + 推送/回复 | ❌ 无（`aes-key` 配置位闲置） | **缺失** |
| 客服消息 11 种（text/image/voice/video/music/news/mpnews/mpnewsarticle/msgmenu/wxcard/miniprogrampage）+ `customservice` + `aimsgcontext` | POST `message/custom/send` | ⚠️ 仅 `sendMessage(Map)` 裸透传 | **未类型化** |
| 客服会话管理（创建/关闭会话、聊天记录、输入状态） | `custom/kf/session`、`custom/message/list` | ⚠️ 仅 `sendTyping` | 部分缺失（P4 可选） |
| 模板消息（服务号，`template/send`，data→`{value,color}`） | POST | ⚠️ Map 透传 | **未类型化** |
| 公众号订阅通知（`template/subscribe`，含 `title`/`scene`，data→`{value,color}`） | POST | ❌ 无 | **缺失**（新公众号推通知的官方通道） |
| 自定义菜单（button+sub_button；type：click/view/miniprogram/scancode_*/pic_*/location_select/media_id/article_id/article_view_limited） | `menu/create|get|delete` | ⚠️ `setMenu(Object)` 裸透传；`getMenu()` 返回 Map 无法回读结构 | **未类型化** |
| 用户管理（单个 `user/info`、标签 CRUD/batch） | ✅ 已有 | ⚠️ 返回 Map | **未类型化**（User/Tag） |
| 批量获取用户 `user/getall` / `user/info/batch` / 备注查询 `updateremark` get | POST | ❌ 无 | P4 可选 |
| 稳定版接口调用凭据 stable_token（POST `stable_token`，更高频控） | GET→POST 升级 | ❌ 仅 legacy | **建议补**（配置开关） |
| 公众号二维码（`qrcode/create` 临时/永久） | POST | ❌ 无 | P4 可选 |

**小程序：**

| 官方能力 | 端点 | SDK 现状 | 结论 |
|---|---|---|---|
| 登录凭证校验 `jscode2session`（返回 openid/session_key/unionid；失败仅 errcode/errmsg） | GET `sns/jscode2session` | ⚠️ 返回裸 Map | **未类型化**（需要失败即异常） |
| 订阅消息（`subscribe/send`，data→`{value}`，`miniprogram_state`/`lang`） | POST | ⚠️ Map 透传 | **未类型化** |
| 素材上传/下载（`media/upload` 临时、`material/add_material` 永久、`material/batchget`） | POST | ❌ 无（公众号侧仅图片） | P4 可选 |
| 小程序码/链接（`getwxacode*`/`generateUrlLink`/`generateScheme`） | POST | ❌ 无 | P4 可选 |

**横切问题（最重要的一条）：**

- 业务失败不可见。微信 API 的"成功"以 HTTP 200 + `errcode!=0` 表示（如 40001 token 失效、40003 非法 openid、45008 图文超限）。现实现对此仅 `logger.warn`，调用方必须自己 `map.get("errcode")` 强转 int 判断。这是 Map 化最大的实际危害。
- `msgid`（模板/订阅/客服消息发送回执）需要手工 `Long.valueOf((String) map.get("msgid"))` 提取，类型与取值方式全靠记忆。

---

## 3. 参照：EasyWeChat 5.x 消息模型

本项目 README 即声明对齐 EasyWeChat。其消息模型的五个关键机制（5.x `Kernel/Messages`，已核对源码）：

1. **抽象基类 `Message`**：`type`（msgtype 字符串）、`to/from/id`、`properties`（属性清单）、`jsonAliases`（内部属性名→wire JSON 键名映射，如 `musicurl→url`、`hqmusicurl→hq_url`）。
2. **双序列化**：`transformForJsonRequest()` → `{msgtype: type, [type]: {…props}, touser: …}`；`transformToXml()` → 被动回复 XML 数组。同一消息类同时支持"主动发 JSON"与"被动回 XML"。
3. **必填校验**：基类 `checkRequiredAttributes()`，子类声明 `required` 列表（如 `MiniProgramPage` 要求 `thumb_media_id/appid/pagepath`），缺字段发送前即抛 `InvalidArgumentException`（快速失败，而不是等微信返回 47001）。
4. **具体消息类极简**：`Text` 只有 `type='text'` + 构造参数 + `toXmlArray()`；`Music` 多一个 `jsonAliases`。新增消息类型 = 一个 20 行以内的小类。
5. **`MessageParser`**：按 `MsgType` 把接收 XML 分发为具体消息对象，接收侧与发送侧共享类型词汇。

本设计将上述机制映射为 Java（无 trait/magic 的显式写法），并补上 EasyWeChat 5.x 中属于"约定"而非"代码"的必填/别名表，使其可测试。

---

## 4. 包结构设计

```
com.weacsoft.jaravel.vendor.wechat
├── （现有，保留）
│   ├── WechatProperties
│   ├── AccessTokenManager          ← 扩展 stable_token
│   ├── OfficialAccountService      ← 新增类型化重载，旧 Map 方法 @Deprecated
│   ├── MiniProgramService          ← 同上
│   ├── WechatAutoConfiguration
│   └── WechatPublish*Config
├── response/
│   ├── WeChatResponse              ← 响应统一封装（errcode/errmsg/msgid/raw/as(T)）
│   └── WechatApiException          ← 业务失败异常（继承 RuntimeException）
├── message/                        ← 发送侧（客服消息 + 被动回复共享）
│   ├── Message                     ← 抽象基类
│   ├── Text, Image, Voice, Video, Music
│   ├── News + NewsItem
│   ├── MpNews, MpNewsArticle
│   ├── MenuMessage + MenuItem      ← msgmenu（客服菜单消息）
│   ├── WeChatCard                  ← wxcard
│   └── MiniProgramPage             ← miniprogrampage
├── server/                         ← 接收侧
│   ├── ServerMessage               ← 基类（toUser/fromUser/createTime/msgId/msgDataId/idx/…）
│   ├── TextMessage, ImageMessage, VoiceMessage, VideoMessage
│   ├── ShortVideoMessage, LocationMessage, LinkMessage
│   ├── EventMessage                ← 事件（subscribe/unsubscribe/SCAN/LOCATION/CLICK/VIEW，eventKey/ticket）
│   ├── SubscribeMsgSentEvent, SubscribeMsgChangeEvent
│   └── MessageParser               ← XML → 类型化对象 分发器
├── crypto/
│   └── WxBizMsgCrypt               ← 签名校验 + AES 加解密（纯 JDK，java.security + java.util.Base64）
├── WeChatServer                    ← 验签/解密/解析/被动回复 一体化入口（公众号接收消息服务端）
├── menu/
│   ├── Menu                        ← {button: [...]}
│   └── MenuItem                    ← name/type(key|url|appId|pagePath|mediaId|articleId)/subButtons
├── template/
│   ├── TemplateDataItem            ← {value, color}
│   ├── TemplateMessage             ← 服务号模板消息 template/send（现有 sendTemplate 的类型化）
│   └── SubscriptionNotice          ← 公众号订阅通知 template/subscribe（title/scene）
├── user/
│   ├── WeChatUser                  ← user/info 字段类型化
│   └── Tag                         ← tags/get 字段类型化
└── mini/
    ├── MiniProgramSession          ← jscode2session 成功载体（失败走异常）
    └── MiniSubscribeMessage        ← 小程序订阅消息（data→{value}）
```

依赖约束：全部仅依赖 JDK + 现有 `okhttp` + 本模块 `json` 门面；XML 解析/构建用 JDK 自带（接收侧 `javax.xml.stream` StAX，发送侧自写 30 行 `XmlWriter`），**不引入新的第三方依赖**（对齐工程约定 #8）。

---

## 5. 核心类设计

### 5.1 `response.WeChatResponse`（响应统一封装）

所有微信 API 响应（`{errcode, errmsg, msgid?, …}`）统一收敛。

```java
/** 微信 API 标准响应封装。errcode 缺省 -1（部分成功响应无 errcode 字段）。 */
public final class WeChatResponse {
    private final int errcode;            // -1 表示响应体无 errcode 字段
    private final String errmsg;          // 可空
    private final String msgid;           // 消息回执 id（发送类接口才有），可空
    private final Map<String, Object> raw; // 原始响应体（含业务字段），不可变视图

    public WeChatResponse(Map<String, Object> raw);        // 构造内部自动提取三字段
    public static WeChatResponse of(Map<String, Object> raw);

    public boolean isSuccess();            // errcode == -1（无该字段）或 == 0
    public int getErrcode();
    public String getErrmsg();
    public String getMsgId();              // 回执 id（字符串形式，保留微信原始表示）

    /** 业务数据转换：raw 中剔除 errcode/errmsg 后转为目标类型（Json.convert 语义）。 */
    public <T> T as(Class<T> type);

    /** 严格模式：失败时抛 {@link WechatApiException}；成功返回自身（链式用）。 */
    public WeChatResponse requireSuccess(String operation);

    @Override public String toString();    // 含 errcode/errmsg/msgid + raw
}
```

```java
/** 微信业务错误（errcode != 0）。保留原始 errcode/errmsg 与操作名，便于排查。 */
public class WechatApiException extends RuntimeException {
    public int getErrcode();
    public String getErrmsg();
    // message 形如: "sendCustomerMessage 业务失败: errcode=40001, errmsg=invalid credential"
}
```

**默认语义决策**：服务层方法**默认不抛业务异常**，返回 `WeChatResponse`（调用方显式 `isSuccess()/requireSuccess()`）——与 EasyWeChat 5.x `Response::isError()` 的"惰性检查"语义一致；但 `jscode2session` 例外：失败响应**不携带 `session_key`**，无法"惰性检查"出有用值，直接抛 `WechatApiException`。此差异在 Javadoc 中显式声明。

### 5.2 `message.Message` 基类与消息类族（发送侧）

```java
/** 发送侧消息抽象基类：双序列化（JSON 请求体 / 被动回复 XML 片段）+ 必填校验。 */
public abstract class Message {

    /** 各消息类的 wire 类型名（msgtype / MsgType），如 "text"、"miniprogrampage"。 */
    public abstract String getType();

    /** 接收者 openid（touser）。被动回复时由 ServerMessage 自动回填，可空。 */
    protected String to;

    /** 发送者（被动回复时的 from = 原消息 toUser），可空。 */
    protected String from;

    /** 必填属性名（下划线 wire 名）。序列化前逐一非空校验，缺省空数组表示无必填。 */
    public String[] requiredWireProperties() { return new String[0]; }

    /** 内部属性名 → wire JSON 键名 的别名表（如 Music 的 musicurl→musicUrl）。默认空。 */
    protected Map<String, String> jsonAliases() { return Map.of(); }

    /** 必填校验：缺必填 → IllegalArgumentException("属性 \"x\" 不能为空")（快速失败）。 */
    public void checkRequired() { }

    /**
     * 序列化为客服消息 JSON 请求体：
     * { "touser":…, "msgtype": getType(), <type>: <payload()> }（touser/from 为可空字段，缺失时省略）。
     */
    public Map<String, Object> toJsonBody();

    /** 序列化核心：各消息类返回自身 {wire-key: value} 结构。 */
    protected abstract Map<String, Object> payload();

    /**
     * 被动回复 XML 片段（<xml> 之外的内部节点，PascalCase 键；数值字段不加 CDATA，
     * 文本字段包 CDATA），由 WeChatServer 拼 ToUserName/FromUserName/CreateTime 头。
     * 默认抛 UnsupportedOperationException（仅客服消息类才需要）。
     */
    public Map<String, Object> toXmlNode() {
        throw new UnsupportedOperationException("该类不支持被动回复: " + getClass().getName());
    }
}
```

消息类族（每类 ≤ 40 行；字段即官方 wire 字段，驼峰属性 + wire 下划线键）：

| 类 | type | wire 属性 | 必填 |
|---|---|---|---|
| `Text` | `text` | `content` | `content` |
| `Image` | `image` | `mediaId` | `mediaId` |
| `Voice` | `voice` | `mediaId` | `mediaId` |
| `Video` | `video` | `mediaId, thumbMediaId, title?, description?` | `mediaId, thumbMediaId` |
| `Music` | `music` | `title, description, musicUrl, hqMusicUrl?, thumbMediaId`（aliases: `musicurl→musicUrl`, `hqmusicurl→hqMusicUrl`） | `title, description, musicUrl, thumbMediaId` |
| `News(List<NewsItem>)` | `news` | `articles[]`（`NewsItem`: `title, description, picurl, url`） | items 非空 |
| `MpNews` | `mpnews` | `mediaId`（图文 ≤1 条，>1 微信返回 45008；类内 `articles` 校验留文档提示） | `mediaId` |
| `MpNewsArticle` | `mpnewsarticle` | `articleId` | `articleId` |
| `MenuMessage` | `msgmenu` | `headContent?, list[{id, content}], tailContent?` | `list` 非空 |
| `WeChatCard` | `wxcard` | `cardId` | `cardId` |
| `MiniProgramPage` | `miniprogrampage` | `title, appId, pagePath, thumbMediaId` | `title, appId, pagePath, thumbMediaId` |

```java
    // 示例：Text 与 Music
public final class Text extends Message {
    private final String content;
    public Text(String content) { if (content == null || content.isEmpty())
        throw new IllegalArgumentException("text 消息 content 不能为空"); this.content = content; }
    public String getType() { return "text"; }
    public String getContent() { return content; }
    protected Map<String, Object> payload() { return Map.of("content", content); }
    public Map<String, Object> toXmlNode() { return Map.of("Content", content); }
}

public final class Music extends Message {
    private final String title, description, musicUrl, thumbMediaId;
    private final String hqMusicUrl;
    // 构造 + getter …
    protected Map<String, String> jsonAliases() {
        return Map.of("musicUrl", "musicurl", "hqMusicUrl", "hqmusicurl", "thumbMediaId", "thumb_media_id");
    }
    public String[] requiredWireProperties() { return {"title", "description", "musicurl", "thumb_media_id"}; }
    protected Map<String, Object> payload() { /* 按 aliases 展开，null 省略 */ }
    public Map<String, Object> toXmlNode() { /* Music:{Title,Description,MusicUrl,HQMusicUrl?,ThumbMediaId} */ }
}
```

**附：`customservice`/`aimsgcontext`**（可选包装，`WeChatResponse` 同层）：
`Message.wrapCustomService(String kfAccount)` 与 `Message.wrapAiContext(boolean isAiMsg)` 两个实例方法，把这两个正交字段挂在请求体顶层而不是消息类内部——与官方"顶层字段"的 JSON 位置一致，避免污染消息结构。

### 5.3 接收侧：`server.*` + `MessageParser`

基类 `ServerMessage`（对应微信推送 XML 公共字段）：

```java
public abstract class ServerMessage {
    private String toUserName;      // 开发者微信号
    private String fromUserName;    // 发送方 openid
    private long createTime;        // 秒级时间戳
    private String msgType;         // text/image/voice/video/shortvideo/location/link/event
    private long msgId;             // MsgId（建议用它做幂等排重；微信 5 秒未响应会重试 3 次）
    private String msgDataId;       // 来自图文文章时才有
    private int idx;               // 多图文第几篇，从 1 开始
    // getter + 包内 setter（由 MessageParser 填充）
}
```

具体接收类（字段直接映射推送 XML）：

| 类 | 额外字段 |
|---|---|
| `TextMessage` | `content` |
| `ImageMessage` | `picUrl, mediaId` |
| `VoiceMessage` | `mediaId, format, mediaId16k?` |
| `VideoMessage` | `mediaId, thumbMediaId` |
| `ShortVideoMessage` | `mediaId, thumbMediaId` |
| `LocationMessage` | `locationX(double), locationY(double), scale(int), label` |
| `LinkMessage` | `title, description, url` |
| `EventMessage` | `event, eventKey?, ticket?` + 便捷方法 `isSubscribe()/isUnsubscribe()/isScan()/isLocation()/isClick()/isView()`；LOCATION 事件额外 `latitude/longitude/precision`(double)、SCAN 事件 `eventKey/qrcodeTicket` |
| `SubscribeMsgSentEvent` | `items: List<Item{templateId, msgId, errorCode, errorStatus}>`（订阅通知下发结果事件） |
| `SubscribeMsgChangeEvent` | `items: List<Item{templateId, status}>`（`reject`=用户拒收） |

```java
/** XML 推送 → 类型化接收消息 的分发器（对齐 EasyWeChat 5.x MessageParser）。 */
public final class MessageParser {

    /** 明文 XML 解析。未知 MsgType → UnsupportedMessageException（携带原始 Map，便于扩展）。 */
    public static ServerMessage parse(String xml);

    /** Map 解析（调用方已自行解析为 Map 时复用；parse(xml) 内部委托此方法）。 */
    public static ServerMessage parseMap(Map<String, Object> map);

    /** 便捷断言：isText(msg) 等 instanceof 判断，避免调用侧强转。 */
    public static boolean isText(ServerMessage msg);
    public static TextMessage asText(ServerMessage msg);
    // … isImage/asImage, isEvent/asEvent …
    public static boolean isEvent(ServerMessage msg);
    public static EventMessage asEvent(ServerMessage msg);
}
```

### 5.4 `crypto.WxBizMsgCrypt`（消息加解密）与 `WeChatServer`（接收消息服务端）

加解密规范（微信"消息加解密说明"，实现时以官方示例代码向量做回归）：

- AES key = `Base64.decode(EncodingAESKey + "=")`；算法 `AES/ECB/NoPadding`；
- 明文结构 = `random(16B) || msg_len(4B 大端) || msg || receiveid`；解密后按此切分；
- 签名 = `sha1( sort( token, timestamp, nonce, encryptBase64String ).concat )`。

```java
/** 微信消息加解密（纯 JDK，无第三方依赖）。不可变，可跨线程共享。 */
public final class WxBizMsgCrypt {
    public WxBizMsgCrypt(String token, String encodingAesKey, String appId);

    /** 推送请求签名校验：sha1(sort(token,timestamp,nonce,encrypt)) 是否等于 msgSignature。 */
    public boolean verifySignature(String timestamp, String nonce, String encrypt, String msgSignature);

    /** 解密 Encrypt(密文 Base64) → 明文 msg；校验末尾 receiveid == appId。 */
    public String decrypt(String encrypted);

    /** 被动回复：msg → Encrypt Base64 + msgSignature（配 timestamp/nonce）。 */
    public String encrypt(String replyMsg);
    public String sign(String timestamp, String nonce, String encrypt);
}
```

```java
/** 公众号"接收消息"服务端入口：验签 → (可选)解密 → 解析 → (可选)加密被动回复。 */
public class WeChatServer {

    private final WechatProperties.OfficialAccountConfig config; // token + aesKey + appId
    private final WxBizMsgCrypt crypt;        // aesKey 配置为空时为 null（明文模式）
    private final String aesMode;             // "none" | "safe"（官方两种加密模式）

    /**
     * 处理一次推送（POST）。query: timestamp/nonce/msg_signature; body: XML。
     * 返回应答字符串（明文模式为回复 XML 或空串""；加密模式为 Encrypt 应答 XML）。
     * 注意：微信 5 秒未响应会重试 3 次 —— 处理超时建议直接 return ""。
     */
    public String postHandler(Map<String, String> query, String bodyXml,
                              BiFunction<ServerMessage, WeChatServer, Message> responderOrNull);

    /** 首次接入 GET 验签：校验签名后返回解密后的 echostr。 */
    public String getEcho(Map<String, String> query);

    public ServerMessage parseForTesting(String xml);  // 单测友好入口
}
```

**被动回复**约定：`responder` 返回的 `Message` 若实现 `toXmlNode()`，则拼成
`<xml><ToUserName>原from</ToUserName><FromUserName>原to</FromUserName><CreateTime>now</CreateTime><MsgType>…</MsgType>…</xml>`；
加密模式下整体走 `WxBizMsgCrypt.encrypt` + 签名。**`responder == null` 时按微信规范返回空串**（不触发重试），这是 5 秒时限下的官方推荐退避。

XML 构建细节（`XmlWriter` 小工具，发送侧复用）：

- 文本值：`<![CDATA[value]]>`（值内转义 `]]>` 为 `]]]]><![CDATA[>`）；
- 数值（`CreateTime`/`Location_X`… 按官方样例不加 CDATA，数值型不加 `ArticleCount` 等）；
- 特殊字符 `& < >` 转义（CDATA 内仅 `]]>` 需处理）。

### 5.5 `menu.Menu / MenuItem`（自定义菜单）

```java
public final class Menu {
    private final List<MenuItem> buttons;        // 顶层 ≤3 个
    public Menu(List<MenuItem> buttons) { /* 校验 ≤3 */ }
    public List<MenuItem> getButtons();
    public Map<String, Object> toJson() { return Map.of("button", buttons.stream().map(MenuItem::toJson).toList()); }
    /** 从 getMenu() 响应的 menu 节点还原（含 null 字段容错）。 */
    public static Menu fromJsonMap(Map<String, Object> menuNode);
}

public final class MenuItem {
    // fluent 构造，字段按官方 button 结构：
    private String name;               // 必填
    private String type;               // click|view|miniprogram|scancode_picurl|scancode_waitmsg
    //                       |pic_syslocation|pic_photo_or_album|pic_sysphoto_or_album|location_select
    //                       |media_id|article_id|article_view_limited|view_limited
    private String key;                // click / scancode_* / location_select
    private String url;                // view
    private String appId;              // miniprogram
    private String pagePath;           // miniprogram
    private String mediaId;            // media_id / view_limited
    private String articleId;          // article_id / article_view_limited
    private List<MenuItem> subButtons; // 子菜单（顶层 ≤3，子级 ≤5，两级）

    public String getName();  // …其余 getter
    public MenuItem name(String v);  // fluent 链
    public MenuItem click(String key);        // type=click + key
    public MenuItem view(String url);         // type=view + url
    public MenuItem miniprogram(String appId, String pagePath, String url);
    public MenuItem sub(List<MenuItem> subs);
    public Map<String, Object> toJson();      // null 字段省略；sub_button 键名按官方
    public static MenuItem fromJsonMap(Map<String, Object> node);
}
```

### 5.6 `template.*`（模板消息 + 订阅通知）

```java
/** 模板消息数据项 {value, color}；color 可空。 */
public final class TemplateDataItem {
    private final String value;
    private final String color;
    public static TemplateDataItem of(String value);
    public static TemplateDataItem of(String value, String color);
    public String getValue(); public String getColor();
    public Map<String, Object> toJson();   // {"value":…, "color":…}（color 空省略）
}

/** 服务号模板消息（现有 sendTemplate 的类型化，端点 template/send）。 */
public final class TemplateMessage {
    private String touser;                 // 必填
    private String templateId;             // 必填
    private String clientMsgId;            // 客户端自定义消息 ID（查重用，官方支持）
    private Map<String, TemplateDataItem> data;   // 必填，key 为模板变量名
    private String url;                    // 可选
    private MiniProgramTarget miniProgram; // 可选：{appid, pagepath}
    public WeChatResponse via(OfficialAccountService svc, String configName); // 便捷发送
    public Map<String, Object> toJson();
}

/** 公众号订阅通知（template/subscribe，新通知通道；data 仅 content 一项为官方规范）。 */
public final class SubscriptionNotice {
    private String touser;      // 必填
    private String templateId;  // 必填
    private String title;       // 订阅通知标题（官方必填项之一，≤20 字）
    private String scene;       // 场景值（如 "1000"）
    private String url;         // 可选
    private MiniProgramTarget miniProgram; // 可选（url 与 miniprogram 同传时优先小程序）
    private TemplateDataItem content;      // data.content {value, color}
    public Map<String, Object> toJson();
}
```

### 5.7 `user.WeChatUser` / `user.Tag`

```java
public final class WeChatUser {
    private String openid; private String nickname; private int sex;
    private String province, city, country;
    private boolean subscribed; private long subscribeTime;
    private String remark;
    private String unionid;     // 已绑定开放平台时才有
    private Map<String, Object> raw; // 全量字段（含 profile 等扩展位）
    public static WeChatUser from(Map<String, Object> raw);
    public boolean isSubscribed();
}

public final class Tag {
    private int id; private String name; private long count; // count：有该标签的用户数
    public static Tag from(Map<String, Object> raw);
    public static List<Tag> listFrom(Map<String, Object> raw); // tags/get 的 tagid_list
}
```

### 5.8 `mini.*`（小程序）

```java
/** jscode2session 成功载体。失败（errcode!=0）由 MiniProgramService 直接抛 WechatApiException。 */
public final class MiniProgramSession {
    private final String openid;
    private final String sessionKey;   // 绝不下发前端（官方安全红线）
    private final String unionid;      // 绑定开放平台时才有
    public static MiniProgramSession from(Map<String, Object> raw); // 校验 sessionKey/openid 非空
    public String getOpenid(); public String sessionKey(); public String unionId();
}

/** 小程序订阅消息（subscribe/send）。 */
public final class MiniSubscribeMessage {
    private String touser;          // 必填
    private String templateId;      // 必填
    private PageDataItem data;      // data: {key: {value}}（value 必填）
    private String page;            // 跳转页面，可空
    private String miniProgramState; // formal|trial|developer，默认 formal
    private String lang;            // 默认 zh_CN
    public Map<String, Object> toJson();
}
```

### 5.9 `AccessTokenManager` 扩展（stable_token）

- 新增配置 `jaravel.wechat.token-mode`：`legacy`（默认，现状）/ `stable`（POST `/cgi-bin/stable_token`，官方推荐的提高频控版本，请求体 `{grant_type, appid, secret}`，响应同形）；
- 两种模式共享同一套缓存 key（`wechat:access_token:{appId}`）与 TTL 缓冲逻辑，仅取数端点不同；
- 既有 19 个单元测试（含 TTL 边界、回退策略）不动，新增 2 个 stable 模式用例（mock 请求体断言 POST + body 含 `grant_type=client_credential`）。

---

## 6. 服务层 API 设计（新签名）

`OfficialAccountService` 新增（旧方法 `@Deprecated`，内部委托新实现）：

```java
// ===== 发送侧（类型化）=====
public WeChatResponse sendCustomerMessage(Message msg) ;                       // 客服消息 11 类
public WeChatResponse sendCustomerMessage(Message msg, String configName);
public WeChatResponse sendTemplateMessage(TemplateMessage msg);                // 服务号模板消息
public WeChatResponse sendSubscriptionNotice(SubscriptionNotice notice);       // 公众号订阅通知
public WeChatResponse setMenu(Menu menu);                                      // 菜单（类型化，替代 setMenu(Object)）
public WeChatResponse deleteMenu();                                            // 补 menu/delete
public WeChatResponse sendTyping(String openid, boolean typing);               // 保留 int 版并加 boolean 版

// ===== 接收侧（新增）=====
public String handlePostedMessage(Map<String,String> query, String bodyXml,
                                  BiFunction<ServerMessage, WeChatServer, Message> responder);
public String handleGetEcho(Map<String,String> query);

// ===== 响应类型化 =====
public WeChatResponse getUserData(String openid);                              // 旧 Map 版 @Deprecated
public WeChatUser getUser(String openid);                                      // = as(WeChatUser.class)
public WeChatResponse createTag(String name);
public List<Tag> getTags();                                                    // 旧 Map 版 @Deprecated
public WeChatResponse batchTagging(int tagId, List<String> openids);
public WeChatUser ... // updateUserRemark 维持响应型
public Menu getMenuAsMenu();                                                   // getMenu() 的类型化回读
```

`MiniProgramService` 新增：

```java
public MiniProgramSession code2Session(String appId, String code);             // 失败抛 WechatApiException
public MiniProgramSession jscode2session(String appId, String code);           // 同义别名
public WeChatResponse sendSubscribeMessage(String appId, MiniSubscribeMessage msg);
```

**统一内部执行器**（把现在散落在 3 个服务里的 `executeGet/executePostJson/uploadFile/parseResponse` 收敛为一处）：

```java
final class WechatTransport {
    WeChatResponse get(String path, Map<String,String> query, String operation);
    WeChatResponse postJson(String path, Object body, String operation);
    WeChatResponse upload(String path, File file, String formName, String operation);
    // 统一：HTTP 非 2xx → WechatApiException；errcode 提取进 WeChatResponse；日志格式不变
}
```

---

## 7. 配置项

现有 `WechatProperties` 已具备 `token`/`aes-key`，仅需新增：

| 配置 | 默认 | 说明 |
|---|---|---|
| `jaravel.wechat.token-mode` | `legacy` | `legacy`=\`/cgi-bin/token\`，`stable`=\`/cgi-bin/stable_token\` |
| `jaravel.wechat.official-accounts.{name}.message-mode` | `plain` | `plain`=明文推送；`safe`=加密推送（Encrypt+MsgSignature）。控制 `WeChatServer` 行为；`aes-key` 缺省时 `safe` 模式抛配置异常 |

不改动的现有配置：`cache-store`、`official-accounts.*.oauth.*`、`mini-apps.*`、`http.*` 全部保持。

---

## 8. 兼容性策略

1. **旧方法保留**：`sendMessage(Map)`、`sendTemplate(…, Map, Map)`、`setMenu(Object)`、`getUserData()→Map` 等一律保留，签名不变，加 `@Deprecated` + Javadoc 指路；返回 `Map` 的旧方法内部 `new WeChatResponse(raw)` 后 `.raw()` 透传，保证逐字节兼容。
2. **行为微调仅一处且向后兼容**：`parseResponse` 对 `errcode!=0` 仍只 warn 不抛 —— 新方法的 `requireSuccess()` 是**调用方主动**开启严格语义，老调用方无感知。
3. **Bean 签名不变**：4 个 Bean（client/token-manager/oa-service/mini-service）构造依赖不变，`WechatAutoConfiguration` 不需改动即可运行新代码；`WeChatServer` 作为 `OfficialAccountService` 的**方法级**入口（非独立 Bean），避免新增装配面。
4. **依赖零新增**：不加第三方库；`javax.xml.stream` 属 JDK（`java.xml` 模块，JRE 自带），与模块 "JRE 即可运行" 约束一致。
5. **测试契约**：现有 19 个 wechat-sdk 测试（`AccessTokenManagerTest`/`WechatPropertiesTest`）必须零改动全绿；新增测试独立成类。

---

## 9. 测试策略

| 层 | 用例要点 |
|---|---|
| `message/*` 序列化 | 11 类 `toJsonBody()` 结构断言（键序/aliases/null 省略/touser 省略）；`Text/Music/News` 的 `toXmlNode()` CDATA/数值断言；必填缺失 → `IllegalArgumentException` 消息含属性名；`MiniProgramPage` 四必填缺一即抛 |
| `server/*` 解析 | 官方样例 XML 逐类解析（7 种普通 + 6 种事件 + 2 种订阅事件）；未知 MsgType → `UnsupportedMessageException`；`MsgDataId/Idx` 缺省容错 |
| `crypto` | 官方示例代码的测试向量回归（token/aesKey/密文三件套，实现时取微信官方 Sample 仓库向量）；签名错乱/ appId 不匹配/乱序 sort 负例 |
| `WeChatServer` | `plain/safe` 双模式 POST 往返（mock responder 返回 Text → 断言 XML 头与 CDATA）；`responder=null` 返回 `""`；GET echo 验签失败拒绝 |
| `response` | 无 errcode 字段→`isSuccess()==true`；`errcode=40001` → `requireSuccess` 抛异常且异常含 errcode+errmsg+操作名；`as(WeChatUser)` 字段映射 |
| `menu/template` | `Menu.toJson()` ≤3 顶层校验；`MenuItem.sub()` 两级嵌套；`SubscriptionNotice.toJson()` 的 `data.content` 形状 |
| `AccessTokenManager` | 现有 19 个全绿 + stable 模式 2 例（POST 方法 + body 断言） |
| 服务层 | 沿用 Mockito mock OkHttp 模式（现有测试已有 `mockWechatResponse` 范式），为每个新服务方法补 1 例请求体形状断言 |

验收命令：`mvn -pl wechat-sdk test`（全绿）+ `mvn -pl wechat-sdk install` + jaravel demo `mvn compile` 冒烟。

---

## 10. 分阶段实施计划

| 阶段 | 交付 | 依赖 |
|---|---|---|
| **P0 核心** | `WechatResponse`+`WechatApiException`；`WechatTransport` 收敛；`message.Message`+11 消息类；`OfficialAccountService.sendCustomerMessage`；对应测试 | 无 |
| **P1 模板/菜单** | `template.*` 三件（含 `TemplateMessage` 类型化 `sendTemplate` 重载）+ `sendSubscriptionNotice`；`menu.Menu/MenuItem`+`setMenu(Menu)`/`getMenuAsMenu`/`deleteMenu` | P0 |
| **P2 接收侧** | `server.*` 10 类 + `MessageParser`；`crypto.WxBizMsgCrypt`；`WeChatServer`（plain+safe）；`message-mode` 配置 | P0 |
| **P3 小程序** | `MiniProgramSession`（失败即异常）；`MiniSubscribeMessage`+`sendSubscribeMessage`；`token-mode`（stable_token） | P0 |
| **P4 选做**（按需求触发，不排期） | `WeChatUser/Tag` 细化、`user/getall`、`qrcode/create`、素材 `media/upload` 全类型、客服会话管理（create/close session、聊天记录）、OCR | — |

每阶段闭环：设计细化 → 实现 → 单测 → `mvn -pl wechat-sdk test` 全绿 → README 同步（对齐关系表 + 新用法示例）→ 提交。

---

## 11. 明确不做（边界）

- **不做群发消息**（`message/mass/send`，营销性质，需单独权限审核，与本项目"消息通道"定位不符，需要时 P4 再议）；
- **不做微信卡券**业务 API 面（`wxcard` 仅作为客服消息一种类型透传 card_id，卡券生成本身不在 SDK 范围）；
- **不做 OAuth 网页授权流**（`oauth` 配置位已保留，属于独立服务面，另立设计）;
- **不改现有 19 个测试**（契约锁定，见 §8.5）。

---

## 12. 附：官方文档依据（2026-08 浏览快照）

- 公众号接收普通消息 / 事件推送（含 XML 样例与 5 秒重试、MsgId 排重建议）：developers.weixin.qq.com → 服务号「能力接入/基础消息与订阅通知」
- 公众号订阅通知（`template/subscribe`，`title`/`scene`，data.content{value,color}）：服务号「能力接入/基础消息与订阅通知/发送一次性订阅消息」
- 客服消息（11 类型 + `customservice` + `aimsgcontext`，2025-11-26 变更日志）：服务号「服务端 API/客服消息/发送客服消息」
- 自定义菜单（button 全类型 + sub_button 样例）：服务号「服务端 API/自定义菜单/创建自定义菜单」
- 消息加解密说明（AES/签名规范）：服务号「开发指南/消息与事件推送/消息加解密说明」
- 小程序登录 `jscode2session`（openid/session_key/unionid 响应形）：小程序「服务端/开放能力/用户信息」
- 小程序订阅消息（`subscribe/send`）：小程序「消息」
- 稳定版接口调用凭据（`stable_token`）：服务号「基础接口/获取稳定版接口调用凭据」
- EasyWeChat 5.x 消息模型（`Kernel/Messages/*`、`OfficialAccount/TemplateMessage/Client.php`）：w7corp/easywechat@5.x
