package com.weacsoft.jaravel.contract.http;

/**
 * 路由动作接口，定义路由处理器的执行契约。
 *
 * <p>参考 Laravel 路由闭包，本接口为函数式接口，
 * 每个路由动作接收请求并返回响应。</p>
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li>实现类应为无状态或线程安全</li>
 *   <li>不应返回 {@code null} 响应</li>
 *   <li>异常应由 {@link Middleware} 层统一处理</li>
 * </ul>
 *
 * @see Request
 * @see Response
 * @see Middleware
 */
@FunctionalInterface
public interface RouteAction {

    /**
     * 处理 HTTP 请求并返回响应。
     *
     * @param request HTTP 请求
     * @return HTTP 响应
     */
    Response handle(Request request);
}
