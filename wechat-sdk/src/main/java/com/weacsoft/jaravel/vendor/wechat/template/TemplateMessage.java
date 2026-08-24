package com.weacsoft.jaravel.vendor.wechat.template;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务号模板消息（{@code POST cgi-bin/message/template/send}）。
 * <p>
 * 注意：服务号模板消息正在被<b>订阅通知</b>（{@link SubscriptionNotice}）替代，
 * 官方已逐步关闭模板消息接口，新建业务优先用订阅通知；本类保留存量兼容。
 *
 * <pre>
 * TemplateMessage message = new TemplateMessage()
 *     .toUser(openid)
 *     .templateId("TEMPLATE_ID")
 *     .url("https://example.com/page")                 // 可选
 *     .miniProgram(new MiniProgramTarget("wx123", "pages/index/index")) // 可选
 *     .data("first", TemplateDataItem.ofValue("欢迎"))
 *     .data("keyword1", TemplateDataItem.colored("张三", "#173177"))
 *     .data("remark", TemplateDataItem.ofValue("欢迎体验"));
 * </pre>
 *
 * @author weacsoft
 */
public final class TemplateMessage {

    private String toUser;
    private String templateId;
    private String clientMsgId;
    private String url;
    private MiniProgramTarget miniProgram;
    private final Map<String, TemplateDataItem> data = new LinkedHashMap<>();

    /**
     * fluent：接收者 openid（必填）。
     */
    public TemplateMessage toUser(String openid) {
        this.toUser = openid;
        return this;
    }

    /**
     * fluent：模板 id（必填）。
     */
    public TemplateMessage templateId(String templateId) {
        this.templateId = templateId;
        return this;
    }

    /**
     * fluent：客户回传消息 id，用于标识交互场景（可选）。
     */
    public TemplateMessage clientMsgId(String clientMsgId) {
        this.clientMsgId = clientMsgId;
        return this;
    }

    /**
     * fluent：用户点击模板消息后跳转的链接（可选）。
     */
    public TemplateMessage url(String url) {
        this.url = url;
        return this;
    }

    /**
     * fluent：点击跳转到小程序（可选，与 url 二选一）。
     */
    public TemplateMessage miniProgram(MiniProgramTarget miniProgram) {
        this.miniProgram = miniProgram;
        return this;
    }

    /**
     * fluent：添加一个数据项（键为模板变量名）。
     */
    public TemplateMessage data(String key, TemplateDataItem item) {
        if (key == null || key.isEmpty() || item == null) {
            throw new IllegalArgumentException("模板数据键和值均不能为空");
        }
        this.data.put(key, item);
        return this;
    }

    /**
     * fluent：添加一个数据项（仅值）。
     */
    public TemplateMessage data(String key, String value) {
        return data(key, TemplateDataItem.ofValue(value));
    }

    /**
     * 序列化为发送请求体；必填校验在此完成（快速失败）。
     *
     * @throws IllegalArgumentException 缺少 toUser/templateId/data 时
     */
    public Map<String, Object> toJsonBody() {
        if (toUser == null || toUser.isEmpty()) {
            throw new IllegalArgumentException("TemplateMessage 缺少 toUser（openid）");
        }
        if (templateId == null || templateId.isEmpty()) {
            throw new IllegalArgumentException("TemplateMessage 缺少 templateId");
        }
        if (data.isEmpty()) {
            throw new IllegalArgumentException("TemplateMessage data 不能为空（至少 1 个模板变量）");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("touser", toUser);
        body.put("template_id", templateId);
        if (clientMsgId != null && !clientMsgId.isEmpty()) {
            body.put("client_msg_id", clientMsgId);
        }
        Map<String, Object> dataBody = new LinkedHashMap<>(data.size());
        data.forEach((key, item) -> dataBody.put(key, item.toWire()));
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
        return "TemplateMessage{toUser=" + toUser + ", templateId=" + templateId
                + ", dataKeys=" + data.keySet() + "}";
    }
}
