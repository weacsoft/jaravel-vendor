package com.weacsoft.jaravel.vendor.springboot.captcha;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证码密钥形态校验的启动期行为（审计 N1 / M4）。
 * <p>
 * 校验打在**解析后的有效配置**上（已经过 {@code jaravel.key} 兜底），分两档处理：
 * 默认只 ERROR 不阻断启动（避免库升级即 brick 既有部署）；显式开启 fail-fast 时中止启动。
 * <p>
 * 关键判定（专家共识）：
 * <ul>
 *   <li><b>RSA 必须 `公钥|私钥`</b>：验证码校验需要服务端用私钥解密用户输入，
 *       只给公钥会让所有验证永远失败；只给私钥则旧实现按公钥解析 → 每请求 500；</li>
 *   <li><b>AES 仍为出厂默认密钥（源码公开常量）或空</b> → 等价于没有密钥，token 可被离线解开。</li>
 * </ul>
 */
class CaptchaKeyShapeValidationTest {

    private static final String DEFAULT_KEY = "jaravel-captcha-default-key";
    private static final String STRONG_KEY = "unit-test-strong-key-32-bytes-min";

    private static com.weacsoft.jaravel.vendor.captcha.CaptchaProperties core(String type, String key) {
        com.weacsoft.jaravel.vendor.captcha.CaptchaProperties props =
                com.weacsoft.jaravel.vendor.captcha.CaptchaProperties.createDefault();
        props.setEncryptionType(type);
        props.setEncryptionKey(key);
        return props;
    }

    private static CaptchaProperties spring(boolean failFast) {
        CaptchaProperties props = new CaptchaProperties();
        props.setFailFastOnInvalidKey(failFast);
        return props;
    }

    @Test
    void rsaWithOnlyPublicKeyIsReported() {
        // 只给公钥（无 '|'）→ 形态不可用；默认不阻断启动
        assertDoesNotThrow(() -> CaptchaAutoConfiguration.validateEncryptionShape(
                core("rsa", "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A"), spring(false)),
                "默认档只 ERROR，不得阻断启动");
        // 开启 fail-fast → 中止启动
        assertThrows(IllegalStateException.class, () -> CaptchaAutoConfiguration.validateEncryptionShape(
                core("rsa", "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A"), spring(true)),
                "fail-fast 开启时形态不可用必须中止启动");
    }

    @Test
    void rsaWithOnlyPrivateKeyIsReported() {
        // 只给私钥：旧实现按公钥解析 → 生成时抛异常（每请求 500）
        assertThrows(IllegalStateException.class, () -> CaptchaAutoConfiguration.validateEncryptionShape(
                core("rsa", "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQ"), spring(true)));
    }

    @Test
    void rsaWithPublicAndPrivateIsAccepted() {
        String pair = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A|MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQ";
        assertDoesNotThrow(() -> CaptchaAutoConfiguration.validateEncryptionShape(
                core("rsa", pair), spring(true)),
                "公钥|私钥 形态应通过校验");
    }

    @Test
    void rsaWithEmptyPrivatePartIsReported() {
        // "公钥|" —— 私钥段为空同样不可用
        assertThrows(IllegalStateException.class, () -> CaptchaAutoConfiguration.validateEncryptionShape(
                core("rsa", "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A|"), spring(true)));
    }

    @Test
    void aesWithFactoryDefaultKeyIsReported() {
        assertThrows(IllegalStateException.class, () -> CaptchaAutoConfiguration.validateEncryptionShape(
                core("aes", DEFAULT_KEY), spring(true)),
                "仍在使用源码中的公开默认密钥必须被视为不可用");
        assertDoesNotThrow(() -> CaptchaAutoConfiguration.validateEncryptionShape(
                core("aes", DEFAULT_KEY), spring(false)));
    }

    @Test
    void aesWithStrongKeyIsSilent() {
        assertDoesNotThrow(() -> CaptchaAutoConfiguration.validateEncryptionShape(
                core("aes", STRONG_KEY), spring(true)),
                "正常配置不得报错（fail-fast 也不应触发）");
    }

    @Test
    void noneModeSkipsValidation() {
        assertDoesNotThrow(() -> CaptchaAutoConfiguration.validateEncryptionShape(
                core("none", null), spring(true)),
                "none 模式由核心层单独 WARN，这里不重复阻断");
    }
}