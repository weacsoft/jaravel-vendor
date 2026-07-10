package com.weacsoft.jaravel.vendor.captcha;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * 旋转验证码：从一张带明显朝向的图片中切出圆形区域，随机旋转后展示，
 * 前端拖动滑块将圆盘旋转回正确方向使图案对齐。
 * <p>
 * 类型名 {@code "rotate"}。生成结果中：
 * <ul>
 *     <li>{@code imageBase64} —— 背景图（中心有暗色圆孔）；</li>
 *     <li>{@code extra.circleImage} —— 被旋转的圆形拼图块（带透明通道的 PNG base64）；</li>
 *     <li>{@code extra.size} —— 图片边长。</li>
 * </ul>
 * <p>
 * 答案为用户需要旋转的角度（即 {@code 360 - 原始旋转角度}），
 * 这样用户拖动滑块的值直接就是答案值，逻辑自然。
 * <p>
 * 验证时：
 * <ol>
 *   <li>角度校验：用户提交的 value 与目标角度的最短角差在 {@link CaptchaProperties#getTolerance()} 内。</li>
 *   <li>轨迹校验：若 {@link CaptchaProperties#isTrajectoryEnabled()} 为 true，
 *       还需校验旋转轨迹是否为人类行为。</li>
 * </ol>
 * <p>
 * <h3>用户输入格式</h3>
 * 启用轨迹验证时，前端需提交 JSON：
 * <pre>
 * {"value": 270, "trajectory": [{"t":0,"v":0},{"t":50,"v":5},...]}
 * </pre>
 * 其中 {@code value} 为滑块最终角度值，{@code trajectory} 为拖动过程中采集的 {时间戳ms, 角度} 点序列。
 */
public class RotateCaptcha extends AbstractCaptcha {

    public RotateCaptcha() {
        super();
    }

    public RotateCaptcha(CaptchaProperties properties) {
        super(properties);
    }

    public RotateCaptcha(CaptchaStore store, CaptchaProperties properties) {
        super(store, properties);
    }

    @Override
    public String getType() {
        return "rotate";
    }

    @Override
    protected CaptchaResult doGenerate(CaptchaContext context) {
        CaptchaProperties p = context.getProperties();
        Random random = new Random();

        // 旋转验证码使用正方形画布
        int size = p.getWidth();
        int angle = random.nextInt(360);

        // 1. 绘制带明显朝向的原图：优先使用自定义背景图，无则生成风景图
        BufferedImage src = loadBackgroundImage(size, size);
        if (src == null) {
            src = createSceneImage(size, random);
        }

        // 2. 定义中心圆形区域
        int cx = size / 2;
        int cy = size / 2;
        int r = (int) (size * 0.35);

        // 3. 从原图中切出圆形拼图块（2r x 2r，恰好是圆形，无透明边距）
        BufferedImage circle = extractCircle(src, cx, cy, r);

        // 4. 创建背景图：原图 + 中心暗色圆孔 + 边框
        BufferedImage bgWithHole = createImage(size, size);
        Graphics2D gBg = bgWithHole.createGraphics();
        try {
            gBg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            gBg.drawImage(src, 0, 0, null);
            // 暗色圆孔（半透明黑色遮罩）
            gBg.setColor(new Color(0, 0, 0, 120));
            gBg.fillOval(cx - r, cy - r, r * 2, r * 2);
            // 暗色描边（与圆孔融为一体，不使用白色）
            gBg.setColor(new Color(0, 0, 0, 60));
            gBg.setStroke(new BasicStroke(1.5f));
            gBg.drawOval(cx - r, cy - r, r * 2 - 1, r * 2 - 1);
        } finally {
            gBg.dispose();
        }

        // 5. 旋转圆形拼图块（2r×2r 图片旋转后再裁剪圆形，无方形边距伪影）
        BufferedImage rotatedCircle = rotateImage(circle, angle, r);

        // 6. 应用水印（仅背景）
        applyWatermark(bgWithHole);

        // 答案是用户需要旋转回正的角度 = 360 - 原始旋转角度
        int userTarget = (360 - angle) % 360;
        context.setAnswer(String.valueOf(userTarget));

        CaptchaResult result = new CaptchaResult();
        result.setImageBase64(toBase64(bgWithHole));
        result.getExtra().put("circleImage", toBase64(rotatedCircle));
        result.getExtra().put("size", size);
        result.getExtra().put("cx", cx);
        result.getExtra().put("cy", cy);
        result.getExtra().put("r", r);
        result.getExtra().put("trajectoryEnabled", p.isTrajectoryEnabled());
        return result;
    }

    @Override
    protected boolean doVerify(String answer, String userInput) {
        if (answer == null || userInput == null) {
            return false;
        }

        double target = Double.parseDouble(answer.trim());
        double input = TrajectoryValidator.extractValue(userInput);

        if (Double.isNaN(input)) {
            return false;
        }

        // 角度校验（双向最短角差）
        double diff = ((target - input) % 360 + 360) % 360;
        if (diff > 180) {
            diff = 360 - diff;
        }
        if (diff > properties.getTolerance()) {
            return false;
        }

        // 轨迹校验
        if (properties.isTrajectoryEnabled()) {
            java.util.List<TrajectoryValidator.Point> points =
                    TrajectoryValidator.extractTrajectory(userInput);
            if (!TrajectoryValidator.validate(points, properties)) {
                return false;
            }
        }

        return true;
    }

    // ==================== 图像生成 ====================

    /**
     * 生成一张风景图，包含太阳、云、山、草地、树等明确朝向的元素。
     */
    private BufferedImage createSceneImage(int size, Random random) {
        BufferedImage img = createImage(size, size);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            // 天空渐变（上 60%）
            Color skyTop = new Color(100, 180, 255);
            Color skyBottom = new Color(200, 230, 255);
            GradientPaint sky = new GradientPaint(0, 0, skyTop, 0, size * 0.65f, skyBottom);
            g.setPaint(sky);
            g.fillRect(0, 0, size, (int) (size * 0.65));

            // 太阳（左上方，明确朝向参照物）
            int sunX = size / 4;
            int sunY = size / 5;
            int sunR = Math.max(8, size / 10);
            g.setColor(new Color(255, 220, 100, 80));
            g.fillOval(sunX - sunR - 6, sunY - sunR - 6, (sunR + 6) * 2, (sunR + 6) * 2);
            g.setColor(new Color(255, 200, 0));
            g.fillOval(sunX - sunR, sunY - sunR, sunR * 2, sunR * 2);

            // 云朵
            g.setColor(new Color(255, 255, 255, 220));
            drawCloud(g, size * 0.6, size * 0.12, size * 0.06);
            drawCloud(g, size * 0.8, size * 0.22, size * 0.05);

            // 远山（深绿）
            g.setColor(new Color(60, 110, 50));
            int[] farX = {0, (int) (size * 0.25), (int) (size * 0.5), (int) (size * 0.75), size};
            int[] farY = {(int) (size * 0.65), (int) (size * 0.4), (int) (size * 0.5), (int) (size * 0.38), (int) (size * 0.6)};
            g.fillPolygon(farX, farY, 5);

            // 近山（中绿）
            g.setColor(new Color(80, 140, 60));
            int[] nearX = {0, (int) (size * 0.3), (int) (size * 0.6), size};
            int[] nearY = {(int) (size * 0.65), (int) (size * 0.48), (int) (size * 0.55), (int) (size * 0.5)};
            g.fillPolygon(nearX, nearY, 4);

            // 雪顶
            g.setColor(new Color(245, 245, 255));
            int[] snowX = {(int) (size * 0.25) - size / 16, (int) (size * 0.25), (int) (size * 0.25) + size / 16};
            int[] snowY = {(int) (size * 0.43), (int) (size * 0.4), (int) (size * 0.43)};
            g.fillPolygon(snowX, snowY, 3);

            // 草地
            GradientPaint grass = new GradientPaint(0, (int) (size * 0.65), new Color(100, 170, 50),
                    0, size, new Color(70, 130, 30));
            g.setPaint(grass);
            g.fillRect(0, (int) (size * 0.65), size, (int) (size * 0.35));

            // 树（右侧，朝向参照物）
            int treeX = (int) (size * 0.78);
            int treeBase = (int) (size * 0.65);
            int trunkH = size / 5;
            g.setColor(new Color(110, 70, 30));
            g.fillRect(treeX - size / 50, treeBase - trunkH, size / 25, trunkH);
            g.setColor(new Color(40, 130, 40));
            g.fillOval(treeX - size / 6, treeBase - trunkH - size / 6, size / 3, size / 3);
            g.setColor(new Color(60, 150, 50));
            g.fillOval(treeX - size / 8, treeBase - trunkH - size / 8, size / 4, size / 4);

            // 小花
            for (int i = 0; i < 6; i++) {
                int fx = random.nextInt(size);
                int fy = (int) (size * 0.7) + random.nextInt((int) (size * 0.25));
                g.setColor(randomFlowerColor(random));
                g.fillOval(fx - 3, fy - 3, 6, 6);
                g.setColor(new Color(255, 235, 60));
                g.fillOval(fx - 1, fy - 1, 2, 2);
            }
        } finally {
            g.dispose();
        }
        return img;
    }

    /**
     * 绘制一朵简单的云。
     */
    private void drawCloud(Graphics2D g, double cx, double cy, double r) {
        int x = (int) cx;
        int y = (int) cy;
        int r1 = (int) r;
        g.fillOval(x - r1, y - r1 / 2, r1 * 2, r1);
        g.fillOval(x - r1 / 2, y - r1, r1 * 3 / 2, r1 * 3 / 2);
        g.fillOval(x + r1 / 2, y - r1 / 2, r1 * 3 / 2, r1);
    }

    /**
     * 随机花朵颜色。
     */
    private Color randomFlowerColor(Random random) {
        Color[] colors = {
            new Color(255, 100, 100), new Color(255, 180, 200),
            new Color(200, 150, 255), new Color(255, 255, 100),
            new Color(255, 150, 50)
        };
        return colors[random.nextInt(colors.length)];
    }

    // ==================== 圆形抠图与旋转 ====================

    /**
     * 从源图中切出圆形区域，生成 2r x 2r 的 ARGB 图片（恰好是圆形，无透明边距）。
     * <p>
     * 图片尺寸严格等于圆的直径，消除了 size×size 画布中圆外的透明区域，
     * 从根本上避免浏览器旋转时方形包围盒产生的描边伪影。
     *
     * @param src 源图
     * @param cx  圆心 x（在源图中的坐标）
     * @param cy  圆心 y（在源图中的坐标）
     * @param r   半径
     * @return 2r×2r 的圆形图片（圆外无像素）
     */
    private BufferedImage extractCircle(BufferedImage src, int cx, int cy, int r) {
        int d = r * 2;
        BufferedImage circle = createArgbImage(d, d);
        Graphics2D g = circle.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            // 填充不透明圆形（在 2r×2r 画布中，圆心位于 (r, r)）
            Ellipse2D circleShape = new Ellipse2D.Float(0, 0, d, d);
            g.setColor(Color.WHITE);
            g.fill(circleShape);
            // SrcIn 合成：仅保留圆形区域内的源图像素
            g.setComposite(java.awt.AlphaComposite.SrcIn);
            // 将源图偏移绘制，使圆心对齐到 (r, r)
            g.drawImage(src, -(cx - r), -(cy - r), null);
        } finally {
            g.dispose();
        }
        return circle;
    }

    /**
     * 将 2r×2r 圆形图片绕中心旋转指定角度，再次裁剪圆形，生成干净的 ARGB 图片。
     * <p>
     * 由于图片尺寸等于圆的直径，旋转后无方形边距伪影。
     *
     * @param src   源图（2r×2r 圆形）
     * @param angle 旋转角度（度）
     * @param r     半径
     * @return 旋转后的 2r×2r 圆形图片
     */
    private BufferedImage rotateImage(BufferedImage src, int angle, int r) {
        int d = r * 2;
        // 1. 旋转
        BufferedImage rotated = createArgbImage(d, d);
        Graphics2D g = rotated.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.rotate(Math.toRadians(angle), r, r);
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }

        // 2. 再次裁剪圆形（消除旋转插值产生的边缘伪影）
        BufferedImage masked = createArgbImage(d, d);
        Graphics2D gm = masked.createGraphics();
        try {
            gm.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Ellipse2D circleShape = new Ellipse2D.Float(0, 0, d, d);
            gm.setColor(Color.WHITE);
            gm.fill(circleShape);
            gm.setComposite(java.awt.AlphaComposite.SrcIn);
            gm.drawImage(rotated, 0, 0, null);
        } finally {
            gm.dispose();
        }
        return masked;
    }
}
