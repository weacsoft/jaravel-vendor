package com.weacsoft.jaravel.vendor.wechat.server;

/**
 * 推送消息类型不受支持时抛出。
 * <p>
 * 携带原始节点表（{@link #getRaw()}），方便新增消息类型后在业务侧先行兜底处理。
 *
 * @author weacsoft
 */
public class UnsupportedMessageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 未知的 MsgType 值 */
    private final String msgType;

    /** 原始节点表（便于排查/兜底） */
    private final java.util.Map<String, Object> raw;

    /**
     * @param msgType 未知的消息类型
     * @param raw     原始节点表
     */
    public UnsupportedMessageException(String msgType, java.util.Map<String, Object> raw) {
        super("不支持的微信推送消息类型: " + msgType + "（可在 MessageParser 扩展后再解析）");
        this.msgType = msgType;
        this.raw = raw;
    }

    /**
     * @return 未知的消息类型
     */
    public String getMsgType() {
        return msgType;
    }

    /**
     * @return 原始节点表
     */
    public java.util.Map<String, Object> getRaw() {
        return raw;
    }
}
