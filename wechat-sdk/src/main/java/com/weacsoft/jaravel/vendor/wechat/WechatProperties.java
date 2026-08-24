package com.weacsoft.jaravel.vendor.wechat;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信 SDK 配置属性，前缀 {@code jaravel.wechat}，对齐 PHP 项目的 {@code config/easywechat.php}。
 * <p>
 * 该配置类对应 PHP {@code overtrue/laravel-wechat}（EasyWeChat）的配置结构，支持多公众号、
 * 多小程序的命名配置。每个命名配置独立维护 appId / secret / token / aesKey 等凭证信息。
 *
 * <h3>PHP 配置对齐</h3>
 * <pre>
 * // PHP config/easywechat.php
 * 'official_account' =&gt; [
 *     'default' =&gt; [
 *         'app_id'  =&gt; env('WECHAT_OFFICIAL_ACCOUNT_APPID', ''),
 *         'secret'  =&gt; env('WECHAT_OFFICIAL_ACCOUNT_SECRET', ''),
 *         'token'   =&gt; env('WECHAT_OFFICIAL_ACCOUNT_TOKEN', ''),
 *         'aes_key' =&gt; env('WECHAT_OFFICIAL_ACCOUNT_AES_KEY', ''),
 *         'oauth'   =&gt; ['scopes' =&gt; ['snsapi_base'], 'callback' =&gt; '/oauth_callback', 'enforce_https' =&gt; true],
 *     ],
 *     'snsapi_userinfo' =&gt; [ ... ],  // snsapi_userinfo 授权范围配置
 * ],
 * 'mini_app' =&gt; [
 *     'default'           =&gt; [ ... ],
 *     'wx7051c4a2a779d651' =&gt; [ ... ],  // 客服小程序（type=2）
 *     'wxb33c8c0f6bea3602' =&gt; [ ... ],  // 管理端小程序（type=3）
 * ],
 * </pre>
 *
 * <h3>Java YAML 对应配置</h3>
 * <pre>
 * jaravel:
 *   wechat:
 *     enabled: true
 *     official-accounts:
 *       default:
 *         app-id: wx1234567890abcdef
 *         secret: your-secret
 *         token: your-token
 *         aes-key: your-aes-key
 *         oauth:
 *           scopes: snsapi_base
 *           callback: /oauth_callback
 *           enforce-https: true
 *       snsapi_userinfo:
 *         app-id: wx1234567890abcdef
 *         secret: your-secret
 *         oauth:
 *           scopes: snsapi_userinfo
 *     mini-apps:
 *       default:
 *         app-id: wx7051c4a2a779d651
 *         secret: your-mini-secret
 *         type: 2
 *     http:
 *       timeout: 5.0
 *       retry: true
 * </pre>
 *
 * @author weacsoft
 */
@ConfigurationProperties(prefix = "jaravel.wechat")
public class WechatProperties {

    /** 是否启用微信 SDK，默认 true */
    private boolean enabled = true;

    /**
     * access_token 获取模式：
     * <ul>
     *   <li>{@code legacy}（默认）：GET {@code cgi-bin/token}（传统接口）</li>
     *   <li>{@code stable}：POST {@code cgi-bin/stable_token}（官方新版稳定接口，
     *       配额与失效策略更优，推荐新接入使用）</li>
     * </ul>
     * 两种模式取到的 token 等价、可混用，仅获取路径不同。
     */
    private String tokenMode = "legacy";

    /**
     * 缓存 store 名称，用于 access_token / jsapi_ticket 缓存。
     * <p>
     * 为空时使用 cache 模块的默认 store（由 {@code jaravel.cache.default-store} 决定），
     * 不关心具体是 array / file / redis / database 哪种实现。
     * 可显式指定 store 名（如 "redis"）以覆盖默认行为。
     */
    private String cacheStore = "";

    /** 公众号配置映射，key 为配置名（如 default、snsapi_userinfo），对齐 PHP official_account 段 */
    private Map<String, OfficialAccountConfig> officialAccounts = new LinkedHashMap<>();

    /** 小程序配置映射，key 为配置名或 appId（如 default、wx7051c4a2a779d651），对齐 PHP mini_app 段 */
    private Map<String, MiniAppConfig> miniApps = new LinkedHashMap<>();

    /** HTTP 客户端配置 */
    private HttpConfig http = new HttpConfig();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return access_token 获取模式（legacy/stable）
     */
    public String getTokenMode() {
        return tokenMode;
    }

    public void setTokenMode(String tokenMode) {
        this.tokenMode = tokenMode;
    }

    public String getCacheStore() {
        return cacheStore;
    }

    public void setCacheStore(String cacheStore) {
        this.cacheStore = cacheStore;
    }

    public Map<String, OfficialAccountConfig> getOfficialAccounts() {
        return officialAccounts;
    }

    public void setOfficialAccounts(Map<String, OfficialAccountConfig> officialAccounts) {
        this.officialAccounts = officialAccounts;
    }

    public Map<String, MiniAppConfig> getMiniApps() {
        return miniApps;
    }

    public void setMiniApps(Map<String, MiniAppConfig> miniApps) {
        this.miniApps = miniApps;
    }

    public HttpConfig getHttp() {
        return http;
    }

    public void setHttp(HttpConfig http) {
        this.http = http;
    }

