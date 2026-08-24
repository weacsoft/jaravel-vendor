package com.weacsoft.jaravel.vendor.wechat.kernel;

import com.weacsoft.jaravel.vendor.wechat.WechatProperties;
import com.weacsoft.jaravel.vendor.wechat.crypto.WxBizMsgCrypt;
import com.weacsoft.jaravel.vendor.wechat.crypto.WechatCryptoException;
import com.weacsoft.jaravel.vendor.wechat.message.Image;
import com.weacsoft.jaravel.vendor.wechat.message.Text;
import com.weacsoft.jaravel.vendor.wechat.message.WeChatCard;
import com.weacsoft.jaravel.vendor.wechat.server.MessageParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 洋葱内核（WechatKernel / WechatRequest / WechatResponse / WechatMiddleware）全链路测试。
 */
class WeChatKernelTest {

    private static final String TOKEN = "kern_token";
    private static final String APPID = "wxkern1234567890ab";
    private static final String AES_KEY = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";

    private static WechatProperties.OfficialAccountConfig cfg(String mode, boolean aes) {
        WechatProperties.OfficialAccountConfig c = new WechatProperties.OfficialAccountConfig();
        c.setAppId(APPID);
        c.setSecret("s");
        c.setToken(TOKEN);
        if (aes) {
            c.setAesKey(AES_KEY);
        }
        c.setMessageMode(mode);
        return c;
    }

    private static String textPush(String content) {
        return "<xml>" +
                "<ToUserName><![CDATA[gh_oa]]></ToUserName>" +
                "<FromUserName><![CDATA[openid123]]></FromUserName>" +
                "<CreateTime>1407564400</CreateTime>" +
                "<MsgType><![CDATA[text]]></MsgType>" +
                "<Content><![CDATA[" + content + "]]></Content>" +
                "<MsgId>1</MsgId>" +
                "</xml>";
    }

    // ===== Request 提取 =====

    @Test
    void testRequestExtractors() {
        WechatRequest req = WechatRequest.ofMessage(new LinkedHashMap<>(), textPush("你好"), "default", cfg("plain", false));
        assertTrue(req.isText(), "isText 应识别 text 推送");
        assertEquals("你好", req.textContent(), "textContent 应提取内容");
        assertEquals("openid123", req.openid(), "openid 应为发件人 FromUserName");
        assertEquals("gh_oa", req.toOpenid(), "toOpenid 应为 ToUserName");
        assertFalse(req.isVerify());
        assertFalse(req.isEvent());
        assertFalse(req.isScan());
        assertEquals("plain", "plain");
    }

    @Test
    void testVerifyRequestRejectsMessageAccess() {
        WechatRequest req = WechatRequest.ofVerify(new LinkedHashMap<>(), "default", cfg("plain", false));
        assertTrue(req.isVerify());
        assertThrows(IllegalStateException.class, req::plainXml, "验签请求无消息体");
        assertThrows(IllegalStateException.class, req::message, "验签请求无消息体");
    }

    @Test
    void testRequestExtractEncryptMissing() {
        WechatRequest req = WechatRequest.ofMessage(new LinkedHashMap<>(), "<xml><A>1</A></xml>", "default", cfg("plain", false));
        WechatCryptoException ex = assertThrows(WechatCryptoException.class, req::extractEncrypt,
                "缺 <Encrypt> 节点必须报错");
        assertTrue(ex.getMessage().contains("Encrypt"));
    }

    // ===== Response 组装/提取 =====

    @Test
    void testResponseKinds() {
        assertTrue(WechatResponse.echostr("E").isEcho());
        assertTrue(WechatResponse.text("hi").isMessage());
        assertTrue(WechatResponse.image("M").isMessage());
        assertTrue(WechatResponse.empty().isEmpty());
        assertTrue(WechatResponse.rawXml("<x/>").isRaw());

        assertEquals("E", WechatResponse.echostr("E").echostr());
        assertThrows(IllegalStateException.class, () -> WechatResponse.text("hi").echostr(),
                "形态不符必须报错");
        assertThrows(IllegalArgumentException.class, () -> WechatResponse.message(null));
    }

    @Test
    void testResponseReplyXmlSwapsDirection() {
        WechatResponse resp = WechatResponse.text("应答");
        String xml = resp.toReplyXml("openid123", "gh_oa");
        assertTrue(xml.contains("openid123"), "ToUserName 应为用户");
        assertTrue(xml.contains("gh_oa"), "FromUserName 应为公众号");
        assertTrue(xml.contains("应答"));
        assertTrue(xml.contains("MsgType"));
    }

