# wechat-sdk AI-API Reference

> Module: `wechat-sdk` | Package: `com.weacsoft.jaravel.vendor.wechat` | Version: 0.1.0

## Overview

wechat-sdk 模块提供了微信开发平台 API 封装，支持公众号（OfficialAccount）和小程序（MiniProgram）两种应用类型。`AccessTokenManager` 负责获取和缓存 access_token（基于 cache 模块的 `CacheStore`，优先 redis、回退 array）；`OfficialAccountService` 封装公众号用户、菜单、模板消息、JSSDK 等 API；`MiniProgramService` 封装小程序登录（jscode2session）、订阅消息等 API。所有 HTTP 请求通过 OkHttp 发送，响应通过 Jackson 解析。

> 缓存说明：token / jsapi_ticket 缓存委托给 cache 模块的 `CacheManager`。优先使用 `redis` store（多实例共享，需引入 `redis-cache` 模块自动注册）；未注册时回退到 `array` 内存 store。无需为缓存修改任何 yaml 配置。

## Classes & Interfaces

### AccessTokenManager
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.wechat`
- **Description**: access_token 管理器。调用微信 `cgi-bin/token` 接口获取 access_token，并通过 cache 模块的 `CacheStore` 缓存，提前 5 分钟（300 秒）过期避免临界点失效。支持公众号和小程序的 token 获取。

#### Constants

| Constant | Type | Value | Description |
|----------|------|-------|-------------|
| `API_BASE_URL` | String | `https://api.weixin.qq.com` | 微信 API 基础地址（public） |

#### Constructors

| Constructor | Parameters | Description |
|-------------|-----------|-------------|
| `AccessTokenManager` | `OkHttpClient httpClient, ObjectMapper objectMapper, CacheManager cacheManager` | 构造 access_token 管理器；通过 `CacheManager` 解析缓存仓库（优先 `redis` store，未注册回退 `array` store） |

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `getToken` | `String appId, String secret` | `String` | 获取 access_token；缓存命中（key `wechat:access_token:{appId}`）则直接返回，未命中则请求微信 API 并写入缓存（TTL = expires_in - 300） |
| `refreshToken` | `String appId, String secret` | `String` | 强制刷新 access_token（忽略缓存，重新请求微信 API 并回填缓存） |

#### Usage Example
```java
@Autowired
private AccessTokenManager tokenManager;

// 获取 access_token（自动缓存）
String token = tokenManager.getToken("wx1234567890abcdef", "your-secret");

// 强制刷新
String freshToken = tokenManager.refreshToken("wx1234567890abcdef", "your-secret");
```

