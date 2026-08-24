package com.weacsoft.jaravel.vendor.wechat.template;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 服务号模板消息 / 订阅通知 序列化测试。
 */
class TemplateTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> dataNode(Map<String, Object> body) {
        return (Map<String, Object>) body.get("data");
    }

    @Test
    void testTemplateMessageFullBody() {
        TemplateMessage msg = new TemplateMessage()
                .toUser("OPENID_1")
                .clientMsgId("CMSG-9")
                .templateId("TPL-1001")
                .url("https://example.com/page")
                .miniProgram(new MiniProgramTarget("wxmini1", "pages/a/index"))
                .data("first", TemplateDataItem.ofValue("欢迎"))
                .data("keyword1", TemplateDataItem.colored("张三", "#173177"))
                .data("remark", TemplateDataItem.ofValue("请查收"));
        Map<String, Object> body = msg.toJsonBody();
        assertEquals("OPENID_1", body.get("touser"));
        assertEquals("TPL-1001", body.get("template_id"));
        assertEquals("CMSG-9", body.get("client_msg_id"));
        assertEquals("https://example.com/page", body.get("url"));
        @SuppressWarnings("unchecked")
        Map<String, Object> mini = (Map<String, Object>) body.get("miniprogram");
        assertEquals("wxmini1", mini.get("appid"));
        assertNotNull(mini.get("pagepath"), "miniprogram 应携带 pagepath");
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) dataNode(body).get("first");
        assertEquals("欢迎", first.get("value"));
        assertFalse(first.containsKey("color"), "无色值时不应输出 color 键");
        @SuppressWarnings("unchecked")
        Map<String, Object> keyword = (Map<String, Object>) dataNode(body).get("keyword1");
        assertEquals("#173177", keyword.get("color"), "色值应以 color 键输出");
    }

    @Test
    void testTemplateMessageRequiresToUserAndTemplateId() {
        assertThrows(IllegalArgumentException.class,
                () -> new TemplateMessage().templateId("T").data("k", TemplateDataItem.ofValue("v")).toJsonBody(),
                "缺 touser 必须拒绝");
        assertThrows(IllegalArgumentException.class,
                () -> new TemplateMessage().toUser("O").data("k", TemplateDataItem.ofValue("v")).toJsonBody(),
                "缺 template_id 必须拒绝");
        assertThrows(IllegalArgumentException.class,
                () -> new TemplateMessage().toUser("O").templateId("T").toJsonBody(),
                "空 data 必须拒绝（模板消息必须携带数据项）");
    }

    @Test
    void testSubscriptionNoticeFullBody() {
        SubscriptionNotice notice = new SubscriptionNotice()
                .toUser("OPENID_2")
                .templateId("TPL-2002")
                .scene("SCENE-8")
                .title("发货通知")
                .url("https://example.com/order/1")
                .content("包裹已发出");
        Map<String, Object> body = notice.toJsonBody();
        assertEquals("OPENID_2", body.get("touser"));
        assertEquals("TPL-2002", body.get("template_id"));
        assertEquals("SCENE-8", body.get("scene"));
        assertEquals("发货通知", body.get("title"));
        @SuppressWarnings("unchecked")
        Map<String, Object> content = (Map<String, Object>) ((Map<String, Object>) body.get("data")).get("content");
        assertEquals("包裹已发出", content.get("value"));
    }

    @Test
    void testSubscriptionNoticeRequiredFields() {
        SubscriptionNotice base = new SubscriptionNotice()
                .toUser("O").templateId("T").title("标题").content("内容");
        assertNotNull(base.toJsonBody(), "齐备字段应通过");

        assertThrows(IllegalArgumentException.class,
                () -> new SubscriptionNotice().templateId("T").title("标题").content("内容").toJsonBody(),
                "缺 toUser 必须拒绝");
        assertThrows(IllegalArgumentException.class,
                () -> new SubscriptionNotice().toUser("O").title("标题").content("内容").toJsonBody(),
                "缺 template_id 必须拒绝");
        assertThrows(IllegalArgumentException.class,
                () -> new SubscriptionNotice().toUser("O").templateId("T").title("标题").toJsonBody(),
                "订阅通知必须携带 content 数据项");
        assertThrows(IllegalArgumentException.class,
                () -> new SubscriptionNotice().toUser("O").templateId("T").content("内容").toJsonBody(),
                "缺 title 必须拒绝");
    }

    @Test
    void testTemplateDataItemWire() {
        assertEquals("值", TemplateDataItem.ofValue("值").toWire().get("value"));
        Map<String, Object> colored = TemplateDataItem.colored("值", "#FF0000").toWire();
        assertEquals("#FF0000", colored.get("color"));
    }

    @Test
    void testMiniProgramTargetRequiresAppId() {
        assertThrows(IllegalArgumentException.class,
                () -> new MiniProgramTarget(null, "pages/a"), "appId 为空必须拒绝");
        Map<String, Object> wire = new MiniProgramTarget("wx123", "pages/b").toWire();
        assertEquals("wx123", wire.get("appid"));
    }
}
