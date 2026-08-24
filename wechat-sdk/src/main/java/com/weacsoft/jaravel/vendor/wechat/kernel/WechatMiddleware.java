package com.weacsoft.jaravel.vendor.wechat.kernel;

/**
 * 微信「接收消息」洋葱模型中间件，对齐 jaravel {@code http.Middleware} 的洋葱结构与
 * EasyWeChat 的 {@code Kernel} 管道（中间件可短路、可放行）。
 * <p>
 * 每个中间件二选一：
 * <ul>
 *   <li><b>短路</b>：直接返回 {@link WechatResponse}（如验签失败、业务已处理完毕）</li>
 *   <li><b>放行</b>：调用 {@link Next#handle(WechatRequest)} 进入下一层</li>
 * </ul>
 * 链的最内层是内核默认应答（被动回复为空串，对齐微信 5 秒时限下「不回复空串」的退避约定）。
 *
 * <h3>内置洋葱层（由 {@link WechatKernel} 固定排布，先于业务层执行）</h3>
 * <ol>
 *   <li>{@link VerifySignatureMiddleware} —— 验签（plain：signature；safe：msg_signature），失败即抛</li>
 *   <li>{@link DecryptParseMiddleware} —— 解密（safe 模式）+ 解析为类型化消息</li>
 * </ol>
 * 之后是业务通过 {@link WechatKernel#middleware(WechatMiddleware)} 追加的洋葱层（按注册顺序）。
 *
 * <h3>示例（洋葱式业务处理）</h3>
 * <pre>
 * WechatKernel kernel = oaService.server("default").kernel();
 * String reply = kernel
 *     .middleware((req, next) -&gt; req.isText() ? WechatResponse.text("echo: " + req.textContent()) : next.handle(req))
 *     .middleware((req, next) -&gt; log(req.openid()) == 0 ? WechatResponse.empty() : next.handle(req)) // 限频
 *     .handlePost(query, xml);
 * </pre>
 *
 * @author weacsoft
 */
@FunctionalInterface
public interface WechatMiddleware {

    /**
     * 处理一次微信推送。
     *
     * @param req  本次请求（已绑定公众号上下文，可提取/验签/解密/解析）
     * @param next 洋葱的下一层
     * @return 应答；不回复（放行到默认层）时为 {@link WechatResponse#empty()} 语义
     */
    WechatResponse handle(WechatRequest req, Next next);

    /**
     * 洋葱的「下一层」入口（等价于 jaravel {@code Middleware.NextFunction}）。
     *
     * @param req 当前请求
     * @return 下一层（或最内层默认应答）的应答
     */
    @FunctionalInterface
    interface Next {

        WechatResponse handle(WechatRequest req);
    }
}
