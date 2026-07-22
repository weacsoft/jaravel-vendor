package com.weacsoft.jaravel.vendor.http.middleware;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;

import java.util.Arrays;
import java.util.List;

/**
 * 信任代理中间件，对齐 Laravel 的 {@code TrustProxies}。
 * <p>
 * 当请求来自受信任的代理时，从 {@code X-Forwarded-*} 等头中还原真实客户端信息。
 * <p>
 * <b>继承式配置</b>：通过覆盖 {@link #trustedProxies()} 方法指定信任代理 IP 列表，而非通过构造器传参。
 * 预定义中间件不标注 {@code @MiddlewareAlias}，由使用者继承后自行标注。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @MiddlewareAlias
 * public class AppTrustProxies extends TrustProxies {
 *     @Override
 *     protected List<String> trustedProxies() {
 *         return Arrays.asList("127.0.0.1", "10.0.0.1", "::1");
 *     }
 * }
 * }</pre>
 */
public class TrustProxies implements Middleware {

    protected static final String X_FORWARDED_FOR = "X-Forwarded-For";
    protected static final String X_FORWARDED_HOST = "X-Forwarded-Host";
    protected static final String X_FORWARDED_PROTO = "X-Forwarded-Proto";
    protected static final String X_FORWARDED_PORT = "X-Forwarded-Port";
    protected static final String X_REAL_IP = "X-Real-IP";

    @Override
    public Response handle(Request request, NextFunction next, String... params) {
        if (isTrustedProxy(request)) {
            setTrustedHeaders(request);
        }
        return next.apply(request);
    }

    /**
     * 受信任的代理 IP 列表，子类可覆盖以自定义。
     *
     * @return 信任代理 IP 列表，默认为 {@code ["127.0.0.1", "::1"]}
     */
    protected List<String> trustedProxies() {
        return Arrays.asList("127.0.0.1", "::1");
    }

    protected boolean isTrustedProxy(Request request) {
        String remoteAddr = request.getRequest().getRemoteAddr();
        return trustedProxies().contains(remoteAddr);
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
