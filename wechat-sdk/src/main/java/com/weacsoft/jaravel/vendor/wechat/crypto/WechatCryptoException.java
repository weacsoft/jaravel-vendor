package com.weacsoft.jaravel.vendor.wechat.crypto;

/**
 * 微信消息加解密失败异常（签名校验、AES 解码、receiveid 不匹配等）。
 *
 * @author weacsoft
 */
public class WechatCryptoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public WechatCryptoException(String message) {
        super(message);
    }

    public WechatCryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
