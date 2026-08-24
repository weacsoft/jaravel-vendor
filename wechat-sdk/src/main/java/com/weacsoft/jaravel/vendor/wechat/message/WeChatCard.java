package com.weacsoft.jaravel.vendor.wechat.message;

import java.util.Map;

/**
 * 卡券消息（{@code msgtype=wxcard}）：向用户下发微信卡券（优惠券/代金券等）。
 * <p>
 * 仅支持客服消息发送，不支持被动回复。卡券本身通过微信卡券 API 创建，
 * 本类只负责以 card_id 引用并下发。
 *
 * @author weacsoft
 */
public final class WeChatCard extends Message {

    private final String cardId;

    /**
     * @param cardId 卡券 id（必填）
     * @throws IllegalArgumentException cardId 为空时
     */
    public WeChatCard(String cardId) {
        requireNonEmpty(cardId, "cardId");
        this.cardId = cardId;
    }

    @Override
    public String getType() {
        return "wxcard";
    }

    /**
     * @return 卡券 id
     */
    public String getCardId() {
        return cardId;
    }

    @Override
    protected Map<String, Object> payload() {
        return Map.of("card_id", cardId);
    }

    @Override
    public String toString() {
        return "WeChatCard{cardId=" + cardId + "}";
    }
}
