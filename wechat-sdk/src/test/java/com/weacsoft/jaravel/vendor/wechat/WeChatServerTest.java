package com.weacsoft.jaravel.vendor.wechat;

import com.weacsoft.jaravel.vendor.wechat.crypto.WechatCryptoException;
import com.weacsoft.jaravel.vendor.wechat.message.Text;
import com.weacsoft.jaravel.vendor.wechat.server.MessageParser;
import com.weacsoft.jaravel.vendor.wechat.server.ServerMessage;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WeChatServer 验签 / 明文回复 / 加密模式 全链路测试。
 */
class WeChatServerTest {

    private static final String TOKEN = "srv_token";
    private static final String APPID = "wx1234567890abcdef";
    private static final String AES_KEY = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";

    private static WechatProperties plainProps() {
        WechatProperties props = new WechatProperties();
        WechatProperties.OfficialAccountConfig cfg = new WechatProperties.OfficialAccountConfig();
        cfg.setAppId(APPID);
        cfg.setSecret("secret");
        cfg.setToken(TOKEN);
        cfg.setMessageMode("plain");
        props.getOfficialAccounts().put("default", cfg);
        return props;
    }

    private static WechatProperties safeProps() {
        WechatProperties props = new WechatProperties();
        WechatProperties.OfficialAccountConfig cfg = new WechatProperties.OfficialAccountConfig();
        cfg.setAppId(APPID);
        cfg.setSecret("secret");
        cfg.setToken(TOKEN);
        cfg.setAesKey(AES_KEY);
        cfg.setMessageMode("safe");
        props.getOfficialAccounts().put("default", cfg);
        return props;
    }

    private static Map<String, String> query(String signature, String timestamp, String nonce, String echostr) {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("signature", signature);
        q.put("timestamp", timestamp);
        q.put("nonce", nonce);
        if (echostr != null) {
            q.put("echostr", echostr);
        }
        return q;
    }

    private static String textPush(String content) {
        return "<xml>" +
                "<ToUserName><![CDATA[gh_oa]]></ToUserName>" +
                "<FromUserName><![CDATA[openid123]]></FromUserName>" +
                "<CreateTime>1407564400</CreateTime>" +
                "<MsgType><![CDATA[text]]></MsgType>" +
                "<Content><![CDATA[" + content + "]]></Content>" +
                "<MsgId>1234567890</MsgId>" +
                "</xml>";
    }

    @Test
    void testHandleGetPlainVerifyAndEcho() {
        WeChatServer server = new WeChatServer(plainProps(), "default");
        assertEquals(WeChatServer.MODE_PLAIN, server.getMode());
        // 签名 = sha1(sort(token,timestamp,nonce,echostr))
        com.weacsoft.jaravel.vendor.wechat.crypto.WxBizMsgCrypt crypt =
                new com.weacsoft.jaravel.vendor.wechat.crypto.WxBizMsgCrypt(TOKEN, null, APPID);
        String signature = crypt.sign("1407564400", "999", "ECHO123");
        assertEquals("ECHO123", server.handleGet(query(signature, "1407564400", "999", "ECHO123")),
                "验签通过后应原样返回 echostr");
    }

    @Test
    void testHandleGetPlainRejectsBadSignature() {
        WeChatServer server = new WeChatServer(plainProps(), "default");
        assertThrows(WechatCryptoException.class,
                () -> server.handleGet(query("bad_signature", "1407564400", "999", "ECHO123")),
                "签名不匹配必须拒绝");
    }

    @Test
    void testHandlePostPlainEchoReply() {
        WeChatServer server = new WeChatServer(plainProps(), "default");
        String reply = server.handlePost(new LinkedHashMap<>(), textPush("你好"),
                (msg, srv) -> {
                    if (MessageParser.isText(msg)) {
                        return new Text("你说了: " + MessageParser.asText(msg).getContent());
                    }
                    return null;
                });
        assertTrue(reply.contains("你说了: 你好"), "被动回复应包含应答文案");
        // 方向互换：ToUserName=用户 openid，FromUserName=公众号
        assertTrue(reply.contains("openid123"), "回复 ToUserName 应为用户");
        assertTrue(reply.contains("gh_oa"), "回复 FromUserName 应为公众号");
        assertTrue(reply.contains("MsgType"), "回复应含 MsgType");
    }

    @Test
    void testHandlePostNullResponderReturnsEmpty() {
        WeChatServer server = new WeChatServer(plainProps(), "default");
        assertEquals("", server.handlePost(new LinkedHashMap<>(), textPush("hi"), null),
                "无 responder 时按微信规范应答空串（不触发重试）");
    }

    @Test
    void testHandlePostResponderReturnsNullYieldsEmpty() {
        WeChatServer server = new WeChatServer(plainProps(), "default");
        assertEquals("", server.handlePost(new LinkedHashMap<>(), textPush("hi"), (m, s) -> null),
                "responder 返回 null 时应答空串");
    }

    @Test
    void testHandlePostResponderExceptionYieldsEmpty() {
        WeChatServer server = new WeChatServer(plainProps(), "default");
        String ok = server.handlePost(new LinkedHashMap<>(), textPush("hi"),
                (m, s) -> {
                    throw new IllegalStateException("业务异常");
                });
        assertEquals(ok, "", "业务异常应吞掉并按空串应答，避免微信重试 3 次");
    }

    @Test
    void testHandlePostImageReply() {
        WeChatServer server = new WeChatServer(plainProps(), "default");
        String reply = server.handlePost(new LinkedHashMap<>(), textPush("发图"),
                (m, s) -> new com.weacsoft.jaravel.vendor.wechat.message.Image("MEDIA_ID_1"));
        assertTrue(reply.contains("MEDIA_ID_1"), "image 被动回复应携带 MediaId");
        assertTrue(reply.contains("image"));
    }

