package com.weacsoft.jaravel.vendor.wechat.response;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WeChatResponse 语义测试：errcode 三态（-1/0/!0）、as() 映射、requireSuccess。
 */
class WeChatResponseTest {

    @Test
    void testErrcodeAbsentIsSuccess() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("total_count", 2);
        WeChatResponse resp = WeChatResponse.of(raw);
        assertTrue(resp.isSuccess(), "无 errcode 字段应视为成功");
        assertEquals(WeChatResponse.NO_ERRCODE, resp.getErrcode());
        assertEquals(2, resp.getInt("total_count"));
    }

    @Test
    void testErrcodeZeroIsSuccess() {
        WeChatResponse resp = WeChatResponse.of(Map.of("errcode", 0, "errmsg", "ok"));
        assertTrue(resp.isSuccess());
        assertEquals(0, resp.getErrcode());
    }

    @Test
    void testErrcodeNonZeroIsFailure() {
        WeChatResponse resp = WeChatResponse.of(Map.of("errcode", 40001, "errmsg", "invalid credential"));
        assertFalse(resp.isSuccess());
        assertEquals(40001, resp.getErrcode());
        assertEquals("invalid credential", resp.getErrmsg());
    }

    @Test
    void testMsgIdExtraction() {
        WeChatResponse resp = WeChatResponse.of(Map.of("errcode", 0, "errmsg", "ok", "msgid", "10077820909753985654223"));
        assertEquals("10077820909753985654223", resp.getMsgId());
        assertTrue(resp.isSuccess());
    }

    @Test
    void testGetString() {
        WeChatResponse resp = WeChatResponse.of(Map.of("url", "https://example.com"));
        assertEquals("https://example.com", resp.getString("url"));
        assertNull(resp.getString("missing"));
    }

    @Test
    void testAsConversion() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("errcode", 0);
        raw.put("errmsg", "ok");
        raw.put("name", "VIP");
        raw.put("id", 7);
        WeChatResponse resp = WeChatResponse.of(raw);
        Holder holder = resp.as(Holder.class);
        assertEquals("VIP", holder.getName(), "as() 应把业务字段映射到目标类型");
        assertEquals(7, holder.getId());
    }

    /** as() 映射目标（需 getter/setter 可被 Jackson 映射） */
    public static class Holder {
        private String name;
        private int id;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }
    }

    @Test
    void testRequireSuccessThrowsOnFailure() {
        WeChatResponse resp = WeChatResponse.of(Map.of("errcode", 48001, "errmsg", "api unauthorized"));
        WechatApiException ex = assertThrows(WechatApiException.class,
                () -> resp.requireSuccess("sendTemplate"), "失败时应抛 WechatApiException");
        assertEquals(48001, ex.getErrcode());
        assertTrue(ex.getMessage().contains("sendTemplate"), "异常信息应含操作名");
        assertTrue(ex.getMessage().contains("48001"));
    }

    @Test
    void testRequireSuccessPassesThrough() {
        WeChatResponse resp = WeChatResponse.of(Map.of("errcode", 0, "errmsg", "ok"));
        assertSame(resp, resp.requireSuccess("op"), "成功时应返回自身");
    }

    @Test
    void testNullRawTreatedAsEmpty() {
        WeChatResponse resp = WeChatResponse.of(null);
        assertTrue(resp.isSuccess());
        assertTrue(resp.raw().isEmpty());
    }
}
