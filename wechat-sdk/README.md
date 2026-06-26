# wechat-sdk 模块（微信 SDK）

> 包名：`com.weacsoft.jaravel.vendor.wechat`
> 对齐 PHP 扩展包：`overtrue/laravel-wechat`（EasyWeChat）

## 模块概述

`wechat-sdk` 模块为 jaravel-vendor 框架提供微信公众号与小程序的 API 访问能力，对齐 PHP 项目的 `WechatService`、`MiniProgramService` 等服务。所有 HTTP 调用使用 OkHttp，JSON 解析使用 Jackson，Access Token / jsapi_ticket 通过 cache 模块的 `CacheStore` 缓存（优先 redis store，未注册时回退 array 内存缓存）。

### PHP 对齐关系

| PHP (EasyWeChat) | Java (wechat-sdk) |
|---|---|
| `WechatService::getApplication()` | `AccessTokenManager.getToken(appId, secret)` |
| `WechatService::getUserData(openid)` | `OfficialAccountService.getUserData(openid)` |
| `WechatService::sendTemplate(...)` | `OfficialAccountService.sendTemplate(...)` |
| `WechatService::getMenu() / setMenu(json)` | `OfficialAccountService.getMenu() / setMenu(menuJson)` |
| `WechatService::createTag / getTag / deleteTag` | `OfficialAccountService.createTag / getTags / deleteTag` |
| `WechatService::batchTagging / batchUnTagging` | `OfficialAccountService.batchTagging / batchUnTagging` |
| `WechatService::uploadImageTemp / uploadImageFull` | `OfficialAccountService.uploadImageTemp / uploadImageFull` |
| `WechatService::downloadImageFull / deleteImageFull` | `OfficialAccountService.downloadImageFull / deleteImageFull` |
| `WechatService::getMaterialList()` | `OfficialAccountService.getMaterialList(type, page, count)` |
| `WechatService::sendMessage(data)` | `OfficialAccountService.sendMessage(data)` |
| `WechatService::sendTyping(openid, command)` | `OfficialAccountService.sendTyping(openid, command)` |
| `WechatService::controllerbuildJsSdkConfig(...)` | `OfficialAccountService.buildJsSdkConfig(...)` |
| `MiniProgramService` | `MiniProgramService` |
| `config/easywechat.php` | `WechatProperties`（`jaravel.wechat.*`） |

## Maven 依赖

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>wechat-sdk</artifactId>
    <version>0.1.0</version>
</dependency>
```

传递依赖：`cache`、`okhttp`、`jackson-databind`、`spring-boot-starter`。

> 若需多实例共享 token / ticket（redis 缓存），需额外引入 `redis-cache` 模块（会自动将 `redis` store 注册到 `CacheManager`）。

## 类总览

```
com.weacsoft.jaravel.vendor.wechat
├── WechatProperties            # 配置属性（jaravel.wechat.*），对齐 config/easywechat.php
├── AccessTokenManager          # Access Token 管理器（基于 cache 模块缓存 token）
├── OfficialAccountService      # 公众号服务（用户/模板/菜单/标签/素材/客服/JSSDK）
├── MiniProgramService          # 小程序服务（登录/token/订阅消息）
└── WechatAutoConfiguration     # Spring Boot 自动装配
```

## 配置属性

配置前缀 `jaravel.wechat`，对应 `WechatProperties` 类，对齐 PHP `config/easywechat.php`。

```yaml
jaravel:
  wechat:
    enabled: true                          # 是否启用微信 SDK，默认 true

    # 公众号配置（对齐 official_account 段）
    official-accounts:
      default:                             # 默认配置（snsapi_base 授权）
        app-id: wx1234567890abcdef
        secret: your-official-secret
        token: your-token
        aes-key: your-aes-key
        oauth:
          scopes: snsapi_base
          callback: /oauth_callback
          enforce-https: true
      snsapi_userinfo:                     # snsapi_userinfo 授权配置
        app-id: wx1234567890abcdef
        secret: your-official-secret
        token: your-token
        aes-key: your-aes-key
        oauth:
          scopes: snsapi_userinfo
          callback: /oauth_callback_userinfo
          enforce-https: true

    # 小程序配置（对齐 mini_app 段）
    mini-apps:
      default:
        app-id: wx7051c4a2a779d651         # 客服小程序
        secret: your-mini-secret
        token: your-mini-token
        aes-key: your-mini-aes-key
        type: 2                            # 2=客服小程序
      wxb33c8c0f6bea3602:                  # 管理端小程序
        app-id: wxb33c8c0f6bea3602
        secret: your-admin-mini-secret
        token: your-admin-mini-token
        aes-key: your-admin-mini-aes-key
        type: 3                            # 3=管理端小程序

    # HTTP 客户端配置
    http:
      timeout: 5.0                         # 超时时间（秒）
      retry: true                          # 是否启用失败重试
