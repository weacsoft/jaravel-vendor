package com.weacsoft.jaravel.vendor.core.validation;

/**
 * 授权失败异常（FormRequest.authorize() 返回 false 时抛出）。
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
