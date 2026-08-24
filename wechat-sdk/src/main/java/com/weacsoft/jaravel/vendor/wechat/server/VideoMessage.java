package com.weacsoft.jaravel.vendor.wechat.server;

import java.util.Map;

/**
 * 接收的视频消息（{@code MsgType=video}）。
 *
 * @author weacsoft
 */
public final class VideoMessage extends ServerMessage {

    private final String mediaId;
    private final String thumbMediaId;

    private VideoMessage(Map<String, Object> map) {
        fillCommon(map);
        this.mediaId = text(map.get("MediaId"));
        this.thumbMediaId = text(map.get("ThumbMediaId"));
    }

    /**
     * @param map 解析后的 XML 节点表
     * @return 视频消息
     */
    public static VideoMessage from(Map<String, Object> map) {
        return new VideoMessage(map);
    }

    public String getMediaId() {
        return mediaId;
    }

    public String getThumbMediaId() {
        return thumbMediaId;
    }

    @Override
    public String toString() {
        return "VideoMessage{mediaId=" + mediaId + "}";
    }
}
