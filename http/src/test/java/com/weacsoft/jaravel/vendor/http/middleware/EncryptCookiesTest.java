package com.weacsoft.jaravel.vendor.http.middleware;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import com.weacsoft.jaravel.vendor.http.controller.response.ResponseBuilder;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EncryptCookies} 安全回归测试。
 * <p>
 * 本类此前<b>完全没有测试</b>（安全关键中间件零覆盖），因此这里把「不可回退的安全不变量」
 * 逐条钉死：失败即丢弃（绝不保留请求原值）、只接受 v2 密文、带 MAC 且篡改可检、
 * 随机 IV、出站原地替换、旧格式兼容必须显式开启且白名单 + 安全敏感名硬拒绝。
 */
class EncryptCookiesTest {

    private static final String SECRET = "unit-test-secret-key-0123456789abcdef";

    /** 固定密钥的最小实现（避免依赖 Spring 上下文 / jaravel.key 兜底） */
    private static class TestCookies extends EncryptCookies {
        @Override
        protected String encryptionKey() {
            return SECRET;
        }
    }

    /** 开启旧格式兼容 + 通配白名单 */
    private static class LegacyCompatCookies extends TestCookies {
        @Override
        protected boolean allowLegacyFormat() {
            return true;
        }

        @Override
        protected String[] legacyFormatCookieNames() {
            return new String[]{"*"};
        }
    }

    /** 只允许 theme 走旧格式 */
    private static class WhitelistedCookies extends TestCookies {
        @Override
        protected boolean allowLegacyFormat() {
            return true;
        }

        @Override
        protected String[] legacyFormatCookieNames() {
            return new String[]{"theme"};
        }
    }

    /** 排除名单透传 */
    private static class ExceptedCookies extends TestCookies {
        @Override
        protected String[] except() {
            return new String[]{"XSRF-TOKEN"};
        }
    }

    /** 加密必失败（验证「失败时置空而不是下发明文」） */
    private static class BrokenEncryptCookies extends TestCookies {
        @Override
        protected String encrypt(String value) {
            throw new IllegalStateException("boom");
        }
    }

    private static Request requestWith(String name, String value) {
        Request request = new Request();
        request.addCookie(name, value);
        return request;
    }

