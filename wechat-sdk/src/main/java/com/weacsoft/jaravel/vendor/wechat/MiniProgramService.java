package com.weacsoft.jaravel.vendor.wechat;

import com.weacsoft.jaravel.vendor.cache.CacheManager;
import com.weacsoft.jaravel.vendor.wechat.mini.MiniProgramSession;
import com.weacsoft.jaravel.vendor.wechat.mini.MiniSubscribeMessage;
import com.weacsoft.jaravel.vendor.wechat.mini.PhoneNumberResult;
import com.weacsoft.jaravel.vendor.wechat.response.WeChatResponse;
import com.weacsoft.jaravel.vendor.wechat.response.WechatApiException;
import com.weacsoft.jaravel.vendor.wechat.transport.JacksonJsonEncoder;
import com.weacsoft.jaravel.vendor.wechat.transport.RequestJsonEncoder;
import com.weacsoft.jaravel.vendor.wechat.transport.WechatTransport;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 微信<b>小程序</b>服务，对齐 EasyWeChat 5.x {@code MiniApp} 对象模型与官方小程序文档。
 * <p>
 * 覆盖能力（官方 API 全集）：
 * <ul>
 *   <li><b>登录会话</b>：code2Session（jscode2session，严格类型 {@link MiniProgramSession}；
 *       session_key 官方要求禁下发客户端）</li>
 *   <li><b>订阅消息</b>：sendSubscribeMessage（{@link MiniSubscribeMessage}，
 *       对应 {@code message/subscribe/send}）</li>
 *   <li><b>素材</b>：uploadMedia（临时/永久）/ deleteMedia</li>
 *   <li><b>小程序码</b>：getMiniProgramCode（wxa/getwxacode/unlimited，PNG 字节流）</li>
 *   <li><b>链接</b>：generateUrlLink（generate_urllink）/ generateScheme（generate_scheme）</li>
 *   <li><b>手机号</b>：getPhoneNumber（phonenumber/get，code 一次性）</li>
 *   <li><b>OCR</b>：ocrCheck（media/ocr，文字/二维码）</li>
 * </ul>
 *
 * <h3>多配置</h3>
 * 支持多小程序命名配置（声明 {@code @RegisterWechatMiniApp} &gt; yml &gt; 兜底默认）；
 * 配置名也支持直接用 appId 定位（{@link WechatProperties#getMiniApp}）。
 *
 * <h3>线程安全</h3>
 * 无状态单例；access_token 通过 cache 模块的 {@link com.weacsoft.jaravel.vendor.cache.CacheStore} 缓存。
 *
 * @author weacsoft
 */
public class MiniProgramService {

    private static final Logger logger = LoggerFactory.getLogger(MiniProgramService.class);

    private final AccessTokenManager accessTokenManager;
    private final WechatProperties properties;
    private final WechatTransport transport;
    private final CacheManager cacheManager;

    /**
     * 便捷构造（默认 JSON 编码器，无外部 cache manager）。
     */
    public MiniProgramService(AccessTokenManager accessTokenManager,
                              WechatProperties properties,
                              OkHttpClient httpClient) {
        this(accessTokenManager, properties, httpClient, new JacksonJsonEncoder(), null);
    }

    /**
     * 构造。
     *
     * @param accessTokenManager Access Token 管理器
     * @param properties         微信配置属性
     * @param httpClient         OkHttp 客户端
     * @param encoder            请求体 JSON 编码器
     * @param cacheManager       缓存管理器（用于票据缓存，可为 null）
     */
    public MiniProgramService(AccessTokenManager accessTokenManager,
                              WechatProperties properties,
                              OkHttpClient httpClient,
                              RequestJsonEncoder encoder,
                              CacheManager cacheManager) {
        this.accessTokenManager = accessTokenManager;
        this.properties = properties;
        this.transport = new WechatTransport(httpClient, encoder);
        this.cacheManager = cacheManager;
    }

    // ==================== 登录会话 ====================

    /**
     * 小程序登录 code2session：{@code GET /sns/jscode2session}。
     * <p>
     * 注意：
     * <ul>
     *   <li>code 为一次性凭证，多次使用将报错</li>
     *   <li>session_key 必须<b>仅保存于服务端</b>，严禁下发客户端（官方规范）</li>
     * </ul>
     *
     * @param configName 小程序配置名或 appId（如 "default"）
     * @param code       小程序登录 code
     * @return 会话对象（openid + session_key + unionid?）
     * @throws WechatApiException 微信返回 errcode 或响应缺少字段时
     */
    public MiniProgramSession code2Session(String configName, String code) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("code2Session 需要非空 code");
        }
        WechatProperties.MiniAppConfig cfg = resolveMiniConfig(configName);
        Map<String, String> query = new LinkedHashMap<>();
        query.put("appid", cfg.getAppId());
        query.put("secret", cfg.getSecret());
        query.put("js_code", code);
        query.put("grant_type", "authorization_code");

        WeChatResponse resp = transport.get("sns/jscode2session", query, "code2Session");
        resp.requireSuccess("code2Session");
        return MiniProgramSession.from(resp.raw());
    }

    // ==================== 订阅消息 ====================

    /**
     * 发送小程序订阅消息。
     * <p>
     * API: {@code POST /cgi-bin/message/subscribe/send}
     * <p>
     * 用户须先授权「一次性订阅」（官方要求）；未授权/拒收会推送
     * {@code subscribe_msg_sent_event} / {@code subscribe_msg_change_event}。
     *
     * @param message    订阅消息对象
     * @param configName 小程序配置名或 appId
     * @return API 响应（成功时含 msgid；失败时 errcode/errmsg）
     */
    public WeChatResponse sendSubscribeMessage(MiniSubscribeMessage message, String configName) {
        WechatProperties.MiniAppConfig cfg = resolveMiniConfig(configName);
        Map<String, String> query = tokenQuery(cfg);
        return transport.postJson("cgi-bin/message/subscribe/send", query, message.toJsonBody(), "sendSubscribeMessage");
    }

    // ==================== 素材 ====================

    /**
     * 上传素材（临时 3 天 / 永久）。
     * <p>
     * 临时：{@code POST /cgi-bin/media/upload?type=…}；永久：{@code POST /cgi-bin/material/add_material?type=…}
     *
     * @param configName 小程序配置名或 appId
     * @param path       本地文件路径
     * @param type       素材类型：image / voice / video / thumb
     * @param permanent  true=永久素材（add_material），false=临时（media/upload）
     * @return API 响应（media_id/url/created_at）
     */
    public WeChatResponse uploadMedia(String configName, String path, String type, boolean permanent) {
        WechatProperties.MiniAppConfig cfg = resolveMiniConfig(configName);
        Map<String, String> query = new LinkedHashMap<>(tokenQuery(cfg));
        query.put("type", type);
        File file = new File(path);
        if (!file.exists()) {
            throw new IllegalArgumentException("文件不存在: " + path);
        }
        String apiPath = permanent ? "cgi-bin/material/add_material" : "cgi-bin/media/upload";
        return transport.upload(apiPath, query, file, "media", "uploadMedia");
    }

    /**
     * 删除素材（永久素材接口）。
     * <p>
     * API: {@code POST /cgi-bin/material/del_material}
     *
     * @param configName 小程序配置名或 appId
     * @param mediaId    素材 id
     * @return API 响应
     */
    public WeChatResponse deleteMedia(String configName, String mediaId) {
        WechatProperties.MiniAppConfig cfg = resolveMiniConfig(configName);
        return transport.postJson("cgi-bin/material/del_material", tokenQuery(cfg),
                Map.of("media_id", mediaId), "deleteMedia");
    }

    // ==================== 小程序码 / URL Link / Scheme ====================

    /**
     * 获取小程序码（wxa/getwxacode/unlimited，场景值二维码，二进制 PNG）。
     * <p>
     * API: {@code GET /wxa/getwxacode/unlimited}
     *
     * @param configName 小程序配置名或 appId
     * @param scene      场景值（≤32 可见字符，字母/数字/!#$&'()*+,/:;=?@-._~）
     * @return 小程序码 PNG 字节流
     */
    public byte[] getMiniProgramCode(String configName, String scene) {
        return getMiniProgramCode(configName, scene, null, 430, null);
    }

    /**
     * 获取小程序码（可指定落地页、宽度、环境版本）。
     *
     * @param configName  小程序配置名或 appId
     * @param scene       场景值
     * @param page        落地页（不带前导 /，可空）
     * @param width       宽度 px（≤1280，默认 430）
     * @param envVersion  版本：release / trial / develop（可空）
     * @return 小程序码 PNG 字节流
     */
    public byte[] getMiniProgramCode(String configName, String scene, String page, int width, String envVersion) {
        WechatProperties.MiniAppConfig cfg = resolveMiniConfig(configName);
        Map<String, String> query = new LinkedHashMap<>(tokenQuery(cfg));
        query.put("scene", scene);
        query.put("width", String.valueOf(Math.min(width, 1280)));
        putIfPresent(query, "page", page);
        putIfPresent(query, "env_version", envVersion);
        return transport.getBinary("wxa/getwxacode/unlimited", query, "getMiniProgramCode");
    }

    /**
     * 生成 url_link（可转义的落地链接，支持打开指定页面）。
     * <p>
     * API: {@code POST /cgi-bin/generate_urllink}
     *
     * @param configName 小程序配置名或 appId
     * @param path       页面路径（可空则首页）
     * @param query      页面参数串（可空）
     * @return url_link 字符串
     */
    public String generateUrlLink(String configName, String path, String query) {
        WechatProperties.MiniAppConfig cfg = resolveMiniConfig(configName);
        Map<String, Object> body = new LinkedHashMap<>();
        putOpt(body, "path", path);
        putOpt(body, "query", query);
        WeChatResponse resp = transport.postJson("cgi-bin/generate_urllink", tokenQuery(cfg), body, "generateUrLLink");
        resp.requireSuccess("generateUrLLink");
        Object url = resp.raw().get("url");
        if (url == null) {
            throw new WechatApiException("generateUrLLink 响应缺少 url 字段");
        }
        return String.valueOf(url);
    }

    /**
     * 生成 scheme（微信内跳转 scheme 链接）。
     * <p>
     * API: {@code POST /cgi-bin/generate_scheme}
     *
     * @param configName 小程序配置名或 appId
     * @param path       页面路径（可空）
     * @param query      页面参数串（可空）
     * @param expireType 1=长期有效（默认）/ 2=按时长
     * @param expireTime expireType=2 时的过期秒数
     * @return scheme 字符串
     */
    public String generateScheme(String configName, String path, String query, int expireType, long expireTime) {
        WechatProperties.MiniAppConfig cfg = resolveMiniConfig(configName);
        Map<String, Object> body = new LinkedHashMap<>();
        putOpt(body, "path", path);
        putOpt(body, "query", query);
        if (expireType == 1) {
            body.put("expire_type", 1);
        } else {
            body.put("expire_type", 2);
            body.put("expire_interval", expireTime);
        }
        WeChatResponse resp = transport.postJson("cgi-bin/generate_scheme", tokenQuery(cfg), body, "generateScheme");
        resp.requireSuccess("generateScheme");
        Object scheme = resp.raw().get("openlink");
        if (scheme == null) {
            scheme = resp.raw().get("scheme");
        }
        if (scheme == null) {
            throw new WechatApiException("generateScheme 响应缺少 openlink/scheme 字段");
        }
        return String.valueOf(scheme);
    }

    // ==================== 手机号 / OCR ====================

    /**
     * 获取手机号（官方 phonenumber/get，code 一次性）。
     * <p>
     * API: {@code POST /phonenumber/get}（body: {code}）
     *
     * @param configName 小程序配置名或 appId
     * @param code       手机号授权 code
     * @return 手机号结果（含纯号/国际区号）
     */
    public PhoneNumberResult getPhoneNumber(String configName, String code) {
        WechatProperties.MiniAppConfig cfg = resolveMiniConfig(configName);
        WeChatResponse resp = transport.postJson("phonenumber/get", tokenQuery(cfg),
                Map.of("code", code), "getPhoneNumber");
        resp.requireSuccess("getPhoneNumber");
        return PhoneNumberResult.from(resp.raw());
    }

    /**
     * 微信 OCR（小程序侧）。
     * <p>
     * API: {@code POST /cgi-bin/media/ocr}（type: 1=识别文字，2=识别 qrcode）
     *
     * @param configName 小程序配置名或 appId
     * @param mediaId    素材 id
     * @param ocrType    1=文字 2=二维码
     * @return API 响应
     */
    public WeChatResponse ocrCheck(String configName, String mediaId, int ocrType) {
        WechatProperties.MiniAppConfig cfg = resolveMiniConfig(configName);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("media_id", mediaId);
        body.put("type", ocrType);
        return transport.postJson("cgi-bin/media/ocr", tokenQuery(cfg), body, "ocrCheck");
    }

    // ==================== 内部 ====================

    private WechatProperties.MiniAppConfig resolveMiniConfig(String nameOrAppId) {
        WechatProperties.MiniAppConfig cfg = properties.getMiniApp(nameOrAppId);
        if (cfg == null) {
            throw new IllegalStateException("未找到小程序配置: " + nameOrAppId
                    + "（可通过 @RegisterWechatMiniApp 声明或 yml 配置）");
        }
        if (cfg.getAppId() == null || cfg.getSecret() == null) {
            throw new IllegalStateException("小程序配置 \"" + nameOrAppId + "\" 缺少 appId 或 secret");
        }
        return cfg;
    }

    private Map<String, String> tokenQuery(WechatProperties.MiniAppConfig cfg) {
        String token = accessTokenManager.getToken(cfg.getAppId(), cfg.getSecret());
        return Map.of("access_token", token);
    }

    private static void putIfPresent(Map<String, String> map, String key, String value) {
        if (value != null && !value.isEmpty()) {
            map.put(key, value);
        }
    }

    private static void putOpt(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isEmpty()) {
            map.put(key, value);
        }
    }
}
