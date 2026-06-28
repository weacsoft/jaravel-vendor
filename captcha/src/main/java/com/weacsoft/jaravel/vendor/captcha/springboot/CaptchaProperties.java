package com.weacsoft.jaravel.vendor.captcha.springboot;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SpringBoot 配置属性。
 * <p>
 * 在 application.yml 中通过 {@code jaravel.captcha.*} 配置：
 * <pre>
 * jaravel:
 *   captcha:
 *     enabled: true
 *     width: 160
 *     height: 50
 *     length: 4
 *     expire-seconds: 300
 *     case-sensitive: false
 *     tolerance: 5.0
 *     noise: 50
 *     interfere-lines: 30
 *     # 视觉配置
 *     font-family: Arial
 *     font-style: 1
 *     min-font-size: 28
 *     max-font-size: 36
 *     max-rotation-degree: 30
 *     char-set: null
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
@ConfigurationProperties(prefix = "jaravel.captcha")
public class CaptchaProperties {

    /** 是否启用自动装配 */
    private boolean enabled = true;

    /** 图片宽度 */
    private int width = 160;

    /** 图片高度 */
    private int height = 50;

    /** 验证码字符长度（数字/字母验证码） */
    private int length = 4;

    /** 过期时间（秒） */
    private long expireSeconds = 300;

    /** 是否区分大小写 */
    private boolean caseSensitive = false;

    /** 滑动/旋转验证的容差范围 */
    private double tolerance = 5.0;

    /** 噪点数量 */
    private int noise = 50;

    /** 干扰线数量 */
    private int interfereLines = 30;

    // 视觉配置
    private String fontFamily = "Arial";
    private int fontStyle = 1;
    private int minFontSize = 0;
    private int maxFontSize = 0;
    private int maxRotationDegree = 30;
    private String charSet = null;
    private boolean arcInterfere = true;
    private int arcInterfereCount = 5;

    // 轨迹验证配置
    private boolean trajectoryEnabled = true;
    private int minTrajectoryPoints = 5;
    private long minTrajectoryDurationMs = 500;
    private long maxTrajectoryDurationMs = 30000;
    private double maxJumpDistance = 80;

    // 自定义背景图配置
    private String backgroundImagePath = null;
    private String backgroundImageBase64 = null;

