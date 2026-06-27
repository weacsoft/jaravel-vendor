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
 * </pre>
 */
public class CaptchaProperties {

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

    /**
     * 创建一份默认配置。
     *
     * @return 使用默认值的配置实例
     */
    public static CaptchaProperties createDefault() {
        return new CaptchaProperties();
    }
}
