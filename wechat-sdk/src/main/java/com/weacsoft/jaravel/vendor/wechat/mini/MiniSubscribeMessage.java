package com.weacsoft.jaravel.vendor.wechat.mini;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 小程序订阅消息（{@code POST cgi-bin/message/subscribe/send} 的请求体）。
 * <p>
 * 与公众号订阅通知（{@link com.weacsoft.jaravel.vendor.wechat.template.SubscriptionNotice}）
 * 数据模型不同：小程序订阅消息的 data 为 {@code {key: {value}}}（无颜色字段），
 * 且有 {@code miniprogram_state}（formal/trial/developer）与 {@code lang} 字段。
 *
 * <pre>
 * MiniSubscribeMessage msg = new MiniSubscribeMessage()
 *     .touser(openid)
 *     .templateId("SUB_TEMPLATE_ID")
 *     .page("pages/result/index")          // 可选
 *     .state("formal")                       // 可选，缺省 formal
 *     .lang("zh_CN")                        // 可选，缺省 zh_CN
 *     .data("thing1", TemplateDataItem.ofValue("已发货"));
 * </pre>
 *
 * @author weacsoft
 */
public final class MiniSubscribeMessage {

    /** 小程序运行状态：正式版 */
    public static final String STATE_FORMAL = "formal";
    /** 小程序运行状态：试验版 */
    public static final String STATE_TRIAL = "trial";
    /** 小程序运行状态：开发版 */
    public static final String STATE_DEVELOPER = "developer";

    /** 数据项（wire 形态 {@code {value}}） */
    public static class DataItem {
        private final String value;

        public DataItem(String value) {
            if (value == null || value.isEmpty()) {
                throw new IllegalArgumentException("订阅消息数据值不能为空");
            }
            this.value = value;
        }

        public static DataItem of(String value) {
            return new DataItem(value);
        }

        public String getValue() {
            return value;
        }

        public Map<String, Object> toWire() {
            return Map.of("value", value);
        }
    }

    private String touser;
    private String templateId;
    private String page;
    private String miniprogramState = STATE_FORMAL;
    private String lang = "zh_CN";
    private final Map<String, DataItem> data = new LinkedHashMap<>();

    /**
     * fluent：接收者 openid（必填，须先通过订阅接口获得一次性授权）。
     */
    public MiniSubscribeMessage touser(String openid) {
        this.touser = openid;
        return this;
    }

    /**
     * fluent：模板 id（必填）。
     */
    public MiniSubscribeMessage templateId(String templateId) {
        this.templateId = templateId;
        return this;
    }

    /**
     * fluent：跳转页面（可选）。
     */
    public MiniSubscribeMessage page(String page) {
        this.page = page;
        return this;
    }

    /**
     * fluent：小程序版本（formal/trial/developer；默认 formal）。
     */
    public MiniSubscribeMessage state(String miniprogramState) {
        this.miniprogramState = miniprogramState;
        return this;
    }

    /**
     * fluent：语言（默认 zh_CN）。
     */
    public MiniSubscribeMessage lang(String lang) {
        this.lang = lang;
        return this;
    }

    /**
     * fluent：追加一个数据项。
     */
    public MiniSubscribeMessage data(String key, DataItem item) {
        if (key == null || key.isEmpty() || item == null) {
            throw new IllegalArgumentException("订阅消息数据键和值均不能为空");
        }
        this.data.put(key, item);
        return this;
    }

    /**
     * fluent：追加一个数据项（仅值）。
     */
    public MiniSubscribeMessage data(String key, String value) {
        return data(key, DataItem.of(value));
    }

    /**
     * 序列化为发送请求体；必填校验在此完成（快速失败）。
     *
     * @throws IllegalArgumentException 缺少 touser/templateId/data 时
     */
    public Map<String, Object> toJsonBody() {
        if (touser == null || touser.isEmpty()) {
            throw new IllegalArgumentException("MiniSubscribeMessage 缺少 touser（openid）");
        }
        if (templateId == null || templateId.isEmpty()) {
            throw new IllegalArgumentException("MiniSubscribeMessage 缺少 templateId");
        }
        if (data.isEmpty()) {
            throw new IllegalArgumentException("MiniSubscribeMessage data 不能为空");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("touser", touser);
        body.put("template_id", templateId);
        if (page != null && !page.isEmpty()) {
            body.put("page", page);
        }
        if (miniprogramState != null && !miniprogramState.isEmpty()) {
            body.put("miniprogram_state", miniprogramState);
        }
        if (lang != null && !lang.isEmpty()) {
            body.put("lang", lang);
        }
        Map<String, Object> dataBody = new LinkedHashMap<>(data.size());
        data.forEach((key, item) -> dataBody.put(key, item.toWire()));
        body.put("data", dataBody);
        return body;
    }

    @Override
    public String toString() {
        return "MiniSubscribeMessage{touser=" + touser + ", templateId=" + templateId
                + ", dataKeys=" + data.keySet() + "}";
    }
}
