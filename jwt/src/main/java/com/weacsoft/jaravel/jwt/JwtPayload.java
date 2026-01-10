package com.weacsoft.jaravel.jwt;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class JwtPayload {

    private String subject;

    private String type;

    private Date issuedAt;

    private Date expiresAt;

    private Date notBefore;

    private String issuer;

    private String audience;

    private String jwtId;

    private Map<String, Object> claims;

    public JwtPayload() {
        this.claims = new HashMap<>();
    }

    public JwtPayload(String subject) {
        this();
        this.subject = subject;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Date getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(Date issuedAt) {
        this.issuedAt = issuedAt;
    }

    public Date getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Date expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Date getNotBefore() {
        return notBefore;
    }

    public void setNotBefore(Date notBefore) {
        this.notBefore = notBefore;
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

    public String getJwtId() {
        return jwtId;
    }

    public void setJwtId(String jwtId) {
        this.jwtId = jwtId;
    }

    public Map<String, Object> getClaims() {
        return claims;
    }

    public void setClaims(Map<String, Object> claims) {
        this.claims = claims;
    }

    public JwtPayload addClaim(String key, Object value) {
        this.claims.put(key, value);
        return this;
    }

    public Object getClaim(String key) {
        return this.claims.get(key);
    }

    public boolean hasClaim(String key) {
        return this.claims.containsKey(key);
    }

    public JwtPayload removeClaim(String key) {
        this.claims.remove(key);
        return this;
    }
}
