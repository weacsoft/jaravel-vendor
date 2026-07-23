package com.weacsoft.jaravel.vendor.wire;

import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import com.weacsoft.jaravel.vendor.json.Json;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link WireResponse} 语义化响应测试。
 * <p>
 * 覆盖 redirect / error / of() / withRedirect / withDispatch / withError / build。
 * wire/update 依赖 BladeEngine，不在此测试。
 */
class WireResponseTest {

    // ObjectMapper replaced by Json SPI (auto-detects Jackson 2/3)

    // ===== redirect =====

    @Test
    void testRedirectImmediate() throws Exception {
        Response r = WireResponse.redirect("/dashboard");

        assertEquals(200, r.getStatus());
        Map<String, Object> json = Json.parseToMap(r.getContent());
        Map<String, Object> effects = (Map<String, Object>) json.get("effects");
        Map<String, Object> redirect = (Map<String, Object>) effects.get("redirect");

        assertEquals("/dashboard", redirect.get("url"));
        assertEquals(0, redirect.get("delay"));
    }

    @Test
    void testRedirectWithDelay() throws Exception {
        Response r = WireResponse.redirect("/items/42", 1500);

        assertEquals(200, r.getStatus());
        Map<String, Object> json = Json.parseToMap(r.getContent());
        Map<String, Object> effects = (Map<String, Object>) json.get("effects");
        Map<String, Object> redirect = (Map<String, Object>) effects.get("redirect");

        assertEquals("/items/42", redirect.get("url"));
        assertEquals(1500, redirect.get("delay"));
    }

    @Test
    void testRedirectHasEmptySectionsAndSnapshot() throws Exception {
        Response r = WireResponse.redirect("/login");

        Map<String, Object> json = Json.parseToMap(r.getContent());
        assertTrue(((Map<?, ?>) json.get("sections")).isEmpty());
        assertEquals("", json.get("snapshot"));
    }

    // ===== error =====

    @Test
    void testErrorWithoutRedirect() {
        Response r = WireResponse.error(403, "Forbidden");

        assertEquals(403, r.getStatus());
        assertTrue(r.getContent().contains("Forbidden"));
        assertTrue(r.getContent().contains("\"message\""));
    }

    @Test
    void testErrorWithRedirect() {
        Response r = WireResponse.error(401, "Unauthorized", "/login");

        assertEquals(401, r.getStatus());
        assertTrue(r.getContent().contains("Unauthorized"));
        assertTrue(r.getContent().contains("\"redirect\""));
        assertTrue(r.getContent().contains("/login"));
    }

    @Test
    void testError500() {
        Response r = WireResponse.error(500, "Internal Server Error");

        assertEquals(500, r.getStatus());
        assertTrue(r.getContent().contains("Internal Server Error"));
    }

    // ===== of() 空构建器 + 链式方法 =====

    @Test
    void testOfEmptyBuilder() {
        WireResponse resp = WireResponse.of();
        assertNotNull(resp);
        assertTrue(resp.getSections().isEmpty());
        assertTrue(resp.getEffects().isEmpty());
    }

    @Test
    void testWithRedirectImmediate() {
        WireResponse resp = WireResponse.of()
                .withRedirect("/dashboard");

        Map<String, Object> redirect = (Map<String, Object>) resp.getEffects().get("redirect");
        assertNotNull(redirect);
        assertEquals("/dashboard", redirect.get("url"));
        assertEquals(0, redirect.get("delay"));
    }

    @Test
    void testWithRedirectWithDelay() {
        WireResponse resp = WireResponse.of()
                .withRedirect("/items/42", 2000);

        Map<String, Object> redirect = (Map<String, Object>) resp.getEffects().get("redirect");
        assertEquals("/items/42", redirect.get("url"));
        assertEquals(2000, redirect.get("delay"));
    }

