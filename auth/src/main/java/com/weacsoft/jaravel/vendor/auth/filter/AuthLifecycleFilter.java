package com.weacsoft.jaravel.vendor.auth.filter;

import com.weacsoft.jaravel.vendor.auth.AuthContext;
import com.weacsoft.jaravel.vendor.auth.AuthManager;
import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 认证生命周期过滤器：每个请求开始时绑定 {@link AuthContext}，结束时清理 ThreadLocal，
 * 对齐 Laravel 每个请求独立的认证上下文。
 */
public class AuthLifecycleFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthLifecycleFilter.class);

    private final AuthManager authManager;

    public AuthLifecycleFilter(AuthManager authManager) {
        this.authManager = authManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            Request req = new Request();
            req.setRequest(request);
            AuthContext.set(req);
            filterChain.doFilter(request, response);
        } finally {
            AuthContext.clear();
            authManager.clear();
        }
    }
}
