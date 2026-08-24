package com.weacsoft.jaravel.vendor.wechat.user;

import java.util.Map;

/**
 * 客服会话记录（{@code custom/service/getsession / message/list} 响应）。
 * <p>
 * 字段对齐官方：openid（用户）、trans_id（会话 id）、create_time（会话创建时间）、
 * valid（会话是否活跃）、msgtype、content（消息主体，含 text/image/voice/video/news 各型内容）。
 *
 * @author weacsoft
 */
public final class ChatRecord {

    private final String openid;
    private final String transId;
    private final long createTime;
    private final boolean valid;
    private final String msgType;
    private final Map<String, Object> content;

    private ChatRecord(String openid, String transId, long createTime, boolean valid,
                       String msgType, Map<String, Object> content) {
        this.openid = openid;
        this.transId = transId;
        this.createTime = createTime;
        this.valid = valid;
        this.msgType = msgType;
        this.content = content;
    }

    /**
     * 从原始节点构建。
     *
     * @param raw 会话记录节点
     * @return 记录对象
     */
    @SuppressWarnings("unchecked")
    public static ChatRecord from(Map<String, Object> raw) {
        String openid = str(raw.get("openid"));
        String transId = str(raw.get("trans_id"));
        long createTime = 0L;
        if (raw.get("create_time") instanceof Number n) {
            createTime = n.longValue();
        }
        boolean valid = Boolean.TRUE.equals(raw.get("valid"));
        String msgType = str(raw.get("msgtype"));
        Map<String, Object> content = Map.of();
        Object contentRaw = raw.get("content");
        if (contentRaw instanceof Map<?, ?> cm) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) cm;
            content = typed;
        }
        return new ChatRecord(openid, transId, createTime, valid, msgType, content);
    }

    public String getOpenid() {
        return openid;
    }

    public String getTransId() {
        return transId;
    }

    public long getCreateTime() {
        return createTime;
    }

    public boolean isValid() {
        return valid;
    }

    public String getMsgType() {
        return msgType;
    }

    /**
     * 文本内容（msgtype=text 时有效，否则为 null）。
     *
     * @return 文本内容
     */
    public String getTextColor() {
        Object text = content.get("text");
        // 官方 kf/sync_msg 的 text 内容为字符串；兼容个别嵌套 {content:"…"} 形态
        if (text instanceof String s) {
            return s;
        }
        if (text instanceof Map<?, ?> textMap) {
            Object c = textMap.get("content");
            if (c instanceof String s) {
                return s;
            }
        }
        return null;
    }

    public Map<String, Object> getContent() {
        return content;
    }

    private static String str(Object value) {
        return value instanceof String s ? s : (value != null ? String.valueOf(value) : null);
    }

    @Override
    public String toString() {
        return "ChatRecord{openid=" + openid + ", msgType=" + msgType + ", valid=" + valid + "}";
    }
}
