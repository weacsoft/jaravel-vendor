package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import com.weacsoft.jaravel.vendor.http.middleware.Middleware;
import com.weacsoft.jaravel.vendor.springboot.annotation.MiddlewareAlias;

/**
 * 测试用中间件，注册别名 "log"。
 * params[0] 作为 channel 写入响应头，用于验证参数传递。
 */
@MiddlewareAlias("log")
public class TestLogMiddleware implements Middleware {

    @Override
    public Response handle(Request request, NextFunction next, String... params) {
        String channel = params.length > 0 ? params[0] : "default";
        Response response = next.apply(request);
        response.addHeader("X-Log-Channel", channel);
        return response;
    }
}
