package com.weacsoft.jaravel.vendor.wechat.user;

import com.weacsoft.jaravel.vendor.wechat.response.WeChatResponse;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 微信用户信息（{@code user/info} / {@code user/getall} 响应）。
 * <p>
 * 字段对齐官方响应（snake_case）：openid、nickname、sex、language、city、province、
 * country、headimgurl、subscribe、subscribe_time、remark、groupid、tagid_list、unionid（条件）。
 *
 * <p>
 * unionid 仅在"微信开放平台绑定"等场景返回，可能缺省——读取侧统一用
 * {@link #getUnionId()}（不存在返回 null，而非抛异常）。
 *
 * @author weacsoft
 */
public final class WeChatUser {

    public static final int SEX_FEMALE = 1;
    public static final int SEX_MALE = 2;
    public static final int SEX_UNKNOWN = 0;

    private final String openid;
    private final String nickname;
    private final int sex;
    private final String language;
    private final String city;
    private final String province;
    private final String country;
    private final String headimgUrl;
    private final boolean subscribed;
    private final long subscribeTime;
    private final String remark;
    private final int groupId;
    private final List<Integer> tagIds;
    private final int tagIdListSize;
    private final String unionId;
    private final Map<String, Object> raw;

    private WeChatUser(Map<String, Object> raw) {
        this.raw = raw;
        this.openid = str(raw.get("openid"));
        this.nickname = str(raw.get("nickname"));
        this.sex = intVal(raw.get("sex"), SEX_UNKNOWN);
        this.language = str(raw.get("language"));
        this.city = str(raw.get("city"));
        this.province = str(raw.get("province"));
        this.country = str(raw.get("country"));
        this.headimgUrl = str(raw.get("headimgurl"));
        this.subscribed = Boolean.TRUE.equals(raw.get("subscribe"));
        this.subscribeTime = longVal(raw.get("subscribe_time"));
        this.remark = str(raw.get("remark"));
        this.groupId = intVal(raw.get("groupid"), 0);
        @SuppressWarnings("unchecked")
        List<Integer> ids = raw.get("tagid_list") instanceof List<?> list ? (List<Integer>) list : List.of();
        this.tagIds = List.copyOf(ids);
        this.tagIdListSize = intVal(raw.get("tagid_list_size"), this.tagIds.size());
        this.unionId = str(raw.get("unionid"));
    }

    /**
     * 从原始响应构建。
     *
     * @param raw 微信响应体（应已先剥离 errcode/errmsg，或直接为 user 对象节点）
     * @return 用户对象
     */
    public static WeChatUser from(Map<String, Object> raw) {
        return new WeChatUser(Objects.requireNonNull(raw, "raw"));
    }

    /**
     * 便捷入口：从 {@link WeChatResponse} 构建（自动剥离状态字段）。
     *
     * @param resp 微信响应
     * @return 用户对象
     */
    public static WeChatUser fromResponse(WeChatResponse resp) {
        return new WeChatUser(resp.raw());
    }

    /**
     * @return openid
     */
    public String getOpenId() {
        return openid;
    }

    public String getNickname() {
        return nickname;
    }

    /**
     * @return 性别：1 女 / 2 男 / 0 未知
     */
    public int getSex() {
        return sex;
    }

    public String getLanguage() {
        return language;
    }

    public String getCity() {
        return city;
    }

    public String getProvince() {
        return province;
    }

    public String getCountry() {
        return country;
    }

    public String getHeadimgUrl() {
        return headimgUrl;
    }

    /**
     * @return 是否关注
     */
    public boolean isSubscribed() {
        return subscribed;
    }

    /**
     * @return 关注时间（秒级时间戳）
     */
    public long getSubscribeTime() {
        return subscribeTime;
    }

    /**
     * @return 开发者备注
     */
    public String getRemark() {
        return remark;
    }

    /**
     * @return 分组 id（0 表示默认分组）
     */
    public int getGroupId() {
        return groupId;
    }

    /**
     * @return 标签 id 列表（只读）
     */
    public List<Integer> getTagIds() {
        return tagIds;
    }

    public int getTagIdListSize() {
        return tagIdListSize;
    }

    /**
     * @return 开放平台 unionid；未绑定时为 null
     */
    public String getUnionId() {
        return unionId;
    }

    /**
     * @return 原始响应节点（只读）
     */
    public Map<String, Object> getRaw() {
        return raw;
    }

    private static String str(Object value) {
        return value instanceof String s ? s : (value != null ? String.valueOf(value) : null);
    }

    private static int intVal(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s && !s.isEmpty()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                // 落到默认值
            }
        }
        return defaultValue;
    }

    private static long longVal(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String s && !s.isEmpty()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                // 落到默认值
            }
        }
        return 0L;
    }

    @Override
    public String toString() {
        return "WeChatUser{openid=" + openid + ", nickname=" + nickname
                + ", subscribed=" + subscribed
                + (unionId != null ? ", unionid=" + unionId : "") + "}";
    }
}
