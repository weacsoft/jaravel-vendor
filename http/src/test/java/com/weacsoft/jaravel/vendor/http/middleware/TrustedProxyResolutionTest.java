package com.weacsoft.jaravel.vendor.http.middleware;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.response.ResponseBuilder;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 受信任代理（审计 S3）回归测试：转发头<b>只在直连来源已被声明为受信任代理时</b>才采信。
 * <p>
 * 旧实现无条件读取 {@code X-Forwarded-For} 并取最左侧值 —— 客户端自己塞一个
 * {@code X-Forwarded-For: 1.2.3.4} 就能冒充来源 IP（限流/审计/白名单全部失真），
 * {@code X-Forwarded-Proto/Host} 同理可伪造协议与 Host（影响回调地址、跳转、cookie 域）。
 */
class TrustedProxyResolutionTest {

    private static final String DIRECT_PEER = "203.0.113.9";
    private static final String PROXY_PEER = "127.0.0.1";

    @BeforeEach
    @AfterEach
    void resetGlobalMatcher() throws Exception {
        // 全局匹配器是进程级状态：每个用例前后都清空，避免用例间互相影响
        Field field = TrustProxies.class.getDeclaredField("globalMatcher");
        field.setAccessible(true);
        field.set(null, null);
    }

    private static Request request(String remoteAddr, Map<String, String> headers) {
        HttpServletRequest servlet = mock(HttpServletRequest.class);
        when(servlet.getRemoteAddr()).thenReturn(remoteAddr);
        when(servlet.isSecure()).thenReturn(false);
        when(servlet.getServerName()).thenReturn("internal.local");
        when(servlet.getServerPort()).thenReturn(8080);
        when(servlet.getRequestURI()).thenReturn("/x");
        when(servlet.getQueryString()).thenReturn(null);
        when(servlet.getMethod()).thenReturn("GET");
        when(servlet.getContentType()).thenReturn(null);
        when(servlet.getContextPath()).thenReturn("");
        when(servlet.getServletPath()).thenReturn("");
        when(servlet.getParameterMap()).thenReturn(new LinkedHashMap<>());
        when(servlet.getCookies()).thenReturn(null);
        // Request.setRequest 会遍历 getHeaderNames() 把请求头拷进内部 map —— 必须给出真实枚举，
        // 否则 header("X-Forwarded-For") 恒为 null，测试会「假绿/假红」。
        when(servlet.getHeaderNames()).thenReturn(java.util.Collections.enumeration(headers.keySet()));
        when(servlet.getHeaders(anyString())).thenAnswer(invocation -> {
            String value = headers.get(invocation.getArgument(0));
            return value == null
                    ? java.util.Collections.emptyEnumeration()
                    : java.util.Collections.enumeration(java.util.List.of(value));
        });
        when(servlet.getHeader(anyString())).thenAnswer(invocation -> headers.get(invocation.getArgument(0)));
        Request request = new Request();
        request.setRequest(servlet);
        return request;
    }

    private static Map<String, String> headers(String forwardedFor, String proto, String host) {
        Map<String, String> map = new LinkedHashMap<>();
        if (forwardedFor != null) {
            map.put("X-Forwarded-For", forwardedFor);
        }
        if (proto != null) {
            map.put("X-Forwarded-Proto", proto);
        }
        if (host != null) {
            map.put("X-Forwarded-Host", host);
        }
        map.put("Host", "real.example.com");
        return map;
    }

    // ==================== 纯函数：转发链解析 ====================

    @Test
    void forwardedChainIsParsedRightToLeftSkippingTrustedHops() {
        // 未经任何受信任代理声明：任何一跳都不被信任 → 返回最左侧（保持旧行为的下界）
        assertEquals("1.2.3.4", TrustProxies.clientIpFromForwarded("1.2.3.4"));
        assertEquals("1.2.3.4", TrustProxies.clientIpFromForwarded(" 1.2.3.4 "));
        assertNull(TrustProxies.clientIpFromForwarded(null));
        assertNull(TrustProxies.clientIpFromForwarded(""));
    }

    // ==================== 未声明受信任代理：一律不采信转发头 ====================

    @Test
    void forwardedHeadersAreIgnoredWithoutTrustedProxyDeclaration() {
        Request request = request(DIRECT_PEER, headers("1.2.3.4", "https", "evil.example.com"));

        assertEquals(DIRECT_PEER, request.ip(), "未声明受信任代理时不得采信 X-Forwarded-For");
        assertFalse(request.fullUrl().startsWith("https://evil.example.com"),
                "未声明受信任代理时不得采信 X-Forwarded-Proto/Host，实际: " + request.fullUrl());
    }

    @Test
    void untrustedPeerIsNotTrustedEvenWhenMiddlewareMatchesOthers() {
        // 发布匹配器（默认信任 127.0.0.1/::1），但请求直连来源是公网地址 → 仍不采信
        publishMatcher();
        Request request = request(DIRECT_PEER, headers("1.2.3.4", "https", "evil.example.com"));

        assertEquals(DIRECT_PEER, request.ip(), "非受信任来源的转发头必须忽略");
        assertFalse(TrustProxies.isTrustedRemote(DIRECT_PEER));
    }

    // ==================== 已声明受信任代理：采信并正确解析 ====================

    @Test
    void forwardedForIsHonoredWhenPeerIsTrustedProxy() {
        publishMatcher();
        Request request = request(PROXY_PEER, headers("1.2.3.4", null, null));

        assertTrue(TrustProxies.isTrustedRemote(PROXY_PEER));
        assertEquals("1.2.3.4", request.ip(), "直连来源受信任时应采信转发头");
    }

    @Test
    void spoofedLeftmostHopIsNotTakenWhenProxiesAreTrusted() {
        publishMatcher();
        // 客户端伪造最左侧 + 受信任代理追加真实地址：必须从右往左取第一个不受信任的地址
        Request request = request(PROXY_PEER, headers("9.9.9.9, 1.2.3.4", null, null));

        assertEquals("1.2.3.4", request.ip(),
                "应从右往左解析，不能被客户端伪造的最左侧地址欺骗");
    }

    @Test
    void forwardedProtoAndHostAreHonoredWhenPeerIsTrustedProxy() {
        publishMatcher();
        Request request = request(PROXY_PEER, headers(null, "https", "public.example.com"));

        String url = request.fullUrl();
        assertTrue(url.startsWith("https://public.example.com/x"), "受信任代理下应还原真实入口: " + url);
    }

    /** 让中间件发布全局匹配器（默认信任 127.0.0.1 / ::1） */
    private static void publishMatcher() {
        new TrustProxies().handle(request(PROXY_PEER, headers(null, null, null)),
                req -> ResponseBuilder.ok());
    }
}