    /**
     * 用 v1 算法（IV 全零 + UTF-8 零填充密钥）加密，用于构造旧格式样本。
     *
     * @param cookies 提供 legacyGenerateKey 的实现
     * @param plain   明文
     * @return 旧格式 Cookie 值
     */
    private static String legacyEncrypt(EncryptCookies cookies, byte[] plain) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, cookies.legacyGenerateKey(), new IvParameterSpec(new byte[16]));
        byte[] cipherText = cipher.doFinal(plain);
        byte[] combined = new byte[16 + cipherText.length];
        System.arraycopy(cipherText, 0, combined, 16, cipherText.length);
        return Base64.getEncoder().encodeToString(combined);
    }

    // ==================== v2 基本行为 ====================

    @Test
    void v2RoundTripSupportsUtf8() throws Exception {
        TestCookies cookies = new TestCookies();
        String value = "梦开始的地方-ü-✓";
        String encrypted = cookies.encrypt(value);
        assertTrue(encrypted.startsWith("v2:"), "出站必须是 v2 格式");
        assertEquals(value, cookies.decrypt(encrypted), "v2 往返必须无损");
    }

    @Test
    void sameValueEncryptsDifferentlyBecauseIvIsRandom() throws Exception {
        TestCookies cookies = new TestCookies();
        assertNotEquals(cookies.encrypt("same"), cookies.encrypt("same"),
                "IV 必须随机：同一明文两次加密不得得到相同密文");
    }

    @Test
    void tamperedCiphertextIsRejectedByMac() throws Exception {
        TestCookies cookies = new TestCookies();
        String encrypted = cookies.encrypt("login_web_id=42");
        byte[] raw = Base64.getDecoder().decode(encrypted.substring("v2:".length()));
        raw[20] ^= 0x01;            // 翻转密文区一个比特（CBC 下无 MAC 即可篡改）
        String tampered = "v2:" + Base64.getEncoder().encodeToString(raw);
        assertThrows(Exception.class, () -> cookies.decrypt(tampered),
                "带 MAC 的 v2 必须能检出比特翻转");
    }

    @Test
    void invalidOrPlainValuesAreRejected() {
        TestCookies cookies = new TestCookies();
        assertThrows(Exception.class, () -> cookies.decrypt("is_admin=1"), "明文不是 v2 密文");
        assertThrows(Exception.class, () -> cookies.decrypt("not-base64!!!"), "非法 Base64");
        assertThrows(Exception.class, () -> cookies.decrypt("v2:AAAA"), "长度不足");
        assertThrows(Exception.class, () -> cookies.decrypt(""), "空值");
    }

    // ==================== 失败即丢弃（核心不变量的红线）====================

    @Test
    void plaintextForgeryIsRemovedNotTrusted() {
        Request request = requestWith("is_admin", "1");
        new TestCookies().decryptCookies(request);
        assertNull(request.cookie("is_admin"),
                "明文冒充必须被移除；保留原值等于加密形同虚设");
    }

    @Test
    void plaintextForgeryIsRemovedEvenInLegacyCompatMode() {
        Request request = requestWith("is_admin", "1");
        new LegacyCompatCookies().decryptCookies(request);
        assertNull(request.cookie("is_admin"),
                "兼容模式下明文冒充同样必须被移除（红线，不得随兼容模式回退）");
    }

    @Test
    void randomBase64IsRemoved() {
        byte[] random = new byte[48];
        new java.security.SecureRandom().nextBytes(random);
        Request request = requestWith("theme", Base64.getEncoder().encodeToString(random));
        new TestCookies().decryptCookies(request);
        assertNull(request.cookie("theme"), "任意 Base64 不得被当成有效 Cookie");
    }

    @Test
    void validV2IsDecryptedInPlace() throws Exception {
        TestCookies cookies = new TestCookies();
        Request request = requestWith("theme", cookies.encrypt("dark"));
        cookies.decryptCookies(request);
        assertEquals("dark", request.cookie("theme"));
    }

    @Test
    void exceptedCookiePassesThroughUntouched() {
        ExceptedCookies cookies = new ExceptedCookies();
        Request request = requestWith("XSRF-TOKEN", "plain-csrf-value");
        cookies.decryptCookies(request);
        assertEquals("plain-csrf-value", request.cookie("XSRF-TOKEN"), "排除名单内的 Cookie 不参与加解密");

        Response response = ResponseBuilder.ok();
        response.addCookie(new Cookie("XSRF-TOKEN", "plain-csrf-value"));
        cookies.encryptCookies(response);
        assertEquals("plain-csrf-value", response.getCookies()[0].getValue());
    }

    // ==================== 出站：原地替换 + 失败置空 ====================

    @Test
    void outboundEncryptionReplacesValueInPlaceAsV2() {
        TestCookies cookies = new TestCookies();
        Response response = ResponseBuilder.ok();
        response.addCookie(new Cookie("theme", "dark"));
        cookies.encryptCookies(response);

        Cookie[] out = response.getCookies();
        assertEquals(1, out.length, "同一 Cookie 不得产生「明文 + 密文」两条 Set-Cookie");
        assertTrue(out[0].getValue().startsWith("v2:"), "出站一律 v2");
    }

    @Test
    void outboundEncryptionFailureWritesNothingRatherThanPlaintext() {
        Response response = ResponseBuilder.ok();
        response.addCookie(new Cookie("theme", "dark"));
        new BrokenEncryptCookies().encryptCookies(response);
        assertEquals("", response.getCookies()[0].getValue(),
                "加密失败必须置空（失败关闭），不得把明文发出去");
    }

    // ==================== 旧格式兼容（默认关闭 + 白名单 + 安全敏感名硬拒绝）====================

    @Test
    void legacyFormatIsRejectedByDefault() throws Exception {
        TestCookies cookies = new TestCookies();
        String legacy = legacyEncrypt(cookies, "dark".getBytes(StandardCharsets.UTF_8));
        Request request = requestWith("theme", legacy);
        cookies.decryptCookies(request);
        assertNull(request.cookie("theme"), "默认（allowLegacyFormat=false）必须拒绝旧格式");
    }

    @Test
    void legacyFormatIsReadWhenExplicitlyEnabledAndWhitelisted() throws Exception {
        LegacyCompatCookies cookies = new LegacyCompatCookies();
        String legacy = legacyEncrypt(cookies, "dark".getBytes(StandardCharsets.UTF_8));
        Request request = requestWith("theme", legacy);
        cookies.decryptCookies(request);
        assertEquals("dark", request.cookie("theme"), "显式开启 + 通配白名单后应能读取旧格式");
    }

    @Test
    void legacyReadStillWritesV2OnTheWayOut() throws Exception {
        LegacyCompatCookies cookies = new LegacyCompatCookies();
        String legacy = legacyEncrypt(cookies, "dark".getBytes(StandardCharsets.UTF_8));
        Response response = ResponseBuilder.ok();
        response.addCookie(new Cookie("theme", legacy));
        cookies.encryptCookies(response);
        assertTrue(response.getCookies()[0].getValue().startsWith("v2:"),
                "只读旧、写新：即使入站是旧格式，出站也必须写 v2");
    }

    @Test
    void whitelistRestrictsLegacyToNamedCookies() throws Exception {
        WhitelistedCookies cookies = new WhitelistedCookies();
        String legacy = legacyEncrypt(cookies, "dark".getBytes(StandardCharsets.UTF_8));

        Request allowed = requestWith("theme", legacy);
        cookies.decryptCookies(allowed);
        assertEquals("dark", allowed.cookie("theme"), "白名单内的名字可读旧格式");

        Request denied = requestWith("other", legacy);
        cookies.decryptCookies(denied);
        assertNull(denied.cookie("other"), "白名单外的名字仍必须拒绝旧格式");
    }

    @Test
    void securitySensitiveCookiesNeverUseLegacyEvenWithWildcard() throws Exception {
        LegacyCompatCookies cookies = new LegacyCompatCookies();
        String legacy = legacyEncrypt(cookies, "attacker-chosen".getBytes(StandardCharsets.UTF_8));
        for (String name : new String[]{"login_web_id", "laravel_session", "access_token",
                "XSRF-TOKEN", "JSESSIONID", "__Host-session"}) {
            Request request = requestWith(name, legacy);
            cookies.decryptCookies(request);
            assertNull(request.cookie(name),
                    "安全敏感 Cookie 即使通配白名单也不得走无 MAC 的旧格式: " + name);
        }
    }

    @Test
    void legacyWithInvalidUtf8IsRejected() throws Exception {
        LegacyCompatCookies cookies = new LegacyCompatCookies();
        String legacy = legacyEncrypt(cookies, new byte[]{(byte) 0xFF, (byte) 0xFE, (byte) 0x80});
        Request request = requestWith("theme", legacy);
        cookies.decryptCookies(request);
        assertNull(request.cookie("theme"), "非法 UTF-8 的旧密文必须丢弃");
    }

    @Test
    void legacyWithBadStructureIsRejected() {
        LegacyCompatCookies cookies = new LegacyCompatCookies();
        for (String bad : new String[]{"", "!!!not-base64!!!", "AAAA",
                Base64.getEncoder().encodeToString(new byte[16])}) {
            Request request = requestWith("theme", bad);
            cookies.decryptCookies(request);
            assertNull(request.cookie("theme"), "结构非法的旧格式必须丢弃: " + bad);
        }
    }

    @Test
    void legacyHelperRejectsNullValue() {
        assertNotNull(new LegacyCompatCookies());
        assertThrows(Exception.class, () -> new LegacyCompatCookies().decryptLegacy(null));
    }
}