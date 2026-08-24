package com.weacsoft.jaravel.vendor.wechat.message;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 视频消息（{@code msgtype=video}）。
 * <p>
 * 支持客服消息发送与被动回复。标题与描述可选。
 *
 * @author weacsoft
 */
public final class Video extends Message {

    private final String mediaId;
    private final String thumbMediaId;
    private final String title;
    private final String description;

    /**
     * @param mediaId      视频媒体 id（必填）
     * @param thumbMediaId 缩略图媒体 id（必填）
     * @param title        视频标题（可空）
     * @param description  视频描述（可空）
     * @throws IllegalArgumentException mediaId/thumbMediaId 为空时
     */
    public Video(String mediaId, String thumbMediaId, String title, String description) {
        requireNonEmpty(mediaId, "mediaId");
        requireNonEmpty(thumbMediaId, "thumbMediaId");
        this.mediaId = mediaId;
        this.thumbMediaId = thumbMediaId;
        this.title = title;
        this.description = description;
    }

    @Override
    public String getType() {
        return "video";
    }

    public String getMediaId() {
        return mediaId;
    }

    public String getThumbMediaId() {
        return thumbMediaId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    @Override
    protected Map<String, Object> payload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("media_id", mediaId);
        p.put("thumb_media_id", thumbMediaId);
        putIfPresent(p, "title", title);
        putIfPresent(p, "description", description);
        return p;
    }

    @Override
    public Map<String, Object> toXmlArray() {
        Map<String, Object> video = new LinkedHashMap<>();
        video.put("MediaId", mediaId);
        putIfPresent(video, "Title", title);
        putIfPresent(video, "Description", description);
        video.put("ThumbMediaId", thumbMediaId);
        return Map.of("Video", video);
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isEmpty()) {
            map.put(key, value);
        }
    }

    @Override
    public String toString() {
        return "Video{mediaId=" + mediaId + ", thumbMediaId=" + thumbMediaId + "}";
    }
}
