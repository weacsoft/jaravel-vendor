package com.weacsoft.jaravel.vendor.jwt;

/**
 * JWT 配置，对齐 manage8 的 {@code config/jwt.php}。
 * <p>
 * 包含 token 签发、刷新（refresh）、黑名单（logout）与宽限期（grace period）相关配置。
 * <p>
 * <b>设计原则</b>：当 {@link #blacklistEnabled} 为 {@code false}（默认）时，JWT 表现为标准形式
 * —— 仅校验签名与过期，不依赖任何缓存。开启黑名单后，登出踢 token 功能生效，
 * 需配合 cache 模块使用。宽限期功能需要黑名单开启才能工作（因为宽限期结束后需要将旧 token
 * 加入黑名单以防止重复使用）。
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
     * 启用后，当 access token 过了 TTL 的一半时，下次请求会自动签发新 token。
     */
    private boolean refreshEnabled = true;
    /**
     * 是否启用黑名单（登出踢 token），默认 <b>关闭</b>。
     * <p>
     * 关闭时 JWT 表现为标准形式：仅校验签名与过期，不依赖缓存模块。
     * 开启后，{@link JwtService#blacklist} 和 {@link JwtService#isBlacklisted} 生效，
     * 需配合 cache 模块使用（通过 {@link #blacklistStore} 指定缓存 store）。
     * <p>
     * 多实例部署建议开启黑名单并使用 redis 等共享缓存，保证登出后所有节点都能识别。
     */
    private boolean blacklistEnabled = false;
    /**
     * 黑名单使用的缓存 store 名称，为空时使用 cache 模块的默认 store
     *（由 {@code jaravel.cache.default-store} 决定，不关心具体实现）。
     * <p>
     * 仅当 {@link #blacklistEnabled} 为 {@code true} 时生效。
     * 可显式指定 store 名（如 "redis"）以覆盖默认行为。
     */
    private String blacklistStore = "";
    /** 黑名单缓存键前缀 */
    private String blacklistPrefix = "jwt:blacklist:";
    /**
     * 宽限期秒数，默认 {@code 0}（关闭）。
     * <p>
     * 当 access token 过期后，在宽限期时间内仍可正常请求一次：
     * <ul>
     *   <li>请求正常执行（用户通过认证）；</li>
     *   <li>响应 header 中携带新 token（通过 {@link #graceHeader} 指定的响应头）；</li>
     *   <li>旧 token 被加入黑名单，无法再次使用。</li>
     * </ul>
     * <b>注意</b>：宽限期功能需要 {@link #blacklistEnabled} 为 {@code true} 才能工作
     * （因为宽限期结束后需要将旧 token 加入黑名单以防止重复使用）。
     */
    private long gracePeriodSeconds = 0;
    /**
     * 宽限期续期时，新 token 放入响应 header 的名称，默认 {@code X-New-Token}。
     */
    private String graceHeader = "X-New-Token";

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

    public boolean isBlacklistEnabled() {
        return blacklistEnabled;
    }

    public JwtConfig setBlacklistEnabled(boolean blacklistEnabled) {
        this.blacklistEnabled = blacklistEnabled;
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

    public long getGracePeriodSeconds() {
        return gracePeriodSeconds;
    }

    public JwtConfig setGracePeriodSeconds(long gracePeriodSeconds) {
        this.gracePeriodSeconds = gracePeriodSeconds;
        return this;
    }

    public String getGraceHeader() {
        return graceHeader;
    }

    public JwtConfig setGraceHeader(String graceHeader) {
        this.graceHeader = graceHeader;
        return this;
    }
}
