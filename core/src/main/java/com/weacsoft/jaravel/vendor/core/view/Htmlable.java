package com.weacsoft.jaravel.vendor.core.view;

/**
 * 可渲染为 HTML 的对象标记接口。
 * <p>
 * 对齐 Laravel 的 {@code Htmlable} 契约：当对象被放入模板并经由 {@code e()} 转义输出时，
 * 若其实现了本接口，则直接输出 {@link #toHtml()} 的返回值（不再二次转义）。
 * 这使得分页器等组件能以「值对象」方式安全地免转义输出 HTML。
 * </p>
 * <p>
 * 本接口位于 core 标准层，不依赖任何具体模板引擎（jblade 仅作为 {@code View} 的实现者）。
 * </p>
 *
 * @see HtmlString
 */
public interface Htmlable {

    /**
     * 返回该对象对应的 HTML 字符串（调用方负责其内容的安全性，框架不再转义）。
     *
     * @return HTML 字符串
     */
    String toHtml();
}
