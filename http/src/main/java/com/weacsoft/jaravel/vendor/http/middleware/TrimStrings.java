package com.weacsoft.jaravel.vendor.http.middleware;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 字符串裁剪中间件，对齐 Laravel 的 {@code TrimStrings}。
 * <p>
 * 无状态、不可变，可被 Spring 容器管理并安全地在并发请求间复用。
 * 所有字段为 {@code final}，构造后不可修改。
 */
@Component
public class TrimStrings implements Middleware {

    private final String[] except;

    public TrimStrings() {
        this(new String[0]);
    }

    public TrimStrings(String[] except) {
        this.except = except != null ? except : new String[0];
    }

    @Override
    public Response handle(Request request, NextFunction next) {
        trimQueryParameters(request);
        trimInputParameters(request);
        return next.apply(request);
    }

    protected void trimQueryParameters(Request request) {
        Map<String, Object> queryParams = request.query();
        if (queryParams != null) {
            queryParams.forEach((key, value) -> {
                if (!isExcluded(key)) {
                    Object trimmedValue = trimValue(value);
                    request.replaceQuery(key, trimmedValue);
                }
            });
        }
    }

    protected void trimInputParameters(Request request) {
        Map<String, Object> inputParams = request.input();
        if (inputParams != null) {
            inputParams.forEach((key, value) -> {
                if (!isExcluded(key)) {
                    Object trimmedValue = trimValue(value);
                    request.replaceInput(key, trimmedValue);
                }
            });
        }
    }

    protected boolean isExcluded(String key) {
        return Arrays.asList(except).contains(key);
    }

    protected Object trimValue(Object value) {
        if (value instanceof String) {
            return ((String) value).trim();
        } else if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                Object o = item instanceof String ? ((String) item).trim() : item;
                result.add(o);
            }
            return result;
        } else if (value instanceof String[] array) {
            return Arrays.stream(array)
                    .map(String::trim)
                    .toArray(String[]::new);
        }
        return value;
    }
}
