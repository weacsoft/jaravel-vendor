package com.weacsoft.jaravel.vendor.captcha;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RotateCaptcha} 单元测试。
 */
class RotateCaptchaTest {

    @Test
    void testGenerate() {
        RotateCaptcha captcha = new RotateCaptcha();
        CaptchaResult result = captcha.generate("rot-gen-key");
        assertNotNull(result);
        assertEquals("rotate", result.getType());
        assertNotNull(result.getImageBase64());
        assertTrue(result.getImageBase64().startsWith("data:image/png;base64,"));
        assertNotNull(result.getExtra().get("size"));
    }

    @Test
    void testVerifyCorrect() {
        RotateCaptcha captcha = new RotateCaptcha();
        String key = "rot-correct-key";
        captcha.generate(key);
        // 从 store 取回答案角度（get 不消费）
        String answer = captcha.getStore().get(key);
        assertNotNull(answer);
        assertTrue(captcha.verify(key, answer));
    }

    @Test
    void testVerifyTolerance() {
        // 默认容差 tolerance=5.0（双向最短角差）
        RotateCaptcha captcha = new RotateCaptcha();
        String key = "rot-tol-key";
        captcha.generate(key);
        String answer = captcha.getStore().get(key);
        assertNotNull(answer);
        double angle = Double.parseDouble(answer);
        // 偏移 3 度，在容差范围内（做 360 取模以保持角度区间）
        double input = (angle + 3) % 360;
        assertTrue(captcha.verify(key, String.valueOf(input)));
    }
}
