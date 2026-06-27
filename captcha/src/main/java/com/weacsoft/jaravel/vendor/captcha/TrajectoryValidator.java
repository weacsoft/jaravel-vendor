package com.weacsoft.jaravel.vendor.captcha;

import java.util.ArrayList;
import java.util.List;

/**
 * 轨迹验证器：检测用户拖动轨迹是否为人类行为。
 * <p>
 * 滑动和旋转验证码不仅校验最终位置/角度，还校验拖动过程的轨迹特征，
 * 以防范自动化脚本直接提交最终值。
 * <p>
 * 验证维度：
 * <ol>
 *   <li><b>点数检查</b>：轨迹点数必须 ≥ {@code minTrajectoryPoints}，机器行为往往点数过少。</li>
 *   <li><b>时长检查</b>：总拖动时长必须在 [minDuration, maxDuration] 区间内，
 *       过快（&lt;500ms）或过慢（&gt;30s）均判定为可疑。</li>
 *   <li><b>连续性检查</b>：相邻两点的值差不能超过 {@code maxJumpDistance}，
 *       排除瞬移式非连续操作。</li>
 *   <li><b>非匀速检查</b>：人类拖动具有"加速→匀速→减速"特征，速度方差应大于阈值。
 *       匀速直线运动判定为机器行为。</li>
 *   <li><b>加速度方向多样性</b>：人类拖动过程中加速度方向会变化（先正后负），
 *       全程同方向加速度判定为可疑。</li>
 * </ol>
 * <p>
 * 前端通过 JSON 提交轨迹：
 * <pre>
 * {"value": 123, "trajectory": [{"t": 0, "v": 0}, {"t": 50, "v": 5}, ...]}
 * </pre>
 */
public class TrajectoryValidator {

    /**
     * 轨迹点：时间戳（毫秒）+ 值（滑动为 x 坐标，旋转为角度）。
     */
    public static class Point {
        public final long t;
        public final double v;

        public Point(long t, double v) {
            this.t = t;
            this.v = v;
        }
    }

