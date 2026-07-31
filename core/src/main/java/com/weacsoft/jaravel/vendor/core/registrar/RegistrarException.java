package com.weacsoft.jaravel.vendor.core.registrar;

/**
 * 注解驱动注册过程中的异常，如注解方法调用失败、返回类型不匹配、
 * 单例组件重复注册等。
 */
public class RegistrarException extends RuntimeException {

    public RegistrarException(String message) {
        super(message);
    }

    public RegistrarException(String message, Throwable cause) {
        super(message, cause);
    }
}