    @Test
    void testWithDispatchSingleEvent() {
        WireResponse resp = WireResponse.of()
                .withDispatch("item-updated", Map.of("id", 42));

        List<Map<String, Object>> dispatchList = (List<Map<String, Object>>) resp.getEffects().get("dispatch");
        assertNotNull(dispatchList);
        assertEquals(1, dispatchList.size());
        assertEquals("item-updated", dispatchList.get(0).get("name"));
        assertEquals(Map.of("id", 42), dispatchList.get(0).get("data"));
    }

    @Test
    void testWithDispatchMultipleEvents() {
        WireResponse resp = WireResponse.of()
                .withDispatch("event1", Map.of("a", 1))
                .withDispatch("event2", Map.of("b", 2));

        List<Map<String, Object>> dispatchList = (List<Map<String, Object>>) resp.getEffects().get("dispatch");
        assertEquals(2, dispatchList.size());
        assertEquals("event1", dispatchList.get(0).get("name"));
        assertEquals("event2", dispatchList.get(1).get("name"));
    }

    @Test
    void testWithError() {
        WireResponse resp = WireResponse.of()
                .withError(403, "No permission");

        Response r = resp.build();
        assertEquals(403, r.getStatus());
        assertTrue(r.getContent().contains("No permission"));
    }

    // ===== build =====

    @Test
    void testBuildWithoutError() throws Exception {
        WireResponse resp = WireResponse.of()
                .withRedirect("/home");

        Response r = resp.build();
        assertEquals(200, r.getStatus());

        Map<String, Object> json = Json.parseToMap(r.getContent());
        assertNotNull(json.get("sections"));
        assertNotNull(json.get("effects"));
        assertNotNull(json.get("snapshot"));
    }

    @Test
    void testBuildWithErrorOverridesStatus() {
        WireResponse resp = WireResponse.of()
                .withRedirect("/home")
                .withError(500, "Server crash");

        Response r = resp.build();
        assertEquals(500, r.getStatus());
        assertTrue(r.getContent().contains("Server crash"));
    }

    @Test
    void testBuildWithRedirectAndDispatch() throws Exception {
        WireResponse resp = WireResponse.of()
                .withRedirect("/dashboard", 1000)
                .withDispatch("saved", Map.of("id", 99));

        Response r = resp.build();
        assertEquals(200, r.getStatus());

        Map<String, Object> json = Json.parseToMap(r.getContent());
        Map<String, Object> effects = (Map<String, Object>) json.get("effects");
        Map<String, Object> redirect = (Map<String, Object>) effects.get("redirect");
        List<?> dispatch = (List<?>) effects.get("dispatch");

        assertEquals("/dashboard", redirect.get("url"));
        assertEquals(1000, redirect.get("delay"));
        assertEquals(1, dispatch.size());
    }

    // ===== of(sections, data) 向后兼容 =====

    @Test
    void testOfSectionsData() {
        Map<String, String> sections = Map.of("content", "<div>hello</div>");
        Map<String, Object> data = Map.of("count", 10);

        WireResponse resp = WireResponse.of(sections, data);

        assertEquals("<div>hello</div>", resp.getSections().get("content"));
        assertNotNull(resp.getSnapshot());
        assertFalse(resp.getSnapshot().isEmpty());
    }

    @Test
    void testOfSectionsDataWithRedirect() {
        Map<String, String> sections = Map.of("content", "<div>updated</div>");
        Map<String, Object> data = Map.of("count", 5);

        WireResponse resp = WireResponse.of(sections, data, "/dashboard");

        // 旧格式 of(sections, data, redirectUrl) 中 redirect 存为 String
        Object redirect = resp.getEffects().get("redirect");
        assertNotNull(redirect);
        // 兼容 String 和 Map 两种格式
        if (redirect instanceof String) {
            assertEquals("/dashboard", redirect);
        } else if (redirect instanceof Map) {
            Map<?, ?> redirectMap = (Map<?, ?>) redirect;
            assertEquals("/dashboard", redirectMap.get("url"));
        }
    }

    @Test
    void testToMap() {
        WireResponse resp = WireResponse.of();

        Map<String, Object> map = resp.toMap();
        assertNotNull(map.get("sections"));
        assertNotNull(map.get("snapshot"));
        assertNotNull(map.get("effects"));
    }
}
