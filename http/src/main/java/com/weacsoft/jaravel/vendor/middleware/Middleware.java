package com.weacsoft.jaravel.vendor.middleware;

import com.weacsoft.jaravel.vendor.http.request.Request;
import com.weacsoft.jaravel.vendor.http.response.Response;

@FunctionalInterface
public interface Middleware {
    Response handle(Request request, NextFunction next);

    @FunctionalInterface
    interface NextFunction {
        Response apply(Request request);
    }
}