package com.weacsoft.jaravel.vendor.wechat.message;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 图文消息条目（{@code news} 的 article / 被动回复的 item）。
 * <p>
 * wire 字段：{@code title}/{@code description}/{@code picurl}/{@code url}；
 * 被动回复 XML 为 {@code Title/Description/PicUrl/Url}。
 *
 * @author weacsoft
 */
public final class NewsItem {

    private final String title;
    private final String description;
    private final String picUrl;
    private final String url;

    /**
     * @param title       图文标题（必填）
     * @param description 图文消息描述（可空）
     * @param picUrl      图片链接，支持 jpg/png（必填）
     * @param url         点击图文消息跳转的链接（必填）
     * @throws IllegalArgumentException 必填项为空时
     */
    public NewsItem(String title, String description, String picUrl, String url) {
        if (title == null || title.isEmpty()) {
            throw new IllegalArgumentException("属性 \"title\" 不能为空");
        }
        if (picUrl == null || picUrl.isEmpty()) {
            throw new IllegalArgumentException("属性 \"picUrl\" 不能为空");
        }
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("属性 \"url\" 不能为空");
        }
        this.title = title;
        this.description = description;
        this.picUrl = picUrl;
        this.url = url;
    }

    /**
     * 客服消息 JSON 形态（article 元素）。
     *
     * @return JSON 对象
     */
    public Map<String, Object> toJsonArray() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", title);
        if (description != null && !description.isEmpty()) {
            m.put("description", description);
        }
        m.put("picurl", picUrl);
        m.put("url", url);
        return m;
    }

    /**
     * 被动回复 XML 形态（item 元素节点）。
     *
     * @return XML 节点表
     */
    public Map<String, Object> toXmlArray() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("Title", title);
        if (description != null && !description.isEmpty()) {
            m.put("Description", description);
        }
        m.put("PicUrl", picUrl);
        m.put("Url", url);
        return m;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getPicUrl() {
        return picUrl;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return "NewsItem{title=" + title + "}";
    }
}
