package com.weacsoft.jaravel.vendor.wechat;

import com.weacsoft.jaravel.vendor.cache.CacheManager;
import com.weacsoft.jaravel.vendor.cache.CacheStore;
import com.weacsoft.jaravel.vendor.json.Json;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * 微信 Access Token 管理器，对齐 PHP {@code WechatService::controllerGetAccessToken()} 及
 * EasyWeChat 的 AccessToken 中间件。
 * <p>
 * 微信 access_token 是公众号 / 小程序调用各 API 的全局唯一票据，有效期为 7200 秒（2 小时）。
 * 微信对 access_token 的获取有频率限制（每天 2000 次），因此必须做缓存，避免频繁刷新。
 *
 * <h3>缓存策略</h3>
 * <ul>
 *   <li><b>基于 cache 模块</b>：通过 {@link CacheManager} 获取 {@link CacheStore}，默认使用
 *       cache 模块的默认 store（由 {@code jaravel.cache.default-store} 决定，不关心具体实现）。
 *       可通过 {@code jaravel.wechat.cache-store} 显式指定 store 名</li>
 *   <li><b>TTL 缓冲</b>：缓存 TTL = expires_in - 300（提前 5 分钟过期），防止临界点 token 失效</li>
 * </ul>
 *
 * <h3>PHP 对齐</h3>
 * <pre>
 * // PHP WechatService 中获取 access_token 的逻辑
 * $app = $this-&gt;getApplication();
 * $accessToken = $app-&gt;access_token;
 * $token = $accessToken-&gt;getToken();  // EasyWeChat 内部自动缓存（默认缓存到 Redis）
 * </pre>
 *
 * <h3>线程安全</h3>
 * 本类为单例，{@link CacheStore} 实现自身保证线程安全。
 * OkHttpClient 与 {@link Json} 均为线程安全。
 *
 * @author weacsoft
 */
public class AccessTokenManager {

    private static final Logger logger = LoggerFactory.getLogger(AccessTokenManager.class);

    /** 微信 API 基础地址 */
    public static final String API_BASE_URL = "https://api.weixin.qq.com";

    /** 缓存键前缀，完整 key 格式：wechat:access_token:{appId} */
    private static final String CACHE_KEY_PREFIX = "wechat:access_token:";

    /** Token 提前过期缓冲时间（秒），防止临界点失效 */
    private static final long EXPIRY_BUFFER_SECONDS = 300;

    /** 获取模式：传统接口 {@code GET cgi-bin/token} */
    public static final String TOKEN_MODE_LEGACY = "legacy";
    /** 获取模式：官方稳定接口 {@code POST cgi-bin/stable_token} */
    public static final String TOKEN_MODE_STABLE = "stable";

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    /** OkHttp 客户端（线程安全，复用连接池） */
    private final OkHttpClient httpClient;

    /** 缓存仓库（优先配置的 store，未注册时回退 array） */
    private final CacheStore cacheStore;

    /** 获取模式：legacy（默认）| stable，决定请求 {@code cgi-bin/token} 还是 {@code cgi-bin/stable_token} */
    private final String tokenMode;

    /**
     * 构造 Access Token 管理器。
     * <p>
     * 通过 {@link CacheManager} 解析缓存仓库：使用 cache 模块的默认 store（由
     * {@code jaravel.cache.default-store} 决定），不关心具体实现。
     *
     * @param httpClient   OkHttp 客户端
     * @param cacheManager 缓存管理器（由 cache 模块提供）
     */
    public AccessTokenManager(OkHttpClient httpClient,
                              CacheManager cacheManager) {
        this(httpClient, cacheManager, "");
    }

    /**
     * 构造 Access Token 管理器，指定首选缓存 store。
     *
     * @param httpClient       OkHttp 客户端
     * @param cacheManager     缓存管理器（可为 null）
     * @param preferredStore   首选缓存 store 名称（如 "redis"、"array"）
     */
    public AccessTokenManager(OkHttpClient httpClient,
                              CacheManager cacheManager,
                              String preferredStore) {
        this(httpClient, cacheManager, preferredStore, TOKEN_MODE_LEGACY);
    }

    /**
     * 构造 Access Token 管理器，指定缓存 store 与获取模式。
     * <p>
     * 获取模式（对应 {@code jaravel.wechat.token-mode}）：
     * <ul>
     *   <li>{@code legacy}（默认）：GET {@code cgi-bin/token}，传统接口</li>
     *   <li>{@code stable}：POST {@code cgi-bin/stable_token}，官方稳定接口（配额更优，推荐新接入）</li>
     * </ul>
     * 两种模式拿到的 access_token 等价（同一 appId 共用票据池）。
     *
     * @param httpClient       OkHttp 客户端
     * @param cacheManager     缓存管理器（可为 null）
     * @param preferredStore   首选缓存 store 名称
     * @param tokenMode        获取模式（legacy/stable，忽略大小写；null/空按 legacy）
     */
    public AccessTokenManager(OkHttpClient httpClient,
                              CacheManager cacheManager,
                              String preferredStore,
                              String tokenMode) {
        this.httpClient = httpClient;
        this.cacheStore = WechatCacheResolver.resolve(cacheManager, preferredStore);
        this.tokenMode = (tokenMode == null || tokenMode.isEmpty())
                ? TOKEN_MODE_LEGACY : tokenMode.toLowerCase();
    }

