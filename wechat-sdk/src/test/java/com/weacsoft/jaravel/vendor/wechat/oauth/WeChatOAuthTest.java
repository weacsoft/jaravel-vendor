package com.weacsoft.jaravel.vendor.wechat.oauth;

import com.weacsoft.jaravel.vendor.http.session.SessionStore;
import com.weacsoft.jaravel.vendor.wechat.WechatProperties;
import com.weacsoft.jaravel.vendor.wechat.response.WeChatResponse;
import com.weacsoft.jaravel.vendor.wechat.response.WechatApiException;
import com.weacsoft.jaravel.vendor.wechat.transport.WechatTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 网页授权服务（WeChatOAuth + WeChatOAuthUser + 会话契约）测试：全程 mock 传输层，无网络。
 */
class WeChatOAuthTest {

    private static final String APPID = "wxaabbccddeeff0011";
    private static final String SECRET = "topsecret";

    private WechatProperties props;
    private WechatTransport transport;
    private WeChatOAuth oauth;

    @BeforeEach
    void setUp() {
        props = new WechatProperties();
        WechatProperties.OfficialAccountConfig cfg = new WechatProperties.OfficialAccountConfig();
        cfg.setAppId(APPID);
        cfg.setSecret(SECRET);
        cfg.setToken("t");
        cfg.getOauth().setScopes("snsapi_base");
        props.getOfficialAccounts().put("default", cfg);

        transport = mock(WechatTransport.class);
        oauth = new WeChatOAuth(props, transport);
    }

    // ===== authorizeUrl 组装 =====

    @Test
    void testAuthorizeUrlShape() {
        String url = oauth.authorizeUrl("default", "https://example.com/weapp?from=mp",
                List.of("snsapi_base"), "STATE123");
        assertTrue(url.startsWith("https://open.weixin.qq.com/connect/oauth2/authorize?appid=" + APPID));
        assertTrue(url.contains("&redirect_uri="), "redirect_uri 必须 URL 编码");
        assertTrue(url.contains("https%3A%2F%2Fexample.com%2Fweapp"), "redirect_uri 应被编码");
        assertTrue(url.contains("&response_type=code"));
        assertTrue(url.contains("&scope=snsapi_base"));
        assertTrue(url.contains("&state=STATE123"));
        assertTrue(url.endsWith("#wechat_redirect"));
    }

    @Test
    void testAuthorizeUrlDefaultScopeFromConfig() {
        String url = oauth.authorizeUrl("default", "https://example.com/weapp");
        assertTrue(url.contains("&scope=snsapi_base"), "未显式给 scope 时应取配置默认");
        assertTrue(url.contains("&state="), "state 应自动生成");
    }

    @Test
    void testAuthorizeUrlRejectsEmptyRedirect() {
        assertThrows(IllegalArgumentException.class,
                () -> oauth.authorizeUrl("default", ""));
    }

    @Test
    void testAuthorizeUrlUnknownAccount() {
        assertThrows(IllegalStateException.class,
                () -> oauth.authorizeUrl("missing", "https://x.com"),
                "未配置的公众号别名必须快速失败，且不静默回退 default");
    }

    // ===== resolveScopes =====

    @Test
    void testResolveScopesPrecedence() {
        assertEquals(List.of("snsapi_base"), oauth.resolveScopes("default", null), "配置默认");
        assertEquals(List.of("snsapi_userinfo"), oauth.resolveScopes("default", "snsapi_userinfo"), "参数字符串");
        assertEquals(List.of("a", "b"), WeChatOAuth.splitScopes("a, b ,"), "逗号拆分去空白");
        assertEquals(List.of("snsapi_base"), WeChatOAuth.splitScopes("   "), "空白回退默认");
    }

    // ===== userFromCode =====

    @Test
    void testUserFromCodeTokenOnly() {
        Map<String, Object> token = new LinkedHashMap<>();
        token.put("openid", "openid_abc");
        token.put("access_token", "sns_token");
        token.put("expires_in", 7200);
        token.put("refresh_token", "r");
        token.put("unionid", "union_1");
        token.put("scope", "snsapi_base");
        when(transport.get(eq("sns/oauth2/access_token"), anyMap(), eq("WeChatOAuth.userFromCode")))
                .thenReturn(WeChatResponse.of(token));

        WeChatOAuthUser user = oauth.userFromCode("default", "CODE1");
        assertEquals("openid_abc", user.getId(), "getId 应为 openid（EasyWeChat 语义）");
        assertEquals("openid_abc", user.getOpenId());
        assertEquals("union_1", user.getUnionId());
        assertEquals("sns_token", user.getAccessToken());
        assertNull(user.getNickname(), "snsapi_base 范围无昵称");
        verify(transport, never()).get(eq("sns/userinfo"), anyMap(), anyString());
    }

