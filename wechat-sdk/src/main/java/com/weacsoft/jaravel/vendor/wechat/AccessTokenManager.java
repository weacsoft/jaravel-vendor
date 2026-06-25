package com.weacsoft.jaravel.vendor.wechat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weacsoft.jaravel.vendor.redis.RedisManager;
import io.lettuce.core.api.sync.RedisCommands;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 微信 Access Token 管理器，对齐 PHP {@code WechatService::controllerGetAccessToken()} 及
 * EasyWeChat 的 AccessToken 中间件。
 * <p>
 * 微信 access_token 是公众号 / 小程序调用各 API 的全局唯一票据，有效期为 7200 秒（2 小时）。
 * 微信对 access_token 的获取有频率限制（每天 2000 次），因此必须做缓存，避免频繁刷新。
 *
 * <h3>缓存策略</h3>
 * <ul>
 *   <li><b>优先 Redis 缓存</b>：多实例部署时共享 token，避免各节点重复获取</li>
 *   <li><b>回退内存缓存</b>：Redis 不可用时使用本地 {@link ConcurrentHashMap} 缓存</li>
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
 * 本类为单例，使用 {@link ObjectProvider} 惰性获取 {@link RedisManager}（可能未配置）。
 * 内存回退缓存使用 {@link ConcurrentHashMap}，线程安全。OkHttpClient 与 ObjectMapper 均为线程安全。
 *
 * @author weacsoft
 */
public class AccessTokenManager {

    private static final Logger logger = LoggerFactory.getLogger(AccessTokenManager.class);

    /** 微信 API 基础地址 */
    public static final String API_BASE_URL = "https://api.weixin.qq.com";

    /** Redis 缓存键前缀 */
    private static final String CACHE_KEY_PREFIX = "wechat:access_token:";

    /** Token 提前过期缓冲时间（秒），防止临界点失效 */
    private static final long EXPIRY_BUFFER_SECONDS = 300;

    /** OkHttp 客户端（线程安全，复用连接池） */
    private final OkHttpClient httpClient;

    /** Jackson JSON 解析器（线程安全） */
    private final ObjectMapper objectMapper;

    /** Redis 管理器提供者（惰性获取，Redis 未配置时为空） */
    private final ObjectProvider<RedisManager> redisManagerProvider;

    /** 内存回退缓存：appId -> TokenEntry（Redis 不可用时使用） */
    private final ConcurrentMap<String, TokenEntry> memoryCache = new ConcurrentHashMap<>();

