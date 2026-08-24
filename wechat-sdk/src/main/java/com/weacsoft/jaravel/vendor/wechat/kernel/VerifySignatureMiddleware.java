package com.weacsoft.jaravel.vendor.wechat.kernel;

import com.weacsoft.jaravel.vendor.wechat.crypto.WechatCryptoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 内置洋葱层①：签名校验（plain：{@code signature}；safe：{@code msg_signature}），失败即抛
 * {@link WechatCryptoException}（等价于旧 {@code WeChatServer} 的验签行为）。
 * <p>
 * 验签请求（GET echostr）验签通过后直接短路应答（safe 模式先解密 echostr）；
 * 消息推送（safe）提取 {@code <Encrypt>} 验签后放行给下一层。
 *
 * @author weacsoft
 */
public final class VerifySignatureMiddleware implements WechatMiddleware {

    private static final Logger logger = LoggerFactory.getLogger(VerifySignatureMiddleware.class);

    /** 全局单例（中间件无状态） */
    public static final VerifySignatureMiddleware INSTANCE = new VerifySignatureMiddleware();

    @Override
    public WechatResponse handle(WechatRequest req, Next next) {
        if (req.isVerify()) {
            String echostr = req.echostr();
            String candidate = req.safeMode() ? req.msgSignature() : req.signature();
            if (!req.crypt().verifySignature(req.timestamp(), req.nonce(), echostr, candidate)) {
                logger.warn("[wechat-kernel] GET 验签失败: nonce={}", req.nonce());
                throw new WechatCryptoException(req.safeMode()
                        ? "GET 验签失败（msg_signature 不匹配）"
                        : "GET 验签失败（signature 不匹配）");
            }
            String value = req.safeMode() ? req.crypt().decrypt(echostr) : echostr;
            return WechatResponse.echostr(value);
        }
        if (req.safeMode()) {
            String encrypted = req.extractEncrypt();
            if (!req.crypt().verifySignature(req.timestamp(), req.nonce(), encrypted, req.msgSignature())) {
                logger.warn("[wechat-kernel] POST 验签失败: nonce={}", req.nonce());
                throw new WechatCryptoException("POST 验签失败（msg_signature 不匹配）");
            }
        }
        return next.handle(req);
    }
}
