package com.weacsoft.jaravel.vendor.wechat.message;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Message 类型族 toJsonBody / toXmlArray 形态测试（kefu JSON 契约）。
 * <p>
 * 注意：fluent 方法（toUser/withKfAccount/withAiMsg）返回基类 {@link Message}，
 * 因此测试中统一以 Message 变量承接。
 */
class MessageSerializationTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> typeNode(Map<String, Object> body, String type) {
        return (Map<String, Object>) body.get(type);
    }

    @Test
    void testTextKeFuBody() {
        Message text = new Text("你好").toUser("OPENID");
        Map<String, Object> body = text.toJsonBody();
        assertEquals("OPENID", body.get("touser"));
        assertEquals("text", body.get("msgtype"));
        assertEquals("你好", typeNode(body, "text").get("content"));
        assertEquals("text", text.getType(), "MsgType 应为 text");
    }

    @Test
    void testTextKeFuWithKfAccountAndAiMsg() {
        Message text = new Text("hi").toUser("OID")
                .withKfAccount("kf1@weixin")
                .withAiMsg(true);
        Map<String, Object> body = text.toJsonBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> customservice = (Map<String, Object>) body.get("customservice");
        assertEquals("kf1@weixin", customservice.get("kf_account"));
        @SuppressWarnings("unchecked")
        Map<String, Object> aimsg = (Map<String, Object>) body.get("aimsgcontext");
        assertEquals(1, aimsg.get("is_ai_msg"), "is_ai_msg 为 0/1 数值形态");
    }

    @Test
    void testTextContentRequired() {
        assertThrows(IllegalArgumentException.class,
                () -> new Text("").toJsonBody(), "空文案应快速失败（构造时）");
    }

    @Test
    void testNewsSingleArticleForKeFu() {
        NewsItem item = new NewsItem("标题", "描述", "https://u.example/p.png", "https://u.example/1");
        Message news = new News(List.of(item)).toUser("OID");
        Map<String, Object> body = news.toJsonBody();
        @SuppressWarnings("unchecked")
        List<?> articles = (List<?>) typeNode(body, "news").get("articles");
        assertEquals(1, articles.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) articles.get(0);
        assertEquals("https://u.example/p.png", first.get("picurl"),
                "news 条目 pic 字段应为 picurl（kefu 约定）");
    }

    @Test
    void testNewsMoreThanOneArticleRejectedForKeFu() {
        NewsItem a = new NewsItem("A", null, "https://pa", "https://a");
        NewsItem b = new NewsItem("B", null, "https://pb", "https://b");
        Message news = new News(List.of(a, b)).toUser("OID");
        assertThrows(IllegalArgumentException.class, news::toJsonBody,
                "kefu news 仅支持 1 条（>1 应抛错，避免静默截断）");
    }

    @Test
    void testNewsXmlArray() {
        NewsItem item = new NewsItem("标题", null, "https://p", "https://u");
        Map<String, Object> xml = new News(List.of(item)).toXmlArray();
        assertEquals(1, xml.get("ArticleCount"));
        assertTrue(xml.keySet().stream().anyMatch(k -> k.toString().equalsIgnoreCase("Articles")),
                "news 被动回复 XML 需要 Articles 节点");
    }

    @Test
    void testTextXmlArray() {
        Map<String, Object> xml = new Text("回复文本").toXmlArray();
        assertEquals("回复文本", xml.get("Content"));
    }

    @Test
    void testMusicWireKeys() {
        Message music = new Music("歌名", "描述", "https://m.example.mp3", "https://hq.example.mp3", "https://t.example.jpg")
                .toUser("OID");
        Map<String, Object> node = typeNode(music.toJsonBody(), "music");
        assertEquals("https://m.example.mp3", node.get("musicurl"), "musicurl 是微信约定的 url 字段名");
        assertEquals("https://hq.example.mp3", node.get("hqmusicurl"));
        assertEquals("https://t.example.jpg", node.get("thumb_media_id"));
    }

    @Test
    void testMusicRequiredFields() {
        assertThrows(IllegalArgumentException.class,
                () -> new Music("歌名", "描述", "", null, "t"), "空 musicUrl 应快速失败");
    }

    @Test
    void testVideoRequiredFieldsAndWire() {
        Message video = new Video("VID", "THUMB", "片名", "片描述").toUser("OID");
        Map<String, Object> node = typeNode(video.toJsonBody(), "video");
        assertEquals("VID", node.get("media_id"));
        assertEquals("THUMB", node.get("thumb_media_id"));
        assertEquals("片名", node.get("title"));
        assertThrows(IllegalArgumentException.class, () -> new Video("V", null, null, null),
                "空 thumbMediaId 应快速失败");
    }

    @Test
    void testMiniProgramPageWireAndValidation() {
        Message page = new MiniProgramPage("标题", "wxAPP", "pages/a", "THUMB").toUser("OID");
        Map<String, Object> node = typeNode(page.toJsonBody(), "miniprogrampage");
        assertEquals("wxAPP", node.get("appid"));
        assertEquals("pages/a", node.get("pagepath"));
        assertEquals("THUMB", node.get("thumb_media_id"));
    }

    @Test
    void testWeChatCardWire() {
        Message card = new WeChatCard("CARD123").toUser("OID");
        Map<String, Object> node = typeNode(card.toJsonBody(), "wxcard");
        assertEquals("CARD123", node.get("card_id"));
    }

    @Test
    void testMsgMenuWire() {
        MenuMessage.Item a = new MenuMessage.Item(0, "文案A");
        MenuMessage.Item b = new MenuMessage.Item(1, "文案B");
        Message menu = new MenuMessage("你好", List.of(a, b), "再见");
        Map<String, Object> node = typeNode(menu.toJsonBody(), "msgmenu");
        assertEquals("你好", node.get("head_content"));
        assertEquals("再见", node.get("tail_content"));
        @SuppressWarnings("unchecked")
        List<?> list = (List<?>) node.get("list");
        assertEquals(2, list.size());
    }

    @Test
    void testMpNewsAndMpNewsArticleWire() {
        Message mp = new MpNews("MEDIA_NEWS").toUser("OID");
        assertEquals("MEDIA_NEWS", typeNode(mp.toJsonBody(), "mpnews").get("media_id"));

        Message art = new MpNewsArticle("ART_1").toUser("OID");
        assertEquals("ART_1", typeNode(art.toJsonBody(), "mpnewsarticle").get("article_id"));
    }

    @Test
    void testMsgMenuEmptyListRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new MenuMessage(null, List.of(), null), "msgmenu 至少 1 项");
    }
}
