package com.weacsoft.jaravel.vendor.jwt.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置属性，前缀 {@code jaravel.jwt}。
 * <pre>
 * jaravel:
 *   jwt:
 *     secret: your-secret-key
 *     ttl: 3600000
 *     refresh-ttl: 604800000
 *     header: Authorization
 *     prefix: "Bearer "
 *     # token 自动刷新（续期），默认 true
 *     refresh-enabled: true
 *     # 黑名单缓存 store，默认 array（内存）；多实例可用 file 或 redis
 *     blacklist-store: array
 *     blacklist-prefix: "jwt:blacklist:"
 * </pre>
 */
@ConfigurationProperties(prefix = "jaravel.jwt")
public class JwtProperties {

    /** 签名密钥（生产环境务必更换） */
    private String secret = "jaravel-secret-key-change-this-in-production-32bytes";
    /** access token 有效期（毫秒），默认 1 小时 */
    private long ttl = 3600_000L;
    /** refresh token 有效期（毫秒），默认 7 天 */
    private long refreshTtl = 7 * 24 * 3600_000L;
    /** 请求头名 */
    private String header = "Authorization";
    /** token 前缀 */
    private String prefix = "Bearer ";
    /** 是否启用 token 自动刷新（续期），默认启用 */
    private boolean refreshEnabled = true;
    /** 黑名单使用的缓存 store 名称，默认 array */
    private String blacklistStore = "array";
    /** 黑名单缓存键前缀 */
    private String blacklistPrefix = "jwt:blacklist:";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getTtl() {
        return ttl;
    }

    public void setTtl(long ttl) {
        this.ttl = ttl;
    }

    public long getRefreshTtl() {
        return refreshTtl;
    }

    public void setRefreshTtl(long refreshTtl) {
        this.refreshTtl = refreshTtl;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public boolean isRefreshEnabled() {
        return refreshEnabled;
    }

    public void setRefreshEnabled(boolean refreshEnabled) {
        this.refreshEnabled = refreshEnabled;
    }

    public String getBlacklistStore() {
        return blacklistStore;
    }

    public void setBlacklistStore(String blacklistStore) {
        this.blacklistStore = blacklistStore;
    }

    public String getBlacklistPrefix() {
        return blacklistPrefix;
    }

    public void setBlacklistPrefix(String blacklistPrefix) {
        this.blacklistPrefix = blacklistPrefix;
    }
}
