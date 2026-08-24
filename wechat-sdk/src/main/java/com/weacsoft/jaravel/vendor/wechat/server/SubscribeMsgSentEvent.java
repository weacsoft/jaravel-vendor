package com.weacsoft.jaravel.vendor.wechat.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 订阅通知的「下发结果」事件（{@code Event=subscribe_msg_sent_event}）。
 * <p>
 * 用户拒收或系统异步推送失败时推送。一次订阅可能对应多条通知（多个 TemplateId）。
 *
 * @author weacsoft
 */
public final class SubscribeMsgSentEvent extends ServerMessage {

    /** 单条通知的下发结果 */
    public static class Item {
        private final String templateId;
        private final String msgId;
        private final int errorCode;
        private final String errorStatus;

        private Item(Map<String, Object> map) {
            this.templateId = text(map.get("TemplateId"));
            this.msgId = text(map.get("MsgID"));
            this.errorCode = intValue(map.get("ErrorCode"), -1);
            this.errorStatus = text(map.get("ErrorStatus"));
        }

        public String getTemplateId() {
            return templateId;
        }

        public String getMsgId() {
            return msgId;
        }

        /**
         * @return 推送结果状态码（0 表示成功；-1 表示推送不含该字段）
         */
        public int getErrorCode() {
            return errorCode;
        }

        public String getErrorStatus() {
            return errorStatus;
        }

        public boolean isSuccess() {
            return errorCode == 0;
        }
    }

    private final List<Item> items;

    private SubscribeMsgSentEvent(Map<String, Object> map) {
        fillCommon(map);
        Map<String, Object> node = child(map, "SubscribeMsgSentEvent");
        List<Item> list = new ArrayList<>();
        for (Map<String, Object> item : rawItems(node)) {
            list.add(new Item(item));
        }
        this.items = List.copyOf(list);
    }

    /**
     * 提取条目列表：兼容
     * {@code <SubscribeMsgSentEvent><List><item>…</item>…</List></SubscribeMsgSentEvent>}（重复 item 聚合）
     * 与直接以列表给出的形态。
     */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> rawItems(Map<String, Object> node) {
        Object raw = null;
        for (String key : new String[]{"List", "Item", "item", "list"}) {
            Object v = node.get(key);
            if (v instanceof List<?> vl) {
                raw = vl;
                break;
            }
            if (v instanceof Map<?, ?> vm) {
                Object inner = ((Map<?, ?>) vm).get("item") != null
                        ? ((Map<?, ?>) vm).get("item")
                        : ((Map<?, ?>) vm).get("Item");
                if (inner instanceof List<?> vl) {
                    raw = vl;
                    break;
                }
                if (inner instanceof Map<?, ?>) {
                    raw = java.util.List.of(inner);
                    break;
                }
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        if (raw instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> om) {
                    out.add((Map<String, Object>) om);
                }
            }
        }
        return out;
    }

    /**
     * @param map 解析后的 XML 节点表
     * @return 下发结果事件
     */
    public static SubscribeMsgSentEvent from(Map<String, Object> map) {
        return new SubscribeMsgSentEvent(map);
    }

    /**
     * @return 各模板的下发结果列表
     */
    public List<Item> getItems() {
        return items;
    }

    @Override
    public String toString() {
        return "SubscribeMsgSentEvent{items=" + items.size() + "}";
    }
}
