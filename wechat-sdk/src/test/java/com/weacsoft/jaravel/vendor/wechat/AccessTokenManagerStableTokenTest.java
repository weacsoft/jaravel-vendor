package com.weacsoft.jaravel.vendor.wechat;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AccessTokenManager stable_token 模式测试（POST /cgi-bin/stable_token）。
 */
class AccessTokenManagerStableTokenTest {

    private OkHttpClient mockHttpClient;

    @BeforeEach
    void setUp() {
        mockHttpClient = mock(OkHttpClient.class);
    }

    private void mockWechatResponse(int code, String jsonBody) {
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

    private Request firstRequestSent() {
        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(mockHttpClient, atLeastOnce()).newCall(captor.capture());
        return captor.getAllValues().get(0);
    }

    @Test
    void testStableModePostsToStableTokenEndpoint() {
        mockWechatResponse(200, "{\"access_token\":\"stable_tok\",\"expires_in\":7200}");
        AccessTokenManager manager = new AccessTokenManager(mockHttpClient, null, "", "stable");

        String token = manager.getToken("wx123", "secret");
        assertEquals("stable_tok", token);

        Request captured = firstRequestSent();
        assertEquals("POST", captured.method(), "stable 模式必须使用 POST 方法");
        assertEquals("/cgi-bin/stable_token", captured.url().encodedPath(),
                "stable 模式端点必须为 /cgi-bin/stable_token");
        String bodyStr;
        try {
            okio.Buffer buffer = new okio.Buffer();
            captured.body().writeTo(buffer);
            bodyStr = buffer.readUtf8();
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        assertTrue(bodyStr.contains("client_credential"), "请求体应含 grant_type=client_credential");
        assertTrue(bodyStr.contains("wx123"), "请求体应含 appid");
        assertTrue(bodyStr.contains("secret"), "请求体应含 appsecret");
    }

    @Test
    void testLegacyModeStillUsesGetTokenEndpoint() {
        mockWechatResponse(200, "{\"access_token\":\"legacy_tok\",\"expires_in\":7200}");
        AccessTokenManager manager = new AccessTokenManager(mockHttpClient, null, "", "legacy");
        String token = manager.getToken("wx456", "s456");
        assertEquals("legacy_tok", token);

        Request captured = firstRequestSent();
        assertEquals("GET", captured.method(), "legacy 模式保持 GET /cgi-bin/token");
        assertEquals("/cgi-bin/token", captured.url().encodedPath());
    }

    @Test
    void testUnknownTokenModeFallsBackToLegacy() {
        mockWechatResponse(200, "{\"access_token\":\"x\",\"expires_in\":7200}");
        // 拼写错误/未知值：不得静默走 stable，应回退 legacy GET
        AccessTokenManager manager = new AccessTokenManager(mockHttpClient, null, "", "LEGACY");
        assertNotNull(manager.getToken("wx789", "s"));

        Request captured = firstRequestSent();
        assertEquals("GET", captured.method(), "legacy（大小写不敏感）应走 GET");
    }
}
