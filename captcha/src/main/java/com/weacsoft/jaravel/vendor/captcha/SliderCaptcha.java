package com.weacsoft.jaravel.vendor.captcha;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * 滑动验证码：在背景图上随机位置抠出缺口，前端拖动滑块拼回缺口。
 * <p>
 * 类型名 {@code "slider"}。生成结果中：
 * <ul>
 *     <li>{@code imageBase64} —— 带缺口的背景图；</li>
 *     <li>{@code extra.sliderImage} —— 滑块拼图小块（带透明通道的 PNG base64）；</li>
 *     <li>{@code extra.gapY} —— 缺口纵坐标（前端固定滑块纵坐标用）；</li>
 *     <li>{@code extra.blockSize} —— 滑块边长。</li>
 * </ul>
 * <p>
 * 答案为缺口横坐标 {@code gapX}。验证时：
 * <ol>
 *   <li>位置校验：用户提交的 value 与 gapX 的偏差在 {@link CaptchaProperties#getTolerance()} 像素内。</li>
 *   <li>轨迹校验：若 {@link CaptchaProperties#isTrajectoryEnabled()} 为 true，
 *       还需校验拖动轨迹是否为人类行为（点数、时长、连续性、非匀速、加速度多样性）。</li>
 * </ol>
 * <p>
 * <h3>用户输入格式</h3>
 * 启用轨迹验证时，前端需提交 JSON：
 * <pre>
 * {"value": 123, "trajectory": [{"t":0,"v":0},{"t":50,"v":5},...]}
 * </pre>
 * 其中 {@code value} 为滑块最终 x 坐标，{@code trajectory} 为拖动过程中采集的 {时间戳ms, x坐标} 点序列。
 * <p>
 * 未启用轨迹验证时，用户输入可直接为数字字符串 {@code "123"}（向后兼容）。
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
        int blockSize = Math.max(20, height / 2);
        // 缺口横坐标：保证滑块从左侧滑入后仍可见，且不贴边
        int minX = blockSize + 10;
        int maxX = width - blockSize - 10;
        if (maxX <= minX) {
            maxX = minX + 1;
        }
        int gapX = minX + random.nextInt(maxX - minX);
        int gapY = random.nextInt(Math.max(1, height - blockSize));

        // 1. 背景图：优先使用自定义背景图，无则随机生成
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

        // 2. 滑块拼图小块（先于绘制缺口拷贝像素，保证小块内容与背景一致）
        BufferedImage slider = createArgbImage(blockSize, blockSize);
        for (int x = 0; x < blockSize; x++) {
            for (int y = 0; y < blockSize; y++) {
                slider.setRGB(x, y, bg.getRGB(gapX + x, gapY + y));
            }
        }
        Graphics2D gs = slider.createGraphics();
        try {
            gs.setColor(new Color(255, 255, 255, 220));
            gs.setStroke(new BasicStroke(1.5f));
            gs.drawRect(0, 0, blockSize - 1, blockSize - 1);
        } finally {
            gs.dispose();
        }

        // 3. 在背景上绘制缺口（半透明遮罩 + 边框）
        Graphics2D g2 = bg.createGraphics();
        try {
            g2.setColor(new Color(0, 0, 0, 110));
            g2.fillRect(gapX, gapY, blockSize, blockSize);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRect(gapX, gapY, blockSize - 1, blockSize - 1);
        } finally {
            g2.dispose();
        }

        // 4. 应用水印
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

    @Override
    protected boolean doVerify(String answer, String userInput) {
        if (answer == null || userInput == null) {
            return false;
        }

        // 提取用户提交的最终值
        double target = Double.parseDouble(answer.trim());
        double input = TrajectoryValidator.extractValue(userInput);

        if (Double.isNaN(input)) {
            return false;
        }

        // 1. 位置校验
        if (Math.abs(target - input) > properties.getTolerance()) {
            return false;
        }

        // 2. 轨迹校验
        if (properties.isTrajectoryEnabled()) {
            java.util.List<TrajectoryValidator.Point> points =
                    TrajectoryValidator.extractTrajectory(userInput);
            if (!TrajectoryValidator.validate(points, properties)) {
                return false;
            }
        }

        return true;
    }
}
