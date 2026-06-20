package com.weacsoft.jaravel.vendor.middleware;

import com.weacsoft.jaravel.vendor.http.request.Request;
import com.weacsoft.jaravel.vendor.http.response.Response;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 信任代理中间件，对齐 Laravel 的 {@code TrustProxies}。
 * <p>
 * 无状态、不可变，可被 Spring 容器管理并安全地在并发请求间复用。
 * 所有字段为 {@code final}，构造后不可修改。
 */
@Component
public class TrustProxies implements Middleware {

    protected static final String X_FORWARDED_FOR = "X-Forwarded-For";
    protected static final String X_FORWARDED_HOST = "X-Forwarded-Host";
    protected static final String X_FORWARDED_PROTO = "X-Forwarded-Proto";
    protected static final String X_FORWARDED_PORT = "X-Forwarded-Port";
    protected static final String X_REAL_IP = "X-Real-IP";

    private final List<String> trustedProxies;

    public TrustProxies() {
        this.trustedProxies = Arrays.asList("127.0.0.1", "::1");
    }

    public TrustProxies(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies;
    }

    public TrustProxies(String[] trustedProxies) {
        this.trustedProxies = Arrays.asList(trustedProxies);
    }

    @Override
    public Response handle(Request request, NextFunction next) {
        if (isTrustedProxy(request)) {
            setTrustedHeaders(request);
        }
        return next.apply(request);
    }

    protected boolean isTrustedProxy(Request request) {
        String remoteAddr = request.getRequest().getRemoteAddr();
        return trustedProxies.contains(remoteAddr);
    }

    protected void setTrustedHeaders(Request request) {
        String xForwardedFor = request.getRequest().getHeader(X_FORWARDED_FOR);
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            String[] ips = xForwardedFor.split(",");
            String realIp = ips[0].trim();
            request.getRequest().setAttribute("real_ip", realIp);
        }

        String xRealIp = request.getRequest().getHeader(X_REAL_IP);
        if (xRealIp != null && !xRealIp.isEmpty()) {
            request.getRequest().setAttribute("real_ip", xRealIp);
        }

        String xForwardedProto = request.getRequest().getHeader(X_FORWARDED_PROTO);
        if (xForwardedProto != null && !xForwardedProto.isEmpty()) {
            request.getRequest().setAttribute("real_scheme", xForwardedProto);
        }

        String xForwardedHost = request.getRequest().getHeader(X_FORWARDED_HOST);
        if (xForwardedHost != null && !xForwardedHost.isEmpty()) {
            request.getRequest().setAttribute("real_host", xForwardedHost);
        }

        String xForwardedPort = request.getRequest().getHeader(X_FORWARDED_PORT);
        if (xForwardedPort != null && !xForwardedPort.isEmpty()) {
            request.getRequest().setAttribute("real_port", xForwardedPort);
        }
    }
}