package com.weacsoft.jaravel.vendor.wechat.message;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单消息（{@code msgtype=msgmenu}）：一次性下发一个菜单，用户点击某项后
 * 平台把 {@code menuid + 选项 id} 回调给开发者。
 * <p>
 * 仅支持客服消息发送，不支持被动回复。
 *
 * @author weacsoft
 */
public final class MenuMessage extends Message {

    /** 菜单选项 */
    public static class Item {
        private final int id;
        private final String content;

        /**
         * @param id      选项 id（必填）
         * @param content 选项内容（必填）
         * @throws IllegalArgumentException content 为空时
         */
        public Item(int id, String content) {
            if (content == null || content.isEmpty()) {
                throw new IllegalArgumentException("msgmenu 选项 \"content\" 不能为空");
            }
            this.id = id;
            this.content = content;
        }

        public int getId() {
            return id;
        }

        public String getContent() {
            return content;
        }
    }

    private final String headContent;
    private final List<Item> list;
    private final String tailContent;

    /**
     * @param headContent 菜单开头描述（可空）
     * @param list        菜单项（必填，至少 1 项）
     * @param tailContent 菜单结尾描述（可空）
     * @throws IllegalArgumentException list 为空时
     */
    public MenuMessage(String headContent, List<Item> list, String tailContent) {
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException("msgmenu 菜单 \"list\" 不能为空（至少 1 项）");
        }
        this.headContent = headContent;
        this.list = List.copyOf(list);
        this.tailContent = tailContent;
    }

    @Override
    public String getType() {
        return "msgmenu";
    }

    public String getHeadContent() {
        return headContent;
    }

    public List<Item> getList() {
        return list;
    }

    public String getTailContent() {
        return tailContent;
    }

    @Override
    protected Map<String, Object> payload() {
        Map<String, Object> p = new LinkedHashMap<>();
        if (headContent != null && !headContent.isEmpty()) {
            p.put("head_content", headContent);
        }
        List<Map<String, Object>> items = new ArrayList<>(list.size());
        for (Item item : list) {
            items.add(Map.of("id", item.getId(), "content", item.getContent()));
        }
        p.put("list", items);
        if (tailContent != null && !tailContent.isEmpty()) {
            p.put("tail_content", tailContent);
        }
        return p;
    }

    @Override
    public String toString() {
        return "MenuMessage{options=" + list.size() + "}";
    }
}
