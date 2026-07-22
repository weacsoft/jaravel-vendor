package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.request.RequestFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Spring MVC 参数解析器，使 Controller 方法可直接声明 {@link Request} 类型参数并自动注入。
 * <p>
 * 适配 Spring Boot 3.2.5 / Jakarta Servlet。
 * <p>
 * 不标注 {@code @Component}，由 {@link SpringBootRouteAutoConfiguration} 通过 {@code @Bean} 注册，
 * 与同模块其他解析器（如 {@code ResponseReturnValueHandler}）风格统一。
 */
public class SpringBootRequestMVCResolver implements HandlerMethodArgumentResolver {
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(Request.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        Request request = RequestFactory.buildFromHttpServletRequest((HttpServletRequest) webRequest.getNativeRequest());
        return request;
    }
}
