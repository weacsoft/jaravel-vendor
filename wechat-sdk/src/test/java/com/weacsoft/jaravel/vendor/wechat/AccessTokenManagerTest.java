package com.weacsoft.jaravel.vendor.wechat;

import com.weacsoft.jaravel.vendor.cache.driver.ArrayCacheDriver;
import com.weacsoft.jaravel.vendor.cache.CacheManager;
import com.weacsoft.jaravel.vendor.cache.CacheStore;
import com.weacsoft.jaravel.vendor.cache.store.DefaultCacheStore;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AccessTokenManager 缓存与 HTTP 请求逻辑测试。
 * 使用 Mockito mock OkHttpClient 模拟微信 API 响应，不测试实际网络连接。
 * 使用 Mockito spy 包装 DefaultCacheStore 以验证 TTL 计算逻辑。
 */
class AccessTokenManagerTest {

    private OkHttpClient mockHttpClient;
    private CacheManager cacheManager;
    private CacheStore spyStore;
    private AccessTokenManager manager;

    @BeforeEach
    void setUp() {
        mockHttpClient = mock(OkHttpClient.class);
        cacheManager = new CacheManager();
        spyStore = spy(new DefaultCacheStore(new ArrayCacheDriver(), ""));
        cacheManager.addStore("array", spyStore);
        manager = new AccessTokenManager(mockHttpClient, cacheManager, "array");
    }

    @Test
    void testGetTokenFromCacheNoHttpCall() {
        // 预填充缓存
        spyStore.put("wechat:access_token:wx123", "cached_token", 3600);
        String token = manager.getToken("wx123", "secret");
        assertEquals("cached_token", token, "缓存命中时应直接返回缓存 token");
        verify(mockHttpClient, never()).newCall(any(Request.class));
    }

    @Test
    void testRefreshTokenFetchesFromWechatApi() {
        mockWechatResponse(200, "{\"access_token\":\"new_token_123\",\"expires_in\":7200}");
        String token = manager.getToken("wx123", "secret");
        assertEquals("new_token_123", token, "缓存未命中时应从微信 API 获取 token");
        // 验证 token 已被缓存
        assertEquals("new_token_123", spyStore.get("wechat:access_token:wx123", String.class),
                "获取的 token 应被缓存");
    }

    @Test
    void testTtlCalculationExpiresIn7200() {
        mockWechatResponse(200, "{\"access_token\":\"token\",\"expires_in\":7200}");
        manager.getToken("wx123", "secret");
        // TTL = 7200 - 300 = 6900
        verify(spyStore).put(eq("wechat:access_token:wx123"), eq("token"), eq(6900L));
    }

    @Test
    void testTtlMinGuardWhenExpiresInTooSmall() {
        mockWechatResponse(200, "{\"access_token\":\"token\",\"expires_in\":100}");
        manager.getToken("wx123", "secret");
        // TTL = max(100 - 300, 60) = 60
        verify(spyStore).put(eq("wechat:access_token:wx123"), eq("token"), eq(60L));
    }

    @Test
    void testInvalidateTokenClearsCache() {
        spyStore.put("wechat:access_token:wx123", "cached_token", 3600);
        manager.invalidateToken("wx123");
        assertNull(spyStore.get("wechat:access_token:wx123", String.class),
                "invalidateToken 后缓存应被清除");
    }

    @Test
    void testApiErrorThrowsException() {
        mockWechatResponse(500, "{}");
        assertThrows(RuntimeException.class, () -> manager.getToken("wx123", "secret"),
                "微信 API 返回错误码时应抛出 RuntimeException");
    }

    @Test
    void testEmptyAccessTokenThrowsException() {
        mockWechatResponse(200, "{\"access_token\":\"\",\"expires_in\":7200}");
        assertThrows(RuntimeException.class, () -> manager.getToken("wx123", "secret"),
                "access_token 为空时应抛出 RuntimeException");
    }

    @Test
    void testNullCacheManagerFallsBackToMemoryStore() {
        manager = new AccessTokenManager(mockHttpClient, null, "");
        mockWechatResponse(200, "{\"access_token\":\"fallback_token\",\"expires_in\":7200}");
        String token = manager.getToken("wx123", "secret");
        assertEquals("fallback_token", token, "CacheManager 为 null 时应回退到内存存储");
    }

    @Test
    void testEmptyStoreUsesDefaultStore() {
        // preferredStore 为空时，应使用 cache 模块的默认 store（此处为 "array"）
        manager = new AccessTokenManager(mockHttpClient, cacheManager, "");
        mockWechatResponse(200, "{\"access_token\":\"default_token\",\"expires_in\":7200}");
        String token = manager.getToken("wx123", "secret");
        assertEquals("default_token", token, "preferredStore 为空时应使用默认 store");
        assertEquals("default_token", spyStore.get("wechat:access_token:wx123", String.class));
    }

    @Test
    void testPreferredStoreNotRegisteredFallsBackToDefault() {
        // preferredStore 为 "redis" 但未注册，应回退到默认 store（此处为 "array"）
        manager = new AccessTokenManager(mockHttpClient, cacheManager, "redis");
        mockWechatResponse(200, "{\"access_token\":\"fb_token\",\"expires_in\":7200}");
        String token = manager.getToken("wx123", "secret");
        assertEquals("fb_token", token, "preferredStore 未注册时应回退到默认 store");
        // 验证 token 被缓存在默认 store（array）中
        assertEquals("fb_token", spyStore.get("wechat:access_token:wx123", String.class));
    }

    /**
     * 模拟微信 API HTTP 响应。
     */
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
}
