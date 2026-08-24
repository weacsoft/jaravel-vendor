package com.weacsoft.jaravel.vendor.wechat.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 订阅通知的「用户拒收」事件（{@code Event=subscribe_msg_change_event}）。
 * <p>
 * 仅推送用户主动拒收（点击"取消"）的通知。
 *
 * @author weacsoft
 */
public final class SubscribeMsgChangeEvent extends ServerMessage {

    /** 单条拒收记录 */
    public static class Item {
        private final String templateId;
        private final String subscribeStatus;

        private Item(Map<String, Object> map) {
            this.templateId = text(map.get("TemplateId"));
            this.subscribeStatus = text(map.get("SubscribeStatusString"));
        }

        public String getTemplateId() {
            return templateId;
        }

        /**
         * @return 用户点击行为（{@code reject} = 取消）
         */
        public String getSubscribeStatus() {
            return subscribeStatus;
        }

        public boolean isReject() {
            return "reject".equalsIgnoreCase(subscribeStatus);
        }
    }

    private final List<Item> items;

    private SubscribeMsgChangeEvent(Map<String, Object> map) {
        fillCommon(map);
        Map<String, Object> node = child(map, "SubscribeMsgChangeEvent");
        List<Item> list = new ArrayList<>();
        for (Map<String, Object> item : SubscribeMsgSentEvent.rawItems(node)) {
            list.add(new Item(item));
        }
        this.items = List.copyOf(list);
    }

    /**
     * @param map 解析后的 XML 节点表
     * @return 拒收事件
     */
    public static SubscribeMsgChangeEvent from(Map<String, Object> map) {
        return new SubscribeMsgChangeEvent(map);
    }

    /**
     * @return 拒收记录列表
     */
    public List<Item> getItems() {
        return items;
    }

    @Override
    public String toString() {
        return "SubscribeMsgChangeEvent{items=" + items.size() + "}";
    }
}