    @Test
    void testResponseUnsupportedXmlCapable() {
        WechatResponse resp = WechatResponse.message(new WeChatCard("C1"));
        WechatCryptoException ex = assertThrows(WechatCryptoException.class, () -> resp.toReplyXml("a", "b"));
        assertTrue(ex.getMessage().contains("WeChatCard"), "异常应指明消息类");
    }

    // ===== Kernel 洋葱语义 =====

    @Test
    void testKernelGetPlainVerify() {
        WechatKernel kernel = new WechatKernel("default", cfg("plain", false));
        WxBizMsgCrypt crypt = new WxBizMsgCrypt(TOKEN, null, APPID);
        String sig = crypt.sign("1", "2", "ECHO");
        Map<String, String> q = new LinkedHashMap<>();
        q.put("timestamp", "1");
        q.put("nonce", "2");
        q.put("echostr", "ECHO");
        q.put("signature", sig);
        assertEquals("ECHO", kernel.handleGet(q), "验签通过回包 echostr");
    }

    @Test
    void testKernelGetRejectsBadSignature() {
        WechatKernel kernel = new WechatKernel("default", cfg("plain", false));
        Map<String, String> q = new LinkedHashMap<>();
        q.put("timestamp", "1");
        q.put("nonce", "2");
        q.put("echostr", "ECHO");
        q.put("signature", "bad");
        assertThrows(WechatCryptoException.class, () -> kernel.handleGet(q));
    }

    @Test
    void testKernelPostDefaultEmptyWhenNoHandlers() {
        WechatKernel kernel = new WechatKernel("default", cfg("plain", false));
        assertEquals("", kernel.handlePost(new LinkedHashMap<>(), textPush("hi")),
                "无业务层时默认不回复（空串退避）");
    }

    @Test
    void testKernelHandlerTextReply() {
        WechatKernel kernel = new WechatKernel("default", cfg("plain", false))
                .middleware((req, next) -> req.isText()
                        ? WechatResponse.text("echo: " + req.textContent())
                        : next.handle(req));
        String reply = kernel.handlePost(new LinkedHashMap<>(), textPush("你好"));
        assertTrue(reply.contains("echo: 你好"));
        assertTrue(reply.contains("openid123"));
    }

    @Test
    void testKernelMiddlewareOrderInnerToOuter() {
        // 洋葱：先注册者更靠外 → 执行顺序：A 进入 → B 进入 → B 离开 → A 离开
        List<String> trace = new ArrayList<>();
        WechatKernel kernel = new WechatKernel("default", cfg("plain", false))
                .middleware((req, next) -> {
                    trace.add("A-in");
                    WechatResponse r = next.handle(req);
                    trace.add("A-out");
                    return r;
                })
                .middleware((req, next) -> {
                    trace.add("B-in");
                    WechatResponse r = next.handle(req);
                    trace.add("B-out");
                    return r;
                });
        kernel.handlePost(new LinkedHashMap<>(), textPush("hi"));
        assertEquals(List.of("A-in", "B-in", "B-out", "A-out"), trace, "洋葱出入顺序应严格嵌套");
    }

    @Test
    void testKernelShortCircuitStopsInnerLayers() {
        List<String> trace = new ArrayList<>();
        WechatKernel kernel = new WechatKernel("default", cfg("plain", false))
                .middleware((req, next) -> {
                    trace.add("outer");
                    return WechatResponse.text("short");
                })
                .middleware((req, next) -> {
                    trace.add("inner");
                    return next.handle(req);
                });
        String reply = kernel.handlePost(new LinkedHashMap<>(), textPush("hi"));
        assertTrue(reply.contains("short"));
        assertEquals(List.of("outer"), trace, "短路后内层不得执行");
    }

    @Test
    void testKernelMiddlewareImmutability() {
        WechatKernel base = new WechatKernel("default", cfg("plain", false));
        WechatKernel withHandler = base.middleware((req, next) -> WechatResponse.text("x"));
        assertNotSame(base, withHandler, "middleware 必须返回新实例");
        assertEquals("", base.handlePost(new LinkedHashMap<>(), textPush("a")), "原实例行为不变");
        assertTrue(withHandler.handlePost(new LinkedHashMap<>(), textPush("a")).contains("x"));
    }

