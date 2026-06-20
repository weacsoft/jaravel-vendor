package com.weacsoft.jaravel.vendor.core.validation;

import java.util.List;
import java.util.Map;

/**
 * 校验失败异常，携带字段 -> 错误消息列表。
 */
public class ValidationException extends RuntimeException {

    private final Map<String, List<String>> errors;

    public ValidationException(Map<String, List<String>> errors) {
        super("The given data was invalid.");
        this.errors = errors;
    }

    public Map<String, List<String>> errors() {
        return errors;
    }
}
