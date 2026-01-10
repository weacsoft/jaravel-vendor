package com.weacsoft.jaravel.jwt;

public class JwtConfig {

    private String secret = "your-secret-key-change-this-in-production";

    private String issuer = "jaravel";

    private String audience = "jaravel-users";

    private long accessTokenTtl = 60 * 60 * 1000;

    private long refreshTokenTtl = 7 * 24 * 60 * 60 * 1000;

    private String accessTokenHeader = "Authorization";

    private String accessTokenPrefix = "Bearer ";

    private String refreshTokenHeader = "Refresh-Token";

    private String algorithm = "HS256";

    private boolean blacklistEnabled = true;

    private long blacklistGracePeriod = 5 * 60 * 1000;

    public JwtConfig() {
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public long getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(long accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public long getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(long refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public String getAccessTokenHeader() {
        return accessTokenHeader;
    }

    public void setAccessTokenHeader(String accessTokenHeader) {
        this.accessTokenHeader = accessTokenHeader;
    }

    public String getAccessTokenPrefix() {
        return accessTokenPrefix;
    }

    public void setAccessTokenPrefix(String accessTokenPrefix) {
        this.accessTokenPrefix = accessTokenPrefix;
    }

    public String getRefreshTokenHeader() {
        return refreshTokenHeader;
    }

    public void setRefreshTokenHeader(String refreshTokenHeader) {
        this.refreshTokenHeader = refreshTokenHeader;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public boolean isBlacklistEnabled() {
        return blacklistEnabled;
    }

    public void setBlacklistEnabled(boolean blacklistEnabled) {
        this.blacklistEnabled = blacklistEnabled;
    }

    public long getBlacklistGracePeriod() {
        return blacklistGracePeriod;
    }

    public void setBlacklistGracePeriod(long blacklistGracePeriod) {
        this.blacklistGracePeriod = blacklistGracePeriod;
    }
}
