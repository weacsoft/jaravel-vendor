package com.weacsoft.jaravel.vendor.http.middleware;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 字符串裁剪中间件，对齐 Laravel 的 {@code TrimStrings}。
 * <p>
 * 自动裁剪 query 与 input 参数中字符串值的首尾空白。
 * <p>
 * <b>继承式配置</b>：通过覆盖 {@link #except()} 方法指定不裁剪的字段名，而非通过构造器传参。
 * 预定义中间件不标注 {@code @MiddlewareAlias}，由使用者继承后自行标注。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @MiddlewareAlias
 * public class AppTrimStrings extends TrimStrings {
 *     @Override
 *     protected String[] except() {
 *         return new String[]{"password", "password_confirmation"};
 *     }
 * }
 * }</pre>
 */
public class TrimStrings implements Middleware {

    @Override
    public Response handle(Request request, NextFunction next, String... params) {
        // 排除列表在入口处取一次，避免每个参数都重建数组并包装成 List
        Set<String> excluded = excludedNames();
        trimQueryParameters(request, excluded);
        trimInputParameters(request, excluded);
        return next.apply(request);
    }

    /**
     * 不裁剪的字段名数组，子类可覆盖以自定义排除列表。
     *
     * @return 排除字段名数组，默认为空
     */
    protected String[] except() {
        return new String[0];
    }

    /**
     * 把 {@link #except()} 转为集合，供一次请求内的所有参数判断复用。
     *
     * @return 排除字段名集合
     */
    private Set<String> excludedNames() {
        String[] names = except();
        if (names == null || names.length == 0) {
            return Set.of();
        }
        return Set.of(names);
    }

    protected void trimQueryParameters(Request request) {
        trimQueryParameters(request, excludedNames());
    }

    protected void trimInputParameters(Request request) {
        trimInputParameters(request, excludedNames());
    }

    protected void trimQueryParameters(Request request, Set<String> excluded) {
        Map<String, Object> queryParams = request.query();
        if (queryParams != null) {
            queryParams.forEach((key, value) -> {
                if (!excluded.contains(key)) {
                    Object trimmedValue = trimValue(value);
                    request.replaceQuery(key, trimmedValue);
                }
            });
        }
    }

    protected void trimInputParameters(Request request, Set<String> excluded) {
        Map<String, Object> inputParams = request.input();
        if (inputParams != null) {
            inputParams.forEach((key, value) -> {
                if (!excluded.contains(key)) {
                    Object trimmedValue = trimValue(value);
                    request.replaceInput(key, trimmedValue);
                }
            });
        }
    }

    protected boolean isExcluded(String key) {
        return excludedNames().contains(key);
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