### OfficialAccountService
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.wechat`
- **Description**: 微信公众号 API 服务。封装公众号常用接口，包括用户信息、备注、模板消息、菜单、标签、素材、客服消息、JSSDK 配置等。所有接口调用前自动获取有效的 access_token；jsapi_ticket 通过 `CacheStore` 缓存（key `wechat:jsapi_ticket:{appId}`）。

#### Constructors

| Constructor | Parameters | Description |
|-------------|-----------|-------------|
| `OfficialAccountService` | `AccessTokenManager accessTokenManager, WechatProperties properties, OkHttpClient httpClient, ObjectMapper objectMapper, CacheManager cacheManager` | 构造公众号服务；`cacheManager` 用于 jsapi_ticket 缓存（优先 redis，回退 array） |

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `getUserData` | `String openid` | `Map<String, Object>` | 获取用户基本信息（默认配置） |
| `getUserData` | `String openid, String configName` | `Map<String, Object>` | 获取用户基本信息（指定配置） |
| `updateUserRemark` | `String openid, String remark` | `Map<String, Object>` | 设置用户备注名（默认配置） |
| `updateUserRemark` | `String openid, String remark, String configName` | `Map<String, Object>` | 设置用户备注名（指定配置） |
| `sendTemplate` | `String templateId, String openid, Map<String, Object> data, String url, Map<String, Object> miniprogram` | `Map<String, Object>` | 发送模板消息（默认配置） |
| `sendTemplate` | `String templateId, String openid, Map<String, Object> data, String url, Map<String, Object> miniprogram, String configName` | `Map<String, Object>` | 发送模板消息（指定配置） |
| `getMenu` | - | `Map<String, Object>` | 获取自定义菜单（默认配置） |
| `getMenu` | `String configName` | `Map<String, Object>` | 获取自定义菜单（指定配置） |
| `setMenu` | `Object menuJson` | `Map<String, Object>` | 创建自定义菜单（默认配置） |
| `setMenu` | `Object menuJson, String configName` | `Map<String, Object>` | 创建自定义菜单（指定配置） |
| `createTag` | `String name` | `Map<String, Object>` | 创建标签（默认配置） |
| `createTag` | `String name, String configName` | `Map<String, Object>` | 创建标签（指定配置） |
| `getTags` | - | `Map<String, Object>` | 获取标签列表（默认配置） |
| `getTags` | `String configName` | `Map<String, Object>` | 获取标签列表（指定配置） |
| `deleteTag` | `int id` | `Map<String, Object>` | 删除标签（默认配置） |
| `deleteTag` | `int id, String configName` | `Map<String, Object>` | 删除标签（指定配置） |
| `batchTagging` | `int tagId, List<String> openids` | `Map<String, Object>` | 批量打标签（默认配置） |
| `batchTagging` | `int tagId, List<String> openids, String configName` | `Map<String, Object>` | 批量打标签（指定配置） |
| `batchUnTagging` | `int tagId, List<String> openids` | `Map<String, Object>` | 批量取消标签（默认配置） |
| `batchUnTagging` | `int tagId, List<String> openids, String configName` | `Map<String, Object>` | 批量取消标签（指定配置） |
| `uploadImageTemp` | `String path` | `Map<String, Object>` | 上传临时图片素材（默认配置） |
| `uploadImageTemp` | `String path, String configName` | `Map<String, Object>` | 上传临时图片素材（指定配置） |
| `uploadImageFull` | `String path` | `Map<String, Object>` | 上传永久图片素材（默认配置） |
| `uploadImageFull` | `String path, String configName` | `Map<String, Object>` | 上传永久图片素材（指定配置） |
| `downloadImageFull` | `String mediaId` | `Map<String, Object>` | 获取永久素材（默认配置） |
| `downloadImageFull` | `String mediaId, String configName` | `Map<String, Object>` | 获取永久素材（指定配置） |
| `deleteImageFull` | `String mediaId` | `Map<String, Object>` | 删除永久素材（默认配置） |
| `deleteImageFull` | `String mediaId, String configName` | `Map<String, Object>` | 删除永久素材（指定配置） |
| `getMaterialList` | `String type, int page, int count` | `Map<String, Object>` | 获取素材列表（默认配置） |
| `getMaterialList` | `String type, int page, int count, String configName` | `Map<String, Object>` | 获取素材列表（指定配置） |
| `sendMessage` | `Map<String, Object> data` | `Map<String, Object>` | 发送客服消息（默认配置） |
| `sendMessage` | `Map<String, Object> data, String configName` | `Map<String, Object>` | 发送客服消息（指定配置） |
| `sendTyping` | `String openid, int command` | `Map<String, Object>` | 发送输入状态（0=typing，1=cancel_typing，默认配置） |
| `sendTyping` | `String openid, int command, String configName` | `Map<String, Object>` | 发送输入状态（指定配置） |
| `buildJsSdkConfig` | `String url, List<String> jsApiList, List<String> openTagList, boolean debug` | `Map<String, Object>` | 构建 JSSDK 配置（默认配置） |
| `buildJsSdkConfig` | `String url, List<String> jsApiList, List<String> openTagList, boolean debug, String configName` | `Map<String, Object>` | 构建 JSSDK 配置（指定配置） |

#### Usage Example
```java
@Autowired
private OfficialAccountService mpService;

// 获取用户信息
Map<String, Object> userInfo = mpService.getUserData("o6_bmjrPTlm6_2sgVt7hMZOPfL2M");

// 发送模板消息
Map<String, Object> data = Map.of(
    "first", Map.of("value", "您有新订单", "color", "#173177"),
    "keyword1", Map.of("value", "ORD-2024-001"),
    "remark", Map.of("value", "请及时处理")
);
Map<String, Object> result = mpService.sendTemplate(
    "template-id-xxx", "o6_bmjrPTlm6_2sgVt7hMZOPfL2M",
    data, "https://example.com/order/1", null
);

// 构建 JSSDK 配置
Map<String, Object> config = mpService.buildJsSdkConfig(
    "https://example.com/page",
    List.of("chooseImage", "previewImage"),
    null, false
);
```

### MiniProgramService
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.wechat`
- **Description**: 微信小程序 API 服务。封装小程序登录凭证校验（jscode2session）、订阅消息发送等 API。

#### Constructors

| Constructor | Parameters | Description |
|-------------|-----------|-------------|
| `MiniProgramService` | `AccessTokenManager accessTokenManager, WechatProperties properties, OkHttpClient httpClient, ObjectMapper objectMapper` | 构造小程序服务（不直接持有缓存，token 委托 AccessTokenManager） |

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `jscode2session` | `String appId, String code` | `Map<String, Object>` | 登录凭证校验，返回 openid、session_key、unionid |
| `getAccessToken` | `String appId` | `String` | 获取小程序 access_token（委托 `AccessTokenManager`，自动缓存） |
| `sendTemplateMessage` | `String appId, String openid, String templateId, Map<String, Object> data, String page` | `Map<String, Object>` | 发送订阅消息 |

