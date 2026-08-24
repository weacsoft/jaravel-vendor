package com.weacsoft.jaravel.vendor.wechat.message;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 图片消息（{@code msgtype=image}）。
 * <p>
 * 支持客服消息发送与被动回复。media_id 通过素材上传接口获得。
 *
 * @author weacsoft
 */
public final class Image extends Message {

    private final String mediaId;

    /**
     * @param mediaId 图片媒体 id（必填，非空）
     * @throws IllegalArgumentException mediaId 为空时
     */
    public Image(String mediaId) {
        requireNonEmpty(mediaId, "mediaId");
        this.mediaId = mediaId;
    }

    @Override
    public String getType() {
        return "image";
    }

    /**
     * @return 媒体 id
     */
    public String getMediaId() {
        return mediaId;
    }

    @Override
    protected Map<String, Object> payload() {
        return Map.of("media_id", mediaId);
    }

    @Override
    public Map<String, Object> toXmlArray() {
        return Map.of("Image", Map.of("MediaId", mediaId));
    }

    @Override
    public String toString() {
        return "Image{mediaId=" + mediaId + "}";
    }
}
