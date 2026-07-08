package com.weacsoft.jaravel.vendor.jwt;

import com.weacsoft.jaravel.vendor.cache.ArrayCacheDriver;
import com.weacsoft.jaravel.vendor.cache.CacheStore;
import com.weacsoft.jaravel.vendor.cache.DefaultCacheStore;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link JwtService} 测试。
 * <p>
 * 覆盖 token 签发（generate / generateRefreshToken）、解析（parse / getSubject）、
 * 校验（validate / isExpired）、刷新（shouldRefresh / refresh），
 * 以及黑名单（blacklist / isBlacklisted / removeFromBlacklist）功能。
 */
class JwtServiceTest {

    private JwtConfig config;
    private CacheStore blacklistStore;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        config = new JwtConfig();
        // 使用足够长的密钥以满足 HS256 要求
        config.setSecret("jaravel-unit-test-secret-key-32-bytes-long!!");
        blacklistStore = new DefaultCacheStore(new ArrayCacheDriver(), null);
        jwtService = new JwtService(config, blacklistStore);
    }

    @Test
    void testGenerateAndParse() {
        String token = jwtService.generate("user:1001");

        assertNotNull(token);
        Claims claims = jwtService.parse(token);
        assertEquals("user:1001", claims.getSubject());
        assertEquals("jaravel", claims.getIssuer());
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    void testGenerateWithCustomClaims() {
        String token = jwtService.generate("user:1002", Map.of("role", "admin", "dept", "tech"), 60_000L);

        Claims claims = jwtService.parse(token);
        assertEquals("user:1002", claims.getSubject());
        assertEquals("admin", claims.get("role"));
        assertEquals("tech", claims.get("dept"));
    }

    @Test
    void testGetSubject() {
        String token = jwtService.generate("user:1003");
        assertEquals("user:1003", jwtService.getSubject(token));
    }

    @Test
    void testValidateValidToken() {
        String token = jwtService.generate("user:1004");
        assertTrue(jwtService.validate(token));
    }

    @Test
    void testValidateRejectsTamperedToken() {
        String token = jwtService.generate("user:1005");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertFalse(jwtService.validate(tampered), "篡改后的 token 校验应失败");
    }

    @Test
    void testIsExpiredForExpiredToken() {
        // 负 TTL：签发即过期
        String token = jwtService.generate("user:1006", Map.of(), -1000L);
        assertTrue(jwtService.isExpired(token), "过期 token 的 isExpired 应为 true");
        assertFalse(jwtService.validate(token), "过期 token 校验应失败");
    }

    @Test
    void testGetSubjectFromExpiredToken() {
        String token = jwtService.generate("user:1007", Map.of(), -1000L);
        // 过期 token 仍可从异常中取出 subject
        assertEquals("user:1007", jwtService.getSubjectFromExpired(token));
    }

    @Test
    void testGenerateRefreshTokenAndRefresh() {
        String refreshToken = jwtService.generateRefreshToken("user:1008");

        Claims claims = jwtService.parse(refreshToken);
        assertEquals("refresh", claims.get("type"));

        // 使用 refresh token 换取新 access token
        String newAccessToken = jwtService.refresh(refreshToken);
        assertNotNull(newAccessToken, "合法 refresh token 应换取新 access token");
        assertEquals("user:1008", jwtService.getSubject(newAccessToken));
    }

    @Test
    void testRefreshFailsWithAccessToken() {
        // access token 没有 type=refresh 声明，不能用于 refresh
        String accessToken = jwtService.generate("user:1009");
        assertNull(jwtService.refresh(accessToken), "access token 不能用于 refresh");
    }

    @Test
    void testShouldRefreshAfterHalfway() throws InterruptedException {
        // TTL = 10000ms，过半时间 = 5000ms，留足裕量避免 CI 环境抖动
        String token = jwtService.generate("user:1010", Map.of(), 10_000L);

        // 刚签发，未过半
        assertFalse(jwtService.shouldRefresh(token), "未过半不应刷新");

        // 等待超过一半（5.5秒 > 5秒过半点，且远未到 10 秒过期）
        Thread.sleep(5_500);
        assertTrue(jwtService.shouldRefresh(token), "过半后应刷新");
    }

    @Test
    void testShouldRefreshReturnsFalseForInvalidToken() {
        assertFalse(jwtService.shouldRefresh("invalid.token.here"));
    }

    // ==================== 黑名单 ====================

    @Test
    void testBlacklistDisabledByDefault() {
        config.setBlacklistEnabled(false);
        JwtService service = new JwtService(config, blacklistStore);

        String token = service.generate("user:1011");
        // 黑名单关闭时，blacklist 为空操作，isBlacklisted 始终 false
        service.blacklist(token);
        assertFalse(service.isBlacklisted(token));
        assertTrue(service.validate(token), "黑名单关闭时 token 仍有效");
    }

    @Test
    void testBlacklistRejectsTokenWhenEnabled() {
        config.setBlacklistEnabled(true);
        JwtService service = new JwtService(config, blacklistStore);

        String token = service.generate("user:1012");
        assertTrue(service.validate(token), "加入黑名单前 token 应有效");

        service.blacklist(token);
        assertTrue(service.isBlacklisted(token), "加入黑名单后应被标记");
        assertFalse(service.validate(token), "黑名单中的 token 校验应失败");
    }

    @Test
    void testRemoveFromBlacklistRestoresToken() {
        config.setBlacklistEnabled(true);
        JwtService service = new JwtService(config, blacklistStore);

        String token = service.generate("user:1013");
        service.blacklist(token);
        assertTrue(service.isBlacklisted(token));

        service.removeFromBlacklist(token);
        assertFalse(service.isBlacklisted(token), "移除黑名单后应不再标记");
        assertTrue(service.validate(token), "移除黑名单后 token 应重新有效");
    }

    @Test
    void testBlacklistedRefreshTokenCannotRefresh() {
        config.setBlacklistEnabled(true);
        JwtService service = new JwtService(config, blacklistStore);

        String refreshToken = service.generateRefreshToken("user:1014");
        service.blacklist(refreshToken);

        assertNull(service.refresh(refreshToken), "黑名单中的 refresh token 不能换取新 token");
    }
}
