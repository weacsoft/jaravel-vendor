package com.weacsoft.jaravel.springboot;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.util.ArrayList;
import java.util.List;

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
