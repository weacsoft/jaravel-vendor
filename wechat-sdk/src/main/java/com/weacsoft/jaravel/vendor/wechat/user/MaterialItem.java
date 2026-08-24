package com.weacsoft.jaravel.vendor.wechat.user;

import java.util.List;
import java.util.Map;

/**
 * 永久素材条目（{@code material/batchget_material} 响应单条）。
 * <p>
 * wire 字段：media_id、name、url、type（image/voice/video/news）、update_time、item（图文条目列表）。
 *
 * @author weacsoft
 */
public final class MaterialItem {

    private final String mediaId;
    private final String name;
    private final String url;
    private final String type;
    private final long updateTime;
    private final List<Map<String, Object>> items;

    private MaterialItem(String mediaId, String name, String url, String type, long updateTime,
                         List<Map<String, Object>> items) {
        this.mediaId = mediaId;
        this.name = name;
        this.url = url;
        this.type = type;
        this.updateTime = updateTime;
        this.items = items;
    }

    /**
     * 从原始节点构建。
     *
     * @param raw 素材节点
     * @return 素材对象
     */
    @SuppressWarnings("unchecked")
    public static MaterialItem from(Map<String, Object> raw) {
        String mediaId = str(raw.get("media_id"));
        String name = str(raw.get("name"));
        String url = str(raw.get("url"));
        String type = str(raw.get("type"));
        long updateTime = 0L;
        if (raw.get("update_time") instanceof Number n) {
            updateTime = n.longValue();
        }
        List<Map<String, Object>> items = List.of();
        Object rawItems = raw.get("item");
        if (rawItems instanceof List<?> list) {
            items = (List<Map<String, Object>>) list;
        }
        return new MaterialItem(mediaId, name, url, type, updateTime, items);
    }

    /**
     * @return 素材 id
     */
    public String getMediaId() {
        return mediaId;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    /**
     * @return 素材类型（image/voice/video/news）
     */
    public String getType() {
        return type;
    }

    /**
     * @return 更新时间（秒级时间戳，不带时为 0）
     */
    public long getUpdateTime() {
        return updateTime;
    }

    /**
     * @return 图文条目列表（type=news 时有效，否则为空）
     */
    public List<Map<String, Object>> getItems() {
        return items;
    }

    private static String str(Object value) {
        return value instanceof String s ? s : (value != null ? String.valueOf(value) : null);
    }

    @Override
    public String toString() {
        return "MaterialItem{mediaId=" + mediaId + ", type=" + type + ", name=" + name + "}";
    }
}
