# wechat-sdk AI-API Reference

> Module: `wechat-sdk` | Package: `com.weacsoft.jaravel.vendor.wechat` | Version: 0.1.0

## Overview

wechat-sdk 模块提供了微信开发平台 API 封装，支持公众号（OfficialAccount）和小程序（MiniProgram）两种应用类型。`AccessTokenManager` 负责获取和缓存 access_token（支持 Redis 分布式缓存）；`OfficialAccountService` 封装公众号用户、菜单、模板消息等 API；`MiniProgramService` 封装小程序登录（code2session）、订阅消息等 API。所有 HTTP 请求通过 `RestTemplate` 发送，响应通过 Jackson 解析。

## Classes & Interfaces

### AccessTokenManager
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.wechat`
- **Description**: access_token 管理器。调用微信 `cgi-bin/token` 接口获取 access_token，缓存到内存（默认）或 Redis（当 `RedisManager` 可用时），提前 5 分钟刷新避免过期。支持公众号和小程序两种 token 类型。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `AccessTokenManager` | `RestTemplate restTemplate, WechatProperties properties` | - | 构造 access_token 管理器 |
| `getAccessToken` | `String appType` | `String` | 获取指定类型的 access_token（`mp` 公众号 / `mini` 小程序），缓存未过期则直接返回 |
| `refreshAccessToken` | `String appType` | `String` | 强制刷新 access_token（忽略缓存） |
| `getCacheKey` | `String appType` | `String` | 获取缓存键名 |
| `isExpired` | `String appType` | `boolean` | 检查当前缓存的 access_token 是否已过期 |

#### Usage Example
```java
@Autowired
private AccessTokenManager tokenManager;

// 获取公众号 access_token
String mpToken = tokenManager.getAccessToken("mp");

// 获取小程序 access_token
String miniToken = tokenManager.getAccessToken("mini");

// 强制刷新
String freshToken = tokenManager.refreshAccessToken("mp");
```

### OfficialAccountService
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.wechat`
- **Description**: 微信公众号 API 服务。封装公众号常用接口，包括用户信息获取、自定义菜单管理、模板消息发送、素材管理等。所有接口调用前自动获取有效的 access_token。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `OfficialAccountService` | `RestTemplate restTemplate, AccessTokenManager tokenManager, WechatProperties properties` | - | 构造公众号服务 |
| `getUserInfo` | `String openid` | `Map<String, Object>` | 获取用户基本信息（昵称、头像、性别等） |
| `getUserList` | `String nextOpenid` | `Map<String, Object>` | 获取关注者列表，nextOpenid 为空从头开始 |
| `sendTemplateMessage` | `String openid, String templateId, String url, Map<String, Object> data` | `String` | 发送模板消息，返回消息 ID |
| `createMenu` | `Map<String, Object> menu` | `boolean` | 创建自定义菜单 |
| `getMenu` | - | `Map<String, Object>` | 获取自定义菜单配置 |
| `deleteMenu` | - | `boolean` | 删除自定义菜单 |
| `getCallbackIp` | - | `List<String>` | 获取微信服务器 IP 列表 |
| `getQrcode` | `String sceneStr, boolean isTemporary` | `String` | 生成带参数的二维码 ticket，返回 ticket 字符串 |
| `getQrcodeUrl` | `String ticket` | `String` | 通过 ticket 获取二维码图片 URL |
| `getJsApiTicket` | - | `String` | 获取 jsapi_ticket（用于 JS-SDK 签名） |

#### Usage Example
```java
@Autowired
private OfficialAccountService mpService;

// 获取用户信息
Map<String, Object> userInfo = mpService.getUserInfo("o6_bmjrPTlm6_2sgVt7hMZOPfL2M");

// 发送模板消息
Map<String, Object> data = Map.of(
    "first", Map.of("value", "您有新订单", "color", "#173177"),
    "keyword1", Map.of("value", "ORD-2024-001"),
    "remark", Map.of("value", "请及时处理")
);
String msgId = mpService.sendTemplateMessage(
    "o6_bmjrPTlm6_2sgVt7hMZOPfL2M",
    "template-id-xxx",
    "https://example.com/order/1",
    data
);

// 创建菜单
mpService.createMenu(Map.of(
    "button", List.of(
        Map.of("type", "view", "name", "首页", "url", "https://example.com")
    )
));
```

