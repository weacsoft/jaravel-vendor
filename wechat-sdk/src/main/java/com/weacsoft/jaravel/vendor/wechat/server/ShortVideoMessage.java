package com.weacsoft.jaravel.vendor.wechat.server;

import java.util.Map;

/**
 * 接收的小视频消息（{@code MsgType=shortvideo}）。
 *
 * @author weacsoft
 */
public final class ShortVideoMessage extends ServerMessage {

    private final String mediaId;
    private final String thumbMediaId;

    private ShortVideoMessage(Map<String, Object> map) {
        fillCommon(map);
        this.mediaId = text(map.get("MediaId"));
        this.thumbMediaId = text(map.get("ThumbMediaId"));
    }

    /**
     * @param map 解析后的 XML 节点表
     * @return 小视频消息
     */
    public static ShortVideoMessage from(Map<String, Object> map) {
        return new ShortVideoMessage(map);
    }

    public String getMediaId() {
        return mediaId;
    }

    public String getThumbMediaId() {
        return thumbMediaId;
    }

    @Override
    public String toString() {
        return "ShortVideoMessage{mediaId=" + mediaId + "}";
    }
}
