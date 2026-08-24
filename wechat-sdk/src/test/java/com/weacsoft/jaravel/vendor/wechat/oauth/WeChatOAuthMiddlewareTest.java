package com.weacsoft.jaravel.vendor.wechat.oauth;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import com.weacsoft.jaravel.vendor.http.controller.response.ResponseBuilder;
import com.weacsoft.jaravel.vendor.http.session.SessionStore;
import com.weacsoft.jaravel.vendor.wechat.WechatProperties;
import com.weacsoft.jaravel.vendor.wechat.response.WeChatResponse;
import com.weacsoft.jaravel.vendor.wechat.transport.WechatTransport;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 网页授权自动重定向中间件（WeChatOAuthMiddleware）测试：
 * 已授权放行 / 回调换 openid 存会话回跳 / state 防 CSRF / 发起授权 302，对齐 overtrue OAuthAuthenticate 语义。
 */
class WeChatOAuthMiddlewareTest {

    private static final String APPID = "wxmid1112223334445";

    private WechatTransport transport;
    private WeChatOAuth oauth;
    private InMemoryStore store;

    private final class InMemoryStore implements SessionStore {
        final Map<String, Object> map = new HashMap<>();

        @Override
        public Object get(String key) {
            return map.get(key);
        }

        @Override
        public void put(String key, Object value) {
            map.put(key, value);
        }

        @Override
        public void remove(String key) {
            map.remove(key);
        }

        @Override
        public void destroy() {
            map.clear();
        }
    }

    @BeforeEach
    void setUp() {
        WechatProperties props = new WechatProperties();
        WechatProperties.OfficialAccountConfig cfg = new WechatProperties.OfficialAccountConfig();
        cfg.setAppId(APPID);
        cfg.setSecret("s");
        cfg.setToken("t");
        cfg.getOauth().setScopes("snsapi_base");
        cfg.getOauth().setEnforceHttps(false);
        props.getOfficialAccounts().put("default", cfg);

        props.getOfficialAccounts().put("sns", props.getOfficialAccounts().get("default"));

        transport = mock(WechatTransport.class);
        oauth = new WeChatOAuth(props, transport);
        store = new InMemoryStore();
    }

    /** 构造 Web Request：内部 map 承载 query，mock HttpServletRequest 驱动 fullUrl() */
    private Request webRequest(String query, boolean secure) {
        HttpServletRequest http = mock(HttpServletRequest.class);
        when(http.getRequestURI()).thenReturn("/weapp");
        when(http.getQueryString()).thenReturn(query != null && !query.isEmpty() ? query : null);
        when(http.isSecure()).thenReturn(secure);
        when(http.getServerName()).thenReturn("example.com");
        when(http.getServerPort()).thenReturn(secure ? 443 : 80);
        when(http.getHeaderNames()).thenReturn(
                java.util.Collections.<String>enumeration(new java.util.ArrayList<>()));
        Request req = new Request();
        req.setRequest(http);
        req.replaceHeader("Host", "example.com");
        for (String p : (query == null ? "" : query).split("&")) {
            if (p.isEmpty()) {
                continue;
            }
            int eq2 = p.indexOf('=');
            if (eq2 > 0) {
                req.replaceQuery(p.substring(0, eq2), p.substring(eq2 + 1));
            }
        }
        return req;
    }

    private boolean nextCalled = false;

    private com.weacsoft.jaravel.vendor.http.middleware.Middleware.NextFunction next() {
        return request -> {
            nextCalled = true;
            return ResponseBuilder.html("business ok");
        };
    }

    private static String header(Response resp, String name) {
        java.util.List<String> v = resp.getHeaders().get(name);
        return (v != null && !v.isEmpty()) ? v.get(0) : null;
    }

    // ===== 1) 已授权 → 放行 =====

    @Test
    void testAlreadyAuthorizedPassesThrough() {
        WeChatOAuthUser user = WeChatOAuthUser.from(Map.of("openid", "o1"), null);
        store.put(WeChatOAuth.sessionKey("default"), user);

        Response resp = new WeChatOAuthMiddleware(oauth, "default", null, store)
                .handle(webRequest("from=mp", false), next(), "default");
        assertTrue(nextCalled, "已授权必须放行 next");
        assertEquals(200, resp.getStatus());
        assertTrue(resp.getContent().contains("business ok"), "应答应为业务层响应");
    }

    // ===== 2) 回调换 openid → 存会话 → 回原地址 =====

    @Test
    void testCallbackExchangesCodeStoresSessionAndRedirectsBack() {
        String state = WeChatOAuth.randomState();
        store.put(WeChatOAuth.stateKey("default"), state);

        Map<String, Object> token = new LinkedHashMap<>();
        token.put("openid", "openid_from_code");
        token.put("access_token", "sns_t");
        token.put("scope", "snsapi_base");
        when(transport.get(eq("sns/oauth2/access_token"), anyMap(), eq("WeChatOAuth.userFromCode")))
                .thenReturn(WeChatResponse.of(token));

        Request req = webRequest("from=mp&code=CODE1&state=" + state, false);
        Response resp = new WeChatOAuthMiddleware(oauth, "default", null, store)
                .handle(req, next(), "default");

        assertFalse(nextCalled, "回调请求不得放行业务层");
        assertEquals(302, resp.getStatus(), "应 302 回跳");
        assertEquals("http://example.com/weapp?from=mp", header(resp, "Location"),
                "回跳原地址（剔除 code/state，保留其余 query）");
        WeChatOAuthUser stored = WeChatOAuth.userInSession(store, "default");
        assertNotNull(stored, "openid 用户应写入会话（业务 guard 从此取）");
        assertEquals("openid_from_code", stored.getId());
        assertNull(store.map.get(WeChatOAuth.stateKey("default")), "state 使用后应清除");
    }

