package com.weacsoft.jaravel.vendor.captcha;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * 验证码抽象基类，实现 {@link Captcha} 接口，提供模板方法与公共图像处理逻辑。
 * <p>
 * 采用模板方法模式：
 * <ul>
 *     <li>{@link #generate(String)} —— 构造上下文、调用 {@link #doGenerate(CaptchaContext)} 生成结果，
 *         将答案写入 {@link CaptchaStore}，并生成无状态 token；</li>
 *     <li>{@link #verify(String, String)} —— 从 {@link CaptchaStore} 一次性取出答案，
 *         交给 {@link #doVerify(String, String)} 比对。</li>
 * </ul>
 * 子类只需实现 {@link #doGenerate(CaptchaContext)}（产出图片 + 通过上下文回写答案）
 * 与 {@link #doVerify(String, String)}（比对逻辑）即可。
 * <p>
 * 另提供 {@link #generateToken(String, String, long)} 与 {@link #verifyToken(String, String)}
 * 用于无状态场景：token 由 captchaKey、answer、expireTime 经 Base64 编码拼接而成
 * （简单编码，非加密安全；如需更高安全性可由子类覆盖）。
 * <p>
 * 核心层不依赖任何第三方库，图像生成基于 {@code java.awt}，编码基于 {@code java.util.Base64}。
 */
public abstract class AbstractCaptcha implements Captcha {

    /** 验证码存储 */
    protected CaptchaStore store;

    /** 配置属性 */
    protected CaptchaProperties properties;

    /**
     * 默认构造：使用 {@link MemoryCaptchaStore} 与默认配置。
     */
    public AbstractCaptcha() {
        this(new MemoryCaptchaStore(), CaptchaProperties.createDefault());
    }

    /**
     * 指定存储与配置构造。
     *
     * @param store      验证码存储
     * @param properties 配置属性
     */
    public AbstractCaptcha(CaptchaStore store, CaptchaProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    // ==================== 模板方法 ====================

    /**
     * 子类实现：生成验证码图片并通过 {@link CaptchaContext#setAnswer(String)} 回写答案。
     *
     * @param context 生成上下文
     * @return 生成结果（至少应填充 imageBase64 与 extra）
     */
    protected abstract CaptchaResult doGenerate(CaptchaContext context);

    /**
     * 子类实现：比对真实答案与用户输入。
     *
     * @param answer    真实答案（来自 store 或 token）
     * @param userInput 用户输入
     * @return 是否匹配
     */
    protected abstract boolean doVerify(String answer, String userInput);

    @Override
    public CaptchaResult generate(String captchaKey) {
        CaptchaContext context = new CaptchaContext(captchaKey, properties);
        CaptchaResult result = doGenerate(context);
        if (result == null) {
            result = new CaptchaResult();
        }

        long expireTime = System.currentTimeMillis() + properties.getExpireSeconds() * 1000L;
        result.setCaptchaKey(captchaKey);
        result.setType(getType());
        result.setExpireTime(expireTime);

        String answer = context.getAnswer();
        if (store != null && answer != null) {
            store.put(captchaKey, answer, properties.getExpireSeconds());
        }
        result.setToken(generateToken(captchaKey, answer, expireTime));
        return result;
    }

    @Override
    public boolean verify(String captchaKey, String userInput) {
        if (store == null) {
            return false;
        }
        String answer = store.pull(captchaKey);
        if (answer == null) {
            return false;
        }
        return doVerify(answer, userInput);
    }

    // ==================== 无状态 token ====================

    /**
     * 生成无状态 token：对 captchaKey、answer、expireTime 分别 Base64 编码后以 "." 拼接。
     *
     * @param captchaKey 验证码标识
     * @param answer     答案
     * @param expireTime 过期时间戳（毫秒）
     * @return token 字符串
     */
    protected String generateToken(String captchaKey, String answer, long expireTime) {
        Base64.Encoder encoder = Base64.getEncoder();
        String keyPart = encoder.encodeToString(toBytes(captchaKey));
        String answerPart = encoder.encodeToString(toBytes(answer != null ? answer : ""));
        String timePart = encoder.encodeToString(toBytes(String.valueOf(expireTime)));
        return keyPart + "." + answerPart + "." + timePart;
    }

    /**
     * 校验无状态 token：解码 token 取出答案与过期时间，过期或解码失败返回 {@code false}，
     * 否则交由 {@link #doVerify(String, String)} 比对。
     *
     * @param token      token 字符串
     * @param userInput  用户输入
     * @return 是否通过
     */
    protected boolean verifyToken(String token, String userInput) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return false;
        }
        try {
            Base64.Decoder decoder = Base64.getDecoder();
            String answer = new String(decoder.decode(parts[1]), StandardCharsets.UTF_8);
            long expireTime = Long.parseLong(new String(decoder.decode(parts[2]), StandardCharsets.UTF_8));
            if (System.currentTimeMillis() > expireTime) {
                return false;
            }
            return doVerify(answer, userInput);
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 公共图像工具 ====================

    /**
     * 创建一张白色背景的 RGB 图片。
     *
     * @param width  宽
     * @param height 高
     * @return 图片
     */
    protected BufferedImage createImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }

    /**
     * 创建一张透明背景的 ARGB 图片（用于滑块等需要透明通道的场景）。
     *
     * @param width  宽
     * @param height 高
     * @return 图片
     */
    protected BufferedImage createArgbImage(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    /**
     * 生成随机颜色（限制亮度避免过白）。
     *
     * @param random 随机源
     * @return 随机颜色
     */
    protected Color randomColor(Random random) {
        return new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200));
    }

    /**
     * 绘制随机渐变背景 + 色块，作为滑动 / 旋转验证码的底图纹理。
     *
     * @param g      画笔
     * @param width  宽
     * @param height 高
     * @param random 随机源
     */
    protected void drawRandomBackground(Graphics2D g, int width, int height, Random random) {
        GradientPaint paint = new GradientPaint(0, 0, randomColor(random), width, height, randomColor(random));
        g.setPaint(paint);
        g.fillRect(0, 0, width, height);
        for (int i = 0; i < 24; i++) {
            g.setColor(randomColor(random));
            int x = random.nextInt(width);
            int y = random.nextInt(height);
            int w = random.nextInt(20) + 5;
            int h = random.nextInt(20) + 5;
            g.fillRect(x, y, w, h);
        }
    }

    /**
     * 添加噪点。
     *
     * @param g      画笔
     * @param width  宽
     * @param height 高
     * @param count  噪点数量
     * @param random 随机源
     */
    protected void addNoise(Graphics2D g, int width, int height, int count, Random random) {
        for (int i = 0; i < count; i++) {
            g.setColor(randomColor(random));
            g.fillRect(random.nextInt(width), random.nextInt(height), 1, 1);
        }
    }

    /**
     * 添加干扰线。
     *
     * @param g      画笔
     * @param width  宽
     * @param height 高
     * @param count  干扰线数量
     * @param random 随机源
     */
    protected void addInterfereLines(Graphics2D g, int width, int height, int count, Random random) {
        g.setStroke(new BasicStroke(1.2f));
        for (int i = 0; i < count; i++) {
            g.setColor(randomColor(random));
            g.drawLine(random.nextInt(width), random.nextInt(height),
                    random.nextInt(width), random.nextInt(height));
        }
    }

    /**
     * 绘制验证码文本：每个字符独立颜色、字体大小与旋转角度。
     * <p>
     * 支持通过 {@link CaptchaProperties} 配置字体名称、样式、大小范围和旋转角度。
     *
     * @param g      画笔
     * @param text   文本
     * @param width  画布宽
     * @param height 画布高
     * @param random 随机源
     */
    protected void drawText(Graphics2D g, String text, int width, int height, Random random) {
        drawText(g, text, width, height, random, properties);
    }

    /**
     * 绘制验证码文本（带自定义配置）。
     *
     * @param g          画笔
     * @param text       文本
     * @param width      画布宽
     * @param height     画布高
     * @param random     随机源
     * @param props      配置属性
     */
    protected void drawText(Graphics2D g, String text, int width, int height, Random random,
                            CaptchaProperties props) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int len = text.length();
        int slot = width / (len + 1);

        int minFs = props.getMinFontSize() > 0 ? props.getMinFontSize() : height - 14;
        int maxFs = props.getMaxFontSize() > 0 ? props.getMaxFontSize() : height - 6;
        if (minFs > maxFs) {
            int tmp = minFs; minFs = maxFs; maxFs = tmp;
        }
        int maxRot = props.getMaxRotationDegree();

        for (int i = 0; i < len; i++) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setColor(randomColor(random));
                int fontSize = minFs + (maxFs > minFs ? random.nextInt(maxFs - minFs) : 0);
                String family = props.getFontFamily() != null ? props.getFontFamily() : "Arial";
                g2.setFont(new Font(family, props.getFontStyle(), fontSize));
                int x = slot * i + slot / 2;
                int y = height / 2 + random.nextInt(height / 4) - height / 8;
                double angle = maxRot > 0 ? random.nextInt(maxRot * 2) - maxRot : 0;
                g2.rotate(Math.toRadians(angle), x, y);
                g2.drawString(String.valueOf(text.charAt(i)), x, y);
            } finally {
                g2.dispose();
            }
        }
    }

    /**
     * 添加弧线干扰（比直线更难被 OCR 识别）。
     *
     * @param g      画笔
     * @param width  宽
     * @param height 高
     * @param count  弧线数量
     * @param random 随机源
     */
    protected void addArcInterference(Graphics2D g, int width, int height, int count, Random random) {
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < count; i++) {
            g.setColor(randomColor(random));
            int x1 = random.nextInt(width);
            int y1 = random.nextInt(height);
            int x2 = random.nextInt(width);
            int y2 = random.nextInt(height);
            int ctrlX = random.nextInt(width);
            int ctrlY = random.nextInt(height);
            java.awt.geom.QuadCurve2D curve = new java.awt.geom.QuadCurve2D.Float(x1, y1, ctrlX, ctrlY, x2, y2);
            g.draw(curve);
        }
    }

    /**
     * 将图片编码为带前缀的 base64 字符串：{@code data:image/png;base64,...}。
     *
     * @param image 图片
     * @return base64 字符串
     */
    protected String toBase64(BufferedImage image) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode captcha image", e);
        }
    }

    /**
     * 从配置的字符集随机生成指定长度字符串。
     *
     * @param length 长度
     * @param random 随机源
     * @return 随机字符串
     */
    protected String randomString(int length, Random random) {
        char[] chars = properties != null ? properties.getEffectiveChars() : CaptchaProperties.DEFAULT_CHARS;
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars[random.nextInt(chars.length)]);
        }
        return sb.toString();
    }

    private static byte[] toBytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    // ==================== getter / setter ====================

    public CaptchaStore getStore() {
        return store;
    }

    public void setStore(CaptchaStore store) {
        this.store = store;
    }

    public CaptchaProperties getProperties() {
        return properties;
    }

    public void setProperties(CaptchaProperties properties) {
        this.properties = properties;
    }
}
