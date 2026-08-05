package com.weacsoft.jaravel.vendor.captcha;

import java.util.HashMap;
import java.util.Map;

/**
 * 验证码生成结果。
 * <p>
 * 封装一次验证码生成产生的全部信息：base64 图片、captchaKey（无状态自包含）、过期时间以及额外数据。
 * 该对象会被返回给调用方用于 JSON 序列化下发到前端，因此答案本身不包含在此对象中
 * （答案被加密编码在 captchaKey 中，仅服务端可解密）。
 */
public class CaptchaResult {

    /** 验证码标识（无状态模式下为加密的自包含令牌，包含答案+过期时间+随机数） */
    private String captchaKey;

    /**
     * 合并凭证（单一凭据参数）。
     * <p>
     * 由 {@code type + "." + captchaKey} 组成，前端只需提交这一个 {@code key}
     * 与用户输入即可完成校验，无需再单独传 {@code type}。
     * 校验时服务端从 {@code key} 中解析出类型并自动分发到对应生成器，
     * 因此业务方可以把验证码与其他表单字段放在<b>同一次请求</b>里一起提交、一起校验，
     * 避免「先单独校验验证码、再提交业务数据」带来的二次提交安全漏洞。
     *
     * @see com.weacsoft.jaravel.vendor.captcha.CaptchaManager#verify(String, String)
     */
    private String key;

    /** 验证码类型 */
    private String type;

    /** base64 编码的图片（带 {@code data:image/png;base64,} 前缀） */
    private String imageBase64;

    /** 过期时间戳（毫秒） */
    private long expireTime;

    /** 额外数据（如滑动验证码的滑块图、缺口位置等） */
    private Map<String, Object> extra;

    /**
     * 加密类型（下发前端用于加密用户输入，仅 AES 模式有意义）。
     * <p>
     * 当服务端启用了应用密钥兜底（{@code jaravel.key}）时，
     * 该值可能与业务静态配置不同，前端应优先使用本字段而非硬编码的默认值。
     */
    private String encType;

    /**
     * 加密密钥（下发前端用于加密用户输入，仅 AES 模式有意义）。
     * <p>
     * 为避免前后端密钥不一致导致解密失败，服务端把<b>实际生效</b>的密钥下发给前端，
     * 前端用它加密用户输入后随 {@code key} 一起提交。
     */
    private String encKey;

    public CaptchaResult() {
        this.extra = new HashMap<>();
    }

    public CaptchaResult(String captchaKey, String type, String imageBase64,
                         long expireTime, Map<String, Object> extra) {
        this.captchaKey = captchaKey;
        this.type = type;
        this.imageBase64 = imageBase64;
        this.expireTime = expireTime;
        this.extra = extra != null ? extra : new HashMap<>();
    }

    public String getCaptchaKey() {
        return captchaKey;
    }

    public void setCaptchaKey(String captchaKey) {
        this.captchaKey = captchaKey;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getImageBase64() {
        return imageBase64;
    }

    public void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
    }

    public long getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(long expireTime) {
        this.expireTime = expireTime;
    }

    public String getEncType() {
        return encType;
    }

    public void setEncType(String encType) {
        this.encType = encType;
    }

    public String getEncKey() {
        return encKey;
    }

    public void setEncKey(String encKey) {
        this.encKey = encKey;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, Object> extra) {
        this.extra = extra;
    }

    /**
     * 将结果转为 {@link Map}，便于 JSON 序列化。
     *
     * @return 包含全部可对外字段的 Map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("key", key);
        map.put("captchaKey", captchaKey);
        map.put("type", type);
        map.put("imageBase64", imageBase64);
        map.put("expireTime", expireTime);
        map.put("encType", encType);
        map.put("encKey", encKey);
        map.put("extra", extra);
        return map;
    }

    @Override
    public String toString() {
        return "CaptchaResult{"
                + "key='" + key + '\''
                + ", captchaKey='" + captchaKey + '\''
                + ", type='" + type + '\''
                + ", expireTime=" + expireTime
                + ", hasImage=" + (imageBase64 != null)
                + ", extra=" + extra
                + '}';
    }
}
