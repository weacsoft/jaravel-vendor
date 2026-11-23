package com.weacsoft.jaravel.vendor.http.middleware;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import com.weacsoft.jaravel.vendor.utils.net.IpMatcher;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    /**
     * 已发布的受信任代理匹配器：中间件实现类 -&gt; 该实例的 IP 匹配器。
     * <p>
     * 由本中间件在处理请求时发布，供
     * {@link com.weacsoft.jaravel.vendor.http.controller.request.Request#ip()} /
     * {@code fullUrl()} 判断「本次请求的直连来源是否可信」。
     * <p>
     * <b>未发布（未安装本中间件）时无任何匹配器 → 一律不采信转发头</b>，
     * 因此默认是安全行为：客户端无法通过伪造 {@code X-Forwarded-For} 冒充来源 IP。
     * <p>
     * 按实现类分别保存而非保存单个全局匹配器，避免同一进程内存在多个
     * {@code TrustProxies} 子类时后写入者覆盖先写入者的信任列表。
     */
    private static final Map<Class<?>, IpMatcher> PUBLISHED_MATCHERS = new ConcurrentHashMap<>();

    /**
     * 判断某个地址是否为「已声明的受信任代理」。
     * <p>
     * 供 {@code Request.ip()} / {@code Request.fullUrl()} 使用；未配置任何受信任代理时恒为 false。
     * <p>
     * 同一进程内存在多个 {@code TrustProxies} 实现时，只要其中任一实现声明该地址受信任，
     * 即视为受信任；每个实现各自声明的信任列表都完整生效。
     *
     * @param remoteAddr 直连来源（或转发链中的某一跳）地址
     * @return 受信任返回 true
     */
    public static boolean isTrustedRemote(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isEmpty()) {
            return false;
        }
        for (IpMatcher matcher : PUBLISHED_MATCHERS.values()) {
            if (matcher != null && matcher.matches(remoteAddr)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从转发链中解析真实客户端地址：<b>从右往左</b>跳过受信任代理，取第一个不受信任的地址。
     * <p>
     * {@code X-Forwarded-For} 是「每经过一跳代理就追加」的链，越靠左越可能是客户端自己伪造的；
     * 因此不能像旧实现那样直接取最左侧值。
     *
     * @param xForwardedFor 转发头原始值（可含多段，逗号分隔）
     * @return 真实客户端地址；无法判定返回 null
     */
    public static String clientIpFromForwarded(String xForwardedFor) {
        if (xForwardedFor == null || xForwardedFor.isEmpty()) {
            return null;
        }
        String[] hops = xForwardedFor.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String candidate = hops[i].trim();
            if (!candidate.isEmpty() && !isTrustedRemote(candidate)) {
                return candidate;
            }
        }
        return hops[0].trim();
    }

    @Override
    public Response handle(Request request, NextFunction next, String... params) {
        // 先发布匹配器：Request.ip()/fullUrl() 据此决定是否采信转发头
        // 按实现类分别发布，避免同进程多个子类互相覆盖信任列表
        PUBLISHED_MATCHERS.put(getClass(), getMatcher());
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
        String remoteAddr = request.remoteAddr();
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
        String xForwardedFor = request.header(X_FORWARDED_FOR);
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // 从右往左解析（最左侧可能是客户端伪造的），而不是直接取 ips[0]
            String realIp = clientIpFromForwarded(xForwardedFor);
            if (realIp != null && !realIp.isEmpty()) {
                request.setAttribute("real_ip", realIp);
            }
        }

        String xRealIp = request.header(X_REAL_IP);
        if (xRealIp != null && !xRealIp.isEmpty()) {
            request.setAttribute("real_ip", xRealIp.trim());
        }

        String xForwardedProto = request.header(X_FORWARDED_PROTO);
        if (xForwardedProto != null && !xForwardedProto.isEmpty()) {
            request.setAttribute("real_scheme", xForwardedProto);
        }

        String xForwardedHost = request.header(X_FORWARDED_HOST);
        if (xForwardedHost != null && !xForwardedHost.isEmpty()) {
            request.setAttribute("real_host", xForwardedHost);
        }

        String xForwardedPort = request.header(X_FORWARDED_PORT);
        if (xForwardedPort != null && !xForwardedPort.isEmpty()) {
            request.setAttribute("real_port", xForwardedPort);
        }
    }
}
