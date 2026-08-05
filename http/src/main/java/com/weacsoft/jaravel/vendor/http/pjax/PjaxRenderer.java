package com.weacsoft.jaravel.vendor.http.pjax;

import com.weacsoft.jaravel.vendor.http.controller.response.Response;

import java.util.Map;

/**
 * PJAX 渲染策略接口。
 *
 * <p>本接口定义在 http 模块，由 wire 模块提供实现并在启动时注册到
 * {@link com.weacsoft.jaravel.vendor.http.controller.response.ResponseBuilder#setPjaxRenderer(PjaxRenderer)}。
 * 这样 {@code ResponseBuilder.view()} 可以在不反向依赖 wire 模块的前提下
 * 把「整页渲染」切换成「局部区域渲染」，从而实现<b>控制器零改动</b>。</p>
 *
 * <h3>调用时序</h3>
 * <pre>
 * 浏览器 --(X-Pjax: true)--> PjaxMiddleware（写 ThreadLocal 上下文）
 *        --> Controller（原样调用 ResponseBuilder.view(...)）
 *        --> ResponseBuilder.view 检测 isPjaxRequest()
 *        --> PjaxRenderer.render(...) 返回仅含变化区域的 JSON 信封
 * </pre>
 *
 * <p>实现类必须保证：</p>
 * <ul>
 *     <li>{@link #shouldIntercept()} 为纯读操作，不抛异常（内部异常一律吞掉返回 false）。</li>
 *     <li>{@link #render} 返回 {@code null} 表示放弃接管，由 {@code ResponseBuilder} 回退到普通整页渲染。</li>
 *     <li>{@link #render} 内部应立即完成渲染（不要延迟到 {@code getContent()}），
 *         因为 PJAX 上下文基于 ThreadLocal，延迟渲染可能跨线程导致上下文丢失。</li>
 * </ul>
 */
public interface PjaxRenderer {

    /**
     * 判断当前请求是否应由 PJAX 接管渲染。
     *
     * <p>注意这里包含<b>两种</b>情况，都需要接管：</p>
     * <ul>
     *     <li>首次直接访问页面 —— 需要输出带区域锚点、内嵌 pjax 运行时的完整 HTML；</li>
     *     <li>由已加载页面发起的切换 —— 只输出变化区域。</li>
     * </ul>
     *
     * @return true 表示应走 PJAX 渲染分支
     */
    boolean shouldIntercept();

    /**
     * 执行 PJAX 局部渲染。
     *
     * @param templateName 模板名（与 {@code ResponseBuilder.view} 第一个参数一致）
     * @param data         模板变量
     * @return 局部渲染响应；返回 {@code null} 表示放弃接管、回退整页渲染
     */
    Response render(String templateName, Map<String, Object> data);
}
