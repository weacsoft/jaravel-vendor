package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import com.weacsoft.jaravel.vendor.http.middleware.MiddlewareResolver;
import com.weacsoft.jaravel.vendor.springboot.annotation.MiddlewareAlias;

/**
 * 测试用参数化中间件解析器，注册别名 "log"。
 * 根据参数返回不同中间件实例。
 */
@MiddlewareAlias("log")
public class TestLogMiddlewareResolver implements MiddlewareResolver {

    @Override
    public com.weacsoft.jaravel.vendor.http.middleware.Middleware resolve(String... params) {
        String channel = params.length > 0 ? params[0] : "default";
        return (request, next) -> {
            Response response = next.apply(request);
            response.addHeader("X-Log-Channel", channel);
            return response;
        };
    }
}
