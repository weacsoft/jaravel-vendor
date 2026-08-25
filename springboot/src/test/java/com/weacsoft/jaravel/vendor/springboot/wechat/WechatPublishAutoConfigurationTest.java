package com.weacsoft.jaravel.vendor.springboot.wechat;

import com.weacsoft.jaravel.vendor.core.publish.Publishable;
import com.weacsoft.jaravel.vendor.core.publish.PublishableRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * wechat-sdk 发布装配测试（由 wechat-sdk 模块迁入：注册触发点
 * {@code WechatPublishAutoConfiguration} 静态块位于本模块）。
 * <p>
 * 验证 {@code artisan vendor:publish --tag=wechat-sdk} 的声明在装配加载后可被扫描到。
 */
class WechatPublishAutoConfigurationTest {

    @AfterEach
    void cleanup() {
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
}
