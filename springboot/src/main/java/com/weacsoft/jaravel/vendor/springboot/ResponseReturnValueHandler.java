package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 处理 Controller 方法直接返回 {@link Response} 类型的返回值，
 * 将状态码、响应头、Cookie 与响应体写入 Servlet 响应。
 * <p>
 * 适配 Spring Boot 3.2.5 / Jakarta Servlet。
 */
public class ResponseReturnValueHandler implements HandlerMethodReturnValueHandler {
    @Override
    public boolean supportsReturnType(MethodParameter returnType) {
        return Response.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public void handleReturnValue(Object returnValue, MethodParameter returnType, ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {
        if (returnValue == null) {
            mavContainer.setRequestHandled(true);
            return;
        }

        Response jaravelResponse = (Response) returnValue;
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);

        if (response != null) {
            response.setStatus(jaravelResponse.getStatus());

            jaravelResponse.getHeaders().forEach((key, values) -> {
                for (String value : values) {
                    response.addHeader(key, value);
                }
            });

            Cookie[] cookies = jaravelResponse.getCookies();
            if (cookies != null && cookies.length > 0) {
                for (Cookie cookie : cookies) {
                    jakarta.servlet.http.Cookie servletCookie = new jakarta.servlet.http.Cookie(cookie.getName(), cookie.getValue());
                    servletCookie.setPath(cookie.getPath());
                    servletCookie.setDomain(cookie.getDomain());
                    servletCookie.setMaxAge(cookie.getMaxAge());
                    servletCookie.setSecure(cookie.getSecure());
                    servletCookie.setHttpOnly(cookie.isHttpOnly());
                    servletCookie.setAttribute("SameSite", cookie.getAttribute("SameSite"));
                    response.addCookie(servletCookie);
                }
            }

            byte[] bytes = jaravelResponse.getBytes();
            if (bytes != null) {
                response.getOutputStream().write(bytes);
                response.getOutputStream().flush();
            } else {
                String content = jaravelResponse.getContent();
                if (content != null) {
                    response.getWriter().write(content);
                    response.getWriter().flush();
                }
            }
        }

        mavContainer.setRequestHandled(true);
    }
}
