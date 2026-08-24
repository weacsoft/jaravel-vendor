package com.weacsoft.jaravel.vendor.wechat.server;

import java.util.Map;

/**
 * 微信推送的事件（{@code MsgType=event}）。
 * <p>
 * 事件类型（{@code Event} 字段）：
 * <ul>
 *   <li>{@link #SUBSCRIBE} 关注（未关注时扫二维码关注，EventKey 以 {@code qrscene_} 开头）</li>
 *   <li>{@link #UNSUBSCRIBE} 取消关注（官方要求：收到后须删除该用户所有信息）</li>
 *   <li>{@link #SCAN} 已关注用户扫描带参二维码（EventKey 为场景值）</li>
 *   <li>{@link #LOCATION} 上报地理位置（附带 longitude/latitude/precision）</li>
 *   <li>{@link #CLICK} 点击菜单拉取消息（EventKey 与菜单 key 对应）</li>
 *   <li>{@link #VIEW} 点击菜单跳转链接（EventKey 为跳转 URL）</li>
 * </ul>
 *
 * @author weacsoft
 */
public final class EventMessage extends ServerMessage {

    /** 关注事件 */
    public static final String SUBSCRIBE = "subscribe";
    /** 取消关注事件 */
    public static final String UNSUBSCRIBE = "unsubscribe";
    /** 扫描带参二维码事件 */
    public static final String SCAN = "SCAN";
    /** 上报地理位置事件 */
    public static final String LOCATION = "LOCATION";
    /** 点击菜单拉取消息事件 */
    public static final String CLICK = "CLICK";
    /** 点击菜单跳转链接事件 */
    public static final String VIEW = "VIEW";

    private final String event;
    private final String eventKey;
    private final String ticket;

    /** 地理位置经度（仅 LOCATION 事件） */
    private final double longitude;
    /** 地理位置纬度（仅 LOCATION 事件） */
    private final double latitude;
    /** 地理位置精度（仅 LOCATION 事件） */
    private final double precision;

    private EventMessage(Map<String, Object> map) {
        fillCommon(map);
        this.event = text(map.get("Event"));
        this.eventKey = text(map.get("EventKey"));
        this.ticket = text(map.get("Ticket"));
        this.longitude = doubleValue(map.get("Longitude"), 0.0);
        this.latitude = doubleValue(map.get("Latitude"), 0.0);
        this.precision = doubleValue(map.get("Precision"), 0.0);
    }

    /**
     * @param map 解析后的 XML 节点表
     * @return 事件消息
     */
    public static EventMessage from(Map<String, Object> map) {
        return new EventMessage(map);
    }

    /**
     * @return 事件类型名（subscribe/unsubscribe/SCAN/LOCATION/CLICK/VIEW）
     */
    public String getEvent() {
        return event;
    }

    /**
     * @return 事件 Key（场景值 / 菜单 key / 跳转 URL；qrscene_ 前缀表示带参二维码）
     */
    public String getEventKey() {
        return eventKey;
    }

    /**
     * @return 二维码 ticket（仅二维码相关事件，可换二维码图片）
     */
    public String getTicket() {
        return ticket;
    }

    public boolean isSubscribe() {
        return SUBSCRIBE.equalsIgnoreCase(event);
    }

    public boolean isUnsubscribe() {
        return UNSUBSCRIBE.equalsIgnoreCase(event);
    }

    public boolean isScan() {
        return SCAN.equals(event);
    }

    public boolean isLocation() {
        return LOCATION.equals(event);
    }

    public boolean isClick() {
        return CLICK.equals(event);
    }

    public boolean isView() {
        return VIEW.equals(event);
    }

    /**
     * @return 经度（仅 LOCATION 事件有效，否则为 0）
     */
    public double getLongitude() {
        return longitude;
    }

    /**
     * @return 纬度（仅 LOCATION 事件有效，否则为 0）
     */
    public double getLatitude() {
        return latitude;
    }

    /**
     * @return 精度（仅 LOCATION 事件有效，否则为 0）
     */
    public double getPrecision() {
        return precision;
    }

    @Override
    public String toString() {
        return "EventMessage{event=" + event + ", eventKey=" + eventKey + "}";
    }
}
