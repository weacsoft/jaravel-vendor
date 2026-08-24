package com.weacsoft.jaravel.vendor.wechat.user;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户 / 标签 / 素材 / 客服 / 会话 模型解析测试。
 */
class UserModelsTest {

    @Test
    void testWeChatUserFrom() {
        Map<String, Object> raw = new java.util.LinkedHashMap<>();
        raw.put("openid", "openid_abc");
        raw.put("nickname", "小明");
        raw.put("sex", 2);
        raw.put("language", "zh_CN");
        raw.put("city", "深圳");
        raw.put("province", "广东");
        raw.put("country", "中国");
        raw.put("headimgurl", "https://thirdwx.qlogo.cn/x");
        raw.put("subscribe", true);
        raw.put("subscribe_time", 1407564400);
        raw.put("remark", "VIP");
        raw.put("groupid", 7);
        raw.put("tagid_list", List.of(1, 2, 3));
        raw.put("tagid_list_size", 3);
        raw.put("unionid", "union_xyz");
        WeChatUser user = WeChatUser.from(raw);
        assertEquals("openid_abc", user.getOpenId());
        assertEquals("小明", user.getNickname());
        assertEquals(WeChatUser.SEX_MALE, user.getSex());
        assertEquals("zh_CN", user.getLanguage());
        assertEquals("深圳", user.getCity());
        assertEquals("https://thirdwx.qlogo.cn/x", user.getHeadimgUrl());
        assertTrue(user.isSubscribed());
        assertEquals(1407564400L, user.getSubscribeTime());
        assertEquals(7, user.getGroupId());
        assertEquals(List.of(1, 2, 3), user.getTagIds());
        assertEquals(3, user.getTagIdListSize());
        assertEquals("union_xyz", user.getUnionId());
    }

    @Test
    void testWeChatUserUnionIdOptional() {
        // unionid 未绑定场景：字段缺省不抛错，getter 返回 null
        Map<String, Object> raw = Map.of("openid", "o1", "subscribe", false);
        WeChatUser user = WeChatUser.from(raw);
        assertNull(user.getUnionId());
        assertFalse(user.isSubscribed());
        assertTrue(user.getTagIds().isEmpty(), "无 tagid_list 时返回空列表");
        assertEquals(WeChatUser.SEX_UNKNOWN, user.getSex());
    }

    @Test
    void testTagFrom() {
        Tag tag = Tag.from(Map.of("id", 12, "name", "活跃用户", "count", 42));
        assertEquals(12, tag.getId());
        assertEquals("活跃用户", tag.getName());
        assertEquals(42, tag.getCount());
    }

    @Test
    void testMaterialItemFrom() {
        Map<String, Object> raw = Map.of(
                "media_id", "M_1",
                "name", "news_1",
                "update_time", 1700000000,
                "type", "news",
                "url", "https://mmbiz.qpic.cn/x",
                "content", Map.of()
        );
        MaterialItem item = MaterialItem.from(raw);
        assertEquals("M_1", item.getMediaId());
        assertEquals("news", item.getType());
        assertEquals(1700000000L, item.getUpdateTime());
    }

    @Test
    void testKfAccountFrom() {
        KfAccount kf = KfAccount.from(Map.of(
                "kf_account", "admin@example.com",
                "kf_id", "kf_001",
                "name", "客服一号",
                "email", "admin@example.com"
        ));
        assertEquals("admin@example.com", kf.getKfAccount());
        assertEquals("kf_001", kf.getKfId());
        assertEquals("客服一号", kf.getName());
    }

    @Test
    void testChatRecordFrom() {
        Map<String, Object> raw = new java.util.LinkedHashMap<>();
        raw.put("openid", "o1");
        raw.put("trans_id", "TRANS_1");
        raw.put("create_time", 1690000000);
        raw.put("valid", true);
        raw.put("msgtype", "text");
        raw.put("content", Map.of("text", "你好"));
        ChatRecord record = ChatRecord.from(raw);
        assertEquals("o1", record.getOpenid());
        assertEquals("TRANS_1", record.getTransId());
        assertEquals(1690000000L, record.getCreateTime());
        assertTrue(record.isValid());
        assertEquals("text", record.getMsgType());
        assertEquals("你好", record.getTextColor(), "text 类型会话记录应能取到文本内容");
    }
}
