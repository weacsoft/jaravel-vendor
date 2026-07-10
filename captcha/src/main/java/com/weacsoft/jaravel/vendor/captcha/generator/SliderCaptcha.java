package com.weacsoft.jaravel.vendor.captcha.generator;

import com.weacsoft.jaravel.vendor.captcha.CaptchaContext;
import com.weacsoft.jaravel.vendor.captcha.CaptchaProperties;
import com.weacsoft.jaravel.vendor.captcha.CaptchaResult;
import com.weacsoft.jaravel.vendor.captcha.TrajectoryValidator;
import com.weacsoft.jaravel.vendor.captcha.store.CaptchaStore;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 滑动验证码：在背景图上随机位置抠出随机形状缺口，前端拖动滑块拼回缺口。
 * <p>
 * 缺口形状从多种简洁图形中随机选择：圆形、三角形、菱形、五角星、六边形、
 * 圆角方形、心形、椭圆。所有形状都内切于 blockSize×blockSize 的方形内。
 * 滑块小块用同一形状裁剪，保证滑块和缺口完全对应。
 * <p>
 * 类型名 {@code "slider"}。
 */
public class SliderCaptcha extends AbstractCaptcha {

    public SliderCaptcha() {
        super();
    }

    public SliderCaptcha(CaptchaProperties properties) {
        super(properties);
    }

    public SliderCaptcha(CaptchaStore store, CaptchaProperties properties) {
        super(store, properties);
    }

    @Override
    public String getType() {
        return "slider";
    }

