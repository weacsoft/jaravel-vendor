package com.weacsoft.jaravel.vendor.wechat.kernel;

import com.weacsoft.jaravel.vendor.wechat.WeChatServer;
import com.weacsoft.jaravel.vendor.wechat.message.Message;
import com.weacsoft.jaravel.vendor.wechat.server.ServerMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiFunction;

/**
 * 把旧式「应答函数」{@code BiFunction<ServerMessage, WeChatServer, Message>}（
 * {@link WeChatServer#handlePost} 的 responder 参数）桥接为洋葱层。
 * <p>
 * 语义与旧实现严格一致：
 * <ul>
 *   <li>responder 返回 {@code null} → 放行到下一层（最终得到不回复的空串）</li>
 *   <li>responder 返回 {@link Message} → 短路为被动回复</li>
 *   <li>responder 抛 {@link RuntimeException} → 吞掉并短路为不回复（避免微信重试 3 次），记录 error 日志</li>
 * </ul>
 *
 * @author weacsoft
 */
public final class ResponderMiddleware implements WechatMiddleware {

    private static final Logger logger = LoggerFactory.getLogger(ResponderMiddleware.class);

    private final WeChatServer server;
    private final BiFunction<ServerMessage, WeChatServer, Message> responder;

    /**
     * @param server    所属服务端（作为 responder 第二参数传入，业务可取其 tokenManager() 等）
     * @param responder 业务应答函数
     */
    public ResponderMiddleware(WeChatServer server, BiFunction<ServerMessage, WeChatServer, Message> responder) {
        this.server = server;
        this.responder = responder;
    }

    @Override
    public WechatResponse handle(WechatRequest req, Next next) {
        try {
            Message outgoing = responder.apply(req.message(), server);
            return (outgoing == null) ? next.handle(req) : WechatResponse.message(outgoing);
        } catch (RuntimeException e) {
            // 业务异常不应让微信重试 3 次：应答空串并记录
            logger.error("[wechat-server] 回复逻辑异常，按空串应答以避免微信重试: {}", e.getMessage(), e);
            return WechatResponse.empty();
        }
    }
}
