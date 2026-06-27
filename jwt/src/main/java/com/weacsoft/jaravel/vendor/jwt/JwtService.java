package com.weacsoft.jaravel.vendor.jwt;

import com.weacsoft.jaravel.vendor.cache.CacheStore;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
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
 *   <li><b>登出黑名单</b>（需开启 {@code blacklistEnabled}）：{@link #blacklist(String)} 将 token 加入缓存黑名单，
 *       {@link #isBlacklisted(String)} 校验是否已注销，{@link #validate(String)} 自动拒绝黑名单 token；</li>
 *   <li><b>宽限期</b>（需开启 {@code blacklistEnabled} 且 {@code gracePeriodSeconds > 0}）：
 *       过期 token 在宽限期内仍可请求一次，{@link #isInGracePeriod(String)} 判断是否处于宽限期。</li>
 * </ul>
 *
 * <h3>黑名单开关</h3>
 * 当 {@link JwtConfig#isBlacklistEnabled()} 为 {@code false}（默认）时，本类表现为标准 JWT：
 * 仅校验签名与过期，不依赖任何缓存。{@link #blacklist(String)} 和 {@link #isBlacklisted(String)}
 * 成为空操作/始终返回 false。开启后才真正读写 {@link CacheStore}。
 *
 * <h3>线程安全</h3>
 * 本类为无状态单例（{@code config}、{@code key}、{@code blacklistStore} 均为构造后不可变字段），
 * 可被多线程并发安全调用。黑名单状态全部委托给 {@link CacheStore}（底层驱动如
 * {@code ArrayCacheDriver} / {@code FileCacheDriver} 自身线程安全）。
 */
public class JwtService {

    private final JwtConfig config;
    private final SecretKey key;
    /** 黑名单缓存 store（array / file / redis / database 等），blacklistEnabled=false 时可为 null */
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

    /** 解析 token，返回 Claims。token 过期时抛出 {@link ExpiredJwtException} */
    public Claims parse(String token) {
        Jws<Claims> jws = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
        return jws.getBody();
    }

    /**
     * 校验 token 是否有效：签名正确、未过期、且不在黑名单中。
     * <p>
     * 当黑名单关闭时，仅校验签名与过期。
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

    /**
     * 从可能已过期的 token 中获取 subject。
     * <p>
     * 用于宽限期场景：token 已过期但需要取出 subject 以查询用户。
     *
     * @param token access token（可能已过期）
     * @return subject 字符串，无法解析返回 {@code null}
     */
    public String getSubjectFromExpired(String token) {
        try {
            return parse(token).getSubject();
        } catch (ExpiredJwtException e) {
            // 过期 token 仍可从异常中获取 Claims
            return e.getClaims().getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isExpired(String token) {
        try {
            return parse(token).getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
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
     * 当 token 已过其 TTL 的一半时返回 {@code true}。token 无效或已过期返回 {@code false}。
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
     * 校验通过后签发新的 access token。
     *
     * @param refreshToken refresh token
     * @return 新的 access token，校验失败返回 {@code null}
     */
    public String refresh(String refreshToken) {
        try {
            Claims claims = parse(refreshToken);
            if (!"refresh".equals(claims.get("type"))) {
                return null;
            }
            if (isBlacklisted(refreshToken)) {
                return null;
            }
            return generate(claims.getSubject());
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 宽限期 ====================

    /**
     * 判断 token 是否处于宽限期内。
     * <p>
     * 宽限期是指 token 已过期，但距离过期时间未超过 {@link JwtConfig#getGracePeriodSeconds()} 秒。
     * 在此期间，token 仍可用于请求一次（由 {@link JwtGuard} 处理），请求成功后会签发新 token
     * 并将旧 token 加入黑名单。
     * <p>
     * 当 {@code gracePeriodSeconds <= 0} 时，宽限期功能关闭，始终返回 {@code false}。
     *
     * @param token access token
     * @return 是否处于宽限期内
     */
    public boolean isInGracePeriod(String token) {
        if (config.getGracePeriodSeconds() <= 0) {
            return false;
        }
        try {
            parse(token);
            // 未过期，不在宽限期
            return false;
        } catch (ExpiredJwtException e) {
            long expiredAt = e.getClaims().getExpiration().getTime();
            long now = System.currentTimeMillis();
            return now < expiredAt + config.getGracePeriodSeconds() * 1000L;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 黑名单（登出踢 token） ====================

    /**
     * 将 token 加入黑名单（登出或宽限期使用后调用）。
     * <p>
     * 当 {@code blacklistEnabled=false} 时为空操作。
     * <p>
     * 黑名单条目的 TTL 设为 token 剩余有效期（秒），token 自然过期后黑名单条目自动清除。
     * 对于已过期的 token（宽限期场景），TTL 设为宽限期剩余秒数。
     *
     * @param token access token 或 refresh token
     */
    public void blacklist(String token) {
        if (!config.isBlacklistEnabled() || blacklistStore == null) {
            return;
        }
        if (token == null || token.isEmpty()) {
            return;
        }
        long ttlSeconds = remainingTtlSeconds(token);
        if (ttlSeconds <= 0) {
            // 已过期，用宽限期剩余秒数
            ttlSeconds = Math.max(1, config.getGracePeriodSeconds());
        }
        blacklistStore.put(blacklistKey(token), "1", ttlSeconds);
    }

    /**
     * 判断 token 是否在黑名单中（已登出或宽限期已使用）。
     * <p>
     * 当 {@code blacklistEnabled=false} 时始终返回 {@code false}。
     */
    public boolean isBlacklisted(String token) {
        if (!config.isBlacklistEnabled() || blacklistStore == null) {
            return false;
        }
        if (token == null || token.isEmpty()) {
            return false;
        }
        return blacklistStore.has(blacklistKey(token));
    }

    /**
     * 从黑名单中移除 token（误杀恢复）。
     * <p>
     * 当 {@code blacklistEnabled=false} 时为空操作。
     * <p>
     * 适用于误将 token 加入黑名单后需要恢复的场景：移除黑名单条目后，
     * 该 token 在有效期内可再次通过 {@link #validate} 校验。
     *
     * @param token 需要从黑名单移除的 token
     */
    public void removeFromBlacklist(String token) {
        if (!config.isBlacklistEnabled() || blacklistStore == null) {
            return;
        }
        if (token == null || token.isEmpty()) {
            return;
        }
        blacklistStore.forget(blacklistKey(token));
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
        } catch (ExpiredJwtException e) {
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
