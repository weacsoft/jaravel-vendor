package com.weacsoft.jaravel.middleware;

import com.weacsoft.jaravel.contract.http.Middleware;
import com.weacsoft.jaravel.contract.http.Request;
import com.weacsoft.jaravel.contract.http.Response;

import java.util.Arrays;
import java.util.List;

public class TrimStrings implements Middleware {

    protected String[] except = new String[0];

    @Override
    public Response handle(Request request, NextFunction next) {
        trimQueryParameters(request);
        trimInputParameters(request);
        return next.apply(request);
    }

    protected void trimQueryParameters(Request request) {
        request.queryNames().forEach(queryName -> {
            if (!isExcluded(queryName) && request instanceof Request) {
                List<String> values = request.queries(queryName);
                if (values != null) {
                    values.replaceAll(String::trim);
                }
            }
        });
    }

    protected void trimInputParameters(Request request) {
        request.inputNames().forEach(inputName -> {
            if (!isExcluded(inputName) && request instanceof Request) {
                List<String> values = request.inputs(inputName);
                if (values != null) {
                    values.replaceAll(String::trim);
                }
            }
        });
    }

    protected boolean isExcluded(String key) {
        return Arrays.asList(except).contains(key);
    }

    public void setExcept(String[] except) {
        this.except = except;
    }
}
