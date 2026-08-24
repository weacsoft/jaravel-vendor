package com.weacsoft.jaravel.vendor.wechat.message;

import java.util.Map;

/**
 * 文本消息（{@code msgtype=text}）。
 * <p>
 * 支持客服消息发送与被动回复。文本内容支持插入跳小程序的文字链
 * （需小程序与公众号绑定，见官方文档）。
 *
 * @author weacsoft
 */
public final class Text extends Message {

    private final String content;

    /**
     * @param content 文本内容（必填，非空）
     * @throws IllegalArgumentException content 为空时
     */
    public Text(String content) {
        requireNonEmpty(content, "content");
        this.content = content;
    }

    @Override
    public String getType() {
        return "text";
    }

    /**
     * @return 文本内容
     */
    public String getContent() {
        return content;
    }

    @Override
    protected Map<String, Object> payload() {
        return Map.of("content", content);
    }

    @Override
    public Map<String, Object> toXmlArray() {
        return Map.of("Content", content);
    }

    @Override
    public String toString() {
        return "Text{content=" + content + "}";
    }
}
