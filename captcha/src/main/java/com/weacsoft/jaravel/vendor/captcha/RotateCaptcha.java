package com.weacsoft.jaravel.vendor.captcha;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * 旋转验证码：将一张带明显朝向的图片随机旋转，前端拖动将其转回正方向。
 * <p>
 * 类型名 {@code "rotate"}。生成结果中 {@code imageBase64} 为旋转后的图片，
 * {@code extra.size} 为图片边长。答案为旋转角度（0~359），验证时按
 * {@link CaptchaProperties#getTolerance()} 角度容差判定（双向最短角差）。
 */
public class RotateCaptcha extends AbstractCaptcha {

    public RotateCaptcha() {
        super();
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
        int size = Math.max(p.getWidth(), 200);
        int angle = random.nextInt(360);

        // 1. 绘制带明显朝向的原图（渐变背景 + 向上箭头）
        BufferedImage src = createImage(size, size);
        Graphics2D g = src.createGraphics();
        try {
            drawRandomBackground(g, size, size, random);
            drawDirectionMarker(g, size, random);
        } finally {
            g.dispose();
        }

        // 2. 旋转生成展示图
        BufferedImage rotated = rotateImage(src, angle);

        context.setAnswer(String.valueOf(angle));

        CaptchaResult result = new CaptchaResult();
        result.setImageBase64(toBase64(rotated));
        result.getExtra().put("size", size);
        return result;
    }

    @Override
    protected boolean doVerify(String answer, String userInput) {
        if (answer == null || userInput == null) {
            return false;
        }
        try {
            double target = Double.parseDouble(answer.trim());
            double input = Double.parseDouble(userInput.trim());
            double diff = ((target - input) % 360 + 360) % 360;
            if (diff > 180) {
                diff = 360 - diff;
            }
            return diff <= properties.getTolerance();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 绘制朝向标记：顶部居中一个向上的箭头，便于用户判断正方向。
     */
    private void drawDirectionMarker(Graphics2D g, int size, Random random) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(255, 255, 255, 230));
        g.setStroke(new BasicStroke(3f));
        int cx = size / 2;
        int top = size / 6;
        int bottom = size / 2;
        // 箭头杆
        g.drawLine(cx, top + size / 10, cx, bottom);
        // 箭头头部三角
        Polygon head = new Polygon();
        head.addPoint(cx, top);
        head.addPoint(cx - size / 14, top + size / 10);
        head.addPoint(cx + size / 14, top + size / 10);
        g.setColor(new Color(random.nextInt(100), random.nextInt(100), 200));
        g.fillPolygon(head);
    }

    /**
     * 将图片绕中心旋转指定角度，生成新的正方形图片（未覆盖区域填白）。
     */
    private BufferedImage rotateImage(BufferedImage src, int angle) {
        int size = src.getWidth();
        BufferedImage out = createImage(size, size);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.rotate(Math.toRadians(angle), size / 2.0, size / 2.0);
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}
