package com.weacsoft.jaravel.vendor.captcha;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NumberCaptcha} 单元测试。
 * <p>
 * 无状态模式下答案加密编码在 captchaKey 中，测试通过 {@link #extractAnswer} 解密提取答案。
 */
class NumberCaptchaTest {

    /**
     * 从无状态 captchaKey 中解密提取答案（仅供测试使用）。
     *
     * @param captcha    验证码实例
     * @param captchaKey 生成时返回的 captchaKey
     * @return 答案字符串
     */
    private String extractAnswer(AbstractCaptcha captcha, String captchaKey) {
        CaptchaProperties props = captcha.getProperties();
        CaptchaCrypto crypto = CaptchaCrypto.create(
                props.getEncryptionType(), props.getEncryptionKey());
        String payload = crypto.decrypt(captchaKey);
        String[] parts = payload.split("\\|", 3);
        return parts[2];
    }

    @Test
    void testGenerate() {
        NumberCaptcha captcha = new NumberCaptcha();
        CaptchaResult result = captcha.generate();
        assertNotNull(result);
        assertEquals("number", result.getType());
        assertNotNull(result.getImageBase64());
        assertTrue(result.getImageBase64().startsWith("data:image/png;base64,"));
        assertNotNull(result.getCaptchaKey());
    }

    @Test
    void testVerifyCorrect() {
        // 无状态模式：使用 none 加密以便明文输入可直接验证
        CaptchaProperties props = CaptchaProperties.createDefault();
        props.setEncryptionType("none");
        NumberCaptcha captcha = new NumberCaptcha(props);
        CaptchaResult result = captcha.generate();
        String captchaKey = result.getCaptchaKey();
        // 从 captchaKey 解密提取答案
        String answer = extractAnswer(captcha, captchaKey);
        assertNotNull(answer);
        assertTrue(captcha.verify(captchaKey, answer));
    }

    @Test
    void testVerifyCaseInsensitive() {
        // 默认配置 caseSensitive=false，大小写不敏感
        // 设置纯字母字符集，保证答案含字母可用于大小写测试
        CaptchaProperties props = CaptchaProperties.createDefault();
        props.setEncryptionType("none");
        props.setCharSet("ABCD");
        NumberCaptcha captcha = new NumberCaptcha(props);
        CaptchaResult result = captcha.generate();
        String captchaKey = result.getCaptchaKey();
        String answer = extractAnswer(captcha, captchaKey);
        assertNotNull(answer);
        // 提交小写版本应通过（大小写不敏感）
        assertTrue(captcha.verify(captchaKey, answer.toLowerCase()));
    }

    @Test
    void testVerifyReusable() {
        // 无状态模式下 captchaKey 可重复验证（无消费机制）
        CaptchaProperties props = CaptchaProperties.createDefault();
        props.setEncryptionType("none");
        NumberCaptcha captcha = new NumberCaptcha(props);
        CaptchaResult result = captcha.generate();
        String captchaKey = result.getCaptchaKey();
        String answer = extractAnswer(captcha, captchaKey);
        // 第一次验证通过
        assertTrue(captcha.verify(captchaKey, answer));
        // 无状态模式下可重复验证
        assertTrue(captcha.verify(captchaKey, answer));
    }
}
