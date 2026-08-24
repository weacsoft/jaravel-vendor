package com.weacsoft.jaravel.vendor.wechat.server;

import java.util.Map;

/**
 * 接收的地理位置消息（{@code MsgType=location}）。
 *
 * @author weacsoft
 */
public final class LocationMessage extends ServerMessage {

    private final double locationX;
    private final double locationY;
    private final int scale;
    private final String label;

    private LocationMessage(Map<String, Object> map) {
        fillCommon(map);
        this.locationX = doubleValue(map.get("Location_X"), 0.0);
        this.locationY = doubleValue(map.get("Location_Y"), 0.0);
        this.scale = intValue(map.get("Scale"), 0);
        this.label = text(map.get("Label"));
    }

    /**
     * @param map 解析后的 XML 节点表
     * @return 地理位置消息
     */
    public static LocationMessage from(Map<String, Object> map) {
        return new LocationMessage(map);
    }

    /**
     * @return 纬度
     */
    public double getLocationX() {
        return locationX;
    }

    /**
     * @return 经度
     */
    public double getLocationY() {
        return locationY;
    }

    /**
     * @return 地图缩放比例
     */
    public int getScale() {
        return scale;
    }

    /**
     * @return 位置信息
     */
    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return "LocationMessage{lat=" + locationX + ", lng=" + locationY + "}";
    }
}