    // 水印配置
    private String watermarkText = null;
    private String watermarkFontFamily = "Arial";
    private int watermarkFontSize = 12;
    private int watermarkColor = 0x80666666;
    private String watermarkPosition = "bottom-right";
    private int watermarkRotation = 0;
    private String watermarkImageBase64 = null;
    private float watermarkOpacity = 0.3f;
    private float watermarkScale = 0.2f;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }

    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    public int getLength() { return length; }
    public void setLength(int length) { this.length = length; }

    public long getExpireSeconds() { return expireSeconds; }
    public void setExpireSeconds(long expireSeconds) { this.expireSeconds = expireSeconds; }

    public boolean isCaseSensitive() { return caseSensitive; }
    public void setCaseSensitive(boolean caseSensitive) { this.caseSensitive = caseSensitive; }

    public double getTolerance() { return tolerance; }
    public void setTolerance(double tolerance) { this.tolerance = tolerance; }

    public int getNoise() { return noise; }
    public void setNoise(int noise) { this.noise = noise; }

    public int getInterfereLines() { return interfereLines; }
    public void setInterfereLines(int interfereLines) { this.interfereLines = interfereLines; }

    public String getFontFamily() { return fontFamily; }
    public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }

    public int getFontStyle() { return fontStyle; }
    public void setFontStyle(int fontStyle) { this.fontStyle = fontStyle; }

    public int getMinFontSize() { return minFontSize; }
    public void setMinFontSize(int minFontSize) { this.minFontSize = minFontSize; }

    public int getMaxFontSize() { return maxFontSize; }
    public void setMaxFontSize(int maxFontSize) { this.maxFontSize = maxFontSize; }

    public int getMaxRotationDegree() { return maxRotationDegree; }
    public void setMaxRotationDegree(int maxRotationDegree) { this.maxRotationDegree = maxRotationDegree; }

    public String getCharSet() { return charSet; }
    public void setCharSet(String charSet) { this.charSet = charSet; }

    public boolean isArcInterfere() { return arcInterfere; }
    public void setArcInterfere(boolean arcInterfere) { this.arcInterfere = arcInterfere; }

    public int getArcInterfereCount() { return arcInterfereCount; }
    public void setArcInterfereCount(int arcInterfereCount) { this.arcInterfereCount = arcInterfereCount; }

    public boolean isTrajectoryEnabled() { return trajectoryEnabled; }
    public void setTrajectoryEnabled(boolean trajectoryEnabled) { this.trajectoryEnabled = trajectoryEnabled; }

    public int getMinTrajectoryPoints() { return minTrajectoryPoints; }
    public void setMinTrajectoryPoints(int minTrajectoryPoints) { this.minTrajectoryPoints = minTrajectoryPoints; }

    public long getMinTrajectoryDurationMs() { return minTrajectoryDurationMs; }
    public void setMinTrajectoryDurationMs(long minTrajectoryDurationMs) { this.minTrajectoryDurationMs = minTrajectoryDurationMs; }

    public long getMaxTrajectoryDurationMs() { return maxTrajectoryDurationMs; }
    public void setMaxTrajectoryDurationMs(long maxTrajectoryDurationMs) { this.maxTrajectoryDurationMs = maxTrajectoryDurationMs; }

    public double getMaxJumpDistance() { return maxJumpDistance; }
    public void setMaxJumpDistance(double maxJumpDistance) { this.maxJumpDistance = maxJumpDistance; }

    public String getBackgroundImagePath() { return backgroundImagePath; }
    public void setBackgroundImagePath(String backgroundImagePath) { this.backgroundImagePath = backgroundImagePath; }

    public String getBackgroundImageBase64() { return backgroundImageBase64; }
    public void setBackgroundImageBase64(String backgroundImageBase64) { this.backgroundImageBase64 = backgroundImageBase64; }

    public String getWatermarkText() { return watermarkText; }
    public void setWatermarkText(String watermarkText) { this.watermarkText = watermarkText; }

    public String getWatermarkFontFamily() { return watermarkFontFamily; }
    public void setWatermarkFontFamily(String watermarkFontFamily) { this.watermarkFontFamily = watermarkFontFamily; }

    public int getWatermarkFontSize() { return watermarkFontSize; }
    public void setWatermarkFontSize(int watermarkFontSize) { this.watermarkFontSize = watermarkFontSize; }

    public int getWatermarkColor() { return watermarkColor; }
    public void setWatermarkColor(int watermarkColor) { this.watermarkColor = watermarkColor; }

    public String getWatermarkPosition() { return watermarkPosition; }
    public void setWatermarkPosition(String watermarkPosition) { this.watermarkPosition = watermarkPosition; }

    public int getWatermarkRotation() { return watermarkRotation; }
    public void setWatermarkRotation(int watermarkRotation) { this.watermarkRotation = watermarkRotation; }

    public String getWatermarkImageBase64() { return watermarkImageBase64; }
    public void setWatermarkImageBase64(String watermarkImageBase64) { this.watermarkImageBase64 = watermarkImageBase64; }

    public float getWatermarkOpacity() { return watermarkOpacity; }
    public void setWatermarkOpacity(float watermarkOpacity) { this.watermarkOpacity = watermarkOpacity; }

    public float getWatermarkScale() { return watermarkScale; }
    public void setWatermarkScale(float watermarkScale) { this.watermarkScale = watermarkScale; }

    /**
     * 转为核心层配置对象。
     */
    public com.weacsoft.jaravel.vendor.captcha.CaptchaProperties toCoreProperties() {
        com.weacsoft.jaravel.vendor.captcha.CaptchaProperties core =
                com.weacsoft.jaravel.vendor.captcha.CaptchaProperties.createDefault();
        core.setWidth(width);
        core.setHeight(height);
        core.setLength(length);
        core.setExpireSeconds(expireSeconds);
        core.setCaseSensitive(caseSensitive);
        core.setTolerance(tolerance);
        core.setNoiseCount(noise);
        core.setInterfereCount(interfereLines);
        core.setFontFamily(fontFamily);
        core.setFontStyle(fontStyle);
        core.setMinFontSize(minFontSize);
        core.setMaxFontSize(maxFontSize);
        core.setMaxRotationDegree(maxRotationDegree);
        core.setCharSet(charSet);
        core.setArcInterfere(arcInterfere);
        core.setArcInterfereCount(arcInterfereCount);
        core.setTrajectoryEnabled(trajectoryEnabled);
        core.setMinTrajectoryPoints(minTrajectoryPoints);
        core.setMinTrajectoryDurationMs(minTrajectoryDurationMs);
        core.setMaxTrajectoryDurationMs(maxTrajectoryDurationMs);
        core.setMaxJumpDistance(maxJumpDistance);
        core.setBackgroundImagePath(backgroundImagePath);
        core.setBackgroundImageBase64(backgroundImageBase64);
        core.setWatermarkText(watermarkText);
        core.setWatermarkFontFamily(watermarkFontFamily);
        core.setWatermarkFontSize(watermarkFontSize);
        core.setWatermarkColor(watermarkColor);
        core.setWatermarkPosition(watermarkPosition);
        core.setWatermarkRotation(watermarkRotation);
        core.setWatermarkImageBase64(watermarkImageBase64);
        core.setWatermarkOpacity(watermarkOpacity);
        core.setWatermarkScale(watermarkScale);
        return core;
    }
}
