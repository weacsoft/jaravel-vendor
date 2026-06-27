package com.weacsoft.jaravel.vendor.http.response;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ResponseBuilder} 响应构建测试。
 * <p>
 * 覆盖 ok / json / content / error / redirect /
 * unauthorized / forbidden / toJson 等工厂方法。
 */
class ResponseBuilderTest {

    @Test
    void testOk() {
        Response r = ResponseBuilder.ok();
        assertEquals(200, r.getStatus());
        assertEquals("ok", r.getContent());
    }

    @Test
    void testJson() {
        Response r = ResponseBuilder.json(Map.of("name", "jaravel", "version", 1));

        assertEquals(200, r.getStatus());
        assertEquals("application/json; charset=utf-8", firstHeader(r, "Content-Type"));
        // 内容应为合法 JSON 且包含字段
        String content = r.getContent();
        assertTrue(content.contains("\"name\":\"jaravel\""));
        assertTrue(content.contains("\"version\":1"));
    }

    @Test
    void testJsonWithList() {
        Response r = ResponseBuilder.json(List.of("a", "b", "c"));
        assertEquals(200, r.getStatus());
        assertEquals("[\"a\",\"b\",\"c\"]", r.getContent());
    }

    @Test
    void testContent() {
        Response r = ResponseBuilder.content("plain text body");
        assertEquals(200, r.getStatus());
        assertEquals("plain text body", r.getContent());
        assertEquals("text/plain; charset=utf-8", firstHeader(r, "Content-Type"));
    }

    @Test
    void testError() {
        Response r = ResponseBuilder.error(404, "resource not found");
        assertEquals(404, r.getStatus());
        assertEquals("application/json; charset=utf-8", firstHeader(r, "Content-Type"));
        assertTrue(r.getContent().contains("resource not found"));
        assertTrue(r.getContent().contains("\"message\""));
    }

    @Test
    void testRedirect() {
        Response r = ResponseBuilder.redirect("https://example.com/home");
        assertEquals(302, r.getStatus());
        assertEquals("https://example.com/home", firstHeader(r, "Location"));
        assertEquals("", r.getContent());
    }

    @Test
    void testUnauthorized() {
        Response r = ResponseBuilder.unauthorized("token expired");
        assertEquals(401, r.getStatus());
        assertTrue(r.getContent().contains("token expired"));
    }

    @Test
    void testForbidden() {
        Response r = ResponseBuilder.forbidden("access denied");
        assertEquals(403, r.getStatus());
        assertTrue(r.getContent().contains("access denied"));
    }

    @Test
    void testToJson() {
        String json = ResponseBuilder.toJson(Map.of("a", 1));
        assertEquals("{\"a\":1}", json);
    }

    @Test
    void testAddHeaderSupportsMultipleValues() {
        // 验证 headers 以 List 形式支持多值
        Response r = ResponseBuilder.redirect("/next");
        r.addHeader("Location", "/fallback");

        List<String> locations = r.getHeaders().get("Location");
        assertEquals(2, locations.size(), "同一 header 应支持多个值");
    }

    /** 取响应头中某名称的第一个值 */
    private static String firstHeader(Response response, String name) {
        List<String> values = response.getHeaders().get(name);
        assertNotNull(values, "header " + name + " 不应缺失");
        assertFalse(values.isEmpty());
        return values.get(0);
    }
}
