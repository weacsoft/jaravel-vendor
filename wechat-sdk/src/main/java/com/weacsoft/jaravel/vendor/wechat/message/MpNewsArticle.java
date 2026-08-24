package com.weacsoft.jaravel.vendor.wechat.message;

import java.util.Map;

/**
 * 发布图文消息（{@code msgtype=mpnewsarticle}，点击跳转到通过「发布」系列接口得到的图文）。
 * <p>
 * 仅支持客服消息发送，不支持被动回复。官方推荐的图文消息类型
 * （替代正在灰度弃用的 {@link MpNews}）。
 *
 * @author weacsoft
 */
public final class MpNewsArticle extends Message {

    private final String articleId;

    /**
     * @param articleId 发布文章 id（必填，「发布」系列接口返回）
     * @throws IllegalArgumentException articleId 为空时
     */
    public MpNewsArticle(String articleId) {
        requireNonEmpty(articleId, "articleId");
        this.articleId = articleId;
    }

    @Override
    public String getType() {
        return "mpnewsarticle";
    }

    /**
     * @return 发布文章 id
     */
    public String getArticleId() {
        return articleId;
    }

    @Override
    protected Map<String, Object> payload() {
        return Map.of("article_id", articleId);
    }

    @Override
    public String toString() {
        return "MpNewsArticle{articleId=" + articleId + "}";
    }
}
