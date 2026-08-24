package com.weacsoft.jaravel.vendor.wechat.mini;

import java.util.Map;

/**
 * 小程序 code2session 结果（{@code GET /sns/jscode2session} 响应）。
 * <p>
 * 包含：openid（本次登录用户）、session_key（会话密钥）、unionid（若绑定开放平台）。
 *
 * <p>
 * <b>安全提示（官方规范）</b>：
 * <ul>
 *   <li>{@code session_key} 用于解密手机号、支付参数等敏感数据，<b>严禁下发到客户端</b>，
 *       只应保存在服务端</li>
 *   <li>{@code code} 为一次性凭证，多次使用会报错</li>
 * </ul>
 *
 * @author weacsoft
 */
public final class MiniProgramSession {

    private final String openid;
    private final String sessionKey;
    private final String unionId;

    private MiniProgramSession(String openid, String sessionKey, String unionId) {
        this.openid = openid;
        this.sessionKey = sessionKey;
        this.unionId = unionId;
    }

    /**
     * 从 jscode2session 成功响应构建。
     * <p>
     * 官方成功响应：{@code {openid, session_key, unionid?}}。
     * {@code errcode/errmsg}（失败）形态由服务层在调用前抛出 {@code WechatApiException}，
     * 不会走到本方法；此处仍做防御性必填校验。
     *
     * @param raw 原始响应（应已剥离 errcode/errmsg）
     * @return 会话对象
     * @throws IllegalArgumentException 缺少 openid 或 session_key 时
     */
    public static MiniProgramSession from(Map<String, Object> raw) {
        Object openid = raw.get("openid");
        Object sessionKey = raw.get("session_key");
        if (openid == null || sessionKey == null) {
            throw new IllegalArgumentException(
                    "jscode2session 响应缺少 openid 或 session_key（可能传入的是失败响应）: " + raw);
        }
        String unionId = raw.get("unionid") instanceof String s ? s : null;
        return new MiniProgramSession(String.valueOf(openid), String.valueOf(sessionKey), unionId);
    }

    /**
     * @return 用户 openid
     */
    public String getOpenId() {
        return openid;
    }

    /**
     * @return 会话密钥（服务端保存，勿下发）
     */
    public String getSessionKey() {
        return sessionKey;
    }

    /**
     * @return 开放平台 unionid（未绑定时为 null）
     */
    public String getUnionId() {
        return unionId;
    }

    @Override
    public String toString() {
        return "MiniProgramSession{openid=" + openid
                + (unionId != null ? ", unionid=" + unionId : "") + "}";
    }
}
