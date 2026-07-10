package com.weacsoft.jaravel.vendor.captcha;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GradientPaint;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import javax.imageio.ImageIO;

/**
 * 验证码抽象基类，实现 {@link Captcha} 接口，提供模板方法与公共图像处理逻辑。
 * <p>
 * 采用<b>无状态设计</b>与模板方法模式：
 * <ul>
 *     <li>{@link #generate()} —— 构造上下文、调用 {@link #doGenerate(CaptchaContext)} 生成图片，
 *         将答案加密为自包含的 captchaKey（answer|expireTime|nonce），服务端无需存储任何状态；</li>
 *     <li>{@link #verify(String, String)} —— 解密 captchaKey 取出答案与过期时间，
     *         检查过期后交由 {@link #doVerify(String, String)} 比对。</li>
 * </ul>
 * 子类只需实现 {@link #doGenerate(CaptchaContext)}（产出图片 + 通过上下文回写答案）
 * 与 {@link #doVerify(String, String)}（比对逻辑）即可。
 * <p>
 * <h3>无状态 captchaKey 格式</h3>
 * <pre>
 *   captchaKey = crypto.encrypt(expireTime + "|" + nonce + "|" + answer)
 * </pre>
 * 解密后按 {@code |} 分割为 3 段（answer 可含 {@code |}），依次为过期时间、随机数、答案。
 * <p>
 * <h3>运行时配置覆盖</h3>
 * 支持通过 {@link #generate(CaptchaProperties)} 和 {@link #generate(CaptchaProperties, String)}
 * 在调用时指定配置覆盖和加密密钥，无需修改全局配置。
 * <p>
 * 核心层不依赖任何第三方库，图像生成基于 {@code java.awt}，编码基于 {@code java.util.Base64}，
 * 加密基于 JDK 内置 {@code javax.crypto} / {@code java.security}。
 */
public abstract class AbstractCaptcha implements Captcha {

    /** 配置属性 */
    protected CaptchaProperties properties;

    /**
     * 默认构造：使用默认配置。
     */
    public AbstractCaptcha() {
        this(CaptchaProperties.createDefault());
    }

    /**
     * 指定配置构造。
     *
     * @param properties 配置属性
     */
    public AbstractCaptcha(CaptchaProperties properties) {
        this.properties = properties;
    }