    /**
     * 验证轨迹。
     *
     * @param points   轨迹点列表
     * @param props    配置属性
     * @return 验证通过返回 true
     */
    public static boolean validate(List<Point> points, CaptchaProperties props) {
        if (points == null || points.isEmpty()) {
            return !props.isTrajectoryEnabled();
        }

        // 如果未启用轨迹验证，直接通过
        if (!props.isTrajectoryEnabled()) {
            return true;
        }

        // 1. 点数检查
        if (points.size() < props.getMinTrajectoryPoints()) {
            return false;
        }

        // 2. 时长检查
        long duration = points.get(points.size() - 1).t - points.get(0).t;
        if (duration < props.getMinTrajectoryDurationMs()) {
            return false;
        }
        if (duration > props.getMaxTrajectoryDurationMs()) {
            return false;
        }

        // 3. 连续性检查
        double maxJump = props.getMaxJumpDistance();
        for (int i = 1; i < points.size(); i++) {
            double jump = Math.abs(points.get(i).v - points.get(i - 1).v);
            if (jump > maxJump) {
                return false;
            }
        }

        // 4. 非匀速检查：计算速度方差
        if (points.size() >= 3) {
            List<Double> velocities = new ArrayList<>();
            for (int i = 1; i < points.size(); i++) {
                long dt = points.get(i).t - points.get(i - 1).t;
                if (dt > 0) {
                    double dv = points.get(i).v - points.get(i - 1).v;
                    velocities.add(dv / dt);
                }
            }

            if (velocities.size() >= 2) {
                double meanVel = velocities.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                double variance = velocities.stream()
                        .mapToDouble(v -> (v - meanVel) * (v - meanVel))
                        .average().orElse(0);

                // 速度方差过小 → 匀速运动 → 机器行为
                // 阈值：方差至少为平均速度平方的 1%（经验值，可调整）
                if (variance < 0.0001 && Math.abs(meanVel) > 0.01) {
                    return false;
                }
            }
        }

        // 5. 加速度方向多样性
        if (points.size() >= 4) {
            boolean hasPositive = false;
            boolean hasNegative = false;
            List<Double> velocities = new ArrayList<>();
            for (int i = 1; i < points.size(); i++) {
                long dt = points.get(i).t - points.get(i - 1).t;
                if (dt > 0) {
                    velocities.add((points.get(i).v - points.get(i - 1).v) / dt);
                }
            }
            for (int i = 1; i < velocities.size(); i++) {
                double accel = velocities.get(i) - velocities.get(i - 1);
                if (accel > 0.001) hasPositive = true;
                if (accel < -0.001) hasNegative = true;
            }
            // 人类拖动通常有加速和减速两个阶段
            // 但短距离拖动可能只有单一方向，因此仅在总距离较大时强制要求
            double totalDist = Math.abs(points.get(points.size() - 1).v - points.get(0).v);
            if (totalDist > 20 && !(hasPositive && hasNegative)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 从简单 JSON 字符串解析轨迹点。
     * <p>
     * 输入格式：{@code [{"t":0,"v":0},{"t":50,"v":5},...]}
     * 不依赖第三方 JSON 库，手动解析。
     *
     * @param json JSON 字符串
     * @return 轨迹点列表，解析失败返回空列表
     */
    public static List<Point> parsePoints(String json) {
        List<Point> points = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return points;
        }

        // 简单 JSON 解析：提取 {"t":xxx,"v":yyy} 模式
        int i = 0;
        while (i < json.length()) {
            int objStart = json.indexOf('{', i);
            if (objStart < 0) break;
            int objEnd = json.indexOf('}', objStart);
            if (objEnd < 0) break;

            String obj = json.substring(objStart, objEnd + 1);
            long t = extractNumber(obj, "\"t\":");
            double v = extractNumber(obj, "\"v\":");

            points.add(new Point(t, v));
            i = objEnd + 1;
        }

        return points;
    }

    /**
     * 从 JSON 对象字符串中提取指定字段后面的数字。
     */
    private static long extractNumber(String json, String field) {
        int idx = json.indexOf(field);
        if (idx < 0) return 0;
        int start = idx + field.length();
        // 跳过空格
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == ':')) {
            start++;
        }
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-' || json.charAt(end) == '.')) {
            end++;
        }
        if (end == start) return 0;
        try {
            return (long) Double.parseDouble(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 从 JSON 对象字符串中提取指定字段后面的浮点数。
     */
    private static double extractDouble(String json, String field) {
        int idx = json.indexOf(field);
        if (idx < 0) return 0;
        int start = idx + field.length();
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == ':')) {
            start++;
        }
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-' || json.charAt(end) == '.')) {
            end++;
        }
        if (end == start) return 0;
        try {
            return Double.parseDouble(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 从用户输入 JSON 中提取最终值（value 字段）。
     * <p>
     * 输入格式：{@code {"value": 123, "trajectory": [...]}}
     *
     * @param userInput 用户输入 JSON
     * @return 最终值，解析失败尝试直接解析为数字
     */
    public static double extractValue(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return Double.NaN;
        }

        // 若包含 value 字段，则从 JSON 中提取该字段的数值
        if (userInput.contains("\"value\"")) {
            return extractDouble(userInput, "\"value\":");
        }

        // 兼容旧格式：用户输入直接为数字字符串（未启用轨迹验证时）
        try {
            return Double.parseDouble(userInput.trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /**
     * 从用户输入 JSON 中提取轨迹点。
     *
     * @param userInput 用户输入 JSON
     * @return 轨迹点列表
     */
    public static List<Point> extractTrajectory(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return new ArrayList<>();
        }

        // 提取 trajectory 数组部分
        int trajIdx = userInput.indexOf("\"trajectory\"");
        if (trajIdx < 0) {
            // 兼容旧格式：无轨迹
            return new ArrayList<>();
        }

        int bracketStart = userInput.indexOf('[', trajIdx);
        int bracketEnd = userInput.indexOf(']', bracketStart);
        if (bracketStart < 0 || bracketEnd < 0) {
            return new ArrayList<>();
        }

        String trajJson = userInput.substring(bracketStart, bracketEnd + 1);
        return parsePoints(trajJson);
    }
}
