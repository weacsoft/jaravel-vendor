package com.weacsoft.jaravel.vendor.jwt;

import com.weacsoft.jaravel.vendor.cache.CacheStore;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT 服务，对齐 manage8 的 {@code Tymon\JWTAuth}。
 * <p>
 * 提供 access token / refresh token 的签发、解析、校验，以及：
 * <ul>
 *   <li><b>token 刷新（续期）</b>：{@link #shouldRefresh(String)} 判断是否该续期，
 *       {@link #refresh(String)} 用 refresh token 换取新 access token；</li>
 *   <li><b>登出黑名单</b>：{@link #blacklist(String)} 将 token 加入缓存黑名单，
 *       {@link #isBlacklisted(String)} 校验是否已注销，{@link #validate(String)} 自动拒绝黑名单 token。</li>
 * </ul>
 *
 * <h3>线程安全</h3>
 * 本类为无状态单例（{@code config}、{@code key}、{@code blacklistStore} 均为构造后不可变字段），
 * 可被多线程并发安全调用。黑名单状态全部委托给 {@link CacheStore}（底层驱动如
 * {@code ArrayCacheDriver} / {@code FileCacheDriver} 自身线程安全）。
 */
public class JwtService {

    private final JwtConfig config;
    private final SecretKey key;
    /** 黑名单缓存 store（array / file / redis 等），构造后不可变 */
    private final CacheStore blacklistStore;

    public JwtService(JwtConfig config, CacheStore blacklistStore) {
        this.config = config;
        this.key = Keys.hmacShaKeyFor(config.getSecret().getBytes(StandardCharsets.UTF_8));
        this.blacklistStore = blacklistStore;
    }

    /** 签发 access token */
    public String generate(String subject) {
        return generate(subject, Map.of(), config.getTtl());
    }

    /** 签发 access token（带自定义声明） */
    public String generate(String subject, Map<String, Object> claims, long ttl) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(subject)
                .addClaims(claims)
                .setIssuer(config.getIssuer())
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + ttl))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /** 签发 refresh token（带 type=refresh 声明，有效期取 refreshTtl） */
    public String generateRefreshToken(String subject) {
        return generate(subject, Map.of("type", "refresh"), config.getRefreshTtl());
    }

    /** 解析 token，返回 Claims */
    public Claims parse(String token) {
        Jws<Claims> jws = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
        return jws.getBody();
    }

    /**
     * 校验 token 是否有效：签名正确、未过期、<b>且不在黑名单中</b>。
     */
    public boolean validate(String token) {
        try {
            parse(token);
            return !isBlacklisted(token);
        } catch (Exception e) {
            return false;
        }
    }

    public String getSubject(String token) {
        return parse(token).getSubject();
    }

    public boolean isExpired(String token) {
        try {
            return parse(token).getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    /** 获取 token 的签发时间 */
    public Date getIssuedAt(String token) {
        return parse(token).getIssuedAt();
    }

    /** 获取 token 的过期时间 */
    public Date getExpiration(String token) {
        return parse(token).getExpiration();
    }

    /**
     * 判断 token 是否应当刷新（续期）。
     * <p>
     * 当 token 已过其 TTL 的一半时返回 {@code true}，对齐 Laravel tymon/jwt-auth
     * 的「token 即将过期时自动续期」机制。token 无效或已过期返回 {@code false}。
     *
     * @param token access token
     * @return 是否应当刷新
     */
    public boolean shouldRefresh(String token) {
        try {
            Claims claims = parse(token);
            Date issuedAt = claims.getIssuedAt();
            Date expiration = claims.getExpiration();
            if (issuedAt == null || expiration == null) {
                return false;
            }
            long now = System.currentTimeMillis();
            // 已过期则不需要刷新（应由 refresh token 换取新 token）
            if (now >= expiration.getTime()) {
                return false;
            }
            long halfway = issuedAt.getTime() + (expiration.getTime() - issuedAt.getTime()) / 2;
            return now >= halfway;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 使用 refresh token 换取新的 access token。
     * <p>
     * 校验 refresh token：签名有效、未过期、未在黑名单中、且声明 {@code type=refresh}。
     * 校验通过后签发新的 access token（不签发新 refresh token，对齐 tymon/jwt-auth 单次续期）。
     *
     * @param refreshToken refresh token
     * @return 新的 access token，校验失败返回 {@code null}
     */
    public String refresh(String refreshToken) {
        try {
            Claims claims = parse(refreshToken);
            // 必须是 refresh 类型
            if (!"refresh".equals(claims.get("type"))) {
                return null;
            }
            // 黑名单中的 refresh token 不可用
            if (isBlacklisted(refreshToken)) {
                return null;
            }
            return generate(claims.getSubject());
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 黑名单（登出踢 token） ====================

    /**
     * 将 token 加入黑名单（登出时调用）。
     * <p>
     * 黑名单条目的 TTL 设为 token 剩余有效期（秒），token 自然过期后黑名单条目自动清除，
     * 避免黑名单无限膨胀。
     *
     * @param token access token 或 refresh token
     */
    public void blacklist(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        long ttlSeconds = remainingTtlSeconds(token);
        // ttlSeconds <= 0 表示已过期或无法解析，仍写入一条短时记录防止边界竞态
        if (ttlSeconds <= 0) {
            ttlSeconds = 1;
        }
        blacklistStore.put(blacklistKey(token), "1", ttlSeconds);
    }

    /**
     * 判断 token 是否在黑名单中（已登出）。
     */
    public boolean isBlacklisted(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        return blacklistStore.has(blacklistKey(token));
    }

    /** 计算黑名单缓存键 */
    private String blacklistKey(String token) {
        return config.getBlacklistPrefix() + token;
    }

    /** 计算 token 剩余有效期（秒），已过期或无法解析返回 0 */
    private long remainingTtlSeconds(String token) {
        try {
            Date expiration = parse(token).getExpiration();
            long remaining = expiration.getTime() - System.currentTimeMillis();
            return remaining > 0 ? remaining / 1000 : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
