package com.weacsoft.jaravel.vendor.captcha;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ArithmeticCaptcha} 单元测试。
 * <p>
 * 无状态模式下答案加密编码在 captchaKey 中，测试通过 {@link #extractAnswer} 解密提取答案。
 */
class ArithmeticCaptchaTest {

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
        ArithmeticCaptcha captcha = new ArithmeticCaptcha();
        CaptchaResult result = captcha.generate();
        assertNotNull(result);
        assertEquals("arithmetic", result.getType());
        assertNotNull(result.getImageBase64());
        assertTrue(result.getImageBase64().startsWith("data:image/png;base64,"));
        assertNotNull(result.getCaptchaKey());
    }

    @Test
    void testVerifyCorrect() {
        // 无状态模式：使用 none 加密以便明文输入可直接验证
        CaptchaProperties props = CaptchaProperties.createDefault();
        props.setEncryptionType("none");
        ArithmeticCaptcha captcha = new ArithmeticCaptcha(props);
        CaptchaResult result = captcha.generate();
        String captchaKey = result.getCaptchaKey();
        // 从 captchaKey 解密提取答案
        String answer = extractAnswer(captcha, captchaKey);
        assertNotNull(answer);
        assertTrue(captcha.verify(captchaKey, answer));
    }
}
