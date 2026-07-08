package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.http.response.Response;
import jakarta.servlet.http.Cookie;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Spring MVC 响应处理器：当 Controller 方法返回 {@link Response} 时，
 * 应用其状态码、响应头、Cookie 与内容；其余返回值补充安全响应头。
 * <p>
 * 适配 Spring Boot 3.2.5 / Jakarta Servlet。使用 Spring 6.x 的
 * {@code @org.springframework.web.bind.annotation.ControllerAdvice}（jakarta 版本）。
 */
@org.springframework.web.bind.annotation.ControllerAdvice
public class SpringBootResponseMVCResolver implements ResponseBodyAdvice<Object> {
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof Response jaravelResponse) {

            jaravelResponse.getHeaders().forEach((key, value) -> response.getHeaders().addAll(key, value));

            // 兜底 Content-Type：如果 Response 没有设置，使用默认值（text/plain）
            if (response.getHeaders().getContentType() == null) {
                response.getHeaders().setContentType(MediaType.parseMediaType(jaravelResponse.getContentType()));
            }

            if (response instanceof ServletServerHttpResponse servletResponse) {
                Cookie[] cookies = jaravelResponse.getCookies();
                if (cookies != null) {
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
