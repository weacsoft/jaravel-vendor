package com.weacsoft.jaravel.vendor.wechat;

import com.weacsoft.jaravel.vendor.core.publish.Publishable;
import com.weacsoft.jaravel.vendor.core.publish.PublishableRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * vendor:publish --tag=wechat-sdk 回归测试：
 * 确保 WechatSdkConfig 模板始终可发布，且内容覆盖「声明式注册 + OAuth 网页授权 + 小程序」三大块。
 */
class WechatPublishableConfigTest {

    @AfterEach
    void cleanup() {
        // 恢复注册表全局状态，避免污染其它测试/宿主扫描
        PublishableRegistry.clearForTest();
    }

    @Test
    void testPublishableRegistryContainsWechatSdk() {
        // 实例化（触发类静态初始化 → PublishableRegistry.register）
        assertNotNull(new WechatPublishAutoConfiguration());
        List<Publishable> all = PublishableRegistry.list();
        boolean found = all.stream().anyMatch(p -> "wechat-sdk".equals(p.tag()));
        assertTrue(found, "PublishableRegistry 必须包含 tag=wechat-sdk 的可发布配置");
    }

    @Test
    void testTemplateCoversDeclarationOauthAndMiniApp() {
        String source = new WechatPublishableConfig().source("com.example.demo");

        assertTrue(source.startsWith("package com.example.demo.config;"),
                "模板必须落在业务工程 {basePackage}.config 包下");
        assertTrue(source.contains("public class WechatSdkConfig"),
                "模板类名应为 WechatSdkConfig");
        assertTrue(source.contains("@RegisterWechatOfficialAccount(value = \"default\""),
                "应发布公众号声明式注册");
        assertTrue(source.contains("@RegisterWechatMiniApp(\"default\")"),
                "应发布小程序声明式注册");
        assertTrue(source.contains("config.getOauth().setScopes(\"snsapi_base\")"),
                "应发布 OAuth 授权范围（snsapi_base 默认）");
        assertTrue(source.contains("config.getOauth().setCallback("), "应发布 OAuth 回调路径");
        assertTrue(source.contains("config.getOauth().setEnforceHttps("), "应发布 OAuth https 升级开关");
        assertTrue(source.contains("config.setMessageMode(\"plain\")"), "应发布消息模式（plain/safe）");
    }

    @Test
    void testMetaFields() {
        WechatPublishableConfig cfg = new WechatPublishableConfig();
        assertEquals("wechat-sdk", cfg.tag());
        assertEquals("WechatSdkConfig", cfg.className());
        assertFalse(cfg.description() == null || cfg.description().isBlank());
        assertEquals(com.weacsoft.jaravel.vendor.core.publish.PublishType.CONFIG, cfg.type(),
                "类型应为 CONFIG（Java 配置类源码）");
    }
}
