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
 *     refresh-enabled: true
 *     # 黑名单（登出踢 token），默认关闭。关闭时为标准 JWT
 *     blacklist-enabled: false
 *     blacklist-store:                # 为空时使用 cache 模块默认 store
 *     blacklist-prefix: "jwt:blacklist:"
 *     # 宽限期（秒），默认 0 关闭。过期 token 在宽限期内仍可请求一次，返回新 token
 *     grace-period-seconds: 0
 *     grace-header: X-New-Token
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
    /** 是否启用黑名单（登出踢 token），默认关闭，关闭时为标准 JWT */
    private boolean blacklistEnabled = false;
    /** 黑名单使用的缓存 store 名称，为空时使用 cache 模块默认 store */
    private String blacklistStore = "";
    /** 黑名单缓存键前缀 */
    private String blacklistPrefix = "jwt:blacklist:";
    /** 宽限期秒数，默认 0（关闭） */
    private long gracePeriodSeconds = 0;
    /** 宽限期续期时新 token 的响应头名称 */
    private String graceHeader = "X-New-Token";

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

    public boolean isBlacklistEnabled() {
        return blacklistEnabled;
    }

    public void setBlacklistEnabled(boolean blacklistEnabled) {
        this.blacklistEnabled = blacklistEnabled;
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

    public long getGracePeriodSeconds() {
        return gracePeriodSeconds;
    }

    public void setGracePeriodSeconds(long gracePeriodSeconds) {
        this.gracePeriodSeconds = gracePeriodSeconds;
    }

    public String getGraceHeader() {
        return graceHeader;
    }

    public void setGraceHeader(String graceHeader) {
        this.graceHeader = graceHeader;
    }
}
