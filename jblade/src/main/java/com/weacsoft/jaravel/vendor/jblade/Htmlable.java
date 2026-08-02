package com.weacsoft.jaravel.vendor.jblade;

/**
 * 标记「本身即为 HTML」的对象，对齐 Laravel 的 {@code Illuminate\Contracts\Support\Htmlable}。
 * <p>
 * Blade 的 <code>{{ }}</code> 输出默认会做 HTML 转义，但当被输出的对象实现了本接口时，
 * 引擎会调用 {@link #toHtml()} 并<b>原样输出</b>，不再转义。这正是 Laravel 中
 * <code>{{ $list-&gt;links() }}</code> 能直接渲染出分页器 HTML 的原因。
 * <p>
 * 典型实现见分页器的 {@code links()} 返回值。
 *
 * @see BladeTemplate#e(Object)
 */
public interface Htmlable {

    /**
     * 返回该对象对应的 HTML 字符串（不会被转义）。
     *
     * @return HTML 内容
     */
    String toHtml();
}
