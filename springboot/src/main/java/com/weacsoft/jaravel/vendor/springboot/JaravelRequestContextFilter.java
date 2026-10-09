package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.http.controller.request.RequestFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 请求上下文清理过滤器：保证每个请求结束后清掉
 * {@link RequestFactory} 的 {@code ThreadLocal<Request>}。
 * <p>
 * <b>为什么必须有它</b>：Servlet 容器复用工作线程，而
 * {@code RequestFactory.setCurrentRequest(...)} 只在请求处理期间被调用；
 * 一旦不在请求结束时清理，线程上下一次处理别的请求（可能是另一个用户）时，
 * 仍会读到上一次的 {@code Request} —— 而 SessionStore / SessionGuard 都是通过
 * {@code RequestFactory.getCurrentRequest()} 取 session 的，于是出现
 * <b>跨用户会话串读</b>；反之若该线程没有历史值，则静默返回 null 导致认证失效。
 * <p>
 * 顺序设为最高优先级：它是洋葱的最外层，{@code finally} 在整条链（含 MVC handler）
 * 执行完之后才运行，确保清理时机正确。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JaravelRequestContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestFactory.clearCurrentRequest();
        }
    }
}