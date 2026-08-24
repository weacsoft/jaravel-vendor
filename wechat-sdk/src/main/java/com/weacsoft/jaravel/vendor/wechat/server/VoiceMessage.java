package com.weacsoft.jaravel.vendor.wechat.server;

import java.util.Map;

/**
 * 接收的语音消息（{@code MsgType=voice}）。
 * <p>
 * {@code format} 为 amr 时返回 8K 采样率 amr 语音；
 * {@code mediaId16k} 为 16K 采样率语音媒体 id（语音识别建议使用 16K）。
 *
 * @author weacsoft
 */
public final class VoiceMessage extends ServerMessage {

    private final String mediaId;
    private final String format;
    private final String mediaId16k;

    private VoiceMessage(Map<String, Object> map) {
        fillCommon(map);
        this.mediaId = text(map.get("MediaId"));
        this.format = text(map.get("Format"));
        this.mediaId16k = text(map.get("MediaId16K"));
    }

    /**
     * @param map 解析后的 XML 节点表
     * @return 语音消息
     */
    public static VoiceMessage from(Map<String, Object> map) {
        return new VoiceMessage(map);
    }

    public String getMediaId() {
        return mediaId;
    }

    /**
     * @return 语音格式（amr、speex 等）
     */
    public String getFormat() {
        return format;
    }

    /**
     * @return 16K 采样率语音媒体 id，可空
     */
    public String getMediaId16k() {
        return mediaId16k;
    }

    @Override
    public String toString() {
        return "VoiceMessage{mediaId=" + mediaId + ", format=" + format + "}";
    }
}
