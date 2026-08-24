package com.weacsoft.jaravel.vendor.wechat.server;

import java.util.Map;

/**
 * 接收的图片消息（{@code MsgType=image}）。
 * <p>
 * {@code picUrl} 为系统生成的链接；{@code mediaId} 可调用获取临时素材接口拉取原图。
 *
 * @author weacsoft
 */
public final class ImageMessage extends ServerMessage {

    private final String picUrl;
    private final String mediaId;

    private ImageMessage(Map<String, Object> map) {
        fillCommon(map);
        this.picUrl = text(map.get("PicUrl"));
        this.mediaId = text(map.get("MediaId"));
    }

    /**
     * @param map 解析后的 XML 节点表
     * @return 图片消息
     */
    public static ImageMessage from(Map<String, Object> map) {
        return new ImageMessage(map);
    }

    public String getPicUrl() {
        return picUrl;
    }

    /**
     * @return 图片媒体 id（可拉取原图）
     */
    public String getMediaId() {
        return mediaId;
    }

    @Override
    public String toString() {
        return "ImageMessage{mediaId=" + mediaId + "}";
    }
}
