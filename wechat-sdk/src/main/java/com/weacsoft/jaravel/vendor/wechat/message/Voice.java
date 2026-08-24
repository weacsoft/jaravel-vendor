package com.weacsoft.jaravel.vendor.wechat.message;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 语音消息（{@code msgtype=voice}）。
 * <p>
 * 支持客服消息发送与被动回复。media_id 通过素材上传接口获得。
 *
 * @author weacsoft
 */
public final class Voice extends Message {

    private final String mediaId;

    /**
     * @param mediaId 语音媒体 id（必填，非空）
     * @throws IllegalArgumentException mediaId 为空时
     */
    public Voice(String mediaId) {
        requireNonEmpty(mediaId, "mediaId");
        this.mediaId = mediaId;
    }

    @Override
    public String getType() {
        return "voice";
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
        return Map.of("Voice", Map.of("MediaId", mediaId));
    }

    @Override
    public String toString() {
        return "Voice{mediaId=" + mediaId + "}";
    }
}
