package com.weacsoft.jaravel.vendor.http.middleware;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import org.springframework.stereotype.Component;

/**
 * 空字符串转 Null 中间件，对齐 Laravel 的 {@code ConvertEmptyStringsToNull}。
 * <p>
 * 无状态、不可变，可被 Spring 容器管理并安全地在并发请求间复用。
 * 所有字段为 {@code final}，构造后不可修改。
 */
@Component
public class ConvertEmptyStringsToNull implements Middleware {
    private final String[] except;

    public ConvertEmptyStringsToNull(String... except) {
        this.except = except;
    }

    public ConvertEmptyStringsToNull() {
        this("password", "password_confirmation", "current_password");
    }

    @Override
    public Response handle(Request request, NextFunction next, String... params) {
        convertEmptyStringsToNull(request);
        return next.apply(request);
    }

    private void convertEmptyStringsToNull(Request request) {
        // 遍历 input 和 query，将空字符串转为 null
        for (String name : request.inputNames()) {
            if (isExcluded(name)) continue;
            Object value = request.input().get(name);
            if (value instanceof String && ((String) value).isEmpty()) {
                request.replaceInput(name, null);
            }
        }
        for (String name : request.queryNames()) {
            if (isExcluded(name)) continue;
            Object value = request.query().get(name);
            if (value instanceof String && ((String) value).isEmpty()) {
                request.replaceQuery(name, null);
            }
        }
    }

    private boolean isExcluded(String name) {
        for (String e : except) {
            if (e.equalsIgnoreCase(name)) return true;
        }
        return false;
    }
}
