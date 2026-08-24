package com.weacsoft.jaravel.vendor.wechat.message;

import java.util.Map;

/**
 * 公众号图文消息（{@code msgtype=mpnews}，点击跳转到图文消息页面，以 media_id 引用草稿）。
 * <p>
 * 仅支持客服消息发送，不支持被动回复。图文条数限制 1 条以内。
 * <p>
 * 注意：官方正在灰度弃用 mpnews（草稿完成后此类型不再支持），
 * 新建场景请改用 {@link MpNewsArticle}（发布接口产生的 article_id）。
 *
 * @author weacsoft
 */
public final class MpNews extends Message {

    private final String mediaId;

    /**
     * @param mediaId 图文素材 id（必填，通过素材/草稿接口获得）
     * @throws IllegalArgumentException mediaId 为空时
     */
    public MpNews(String mediaId) {
        requireNonEmpty(mediaId, "mediaId");
        this.mediaId = mediaId;
    }

    @Override
    public String getType() {
        return "mpnews";
    }

    /**
     * @return 图文素材 id
     */
    public String getMediaId() {
        return mediaId;
    }

    @Override
    protected Map<String, Object> payload() {
        return Map.of("media_id", mediaId);
    }

    @Override
    public String toString() {
        return "MpNews{mediaId=" + mediaId + "}";
    }
}