    @Test
    void testUserFromCodeWithUserInfoScope() {
        Map<String, Object> token = new LinkedHashMap<>();
        token.put("openid", "openid_xyz");
        token.put("access_token", "sns_t2");
        token.put("scope", "snsapi_userinfo");
        when(transport.get(eq("sns/oauth2/access_token"), anyMap(), eq("WeChatOAuth.userFromCode")))
                .thenReturn(WeChatResponse.of(token));
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("openid", "openid_xyz");
        profile.put("nickname", "昵称");
        profile.put("headimgurl", "https://wx.qlogo.cn/x");
        profile.put("unionid", "union_2");
        when(transport.get(eq("sns/userinfo"), anyMap(), eq("WeChatOAuth.userinfo")))
                .thenReturn(WeChatResponse.of(profile));

        WeChatOAuthUser user = oauth.userFromCode("default", "CODE2", List.of("snsapi_userinfo"));
        assertEquals("openid_xyz", user.getId());
        assertEquals("昵称", user.getNickname());
        assertEquals("https://wx.qlogo.cn/x", user.getHeadimgUrl());
    }

    @Test
    void testUserFromCodeProfileFailureDegradesToOpenid() {
        Map<String, Object> token = new LinkedHashMap<>();
        token.put("openid", "openid_y");
        token.put("access_token", "sns_t3");
        token.put("scope", "snsapi_userinfo");
        when(transport.get(eq("sns/oauth2/access_token"), anyMap(), eq("WeChatOAuth.userFromCode")))
                .thenReturn(WeChatResponse.of(token));
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("errcode", 40001);
        err.put("errmsg", "invalid credential");
        when(transport.get(eq("sns/userinfo"), anyMap(), eq("WeChatOAuth.userinfo")))
                .thenReturn(WeChatResponse.of(err));

        WeChatOAuthUser user = assertDoesNotThrow(
                () -> oauth.userFromCode("default", "CODE3", List.of("snsapi_userinfo")),
                "userinfo 失败应降级为仅 openid，不致命");
        assertEquals("openid_y", user.getId());
        assertNull(user.getNickname());
    }

    @Test
    void testUserFromCodeTokenErrorThrows() {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("errcode", 40029);
        err.put("errmsg", "invalid code");
        when(transport.get(eq("sns/oauth2/access_token"), anyMap(), eq("WeChatOAuth.userFromCode")))
                .thenReturn(WeChatResponse.of(err));
        assertThrows(WechatApiException.class, () -> oauth.userFromCode("default", "BAD_CODE"));
    }

    @Test
    void testUserFromCodeRequiresCodeAndSecret() {
        assertThrows(IllegalArgumentException.class, () -> oauth.userFromCode("default", ""));
        WechatProperties p2 = new WechatProperties();
        WechatProperties.OfficialAccountConfig cfg = new WechatProperties.OfficialAccountConfig();
        cfg.setAppId(APPID);
        props.getOfficialAccounts().clear();
        props.getOfficialAccounts().put("nosecret", cfg);
        assertThrows(IllegalStateException.class, () -> new WeChatOAuth(props, transport)
                .userFromCode("nosecret", "C"), "缺 secret 应快速失败");
    }

    // ===== 会话契约 =====

    static class InMemoryStore implements SessionStore {
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

    @Test
    void testSessionContract() {
        assertEquals("easywechat.oauth_user.default", WeChatOAuth.sessionKey(null));
        assertEquals("easywechat.oauth_user.default", WeChatOAuth.sessionKey(""));
        assertEquals("easywechat.oauth_user.sns", WeChatOAuth.sessionKey("sns"));
        assertEquals("easywechat.oauth_state.default", WeChatOAuth.stateKey("default"));

        InMemoryStore store = new InMemoryStore();
        WeChatOAuthUser user = WeChatOAuthUser.from(Map.of("openid", "o1"), null);
        assertNull(WeChatOAuth.userInSession(store, "default"), "空会话应返回 null");
        assertNull(WeChatOAuth.userInSession(null, "default"), "store 为 null 应安全返回 null");

        WeChatOAuth.saveToSession(store, "default", user);
        assertSame(user, WeChatOAuth.userInSession(store, "default"));
        assertSame(user, WeChatOAuth.userInSession(store, null), "null account 应归一为 default");

        WeChatOAuth.clearFromSession(store, "default");
        assertNull(WeChatOAuth.userInSession(store, "default"));
    }

    @Test
    void testUserFromStoredFromMap() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("openid", "o2");
        raw.put("unionid", "u2");
        WeChatOAuthUser fromMap = WeChatOAuthUser.fromStored(raw);
        assertEquals("o2", fromMap.getId());
        assertEquals("u2", fromMap.getUnionId());
        assertNull(WeChatOAuthUser.fromStored(null));
        assertNull(WeChatOAuthUser.fromStored("garbage"), "非 Map/非用户值应返回 null");
    }

    @Test
    void testRandomStateUniquenessAndLength() {
        String a = WeChatOAuth.randomState();
        String b = WeChatOAuth.randomState();
        assertEquals(32, a.length(), "32 位十六进制");
        assertNotEquals(a, b);
        assertAll("hex 校验", () -> a.matches("[0-9a-f]{32}"));
    }

    @Test
    void testUserFromMissingOpenidFails() {
        Map<String, Object> bad = new HashMap<>();
        bad.put("access_token", "t");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> WeChatOAuthUser.from(bad, null));
        assertTrue(ex.getMessage().contains("openid"));
    }
}
