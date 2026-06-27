package com.weacsoft.jaravel.vendor.captcha;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TrajectoryValidator} 单元测试，独立验证轨迹解析与各维度校验逻辑。
 * <p>
 * 默认配置（{@link CaptchaProperties#createDefault()}）：
 * minTrajectoryPoints=5、minTrajectoryDurationMs=500、maxJumpDistance=80。
 */
class TrajectoryValidatorTest {

    private final CaptchaProperties props = CaptchaProperties.createDefault();

    @Test
    void testParsePoints() {
        // 注意：parsePoints 对 v 字段按整数解析（extractNumber 返回 long），
        // 滑动 / 旋转轨迹均为整数坐标 / 角度，故此处使用整数 v 验证解析。
        String json = "[{\"t\":0,\"v\":0},{\"t\":50,\"v\":5},{\"t\":100,\"v\":10}]";
        List<TrajectoryValidator.Point> points = TrajectoryValidator.parsePoints(json);
        assertEquals(3, points.size());
        assertEquals(0, points.get(0).t);
        assertEquals(0.0, points.get(0).v, 0.0001);
        assertEquals(50, points.get(1).t);
        assertEquals(5.0, points.get(1).v, 0.0001);
        assertEquals(100, points.get(2).t);
        assertEquals(10.0, points.get(2).v, 0.0001);
    }

    @Test
    void testValidateValidTrajectory() {
        // 10 个点，总时长 1000ms，模拟加速 → 匀速 → 减速
        List<TrajectoryValidator.Point> points = Arrays.asList(
                new TrajectoryValidator.Point(0, 0),
                new TrajectoryValidator.Point(100, 10),
                new TrajectoryValidator.Point(200, 25),
                new TrajectoryValidator.Point(300, 50),
                new TrajectoryValidator.Point(400, 75),
                new TrajectoryValidator.Point(500, 90),
                new TrajectoryValidator.Point(600, 95),
                new TrajectoryValidator.Point(700, 98),
                new TrajectoryValidator.Point(850, 100),
                new TrajectoryValidator.Point(1000, 100));
        assertTrue(TrajectoryValidator.validate(points, props));
    }

    @Test
    void testValidateTooFewPoints() {
        // 仅 3 个点，低于默认 minTrajectoryPoints=5
        List<TrajectoryValidator.Point> points = Arrays.asList(
                new TrajectoryValidator.Point(0, 0),
                new TrajectoryValidator.Point(500, 50),
                new TrajectoryValidator.Point(1000, 100));
        assertFalse(TrajectoryValidator.validate(points, props));
    }

    @Test
    void testValidateTooFast() {
        // 6 个点（充足），但总时长 300ms < 默认 minTrajectoryDurationMs=500
        List<TrajectoryValidator.Point> points = Arrays.asList(
                new TrajectoryValidator.Point(0, 0),
                new TrajectoryValidator.Point(60, 10),
                new TrajectoryValidator.Point(120, 20),
                new TrajectoryValidator.Point(180, 30),
                new TrajectoryValidator.Point(240, 40),
                new TrajectoryValidator.Point(300, 50));
        assertFalse(TrajectoryValidator.validate(points, props));
    }

    @Test
    void testValidateNonContinuous() {
        // 首段跳变 100 > 默认 maxJumpDistance=80，判定为非连续操作
        List<TrajectoryValidator.Point> points = Arrays.asList(
                new TrajectoryValidator.Point(0, 0),
                new TrajectoryValidator.Point(100, 100),
                new TrajectoryValidator.Point(300, 110),
                new TrajectoryValidator.Point(600, 115),
                new TrajectoryValidator.Point(900, 118),
                new TrajectoryValidator.Point(1000, 120));
        assertFalse(TrajectoryValidator.validate(points, props));
    }

    @Test
    void testValidateUniform() {
        // 匀速运动：等时间间隔、等值增量，速度方差为 0，判定为机器行为
        List<TrajectoryValidator.Point> points = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            points.add(new TrajectoryValidator.Point(i * 100L, i * 10));
        }
        assertFalse(TrajectoryValidator.validate(points, props));
    }

    @Test
    void testExtractValue() {
        String json = "{\"value\":123,\"trajectory\":[{\"t\":0,\"v\":0}]}";
        assertEquals(123.0, TrajectoryValidator.extractValue(json), 0.0001);
    }

    @Test
    void testExtractValuePlainNumber() {
        // 兼容纯数字格式（未启用轨迹验证时的旧格式）
        assertEquals(123.0, TrajectoryValidator.extractValue("123"), 0.0001);
        assertEquals(45.5, TrajectoryValidator.extractValue("  45.5  "), 0.0001);
    }
}