#### Usage Example
```java
@Autowired
private MiniProgramService miniService;

// 小程序登录
Map<String, Object> session = miniService.jscode2session("wx7051c4a2a779d651", "js_code_from_client");
String openid = (String) session.get("openid");
String sessionKey = (String) session.get("session_key");

// 发送订阅消息
Map<String, Object> data = Map.of(
    "thing1", Map.of("value", "新订单提醒"),
    "amount2", Map.of("value", "￥99.00")
);
Map<String, Object> result = miniService.sendTemplateMessage(
    "wx7051c4a2a779d651", openid, "template-id-xxx", data, "pages/order/detail"
);
```

### WechatProperties
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.wechat`
- **Annotations**: `@ConfigurationProperties(prefix = "jaravel.wechat")`
- **Description**: 微信 SDK 配置属性，前缀 `jaravel.wechat`，对齐 PHP `config/easywechat.php`。支持多公众号（`official-accounts`）与多小程序（`mini-apps`）命名配置。

#### Top-level Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `isEnabled` | - | `boolean` | 是否启用微信 SDK |
| `getOfficialAccounts` | - | `Map<String, OfficialAccountConfig>` | 公众号配置映射 |
| `getMiniApps` | - | `Map<String, MiniAppConfig>` | 小程序配置映射 |
| `getHttp` | - | `HttpConfig` | HTTP 客户端配置 |
| `getOfficialAccount` | `String configName` | `OfficialAccountConfig` | 按名称获取公众号配置，缺失回退 default |
| `getMiniApp` | `String configName` | `MiniAppConfig` | 按名称获取小程序配置，缺失回退 default |

#### Nested Classes

| Class | Description | Key Fields |
|-------|-------------|------------|
| `OfficialAccountConfig` | 公众号配置 | `appId`, `secret`, `token`, `aesKey`, `oauth` |
| `OauthConfig` | OAuth 授权配置 | `scopes`（默认 `snsapi_base`）, `callback`, `enforceHttps` |
| `MiniAppConfig` | 小程序配置 | `appId`, `secret`, `token`, `aesKey`, `type`（2=客服，3=管理端） |
| `HttpConfig` | HTTP 配置 | `timeout`（默认 5.0 秒）, `retry`（默认 true） |

#### Usage Example
```yaml
jaravel:
  wechat:
    enabled: true
    official-accounts:
      default:
        app-id: wx1234567890abcdef
        secret: your-official-secret
        oauth:
          scopes: snsapi_base
          callback: /oauth_callback
    mini-apps:
      default:
        app-id: wx7051c4a2a779d651
        secret: your-mini-secret
        type: 2
    http:
      timeout: 5.0
      retry: true
```

### WechatAutoConfiguration
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.wechat`
- **Annotations**: `@AutoConfiguration`, `@ConditionalOnClass(OkHttpClient.class)`, `@ConditionalOnProperty(name = "jaravel.wechat.enabled", havingValue = "true", matchIfMissing = true)`, `@EnableConfigurationProperties(WechatProperties.class)`
- **Description**: 微信 SDK 自动装配。创建 `OkHttpClient`、`AccessTokenManager`、`OfficialAccountService`、`MiniProgramService` Bean。缓存依赖由 cache 模块的 `CacheManager` 提供（通过 `ObjectProvider<CacheManager>` 注入）。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `wechatHttpClient` | `WechatProperties properties` | `OkHttpClient` | 创建微信 API 专用 OkHttpClient Bean（`@Bean`, `@ConditionalOnMissingBean(name = "wechatHttpClient")`） |
| `accessTokenManager` | `OkHttpClient wechatHttpClient, ObjectMapper objectMapper, ObjectProvider<CacheManager> cacheManagerProvider` | `AccessTokenManager` | 创建 access_token 管理器 Bean（`@Bean`, `@ConditionalOnMissingBean`） |
| `officialAccountService` | `AccessTokenManager accessTokenManager, WechatProperties properties, OkHttpClient wechatHttpClient, ObjectMapper objectMapper, ObjectProvider<CacheManager> cacheManagerProvider` | `OfficialAccountService` | 创建公众号服务 Bean（`@Bean`, `@ConditionalOnMissingBean`） |
| `miniProgramService` | `AccessTokenManager accessTokenManager, WechatProperties properties, OkHttpClient wechatHttpClient, ObjectMapper objectMapper` | `MiniProgramService` | 创建小程序服务 Bean（`@Bean`, `@ConditionalOnMissingBean`） |

#### Usage Example
```java
// 自动装配后，直接注入使用
@Autowired
private OfficialAccountService mpService;

@Autowired
private MiniProgramService miniService;
```
