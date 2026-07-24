package com.weacsoft.jaravel.vendor.http.middleware;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import com.weacsoft.jaravel.vendor.utils.net.IpMatcher;

import java.util.Arrays;
import java.util.List;

/**
 * 信任代理中间件，对齐 Laravel 的 {@code TrustProxies}。
 * <p>
 * 当请求来自受信任的代理时，从 {@code X-Forwarded-*} 等头中还原真实客户端信息。
 * <p>
 * <b>IP 匹配能力</b>：通过 {@link IpMatcher} 支持三种表达式格式，子类只需在
 * {@link #trustedProxies()} 中返回字符串列表即可：
 * <ul>
 *   <li>单个 IP：{@code "127.0.0.1"}、{@code "::1"}</li>
 *   <li>CIDR 掩码：{@code "120.236.146.0/23"}、{@code "10.0.0.0/8"}</li>
 *   <li>IP 范围：{@code "192.168.1.10-192.168.1.20"}（起止用 {@code -} 连接）</li>
 * </ul>
 * 同时支持 IPv4 和 IPv6，可混合使用。
 * <p>
 * <b>继承式配置</b>：通过覆盖 {@link #trustedProxies()} 方法指定信任代理 IP 列表，
 * 而非通过构造器传参。预定义中间件不标注 {@code @MiddlewareAlias}，由使用者继承后自行标注。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @MiddlewareAlias
 * public class AppTrustProxies extends TrustProxies {
 *     @Override
 *     protected List<String> trustedProxies() {
 *         return Arrays.asList(
 *             "127.0.0.1",           // 单个 IP
 *             "10.0.0.0/8",          // CIDR 网段
 *             "120.236.146.0/23",    // CIDR 网段
 *             "192.168.1.1-192.168.1.100",  // IP 范围
 *             "::1"                  // IPv6 本地回环
 *         );
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

    /**
     * 缓存的 IP 匹配器，在首次使用时懒初始化。
     * <p>
     * volatile 保证多线程可见性，构造逻辑无锁（重复构造无害，最终一致即可）。
     */
    private volatile IpMatcher cachedMatcher;

    @Override
    public Response handle(Request request, NextFunction next, String... params) {
        if (isTrustedProxy(request)) {
            setTrustedHeaders(request);
        }
        return next.apply(request);
    }

    /**
     * 受信任的代理 IP 列表，子类可覆盖以自定义。
     * <p>
     * 每项可为单个 IP、CIDR 掩码（如 {@code "10.0.0.0/8"}）或 IP 范围
     * （如 {@code "192.168.1.1-192.168.1.100"}），支持 IPv4 和 IPv6。
     *
     * @return 信任代理 IP 表达式列表，默认为 {@code ["127.0.0.1", "::1"]}
     */
    protected List<String> trustedProxies() {
        return Arrays.asList("127.0.0.1", "::1");
    }

    protected boolean isTrustedProxy(Request request) {
        String remoteAddr = request.getRequest().getRemoteAddr();
        return getMatcher().matches(remoteAddr);
    }

    /**
     * 获取（懒初始化）IP 匹配器。
     * <p>
     * 基于 {@link #trustedProxies()} 返回的列表构造 {@link IpMatcher} 并缓存。
     * 若子类动态修改了 {@code trustedProxies()} 的返回值，可调用
     * {@link #refreshMatcher()} 清除缓存。
     *
     * @return IP 匹配器
     */
    protected IpMatcher getMatcher() {
        if (cachedMatcher == null) {
            cachedMatcher = new IpMatcher(trustedProxies());
        }
        return cachedMatcher;
    }

    /**
     * 清除缓存的 IP 匹配器，下次调用 {@link #getMatcher()} 时重新构造。
     * <p>
     * 适用于子类需要动态更新 {@link #trustedProxies()} 的场景。
     */
    protected void refreshMatcher() {
        cachedMatcher = null;
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
