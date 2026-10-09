package com.weacsoft.jaravel.vendor.captcha;

import com.weacsoft.jaravel.vendor.captcha.crypto.CaptchaCrypto;
import com.weacsoft.jaravel.vendor.captcha.generator.NumberCaptcha;
import com.weacsoft.jaravel.vendor.captcha.generator.RotateCaptcha;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证码安全回归：锁住三处曾经可被绕过的缺陷。
 * <ol>
 *   <li><b>encKey 不得能解密 captchaKey</b>：captchaKey 内含 answer，一旦下发的是
 *       「解密 token 的那把钥匙」，攻击者无需识别图片即可解出答案；
 *       对称模式下改为下发一次性输入密钥。</li>
 *   <li><b>±Infinity 不得绕过角度校验</b>：提交 {@code 1e999} 会让角度差变成 -Infinity，
 *       使所有阈值比较都为 false 而直接通过。</li>
 *   <li><b>明文伪造不得通过</b>：非密文输入在启用加密时不能当作用户输入被接受。</li>
 * </ol>
 */
class CaptchaSecurityTest {

    private static CaptchaProperties aesProps() {
        CaptchaProperties props = CaptchaProperties.createDefault();
        props.setEncryptionType("aes");
        props.setEncryptionKey("unit-test-secret-key");
        return props;
    }

    /**
     * 从 token 中取出答案（白盒助手：用服务端密钥解密，兼容 3 段/4 段两种格式）。
     */
    private static String answerOf(CaptchaProperties props, String captchaKey) {
        CaptchaCrypto server = CaptchaCrypto.create(
                props.getEncryptionType(), props.getEncryptionKey());
        String payload = server.decrypt(captchaKey);
        String[] parts = payload.split("\\|", 4);
        return parts.length == 4 ? parts[3] : parts[2];
    }

    @Test
    void aesModeDoesNotHandOutTokenKey() {
        CaptchaProperties props = aesProps();
        NumberCaptcha captcha = new NumberCaptcha(props);
        CaptchaResult result = captcha.generate();
        assertNotNull(result.getEncKey(), "仍应下发输入加密密钥供前端使用");

        // 关键：用下发的 encKey 去解 captchaKey 必须失败（否则答案泄露）
        CaptchaCrypto client = CaptchaCrypto.create("aes", result.getEncKey());
        assertNull(client.decrypt(result.getCaptchaKey()),
                "encKey 不得能解密 captchaKey（否则验证码可被直接绕过）");
    }

    @Test
    void aesModeStillVerifiesWithHandedOutInputKey() {
        CaptchaProperties props = aesProps();
        NumberCaptcha captcha = new NumberCaptcha(props);
        CaptchaResult result = captcha.generate();
        String answer = answerOf(props, result.getCaptchaKey());

        // 前端用 encKey 加密用户输入 → 服务端应能校验通过
        CaptchaCrypto client = CaptchaCrypto.create("aes", result.getEncKey());
        String encryptedInput = client.encrypt(answer);
        assertTrue(captcha.verify(result.getCaptchaKey(), encryptedInput),
                "一次性输入密钥通道必须仍可用");
    }

    @Test
    void rotateCaptchaRejectsInfiniteAngle() {
        CaptchaProperties props = CaptchaProperties.createDefault();
        props.setEncryptionType("none");
        RotateCaptcha captcha = new RotateCaptcha(props);
        CaptchaResult result = captcha.generate();
        // 1e999 解析为 +Infinity：修复前会让角度差成为 -Infinity 从而通过校验
        assertFalse(captcha.verify(result.getCaptchaKey(), "1e999"),
                "+Infinity 不得绕过旋转验证码角度校验");
        assertFalse(captcha.verify(result.getCaptchaKey(), "-1e999"),
                "-Infinity 不得绕过旋转验证码角度校验");
    }

    @Test
    void plaintextForgeryIsRejectedWhenEncryptionEnabled() {
        CaptchaProperties props = aesProps();
        NumberCaptcha captcha = new NumberCaptcha(props);
        CaptchaResult result = captcha.generate();
        String answer = answerOf(props, result.getCaptchaKey());
        // 直接提交明文答案（不加密）在启用加密时应当失败
        assertFalse(captcha.verify(result.getCaptchaKey(), answer),
                "启用加密后不得接受明文输入");
    }
}