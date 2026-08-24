package com.weacsoft.jaravel.vendor.wechat.server;

import java.util.Map;

/**
 * 接收的文本消息（{@code MsgType=text}）。
 *
 * @author weacsoft
 */
public final class TextMessage extends ServerMessage {

    private final String content;

    private TextMessage(Map<String, Object> map) {
        fillCommon(map);
        this.content = text(map.get("Content"));
    }

    /**
     * @param map 解析后的 XML 节点表
     * @return 文本消息
     */
    public static TextMessage from(Map<String, Object> map) {
        return new TextMessage(map);
    }

    /**
     * @return 文本内容
     */
    public String getContent() {
        return content;
    }

    @Override
    public String toString() {
        return "TextMessage{content=" + content + ", from=" + fromUserName + "}";
    }
}
