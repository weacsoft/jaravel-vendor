package com.weacsoft.jaravel.vendor.captcha;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 水印与自定义背景图功能测试，覆盖四种验证码的文字水印、图片水印、
 * 水印位置 / 旋转、自定义背景图（base64）以及无水印无背景图的回归场景。
 * <p>
 * 文字水印与图片水印由 {@link AbstractCaptcha#applyWatermark} 统一叠加，
 * 四种验证码（数字、算术、滑动、旋转）均在内容绘制完成后调用该方法。
 * 自定义背景图由 {@link AbstractCaptcha#loadBackgroundImage} 加载，
 * 仅滑动（{@link SliderCaptcha}）与旋转（{@link RotateCaptcha}）验证码使用。
 */
class WatermarkAndBackgroundTest {

    // ==================== 文字水印 ====================

    @Test
    void testTextWatermarkOnNumber() {
        CaptchaProperties props = CaptchaProperties.createDefault();
        props.setWatermarkText("MyWatermark");
        NumberCaptcha captcha = new NumberCaptcha(new MemoryCaptchaStore(), props);

        CaptchaResult result = captcha.generate();
        assertNotNull(result);
        assertValidPng(result.getImageBase64());
    }

    @Test
    void testTextWatermarkOnSlider() {
        CaptchaProperties props = CaptchaProperties.createDefault();
        props.setWatermarkText("MyWatermark");
        SliderCaptcha captcha = new SliderCaptcha(new MemoryCaptchaStore(), props);

        CaptchaResult result = captcha.generate();
        assertNotNull(result);
        assertValidPng(result.getImageBase64());
    }

    @Test
    void testTextWatermarkOnRotate() {
        CaptchaProperties props = CaptchaProperties.createDefault();
        props.setWatermarkText("MyWatermark");
        RotateCaptcha captcha = new RotateCaptcha(new MemoryCaptchaStore(), props);

        CaptchaResult result = captcha.generate();
        assertNotNull(result);
        assertValidPng(result.getImageBase64());
    }

    @Test
    void testTextWatermarkOnArithmetic() {
        CaptchaProperties props = CaptchaProperties.createDefault();
        props.setWatermarkText("MyWatermark");
        ArithmeticCaptcha captcha = new ArithmeticCaptcha(new MemoryCaptchaStore(), props);

        CaptchaResult result = captcha.generate();
        assertNotNull(result);
        assertValidPng(result.getImageBase64());
    }

    // ==================== 水印位置与旋转 ====================

    @Test
    void testWatermarkPosition() {
        String[] positions = {"top-left", "center", "bottom-right"};
        for (String pos : positions) {
            CaptchaProperties props = CaptchaProperties.createDefault();
            props.setWatermarkText("MyWatermark");
            props.setWatermarkPosition(pos);
            NumberCaptcha captcha = new NumberCaptcha(new MemoryCaptchaStore(), props);

            CaptchaResult result = captcha.generate();
            assertNotNull(result);
            assertValidPng(result.getImageBase64());
        }
    }

    @Test
    void testWatermarkRotation() {
        CaptchaProperties props = CaptchaProperties.createDefault();
        props.setWatermarkText("MyWatermark");
        props.setWatermarkRotation(45);
        NumberCaptcha captcha = new NumberCaptcha(new MemoryCaptchaStore(), props);

        CaptchaResult result = captcha.generate();
        assertNotNull(result);
        assertValidPng(result.getImageBase64());
    }

    // ==================== 自定义背景图 ====================

    @Test
    void testBackgroundImageBase64() {
        CaptchaProperties props = CaptchaProperties.createDefault();
        props.setBackgroundImageBase64(createTestImageBase64(props.getWidth(), props.getHeight()));
        SliderCaptcha captcha = new SliderCaptcha(new MemoryCaptchaStore(), props);

        CaptchaResult result = captcha.generate();
        assertNotNull(result);
        assertNotNull(result.getImageBase64());
        assertTrue(result.getImageBase64().startsWith("data:image/png;base64,"));
    }

    @Test
    void testBackgroundImageBase64OnRotate() {
        CaptchaProperties props = CaptchaProperties.createDefault();
        int size = Math.max(props.getWidth(), 200);
        props.setBackgroundImageBase64(createTestImageBase64(size, size));
        RotateCaptcha captcha = new RotateCaptcha(new MemoryCaptchaStore(), props);

        CaptchaResult result = captcha.generate();
        assertNotNull(result);
        assertNotNull(result.getImageBase64());
        assertTrue(result.getImageBase64().startsWith("data:image/png;base64,"));
    }

    // ==================== 回归测试 ====================

    @Test
    void testNoWatermarkNoError() {
        CaptchaProperties props = CaptchaProperties.createDefault();
        // 不设置任何水印与背景图，确保正常生成
        NumberCaptcha captcha = new NumberCaptcha(new MemoryCaptchaStore(), props);

        CaptchaResult result = captcha.generate();
        assertNotNull(result);
        assertValidPng(result.getImageBase64());
    }

    @Test
    void testImageWatermark() {
        CaptchaProperties props = CaptchaProperties.createDefault();
        props.setWatermarkImageBase64(createTestImageBase64(60, 30));
        NumberCaptcha captcha = new NumberCaptcha(new MemoryCaptchaStore(), props);

        CaptchaResult result = captcha.generate();
        assertNotNull(result);
        assertValidPng(result.getImageBase64());
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造一张纯色 PNG 的 base64 字符串（带 data URI 前缀），用于背景图与图片水印测试。
     *
     * @param w 宽
     * @param h 高
     * @return 形如 {@code data:image/png;base64,...} 的字符串
     */
    private String createTestImageBase64(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 断言给定的 base64 图片字符串是有效的 PNG：非空、带正确前缀，且解码后可读为有效图片。
     *
     * @param base64Image base64 图片字符串
     */
    private void assertValidPng(String base64Image) {
        assertNotNull(base64Image);
        assertTrue(base64Image.startsWith("data:image/png;base64,"));
        // 解码并验证是有效图片
        String data = base64Image.substring(base64Image.indexOf(",") + 1);
        byte[] bytes = Base64.getDecoder().decode(data);
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            assertNotNull(img);
            assertTrue(img.getWidth() > 0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
