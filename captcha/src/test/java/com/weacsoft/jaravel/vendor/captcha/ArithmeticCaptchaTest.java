package com.weacsoft.jaravel.vendor.captcha;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ArithmeticCaptcha} 单元测试。
 */
class ArithmeticCaptchaTest {

    @Test
    void testGenerate() {
        ArithmeticCaptcha captcha = new ArithmeticCaptcha();
        CaptchaResult result = captcha.generate("ari-gen-key");
        assertNotNull(result);
        assertEquals("arithmetic", result.getType());
        assertNotNull(result.getImageBase64());
        assertTrue(result.getImageBase64().startsWith("data:image/png;base64,"));
        assertNotNull(result.getToken());
    }

    @Test
    void testVerifyCorrect() {
        ArithmeticCaptcha captcha = new ArithmeticCaptcha();
        String key = "ari-correct-key";
        captcha.generate(key);
        // 从 store 取回答案（get 不消费）
        String answer = captcha.getStore().get(key);
        assertNotNull(answer);
        assertTrue(captcha.verify(key, answer));
    }
}
