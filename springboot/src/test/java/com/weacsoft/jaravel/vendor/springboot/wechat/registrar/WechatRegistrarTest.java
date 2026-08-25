package com.weacsoft.jaravel.vendor.springboot.wechat.registrar;

import com.weacsoft.jaravel.vendor.core.registrar.RegistrarException;
import com.weacsoft.jaravel.vendor.wechat.RegisterWechatMiniApp;
import com.weacsoft.jaravel.vendor.wechat.RegisterWechatOfficialAccount;
import com.weacsoft.jaravel.vendor.wechat.WechatProperties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 声明式注册器测试：@RegisterWechatOfficialAccount / @RegisterWechatMiniApp
 * 的产物回填、别名展开、校验失败。
 * <p>
 * 测试类与注册器同包，可直接调用 protected register() 而不启动 Spring 容器。
 */
class WechatRegistrarTest {

    private static class Dummy {
        public WechatProperties.OfficialAccountConfig sampleOfficialAccount() {
            return null;
        }

        public WechatProperties.MiniAppConfig sampleMiniApp() {
            return null;
        }
    }

    private static Method method(String name) {
        try {
            return Dummy.class.getMethod(name);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static RegisterWechatOfficialAccount oaAnn(String value, String... alias) {
        RegisterWechatOfficialAccount ann = mock(RegisterWechatOfficialAccount.class);
        when(ann.value()).thenReturn(value);
        when(ann.alias()).thenReturn(alias);
        return ann;
    }

    private static RegisterWechatMiniApp miniAnn(String value, String... alias) {
        RegisterWechatMiniApp ann = mock(RegisterWechatMiniApp.class);
        when(ann.value()).thenReturn(value);
        when(ann.alias()).thenReturn(alias);
        return ann;
    }

    private static WechatProperties.OfficialAccountConfig oaConfig(String appId, String secret) {
        WechatProperties.OfficialAccountConfig cfg = new WechatProperties.OfficialAccountConfig();
        cfg.setAppId(appId);
        cfg.setSecret(secret);
        return cfg;
    }

    @Test
    void testOfficialAccountRegistrationIntoSharedProperties() {
        WechatProperties props = new WechatProperties();
        WechatOfficialAccountRegistrar registrar =
                new WechatOfficialAccountRegistrar(props);

        registrar.register(oaConfig("wx_declared", "declared_secret"),
                method("sampleOfficialAccount"), oaAnn("default"));

        WechatProperties.OfficialAccountConfig resolved = props.getOfficialAccount("default");
        assertNotNull(resolved, "声明产物应回填到共享 WechatProperties");
        assertEquals("wx_declared", resolved.getAppId());
        assertEquals("declared_secret", resolved.getSecret());
    }

    @Test
    void testAliasExpansion() {
        WechatProperties props = new WechatProperties();
        WechatOfficialAccountRegistrar registrar =
                new WechatOfficialAccountRegistrar(props);

        WechatProperties.OfficialAccountConfig cfg = oaConfig("wx_multi", "multi_secret");
        cfg.getOauth().setScopes("snsapi_userinfo");

        registrar.register(cfg, method("sampleOfficialAccount"),
                oaAnn("default", "snsapi_userinfo"));

        assertEquals("wx_multi", props.getOfficialAccount("default").getAppId());
        assertEquals("wx_multi", props.getOfficialAccount("snsapi_userinfo").getAppId(),
                "alias 别名应可命中同一份配置");
    }

    @Test
    void testEmptyNameFallsBackToDefault() {
        WechatProperties props = new WechatProperties();
        WechatOfficialAccountRegistrar registrar =
                new WechatOfficialAccountRegistrar(props);

        registrar.register(oaConfig("wx_empty", "s"), method("sampleOfficialAccount"), oaAnn(""));

        assertNotNull(props.getOfficialAccount("default"), "value 留空应回退到 default 名");
    }

    @Test
    void testMissingSecretRejected() {
        WechatProperties props = new WechatProperties();
        WechatOfficialAccountRegistrar registrar =
                new WechatOfficialAccountRegistrar(props);

        WechatProperties.OfficialAccountConfig cfg = oaConfig("wx_only_appid", null);
        assertThrows(RegistrarException.class,
                () -> registrar.register(cfg, method("sampleOfficialAccount"), oaAnn("default")),
                "声明缺少 secret 必须被注册器拒绝");
    }

    @Test
    void testWrongReturnTypeRejected() {
        WechatProperties props = new WechatProperties();
        WechatOfficialAccountRegistrar registrar =
                new WechatOfficialAccountRegistrar(props);
        assertThrows(RegistrarException.class,
                () -> registrar.register("not-a-config-object", method("sampleOfficialAccount"), oaAnn("default")),
                "返回类型不匹配必须被注册器拒绝");
    }

    @Test
    void testMiniAppRegistration() {
        WechatProperties props = new WechatProperties();
        WechatMiniAppRegistrar registrar =
                new WechatMiniAppRegistrar(props);

        WechatProperties.MiniAppConfig cfg = new WechatProperties.MiniAppConfig();
        cfg.setAppId("wxa_declarative");
        cfg.setSecret("mini_secret");
        cfg.setType(3);

        registrar.register(cfg, method("sampleMiniApp"), miniAnn("default"));

        WechatProperties.MiniAppConfig resolved = props.getMiniApp("default");
        assertNotNull(resolved, "声明产物应回填到共享 WechatProperties");
        assertEquals("wxa_declarative", resolved.getAppId());
        assertEquals(3, resolved.getType());
    }

    @Test
    void testMiniAppAliasAndMissingCredentials() {
        WechatProperties props = new WechatProperties();
        WechatMiniAppRegistrar registrar =
                new WechatMiniAppRegistrar(props);

        WechatProperties.MiniAppConfig ok = new WechatProperties.MiniAppConfig();
        ok.setAppId("wxa_alias");
        ok.setSecret("s");
        registrar.register(ok, method("sampleMiniApp"), miniAnn("service", "service_alias"));
        assertEquals("wxa_alias", props.getMiniApp("service").getAppId());
        assertEquals("wxa_alias", props.getMiniApp("service_alias").getAppId());

        WechatProperties.MiniAppConfig broken = new WechatProperties.MiniAppConfig();
        broken.setAppId("wxa_broken");
        assertThrows(RegistrarException.class,
                () -> registrar.register(broken, method("sampleMiniApp"), miniAnn("x")),
                "声明缺少 secret 必须被注册器拒绝");
    }
}
