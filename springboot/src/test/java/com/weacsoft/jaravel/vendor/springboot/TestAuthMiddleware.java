package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import com.weacsoft.jaravel.vendor.http.middleware.Middleware;
import com.weacsoft.jaravel.vendor.springboot.annotation.MiddlewareAlias;

/**
 * 测试用简单中间件，注册别名 "auth"。
 */
@MiddlewareAlias("auth")
public class TestAuthMiddleware implements Middleware {

    @Override
    public Response handle(Request request, NextFunction next) {
        return next.apply(request);
    }
}
