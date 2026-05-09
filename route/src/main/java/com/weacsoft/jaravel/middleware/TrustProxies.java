package com.weacsoft.jaravel.middleware;

import com.weacsoft.jaravel.contract.http.Middleware;
import com.weacsoft.jaravel.contract.http.Request;
import com.weacsoft.jaravel.contract.http.Response;
import com.weacsoft.jaravel.http.request.HttpRequest;

import java.util.Arrays;
import java.util.List;

public class TrustProxies implements Middleware {

    protected static final String X_FORWARDED_FOR = "X-Forwarded-For";
    protected static final String X_FORWARDED_HOST = "X-Forwarded-Host";
    protected static final String X_FORWARDED_PROTO = "X-Forwarded-Proto";
    protected static final String X_FORWARDED_PORT = "X-Forwarded-Port";
    protected static final String X_REAL_IP = "X-Real-IP";

    protected List<String> trustedProxies;

    public TrustProxies() {
        this.trustedProxies = Arrays.asList("127.0.0.1", "::1");
    }

    public TrustProxies(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies;
    }

    @Override
    public Response handle(Request request, NextFunction next) {
        if (isTrustedProxy(request)) {
            setTrustedHeaders(request);
        }
        return next.apply(request);
    }

    protected boolean isTrustedProxy(Request request) {
        if (request instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) request;
            String remoteAddr = httpRequest.getRequest().getRemoteAddr();
            return trustedProxies.contains(remoteAddr);
        }
        return false;
    }

    protected void setTrustedHeaders(Request request) {
        if (!(request instanceof HttpRequest)) {
            return;
        }
        HttpRequest httpRequest = (HttpRequest) request;
        String xForwardedFor = httpRequest.getRequest().getHeader(X_FORWARDED_FOR);
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            String[] ips = xForwardedFor.split(",");
            String realIp = ips[0].trim();
            httpRequest.getRequest().setAttribute("real_ip", realIp);
        }

        String xRealIp = httpRequest.getRequest().getHeader(X_REAL_IP);
        if (xRealIp != null && !xRealIp.isEmpty()) {
            httpRequest.getRequest().setAttribute("real_ip", xRealIp);
        }

        String xForwardedProto = httpRequest.getRequest().getHeader(X_FORWARDED_PROTO);
        if (xForwardedProto != null && !xForwardedProto.isEmpty()) {
            httpRequest.getRequest().setAttribute("real_scheme", xForwardedProto);
        }

        String xForwardedHost = httpRequest.getRequest().getHeader(X_FORWARDED_HOST);
        if (xForwardedHost != null && !xForwardedHost.isEmpty()) {
            httpRequest.getRequest().setAttribute("real_host", xForwardedHost);
        }

        String xForwardedPort = httpRequest.getRequest().getHeader(X_FORWARDED_PORT);
        if (xForwardedPort != null && !xForwardedPort.isEmpty()) {
            httpRequest.getRequest().setAttribute("real_port", xForwardedPort);
        }
    }

    public void setTrustedProxies(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies;
    }

    public void setTrustedProxies(String[] trustedProxies) {
        this.trustedProxies = Arrays.asList(trustedProxies);
    }
}