    @Test
    void testUnsupportedReplyTypeThrows() {
        WeChatServer server = new WeChatServer(plainProps(), "default");
        // WeChatCard 不支持被动回复（微信仅支持 text/image/voice/video/music/news）
        WechatCryptoException ex = assertThrows(WechatCryptoException.class,
                () -> server.handlePost(new LinkedHashMap<>(), textPush("卡"),
                        (m, s) -> new com.weacsoft.jaravel.vendor.wechat.message.WeChatCard("CARD1")),
                "不支持被动回复的消息类应明确报错");
        assertTrue(ex.getMessage().contains("WeChatCard"), "异常应指明消息类型");
    }

    @Test
    void testSafeModeRequiresTokenAndAesKey() {
        WechatProperties props = new WechatProperties();
        WechatProperties.OfficialAccountConfig cfg = new WechatProperties.OfficialAccountConfig();
        cfg.setAppId(APPID);
        cfg.setSecret("s");
        cfg.setToken(TOKEN);
        // 无 aes-key → safe 模式构造必须失败
        cfg.setMessageMode("safe");
        props.getOfficialAccounts().put("default", cfg);
        assertThrows(IllegalStateException.class, () -> new WeChatServer(props, "default"),
                "safe 模式缺少 aes-key 应拒绝装配");
    }

    @Test
    void testSafeModeGetDecryptsEchostr() {
        WeChatServer server = new WeChatServer(safeProps(), "default");
        assertTrue(server.isSafeMode());
        com.weacsoft.jaravel.vendor.wechat.crypto.WxBizMsgCrypt crypt =
                new com.weacsoft.jaravel.vendor.wechat.crypto.WxBizMsgCrypt(TOKEN, AES_KEY, APPID);
        String encrypted = crypt.encrypt("ECHO_SAFE");
        String msgSignature = crypt.sign("1407564400", "777", encrypted);
        Map<String, String> q = new LinkedHashMap<>();
        q.put("msg_signature", msgSignature);
        q.put("timestamp", "1407564400");
        q.put("nonce", "777");
        q.put("echostr", encrypted);
        assertEquals("ECHO_SAFE", server.handleGet(q), "安全模式 GET 应解密后返回 echostr");
    }

    @Test
    void testSafeModePostRoundtrip() {
        WeChatServer server = new WeChatServer(safeProps(), "default");
        com.weacsoft.jaravel.vendor.wechat.crypto.WxBizMsgCrypt crypt =
                new com.weacsoft.jaravel.vendor.wechat.crypto.WxBizMsgCrypt(TOKEN, AES_KEY, APPID);

        // 1) 微信侧加密推送
        String plainPush = textPush("加密测试");
        String encrypted = crypt.encrypt(plainPush);
        String msgSignature = crypt.sign("1407564400", "888", encrypted);
        Map<String, String> q = new LinkedHashMap<>();
        q.put("msg_signature", msgSignature);
        q.put("timestamp", "1407564400");
        q.put("nonce", "888");
        String pushXml = "<xml>" +
                "<Encrypt><![CDATA[" + encrypted + "]]></Encrypt>" +
                "<MsgSignature><![CDATA[" + msgSignature + "]]></MsgSignature>" +
                "<TimeStamp>1407564400</TimeStamp>" +
                "<Nonce>888</Nonce>" +
                "</xml>";

        // 2) parsePost 应解密并解析
        ServerMessage parsed = server.parsePost(q, pushXml);
        assertTrue(MessageParser.isText(parsed));
        assertEquals("加密测试", MessageParser.asText(parsed).getContent());

        // 3) handlePost 应答必须是加密 XML，且可被微信侧解密回原文
        String reply = server.handlePost(q, pushXml, (m, s) -> new Text("加密回复"));
        assertTrue(reply.contains("<Encrypt>"), "安全模式应答应包含 Encrypt 节点");
        Map<String, Object> nodes = com.weacsoft.jaravel.vendor.wechat.xml.XmlUtil.parseXml(reply);
        Object root = nodes.get("xml");
        @SuppressWarnings("unchecked")
        Map<String, Object> replyNodes = (Map<String, Object>) root;
        String replyEncrypted = String.valueOf(replyNodes.get("Encrypt")).trim();
        String replyPlain = crypt.decrypt(replyEncrypted);
        assertTrue(replyPlain.contains("加密回复"), "应答密文解密后应含被动回复文案");
        assertTrue(replyPlain.contains("openid123"), "应答方向：ToUserName=用户");
    }

    @Test
    void testSafeModeRejectsBadSignature() {
        WeChatServer server = new WeChatServer(safeProps(), "default");
        Map<String, String> q = new LinkedHashMap<>();
        q.put("msg_signature", "not_the_right_signature");
        q.put("timestamp", "1");
        q.put("nonce", "2");
        q.put("echostr", "anything");
        assertThrows(WechatCryptoException.class, () -> server.handleGet(q), "安全模式验签失败必须拒绝");
    }

    @Test
    void testConfigNameLookupAndFallback() {
        WechatProperties props = plainProps();
        WechatProperties.OfficialAccountConfig other = new WechatProperties.OfficialAccountConfig();
        other.setAppId(APPID);
        other.setToken(TOKEN);
        props.getOfficialAccounts().put("other", other);
        // 未配置的别名
        assertThrows(IllegalStateException.class, () -> new WeChatServer(props, "missing"));
        // 配置的别名可用
        WeChatServer s2 = new WeChatServer(props, "other");
        assertNotNull(s2);
    }
}
