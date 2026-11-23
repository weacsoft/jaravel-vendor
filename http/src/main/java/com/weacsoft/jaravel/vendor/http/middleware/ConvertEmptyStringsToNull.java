package com.weacsoft.jaravel.vendor.http.middleware;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;

import java.util.Arrays;
import java.util.Map;

/**
 * 空字符串转 Null 中间件，对齐 Laravel 的 {@code ConvertEmptyStringsToNull}。
 * <p>
 * 将 input 与 query 中的空字符串转为 {@code null}。
 * <p>
 * <b>继承式配置</b>：通过覆盖 {@link #except()} 方法指定排除字段，而非通过构造器传参。
 * 预定义中间件不标注 {@code @MiddlewareAlias}，由使用者继承后自行标注。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @MiddlewareAlias
 * public class AppConvertEmptyStringsToNull extends ConvertEmptyStringsToNull {
 *     @Override
 *     protected String[] except() {
 *         return new String[]{"password", "remark"};
 *     }
 * }
 * }</pre>
 */
public class ConvertEmptyStringsToNull implements Middleware {

    private static final String[] DEFAULT_EXCEPT = {
            "password", "password_confirmation", "current_password"
    };

    @Override
    public Response handle(Request request, NextFunction next, String... params) {
        convertEmptyStringsToNull(request);
        return next.apply(request);
    }

    /**
     * 不转换的字段名数组，子类可覆盖以自定义排除列表。
     * <p>
     * 默认排除 {@code password}、{@code password_confirmation}、{@code current_password}。
     *
     * @return 排除字段名数组
     */
    protected String[] except() {
        return DEFAULT_EXCEPT;
    }

    private void convertEmptyStringsToNull(Request request) {
        // 每次迭代都调用 request.input()/query() 会各产生一次全表拷贝，
        // 参数量为 n 时开销随 n 平方增长；此处先取一次快照再遍历。
        Map<String, Object> inputs = request.input();
        for (Map.Entry<String, Object> entry : inputs.entrySet()) {
            String name = entry.getKey();
            if (isExcluded(name)) continue;
            Object value = entry.getValue();
            if (value instanceof String && ((String) value).isEmpty()) {
                request.replaceInput(name, null);
            }
        }
        Map<String, Object> queries = request.query();
        for (Map.Entry<String, Object> entry : queries.entrySet()) {
            String name = entry.getKey();
            if (isExcluded(name)) continue;
            Object value = entry.getValue();
            if (value instanceof String && ((String) value).isEmpty()) {
                request.replaceQuery(name, null);
            }
        }
    }

    private boolean isExcluded(String name) {
        for (String e : except()) {
            if (e.equalsIgnoreCase(name)) return true;
        }
        return false;
    }
}
