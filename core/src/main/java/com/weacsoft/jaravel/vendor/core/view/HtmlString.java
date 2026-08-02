package com.weacsoft.jaravel.vendor.core.view;

import java.util.Objects;

/**
 * 不可变的 HTML 字符串值对象，实现 {@link Htmlable}。
 * <p>
 * 与 Laravel 的 {@code HtmlString} 语义一致：包裹一段「已确认安全、无需转义」的 HTML 文本。
 * 当它在模板中通过 {@code e()} 输出时，框架识别 {@link Htmlable} 契约直接返回其原始内容。
 * </p>
 * 提供 {@link #raw(String)} 工厂与 Laravel 风格 {@link #toHtml()}。
 */
public final class HtmlString implements Htmlable {

    private final String value;

    public HtmlString(String value) {
        this.value = value;
    }

    /**
     * 工厂方法：包裹一段原始 HTML（不会再次转义）。
     *
     * @param value 原始 HTML，可为 null（输出空串）
     * @return HtmlString 实例
     */
    public static HtmlString raw(String value) {
        return new HtmlString(value);
    }

    /** 兼容别名。 */
    public static HtmlString of(String value) {
        return new HtmlString(value);
    }

    @Override
    public String toHtml() {
        return value == null ? "" : value;
    }

    @Override
    public String toString() {
        return toHtml();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HtmlString)) {
            return false;
        }
        HtmlString that = (HtmlString) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
