package com.weacsoft.jaravel.vendor.captcha;

import java.util.HashMap;
import java.util.Map;

/**
 * 验证码生成结果。
 * <p>
 * 封装一次验证码生成产生的全部信息：base64 图片、加密 token、过期时间以及额外数据。
 * 该对象会被返回给调用方用于 JSON 序列化下发到前端，因此答案本身不包含在此对象中
 * （答案仅存于 {@link CaptchaStore}，避免泄露）。
 */
public class CaptchaResult {

    /** 验证码标识 */
    private String captchaKey;

    /** 验证码类型 */
    private String type;

    /** base64 编码的图片（带 {@code data:image/png;base64,} 前缀） */
    private String imageBase64;

    /** 加密 token（包含答案的加密信息，可用于无状态验证） */
    private String token;

    /** 过期时间戳（毫秒） */
    private long expireTime;

    /** 额外数据（如滑动验证码的滑块图、缺口位置等） */
    private Map<String, Object> extra;

    public CaptchaResult() {
        this.extra = new HashMap<>();
    }

    public CaptchaResult(String captchaKey, String type, String imageBase64,
                         String token, long expireTime, Map<String, Object> extra) {
        this.captchaKey = captchaKey;
        this.type = type;
        this.imageBase64 = imageBase64;
        this.token = token;
        this.expireTime = expireTime;
        this.extra = extra != null ? extra : new HashMap<>();
    }

    public String getCaptchaKey() {
        return captchaKey;
    }

    public void setCaptchaKey(String captchaKey) {
        this.captchaKey = captchaKey;
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

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public long getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(long expireTime) {
        this.expireTime = expireTime;
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
        map.put("captchaKey", captchaKey);
        map.put("type", type);
        map.put("imageBase64", imageBase64);
        map.put("token", token);
        map.put("expireTime", expireTime);
        map.put("extra", extra);
        return map;
    }

    @Override
    public String toString() {
        return "CaptchaResult{"
                + "captchaKey='" + captchaKey + '\''
                + ", type='" + type + '\''
                + ", expireTime=" + expireTime
                + ", hasImage=" + (imageBase64 != null)
                + ", hasToken=" + (token != null)
                + ", extra=" + extra
                + '}';
    }
}
