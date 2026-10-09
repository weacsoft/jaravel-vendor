package com.weacsoft.jaravel.vendor.wechat.kernel;

import com.weacsoft.jaravel.vendor.wechat.crypto.WechatCryptoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 内置洋葱层①：签名校验，失败即抛 {@link WechatCryptoException}（等价于旧 {@code WeChatServer} 的验签行为）。
 * <p>
 * 两种消息模式用的是<b>两套不同参数个数</b>的签名算法（依据微信官方《消息加解密说明》「接入指引」）：
 * <ul>
 *   <li><b>明文模式</b>（{@code message-mode=plain}）：
 *       {@code signature = sha1(sort(token, timestamp, nonce))} —— <b>三个</b>参数，不含消息体，
 *       同时用于 GET 接入校验与 POST 推送校验；</li>
 *   <li><b>安全模式</b>（{@code message-mode=safe}）：
 *       {@code msg_signature = sha1(sort(token, timestamp, nonce, Encrypt))} —— <b>四个</b>参数
 *       （官方原文特别强调「不要使用 signature 验证」）。</li>
 * </ul>
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
            return handleVerify(req);
        }
        if (req.safeMode()) {
            String encrypted = req.extractEncrypt();
            // 安全模式：msg_signature = sha1(sort(token, timestamp, nonce, Encrypt))
            if (!req.crypt().verifySignature(req.timestamp(), req.nonce(), encrypted, req.msgSignature())) {
                logger.warn("[wechat-kernel] POST 验签失败（msg_signature）: nonce={}", req.nonce());
                throw new WechatCryptoException("POST 验签失败（msg_signature 不匹配）");
            }
        } else if (req.account().isVerifyPostSignature()
                && !req.crypt().verifyPlainSignature(req.timestamp(), req.nonce(), req.signature())) {
            // 明文模式：signature = sha1(sort(token, timestamp, nonce))（官方三参数，不含消息体）
            logger.warn("[wechat-kernel] POST 验签失败（signature）: nonce={}", req.nonce());
            throw new WechatCryptoException("POST 验签失败（明文模式 signature 不匹配或缺失）");
        } else if (!req.account().isVerifyPostSignature()) {
            logger.warn("[wechat-kernel] 明文模式 POST 跳过验签（verify-post-signature=false）: nonce={}",
                    req.nonce());
        }
        return next.handle(req);
    }

    /**
     * GET 接入校验：校验签名后返回 echostr（安全模式先解密）。
     *
     * @param req 验签请求
     * @return ECHO 应答
     */
    private WechatResponse handleVerify(WechatRequest req) {
        String echostr = req.echostr();
        boolean verified = req.safeMode()
                // 安全模式：msg_signature = sha1(sort(token, timestamp, nonce, echostr))
                ? req.crypt().verifySignature(req.timestamp(), req.nonce(), echostr, req.msgSignature())
                // 明文模式：signature = sha1(sort(token, timestamp, nonce))（echostr 不参与 sha1）
                : req.crypt().verifyPlainSignature(req.timestamp(), req.nonce(), req.signature());
        if (!verified) {
            logger.warn("[wechat-kernel] GET 验签失败: nonce={}, mode={}",
                    req.nonce(), req.safeMode() ? "safe" : "plain");
            throw new WechatCryptoException(req.safeMode()
                    ? "GET 验签失败（msg_signature 不匹配）"
                    : "GET 验签失败（signature 不匹配；明文模式签名为 sha1(sort(token, timestamp, nonce)) 三参数）");
        }
        String value = req.safeMode() ? req.crypt().decrypt(echostr) : echostr;
        return WechatResponse.echostr(value);
    }
}