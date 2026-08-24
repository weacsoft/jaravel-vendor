package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import com.weacsoft.jaravel.vendor.http.middleware.Middleware;
import com.weacsoft.jaravel.vendor.springboot.annotation.MiddlewareAlias;

/**
 * 测试用中间件，标注 @MiddlewareAlias 但不填别名。
 * 用于验证"有注解无别名"场景：按类对象或类名识别。
 */
@MiddlewareAlias
public class TestNoAliasMiddleware implements Middleware {

    @Override
    public Response handle(Request request, NextFunction next, String... params) {
        Response response = next.apply(request);
        response.addHeader("X-NoAlias-Called", "true");
        return response;
    }
}
