package com.weacsoft.jaravel.vendor.captcha;

import com.weacsoft.jaravel.vendor.captcha.crypto.CaptchaCrypto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证码「参数顺序写反」的语义护栏（审计 M17）。
 * <p>
 * 旧 README 的 {@code verify(type, key, input)} 能编译但错绑到
 * {@code verify(key, userInput, encryptionKey)}。护栏必须<b>区分两条路径</b>：
 * <ul>
 *   <li><b>3/4 参重载</b>（额外参数非空）出现「首参是类型名」= 调用方写反 → 抛
 *       {@code IllegalArgumentException}（程序员错误应当大声）；</li>
 *   <li><b>2 参公开路径</b>的 key 可能来自不可信输入（攻击者伪造 {@code captchaKey=number}）
 *       → <b>绝不抛异常</b>（否则未捕获即 HTTP 500），只记一次性 ERROR 并按失败处理。
 *       这一条是终局评审复现出的缺陷，必须由本用例钉死。</li>
 * </ul>
 */
class CaptchaVerifyMisuseGuardTest {

    private static CaptchaManager manager() {
        // createDefault() 已注册内置类型（number/arithmetic/slider/rotate/click…）
        return CaptchaManager.createDefault();
    }

    @Test
    void twoArgPathWithBareTypeNameMustNotThrow() {
        CaptchaManager manager = manager();

        // 攻击者可控输入：不得抛异常（否则 500），必须是「校验失败」
        assertDoesNotThrow(() -> {
            boolean result = manager.verify("number", "1234");
            assertFalse(result, "裸类型名不是有效凭证，必须判失败");
        });
    }

    @Test
    void threeArgMisuseThrowsActionableError() {
        CaptchaManager manager = manager();
        CaptchaProperties props = CaptchaProperties.createDefault();

        // 这正是旧 README 的 verify(type, key, input) 错序：第三个参数落到 encryptionKey
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> manager.verifyDetailed("number", "number.abc", props, "userInputAsKey"));
        assertTrue(error.getMessage().contains("参数顺序"), "错误信息必须可操作: " + error.getMessage());
    }

    @Test
    void malformedKeysFailWithoutThrowing() {
        CaptchaManager manager = manager();
        CaptchaProperties props = CaptchaProperties.createDefault();

        for (String malformed : new String[]{"number.", ".abc", "unknown-type"}) {
            assertDoesNotThrow(() -> {
                VerifyResult result = manager.verifyDetailed(malformed, "x", null, null);
                assertFalse(result.isPassed(), malformed + " 不应通过");
            }, "畸形 key 只应失败，不应抛异常: " + malformed);
        }
    }

    @Test
    void normalTwoArgVerificationStillWorks() {
        CaptchaManager manager = manager();
        CaptchaResult result = manager.generate("number");

        // createDefault() 默认 AES：payload = expireTime|nonce|inputKey|answer，
        // 前端用下发的 encKey（一次性输入密钥）加密用户输入，服务端再用同一密钥解密
        String payload = CaptchaCrypto.create("aes",
                CaptchaProperties.DEFAULT_ENCRYPTION_KEY).decrypt(result.getCaptchaKey());
        String[] parts = payload.split("\\|", 4);
        String encryptedInput = CaptchaCrypto.create("aes", parts[2]).encrypt(parts[3]);

        assertTrue(manager.verify(result.getKey(), encryptedInput), "正常两参校验必须可用（勿误伤）");
    }
}