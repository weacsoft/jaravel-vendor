package com.weacsoft.jaravel.vendor.wechat;

import com.weacsoft.jaravel.vendor.wechat.mini.MiniProgramSession;
import com.weacsoft.jaravel.vendor.wechat.mini.MiniSubscribeMessage;
import com.weacsoft.jaravel.vendor.wechat.mini.PhoneNumberResult;
import com.weacsoft.jaravel.vendor.wechat.response.WechatApiException;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MiniProgramService 关键链路测试（mock OkHttp + mock AccessTokenManager）。
 */
class MiniProgramServiceTest {

    private static final String MINI_APPID = "wxa1234567890abc";
    private static final String MINI_SECRET = "minisecret";
    private static final String TOKEN = "MINITOK";

    private OkHttpClient mockHttpClient;
    private AccessTokenManager mockTokenManager;
    private WechatProperties props;
    private MiniProgramService service;

    @BeforeEach
    void setUp() {
        mockHttpClient = mock(OkHttpClient.class);
        mockTokenManager = mock(AccessTokenManager.class);
        when(mockTokenManager.getToken(MINI_APPID, MINI_SECRET)).thenReturn(TOKEN);
        props = new WechatProperties();
        props.setTokenMode("stable");
        WechatProperties.MiniAppConfig cfg = new WechatProperties.MiniAppConfig();
        cfg.setAppId(MINI_APPID);
        cfg.setSecret(MINI_SECRET);
        cfg.setType(2);
        props.getMiniApps().put("default", cfg);
        service = new MiniProgramService(mockTokenManager, props, mockHttpClient);
    }

    // ---- helpers ----

