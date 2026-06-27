package com.weacsoft.jaravel.vendor.captcha;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RotateCaptcha} 单元测试，覆盖轨迹验证启用 / 禁用两种模式。
 * <p>
 * 默认配置 {@link CaptchaProperties#isTrajectoryEnabled()} 为 true，
 * 因此除"禁用轨迹"用例外，均需提交包含 trajectory 的 JSON。
 */
class RotateCaptchaTest {

    /**
     * 构造一份合法的"人类旋转"轨迹 JSON：value 为最终旋转角度，
     * trajectory 为 11 个采样点，总时长 1000ms，使用余弦缓动（ease-in-out）
     * 模拟"加速 → 接近匀速 → 减速"的真实旋转过程。
     * <p>
     * 余弦缓动保证相邻点跳变随 finalValue 线性缩放：在 finalValue ≤ 360 时
     * 单步跳变不超过约 56（默认 maxJumpDistance=80），且速度方差与加速度方向
     * 多样性均满足 {@link TrajectoryValidator} 校验，从而对任意角度（0~359）均稳定合法。
     */
    private String buildHumanTrajectoryJson(int finalValue) {
        int n = 11;
        StringBuilder sb = new StringBuilder();
        sb.append("{\"value\":").append(finalValue).append(",\"trajectory\":[");
        for (int i = 0; i < n; i++) {
            double s = (double) i / (n - 1);                  // 0..1
            double eased = 0.5 * (1 - Math.cos(Math.PI * s)); // S 曲线 0..1
            long t = Math.round(s * 1000.0);
            long v = Math.round(finalValue * eased);
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{\"t\":").append(t).append(",\"v\":").append(v).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

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
    void testVerifyWithTrajectory() {
        // 正确角度 + 合法人类轨迹 → 验证通过
        RotateCaptcha captcha = new RotateCaptcha(); // 默认启用轨迹验证
        String key = "rot-with-traj-key";
        captcha.generate(key);
        // store.get 不消费答案，verify 内部用 pull 消费
        String answer = captcha.getStore().get(key);
        assertNotNull(answer);
        int angle = Integer.parseInt(answer.trim());
        assertTrue(captcha.verify(key, buildHumanTrajectoryJson(angle)));
    }

    @Test
    void testVerifyWithoutTrajectory() {
        // 启用轨迹验证时，只提交数字（无 trajectory）应失败
        RotateCaptcha captcha = new RotateCaptcha();
        String key = "rot-no-traj-key";
        captcha.generate(key);
        String answer = captcha.getStore().get(key);
        assertNotNull(answer);
        // 即便角度正确，缺少轨迹也无法通过
        assertFalse(captcha.verify(key, answer));
    }

    @Test
    void testVerifyTrajectoryDisabled() {
        // 禁用轨迹验证时，只提交数字应通过
        CaptchaProperties props = CaptchaProperties.createDefault();
        props.setTrajectoryEnabled(false);
        RotateCaptcha captcha = new RotateCaptcha(new MemoryCaptchaStore(), props);
        String key = "rot-disabled-key";
        captcha.generate(key);
        String answer = captcha.getStore().get(key);
        assertNotNull(answer);
        assertTrue(captcha.verify(key, answer));
    }

    @Test
    void testVerifyWrongAngle() {
        // 角度不在容差范围内应失败
        RotateCaptcha captcha = new RotateCaptcha();
        String key = "rot-wrong-angle-key";
        captcha.generate(key);
        String answer = captcha.getStore().get(key);
        assertNotNull(answer);
        int angle = Integer.parseInt(answer.trim());
        int wrongAngle = (angle + 90) % 360; // 明显超出容差（默认 5）
        assertFalse(captcha.verify(key, buildHumanTrajectoryJson(wrongAngle)));
    }
}
