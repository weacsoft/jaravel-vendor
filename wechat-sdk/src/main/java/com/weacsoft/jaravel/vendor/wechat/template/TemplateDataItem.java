package com.weacsoft.jaravel.vendor.wechat.template;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模板消息数据项（wire 形态 {@code {value: "…", color: "#FF0000"}}）。
 * <p>
 * color 可选（官方示例普遍不传，缺省黑色）。
 *
 * @author weacsoft
 */
public final class TemplateDataItem {

    private final String value;
    private final String color;

    /**
     * @param value 数据值（必填，非空）
     * @param color 文字颜色（16 进制，如 {@code #173177}；可空调用 {@link #ofValue}）
     * @throws IllegalArgumentException value 为空时
     */
    public TemplateDataItem(String value, String color) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("模板数据值不能为空");
        }
        this.value = value;
        this.color = color;
    }

    /**
     * 无颜色版。
     *
     * @param value 数据值
     * @return 数据项
     */
    public static TemplateDataItem ofValue(String value) {
        return new TemplateDataItem(value, null);
    }

    /**
     * 带颜色版（fluent 语义）。
     *
     * @param value 数据值
     * @param color 颜色
     * @return 数据项
     */
    public static TemplateDataItem colored(String value, String color) {
        return new TemplateDataItem(value, color);
    }

    public String getValue() {
        return value;
    }

    public String getColor() {
        return color;
    }

    /**
     * wire 形态。
     *
     * @return {@code {value, color?}}
     */
    public Map<String, Object> toWire() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", value);
        if (color != null && !color.isEmpty()) {
            m.put("color", color);
        }
        return m;
    }
}
