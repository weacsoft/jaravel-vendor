package com.weacsoft.jaravel.vendor.wechat.server;

/**
 * 微信推送消息类型常量（接收侧 wire 名，PascalCase 消息体的 {@code MsgType} 字段）。
 *
 * @author weacsoft
 */
public final class MessageType {

    private MessageType() {
    }

    /** 文本消息 */
    public static final String TEXT = "text";
    /** 图片消息 */
    public static final String IMAGE = "image";
    /** 语音消息 */
    public static final String VOICE = "voice";
    /** 视频消息 */
    public static final String VIDEO = "video";
    /** 小视频消息 */
    public static final String SHORTVIDEO = "shortvideo";
    /** 地理位置消息 */
    public static final String LOCATION = "location";
    /** 链接消息 */
    public static final String LINK = "link";
    /** 事件 */
    public static final String EVENT = "event";

    /** 事件：关注 */
    public static final String EVENT_SUBSCRIBE = "subscribe";
    /** 事件：取消关注 */
    public static final String EVENT_UNSUBSCRIBE = "unsubscribe";
    /** 事件：扫描带参二维码 */
    public static final String EVENT_SCAN = "SCAN";
    /** 事件：上报地理位置 */
    public static final String EVENT_LOCATION = "LOCATION";
    /** 事件：点击菜单拉取消息 */
    public static final String EVENT_CLICK = "CLICK";
    /** 事件：点击菜单跳转链接 */
    public static final String EVENT_VIEW = "VIEW";
    /** 事件：订阅通知下发结果 */
    public static final String EVENT_SUBSCRIBE_MSG_SENT = "subscribe_msg_sent_event";
    /** 事件：订阅通知用户拒收 */
    public static final String EVENT_SUBSCRIBE_MSG_CHANGE = "subscribe_msg_change_event";
}
