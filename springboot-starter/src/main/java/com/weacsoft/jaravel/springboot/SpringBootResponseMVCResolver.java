package com.weacsoft.jaravel.springboot;

import com.weacsoft.jaravel.http.response.Response;
import jakarta.servlet.http.Cookie;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@ControllerAdvice
public class SpringBootResponseMVCResolver implements ResponseBodyAdvice<Object> {
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof Response) {
            Response jaravelResponse = (Response) body;
            
            jaravelResponse.getHeaders().forEach((key, value) -> response.getHeaders().addAll(key, value));
            
            if (response instanceof ServletServerHttpResponse) {
                ServletServerHttpResponse servletResponse = (ServletServerHttpResponse) response;
                Cookie[] cookies = jaravelResponse.getCookies();
                if (cookies != null && cookies.length > 0) {
                    for (Cookie cookie : cookies) {
                        servletResponse.getServletResponse().addCookie(cookie);
                    }
                }
            }
            
            response.setStatusCode(HttpStatus.valueOf(jaravelResponse.getStatus()));
            return jaravelResponse.getContent();
        }
        
        if (body == null) {
            response.setStatusCode(HttpStatus.OK);
            return "";
        }
        
        if (response.getHeaders().getContentType() == null) {
            response.getHeaders().setContentType(MediaType.TEXT_HTML);
        }
        
        response.getHeaders().add("X-Content-Type-Options", "nosniff");
        response.getHeaders().add("X-Frame-Options", "SAMEORIGIN");
        response.getHeaders().add("X-XSS-Protection", "1; mode=block");
        
        return body;
    }
}
