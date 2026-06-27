package com.weacsoft.jaravel.vendor.wechat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WechatProperties 配置解析测试。
 * 覆盖默认值、公众号/小程序按名查找、默认回退逻辑。
 */
class WechatPropertiesTest {

    @Test
    void testDefaultValues() {
        WechatProperties props = new WechatProperties();
        assertTrue(props.isEnabled(), "默认应启用微信 SDK");
        assertEquals("redis", props.getCacheStore(), "默认缓存存储应为 redis");
        assertNotNull(props.getOfficialAccounts(), "公众号配置 Map 不应为 null");
        assertTrue(props.getOfficialAccounts().isEmpty(), "默认无公众号配置");
        assertNotNull(props.getMiniApps(), "小程序配置 Map 不应为 null");
        assertTrue(props.getMiniApps().isEmpty(), "默认无小程序配置");
        assertNotNull(props.getHttp(), "HTTP 配置不应为 null");
    }

    @Test
    void testOfficialAccountByName() {
        WechatProperties props = new WechatProperties();
        WechatProperties.OfficialAccountConfig oaConfig = new WechatProperties.OfficialAccountConfig();
        oaConfig.setAppId("wx_official_1");
        oaConfig.setSecret("secret1");
        props.getOfficialAccounts().put("default", oaConfig);

        WechatProperties.OfficialAccountConfig result = props.getOfficialAccount("default");
        assertNotNull(result);
        assertEquals("wx_official_1", result.getAppId());
        assertEquals("secret1", result.getSecret());
    }

    @Test
    void testOfficialAccountFallbackToDefault() {
        WechatProperties props = new WechatProperties();
        WechatProperties.OfficialAccountConfig oaConfig = new WechatProperties.OfficialAccountConfig();
        oaConfig.setAppId("wx_default");
        props.getOfficialAccounts().put("default", oaConfig);

        // 查找不存在的名称时应回退到 default
        WechatProperties.OfficialAccountConfig result = props.getOfficialAccount("nonexistent");
        assertNotNull(result, "查找不存在的名称应回退到 default 配置");
        assertEquals("wx_default", result.getAppId());
    }

    @Test
    void testOfficialAccountReturnsNullWhenNotFound() {
        WechatProperties props = new WechatProperties();
        // 不配置任何公众号
        assertNull(props.getOfficialAccount("any"), "无 default 配置时查找应返回 null");
    }

    @Test
    void testMiniAppByName() {
        WechatProperties props = new WechatProperties();
        WechatProperties.MiniAppConfig miniConfig = new WechatProperties.MiniAppConfig();
        miniConfig.setAppId("wx_mini_1");
        miniConfig.setSecret("miniSecret1");
        miniConfig.setType(2);
        props.getMiniApps().put("default", miniConfig);

        WechatProperties.MiniAppConfig result = props.getMiniApp("default");
        assertNotNull(result);
        assertEquals("wx_mini_1", result.getAppId());
        assertEquals("miniSecret1", result.getSecret());
        assertEquals(2, result.getType());
    }

    @Test
    void testMiniAppFallbackToDefault() {
        WechatProperties props = new WechatProperties();
        WechatProperties.MiniAppConfig miniConfig = new WechatProperties.MiniAppConfig();
        miniConfig.setAppId("wx_mini_default");
        props.getMiniApps().put("default", miniConfig);

        WechatProperties.MiniAppConfig result = props.getMiniApp("nonexistent");
        assertNotNull(result, "查找不存在的名称应回退到 default 配置");
        assertEquals("wx_mini_default", result.getAppId());
    }

    @Test
    void testHttpConfigDefaults() {
        WechatProperties.HttpConfig httpConfig = new WechatProperties.HttpConfig();
        assertEquals(5.0, httpConfig.getTimeout(), 0.001, "默认超时应为 5.0 秒");
        assertTrue(httpConfig.isRetry(), "默认应启用重试");
    }

    @Test
    void testMultipleOfficialAccounts() {
        WechatProperties props = new WechatProperties();
        WechatProperties.OfficialAccountConfig defaultCfg = new WechatProperties.OfficialAccountConfig();
        defaultCfg.setAppId("wx_default");
        WechatProperties.OfficialAccountConfig app1Cfg = new WechatProperties.OfficialAccountConfig();
        app1Cfg.setAppId("wx_app1");

        props.getOfficialAccounts().put("default", defaultCfg);
        props.getOfficialAccounts().put("app1", app1Cfg);

        assertEquals(2, props.getOfficialAccounts().size());
        assertEquals("wx_app1", props.getOfficialAccount("app1").getAppId());
        assertEquals("wx_default", props.getOfficialAccount("unknown").getAppId());
    }

    @Test
    void testOauthConfigDefaults() {
        WechatProperties.OauthConfig oauth = new WechatProperties.OauthConfig();
        assertEquals("snsapi_base", oauth.getScopes(), "默认授权作用域应为 snsapi_base");
        assertTrue(oauth.isEnforceHttps(), "默认应强制 HTTPS");
    }
}
