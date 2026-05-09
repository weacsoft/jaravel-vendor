package com.weacsoft.jaravel.contract.http;

/**
 * HTTP 中间件接口，定义请求/响应的管道处理契约。
 *
 * <p>参考 Laravel 中间件机制，本接口采用责任链模式，
 * 每个中间件可以在请求到达目标处理器之前和响应返回之后执行逻辑。</p>
 *
 * <h3>执行流程</h3>
 * <pre>
 * Request → Middleware1 → Middleware2 → ... → RouteAction → Response
 * Response ← Middleware1 ← Middleware2 ← ... ← RouteAction ← Response
 * </pre>
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li>实现类必须为无状态或线程安全</li>
 *   <li>必须调用 {@link NextFunction#apply(Request)} 将请求传递给下一个处理器</li>
 *   <li>不应吞没异常；如需处理异常应重新抛出或包装</li>
 * </ul>
 *
 * @see Request
 * @see Response
 */
@FunctionalInterface
public interface Middleware {

    /**
     * 处理请求。
     *
     * @param request HTTP 请求
     * @param next    下一个处理器的调用函数
     * @return HTTP 响应
     */
    Response handle(Request request, NextFunction next);

    /**
     * 下一个处理器函数接口。
     */
    @FunctionalInterface
    interface NextFunction {
        /**
         * 调用下一个处理器。
         *
         * @param request HTTP 请求
         * @return HTTP 响应
         */
        Response apply(Request request);
    }
}
