package com.weacsoft.jaravel.vendor.captcha;

/**
 * 验证码配置属性。
 * <p>
 * 核心层不依赖 Spring，因此此处使用普通 Java 类而非
 * {@code @ConfigurationProperties}。SpringBoot 兼容层可在外部继承或包装本类，
 * 通过 {@code jaravel.captcha} 前缀绑定配置后注入。
 *
 * <pre>
 * jaravel:
 *   captcha:
 *     width: 160
 *     height: 50
 *     length: 4
 *     expire-seconds: 300
 *     case-sensitive: false
 *     tolerance: 5.0
 *     interfere-count: 30
 *     noise-count: 50
 *     # 视觉配置
 *     font-family: Arial
 *     font-style: 1          # Font.BOLD=1
 *     min-font-size: 28
 *     max-font-size: 36
 *     max-rotation-degree: 30
 *     char-set: null          # null=默认字符集
 *     arc-interfere: true
 *     arc-interfere-count: 5
 *     # 轨迹验证配置
 *     trajectory-enabled: true
 *     min-trajectory-points: 5
 *     min-trajectory-duration-ms: 500
 *     max-trajectory-duration-ms: 30000
 *     max-jump-distance: 80
 * </pre>
 */
public class CaptchaProperties {

    /**
     * 模块内置的默认加密密钥。
     * <p>
     * 当 {@code jaravel.captcha.encryption-key} 保持此默认值时，
     * 表示业务方未为验证码模块单独配置密钥，此时应回退到
     * core 模块的全局应用密钥 {@code jaravel.key}
     * （见 {@code com.weacsoft.jaravel.vendor.core.crypto.AppKey#resolve}）。
     */
    public static final String DEFAULT_ENCRYPTION_KEY = "jaravel-captcha-default-key";

    // ==================== 基础配置 ====================

    /** 图片宽度（像素） */
    private int width = 160;

    /** 图片高度（像素） */
    private int height = 50;

    /** 验证码字符长度（适用于数字 / 算术验证码） */
    private int length = 4;

    /** 过期秒数，默认 5 分钟 */
    private long expireSeconds = 300;

    /** 是否区分大小写（适用于数字验证码） */
    private boolean caseSensitive = false;

    /** 滑动 / 旋转验证的容差范围（滑动为像素，旋转为角度） */
    private double tolerance = 5.0;

    /** 干扰线数量 */
    private int interfereCount = 30;

    /** 噪点数量 */
    private int noiseCount = 50;

    // ==================== 干扰级别配置 ====================

    /**
     * 干扰强度级别（1~5）。
     * <p>
     * 统一控制噪点、干扰线、弧线干扰的数量倍率：
     * <ul>
     *   <li>1 — 极轻（0.2x）：几乎无干扰</li>
     *   <li>2 — 轻度（0.5x）</li>
     *   <li>3 — 中等（1.0x）：默认</li>
     *   <li>4 — 较强（1.5x）</li>
     *   <li>5 — 极强（2.0x）：干扰最大化</li>
     * </ul>
     * 设置后会覆盖 noiseCount / interfereCount / arcInterfereCount 的原始值。
     */
    private int interferenceLevel = 3;

    // ==================== 视觉配置 ====================

    /** 字体名称（null 表示使用系统默认） */
    private String fontFamily = null;

    /**
     * CJK 字体名称（用于文字点选等需要中文的场景）。
     * null 表示自动检测系统中可用的中文字体。
     */
    private String cjkFontFamily = null;

    /** 自定义字体文件路径（.ttf / .otf / .ttc），null 表示不使用自定义字体文件 */
    private String fontPath = null;

    /** 字体样式：Font.PLAIN=0, Font.BOLD=1, Font.ITALIC=2, Font.BOLD|Font.ITALIC=3 */
    private int fontStyle = 1;

    /** 最小字体大小（0 表示自动按图片高度计算） */
    private int minFontSize = 0;

