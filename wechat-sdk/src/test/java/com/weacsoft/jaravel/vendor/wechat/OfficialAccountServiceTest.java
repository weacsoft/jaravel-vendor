package com.weacsoft.jaravel.vendor.wechat;

import com.weacsoft.jaravel.vendor.wechat.jsdk.JssdkConfig;
import com.weacsoft.jaravel.vendor.wechat.message.Message;
import com.weacsoft.jaravel.vendor.wechat.message.Text;
import com.weacsoft.jaravel.vendor.wechat.menu.Menu;
import com.weacsoft.jaravel.vendor.wechat.menu.MenuItem;
import com.weacsoft.jaravel.vendor.wechat.response.WeChatResponse;
import com.weacsoft.jaravel.vendor.wechat.response.WechatApiException;
import com.weacsoft.jaravel.vendor.wechat.template.TemplateMessage;
import com.weacsoft.jaravel.vendor.wechat.template.TemplateDataItem;
import com.weacsoft.jaravel.vendor.wechat.user.WeChatUser;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * OfficialAccountService 关键链路测试（mock OkHttp + mock AccessTokenManager）。
 */
class OfficialAccountServiceTest {

    private static final String APPID = "wx123";
    private static final String SECRET = "sec123";
    private static final String TOKEN = "TOK123";

    private OkHttpClient mockHttpClient;
    private AccessTokenManager mockTokenManager;
    private WechatProperties props;
    private OfficialAccountService service;

    @BeforeEach
    void setUp() {
        mockHttpClient = mock(OkHttpClient.class);
        mockTokenManager = mock(AccessTokenManager.class);
        when(mockTokenManager.getToken(APPID, SECRET)).thenReturn(TOKEN);
        props = new WechatProperties();
        props.setTokenMode("legacy");
        WechatProperties.OfficialAccountConfig cfg = new WechatProperties.OfficialAccountConfig();
        cfg.setAppId(APPID);
        cfg.setSecret(SECRET);
        cfg.setToken("servertoken");
        props.getOfficialAccounts().put("default", cfg);
        service = new OfficialAccountService(mockTokenManager, props, mockHttpClient);
    }

    // ---- helpers ----

