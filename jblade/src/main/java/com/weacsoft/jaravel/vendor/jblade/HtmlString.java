package com.weacsoft.jaravel.vendor.jblade;

/**
 * 一段已经是 HTML 的字符串，对齐 Laravel 的 {@code Illuminate\Support\HtmlString}。
 * <p>
 * 用 <code>{{ }}</code> 输出本对象时不会被 HTML 转义。
 */
public class HtmlString implements Htmlable {

    private final String html;

    public HtmlString(String html) {
        this.html = html == null ? "" : html;
    }

    /**
     * 便捷构造方法。
     *
     * @param html HTML 内容
     * @return 包装后的 HtmlString
     */
    public static HtmlString of(String html) {
        return new HtmlString(html);
    }

    @Override
    public String toHtml() {
        return html;
    }

    /**
     * 是否为空串。
     */
    public boolean isEmpty() {
        return html.isEmpty();
    }

    @Override
    public String toString() {
        return html;
    }
}