### MiniProgramService
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.wechat`
- **Description**: 微信小程序 API 服务。封装小程序常用接口，包括登录凭证校验（code2session）、订阅消息发送、内容安全检测等。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `MiniProgramService` | `RestTemplate restTemplate, AccessTokenManager tokenManager, WechatProperties properties` | - | 构造小程序服务 |
| `code2Session` | `String jsCode` | `Map<String, Object>` | 登录凭证校验，返回 openid、session_key、unionid |
| `sendSubscribeMessage` | `String openid, String templateId, String page, Map<String, Object> data` | `String` | 发送订阅消息，返回消息 ID |
| `getPhoneNumber` | `String code` | `String` | 通过手机号授权 code 获取用户手机号 |
| `checkTextSecurity` | `String content` | `boolean` | 文本内容安全检测，返回是否合规 |
| `checkImageSecurity` | `String mediaUrl` | `boolean` | 图片内容安全检测，返回是否合规 |
| `generateQrcode` | `String scene, String page, int width` | `byte[]` | 生成小程序码，返回图片字节数组 |
| `getWxaCodeUnlimit` | `String scene, String page` | `byte[]` | 生成无数量限制的小程序码 |

#### Usage Example
```java
@Autowired
private MiniProgramService miniService;

// 小程序登录
Map<String, Object> session = miniService.code2Session("js_code_from_client");
String openid = (String) session.get("openid");
String sessionKey = (String) session.get("session_key");

// 发送订阅消息
Map<String, Object> data = Map.of(
    "thing1", Map.of("value", "新订单提醒"),
    "amount2", Map.of("value", "￥99.00")
);
String msgId = miniService.sendSubscribeMessage(
    openid, "template-id-xxx", "pages/order/detail", data
);

// 获取手机号
String phone = miniService.getPhoneNumber("phone_code_from_client");
```

### WechatProperties
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.wechat`
- **Annotations**: `@ConfigurationProperties(prefix = "jaravel.wechat")`
- **Description**: 微信 SDK 配置属性，前缀 `jaravel.wechat`。支持公众号和小程序双应用配置。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `getMpAppId` | - | `String` | 获取公众号 AppID |
| `setMpAppId` | `String mpAppId` | `void` | 设置公众号 AppID |
| `getMpSecret` | - | `String` | 获取公众号 AppSecret |
| `setMpSecret` | `String mpSecret` | `void` | 设置公众号 AppSecret |
| `getMpToken` | - | `String` | 获取公众号服务器 Token |
| `setMpToken` | `String mpToken` | `void` | 设置公众号服务器 Token |
| `getMpAesKey` | - | `String` | 获取公众号消息加解密密钥 |
| `setMpAesKey` | `String mpAesKey` | `void` | 设置公众号消息加解密密钥 |
| `getMiniAppId` | - | `String` | 获取小程序 AppID |
| `setMiniAppId` | `String miniAppId` | `void` | 设置小程序 AppID |
| `getMiniSecret` | - | `String` | 获取小程序 AppSecret |
| `setMiniSecret` | `String miniSecret` | `void` | 设置小程序 AppSecret |
| `isUseRedisCache` | - | `boolean` | 是否使用 Redis 缓存 access_token，默认 true |
| `setUseRedisCache` | `boolean useRedisCache` | `void` | 设置是否使用 Redis 缓存 |

#### Usage Example
```yaml
# application.yml
jaravel:
  wechat:
    mp-app-id: wx1234567890abcdef
    mp-secret: your-mp-secret
    mp-token: your-mp-token
    mp-aes-key: your-mp-aes-key
    mini-app-id: wxabcdef1234567890
    mini-secret: your-mini-secret
    use-redis-cache: true
```

### WechatAutoConfiguration
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.wechat`
- **Annotations**: `@AutoConfiguration`, `@ConditionalOnClass(AccessTokenManager.class)`, `@ConditionalOnProperty(prefix = "jaravel.wechat", name = "enabled", havingValue = "true", matchIfMissing = true)`
- **Description**: 微信 SDK 自动装配。创建 `RestTemplate`、`AccessTokenManager`、`OfficialAccountService`、`MiniProgramService` Bean。当 `RedisManager` 存在且 `useRedisCache=true` 时，access_token 缓存到 Redis。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `wechatRestTemplate` | - | `RestTemplate` | 创建微信 API 专用 RestTemplate Bean（`@Bean`, `@ConditionalOnMissingBean`） |
| `accessTokenManager` | `RestTemplate restTemplate, WechatProperties properties` | `AccessTokenManager` | 创建 access_token 管理器 Bean（`@Bean`, `@ConditionalOnMissingBean`） |
| `officialAccountService` | `RestTemplate restTemplate, AccessTokenManager tokenManager, WechatProperties properties` | `OfficialAccountService` | 创建公众号服务 Bean（`@Bean`, `@ConditionalOnMissingBean`） |
| `miniProgramService` | `RestTemplate restTemplate, AccessTokenManager tokenManager, WechatProperties properties` | `MiniProgramService` | 创建小程序服务 Bean（`@Bean`, `@ConditionalOnMissingBean`） |

#### Usage Example
```java
// 自动装配后，直接注入使用
@Autowired
private OfficialAccountService mpService;

@Autowired
private MiniProgramService miniService;
```
