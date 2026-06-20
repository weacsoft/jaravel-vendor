package com.weacsoft.jaravel.vendor.springboot;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * 响应自动装配：将 {@link ResponseReturnValueHandler} 前置注入到
 * {@link RequestMappingHandlerAdapter} 的返回值处理器链，使 Controller 方法
 * 可直接返回 {@link com.weacsoft.jaravel.vendor.http.response.Response}。
 * <p>
 * 适配 Spring Boot 3.2.5 / Jakarta Servlet。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ResponseAutoConfiguration {

    public ResponseAutoConfiguration(RequestMappingHandlerAdapter requestMappingHandlerAdapter) {
        List<HandlerMethodReturnValueHandler> handlers = new ArrayList<>();

        List<HandlerMethodReturnValueHandler> originalHandlers = requestMappingHandlerAdapter.getReturnValueHandlers();
        if (originalHandlers != null) {
            handlers.addAll(originalHandlers);
        }

        handlers.add(0, new ResponseReturnValueHandler());

        requestMappingHandlerAdapter.setReturnValueHandlers(handlers);
    }
}
