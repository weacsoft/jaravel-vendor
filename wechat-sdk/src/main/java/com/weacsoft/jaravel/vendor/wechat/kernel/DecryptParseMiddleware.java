package com.weacsoft.jaravel.vendor.wechat.kernel;

/**
 * 内置洋葱层②：解密（safe 模式）+ 解析为类型化消息，供后续业务层通过
 * {@link WechatRequest#message()}/{@link WechatRequest#isText()} 等直接读取。
 * <p>
 * 惰性执行：真正的解密/解析发生在首次访问 {@link WechatRequest#plainXml()} /
 * {@link WechatRequest#message()}；本层只负责「验签通过后、业务层之前」强制完成该步骤，
 * 使验签失败时不产生解密开销（且异常归属清晰）。
 *
 * @author weacsoft
 */
public final class DecryptParseMiddleware implements WechatMiddleware {

    /** 全局单例（中间件无状态） */
    public static final DecryptParseMiddleware INSTANCE = new DecryptParseMiddleware();

    @Override
    public WechatResponse handle(WechatRequest req, Next next) {
        if (!req.isVerify()) {
            req.message();
        }
        return next.handle(req);
    }
}
