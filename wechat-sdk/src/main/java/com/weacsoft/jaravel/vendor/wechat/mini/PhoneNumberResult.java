package com.weacsoft.jaravel.vendor.wechat.mini;

import java.util.Map;

/**
 * 小程序手机号解密结果（{@code POST /php/customerService/message} 之外的
 * 手机号能力，对应 {@code /cgi-bin/user/getphonenumber} 风格响应）。
 * <p>
 * wire 字段（解密后）：phone_info.{phoneNumber, purePhoneNumber, countryCode}、
 * openid、unionid（条件）、watermark.{appid, timestamp}。
 *
 * @author weacsoft
 */
public final class PhoneNumberResult {

    private final String phone;
    private final String purePhone;
    private final String countryCode;
    private final String openid;
    private final String unionId;
    private final String watermarkAppId;
    private final Long watermarkTimestamp;

    private PhoneNumberResult(String phone, String purePhone, String countryCode,
                              String openid, String unionId, String watermarkAppId,
                              Long watermarkTimestamp) {
        this.phone = phone;
        this.purePhone = purePhone;
        this.countryCode = countryCode;
        this.openid = openid;
        this.unionId = unionId;
        this.watermarkAppId = watermarkAppId;
        this.watermarkTimestamp = watermarkTimestamp;
    }

    /**
     * 从解密后的原始响应构建。
     *
     * @param raw 原始响应（应含 phone_info 节点）
     * @return 手机号对象
     * @throws IllegalArgumentException 缺少 phone_info 时
     */
    public static PhoneNumberResult from(Map<String, Object> raw) {
        Object phoneInfoRaw = raw.get("phone_info");
        if (!(phoneInfoRaw instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("手机号响应缺少 phone_info 节点: " + raw);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> phoneInfo = (Map<String, Object>) phoneInfoRaw;
        String phone = str(phoneInfo.get("phoneNumber"));
        String purePhone = str(phoneInfo.get("purePhoneNumber"));
        String countryCode = str(phoneInfo.get("countryCode"));
        String openid = str(raw.get("openid"));
        String unionId = str(raw.get("unionid"));
        String watermarkAppId = null;
        Long watermarkTimestamp = null;
        Object watermarkRaw = raw.get("watermark");
        if (watermarkRaw instanceof Map<?, ?> wm) {
            @SuppressWarnings("unchecked")
            Map<String, Object> watermark = (Map<String, Object>) wm;
            watermarkAppId = str(watermark.get("appid"));
            Object ts = watermark.get("timestamp");
            watermarkTimestamp = ts instanceof Number n ? n.longValue() : null;
        }
        return new PhoneNumberResult(phone, purePhone, countryCode, openid, unionId,
                watermarkAppId, watermarkTimestamp);
    }

    /**
     * @return 完整手机号（如 8613800000000）
     */
    public String getPhone() {
        return phone;
    }

    /**
     * @return 纯手机号（去掉国际区号）
     */
    public String getPurePhone() {
        return purePhone;
    }

    /**
     * @return 国际区号（如 86）
     */
    public String getCountryCode() {
        return countryCode;
    }

    public String getOpenid() {
        return openid;
    }

    public String getUnionId() {
        return unionId;
    }

    public String getWatermarkAppId() {
        return watermarkAppId;
    }

    public Long getWatermarkTimestamp() {
        return watermarkTimestamp;
    }

    private static String str(Object value) {
        return value instanceof String s ? s : (value != null ? String.valueOf(value) : null);
    }

    @Override
    public String toString() {
        return "PhoneNumberResult{phone=" + (phone != null ? "****" + tail(phone) : null)
                + ", countryCode=" + countryCode + "}";
    }

    private static String tail(String s) {
        return s.length() <= 4 ? "***" : s.substring(s.length() - 4);
    }
}