    // ===== 3) state 不匹配 → 拒绝 =====

    @Test
    void testCallbackStateMismatchRejected() {
        store.put(WeChatOAuth.stateKey("default"), "expected_state");

        Response resp = new WeChatOAuthMiddleware(oauth, "default", null, store)
                .handle(webRequest("code=STOLEN&state=attacker_state", false), next(), "default");
        assertEquals(403, resp.getStatus(), "state 不匹配必须拒绝（防 CSRF）");
        assertFalse(nextCalled);
        assertNull(store.map.get(WeChatOAuth.sessionKey("default")), "不得写入用户会话");
        assertNull(store.map.get(WeChatOAuth.stateKey("default")), "非法 state 应清除");
    }

    // ===== 4) 未授权 → 302 微信授权页 =====

    @Test
    void testNotAuthorizedRedirectsToWeChat() {
        Request req = webRequest("from=mp", false);
        Response resp = new WeChatOAuthMiddleware(oauth, "default", null, store)
                .handle(req, next(), "default");

        assertEquals(302, resp.getStatus());
        String loc = header(resp, "Location");
        assertTrue(loc.startsWith(WeChatOAuth.AUTHORIZE_BASE_URL + "?appid=" + APPID));
        assertTrue(loc.contains("&redirect_uri="), "必须带编码后的 redirect_uri");
        assertTrue(loc.contains("http%3A%2F%2Fexample.com%2Fweapp"), "redirect_uri 应为当前 URL");
        assertTrue(loc.contains("&scope=snsapi_base"));
        assertTrue(loc.endsWith("#wechat_redirect"));
        assertNotNull(store.map.get(WeChatOAuth.stateKey("default")), "state 应写入会话（回调时核对）");
    }

    @Test
    void testExistingSessionUserPassesThroughEvenIfStale() {
        // 对齐 overtrue：会话已有用户值即视为「已授权」放行（登出由业务侧显式清除会话）
        store.put(WeChatOAuth.sessionKey("default"), WeChatOAuthUser.from(Map.of("openid", "stale"), null));
        Response resp = new WeChatOAuthMiddleware(oauth, "default", null, store)
                .handle(webRequest("from=mp", false), next(), "default");
        assertTrue(nextCalled);
        assertEquals(200, resp.getStatus());
    }

    // ===== 5) enforceHttps 升级 =====

    @Test
    void testEnforceHttpsUpgradesScheme() {
        WechatProperties props = new WechatProperties();
        WechatProperties.OfficialAccountConfig cfg = new WechatProperties.OfficialAccountConfig();
        cfg.setAppId(APPID);
        cfg.setSecret("s");
        cfg.getOauth().setEnforceHttps(true);
        props.getOfficialAccounts().put("default", cfg);
        WeChatOAuth strict = new WeChatOAuth(props, transport);

        Response resp = new WeChatOAuthMiddleware(strict, "default", null, store)
                .handle(webRequest("from=mp", false), next(), "default");
        String loc = header(resp, "Location");
        String redirectUri = loc.substring(loc.indexOf("&redirect_uri=") + "&redirect_uri=".length());
        assertTrue(redirectUri.startsWith("https%3A%2F%2Fexample.com"), "enforceHttps 应把 http 升级为 https");
    }

    // ===== 6) 别名参数（account,scope）解析 =====

    @Test
    void testAliasParamsResolveAccountAndScope() {
        Response resp = new WeChatOAuthMiddleware(oauth, null, null, store) // 无构造器默认值，账号/scope 取自路由参数
                .handle(webRequest("", false), next(), "sns", "snsapi_userinfo");
        String loc = header(resp, "Location");
        assertTrue(loc.contains("&scope=snsapi_userinfo"), "路由参数 scope 应覆盖配置默认");
        assertNotNull(store.map.get(WeChatOAuth.stateKey("sns")), "state 应按账号 sns 记录");
    }

    @Test
    void testNullStoreToleratedInHeadlessUsage() {
        // 无会话场景（纯 API 调用方）：null store 应全程无异常
        Response resp = new WeChatOAuthMiddleware(oauth)
                .handle(webRequest("", false), next(), "default");
        assertEquals(302, resp.getStatus(), "无会话时也应跳转授权页");
    }

    // ===== 7) intendedUrl 边界 =====

    @Test
    void testIntendedUrlKeepsOtherQuery() {
        Request req = webRequest("a=1&code=C&keep=yes&state=S", false);
        assertEquals("http://example.com/weapp?a=1&keep=yes",
                WeChatOAuthMiddleware.intendedUrl(req, false),
                "只剔除 code/state，保留其余 query 且保持出现顺序");
        assertEquals("https://example.com/weapp",
                WeChatOAuthMiddleware.intendedUrl(webRequest("", false), true),
                "enforceHttps 应升级协议");
        assertEquals("https://example.com/weapp",
                WeChatOAuthMiddleware.intendedUrl(webRequest("", true), true),
                "本身 https 时不变");
    }
}
