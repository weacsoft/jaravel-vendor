package com.weacsoft.jaravel.vendor.wechat.response;

/**
 * 微信业务异常：HTTP 非 2xx、业务错误码（errcode != 0）或关键数据缺失时抛出。
 * <p>
 * 与 {@link WeChatResponse} 的分工：
 * <ul>
 *   <li>「发送类」接口的业务失败默认<b>不</b>抛异常，调用方通过
 *       {@code WeChatResponse.isSuccess() / requireSuccess(...)} 自行决定语义；</li>
 *   <li>「读取类」接口（用户信息、菜单回读、code2Session 等）失败时<b>直接抛出</b>本异常，
 *       避免出现"返回了一个空对象却看起来成功"的隐蔽错误。</li>
 * </ul>
 *
 * @author weacsoft
 */
public class WechatApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 错误码；非微信业务错误（网络、HTTP 层等）时为 {@code -1}。 */
    private final int errcode;

    /** 微信错误描述（errmsg）；非微信业务错误时为 {@code null}。 */
    private final String errmsg;

    /**
     * 构造微信业务失败异常。
     *
     * @param operation 操作名（用于排查，如 "sendCustomerMessage"）
     * @param errcode   微信错误码
     * @param errmsg    微信错误描述，可为 null
     */
    public WechatApiException(String operation, int errcode, String errmsg) {
        super(operation + " 业务失败: errcode=" + errcode + ", errmsg=" + errmsg);
        this.errcode = errcode;
        this.errmsg = errmsg;
    }

    /**
     * 构造非标准业务异常（如"响应缺少关键字段"、HTTP 层失败）。
     *
     * @param message 异常信息
     */
    public WechatApiException(String message) {
        super(message);
        this.errcode = -1;
        this.errmsg = null;
    }

    /**
     * @return 微信错误码，非微信业务错误时为 -1
     */
    public int getErrcode() {
        return errcode;
    }

    /**
     * @return 微信错误描述，非微信业务错误时为 null
     */
    public String getErrmsg() {
        return errmsg;
    }
}
