package com.weacsoft.jaravel.vendor.wire;

/**
 * 快照签名验证失败异常。
 * <p>
 * 当客户端提交的 wire_body snapshot 的 HMAC 签名与本地计算结果不一致时抛出,
 * 表明快照在传输途中被篡改。
 */
public class TamperedSnapshotException extends RuntimeException {

    public TamperedSnapshotException(String message) {
        super(message);
    }

    public TamperedSnapshotException(String message, Throwable cause) {
        super(message, cause);
    }
}