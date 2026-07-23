package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import com.weacsoft.jaravel.vendor.http.controller.response.ResponseBuilder;
import org.springframework.stereotype.Controller;

/**
 * 测试用纯 Spring {@code @Controller} 控制器（不实现 {@link com.weacsoft.jaravel.vendor.http.controller.Controllers} 接口）。
 * <p>
 * 用于验证框架能自动扫描并注册仅标注了 Spring {@code @Controller} 注解的控制器，
 * 无需手动实现 {@code Controllers} 接口或调用 {@code ControllerRegistry.register()}。
 */
@Controller
public class SpringTestController {

    public Response index(Request request) {
        return ResponseBuilder.content("spring-index");
    }

    public Response hello(Request request) {
        return ResponseBuilder.content("spring-hello");
    }
}