    /**
     * 兼容旧构造（store 参数被忽略，无状态模式不需要存储）。
     *
     * @param store      验证码存储（已弃用，无状态模式不使用）
     * @param properties 配置属性
     */
    public AbstractCaptcha(CaptchaStore store, CaptchaProperties properties) {
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
     * @param answer    真实答案（从 captchaKey 解密获得）
     * @param userInput 用户输入（已解密）
     * @return 是否匹配
     */
    protected abstract boolean doVerify(String answer, String userInput);

    // ==================== 生成（无状态） ====================

    @Override
    public CaptchaResult generate() {
        return generate(null, null);
    }

    /**
     * 生成验证码（带运行时配置覆盖）。
     *
     * @param overrides 运行时配置覆盖（null 表示使用默认配置）
     * @return 验证码结果
     */
    public CaptchaResult generate(CaptchaProperties overrides) {
        return generate(overrides, null);
    }

    /**
     * 生成验证码（带运行时配置覆盖和加密密钥）。
     *
     * @param overrides      运行时配置覆盖（null 表示使用默认配置）
     * @param encryptionKey  运行时加密密钥（null 表示使用配置中的密钥）
     * @return 验证码结果
     */
    public CaptchaResult generate(CaptchaProperties overrides, String encryptionKey) {
        CaptchaProperties props = overrides != null ? overrides : this.properties;
        CaptchaCrypto crypto = createCrypto(props, encryptionKey);

        CaptchaContext context = new CaptchaContext(null, props);
        CaptchaResult result = doGenerate(context);
        if (result == null) {
            result = new CaptchaResult();
        }

        long expireTime = System.currentTimeMillis() + props.getExpireSeconds() * 1000L;
        String answer = context.getAnswer();
        if (answer == null) {
            answer = "";
        }
        String nonce = Long.toHexString(System.nanoTime())
                + Integer.toHexString(new Random().nextInt(0xFFFF));
        // 格式: expireTime|nonce|answer（answer 在最后，可含特殊字符）
        String payload = expireTime + "|" + nonce + "|" + answer;
        String captchaKey = crypto.encrypt(payload);

        result.setCaptchaKey(captchaKey);
        result.setType(getType());
        result.setExpireTime(expireTime);
        return result;
    }

    // ==================== 验证（无状态） ====================

    @Override
    public boolean verify(String captchaKey, String userInput) {
        return verify(captchaKey, userInput, null, null);
    }

    /**
     * 验证验证码（带运行时加密密钥）。
     *
     * @param captchaKey     验证码 key（自包含加密令牌）
     * @param userInput      用户输入（可能是加密的）
     * @param encryptionKey  运行时加密密钥（null 表示使用配置中的密钥）
     * @return 是否通过
     */
    public boolean verify(String captchaKey, String userInput, String encryptionKey) {
        return verify(captchaKey, userInput, null, encryptionKey);
    }

    /**
     * 验证验证码（带运行时配置覆盖和加密密钥）。
     *
     * @param captchaKey     验证码 key
     * @param userInput      用户输入
     * @param overrides      运行时配置覆盖
     * @param encryptionKey  运行时加密密钥
     * @return 是否通过
     */
    public boolean verify(String captchaKey, String userInput,
                          CaptchaProperties overrides, String encryptionKey) {
        if (captchaKey == null || captchaKey.isEmpty()) {
            return false;
        }
        CaptchaProperties props = overrides != null ? overrides : this.properties;
        CaptchaCrypto crypto = createCrypto(props, encryptionKey);

        // 1. 解密 captchaKey
        String payload = crypto.decrypt(captchaKey);
        if (payload == null) {
            return false;
        }

        // 2. 解析: "expireTime|nonce|answer"（answer 可含 |，用 limit=3 分割）
        String[] parts = payload.split("\\|", 3);
        if (parts.length < 3) {
            return false;
        }

        long expireTime;
        try {
            expireTime = Long.parseLong(parts[0]);
        } catch (NumberFormatException e) {
            return false;
        }
        if (System.currentTimeMillis() > expireTime) {
            return false;
        }

        String answer = parts[2];

        // 3. 解密用户输入（若启用加密）
        String decryptedInput = userInput;
        if (crypto.isEnabled() && userInput != null && !userInput.isEmpty()) {
            decryptedInput = crypto.decrypt(userInput);
            if (decryptedInput == null) {
                return false;
            }
        }

        // 4. 交由子类比对
        return doVerify(answer, decryptedInput);
    }

    // ==================== 加密工具 ====================

    /**
     * 根据配置和可选的运行时密钥创建加密实例。
     *
     * @param props         配置属性
     * @param encryptionKey 运行时密钥（null 则使用 props 中的密钥）
     * @return CaptchaCrypto 实例
     */
    protected CaptchaCrypto createCrypto(CaptchaProperties props, String encryptionKey) {
        String type = props != null ? props.getEncryptionType() : "none";
        String key = encryptionKey != null ? encryptionKey
                : (props != null ? props.getEncryptionKey() : null);
        return CaptchaCrypto.create(type, key);
    }

    // ==================== 公共图像工具 ====================

    /** 缓存检测到的 CJK 字体名 */
    private static String detectedCjkFont = null;

    /** 自定义字体缓存 */
    private static Font customFontCache = null;
    private static String customFontPathCache = null;

    /**
     * 自动检测系统中可用的中文字体。
     * 按优先级检测常见中文字体名称，找不到则回退到 SansSerif。
     *
     * @return 可用的中文字体名
     */
    protected static String detectCjkFont() {
        if (detectedCjkFont != null) {
            return detectedCjkFont;
        }
        String[] available = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames();
        Set<String> availableSet = new HashSet<>(Arrays.asList(available));
        // 按优先级排列常见中文字体
        String[] candidates = {
            "Microsoft YaHei", "微软雅黑", "SimHei", "黑体", "SimSun", "宋体",
            "Noto Sans CJK SC", "Noto Sans SC", "WenQuanYi Micro Hei", "文泉驿微米黑",
            "PingFang SC", "Heiti SC", "STHeiti", "Source Han Sans SC",
            "Source Han Sans CN", "Arial Unicode MS", "DejaVu Sans"
        };
        for (String c : candidates) {
            if (availableSet.contains(c)) {
                detectedCjkFont = c;
                return c;
            }
        }
        detectedCjkFont = "SansSerif";
        return detectedCjkFont;
    }

    /**
     * 从文件加载自定义字体（.ttf / .otf / .ttc），带缓存。
     *
     * @param path 字体文件路径
     * @return Font 对象，加载失败返回 null
     */
    protected static Font loadCustomFont(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        if (customFontCache != null && path.equals(customFontPathCache)) {
            return customFontCache;
        }
        try {
            File file = new File(path);
            if (file.exists()) {
                customFontCache = Font.createFont(Font.TRUETYPE_FONT, file);
                customFontPathCache = path;
                return customFontCache;
            }
            // 尝试 classpath
            try (InputStream is = AbstractCaptcha.class.getClassLoader()
                    .getResourceAsStream(path)) {
                if (is != null) {
                    customFontCache = Font.createFont(Font.TRUETYPE_FONT, is);
                    customFontPathCache = path;
                    return customFontCache;
                }
            }
        } catch (Exception e) {
            // 加载失败静默处理
        }
        return null;
    }

    /**
     * 解析字体：优先使用自定义字体文件，其次使用配置的 fontFamily，最后回退到 SansSerif。
     *
     * @param style 字体样式
     * @param size  字体大小
     * @return Font 对象
     */
    protected Font resolveFont(int style, int size) {
        return resolveFont(style, size, false);
    }

    /**
     * 解析字体（可指定是否需要 CJK 支持）。
     *
     * @param style 字体样式
     * @param size  字体大小
     * @param cjk   是否需要 CJK（中文）字体
     * @return Font 对象
     */
    protected Font resolveFont(int style, int size, boolean cjk) {
        // 1. 自定义字体文件优先
        if (properties != null && properties.getFontPath() != null) {
            Font custom = loadCustomFont(properties.getFontPath());
            if (custom != null) {
                return custom.deriveFont(style, (float) size);
            }
        }
        // 2. CJK 字体
        if (cjk) {
            String family = properties != null && properties.getCjkFontFamily() != null
                    ? properties.getCjkFontFamily() : detectCjkFont();
            return new Font(family, style, size);
        }
        // 3. 普通字体
        String family = properties != null && properties.getFontFamily() != null
                ? properties.getFontFamily() : "SansSerif";
        return new Font(family, style, size);
    }

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
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int len = text.length();
        int slot = width / (len + 1);

        int minFs = props.getMinFontSize() > 0 ? props.getMinFontSize() : height / 2;
        int maxFs = props.getMaxFontSize() > 0 ? props.getMaxFontSize() : (int) (height * 0.7);
        if (minFs > maxFs) {
            int tmp = minFs; minFs = maxFs; maxFs = tmp;
        }
        int maxRot = props.getMaxRotationDegree();

        for (int i = 0; i < len; i++) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setColor(randomColor(random));
                int fontSize = minFs + (maxFs > minFs ? random.nextInt(maxFs - minFs) : 0);
                Font font = resolveFont(props.getFontStyle(), fontSize);
                g2.setFont(font);
                FontMetrics fm = g2.getFontMetrics();

                String ch = String.valueOf(text.charAt(i));
                int charWidth = fm.stringWidth(ch);

                // 水平居中于 slot
                int x = slot * i + slot / 2 - charWidth / 2;
                // 垂直居中：使用 FontMetrics 的 ascent/descent 精确计算
                int baselineY = (height + fm.getAscent() - fm.getDescent()) / 2;
                // 添加少量随机偏移
                int y = baselineY + (height > 20 ? random.nextInt(height / 6) - height / 12 : 0);

                double angle = maxRot > 0 ? random.nextInt(maxRot * 2) - maxRot : 0;
                g2.rotate(Math.toRadians(angle), x + charWidth / 2.0, y);
                g2.drawString(ch, x, y);
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
     * 加载自定义背景图。
     * <p>
     * 优先从 {@code backgroundImageBase64} 加载，其次从 {@code backgroundImagePath} 加载（支持文件路径和 classpath 资源）。
     * 图片会被缩放到指定的 width x height。
     *
     * @param width  目标宽度
     * @param height 目标高度
     * @return 背景图，加载失败返回 null
     */
    protected BufferedImage loadBackgroundImage(int width, int height) {
        if (properties == null) {
            return null;
        }

        BufferedImage src = null;

        // 优先从 base64 加载
        String b64 = properties.getBackgroundImageBase64();
        if (b64 != null && !b64.isBlank()) {
            try {
                byte[] bytes = decodeBase64Image(b64);
                src = ImageIO.read(new ByteArrayInputStream(bytes));
            } catch (Exception e) {
                // 加载失败静默处理，后续回退到随机背景
            }
        }

        // 其次从多张背景图列表中随机选择一张
        if (src == null) {
            List<String> images = properties.getBackgroundImages();
            if (images != null && !images.isEmpty()) {
                Random rand = new Random();
                String path = images.get(rand.nextInt(images.size()));
                src = loadImageFromPath(path);
            }
        }

        // 最后从单张背景图路径加载
        if (src == null) {
            String path = properties.getBackgroundImagePath();
            if (path != null && !path.isBlank()) {
                src = loadImageFromPath(path);
            }
        }

        if (src == null) {
            return null;
        }

        // 缩放到目标尺寸
        BufferedImage scaled = createImage(width, height);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return scaled;
    }

    /**
     * 从文件路径或 classpath 路径加载图片。
     *
     * @param path 文件路径或 classpath 资源路径
     * @return 图片，加载失败返回 null
     */
    private BufferedImage loadImageFromPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            File file = new File(path);
            if (file.exists()) {
                return ImageIO.read(file);
            }
            // 尝试 classpath
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
                if (is != null) {
                    return ImageIO.read(is);
                }
            }
        } catch (Exception e) {
            // 加载失败静默处理
        }
        return null;
    }

    /**
     * 在图片上绘制文字水印和图片水印。
     * <p>
     * 根据 {@link CaptchaProperties} 的水印配置，在图片上叠加文字水印和/或图片水印。
     * 应在验证码内容绘制完成后调用。
     *
     * @param image 目标图片
     */
    protected void applyWatermark(BufferedImage image) {
        if (properties == null) {
            return;
        }
        int width = image.getWidth();
        int height = image.getHeight();

        // 文字水印
        String text = properties.getWatermarkText();
        if (text != null && !text.isBlank()) {
            drawTextWatermark(image, text, width, height);
        }

        // 图片水印
        String imgB64 = properties.getWatermarkImageBase64();
        if (imgB64 != null && !imgB64.isBlank()) {
            drawImageWatermark(image, imgB64, width, height);
        }
    }

    /**
     * 绘制文字水印。
     */
    private void drawTextWatermark(BufferedImage image, String text, int width, int height) {
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            String family = properties.getWatermarkFontFamily() != null
                    ? properties.getWatermarkFontFamily() : "Arial";
            int fontSize = properties.getWatermarkFontSize();
            g.setFont(new Font(family, Font.PLAIN, fontSize));

            // 计算文字尺寸
            FontMetrics fm = g.getFontMetrics();
            int textWidth = fm.stringWidth(text);
            int textHeight = fm.getHeight();

            // 计算位置
            int[] pos = calculateWatermarkPosition(properties.getWatermarkPosition(),
                    width, height, textWidth, textHeight);
            int x = pos[0];
            int y = pos[1] + fm.getAscent();

            // 旋转
            if (properties.getWatermarkRotation() != 0) {
                g.rotate(Math.toRadians(properties.getWatermarkRotation()), x + textWidth / 2.0, y - textHeight / 2.0);
            }

            // 设置半透明颜色
            int colorVal = properties.getWatermarkColor();
            g.setColor(new Color(colorVal, true));
            g.drawString(text, x, y);
        } finally {
            g.dispose();
        }
    }

    /**
     * 绘制图片水印。
     */
    private void drawImageWatermark(BufferedImage image, String imgBase64, int width, int height) {
        try {
            byte[] bytes = decodeBase64Image(imgBase64);
            BufferedImage watermark = ImageIO.read(new ByteArrayInputStream(bytes));
            if (watermark == null) {
                return;
            }

            // 缩放水印
            int wmWidth = (int) (width * properties.getWatermarkScale());
            int wmHeight = (int) (wmWidth * ((double) watermark.getHeight() / watermark.getWidth()));
            if (wmWidth <= 0 || wmHeight <= 0) {
                return;
            }

            // 计算位置（居中）
            int x = (width - wmWidth) / 2;
            int y = (height - wmHeight) / 2;

            Graphics2D g = image.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setComposite(java.awt.AlphaComposite.getInstance(
                        java.awt.AlphaComposite.SRC_OVER, properties.getWatermarkOpacity()));
                g.drawImage(watermark, x, y, wmWidth, wmHeight, null);
            } finally {
                g.dispose();
            }
        } catch (Exception e) {
            // 图片水印加载失败静默处理
        }
    }

    /**
     * 根据位置字符串计算水印坐标。
     */
    private int[] calculateWatermarkPosition(String position, int canvasW, int canvasH, int contentW, int contentH) {
        int margin = 5;
        if (position == null) position = "bottom-right";
        switch (position.toLowerCase()) {
            case "top-left":
                return new int[]{margin, margin};
            case "top-right":
                return new int[]{canvasW - contentW - margin, margin};
            case "bottom-left":
                return new int[]{margin, canvasH - contentH - margin};
            case "center":
                return new int[]{(canvasW - contentW) / 2, (canvasH - contentH) / 2};
            case "bottom-right":
            default:
                return new int[]{canvasW - contentW - margin, canvasH - contentH - margin};
        }
    }

    /**
     * 解码 base64 图片数据（支持带前缀和不带前缀两种格式）。
     */
    private byte[] decodeBase64Image(String base64) {
        String data = base64;
        if (data.startsWith("data:")) {
            int idx = data.indexOf(",");
            if (idx > 0) {
                data = data.substring(idx + 1);
            }
        }
        return Base64.getDecoder().decode(data);
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

    public CaptchaProperties getProperties() {
        return properties;
    }

    public void setProperties(CaptchaProperties properties) {
        this.properties = properties;
    }
}
