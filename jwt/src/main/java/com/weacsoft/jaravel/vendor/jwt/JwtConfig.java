package com.weacsoft.jaravel.vendor.jwt;

/**
 * JWT 配置，对齐 manage8 的 {@code config/jwt.php}。
 * <p>
 * 包含 token 签发、刷新（refresh）与黑名单（logout）相关配置。
 */
public class JwtConfig {

    /** 签名密钥（生产环境务必更换） */
    private String secret = "jaravel-secret-key-change-this-in-production-32bytes";
    /** 签发者 */
    private String issuer = "jaravel";
    /** access token 有效期（毫秒），默认 1 小时 */
    private long ttl = 3600_000L;
    /** refresh token 有效期（毫秒），默认 7 天 */
    private long refreshTtl = 7 * 24 * 3600_000L;
    /** 请求头名 */
    private String header = "Authorization";
    /** token 前缀 */
    private String prefix = "Bearer ";
    /**
     * 是否启用 token 自动刷新（续期），默认启用。
     * <p>
     * 启用后，当 access token 过了 TTL 的一半时，下次请求会自动签发新 token，
     * 对齐 Laravel tymon/jwt-auth 的 {@code blacklist_grace_period} + 自动续期机制。
     * 项目可通过 {@code jaravel.jwt.refresh-enabled=false} 手动禁用。
     */
    private boolean refreshEnabled = true;
    /**
     * 黑名单使用的缓存 store 名称，默认 {@code array}。
     * <p>
     * 单机部署可用 {@code array}（内存）；多实例部署应使用 {@code file} 或 Redis 等共享缓存，
     * 以保证登出后所有节点都能识别黑名单 token。
     */
    private String blacklistStore = "array";
    /** 黑名单缓存键前缀 */
    private String blacklistPrefix = "jwt:blacklist:";

    public String getSecret() {
        return secret;
    }

    public JwtConfig setSecret(String secret) {
        this.secret = secret;
        return this;
    }

    public String getIssuer() {
        return issuer;
    }

    public JwtConfig setIssuer(String issuer) {
        this.issuer = issuer;
        return this;
    }

    public long getTtl() {
        return ttl;
    }

    public JwtConfig setTtl(long ttl) {
        this.ttl = ttl;
        return this;
    }

    public long getRefreshTtl() {
        return refreshTtl;
    }

    public JwtConfig setRefreshTtl(long refreshTtl) {
        this.refreshTtl = refreshTtl;
        return this;
    }

    public String getHeader() {
        return header;
    }

    public JwtConfig setHeader(String header) {
        this.header = header;
        return this;
    }

    public String getPrefix() {
        return prefix;
    }

    public JwtConfig setPrefix(String prefix) {
        this.prefix = prefix;
        return this;
    }

    public boolean isRefreshEnabled() {
        return refreshEnabled;
    }

    public JwtConfig setRefreshEnabled(boolean refreshEnabled) {
        this.refreshEnabled = refreshEnabled;
        return this;
    }

    public String getBlacklistStore() {
        return blacklistStore;
    }

    public JwtConfig setBlacklistStore(String blacklistStore) {
        this.blacklistStore = blacklistStore;
        return this;
    }

    public String getBlacklistPrefix() {
        return blacklistPrefix;
    }

    public JwtConfig setBlacklistPrefix(String blacklistPrefix) {
        this.blacklistPrefix = blacklistPrefix;
        return this;
    }
}