    /**
     * 构造 Access Token 管理器。
     *
     * @param httpClient           OkHttp 客户端
     * @param objectMapper         Jackson JSON 解析器
     * @param redisManagerProvider Redis 管理器提供者（允许为空，Redis 未配置时回退内存缓存）
     */
    public AccessTokenManager(OkHttpClient httpClient,
                              ObjectMapper objectMapper,
                              ObjectProvider<RedisManager> redisManagerProvider) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.redisManagerProvider = redisManagerProvider;
    }

    /**
     * 获取微信 access_token，对齐 PHP {@code WechatService::controllerGetAccessToken()}。
     * <p>
     * 流程：
     * <ol>
     *   <li>优先从 Redis 缓存读取（key: {@code wechat:access_token:{appId}}）</li>
     *   <li>Redis 不可用时回退到内存缓存</li>
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

        // 1. 优先从 Redis 读取
        String cached = getFromRedis(cacheKey);
        if (cached != null && !cached.isEmpty()) {
            logger.debug("[wechat] AccessToken 命中缓存: appId={}", appId);
            return cached;
        }

        // 2. Redis 不可用时回退内存缓存
        TokenEntry memoryEntry = memoryCache.get(appId);
        if (memoryEntry != null && !memoryEntry.isExpired()) {
            logger.debug("[wechat] AccessToken 命中内存缓存: appId={}", appId);
            return memoryEntry.token;
        }

        // 3. 缓存未命中，调用微信 API 获取新 token
        logger.info("[wechat] AccessToken 缓存未命中，请求微信 API: appId={}", appId);
        TokenEntry newEntry = requestTokenFromWechat(appId, secret);

        // 4. 写入缓存
        saveToCache(cacheKey, appId, newEntry);

        return newEntry.token;
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
        TokenEntry newEntry = requestTokenFromWechat(appId, secret);
        saveToCache(CACHE_KEY_PREFIX + appId, appId, newEntry);
        return newEntry.token;
    }

    /**
     * 调用微信 API 获取 access_token。
     * <p>
     * API: {@code GET https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid={appId}&secret={secret}}
     *
     * @param appId  微信 AppID
     * @param secret 微信 AppSecret
     * @return 包含 token 与过期时间的 TokenEntry
     * @throws RuntimeException API 返回错误码或网络异常时抛出
     */
    @SuppressWarnings("unchecked")
    private TokenEntry requestTokenFromWechat(String appId, String secret) {
        String url = API_BASE_URL + "/cgi-bin/token?grant_type=client_credential"
                + "&appid=" + appId + "&secret=" + secret;

        Request request = new Request.Builder().url(url).get().build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                logger.error("[wechat] 获取 AccessToken HTTP 失败: appId={}, code={}, body={}",
                        appId, response.code(), body);
                throw new RuntimeException("获取 AccessToken HTTP 失败: " + response.code());
            }

            Map<String, Object> result = objectMapper.readValue(body, Map.class);
            String accessToken = (String) result.get("access_token");

            if (accessToken == null || accessToken.isEmpty()) {
                Integer errcode = (Integer) result.get("errcode");
                String errmsg = (String) result.get("errmsg");
                logger.error("[wechat] 获取 AccessToken 业务失败: appId={}, errcode={}, errmsg={}",
                        appId, errcode, errmsg);
                throw new RuntimeException("获取 AccessToken 失败: errcode=" + errcode + ", errmsg=" + errmsg);
            }

            // expires_in 默认 7200 秒
            int expiresIn = result.get("expires_in") != null
                    ? ((Number) result.get("expires_in")).intValue() : 7200;
            long ttlSeconds = Math.max(expiresIn - EXPIRY_BUFFER_SECONDS, 60);

            logger.info("[wechat] 获取 AccessToken 成功: appId={}, expires_in={}s, cache_ttl={}s",
                    appId, expiresIn, ttlSeconds);

            return new TokenEntry(accessToken, System.currentTimeMillis() + ttlSeconds * 1000, ttlSeconds);

        } catch (IOException e) {
            logger.error("[wechat] 获取 AccessToken 网络异常: appId={}", appId, e);
            throw new RuntimeException("获取 AccessToken 网络异常: " + e.getMessage(), e);
        }
    }

    /**
     * 从 Redis 读取缓存的 token。
     *
     * @param cacheKey 缓存键
     * @return 缓存的 token，不存在或 Redis 不可用返回 null
     */
    private String getFromRedis(String cacheKey) {
        if (redisManagerProvider == null) {
            return null;
        }
        try {
            RedisManager redisManager = redisManagerProvider.getIfAvailable();
            if (redisManager == null) {
                return null;
            }
            RedisCommands<String, String> commands = redisManager.sync();
            return commands.get(cacheKey);
        } catch (Exception e) {
            logger.warn("[wechat] Redis 读取 AccessToken 缓存失败，回退内存缓存: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将 token 写入缓存（Redis + 内存双写）。
     *
     * @param cacheKey Redis 缓存键
     * @param appId    微信 AppID（内存缓存键）
     * @param entry    token 条目
     */
    private void saveToCache(String cacheKey, String appId, TokenEntry entry) {
        // 写入内存缓存（始终写入，作为 Redis 不可用时的回退）
        memoryCache.put(appId, entry);

        // 写入 Redis
        if (redisManagerProvider != null) {
            try {
                RedisManager redisManager = redisManagerProvider.getIfAvailable();
                if (redisManager != null) {
                    RedisCommands<String, String> commands = redisManager.sync();
                    commands.setex(cacheKey, entry.ttlSeconds, entry.token);
                    logger.debug("[wechat] AccessToken 已写入 Redis 缓存: key={}, ttl={}s", cacheKey, entry.ttlSeconds);
                }
            } catch (Exception e) {
                logger.warn("[wechat] Redis 写入 AccessToken 缓存失败，仅使用内存缓存: {}", e.getMessage());
            }
        }
    }

    /**
     * Token 缓存条目，记录 token 值与过期时间。
     */
    private static class TokenEntry {

        /** access_token 值 */
        final String token;

        /** 过期时间戳（毫秒） */
        final long expiryAt;

        /** 缓存 TTL（秒） */
        final long ttlSeconds;

        TokenEntry(String token, long expiryAt, long ttlSeconds) {
            this.token = token;
            this.expiryAt = expiryAt;
            this.ttlSeconds = ttlSeconds;
        }

        /** 判断是否已过期 */
        boolean isExpired() {
            return System.currentTimeMillis() >= expiryAt;
        }
    }
}
