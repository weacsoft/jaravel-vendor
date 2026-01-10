package com.weacsoft.jaravel.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

public class JwtService {

    private final JwtConfig config;

    private final JwtBlacklist blacklist;

    private final SecretKey signingKey;

    public JwtService() {
        this(new JwtConfig());
    }

    public JwtService(JwtConfig config) {
        this.config = config;
        this.blacklist = new JwtBlacklist(config);
        this.signingKey = Keys.hmacShaKeyFor(config.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public JwtService(JwtConfig config, JwtBlacklist blacklist) {
        this.config = config;
        this.blacklist = blacklist;
        this.signingKey = Keys.hmacShaKeyFor(config.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String subject) {
        return generateToken(subject, config.getAccessTokenTtl(), "access");
    }

    public String generateToken(String subject, long ttl) {
        return generateToken(subject, ttl, "access");
    }

    public String generateToken(String subject, long ttl, String type) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + ttl);

        JwtPayload payload = new JwtPayload(subject);
        payload.setType(type);
        payload.setIssuedAt(now);
        payload.setExpiresAt(expiryDate);
        payload.setIssuer(config.getIssuer());
        payload.setAudience(config.getAudience());

        return generateTokenFromPayload(payload);
    }

    public String generateToken(JwtPayload payload) {
        return generateTokenFromPayload(payload);
    }

    private String generateTokenFromPayload(JwtPayload payload) {
        Date now = new Date();
        if (payload.getIssuedAt() == null) {
            payload.setIssuedAt(now);
        }

        io.jsonwebtoken.JwtBuilder builder = Jwts.builder()
                .setSubject(payload.getSubject())
                .setIssuedAt(payload.getIssuedAt())
                .setIssuer(payload.getIssuer() != null ? payload.getIssuer() : config.getIssuer())
                .setAudience(payload.getAudience() != null ? payload.getAudience() : config.getAudience())
                .signWith(signingKey);

        if (payload.getExpiresAt() != null) {
            builder.setExpiration(payload.getExpiresAt());
        }

        if (payload.getNotBefore() != null) {
            builder.setNotBefore(payload.getNotBefore());
        }

        if (payload.getJwtId() != null) {
            builder.setId(payload.getJwtId());
        }

        if (payload.getType() != null) {
            builder.claim("type", payload.getType());
        }

        if (payload.getClaims() != null && !payload.getClaims().isEmpty()) {
            for (Map.Entry<String, Object> entry : payload.getClaims().entrySet()) {
                builder.claim(entry.getKey(), entry.getValue());
            }
        }

        return builder.compact();
    }

    public String generateRefreshToken(String subject) {
        return generateToken(subject, config.getRefreshTokenTtl(), "refresh");
    }

    public JwtPayload parseToken(String token) {
        if (config.isBlacklistEnabled() && blacklist.isBlacklisted(token)) {
            throw new JwtBlacklistedException("Token has been blacklisted");
        }

        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(signingKey)
                    .parseClaimsJws(token)
                    .getBody();

            return buildPayloadFromClaims(claims);
        } catch (ExpiredJwtException e) {
            throw new JwtExpiredException("Token has expired", e);
        } catch (UnsupportedJwtException | MalformedJwtException | SignatureException e) {
            throw new JwtInvalidException("Invalid token", e);
        } catch (Exception e) {
            throw new JwtException("Error parsing token", e);
        }
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    public String refreshToken(String refreshToken) {
        JwtPayload payload = parseToken(refreshToken);
        if (!"refresh".equals(payload.getType())) {
            throw new JwtInvalidException("Invalid refresh token");
        }

        String subject = payload.getSubject();
        return generateToken(subject);
    }

    public String refreshAccessToken(String refreshToken) {
        return refreshToken(refreshToken);
    }

    public void invalidateToken(String token) {
        if (!config.isBlacklistEnabled()) {
            throw new JwtException("Blacklist is disabled");
        }

        JwtPayload payload = parseToken(token);
        Date expiresAt = payload.getExpiresAt();
        if (expiresAt != null) {
            blacklist.add(token, expiresAt);
        } else {
            blacklist.add(token, config.getAccessTokenTtl());
        }
    }

    public void invalidateToken(String token, long ttl) {
        if (!config.isBlacklistEnabled()) {
            throw new JwtException("Blacklist is disabled");
        }
        blacklist.add(token, ttl);
    }

    public void invalidateAllTokens(String subject) {
        throw new JwtException("invalidateAllTokens not implemented - requires token storage");
    }

    public String extractSubject(String token) {
        JwtPayload payload = parseToken(token);
        return payload.getSubject();
    }

    public Date extractExpiration(String token) {
        JwtPayload payload = parseToken(token);
        return payload.getExpiresAt();
    }

    public boolean isTokenExpired(String token) {
        try {
            Date expiration = extractExpiration(token);
            return expiration.before(new Date());
        } catch (JwtException e) {
            return true;
        }
    }

    public String getTokenFromHeader(String authHeader) {
        if (authHeader == null || authHeader.isEmpty()) {
            return null;
        }

        String prefix = config.getAccessTokenPrefix();
        if (authHeader.startsWith(prefix)) {
            return authHeader.substring(prefix.length());
        }

        return authHeader;
    }

    public JwtConfig getConfig() {
        return config;
    }

    public TokenResult validateWithRefresh(String token) {
        return validateWithRefresh(token, true);
    }

    public TokenResult validateWithRefresh(String token, boolean generateNewToken) {
        if (config.isBlacklistEnabled() && blacklist.isBlacklisted(token)) {
            return TokenResult.builder()
                    .valid(false)
                    .expired(false)
                    .build();
        }

        try {
            JwtPayload payload = parseToken(token);
            return TokenResult.builder()
                    .payload(payload)
                    .valid(true)
                    .expired(false)
                    .build();
        } catch (JwtExpiredException e) {
            return handleExpiredToken(token, e, generateNewToken);
        } catch (JwtException e) {
            return TokenResult.builder()
                    .valid(false)
                    .expired(false)
                    .build();
        }
    }

    private TokenResult handleExpiredToken(String token, JwtExpiredException e, boolean generateNewToken) {
        try {
            JwtPayload payload = parseTokenWithoutExpiryCheck(token);

            TokenResult.Builder builder = TokenResult.builder(payload)
                    .valid(true)
                    .expired(true);

            if (generateNewToken && "access".equals(payload.getType())) {
                String newToken = generateToken(payload.getSubject());
                builder.newToken(newToken);

                if (config.isBlacklistEnabled()) {
                    blacklist.add(token, config.getBlacklistGracePeriod());
                }
            }

            return builder.build();
        } catch (Exception ex) {
            return TokenResult.builder()
                    .valid(false)
                    .expired(true)
                    .build();
        }
    }

    public JwtPayload parseTokenWithoutExpiryCheck(String token) {
        if (config.isBlacklistEnabled() && blacklist.isBlacklisted(token)) {
            throw new JwtBlacklistedException("Token has been blacklisted");
        }

        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(signingKey)
                    .parseClaimsJws(token)
                    .getBody();

            return buildPayloadFromClaims(claims);
        } catch (ExpiredJwtException e) {
            Claims claims = e.getClaims();
            if (claims != null) {
                return buildPayloadFromClaims(claims);
            }
            throw new JwtExpiredException("Token has expired", e);
        } catch (UnsupportedJwtException | MalformedJwtException | SignatureException e) {
            throw new JwtInvalidException("Invalid token", e);
        } catch (Exception e) {
            throw new JwtException("Error parsing token", e);
        }
    }

    private JwtPayload buildPayloadFromClaims(Claims claims) {
        JwtPayload payload = new JwtPayload();
        payload.setSubject(claims.getSubject());
        payload.setIssuedAt(claims.getIssuedAt());
        payload.setExpiresAt(claims.getExpiration());
        payload.setNotBefore(claims.getNotBefore());
        payload.setIssuer(claims.getIssuer());
        payload.setAudience(claims.getAudience());
        payload.setJwtId(claims.getId());

        if (claims.get("type") != null) {
            payload.setType(claims.get("type").toString());
        }

        for (Map.Entry<String, Object> entry : claims.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (!"sub".equals(key) && !"iat".equals(key) && !"exp".equals(key)
                    && !"nbf".equals(key) && !"iss".equals(key) && !"aud".equals(key)
                    && !"jti".equals(key) && !"type".equals(key)) {
                payload.addClaim(key, value);
            }
        }

        return payload;
    }

    public JwtBlacklist getBlacklist() {
        return blacklist;
    }
}
