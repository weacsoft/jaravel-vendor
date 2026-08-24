package com.weacsoft.jaravel.vendor.wechat.jsdk;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JSSDK 配置对象测试。
 */
class JssdkConfigTest {

    @Test
    void testToJsonBodyShape() {
        JssdkConfig config = new JssdkConfig(
                "wx1234567890abcdef",
                "1700000000",
                "NONCE",
                "sig4040404040404040404040404040404040404040",
                List.of("chooseWXPay", "scanQRCode"),
                List.of("wx-open-launch-weapp"),
                false
        );
        Map<String, Object> body = config.toJsonBody();
        assertEquals("wx1234567890abcdef", body.get("appId"));
        assertEquals("1700000000", body.get("timestamp"));
        assertEquals("NONCE", body.get("nonceStr"));
        assertEquals(2, ((List<?>) body.get("jsApiList")).size());
        assertEquals(List.of("wx-open-launch-weapp"), body.get("openTagList"));
        assertEquals(Boolean.FALSE, body.get("debug"));
    }

    @Test
    void testOpenTagListOmittedWhenEmpty() {
        JssdkConfig config = new JssdkConfig("wx1", "t", "n", "sig", List.of("a"), List.of(), false);
        Map<String, Object> body = config.toJsonBody();
        assertFalse(body.containsKey("openTagList"), "空 openTagList 应省略");
        assertEquals(List.of(), config.getOpenTagList());
    }

    @Test
    void testValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> new JssdkConfig("", "t", "n", "sig", List.of("a"), null, false), "appId 必填");
        assertThrows(IllegalArgumentException.class,
                () -> new JssdkConfig("wx", "t", "n", "", List.of("a"), null, false), "signature 必填");
        assertThrows(IllegalArgumentException.class,
                () -> new JssdkConfig("wx", "t", "n", "sig", List.of(), null, false), "jsApiList 必填非空");
    }

    @Test
    void testToJavascriptSnippet() {
        JssdkConfig config = new JssdkConfig("wx1", "t", "n", "s", List.of("x"), null, true);
        String js = config.toJavascript();
        assertTrue(js.contains("wx.config("), "应生成 wx.config 调用");
        assertTrue(js.contains("wx.error("), "应附带失败回调");
        assertTrue(js.contains("debug"), "应携带 debug");
    }
}
