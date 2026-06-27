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
        return core;
    }
}