    private void mockResponse(int code, String jsonBody) {
        try {
            Call mockCall = mock(Call.class);
            Response response = new Response.Builder()
                    .request(new Request.Builder().url("https://api.weixin.qq.com/test").build())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("OK")
                    .body(ResponseBody.create(jsonBody, MediaType.parse("application/json")))
                    .build();
            when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
            when(mockCall.execute()).thenReturn(response);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void mockResponses(Response... responses) {
        try {
            Call mockCall = mock(Call.class);
            when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
            org.mockito.stubbing.OngoingStubbing<Response> stub =
                    when(mockCall.execute()).thenReturn(responses[0]);
            for (int i = 1; i < responses.length; i++) {
                stub.thenReturn(responses[i]);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Request firstRequestSent(int nCalls, int index) {
        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(mockHttpClient, atLeast(nCalls)).newCall(captor.capture());
        return captor.getAllValues().get(index);
    }

    private static String bodyUtf8(Request req) {
        try {
            okio.Buffer buffer = new okio.Buffer();
            req.body().writeTo(buffer);
            return buffer.readUtf8();
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Response jsonResponse(int code, String jsonBody, String contentType) {
        return new Response.Builder()
                .request(new Request.Builder().url("https://api.weixin.qq.com/x").build())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("OK")
                .header("Content-Type", contentType)
                .body(ResponseBody.create(jsonBody, MediaType.parse(contentType)))
                .build();
    }

    // ---- tests ----

    @Test
    void testGetUserTypedResultAndQuery() {
        mockResponse(200, "{\"openid\":\"oid1\",\"nickname\":\"小明\",\"subscribe\":true}");
        WeChatUser user = service.getUser("oid1");
        assertEquals("oid1", user.getOpenId());
        assertEquals("小明", user.getNickname());
        assertTrue(user.isSubscribed());

        Request req = firstRequestSent(1, 0);
        assertEquals("/cgi-bin/user/info", req.url().encodedPath());
        assertEquals("oid1", req.url().queryParameter("openid"));
        assertEquals(TOKEN, req.url().queryParameter("access_token"), "必须携带 access_token 查询参数");
    }

    @Test
    void testGetUserBusinessErrorThrows() {
        mockResponse(200, "{\"errcode\":40003,\"errmsg\":\"invalid openid\"}");
        WechatApiException ex = assertThrows(WechatApiException.class,
                () -> service.getUser("bad_openid"),
                "严格类型方法遇到业务错误必须抛 WechatApiException");
        assertEquals(40003, ex.getErrcode());
    }

    @Test
    void testSendCustomerMessageBody() {
        mockResponse(200, "{\"errcode\":0,\"errmsg\":\"ok\",\"msgid\":\"123\"}");
        WeChatResponse resp = service.sendText("OPENID_A", "你好");
        assertEquals("123", resp.getMsgId());

        Request req = firstRequestSent(1, 0);
        assertEquals("/cgi-bin/message/custom/send", req.url().encodedPath());
        String json = bodyUtf8(req);
        assertTrue(json.contains("\"touser\":\"OPENID_A\""), "客服消息应携带 touser");
        assertTrue(json.contains("\"msgtype\":\"text\""), "客服消息应携带 msgtype");
        assertTrue(json.contains("你好"), "客服消息应携带文案");
        assertEquals(TOKEN, req.url().queryParameter("access_token"));
    }

    @Test
    void testSendTypedCustomerMessage() {
        mockResponse(200, "{\"errcode\":0,\"errmsg\":\"ok\"}");
        Message msg = new Text("hello").toUser("OID").withKfAccount("kf1@corp").withAiMsg(true);
        service.sendCustomerMessage(msg);
        Request req = firstRequestSent(1, 0);
        String json = bodyUtf8(req);
        assertTrue(json.contains("kf1@corp"), "customservice.kf_account 应随请求提交");
        assertTrue(json.contains("is_ai_msg"), "aimsgcontext.is_ai_msg 应随请求提交");
    }

    @Test
    void testSetTypingCommand() {
        mockResponse(200, "{\"errcode\":0,\"errmsg\":\"ok\"}");
        service.setTyping("OID_X", true);
        Request req = firstRequestSent(1, 0);
        assertEquals("/cgi-bin/message/custom/typing", req.url().encodedPath());
        String json = bodyUtf8(req);
        assertTrue(json.contains("\"command\":\"typing\""), "typing 状态下发应携带 command=typing（body JSON）");
        assertTrue(json.contains("\"touser\":\"OID_X\""), "body 应携带接收者 openid");
    }

    @Test
    void testListUserOpenidsPaging() {
        mockResponses(
                jsonResponse(200,
                        "{\"count\":3,\"next_openid\":\"o4\",\"data\":{\"openid_list\":[\"o1\",\"o2\",\"o3\"]}}",
                        "application/json"),
                jsonResponse(200,
                        "{\"count\":2,\"data\":{\"openid_list\":[\"o4\",\"o5\"]}}",
                        "application/json")
        );
        List<String> openids = service.listUserOpenids();
        assertEquals(List.of("o1", "o2", "o3", "o4", "o5"), openids, "分页应汇聚全部 openid");
        Request page2 = firstRequestSent(2, 1);
        assertEquals("/cgi-bin/user/getall", page2.url().encodedPath(), "getall 端点应为 user/getall");
        String body2 = bodyUtf8(page2);
        assertTrue(body2.contains("\"next_openid\":\"o4\""), "第二页应在 body 携带 next_openid");
    }

    @Test
    void testSendTemplateBody() {
        mockResponse(200, "{\"errcode\":0,\"errmsg\":\"ok\"}");
        TemplateMessage t = new TemplateMessage()
                .toUser("OID_T")
                .templateId("TPL_T")
                .data("first", TemplateDataItem.ofValue("hi"));
        service.sendTemplate(t);
        Request req = firstRequestSent(1, 0);
        assertEquals("/cgi-bin/message/template/send", req.url().encodedPath());
        String json = bodyUtf8(req);
        assertTrue(json.contains("OID_T") && json.contains("TPL_T"));
    }

    @Test
    void testGetCustomMenuTyped() {
        mockResponse(200, "{\"menu\":{\"button\":[{\"name\":\"首页\",\"type\":\"click\",\"key\":\"H\"}]}}");
        Menu menu = service.getCustomMenu();
        assertEquals(1, menu.getButtons().size());
        assertEquals("首页", menu.getButtons().get(0).getName());
        assertEquals("H", menu.getButtons().get(0).getKey());
    }

    @Test
    void testGetCustomMenuMissingMenuNodeThrows() {
        mockResponse(200, "{\"errcode\":48001,\"errmsg\":\"api unauthorized\"}");
        assertThrows(WechatApiException.class, service::getCustomMenu);
    }

    @Test
    void testCreateTemporaryQrCodeValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createTemporaryQrCode("SC", 10), "有效期 <60s 必须拒绝");
        assertThrows(IllegalArgumentException.class,
                () -> service.createTemporaryQrCode("SC", 60 * 60 * 24 * 30 + 1), "有效期 >30 天必须拒绝");
    }

    @Test
    void testCreateTemporaryQrCodeBody() {
        mockResponse(200, "{\"ticket\":\"TKT\",\"expire_in\":3600,\"url\":\"https://mp.weixin.qq.com/qrcode/x\"}");
        WeChatResponse resp = service.createTemporaryQrCode("SCENE", 3600);
        assertEquals("TKT", resp.getString("ticket"));

        Request req = firstRequestSent(1, 0);
        assertEquals("/cgi-bin/qrcode/create", req.url().encodedPath());
        String json = bodyUtf8(req);
        assertTrue(json.contains("QR_LIMIT_SCENE"), "临时二维码应为 QR_LIMIT_SCENE");
        assertTrue(json.contains("3600"), "应携带 expire_in=3600");
    }

    @Test
    void testCreatePermanentQrCodeBody() {
        mockResponse(200, "{\"ticket\":\"TKT2\",\"url\":\"u\"}");
        service.createPermanentQrCode("SCENE2");
        Request req = firstRequestSent(1, 0);
        String json = bodyUtf8(req);
        assertTrue(json.contains("QR_LIMIT"), "永久二维码应为 QR_LIMIT 系");
        assertFalse(json.contains("QR_LIMIT_SCENE"), "永久二维码不应为 QR_LIMIT_SCENE");
    }

    @Test
    void testBuildJsSdkConfigSignature() {
        mockResponse(200, "{\"ticket\":\"JSTICKET\",\"expires_in\":7200}");
        JssdkConfig config = service.buildJsSdkConfig("https://example.com/page", List.of("chooseWXPay"), List.of(), false);
        assertEquals(APPID, config.getAppId());
        assertEquals(List.of("chooseWXPay"), config.getJsApiList());
        assertNotNull(config.getSignature());
        assertEquals(40, config.getSignature().length(), "JSSDK signature 应为 sha1 十六进制");

        Request req = firstRequestSent(1, 0);
        assertEquals("/cgi-bin/ticket/getticket", req.url().encodedPath());
        assertEquals("jsapi", req.url().queryParameter("type"), "jsapi ticket 必须 type=jsapi");
    }

    @Test
    void testUnconfiguredAccountRejected() {
        props.getOfficialAccounts().clear();
        assertThrows(IllegalStateException.class, () -> service.getUser("OID"),
                "未配置任何公众号时必须显式失败（而非静默 NPE）");
        assertThrows(IllegalStateException.class, () -> service.getUser("OID", "whatever"),
                "名字与 default 均缺失时同样显式失败");
    }

    @Test
    void testServerFactory() {
        WeChatServer server = service.server();
        assertNotNull(server);
        assertEquals(WeChatServer.MODE_PLAIN, server.getMode(), "未配置 message-mode 时默认 plain");
    }
}
