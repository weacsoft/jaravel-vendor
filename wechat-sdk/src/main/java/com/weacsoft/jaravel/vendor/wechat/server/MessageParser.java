package com.weacsoft.jaravel.vendor.wechat.server;

import com.weacsoft.jaravel.vendor.wechat.xml.XmlUtil;

import java.util.Map;

/**
 * 微信推送 XML → 类型化消息 的分发器，对齐 EasyWeChat 5.x 的 {@code MessageParser} 机制。
 * <p>
 * 支持的消息类型（{@link MessageType}）：
 * <ul>
 *   <li>普通消息：text/image/voice/video/shortvideo/location/link</li>
 *   <li>事件：subscribe/unsubscribe/SCAN/LOCATION/CLICK/VIEW → {@link EventMessage}</li>
 *   <li>订阅通知事件：subscribe_msg_sent_event → {@link SubscribeMsgSentEvent}；
 *       subscribe_msg_change_event → {@link SubscribeMsgChangeEvent}</li>
 * </ul>
 *
 * 未知类型抛 {@link UnsupportedMessageException}（携带 raw Map）——
 * 新增微信消息类型时，先在这里补一个分支即可，服务层无需改动。
 *
 * @author weacsoft
 */
public final class MessageParser {

    private MessageParser() {
    }

    /**
     * 解析微信推送明文 XML 为类型化消息。
     *
     * @param xml 推送 XML 文本
     * @return 类型化消息
     * @throws UnsupportedMessageException 未知 MsgType
     * @throws IllegalArgumentException    XML 结构非法
     */
    public static ServerMessage parse(String xml) {
        Map<String, Object> nodes = XmlUtil.parseXml(xml);
        // 微信推送 XML 的根元素是 <xml>
        Map<String, Object> body;
        Object xmlNode = nodes.get("xml");
        if (xmlNode instanceof Map<?, ?> xm) {
            body = asStringMap(xm);
        } else {
            body = null;
        }
        if (body == null) {
            for (Object value : nodes.values()) {
                if (value instanceof Map<?, ?>) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> candidate = (Map<String, Object>) value;
                    return parseMap(candidate);
                }
            }
            return parseMap(nodes);
        }
        return parseMap(body);
    }

    /**
     * 从节点表（已剥离根元素）按 MsgType 分发。
     *
     * @param map 消息节点表（含 MsgType 键）
     * @return 类型化消息
     * @throws UnsupportedMessageException 未知 MsgType
     */
    public static ServerMessage parseMap(Map<String, Object> map) {
        String msgType = map.get("MsgType") != null ? String.valueOf(map.get("MsgType")) : null;
        if (msgType == null) {
            throw new UnsupportedMessageException("(missing)", map);
        }
        return switch (msgType.toLowerCase()) {
            case MessageType.TEXT -> TextMessage.from(map);
            case MessageType.IMAGE -> ImageMessage.from(map);
            case MessageType.VOICE -> VoiceMessage.from(map);
            case MessageType.VIDEO -> VideoMessage.from(map);
            case MessageType.SHORTVIDEO -> ShortVideoMessage.from(map);
            case MessageType.LOCATION -> LocationMessage.from(map);
            case MessageType.LINK -> LinkMessage.from(map);
            case MessageType.EVENT -> parseEvent(map);
            default -> throw new UnsupportedMessageException(msgType, map);
        };
    }

    /**
     * 宽松 Map → typed Map（XML 解析产物安全转换）。
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringMap(Map<?, ?> raw) {
        return (Map<String, Object>) raw;
    }

    private static ServerMessage parseEvent(Map<String, Object> map) {
        String event = map.get("Event") != null ? String.valueOf(map.get("Event")) : "";
        return switch (event.toLowerCase()) {
            case MessageType.EVENT_SUBSCRIBE_MSG_SENT -> SubscribeMsgSentEvent.from(map);
            case MessageType.EVENT_SUBSCRIBE_MSG_CHANGE -> SubscribeMsgChangeEvent.from(map);
            default -> EventMessage.from(map);
        };
    }

    // ===== instanceof 便捷判断（避免调用侧强转） =====

    public static boolean isText(ServerMessage msg) {
        return msg instanceof TextMessage;
    }

    public static boolean isImage(ServerMessage msg) {
        return msg instanceof ImageMessage;
    }

    public static boolean isVoice(ServerMessage msg) {
        return msg instanceof VoiceMessage;
    }

    public static boolean isEvent(ServerMessage msg) {
        return msg instanceof EventMessage;
    }

    /**
     * 强转型（不匹配时抛 ClassCastException，请先用 isXxx 判断）。
     */
    public static TextMessage asText(ServerMessage msg) {
        if (!(msg instanceof TextMessage text)) {
            throw new ClassCastException(msg + " 不是 " + TextMessage.class.getSimpleName());
        }
        return text;
    }

    /**
     * 强转型（不匹配时抛 ClassCastException，请先用 isXxx 判断）。
     */
    public static EventMessage asEvent(ServerMessage msg) {
        if (!(msg instanceof EventMessage event)) {
            throw new ClassCastException(msg + " 不是 " + EventMessage.class.getSimpleName());
        }
        return event;
    }
}