    @Test
    void testKernelBareMiddlewareExceptionPropagates() {
        // 裸洋葱层的异常直接向上抛（由开发者控制）；
        // 「吞异常按空串应答」的旧语义由 ResponderMiddleware（WeChatServer.handlePost 的 responder 路径）承载，
        // 见 WeChatServerTest.testHandlePostResponderExceptionYieldsEmpty。
        WechatKernel kernel = new WechatKernel("default", cfg("plain", false))
                .middleware((req, next) -> {
                    throw new IllegalStateException("boom");
                });
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> kernel.handlePost(new LinkedHashMap<>(), textPush("hi")));
        assertEquals("boom", ex.getMessage());
    }

    @Test
    void testKernelParse() {
        WechatKernel kernel = new WechatKernel("default", cfg("plain", false));
        com.weacsoft.jaravel.vendor.wechat.server.ServerMessage msg =
                kernel.parse(new LinkedHashMap<>(), textPush("parsing"));
        assertTrue(MessageParser.isText(msg));
        assertEquals("parsing", MessageParser.asText(msg).getContent());
    }

    // ===== safe 模式洋葱链路 =====

    private static Map<String, String> safeEnvelope(String encrypted, String msgSignature) {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("msg_signature", msgSignature);
        q.put("timestamp", "1407564400");
        q.put("nonce", "888");
        return q;
    }

    @Test
    void testSafeKernelGetDecrypts() {
        WechatKernel kernel = new WechatKernel("default", cfg("safe", true));
        WxBizMsgCrypt crypt = new WxBizMsgCrypt(TOKEN, AES_KEY, APPID);
        String encrypted = crypt.encrypt("ECHO_SAFE");
        Map<String, String> q = safeEnvelope(encrypted, crypt.sign("1407564400", "888", encrypted));
        q.put("echostr", encrypted);
        assertEquals("ECHO_SAFE", kernel.handleGet(q));
    }

    @Test
    void testSafeKernelPostRoundtripWithMiddleware() {
        WechatKernel kernel = new WechatKernel("default", cfg("safe", true));
        WxBizMsgCrypt crypt = new WxBizMsgCrypt(TOKEN, AES_KEY, APPID);
        String encrypted = crypt.encrypt(textPush("密文进"));
        Map<String, String> q = safeEnvelope(encrypted, crypt.sign("1407564400", "888", encrypted));
        String pushXml = "<xml>" +
                "<Encrypt><![CDATA[" + encrypted + "]]></Encrypt>" +
                "<MsgSignature><![CDATA[" + encrypted + "]]></MsgSignature>" +
                "<TimeStamp>1407564400</TimeStamp>" +
                "<Nonce>888</Nonce>" +
                "</xml>";

        // parse：验签+解密+解析
        com.weacsoft.jaravel.vendor.wechat.server.ServerMessage parsed = kernel.parse(q, pushXml);
        assertTrue(MessageParser.isText(parsed));

        // handlePost：洋葱层应答 → 加密回包 → 微信侧可解密
        String reply = kernel.middleware((req, next) -> WechatResponse.text("密文出")).handlePost(q, pushXml);
        assertTrue(reply.contains("<Encrypt>"), "safe 应答应带 Encrypt");
        Map<String, Object> nodes = com.weacsoft.jaravel.vendor.wechat.xml.XmlUtil.parseXml(reply);
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) nodes.get("xml");
        String replyEnc = String.valueOf(root.get("Encrypt")).trim();
        String plain = crypt.decrypt(replyEnc);
        assertTrue(plain.contains("密文出"));
        assertTrue(plain.contains("openid123"));
    }

    @Test
    void testSafeKernelBadSignThrows() {
        WechatKernel kernel = new WechatKernel("default", cfg("safe", true));
        String encrypted = "AAAA";
        Map<String, String> q = safeEnvelope(encrypted, "wrong_sig");
        String pushXml = "<xml><Encrypt><![CDATA[" + encrypted + "]]></Encrypt></xml>";
        assertThrows(WechatCryptoException.class, () -> kernel.handlePost(q, pushXml), "safe 验签失败必须拒绝");
    }

    @Test
    void testKernelUnsupportedXmlCapableThroughOnion() {
        WechatKernel kernel = new WechatKernel("default", cfg("plain", false));
        WechatCryptoException ex = assertThrows(WechatCryptoException.class, () ->
                kernel.middleware((req, next) -> WechatResponse.message(new WeChatCard("C1")))
                        .handlePost(new LinkedHashMap<>(), textPush("x")),
                "不支持被动回复的消息类必须抛错");
        assertTrue(ex.getMessage().contains("WeChatCard"), "异常应指明消息类");
    }
}