    /** 最大字体大小（0 表示自动按图片高度计算） */
    private int maxFontSize = 0;

    /** 每个字符最大旋转角度（0~90） */
    private int maxRotationDegree = 30;

    /** 自定义字符集（null 表示使用默认字符集 ABCDEFGHJKMNPQRSTUVWXYZ23456789） */
    private String charSet = null;

    /** 是否添加弧线干扰（比直线更难被 OCR 识别） */
    private boolean arcInterfere = true;

    /** 弧线干扰数量 */
    private int arcInterfereCount = 5;

    // ==================== 轨迹验证配置 ====================

    /** 是否启用轨迹验证（滑动/旋转验证码） */
    private boolean trajectoryEnabled = true;

    /** 轨迹最少点数（低于此值判定为机器行为） */
    private int minTrajectoryPoints = 5;

    /** 轨迹最短持续时间（毫秒，低于此值判定为机器行为） */
    private long minTrajectoryDurationMs = 500;

    /** 轨迹最长持续时间（毫秒，超过此值判定为可疑行为） */
    private long maxTrajectoryDurationMs = 30000;

    /** 相邻轨迹点最大跳变距离（超过此值判定为非连续操作） */
    private double maxJumpDistance = 80;

    // ==================== 自定义背景图配置 ====================

    /** 滑动/旋转验证码的自定义背景图路径（文件路径或 classpath 资源路径），null 表示使用随机生成 */
    private String backgroundImagePath = null;

    /** 自定义背景图的 base64 数据（优先级高于 backgroundImagePath），null 表示不使用 */
    private String backgroundImageBase64 = null;

    /**
     * 多张自定义背景图路径列表（文件路径或 classpath 资源路径）。
     * 每次生成时从中随机选择一张，null 或空表示不使用。
     * 优先级：backgroundImageBase64 > backgroundImages > backgroundImagePath > 随机生成。
     */
    private java.util.List<String> backgroundImages = null;

    // ==================== 点选验证码配置 ====================

    /** 文字点选验证码：需要点选的目标文字数量（默认 3） */
    private int clickTargetCount = 3;

    /** 文字点选验证码：干扰文字数量（默认 3） */
    private int clickDecoyCount = 3;

    // ==================== 加密配置 ====================

    /**
     * 加密类型：none（不加密，纯 Base64）、aes（AES/CBC/PKCS5Padding）、rsa（RSA/OAEP）。
     * 默认 aes。
     */
    private String encryptionType = "aes";

    /**
     * 加密密钥。
     * <ul>
     *   <li>NONE 模式：忽略</li>
     *   <li>AES 模式：对称密钥字符串（SHA-256 哈希后取前 16 字节）</li>
     *   <li>RSA 模式：Base64 公钥 + "|" + Base64 私钥，或仅公钥 / 仅私钥</li>
     * </ul>
     */
    private String encryptionKey = DEFAULT_ENCRYPTION_KEY;

    // ==================== 水印配置 ====================

    /** 文字水印内容（null 表示不添加文字水印） */
    private String watermarkText = null;

    /** 文字水印字体名称 */
    private String watermarkFontFamily = "Arial";

    /** 文字水印字体大小 */
    private int watermarkFontSize = 12;

    /** 文字水印颜色（ARGB 格式，如 0x80808080 表示半透明灰色） */
    private int watermarkColor = 0x80666666;

    /** 文字水印位置：top-left, top-right, bottom-left, bottom-right, center */
    private String watermarkPosition = "bottom-right";

    /** 文字水印旋转角度 */
    private int watermarkRotation = 0;

    /** 图片水印的 base64 数据（null 表示不添加图片水印） */
    private String watermarkImageBase64 = null;

    /** 图片水印透明度（0.0~1.0） */
    private float watermarkOpacity = 0.3f;

    /** 图片水印缩放比例（0.0~1.0，相对于画布宽度） */
    private float watermarkScale = 0.2f;