    /**
     * 获取微信 access_token，对齐 PHP {@code WechatService::controllerGetAccessToken()}。
     * <p>
     * 流程：
     * <ol>
     *   <li>从缓存仓库读取（key: {@code wechat:access_token:{appId}}）</li>
     *   <li>缓存未命中则调用微信 API 获取新 token</li>
     *   <li>将新 token 写入缓存，TTL = expires_in - 300（提前 5 分钟过期）</li>
     * </ol>
     *
     * @param appId  微信 AppID
     * @param secret 微信 AppSecret
     * @return access_token 字符串
     * @throws RuntimeException 微信 API 返回错误或网络异常时抛出
     */
    public String getToken(String appId, String secret) {
        String cacheKey = CACHE_KEY_PREFIX + appId;

        // 1. 优先从缓存读取
        String cached = cacheStore.get(cacheKey, String.class);
        if (cached != null && !cached.isEmpty()) {
            logger.debug("[wechat] AccessToken 命中缓存: appId={}", appId);
            return cached;
        }

        // 2. 缓存未命中，调用微信 API 获取新 token 并写入缓存
        logger.info("[wechat] AccessToken 缓存未命中，请求微信 API: appId={}", appId);
        return fetchAndCacheToken(appId, secret);
    }

    /**
     * 强制刷新 access_token（忽略缓存），对齐 PHP 中 AccessToken 的 rewrite 机制。
     *
     * @param appId  微信 AppID
     * @param secret 微信 AppSecret
     * @return 新的 access_token
     */
    public String refreshToken(String appId, String secret) {
        logger.info("[wechat] 强制刷新 AccessToken: appId={}", appId);
        return fetchAndCacheToken(appId, secret);
    }

    /**
     * 清除指定应用的 access_token 缓存，下次调用 {@link #getToken} 会重新从微信 API 获取。
     * <p>
     * 适用于 token 被微信端失效（如修改密码、解绑等）后主动清除本地缓存的场景。
     *
     * @param appId 微信 AppID
     */
    public void invalidateToken(String appId) {
        cacheStore.forget(CACHE_KEY_PREFIX + appId);
        logger.info("[wechat] 清除 AccessToken 缓存: appId={}", appId);
    }

    /**
     * 清除所有应用的 access_token 缓存。
     * <p>
     * <b>注意</b>：此方法调用 {@code flush()} 会清空当前 store 下所有缓存（包括其他模块），
     * 仅在独立 store 或需要全局重置时使用。
     */
    public void invalidateAllTokens() {
        cacheStore.flush();
        logger.info("[wechat] 清除所有 AccessToken 缓存");
    }

    /**
     * 调用微信 API 获取 access_token 并写入缓存。
     *
     * @param appId  微信 AppID
     * @param secret 微信 AppSecret
     * @return access_token 字符串
     */
    @SuppressWarnings("unchecked")
    private String fetchAndCacheToken(String appId, String secret) {
        Map<String, Object> result = requestTokenFromWechat(appId, secret);
        String token = (String) result.get("access_token");

        // expires_in 默认 7200 秒
        int expiresIn = result.get("expires_in") != null
                ? ((Number) result.get("expires_in")).intValue() : 7200;
        long ttlSeconds = Math.max(expiresIn - EXPIRY_BUFFER_SECONDS, 60);

        cacheStore.put(CACHE_KEY_PREFIX + appId, token, ttlSeconds);
        logger.info("[wechat] 获取 AccessToken 成功: appId={}, expires_in={}s, cache_ttl={}s",
                appId, expiresIn, ttlSeconds);
        return token;
    }

    /**
     * 调用微信 API 获取 access_token。
     * <p>
     * 两种模式：
     * <ul>
     *   <li>legacy: {@code GET /cgi-bin/token?grant_type=client_credential&appid={appId}&secret={secret}}</li>
     *   <li>stable: {@code POST /cgi-bin/stable_token}，请求体
     *       {@code {"grant_type":"client_credential","appid":"…","secret":"…"}}（官方稳定接口）</li>
     * </ul>
     *
     * @param appId  微信 AppID
     * @param secret 微信 AppSecret
     * @return 微信 API 响应（含 access_token 与 expires_in）
     * @throws RuntimeException API 返回错误码或网络异常时抛出
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> requestTokenFromWechat(String appId, String secret) {
        Request request;
        if (TOKEN_MODE_STABLE.equals(tokenMode)) {
            Map<String, Object> tokenBody = new java.util.LinkedHashMap<>();
            tokenBody.put("grant_type", "client_credential");
            tokenBody.put("appid", appId);
            tokenBody.put("secret", secret);
            request = new Request.Builder()
                    .url(API_BASE_URL + "/cgi-bin/stable_token")
                    .post(RequestBody.create(Json.stringify(tokenBody), JSON_MEDIA_TYPE))
                    .build();
        } else {
            String url = API_BASE_URL + "/cgi-bin/token?grant_type=client_credential"
                    + "&appid=" + appId + "&secret=" + secret;
            request = new Request.Builder().url(url).get().build();
        }

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                logger.error("[wechat] 获取 AccessToken HTTP 失败: appId={}, mode={}, code={}, body={}",
                        appId, tokenMode, response.code(), body);
                throw new RuntimeException("获取 AccessToken HTTP 失败: " + response.code());
            }

            Map<String, Object> result = Json.parseToMap(body);
            String accessToken = (String) result.get("access_token");

            if (accessToken == null || accessToken.isEmpty()) {
                Integer errcode = (Integer) result.get("errcode");
                String errmsg = (String) result.get("errmsg");
                logger.error("[wechat] 获取 AccessToken 业务失败: appId={}, errcode={}, errmsg={}",
                        appId, errcode, errmsg);
                throw new RuntimeException("获取 AccessToken 失败: errcode=" + errcode + ", errmsg=" + errmsg);
            }

            return result;

        } catch (IOException e) {
            logger.error("[wechat] 获取 AccessToken 网络异常: appId={}", appId, e);
            throw new RuntimeException("获取 AccessToken 网络异常: " + e.getMessage(), e);
        }
    }

}
