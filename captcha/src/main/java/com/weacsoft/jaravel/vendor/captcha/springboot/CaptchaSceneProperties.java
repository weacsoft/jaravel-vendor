package com.weacsoft.jaravel.vendor.captcha.springboot;

/**
 * 验证码「场景」配置。
 * <p>
 * <b>设计动机（安全边界）</b>：早期实现允许前端通过查询参数直接指定
 * {@code length / width / height / tolerance / noise / clickTargetCount} 等生成参数，
 * 攻击者可以把 {@code tolerance} 调到极大、把 {@code clickTargetCount} 调到 1，
 * 从而把验证码难度降为 0。这些参数属于<b>安全 / 校验域</b>，必须由后端下发。
 * <p>
 * 因此改为「场景白名单」模型（参考 anji-plus/captcha 的 CaptchaConfig 与 CaptchaVO 分离思路）：
 * <ul>
 *   <li>后端在 {@code jaravel.captcha.scenes.<name>.*} 中<b>预先声明</b>若干命名场景；</li>
 *   <li>前端只能传一个场景名（如 {@code scene=login}），无法传任何具体数值；</li>
 *   <li>场景名不在白名单内时，直接回落到全局默认配置，不报错也不放宽难度。</li>
 * </ul>
 * <p>
 * 本类所有字段均为<b>包装类型且默认为 null</b>，null 表示「继承全局配置」，
 * 只有显式配置的字段才会覆盖，避免整份配置被替换导致其它字段被重置。
 *
 * <pre>
 * jaravel:
 *   captcha:
 *     click-target-count: 3          # 全局默认
 *     scenes:
 *       login:                       # 登录场景：更严格
 *         tolerance: 3.0
 *         interference-level: 4
 *       register:                    # 注册场景：点选 6 个字
 *         click-target-count: 6
 *         click-decoy-count: 4
 *       comment:                     # 评论场景：更宽松
 *         length: 4
 *         interference-level: 2
 * </pre>
 *
 * @see CaptchaSceneRegistry
 */
public class CaptchaSceneProperties {

    /** 图片宽度（像素），null=继承全局 */
    private Integer width;

    /** 图片高度（像素），null=继承全局 */
    private Integer height;

    /** 字符长度（数字 / 算术验证码），null=继承全局 */
    private Integer length;

    /** 过期秒数，null=继承全局 */
    private Long expireSeconds;

    /** 是否区分大小写，null=继承全局 */
    private Boolean caseSensitive;

    /** 滑动 / 旋转容差，null=继承全局 */
    private Double tolerance;

    /** 噪点数量，null=继承全局 */
    private Integer noise;

    /** 干扰线数量，null=继承全局 */
    private Integer interfereLines;

    /** 干扰强度级别 1~5，null=继承全局 */
    private Integer interferenceLevel;

    /** 字符最大旋转角度，null=继承全局 */
    private Integer maxRotationDegree;

    /** 自定义字符集，null=继承全局 */
    private String charSet;

    /** 点选验证码目标数量，null=继承全局 */
    private Integer clickTargetCount;

    /** 点选验证码干扰项数量，null=继承全局 */
    private Integer clickDecoyCount;

    /** 是否启用轨迹验证，null=继承全局 */
    private Boolean trajectoryEnabled;

    /** 文字水印内容，null=继承全局 */
    private String watermarkText;

    /** 场景说明（仅用于文档 / vendor:publish 展示，不参与生成） */
    private String description;

    /**
     * 将本场景的非空字段应用到给定的核心配置对象上（原地修改）。
     * <p>
     * 调用方应先对全局配置执行
     * {@link com.weacsoft.jaravel.vendor.captcha.CaptchaProperties#copy()}，
     * 再把副本传入本方法，以保证「未声明的字段继承全局配置」。
     *
     * @param target 待覆盖的核心配置副本，不可为 null
     */
    public void applyTo(com.weacsoft.jaravel.vendor.captcha.CaptchaProperties target) {
        if (target == null) {
            return;
        }
        if (width != null) target.setWidth(width);
        if (height != null) target.setHeight(height);
        if (length != null) target.setLength(length);
        if (expireSeconds != null) target.setExpireSeconds(expireSeconds);
        if (caseSensitive != null) target.setCaseSensitive(caseSensitive);
        if (tolerance != null) target.setTolerance(tolerance);
        if (noise != null) target.setNoiseCount(noise);
        if (interfereLines != null) target.setInterfereCount(interfereLines);
        if (interferenceLevel != null) target.setInterferenceLevel(interferenceLevel);
        if (maxRotationDegree != null) target.setMaxRotationDegree(maxRotationDegree);
        if (charSet != null) target.setCharSet(charSet);
        if (clickTargetCount != null) target.setClickTargetCount(clickTargetCount);
        if (clickDecoyCount != null) target.setClickDecoyCount(clickDecoyCount);
        if (trajectoryEnabled != null) target.setTrajectoryEnabled(trajectoryEnabled);
        if (watermarkText != null) target.setWatermarkText(watermarkText);
    }

    // ==================== getter / setter ====================

    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }

    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }

    public Integer getLength() { return length; }
    public void setLength(Integer length) { this.length = length; }

    public Long getExpireSeconds() { return expireSeconds; }
    public void setExpireSeconds(Long expireSeconds) { this.expireSeconds = expireSeconds; }

    public Boolean getCaseSensitive() { return caseSensitive; }
    public void setCaseSensitive(Boolean caseSensitive) { this.caseSensitive = caseSensitive; }

    public Double getTolerance() { return tolerance; }
    public void setTolerance(Double tolerance) { this.tolerance = tolerance; }

    public Integer getNoise() { return noise; }
    public void setNoise(Integer noise) { this.noise = noise; }

    public Integer getInterfereLines() { return interfereLines; }
    public void setInterfereLines(Integer interfereLines) { this.interfereLines = interfereLines; }

    public Integer getInterferenceLevel() { return interferenceLevel; }
    public void setInterferenceLevel(Integer interferenceLevel) { this.interferenceLevel = interferenceLevel; }

    public Integer getMaxRotationDegree() { return maxRotationDegree; }
    public void setMaxRotationDegree(Integer maxRotationDegree) { this.maxRotationDegree = maxRotationDegree; }

    public String getCharSet() { return charSet; }
    public void setCharSet(String charSet) { this.charSet = charSet; }

    public Integer getClickTargetCount() { return clickTargetCount; }
    public void setClickTargetCount(Integer clickTargetCount) { this.clickTargetCount = clickTargetCount; }

    public Integer getClickDecoyCount() { return clickDecoyCount; }
    public void setClickDecoyCount(Integer clickDecoyCount) { this.clickDecoyCount = clickDecoyCount; }

    public Boolean getTrajectoryEnabled() { return trajectoryEnabled; }
    public void setTrajectoryEnabled(Boolean trajectoryEnabled) { this.trajectoryEnabled = trajectoryEnabled; }

    public String getWatermarkText() { return watermarkText; }
    public void setWatermarkText(String watermarkText) { this.watermarkText = watermarkText; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
