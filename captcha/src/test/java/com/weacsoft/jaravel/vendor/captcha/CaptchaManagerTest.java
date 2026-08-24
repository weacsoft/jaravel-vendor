package com.weacsoft.jaravel.vendor.captcha;

import com.weacsoft.jaravel.vendor.captcha.generator.NumberCaptcha;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CaptchaManager} 单元测试。
 */
class CaptchaManagerTest {

    @Test
    void testCreateDefault() {
        CaptchaManager manager = CaptchaManager.createDefault();
        Set<String> types = manager.getTypes();
        assertEquals(5, types.size());
        assertTrue(types.contains("number"));
        assertTrue(types.contains("arithmetic"));
        assertTrue(types.contains("slider"));
        assertTrue(types.contains("rotate"));
    }

    @Test
    void testGenerateNumber() {
        CaptchaManager manager = CaptchaManager.createDefault();
        CaptchaResult result = manager.generate("number");
        assertNotNull(result);
        assertEquals("number", result.getType());
        assertNotNull(result.getCaptchaKey());
        assertNotNull(result.getImageBase64());
        assertTrue(result.getImageBase64().startsWith("data:image/png;base64,"));
    }

    @Test
    void testGenerateArithmetic() {
        CaptchaManager manager = CaptchaManager.createDefault();
        CaptchaResult result = manager.generate("arithmetic");
        assertNotNull(result);
        assertEquals("arithmetic", result.getType());
        assertNotNull(result.getImageBase64());
        assertTrue(result.getImageBase64().startsWith("data:image/png;base64,"));
        assertNotNull(result.getCaptchaKey());
    }

    @Test
    void testGenerateSlider() {
        CaptchaManager manager = CaptchaManager.createDefault();
        CaptchaResult result = manager.generate("slider");
        assertNotNull(result);
        assertEquals("slider", result.getType());
        assertNotNull(result.getImageBase64());
        assertTrue(result.getImageBase64().startsWith("data:image/png;base64,"));
        // 背景图 + 滑块图
        Object sliderImage = result.getExtra().get("sliderImage");
        assertNotNull(sliderImage);
        assertTrue(sliderImage.toString().startsWith("data:image/png;base64,"));
        // 缺口纵坐标
        assertNotNull(result.getExtra().get("gapY"));
        // 滑块边长
        assertNotNull(result.getExtra().get("blockSize"));
    }

    @Test
    void testGenerateRotate() {
        CaptchaManager manager = CaptchaManager.createDefault();
        CaptchaResult result = manager.generate("rotate");
        assertNotNull(result);
        assertEquals("rotate", result.getType());
        assertNotNull(result.getImageBase64());
        assertTrue(result.getImageBase64().startsWith("data:image/png;base64,"));
        assertNotNull(result.getExtra().get("size"));
    }

    @Test
    void testVerifyWrongAnswer() {
        CaptchaManager manager = CaptchaManager.createDefault();
        CaptchaResult result = manager.generate("number");
        assertFalse(manager.verify(result.getKey(), "definitely-wrong-answer"));
    }

    @Test
    void testMergedKeyFormat() {
        CaptchaManager manager = CaptchaManager.createDefault();
        CaptchaResult result = manager.generate("number");
        // 合并凭证 = type + "." + captchaKey，前端只需持有它一个值
        assertEquals("number." + result.getCaptchaKey(), result.getKey());
        assertEquals("number." + result.getCaptchaKey(), result.toMap().get("key"));
    }

    @Test
    void testVerifyRejectsMalformedKey() {
        CaptchaManager manager = CaptchaManager.createDefault();
        // 无分隔符 / 空类型 / 空 captchaKey 一律判失败，不抛异常
        assertFalse(manager.verify(null, "input"));
        assertFalse(manager.verify("nodot", "input"));
        assertFalse(manager.verify(".onlykey", "input"));
        assertFalse(manager.verify("number.", "input"));
    }

    @Test
    void testUnregisterMakesTypePluggable() {
        CaptchaManager manager = CaptchaManager.createDefault();
        assertTrue(manager.getTypes().contains("click"));

        // 运行时注销 → 该类型不再可用（生成抛异常、校验返回 false）
        assertNotNull(manager.unregister("click"));
        assertFalse(manager.getTypes().contains("click"));
        assertThrows(IllegalArgumentException.class, () -> manager.generate("click"));
        assertFalse(manager.verify("click.whatever", "input"));

        // 重新注册 → 恢复可用，其它类型全程不受影响
        manager.register(new com.weacsoft.jaravel.vendor.captcha.generator.ClickCaptcha(
                manager.getProperties()));
        assertTrue(manager.getTypes().contains("click"));
        assertNotNull(manager.generate("click"));
        assertNotNull(manager.generate("number"));
    }

    @Test
    void testVerifyAfterExpire() throws InterruptedException {
        // 设置很短的过期时间（1 秒）
        CaptchaProperties props = new CaptchaProperties();
        props.setExpireSeconds(1);
        CaptchaManager manager = new CaptchaManager(props);
        manager.register(new NumberCaptcha(props));

        CaptchaResult result = manager.generate("number");
        // 等待超过过期时间
        Thread.sleep(1100);
        assertFalse(manager.verify(result.getKey(), "anything"));
    }

    @Test
    void testUnknownType() {
        CaptchaManager manager = CaptchaManager.createDefault();
        // 未知类型生成抛 IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> manager.generate("unknown"));
        // 未知类型验证返回 false（不抛异常）
        assertFalse(manager.verify("unknown.key", "input"));
    }
}
