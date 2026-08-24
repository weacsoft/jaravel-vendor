package com.weacsoft.jaravel.vendor.wechat.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 微信消息加解密标准实现测试（自洽 roundtrip + 验签 + 篡改负例）。
 */
class WxBizMsgCryptTest {

    private static final String TOKEN = "TestToken123";
    private static final String APPID = "wx1234567890abcdef";
    private static final String AES_KEY = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG"; // 43 位

    private WxBizMsgCrypt crypt() {
        return new WxBizMsgCrypt(TOKEN, AES_KEY, APPID);
    }

    @Test
    void testSignIs32HexAndStable() {
        String s1 = crypt().sign("1407564400", "10001", "ENCRYPTED");
        String s2 = crypt().sign("1407564400", "10001", "ENCRYPTED");
        assertEquals(s1, s2, "签名应为确定性（同输入同签名）");
        assertEquals(40, s1.length(), "sha1 十六进制应为 40 位");
        // 规范：sha1(sort(token,timestamp,nonce,encrypt).join(""))
        java.util.List<String> parts = new java.util.ArrayList<>(
                java.util.List.of(TOKEN, "1407564400", "10001", "ENCRYPTED"));
        parts.sort(java.util.Comparator.naturalOrder());
        String expected = sha1Hex(String.join("", parts));
        assertEquals(expected, s1, "签名应为 sha1(sort(token,ts,nonce,encrypt))");
    }

    @Test
    void testVerifySignatureMatchesAndRejects() {
        WxBizMsgCrypt c = crypt();
        String sig = c.sign("ts", "nonce", "ciphertext");
        assertTrue(c.verifySignature("ts", "nonce", "ciphertext", sig), "正确签名应通过");
        assertTrue(c.verifySignature("ts", "nonce", "ciphertext", sig.toUpperCase()), "签名比较应忽略大小写");
        assertFalse(c.verifySignature("ts", "nonce", "ciphertext", "deadbeef"), "错误签名应拒绝");
        assertFalse(c.verifySignature("ts", "nonce", "OTHER", sig), "密文不一致应拒绝");
    }

    @Test
    void testEncryptDecryptRoundtrip() {
        WxBizMsgCrypt c = crypt();
        String msg = "<xml><Content><![CDATA[你好，微信]]></Content></xml>";
        String encrypted = c.encrypt(msg);
        assertNotNull(encrypted);
        String decrypted = c.decrypt(encrypted);
        assertEquals(msg, decrypted, "encrypt→decrypt 应还原原文（含中文 UTF-8）");
    }

    @Test
    void testEncryptDecryptUnicodeLength() {
        WxBizMsgCrypt c = crypt();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            sb.append('测').append('试').append('文');
        }
        String msg = sb.toString();
        assertEquals(c.decrypt(c.encrypt(msg)), msg, "长中文消息应能无损往返");
    }

    @Test
    void testDecryptRejectsWrongReceiveId() {
        // 用别的 appId 加密，用本 appId 解密 → receiveid 校验必须失败
        WxBizMsgCrypt other = new WxBizMsgCrypt(TOKEN, AES_KEY, "wx_other_appid");
        String encrypted = other.encrypt("payload");
        WechatCryptoException ex = assertThrows(WechatCryptoException.class,
                () -> crypt().decrypt(encrypted), "receiveid 不匹配应抛错");
        assertTrue(ex.getMessage().contains("receiveid"), "应提示 receiveid 不匹配");
    }

    @Test
    void testDecryptRejectsTamperedCiphertext() {
        WxBizMsgCrypt c = crypt();
        String encrypted = c.encrypt("hello");
        byte[] raw = java.util.Base64.getDecoder().decode(encrypted);
        raw[raw.length / 2] ^= 0x01; // 篡改一个 bit：破坏密文或长度声明
        String corrupted = java.util.Base64.getEncoder().encodeToString(raw);
        assertThrows(WechatCryptoException.class, () -> c.decrypt(corrupted), "篡改密文应解密失败或 receiveid 校验失败");
        // AES-ECB 对未填充区篡改可能仍解出乱码，但 receiveid 校验必须拒绝
        final boolean[] rejected = {false};
        try {
            String out = c.decrypt(corrupted);
            if (out == null || !out.contains("hello")) {
                rejected[0] = true;
            }
        } catch (WechatCryptoException e) {
            rejected[0] = true;
        }
        assertTrue(rejected[0], "篡改后的密文不得还原出原文");
    }

    @Test
    void testCryptDisabledWithoutAesKey() {
        WxBizMsgCrypt c = new WxBizMsgCrypt(TOKEN, null, APPID);
        assertFalse(c.isCryptEnabled(), "无 EncodingAESKey 时应无加解密能力");
        assertThrows(WechatCryptoException.class, () -> c.encrypt("x"), "无密钥时 encrypt 应报错");
        assertThrows(WechatCryptoException.class, () -> c.decrypt("x"), "无密钥时 decrypt 应报错");
    }

    @Test
    void testAesKeyLengthValidation() {
        assertThrows(WechatCryptoException.class,
                () -> new WxBizMsgCrypt(TOKEN, "toolong4444444444444444444444444", APPID),
                "EncodingAESKey 长度必须 43");
        assertThrows(WechatCryptoException.class,
                () -> new WxBizMsgCrypt(TOKEN, "!!not-base64!!", APPID),
                "非法 Base64 的 EncodingAESKey 应被拒绝");
    }

    @Test
    void testTokenAndAppIdRequired() {
        assertThrows(WechatCryptoException.class, () -> new WxBizMsgCrypt("", AES_KEY, APPID));
        assertThrows(WechatCryptoException.class, () -> new WxBizMsgCrypt(TOKEN, AES_KEY, ""));
    }

    // ---- helpers ----

    private static String sha1Hex(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] h = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