```

### 配置项汇总

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `jaravel.wechat.enabled` | boolean | `true` | 是否启用微信 SDK |
| `jaravel.wechat.official-accounts.{name}.app-id` | String | - | 公众号 AppID |
| `jaravel.wechat.official-accounts.{name}.secret` | String | - | 公众号 AppSecret |
| `jaravel.wechat.official-accounts.{name}.token` | String | - | 消息校验 Token |
| `jaravel.wechat.official-accounts.{name}.aes-key` | String | - | 消息加解密密钥 |
| `jaravel.wechat.official-accounts.{name}.oauth.scopes` | String | `snsapi_base` | OAuth 授权作用域 |
| `jaravel.wechat.official-accounts.{name}.oauth.callback` | String | - | OAuth 回调地址 |
| `jaravel.wechat.official-accounts.{name}.oauth.enforce-https` | boolean | `true` | 是否强制 HTTPS |
| `jaravel.wechat.mini-apps.{name}.app-id` | String | - | 小程序 AppID |
| `jaravel.wechat.mini-apps.{name}.secret` | String | - | 小程序 AppSecret |
| `jaravel.wechat.mini-apps.{name}.token` | String | - | 消息校验 Token |
| `jaravel.wechat.mini-apps.{name}.aes-key` | String | - | 消息加解密密钥 |
| `jaravel.wechat.mini-apps.{name}.type` | int | `0` | 业务类型（2=客服，3=管理端） |
| `jaravel.wechat.http.timeout` | double | `5.0` | HTTP 超时（秒） |
| `jaravel.wechat.http.retry` | boolean | `true` | 是否启用失败重试 |

## 使用示例

### 1. 注入服务

```java
@Autowired
private OfficialAccountService officialAccountService;

@Autowired
private MiniProgramService miniProgramService;
```

### 2. 获取用户信息

```java
// 使用默认配置
Map<String, Object> userInfo = officialAccountService.getUserData("o6_bmjrPTlm6_2sgVt7hMZOPfL2M");

// 指定配置（如 snsapi_userinfo）
Map<String, Object> userInfo = officialAccountService.getUserData("o6_bmjrPTlm6_2sgVt7hMZOPfL2M", "snsapi_userinfo");
```

### 3. 发送模板消息

```java
Map<String, Object> data = new HashMap<>();
data.put("first", Map.of("value", "您好，您有一份新订单", "color", "#173177"));
data.put("keyword1", Map.of("value", "订单号123456", "color", "#173177"));
data.put("keyword2", Map.of("value", "2026-06-22 10:00", "color", "#173177"));
data.put("remark", Map.of("value", "请及时处理", "color", "#173177"));

Map<String, Object> miniprogram = Map.of("appid", "wx7051c4a2a779d651", "pagepath", "pages/order/detail");

Map<String, Object> result = officialAccountService.sendTemplate(
        "TEMPLATE_ID",
        "o6_bmjrPTlm6_2sgVt7hMZOPfL2M",
        data,
        "https://example.com/order/123",
        miniprogram
);
```

### 4. 菜单管理

```java
// 获取菜单
Map<String, Object> menu = officialAccountService.getMenu();

// 创建菜单
Map<String, Object> menuJson = Map.of(
    "button", List.of(
        Map.of("type", "click", "name", "今日歌曲", "key", "V1001_TODAY_MUSIC"),
        Map.of("type", "view", "name", "关于我们", "url", "https://example.com/about")
    )
);
Map<String, Object> result = officialAccountService.setMenu(menuJson);
```

### 5. 标签管理

```java
// 创建标签
Map<String, Object> createResult = officialAccountService.createTag("VIP用户");

// 获取标签列表
Map<String, Object> tags = officialAccountService.getTags();

// 批量打标签
Map<String, Object> tagResult = officialAccountService.batchTagging(101,
        List.of("openid1", "openid2", "openid3"));

