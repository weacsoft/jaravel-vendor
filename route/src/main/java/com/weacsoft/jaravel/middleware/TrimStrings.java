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
        request.queryNames().forEach(queryName -> {
            if(!isExcluded(queryName)){
                request.queries(queryName).replaceAll(String::trim);
            }
        });
    }

    protected void trimInputParameters(Request request) {
        request.inputNames().forEach(queryName -> {
            if(!isExcluded(queryName)){
                request.inputs(queryName).replaceAll(String::trim);
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