    // ==================== getter / setter ====================

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }

    public void setExpireSeconds(long expireSeconds) {
        this.expireSeconds = expireSeconds;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    public double getTolerance() {
        return tolerance;
    }

    public void setTolerance(double tolerance) {
        this.tolerance = tolerance;
    }

    public int getInterfereCount() {
        return interfereCount;
    }

    public void setInterfereCount(int interfereCount) {
        this.interfereCount = interfereCount;
    }

    public int getNoiseCount() {
        return noiseCount;
    }

    public void setNoiseCount(int noiseCount) {
        this.noiseCount = noiseCount;
    }

    public String getFontFamily() {
        return fontFamily;
    }

    public void setFontFamily(String fontFamily) {
        this.fontFamily = fontFamily;
    }

    public int getFontStyle() {
        return fontStyle;
    }

    public void setFontStyle(int fontStyle) {
        this.fontStyle = fontStyle;
    }

    public int getMinFontSize() {
        return minFontSize;
    }

    public void setMinFontSize(int minFontSize) {
        this.minFontSize = minFontSize;
    }

    public int getMaxFontSize() {
        return maxFontSize;
    }

    public void setMaxFontSize(int maxFontSize) {
        this.maxFontSize = maxFontSize;
    }

    public int getMaxRotationDegree() {
        return maxRotationDegree;
    }

    public void setMaxRotationDegree(int maxRotationDegree) {
        this.maxRotationDegree = maxRotationDegree;
    }

    public String getCharSet() {
        return charSet;
    }

    public void setCharSet(String charSet) {
        this.charSet = charSet;
    }

    public boolean isArcInterfere() {
        return arcInterfere;
    }

    public void setArcInterfere(boolean arcInterfere) {
        this.arcInterfere = arcInterfere;
    }

    public int getArcInterfereCount() {
        return arcInterfereCount;
    }

    public void setArcInterfereCount(int arcInterfereCount) {
        this.arcInterfereCount = arcInterfereCount;
    }

    public boolean isTrajectoryEnabled() {
        return trajectoryEnabled;
    }

    public void setTrajectoryEnabled(boolean trajectoryEnabled) {
        this.trajectoryEnabled = trajectoryEnabled;
    }

    public int getMinTrajectoryPoints() {
        return minTrajectoryPoints;
    }

    public void setMinTrajectoryPoints(int minTrajectoryPoints) {
        this.minTrajectoryPoints = minTrajectoryPoints;
    }

    public long getMinTrajectoryDurationMs() {
        return minTrajectoryDurationMs;
    }

    public void setMinTrajectoryDurationMs(long minTrajectoryDurationMs) {
        this.minTrajectoryDurationMs = minTrajectoryDurationMs;
    }

    public long getMaxTrajectoryDurationMs() {
        return maxTrajectoryDurationMs;
    }

    public void setMaxTrajectoryDurationMs(long maxTrajectoryDurationMs) {
        this.maxTrajectoryDurationMs = maxTrajectoryDurationMs;
    }

    public double getMaxJumpDistance() {
        return maxJumpDistance;
    }

    public void setMaxJumpDistance(double maxJumpDistance) {
        this.maxJumpDistance = maxJumpDistance;
    }

    public String getBackgroundImagePath() {
        return backgroundImagePath;
    }

    public void setBackgroundImagePath(String backgroundImagePath) {
        this.backgroundImagePath = backgroundImagePath;
    }

    public String getBackgroundImageBase64() {
        return backgroundImageBase64;
    }

    public void setBackgroundImageBase64(String backgroundImageBase64) {
        this.backgroundImageBase64 = backgroundImageBase64;
    }

    public String getWatermarkText() {
        return watermarkText;
    }

    public void setWatermarkText(String watermarkText) {
        this.watermarkText = watermarkText;
    }

    public String getWatermarkFontFamily() {
        return watermarkFontFamily;
    }

    public void setWatermarkFontFamily(String watermarkFontFamily) {
        this.watermarkFontFamily = watermarkFontFamily;
    }

    public int getWatermarkFontSize() {
        return watermarkFontSize;
    }

    public void setWatermarkFontSize(int watermarkFontSize) {
        this.watermarkFontSize = watermarkFontSize;
    }

    public int getWatermarkColor() {
        return watermarkColor;
    }

    public void setWatermarkColor(int watermarkColor) {
        this.watermarkColor = watermarkColor;
    }

    public String getWatermarkPosition() {
        return watermarkPosition;
    }

    public void setWatermarkPosition(String watermarkPosition) {
        this.watermarkPosition = watermarkPosition;
    }

    public int getWatermarkRotation() {
        return watermarkRotation;
    }

    public void setWatermarkRotation(int watermarkRotation) {
        this.watermarkRotation = watermarkRotation;
    }

    public String getWatermarkImageBase64() {
        return watermarkImageBase64;
    }

    public void setWatermarkImageBase64(String watermarkImageBase64) {
        this.watermarkImageBase64 = watermarkImageBase64;
    }

    public float getWatermarkOpacity() {
        return watermarkOpacity;
    }

    public void setWatermarkOpacity(float watermarkOpacity) {
        this.watermarkOpacity = watermarkOpacity;
    }

    public float getWatermarkScale() {
        return watermarkScale;
    }

    public void setWatermarkScale(float watermarkScale) {
        this.watermarkScale = watermarkScale;
    }

    // ==================== 新增字段 getter / setter ====================

    public int getInterferenceLevel() {
        return interferenceLevel;
    }

    public void setInterferenceLevel(int interferenceLevel) {
        this.interferenceLevel = Math.max(1, Math.min(5, interferenceLevel));
    }

    public String getCjkFontFamily() {
        return cjkFontFamily;
    }

    public void setCjkFontFamily(String cjkFontFamily) {
        this.cjkFontFamily = cjkFontFamily;
    }

    public String getFontPath() {
        return fontPath;
    }

    public void setFontPath(String fontPath) {
        this.fontPath = fontPath;
    }

    public java.util.List<String> getBackgroundImages() {
        return backgroundImages;
    }

    public void setBackgroundImages(java.util.List<String> backgroundImages) {
        this.backgroundImages = backgroundImages;
    }

    public int getClickTargetCount() {
        return clickTargetCount;
    }

    public void setClickTargetCount(int clickTargetCount) {
        this.clickTargetCount = Math.max(1, Math.min(8, clickTargetCount));
    }

    public int getClickDecoyCount() {
        return clickDecoyCount;
    }

    public void setClickDecoyCount(int clickDecoyCount) {
        this.clickDecoyCount = Math.max(0, Math.min(10, clickDecoyCount));
    }

    public String getEncryptionType() {
        return encryptionType;
    }

    public void setEncryptionType(String encryptionType) {
        this.encryptionType = encryptionType;
    }

    public String getEncryptionKey() {
        return encryptionKey;
    }

    public void setEncryptionKey(String encryptionKey) {
        this.encryptionKey = encryptionKey;
    }

    // ==================== 干扰级别辅助方法 ====================

    /**
     * 根据干扰级别返回噪点数量。
     */
    public int getEffectiveNoiseCount() {
        return (int) (noiseCount * getInterferenceMultiplier());
    }

    /**
     * 根据干扰级别返回干扰线数量。
     */
    public int getEffectiveInterfereCount() {
        return (int) (interfereCount * getInterferenceMultiplier());
    }

    /**
     * 根据干扰级别返回弧线干扰数量。
     */
    public int getEffectiveArcInterfereCount() {
        if (!arcInterfere) return 0;
        return (int) (arcInterfereCount * getInterferenceMultiplier());
    }

    /**
     * 干扰级别对应的倍率。
     */
    public double getInterferenceMultiplier() {
        switch (interferenceLevel) {
            case 1: return 0.2;
            case 2: return 0.5;
            case 4: return 1.5;
            case 5: return 2.0;
            default: return 1.0; // level 3
        }
    }

    /**
     * 获取有效字符集（charSet 为空时返回默认字符集）。
     */
    public char[] getEffectiveChars() {
        if (charSet != null && !charSet.isEmpty()) {
            return charSet.toCharArray();
        }
        return DEFAULT_CHARS;
    }

    /** 默认字符集（排除易混淆字符 0/O/1/I/L） */
    public static final char[] DEFAULT_CHARS =
            "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

    /**
     * 创建一份默认配置。
     */
    public static CaptchaProperties createDefault() {
        return new CaptchaProperties();
    }

    /**
     * 创建当前配置的副本（浅拷贝，集合字段做新列表拷贝）。
     * <p>
     * 用途：运行时需要"以全局配置为基线，只覆盖个别字段"时，
     * 先 {@code copy()} 再修改，避免 {@link #createDefault()} 造成的
     * 「未显式设置的字段被静默重置为出厂默认值」问题。
     * <p>
     * 典型场景：按业务场景（scene）下发差异化配置：
     * <pre>
     *   CaptchaProperties scene = global.copy();
     *   scene.setClickTargetCount(6);   // 其余字段仍继承全局配置
     * </pre>
     * <p>
     * 注意：本方法直接复制字段值，<b>不经过 setter 的取值范围校验</b>，
     * 因此副本与源对象在语义上完全等价。
     *
     * @return 与当前对象字段值完全一致的新实例
     */
    public CaptchaProperties copy() {
        CaptchaProperties c = new CaptchaProperties();
        // 基础配置
        c.width = this.width;
        c.height = this.height;
        c.length = this.length;
        c.expireSeconds = this.expireSeconds;
        c.caseSensitive = this.caseSensitive;
        c.tolerance = this.tolerance;
        c.interfereCount = this.interfereCount;
        c.noiseCount = this.noiseCount;
        c.interferenceLevel = this.interferenceLevel;
        // 视觉配置
        c.fontFamily = this.fontFamily;
        c.cjkFontFamily = this.cjkFontFamily;
        c.fontPath = this.fontPath;
        c.fontStyle = this.fontStyle;
        c.minFontSize = this.minFontSize;
        c.maxFontSize = this.maxFontSize;
        c.maxRotationDegree = this.maxRotationDegree;
        c.charSet = this.charSet;
        c.arcInterfere = this.arcInterfere;
        c.arcInterfereCount = this.arcInterfereCount;
        // 轨迹验证配置
        c.trajectoryEnabled = this.trajectoryEnabled;
        c.minTrajectoryPoints = this.minTrajectoryPoints;
        c.minTrajectoryDurationMs = this.minTrajectoryDurationMs;
        c.maxTrajectoryDurationMs = this.maxTrajectoryDurationMs;
        c.maxJumpDistance = this.maxJumpDistance;
        // 背景图配置
        c.backgroundImagePath = this.backgroundImagePath;
        c.backgroundImageBase64 = this.backgroundImageBase64;
        c.backgroundImages = (this.backgroundImages == null)
                ? null : new java.util.ArrayList<>(this.backgroundImages);
        // 点选验证码配置
        c.clickTargetCount = this.clickTargetCount;
        c.clickDecoyCount = this.clickDecoyCount;
        // 加密配置
        c.encryptionType = this.encryptionType;
        c.encryptionKey = this.encryptionKey;
        // 水印配置
        c.watermarkText = this.watermarkText;
        c.watermarkFontFamily = this.watermarkFontFamily;
        c.watermarkFontSize = this.watermarkFontSize;
        c.watermarkColor = this.watermarkColor;
        c.watermarkPosition = this.watermarkPosition;
        c.watermarkRotation = this.watermarkRotation;
        c.watermarkImageBase64 = this.watermarkImageBase64;
        c.watermarkOpacity = this.watermarkOpacity;
        c.watermarkScale = this.watermarkScale;
        return c;
    }
}
