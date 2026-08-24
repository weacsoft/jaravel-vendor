package com.weacsoft.jaravel.vendor.wechat.message;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 音乐消息（{@code msgtype=music}）。
 * <p>
 * 支持客服消息发送与被动回复。
 * <p>
 * 属性名到 wire 键名的别名对齐官方字段：{@code musicUrl→musicurl}、{@code hqMusicUrl→hqmusicurl}。
 *
 * @author weacsoft
 */
public final class Music extends Message {

    private final String title;
    private final String description;
    private final String musicUrl;
    private final String hqMusicUrl;
    private final String thumbMediaId;

    /**
     * @param title        音乐标题（必填）
     * @param description  音乐描述（必填）
     * @param musicUrl     音乐链接（必填，mp3/midi，≤5MB）
     * @param hqMusicUrl   高质量音乐链接（可空，可跨网段）
     * @param thumbMediaId 缩略图媒体 id（必填）
     * @throws IllegalArgumentException 必填项为空时
     */
    public Music(String title, String description, String musicUrl, String hqMusicUrl, String thumbMediaId) {
        requireNonEmpty(title, "title");
        requireNonEmpty(description, "description");
        requireNonEmpty(musicUrl, "musicUrl");
        requireNonEmpty(thumbMediaId, "thumbMediaId");
        this.title = title;
        this.description = description;
        this.musicUrl = musicUrl;
        this.hqMusicUrl = hqMusicUrl;
        this.thumbMediaId = thumbMediaId;
    }

    @Override
    public String getType() {
        return "music";
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getMusicUrl() {
        return musicUrl;
    }

    public String getHqMusicUrl() {
        return hqMusicUrl;
    }

    public String getThumbMediaId() {
        return thumbMediaId;
    }

    @Override
    protected Map<String, Object> payload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("title", title);
        p.put("description", description);
        p.put("musicurl", musicUrl);
        if (hqMusicUrl != null && !hqMusicUrl.isEmpty()) {
            p.put("hqmusicurl", hqMusicUrl);
        }
        p.put("thumb_media_id", thumbMediaId);
        return p;
    }

    @Override
    public Map<String, Object> toXmlArray() {
        Map<String, Object> music = new LinkedHashMap<>();
        music.put("Title", title);
        music.put("Description", description);
        music.put("MusicUrl", musicUrl);
        if (hqMusicUrl != null && !hqMusicUrl.isEmpty()) {
            music.put("HQMusicUrl", hqMusicUrl);
        }
        music.put("ThumbMediaId", thumbMediaId);
        return Map.of("Music", music);
    }

    @Override
    public String toString() {
        return "Music{title=" + title + "}";
    }
}
