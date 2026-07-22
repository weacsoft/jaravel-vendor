package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.http.controller.Controllers;
import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import com.weacsoft.jaravel.vendor.http.controller.response.ResponseBuilder;

/**
 * 测试用控制器，实现 {@link Controllers} 接口。
 * 用于验证控制器扫描注册和字符串/类对象引用解析。
 */
public class TestController implements Controllers {

    public Response ping(Request request) {
        return ResponseBuilder.content("pong");
    }

    public Response echo(Request request) {
        String msg = request.get("msg") != null ? request.get("msg") : "empty";
        return ResponseBuilder.content(msg);
    }
}
