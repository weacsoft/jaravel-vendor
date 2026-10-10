package com.weacsoft.jaravel.vendor.jwt;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT 签发方（{@code iss}）校验回归测试（审计 L5）。
 * <p>
 * 修复前：签发时写入 {@code iss}，但解析时完全不校验 —— 用同一把密钥签发的<b>其它系统</b>
 * 令牌会被本系统接受（多应用共用密钥时是真实越权面）。
 * <p>
 * 注：框架<b>没有</b> {@code aud}（受众）字段、签发端也从未写入 aud，
 * 因此本轮只做 iss 校验；aud 必须「配置 + 签发 + 校验」同批上线，否则会拒掉全部自签令牌。
 */
class JwtIssuerValidationTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef";

    private static JwtService service(String issuer) {
        return new JwtService(new JwtConfig().setSecret(SECRET).setIssuer(issuer));
    }

    /** 手工构造一个「不带 iss」的令牌（同一把密钥），模拟第三方系统签发 */
    private static String tokenWithoutIssuer() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("user-1")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
    }

    @Test
    void ownTokenIsAccepted() {
        JwtService jwt = service("app-a");
        String token = jwt.generate("user-1");
        assertNotNull(jwt.parse(token));
        assertTrue(jwt.validate(token), "本系统签发的令牌应通过");
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        JwtService appA = service("app-a");
        JwtService appB = service("app-b");
        String tokenFromB = appB.generate("user-1");

        assertThrows(JwtException.class, () -> appA.parse(tokenFromB),
                "共用密钥但 issuer 不同的令牌必须被拒绝");
        assertFalse(appA.validate(tokenFromB));
    }

    @Test
    void tokenWithoutIssuerIsRejected() {
        JwtService jwt = service("app-a");
        String noIssuer = tokenWithoutIssuer();

        assertThrows(JwtException.class, () -> jwt.parse(noIssuer),
                "缺少 iss 的令牌必须被拒绝（否则同密钥的第三方令牌可绕过校验）");
        assertFalse(jwt.validate(noIssuer));
    }

    @Test
    void blankIssuerDisablesValidation() {
        // 未配置 issuer（空白）时跳过校验：允许接入不带 iss 的外部令牌
        JwtService jwt = service("");
        assertDoesNotThrow(() -> jwt.parse(tokenWithoutIssuer()),
                "issuer 未配置时不应因缺少 iss 而拒绝");
    }
}