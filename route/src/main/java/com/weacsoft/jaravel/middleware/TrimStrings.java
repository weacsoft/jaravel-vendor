package com.weacsoft.jaravel.middleware;

import com.weacsoft.jaravel.http.request.Request;
import com.weacsoft.jaravel.http.response.Response;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TrimStrings implements Middleware {

    protected String[] except = new String[0];

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
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                Object o = item instanceof String ? ((String) item).trim() : item;
                result.add(o);
            }
            return result;
        } else if (value instanceof String[]) {
            String[] array = (String[]) value;
            return Arrays.stream(array)
                    .map(String::trim)
                    .toArray(String[]::new);
        }
        return value;
    }

    public void setExcept(String[] except) {
        this.except = except;
    }
}
