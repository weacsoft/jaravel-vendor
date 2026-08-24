package com.weacsoft.jaravel.vendor.json;

/**
 * JsonCodec 运行时异常。
 */
public class JsonCodecException extends RuntimeException {

    public JsonCodecException(String message) {
        super(message);
    }

    public JsonCodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