// 删除标签
Map<String, Object> delResult = officialAccountService.deleteTag(101);
```

### 6. 素材管理

```java
// 上传临时图片
Map<String, Object> tempResult = officialAccountService.uploadImageTemp("/path/to/image.jpg");

// 上传永久图片
Map<String, Object> fullResult = officialAccountService.uploadImageFull("/path/to/image.jpg");

// 获取永久素材
Map<String, Object> material = officialAccountService.downloadImageFull("MEDIA_ID");

// 删除永久素材
Map<String, Object> delResult = officialAccountService.deleteImageFull("MEDIA_ID");

// 获取素材列表
Map<String, Object> list = officialAccountService.getMaterialList("image", 0, 20);
```

### 7. 客服消息

```java
// 发送文本客服消息
Map<String, Object> msg = Map.of(
    "touser", "o6_bmjrPTlm6_2sgVt7hMZOPfL2M",
    "msgtype", "text",
    "text", Map.of("content", "您好，请问有什么可以帮您？")
);
Map<String, Object> result = officialAccountService.sendMessage(msg);

// 发送输入状态（0=正在输入，1=取消输入）
officialAccountService.sendTyping("o6_bmjrPTlm6_2sgVt7hMZOPfL2M", 0);
```

### 8. JSSDK 配置

```java
// 构建 JSSDK 配置（前端用于 wx.config）
Map<String, Object> config = officialAccountService.buildJsSdkConfig(
    "https://example.com/page",                          // 当前页面 URL
    List.of("chooseImage", "previewImage", "uploadImage"), // JS 接口列表
    List.of("wx-open-launch-app"),                        // 开放标签列表
    false                                                 // 是否调试模式
);
// 返回: {appId, timestamp, nonceStr, signature, jsApiList, openTagList, debug}
```

### 9. 小程序登录

```java
// 小程序登录凭证校验（前端 wx.login 获取 code 后调用）
Map<String, Object> session = miniProgramService.jscode2session(
    "wx7051c4a2a779d651",  // 小程序 AppID
    "js_code_from_frontend" // 前端 wx.login 返回的 code
);
// 返回: {openid, session_key, unionid?}
```

### 10. 小程序订阅消息

```java
Map<String, Object> data = new HashMap<>();
data.put("thing1", Map.of("value", "订单已发货"));
data.put("time2", Map.of("value", "2026-06-22 10:00"));

Map<String, Object> result = miniProgramService.sendTemplateMessage(
    "wx7051c4a2a779d651",           // 小程序 AppID
    "openid_from_jscode2session",   // 接收者 openid
    "TEMPLATE_ID",                  // 订阅消息模板 ID
    data,                           // 模板数据
    "pages/order/detail"            // 跳转页面
);
```

## Access Token 缓存机制

`AccessTokenManager` 负责管理微信 access_token 的获取与缓存，缓存能力委托给 cache 模块的 `CacheStore`：

1. **基于 cache 模块**：通过 `CacheManager` 解析 `CacheStore`，优先使用 `redis` store（多实例共享 token），未注册时回退到 `array` 内存 store
2. **缓存键**：`wechat:access_token:{appId}`（jsapi_ticket 缓存键为 `wechat:jsapi_ticket:{appId}`）
3. **TTL 缓冲**：缓存 TTL = `expires_in - 300`（提前 5 分钟过期），防止临界点 token 失效
4. **强制刷新**：可通过 `refreshToken(appId, secret)` 忽略缓存强制刷新

> 微信 access_token 每天获取限额 2000 次，务必使用缓存避免超限。
>
> 默认仅依赖 `cache` 模块（array 内存缓存，进程级）。若需多实例共享，引入 `redis-cache` 模块后，`redis` store 会自动注册并被优先使用，无需修改任何代码或配置。

## 线程安全

| 组件 | 线程安全机制 |
|---|---|
| `AccessTokenManager` | 无状态单例，缓存委托给 `CacheStore`（array 基于 `ConcurrentHashMap`，redis 基于 Redis），OkHttp/ObjectMapper 线程安全 |
| `OfficialAccountService` | 无状态单例，jsapi_ticket 缓存委托给 `CacheStore` |
| `MiniProgramService` | 无状态单例，所有字段构造后不可变 |
| `WechatProperties` | Spring Boot 配置属性绑定，启动后只读 |
