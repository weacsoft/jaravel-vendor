package com.weacsoft.jaravel.vendor.wechat.oauth;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 公众号网页授权用户（EasyWeChat 兼容会话值）。
 * <p>
 * 对齐 EasyWeChat 的 {@code User}（{@code id = openid} + {@code raw}）：
 * PHP 侧 {@code $raw_user->getId()} 即为 openid（manage8.0 的
 * {@code WechatService::getUserData($raw_user->getId())} / {@code UserWechatOfficialAccount::where('open_id', ...)}）。
 *
 * <h3>会话契约</h3>
 * <ul>
 *   <li>会话键：{@code easywechat.oauth_user.{account}}（{@link WeChatOAuth#sessionKey}）</li>
 *   <li>值：本对象（{@code WeChatOAuth.saveToSession} 写入）</li>
 *   <li>业务侧（{@code wechat} Auth guard）从该键读取，用 {@link #getId()} 取 openid，
 *       再映射到自己的用户表并 {@code Auth.guard("web").login(...)}</li>
 * </ul>
 *
 * 字段来源：
 * <ul>
 *   <li>{@code openid}/{@code accessToken}/{@code unionId?}/{@code scope?} —— {@code sns/oauth2/access_token}</li>
 *   <li>{@code nickname}/{@code headimgUrl}/{@code unionId(补全)} —— {@code sns/userinfo}（仅 {@code snsapi_userinfo} 范围）</li>
 * </ul>
 *
 * 本类不可变、可序列化（会话存储）。
 *
 * @author weacsoft
 */
public final class WeChatOAuthUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String openid;
    private final String unionId;
    private final String nickname;
    private final String headimgUrl;
    private final String accessToken;
    private final String scope;
    private final Map<String, Object> raw;

    private WeChatOAuthUser(String openid, String unionId, String nickname, String headimgUrl,
                            String accessToken, String scope, Map<String, Object> raw) {
        this.openid = openid;
        this.unionId = unionId;
        this.nickname = nickname;
        this.headimgUrl = headimgUrl;
        this.accessToken = accessToken;
        this.scope = scope;
        this.raw = raw;
    }

    /**
     * 从 {@code sns/oauth2/access_token} 响应（及可选 {@code sns/userinfo} 响应）构建。
     *
     * @param token    access_token 响应（需含 {@code openid}）
     * @param profile  userinfo 响应（可 null；snsapi_base 范围下为 null）
     * @return 用户对象
     */
    public static WeChatOAuthUser from(Map<String, Object> token, Map<String, Object> profile) {
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.putAll(token);
        if (profile != null) {
            for (Map.Entry<String, Object> e : profile.entrySet()) {
                merged.putIfAbsent(e.getKey(), e.getValue());
            }
        }
        String openid = asString(merged.get("openid"));
        if (openid == null || openid.isEmpty()) {
            throw new IllegalStateException("微信 OAuth 响应缺少 openid: " + merged.keySet());
        }
        return new WeChatOAuthUser(
                openid,
                asString(merged.get("unionid")),
                asString(merged.get("nickname")),
                asString(merged.get("headimgurl")),
                asString(merged.get("access_token")),
                asString(merged.get("scope")),
                Collections.unmodifiableMap(new LinkedHashMap<>(merged)));
    }

    /**
     * 从会话中读出的原始值还原（防御性）：
     * <ul>
     *   <li>{@code WeChatOAuthUser} 原样返回</li>
     *   <li>{@code Map}（序列化产物）按 token 响应还原（openid 必须存在）</li>
     * </ul>
     *
     * @param stored 会话值
     * @return 用户对象；stored 为 null 时返回 null
     */
    public static WeChatOAuthUser fromStored(Object stored) {
        if (stored == null) {
            return null;
        }
        if (stored instanceof WeChatOAuthUser user) {
            return user;
        }
        if (stored instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = (Map<String, Object>) map;
            if (raw.get("openid") == null) {
                return null;
            }
            return WeChatOAuthUser.from(raw, null);
        }
        return null;
    }

    /**
     * @return 用户 id —— <b>openid</b>（EasyWeChat {@code User.id} 语义，PHP ${@code $raw_user->getId()} 对齐）
     */
    public String getId() {
        return openid;
    }

    /**
     * @return 用户 openid（与 {@link #getId()} 等价）
     */
    public String getOpenId() {
        return openid;
    }

    /**
     * @return 开放平台 unionid；未绑定或 snsapi_base 范围下可能为 null
     */
    public String getUnionId() {
        return unionId;
    }

    /**
     * @return 昵称；仅 {@code snsapi_userinfo} 范围
     */
    public String getNickname() {
        return nickname;
    }

    /**
     * @return 头像 URL；仅 {@code snsapi_userinfo} 范围
     */
    public String getHeadimgUrl() {
        return headimgUrl;
    }

    /**
     * @return 网页授权 access_token（用于 sns/* 接口，与公众号 access_token 不同体系）
     */
    public String getAccessToken() {
        return accessToken;
    }

    /**
     * @return 实际授权范围字符串
     */
    public String getScope() {
        return scope;
    }

    /**
     * @return openid 非空校验（构造器已保证）
     */
    public boolean isUsable() {
        return openid != null && !openid.isEmpty();
    }

    /**
     * @return 原始合并响应（只读）
     */
    public Map<String, Object> getRaw() {
        return raw;
    }

    private static String asString(Object v) {
        return (v != null) ? String.valueOf(v) : null;
    }

    @Override
    public String toString() {
        return "WeChatOAuthUser{openid=" + openid
                + (unionId != null ? ", unionid=" + unionId : "")
                + (nickname != null ? ", nickname=" + nickname : "")
                + "}";
    }
}
