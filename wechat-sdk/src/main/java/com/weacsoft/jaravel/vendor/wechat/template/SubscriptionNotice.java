package com.weacsoft.jaravel.vendor.wechat.template;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 公众号/小程序「订阅通知」（{@code POST cgi-bin/message/template/subscribe}）。
 * <p>
 * 这是官方主推的消息类型（对应接收侧 {@code subscribe_msg_sent_event} /
 * {@code subscribe_msg_change_event} 两个事件）。
 * <pre>
 * SubscriptionNotice notice = new SubscriptionNotice()
 *     .toUser(openid)
 *     .templateId("SUB_TEMPLATE_ID")
 *     .title("订单状态提醒")                       // 必填
 *     .scene("订单更新")                          // 可选
 *     .url("https://example.com/order/123")      // 可选
 *     .miniProgram(new MiniProgramTarget("wx123", "pages/order/detail?id=123")) // 可选
 *     .content("您的订单已发货", "#173177");      // data.content（必填，官方模板字段固定为 content）
 * </pre>
 *
 * @author weacsoft
 */
public final class SubscriptionNotice {

    private String toUser;
    private String templateId;
    private String title;
    private String scene;
    private String url;
    private MiniProgramTarget miniProgram;
    private TemplateDataItem content;

    /**
     * fluent：接收者 openid（必填，须先订阅通知权限）。
     */
    public SubscriptionNotice toUser(String openid) {
        this.toUser = openid;
        return this;
    }

    /**
     * fluent：订阅通知模板 id（必填）。
     */
    public SubscriptionNotice templateId(String templateId) {
        this.templateId = templateId;
        return this;
    }

    /**
     * fluent：标题（必填，展示在消息顶部）。
     */
    public SubscriptionNotice title(String title) {
        this.title = title;
        return this;
    }

    /**
     * fluent：场景说明（可选，官方要求说明使用场景）。
     */
    public SubscriptionNotice scene(String scene) {
        this.scene = scene;
        return this;
    }

    /**
     * fluent：跳转链接（可选，与小程序目标二选一）。
     */
    public SubscriptionNotice url(String url) {
        this.url = url;
        return this;
    }

    /**
     * fluent：跳转小程序（可选，与 url 二选一）。
     */
    public SubscriptionNotice miniProgram(MiniProgramTarget miniProgram) {
        this.miniProgram = miniProgram;
        return this;
    }

    /**
     * fluent：正文内容 data.content（必填）。
     */
    public SubscriptionNotice content(String value) {
        this.content = TemplateDataItem.ofValue(value);
        return this;
    }

    /**
     * fluent：正文内容（带颜色）。
     */
    public SubscriptionNotice content(String value, String color) {
        this.content = TemplateDataItem.colored(value, color);
        return this;
    }

    /**
     * 序列化为发送请求体；必填校验在此完成（快速失败）。
     *
     * @throws IllegalArgumentException 缺少必填项时
     */
    public Map<String, Object> toJsonBody() {
        if (toUser == null || toUser.isEmpty()) {
            throw new IllegalArgumentException("SubscriptionNotice 缺少 toUser（openid）");
        }
        if (templateId == null || templateId.isEmpty()) {
            throw new IllegalArgumentException("SubscriptionNotice 缺少 templateId");
        }
        if (title == null || title.isEmpty()) {
            throw new IllegalArgumentException("SubscriptionNotice 缺少 title");
        }
        if (content == null) {
            throw new IllegalArgumentException("SubscriptionNotice 缺少 content（data.content）");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("touser", toUser);
        body.put("template_id", templateId);
        if (scene != null && !scene.isEmpty()) {
            body.put("scene", scene);
        }
        body.put("title", title);
        Map<String, Object> dataBody = new LinkedHashMap<>();
        dataBody.put("content", content.toWire());
        body.put("data", dataBody);
        if (url != null && !url.isEmpty()) {
            body.put("url", url);
        }
        if (miniProgram != null) {
            body.put("miniprogram", miniProgram.toWire());
        }
        return body;
    }

    @Override
    public String toString() {
        return "SubscriptionNotice{toUser=" + toUser + ", templateId=" + templateId + ", title=" + title + "}";
    }
}
