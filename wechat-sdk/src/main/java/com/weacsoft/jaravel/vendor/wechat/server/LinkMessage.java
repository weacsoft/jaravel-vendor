package com.weacsoft.jaravel.vendor.wechat.server;

import java.util.Map;

/**
 * 接收的链接消息（{@code MsgType=link}）。
 *
 * @author weacsoft
 */
public final class LinkMessage extends ServerMessage {

    private final String title;
    private final String description;
    private final String url;

    private LinkMessage(Map<String, Object> map) {
        fillCommon(map);
        this.title = text(map.get("Title"));
        this.description = text(map.get("Description"));
        this.url = text(map.get("Url"));
    }

    /**
     * @param map 解析后的 XML 节点表
     * @return 链接消息
     */
    public static LinkMessage from(Map<String, Object> map) {
        return new LinkMessage(map);
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return "LinkMessage{title=" + title + ", url=" + url + "}";
    }
}
