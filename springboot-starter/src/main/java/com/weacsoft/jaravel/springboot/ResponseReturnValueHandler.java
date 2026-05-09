package com.weacsoft.jaravel.springboot;

import com.weacsoft.jaravel.contract.http.Cookie;
import com.weacsoft.jaravel.contract.http.Response;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

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

            jaravelResponse.getHeaders().forEach(response::setHeader);

            List<Cookie> cookies = jaravelResponse.getCookies();
            if (cookies != null && !cookies.isEmpty()) {
                for (Cookie cookie : cookies) {
                    jakarta.servlet.http.Cookie servletCookie = new jakarta.servlet.http.Cookie(cookie.getName(), cookie.getValue());
                    if (cookie.getPath() != null) {
                        servletCookie.setPath(cookie.getPath());
                    }
                    if (cookie.getDomain() != null) {
                        servletCookie.setDomain(cookie.getDomain());
                    }
                    servletCookie.setMaxAge(cookie.getMaxAge());
                    servletCookie.setSecure(cookie.isSecure());
                    servletCookie.setHttpOnly(cookie.isHttpOnly());
                    String sameSite = cookie.getAttribute("SameSite");
                    if (sameSite != null) {
                        servletCookie.setAttribute("SameSite", sameSite);
                    }
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
