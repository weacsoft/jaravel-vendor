package com.weacsoft.jaravel.middleware;

import com.weacsoft.jaravel.http.request.Request;
import com.weacsoft.jaravel.http.response.Response;

@FunctionalInterface
public interface Middleware {
    Response handle(Request request, NextFunction next);

    @FunctionalInterface
    interface NextFunction {
        Response apply(Request request);
    }
}
