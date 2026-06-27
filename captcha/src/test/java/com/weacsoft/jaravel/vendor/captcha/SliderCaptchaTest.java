package com.weacsoft.jaravel.vendor.captcha;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SliderCaptcha} 单元测试。
 */
class SliderCaptchaTest {

    @Test
    void testGenerate() {
        SliderCaptcha captcha = new SliderCaptcha();
        CaptchaResult result = captcha.generate("sli-gen-key");
        assertNotNull(result);
        assertEquals("slider", result.getType());
        // 背景图
        assertNotNull(result.getImageBase64());
        assertTrue(result.getImageBase64().startsWith("data:image/png;base64,"));
        // 滑块图
        Object sliderImage = result.getExtra().get("sliderImage");
        assertNotNull(sliderImage);
        assertTrue(sliderImage.toString().startsWith("data:image/png;base64,"));
        // 缺口纵坐标与滑块边长
        assertNotNull(result.getExtra().get("gapY"));
        assertNotNull(result.getExtra().get("blockSize"));
    }

    @Test
    void testVerifyCorrect() {
        SliderCaptcha captcha = new SliderCaptcha();
        String key = "sli-correct-key";
        captcha.generate(key);
        // 从 store 取回答案 gapX（get 不消费）
        String answer = captcha.getStore().get(key);
        assertNotNull(answer);
        assertTrue(captcha.verify(key, answer));
    }

    @Test
    void testVerifyTolerance() {
        // 默认容差 tolerance=5.0
        SliderCaptcha captcha = new SliderCaptcha();
        String key = "sli-tol-key";
        captcha.generate(key);
        String answer = captcha.getStore().get(key);
        assertNotNull(answer);
        double gapX = Double.parseDouble(answer);
        // 偏移 3 像素，在容差范围内
        assertTrue(captcha.verify(key, String.valueOf(gapX + 3)));
    }
}
