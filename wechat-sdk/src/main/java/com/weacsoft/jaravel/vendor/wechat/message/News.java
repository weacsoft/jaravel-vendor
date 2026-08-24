package com.weacsoft.jaravel.vendor.wechat.message;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 图文消息（{@code msgtype=news}，点击跳转到外链）。
 * <p>
 * 支持客服消息发送与被动回复。
 * <p>
 * <b>条数约束</b>：客服消息（JSON 发送）官方限制 ≤1 条（超出微信返回 45008）；
 * 被动回复（XML）官方允许 ≤8 条。本类在 JSON 序列化时强制 ≤1，
 * 被动回复不做条数限制（由微信平台自行约束）。
 *
 * @author weacsoft
 */
public final class News extends Message {

    private static final int CUSTOM_MESSAGE_LIMIT = 1;

    private final List<NewsItem> items;

    /**
     * @param items 图文条目（必填，至少 1 条）
     * @throws IllegalArgumentException items 为空时
     */
    public News(List<NewsItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("属性 \"items\" 不能为空（至少 1 条图文）");
        }
        this.items = List.copyOf(items);
    }

    /**
     * 便捷构造：单条图文。
     *
     * @param item 图文条目
     */
    public News(NewsItem item) {
        this(List.of(item));
    }

    @Override
    public String getType() {
        return "news";
    }

    /**
     * @return 图文条目（只读列表）
     */
    public List<NewsItem> getItems() {
        return items;
    }

    @Override
    protected void checkRequired() {
        super.checkRequired();
        if (items.size() > CUSTOM_MESSAGE_LIMIT) {
            throw new IllegalArgumentException("news 客服消息图文条数限制在 "
                    + CUSTOM_MESSAGE_LIMIT + " 条以内（当前 " + items.size() + "，微信将返回 45008）；"
                    + "多图文请使用被动回复");
        }
    }

    @Override
    protected Map<String, Object> payload() {
        List<Object> articles = new ArrayList<>(items.size());
        for (NewsItem item : items) {
            articles.add(item.toJsonArray());
        }
        return Map.of("articles", articles);
    }

    @Override
    public Map<String, Object> toXmlArray() {
        List<Map<String, Object>> nodeItems = new ArrayList<>(items.size());
        for (NewsItem item : items) {
            nodeItems.add(item.toXmlArray());
        }
        Map<String, Object> xml = new LinkedHashMap<>();
        xml.put("ArticleCount", items.size());
        xml.put("Articles", Map.of("item", nodeItems));
        return xml;
    }

    @Override
    public String toString() {
        return "News{items=" + items.size() + "}";
    }
}
