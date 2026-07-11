package com.weacsoft.jaravel.vendor.http.middleware;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;

@FunctionalInterface
public interface Middleware {
    Response handle(Request request, NextFunction next);

    @FunctionalInterface
    interface NextFunction {
        Response apply(Request request);
    }
}
