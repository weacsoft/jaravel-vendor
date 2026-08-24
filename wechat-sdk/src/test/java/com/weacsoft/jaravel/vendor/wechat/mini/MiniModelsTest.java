package com.weacsoft.jaravel.vendor.wechat.mini;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 小程序会话/订阅消息/手机号 模型测试。
 */
class MiniModelsTest {

    @Test
    void testMiniProgramSessionFrom() {
        MiniProgramSession session = MiniProgramSession.from(Map.of(
                "openid", "openid_mini",
                "session_key", "SK_123",
                "unionid", "union_abc",
                "errcode", 0,
                "errmsg", "ok"
        ));
        assertEquals("openid_mini", session.getOpenId());
        assertEquals("SK_123", session.getSessionKey());
        assertEquals("union_abc", session.getUnionId());
    }

    @Test
    void testMiniProgramSessionRequiresOpenidAndSessionKey() {
        assertThrows(IllegalArgumentException.class,
                () -> MiniProgramSession.from(Map.of("openid", "o1")), "缺 session_key 必须失败");
        assertThrows(IllegalArgumentException.class,
                () -> MiniProgramSession.from(Map.of("session_key", "sk")), "缺 openid 必须失败");
        // 微信错误响应（含 errcode）：openid/session_key 缺失 → 必须抛错，禁止静默拿到 null
        assertThrows(IllegalArgumentException.class,
                () -> MiniProgramSession.from(Map.of("errcode", 40125, "errmsg", "invalid code")));
    }

    @Test
    void testMiniSubscribeMessageBody() {
        MiniSubscribeMessage msg = new MiniSubscribeMessage()
                .touser("OPENID")
                .templateId("SUB_T1")
                .page("pages/result/index")
                .data("thing1", "包裹已发货");
        Map<String, Object> body = msg.toJsonBody();
        assertEquals("OPENID", body.get("touser"));
        assertEquals("SUB_T1", body.get("template_id"));
        assertEquals("pages/result/index", body.get("page"));
        assertEquals("formal", body.get("miniprogram_state"), "缺省 miniprogram_state 为 formal");
        assertEquals("zh_CN", body.get("lang"), "缺省 lang 为 zh_CN");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> thing = (Map<String, Object>) data.get("thing1");
        assertEquals("包裹已发货", thing.get("value"));
    }

    @Test
    void testMiniSubscribeMessageCustomStateAndLang() {
        MiniSubscribeMessage msg = new MiniSubscribeMessage()
                .touser("O").templateId("T").data("k", MiniSubscribeMessage.DataItem.of("v"))
                .state(MiniSubscribeMessage.STATE_TRIAL).lang("en_US");
        Map<String, Object> body = msg.toJsonBody();
        assertEquals("trial", body.get("miniprogram_state"));
        assertEquals("en_US", body.get("lang"));
    }

    @Test
    void testMiniSubscribeMessageRequiredFields() {
        assertThrows(IllegalArgumentException.class,
                () -> new MiniSubscribeMessage().templateId("T").data("k", "v").toJsonBody());
        assertThrows(IllegalArgumentException.class,
                () -> new MiniSubscribeMessage().touser("O").data("k", "v").toJsonBody());
        assertThrows(IllegalArgumentException.class,
                () -> new MiniSubscribeMessage().touser("O").templateId("T").toJsonBody());
    }

    @Test
    void testPhoneNumberResultFrom() {
        Map<String, Object> raw = Map.of(
                "phone_info", Map.of(
                        "phoneNumber", "8613800000000",
                        "purePhoneNumber", "13800000000",
                        "countryCode", "86"
                ),
                "openid", "openid_1",
                "watermark", Map.of("appid", "wx1234567890123456", "timestamp", 1700000000)
        );
        PhoneNumberResult result = PhoneNumberResult.from(raw);
        assertEquals("8613800000000", result.getPhone());
        assertEquals("13800000000", result.getPurePhone());
        assertEquals("86", result.getCountryCode());
        assertEquals("openid_1", result.getOpenid());
        assertEquals("wx1234567890123456", result.getWatermarkAppId());
        assertEquals(1700000000L, result.getWatermarkTimestamp());
    }

    @Test
    void testPhoneNumberResultRequiresPhoneInfo() {
        Map<String, Object> raw = Map.of("openid", "o", "errcode", 40029);
        assertThrows(IllegalArgumentException.class, () -> PhoneNumberResult.from(raw),
                "缺少 phone_info（如 errcode=40029 未授权）必须显式失败");
    }

    @Test
    void testPhoneNumberResultWithoutWatermark() {
        Map<String, Object> raw = Map.of(
                "phone_info", Map.of("phoneNumber", "8613900000000", "purePhoneNumber", "13900000000", "countryCode", "86"),
                "openid", "o9"
        );
        PhoneNumberResult result = PhoneNumberResult.from(raw);
        assertEquals("8613900000000", result.getPhone());
        assertNull(result.getWatermarkAppId(), "无 watermark 节点时应返回 null 而非异常");
    }
}