    @Override
    protected CaptchaResult doGenerate(CaptchaContext context) {
        CaptchaProperties p = context.getProperties();
        Random random = new Random();

        int width = p.getWidth();
        int height = p.getHeight();
        // 滑块大小 = min(width, height) 的 1/4 ~ 1/3，随机选择
        int minDim = Math.min(width, height);
        int blockSize = minDim / 4 + random.nextInt(minDim / 12 + 1);  // ~1/4 到 ~1/3
        int blockW = blockSize;
        int blockH = blockSize;

        // 1. 随机选择形状（0~5 共 6 种简洁形状）
        int shapeType = random.nextInt(6);
        Shape shape = createShape(shapeType, blockSize);

        // 2. 真实缺口位置
        int gapMinX = blockSize + 10;
        int gapMaxX = width - blockW - 10;
        if (gapMaxX <= gapMinX) gapMaxX = gapMinX + 1;
        int minGap = (int) (width * 0.35);
        if (gapMinX < minGap) gapMinX = minGap;
        if (gapMaxX <= gapMinX) gapMaxX = gapMinX + 1;
        int gapX = gapMinX + random.nextInt(gapMaxX - gapMinX);
        int gapY = random.nextInt(Math.max(1, height - blockH));

        // 3. 背景图
        BufferedImage bg = loadBackgroundImage(width, height);
        if (bg == null) {
            bg = createImage(width, height);
            Graphics2D g = bg.createGraphics();
            try {
                drawRandomBackground(g, width, height, random);
            } finally {
                g.dispose();
            }
        }

        // 4. 创建滑块小块：从背景 (gapX, gapY) 处按形状裁剪
        //    用 ARGB 图片，形状外为透明，形状内为背景像素 + 白色描边
        BufferedImage slider = createArgbImage(blockW, blockH);
        Graphics2D gs = slider.createGraphics();
        try {
            gs.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            // 裁剪到形状区域，只保留形状内的背景像素
            gs.setClip(shape);
            gs.drawImage(bg, 0, 0, blockW, blockH,
                    gapX, gapY, gapX + blockW, gapY + blockH, null);
            // 描边
            gs.setClip(null);
            gs.setColor(new Color(255, 255, 255, 220));
            gs.setStroke(new BasicStroke(2f));
            gs.draw(shape);
        } finally {
            gs.dispose();
        }

        // 5. 收集所有缺口位置（真实缺口 + 1~2 个干扰缺口）
        List<int[]> gaps = new ArrayList<>();
        gaps.add(new int[]{gapX, gapY});

        int decoyCount = 1 + random.nextInt(2);
        int attempts = 0;
        while (gaps.size() < 1 + decoyCount && attempts < 30) {
            attempts++;
            int dx = blockSize + 10 + random.nextInt(Math.max(1, width - blockW - blockSize - 20));
            int dy = random.nextInt(Math.max(1, height - blockH));
            boolean ok = true;
            for (int[] existing : gaps) {
                if (Math.abs(dx - existing[0]) < blockW + 4 && Math.abs(dy - existing[1]) < blockH + 4) {
                    ok = false;
                    break;
                }
            }
            if (ok) gaps.add(new int[]{dx, dy});
        }

        // 6. 在背景上绘制所有缺口（半透明黑色遮罩 + 白色描边）
        Graphics2D g2 = bg.createGraphics();
        try {
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            for (int[] pos : gaps) {
                AffineTransform saved = g2.getTransform();
                g2.translate(pos[0], pos[1]);
                g2.setColor(new Color(0, 0, 0, 130));
                g2.fill(shape);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2f));
                g2.draw(shape);
                g2.setTransform(saved);
            }
        } finally {
            g2.dispose();
        }

        // 7. 水印
        applyWatermark(bg);

        context.setAnswer(String.valueOf(gapX));

        CaptchaResult result = new CaptchaResult();
        result.setImageBase64(toBase64(bg));
        result.getExtra().put("sliderImage", toBase64(slider));
        result.getExtra().put("gapY", gapY);
        result.getExtra().put("blockSize", blockSize);
        result.getExtra().put("trajectoryEnabled", p.isTrajectoryEnabled());
        return result;
    }

    /**
     * 根据 shapeType 创建形状。所有形状都内切于 size×size 的方形，
     * 留 2px 边距，视觉上简洁美观。
     *
     * @param shapeType  形状类型（0~5）
     * @param size       边长
     * @return Shape 对象
     */
    private Shape createShape(int shapeType, int size) {
        int margin = 2;  // 留 2px 边距，避免形状贴到边缘
        int inner = size - margin * 2;  // 内切区域大小
        int half = size / 2;

        switch (shapeType) {
            case 0:  // 圆形
                return new Ellipse2D.Float(margin, margin, inner, inner);

            case 1:  // 三角形（等边，朝上）
                return createTriangle(size);

            case 2:  // 菱形
                return createDiamond(size);

            case 3:  // 五角星（内径比 0.5，简洁不刺眼）
                return createStar(size, 5, 0.5, margin);

            case 4:  // 六边形
                return createPolygon(size, 6, half - margin, -Math.PI / 2);

            case 5:  // 圆角方形
                return new RoundRectangle2D.Float(margin, margin, inner, inner, 10, 10);

            default:
                return new Ellipse2D.Float(margin, margin, inner, inner);
        }
    }

    /**
     * 创建等边三角形（朝上），内切于 size×size。
     */
    private Shape createTriangle(int size) {
        int margin = 3;
        Path2D path = new Path2D.Double();
        path.moveTo(size / 2.0, margin);
        path.lineTo(size - margin, size - margin);
        path.lineTo(margin, size - margin);
        path.closePath();
        return path;
    }

    /**
     * 创建菱形，内切于 size×size。
     */
    private Shape createDiamond(int size) {
        int margin = 2;
        int half = size / 2;
        Path2D path = new Path2D.Double();
        path.moveTo(half, margin);
        path.lineTo(size - margin, half);
        path.lineTo(half, size - margin);
        path.lineTo(margin, half);
        path.closePath();
        return path;
    }

    /**
     * 创建星形（n 个角），内切于 size×size，留 margin 边距。
     *
     * @param size       边长
     * @param points     角数
     * @param innerRatio 内径/外径比（0~1，越小越尖）
     * @param margin     边距
     */
    private Shape createStar(int size, int points, double innerRatio, int margin) {
        int half = size / 2;
        double outerR = half - margin;
        double innerR = outerR * innerRatio;
        Path2D path = new Path2D.Double();
        for (int i = 0; i < points * 2; i++) {
            double r = (i % 2 == 0) ? outerR : innerR;
            double angle = -Math.PI / 2 + i * Math.PI / points;
            double x = half + r * Math.cos(angle);
            double y = half + r * Math.sin(angle);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.closePath();
        return path;
    }

    /**
     * 创建正多边形，内切于 size×size，留 margin 边距。
     *
     * @param size       边长
     * @param sides      边数
     * @param radius     外接圆半径
     * @param startAngle 起始角度
     */
    private Shape createPolygon(int size, int sides, double radius, double startAngle) {
        int half = size / 2;
        Path2D path = new Path2D.Double();
        for (int i = 0; i < sides; i++) {
            double angle = startAngle + i * 2 * Math.PI / sides;
            double x = half + radius * Math.cos(angle);
            double y = half + radius * Math.sin(angle);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.closePath();
        return path;
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

        if (Math.abs(target - input) > properties.getTolerance()) {
            return false;
        }

        if (properties.isTrajectoryEnabled()) {
            List<TrajectoryValidator.Point> points =
                    TrajectoryValidator.extractTrajectory(userInput);
            if (!TrajectoryValidator.validate(points, properties)) {
                return false;
            }
        }

        return true;
    }
}