    /**
     * 按名称获取公众号配置，不存在则回退到 default，仍不存在返回 null。
     *
     * @param configName 配置名，null 或空串使用 default
     * @return 公众号配置
     */
    public OfficialAccountConfig getOfficialAccount(String configName) {
        String name = (configName == null || configName.isEmpty()) ? "default" : configName;
        OfficialAccountConfig config = officialAccounts.get(name);
        if (config == null && !"default".equals(name)) {
            config = officialAccounts.get("default");
        }
        return config;
    }

    /**
     * 按名称获取小程序配置，不存在则回退到 default，仍不存在返回 null。
     *
     * @param configName 配置名或 appId，null 或空串使用 default
     * @return 小程序配置
     */
    public MiniAppConfig getMiniApp(String configName) {
        String name = (configName == null || configName.isEmpty()) ? "default" : configName;
        MiniAppConfig config = miniApps.get(name);
        if (config != null) {
            return config;
        }
        if (!"default".equals(name)) {
            // 支持用 appId 直接定位小程序命名配置
            for (MiniAppConfig candidate : miniApps.values()) {
                if (name.equals(candidate.getAppId())) {
                    return candidate;
                }
            }
            config = miniApps.get("default");
        }
        return config;
    }

    /**
     * 公众号配置，对齐 PHP {@code official_account.default} 段。
     * <p>
     * 包含公众号的 appId、secret、token、aesKey 以及 OAuth 授权配置。
     */
    public static class OfficialAccountConfig {

        /** 公众号 AppID */
        private String appId;

        /** 公众号 AppSecret */
        private String secret;

        /** 公众号消息校验 Token */
        private String token;

        /** 公众号消息加解密密钥（EncodingAESKey） */
        private String aesKey;

        /**
         * 接收消息模式：
         * <ul>
         *   <li>{@code plain}（默认）：明文模式，推送/回复为明文 XML</li>
         *   <li>{@code safe}：加密模式，推送/回复整体 AES 加密于 {@code Encrypt}，
         *       需同时配置 {@code token} 与 {@code aes-key}</li>
         * </ul>
         * 对应微信「消息与推送 - 消息加解密」的配置项。
         */
        private String messageMode = "plain";

        /** OAuth 授权配置 */
        private OauthConfig oauth = new OauthConfig();

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getAesKey() {
            return aesKey;
        }

        public void setAesKey(String aesKey) {
            this.aesKey = aesKey;
        }

        /**
         * @return 接收消息模式（plain/safe，默认 plain）
         */
        public String getMessageMode() {
            return messageMode;
        }

        public void setMessageMode(String messageMode) {
            this.messageMode = messageMode;
        }

        public OauthConfig getOauth() {
            return oauth;
        }

        public void setOauth(OauthConfig oauth) {
            this.oauth = oauth;
        }
    }

    /**
     * OAuth 授权配置，对齐 PHP {@code official_account.default.oauth} 段。
     */
    public static class OauthConfig {

        /** 授权作用域，如 snsapi_base / snsapi_userinfo */
        private String scopes = "snsapi_base";

        /** OAuth 回调地址 */
        private String callback;

        /** 是否强制 HTTPS */
        private boolean enforceHttps = true;

        public String getScopes() {
            return scopes;
        }

        public void setScopes(String scopes) {
            this.scopes = scopes;
        }

        public String getCallback() {
            return callback;
        }

        public void setCallback(String callback) {
            this.callback = callback;
        }

        public boolean isEnforceHttps() {
            return enforceHttps;
        }

        public void setEnforceHttps(boolean enforceHttps) {
            this.enforceHttps = enforceHttps;
        }
    }

    /**
     * 小程序配置，对齐 PHP {@code mini_app.*} 段。
     * <p>
     * 包含小程序的 appId、secret、token、aesKey 以及业务类型标识。
     * type 字段用于区分不同业务的小程序：
     * <ul>
     *   <li>type=2：客服小程序（如 wx7051c4a2a779d651）</li>
     *   <li>type=3：管理端小程序（如 wxb33c8c0f6bea3602）</li>
     * </ul>
     */
    public static class MiniAppConfig {

        /** 小程序 AppID */
        private String appId;

        /** 小程序 AppSecret */
        private String secret;

        /** 小程序消息校验 Token */
        private String token;

        /** 小程序消息加解密密钥（EncodingAESKey） */
        private String aesKey;

        /** 业务类型：2=客服小程序，3=管理端小程序 */
        private int type;

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getAesKey() {
            return aesKey;
        }

        public void setAesKey(String aesKey) {
            this.aesKey = aesKey;
        }

        public int getType() {
            return type;
        }

        public void setType(int type) {
            this.type = type;
        }
    }

    /**
     * HTTP 客户端配置，控制 OkHttp 的超时与重试行为。
     */
    public static class HttpConfig {

        /** 连接与读取超时时间（秒），默认 5.0 */
        private double timeout = 5.0;

        /** 是否启用失败重试，默认 true */
        private boolean retry = true;

        public double getTimeout() {
            return timeout;
        }

        public void setTimeout(double timeout) {
            this.timeout = timeout;
        }

        public boolean isRetry() {
            return retry;
        }

        public void setRetry(boolean retry) {
            this.retry = retry;
        }
    }
}
