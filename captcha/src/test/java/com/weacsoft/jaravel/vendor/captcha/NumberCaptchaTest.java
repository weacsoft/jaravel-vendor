package com.weacsoft.jaravel.vendor.captcha;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NumberCaptcha} 单元测试。
 * <p>
 * 注意：{@link AbstractCaptcha#verify} 通过 {@code store.pull} 一次性消费答案，
 * 因此验证前需先通过 {@code store.get}（不删除）取回答案。
 */
class NumberCaptchaTest {

    @Test
    void testGenerate() {
        NumberCaptcha captcha = new NumberCaptcha();
        CaptchaResult result = captcha.generate("num-gen-key");
        assertNotNull(result);
        assertEquals("number", result.getType());
        assertNotNull(result.getImageBase64());
        assertTrue(result.getImageBase64().startsWith("data:image/png;base64,"));
        assertNotNull(result.getToken());
    }

    @Test
    void testVerifyCorrect() {
        NumberCaptcha captcha = new NumberCaptcha();
        String key = "num-correct-key";
        captcha.generate(key);
        // 验证前先从 store 取回答案（get 不消费，verify 内部 pull 才消费）
        String answer = captcha.getStore().get(key);
        assertNotNull(answer);
        assertTrue(captcha.verify(key, answer));
    }

    @Test
    void testVerifyCaseInsensitive() {
        // 默认配置 caseSensitive=false，大小写不敏感
        NumberCaptcha captcha = new NumberCaptcha();
        String key = "num-case-key";
        // 直接写入已知大写答案，保证测试确定性
        captcha.getStore().put(key, "AB3D", 60);
        assertTrue(captcha.verify(key, "ab3d"));
    }

    @Test
    void testVerifyConsumed() {
        NumberCaptcha captcha = new NumberCaptcha();
        String key = "num-consumed-key";
        captcha.generate(key);
        String answer = captcha.getStore().get(key);
        // 第一次验证通过，答案被 pull 消费
        assertTrue(captcha.verify(key, answer));
        // 第二次验证因答案已消费而失败
        assertFalse(captcha.verify(key, answer));
    }
}
