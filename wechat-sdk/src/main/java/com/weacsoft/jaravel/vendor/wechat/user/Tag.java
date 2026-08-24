package com.weacsoft.jaravel.vendor.wechat.user;

import java.util.Map;

/**
 * 用户标签（{@code tag/get / tag/add / tag/getid / tag/members/add / tag/members/delete} 响应）。
 * <p>
 * wire 字段：id（标签 id）、name、count（标签下的用户数，部分接口不带该字段）。
 *
 * @author weacsoft
 */
public final class Tag {

    private final int id;
    private final String name;
    private final int count;

    private Tag(int id, String name, int count) {
        this.id = id;
        this.name = name;
        this.count = count;
    }

    /**
     * 从原始节点构建。
     *
     * @param raw 标签节点（含 id/name/count?）
     * @return 标签对象
     */
    public static Tag from(Map<String, Object> raw) {
        int id = intVal(raw.get("id"));
        String name = raw.get("name") != null ? String.valueOf(raw.get("name")) : null;
        int count = intVal(raw.get("count"));
        return new Tag(id, name, count);
    }

    /**
     * @return 标签 id
     */
    public int getId() {
        return id;
    }

    /**
     * @return 标签名
     */
    public String getName() {
        return name;
    }

    /**
     * @return 标签下的用户数（部分接口不返回时为 0）
     */
    public int getCount() {
        return count;
    }

    private static int intVal(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s && !s.isEmpty()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                // 落到默认值
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        return "Tag{id=" + id + ", name=" + name + ", count=" + count + "}";
    }
}
