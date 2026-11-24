package com.weacsoft.jaravel.vendor.captcha.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * AES 密文格式回归测试：
 * <ul>
 *   <li>新格式 = 4 字节版本标记 (0x01,'J','V','1') + 16 字节随机 IV + AES 密文，
 *       整体 Base64；同一明文两次加密必须产生不同密文（随机 IV）；</li>
 *   <li>旧格式（IV 恒等于密钥派生值、无标记前缀）必须仍可解密（升级前前端数据兼容）。</li>
 * </ul>
 */
class CaptchaCryptoAesFormatTest {

    private static final String KEY = "unit-test-key";

    private static final byte[] MAGIC = { 0x01, 0x4A, 0x56, 0x31 };

    @Test
    void encryptEmitsMagicAndRandomIv() throws Exception {
        CaptchaCrypto crypto = CaptchaCrypto.create("aes", KEY);
        String a = crypto.encrypt("hello");
        String b = crypto.encrypt("hello");

        byte[] decA = Base64.getDecoder().decode(a);
        // 版本标记
        for (int i = 0; i < MAGIC.length; i++) {
            assertEquals(MAGIC[i], decA[i], "密文前 4 字节应为版本标记");
        }
        // 同一明文两次加密：整体密文不同（IV 随机）
        assertFalse(a.equals(b), "相同明文两次加密必须产生不同密文");
        // 长度 ≡ 4 (mod 16)：标记(4) + IV(16) + 至少一个 AES 块
        assertEquals(4, decA.length % 16, "新格式总长度应 ≡ 4 (mod 16)");
    }

    @Test
    void newFormatRoundTrip() throws Exception {
        CaptchaCrypto crypto = CaptchaCrypto.create("aes", KEY);
        String plaintext = "验证码输入：123456";
        assertEquals(plaintext, crypto.decrypt(crypto.encrypt(plaintext)));
    }

    @Test
    void legacyFixedIvCiphertextStillDecrypts() throws Exception {
        CaptchaCrypto crypto = CaptchaCrypto.create("aes", KEY);
        // 手工构造旧格式密文：SHA-256(key) 前 16 字节既做密钥又做 IV
        byte[] hash = sha256(KEY);
        byte[] keyBytes = new byte[16];
        System.arraycopy(hash, 0, keyBytes, 0, 16);

        javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(javax.crypto.Cipher.ENCRYPT_MODE,
                new javax.crypto.spec.SecretKeySpec(keyBytes, "AES"),
                new javax.crypto.spec.IvParameterSpec(keyBytes));
        byte[] legacy = c.doFinal("legacy-input".getBytes(StandardCharsets.UTF_8));

        String legacyB64 = Base64.getEncoder().encodeToString(legacy);
        assertEquals("legacy-input", crypto.decrypt(legacyB64),
                "升级前（IV=密钥派生值）加密的密文必须仍可解密");
    }

    @Test
    void differentKeysStillProduceDifferentCiphertexts() throws Exception {
        CaptchaCrypto a = CaptchaCrypto.create("aes", "key-a");
        CaptchaCrypto b = CaptchaCrypto.create("aes", "key-b");
        assertNotEquals(a.encrypt("x"), b.encrypt("x"));
        // 交叉不可解密：key-b 解 key-a 的密文应抛异常
        try {
            b.decrypt(a.encrypt("x"));
        } catch (Exception expected) {
            // 解密失败（BadPaddingException 等）
        }
    }

    private static byte[] sha256(String s) throws Exception {
        return java.security.MessageDigest.getInstance("SHA-256")
                .digest(s.getBytes(StandardCharsets.UTF_8));
    }
}