    private void mockJsonResponse(int code, String jsonBody) {
        try {
            Call mockCall = mock(Call.class);
            Response response = new Response.Builder()
                    .request(new Request.Builder().url("https://api.weixin.qq.com/test").build())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("OK")
                    .header("Content-Type", "application/json")
                    .body(ResponseBody.create(jsonBody, MediaType.parse("application/json")))
                    .build();
            when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
            when(mockCall.execute()).thenReturn(response);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void mockBinaryResponse(byte[] bytes) {
        try {
            Call mockCall = mock(Call.class);
            Response response = new Response.Builder()
                    .request(new Request.Builder().url("https://api.weixin.qq.com/test").build())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Type", "image/png")
                    .body(ResponseBody.create(bytes, MediaType.parse("image/png")))
                    .build();
            when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
            when(mockCall.execute()).thenReturn(response);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Request firstRequestSent() {
        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(mockHttpClient, atLeastOnce()).newCall(captor.capture());
        return captor.getAllValues().get(0);
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

    // ---- tests ----

    @Test
    void testCode2SessionTypedResult() {
        mockJsonResponse(200,
                "{\"openid\":\"openid_m1\",\"session_key\":\"SK_1\",\"unionid\":\"union_m1\",\"errcode\":0,\"errmsg\":\"ok\"}");
        MiniProgramSession session = service.code2Session("default", "JS_CODE_1");
        assertEquals("openid_m1", session.getOpenId());
        assertEquals("SK_1", session.getSessionKey());
        assertEquals("union_m1", session.getUnionId());

        Request req = firstRequestSent();
        assertEquals("/sns/jscode2session", req.url().encodedPath(), "jscode2session 端点应为 /sns/jscode2session");
        assertEquals(MINI_APPID, req.url().queryParameter("appid"));
        assertEquals(MINI_SECRET, req.url().queryParameter("secret"), "jscode2session 直接用 appId/secret 换取会话");
        assertEquals("JS_CODE_1", req.url().queryParameter("js_code"));
        assertEquals("authorization_code", req.url().queryParameter("grant_type"));
    }

    @Test
    void testCode2SessionRejectsError() {
        mockJsonResponse(200, "{\"errcode\":40029,\"errmsg\":\"invalid code\"}");
        WechatApiException ex = assertThrows(WechatApiException.class,
                () -> service.code2Session("default", "BAD_CODE"));
        assertEquals(40029, ex.getErrcode());
    }

    @Test
    void testCode2SessionRequiresCode() {
        assertThrows(IllegalArgumentException.class,
                () -> service.code2Session("default", ""), "空 code 必须快速失败");
    }

    @Test
    void testSendSubscribeMessage() {
        mockJsonResponse(200, "{\"errcode\":0,\"errmsg\":\"ok\"}");
        MiniSubscribeMessage msg = new MiniSubscribeMessage()
                .touser("OPENID_M").templateId("SUB_T").data("thing1", "已发货");
        service.sendSubscribeMessage(msg, "default");
        Request req = firstRequestSent();
        assertEquals("/cgi-bin/message/subscribe/send", req.url().encodedPath());
        String json = bodyUtf8(req);
        assertTrue(json.contains("OPENID_M") && json.contains("SUB_T"));
        assertEquals(TOKEN, req.url().queryParameter("access_token"));
    }

    @Test
    void testGetMiniProgramCodeBytes() {
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, 1, 2, 3};
        mockBinaryResponse(png);
        byte[] code = service.getMiniProgramCode("default", "SCENE_1");
        assertArrayEquals(png, code, "小程序码应原样返回字节流");
        Request req = firstRequestSent();
        assertEquals("/wxa/getwxacode/unlimited", req.url().encodedPath());
        assertEquals("SCENE_1", req.url().queryParameter("scene"));
        assertEquals("430", req.url().queryParameter("width"), "默认宽度 430");
    }

    @Test
    void testGetMiniProgramCodeCapsWidth() {
        byte[] png = new byte[]{1, 2, 3};
        mockBinaryResponse(png);
        service.getMiniProgramCode("default", "SC", null, 2000, "release");
        Request req = firstRequestSent();
        assertEquals("1280", req.url().queryParameter("width"), "宽度必须封顶 1280");
        assertEquals("release", req.url().queryParameter("env_version"));
    }

    @Test
    void testGetMiniProgramCodeBusinessErrorThrows() {
        mockJsonResponse(200, "{\"errcode\":41030,\"errmsg\":\"invalid page\"}");
        assertThrows(WechatApiException.class,
                () -> service.getMiniProgramCode("default", "SC", "bad/page", 430, null),
                "图片接口返回 JSON 业务错误必须抛 WechatApiException");
    }

    @Test
    void testGenerateUrlLink() {
        mockJsonResponse(200, "{\"url\":\"https://wxa.url.cn/w/123\"}");
        String link = service.generateUrlLink("default", "pages/a", "id=1");
        assertEquals("https://wxa.url.cn/w/123", link, "应返回 url_link 字符串");
        Request req = firstRequestSent();
        assertEquals("/cgi-bin/generate_urllink", req.url().encodedPath());
    }

    @Test
    void testGenerateUrlLinkErrorThrows() {
        mockJsonResponse(200, "{\"errcode\":40001,\"errmsg\":\"invalid credential\"}");
        assertThrows(WechatApiException.class, () -> service.generateUrlLink("default", "p", null));
    }

    @Test
    void testGenerateScheme() {
        mockJsonResponse(200, "{\"openlink\":\"weixin://dl/business/xxx\"}");
        String scheme = service.generateScheme("default", "pages/a", "id=1", 1, 86400);
        assertEquals("weixin://dl/business/xxx", scheme);
        Request req = firstRequestSent();
        assertEquals("/cgi-bin/generate_scheme", req.url().encodedPath());
    }

    @Test
    void testGetPhoneNumber() {
        mockJsonResponse(200, "{\"phone_info\":{\"phoneNumber\":\"86139\",\"purePhoneNumber\":\"139\",\"countryCode\":\"86\"},\"openid\":\"o1\"}");
        PhoneNumberResult result = service.getPhoneNumber("default", "PH_CODE");
        assertEquals("86139", result.getPhone());
        Request req = firstRequestSent();
        // 官方端点 phonenumber/get（小程序域，与 jscode2session 同在 api.weixin.qq.com 下）
        assertEquals("/phonenumber/get", req.url().encodedPath());
    }

    @Test
    void testUploadMediaPermanentPath() {
        mockJsonResponse(200, "{\"media_id\":\"MEDIA_P\",\"created_at\":1700000000}");
        try {
            java.io.File tmp = java.io.File.createTempFile("wechat_test", ".png");
            tmp.deleteOnExit();
            service.uploadMedia("default", tmp.getAbsolutePath(), "image", true);
            Request req = firstRequestSent();
            assertEquals("/cgi-bin/material/add_material", req.url().encodedPath(),
                    "永久素材应走 material/add_material");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testUploadMediaTemporaryPath() {
        mockJsonResponse(200, "{\"media_id\":\"MEDIA_T\",\"created_at\":1700000000}");
        try {
            java.io.File tmp = java.io.File.createTempFile("wechat_test2", ".png");
            tmp.deleteOnExit();
            service.uploadMedia("default", tmp.getAbsolutePath(), "image", false);
            Request req = firstRequestSent();
            assertEquals("/cgi-bin/media/upload", req.url().encodedPath(),
                    "临时素材应走 media/upload（24h 时效）");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testDeleteMedia() {
        mockJsonResponse(200, "{\"errcode\":0,\"errmsg\":\"ok\"}");
        service.deleteMedia("default", "MEDIA_X");
        Request req = firstRequestSent();
        assertEquals("/cgi-bin/material/del_material", req.url().encodedPath());
    }

    @Test
    void testUnconfiguredMiniAppRejected() {
        props.getMiniApps().clear();
        assertThrows(IllegalStateException.class,
                () -> service.code2Session("missing_app", "code"),
                "未配置小程序时调用必须失败（而非静默 NPE）");
        assertThrows(IllegalStateException.class,
                () -> service.code2Session("default", "code"),
                "default 也未配置时应报未找到配置");
    }
}
