package com.weacsoft.jaravel.vendor.http.controller.response;

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

    // ===== html =====

    @Test
    void testHtml() {
        Response r = ResponseBuilder.html("<h1>Hello</h1>");

        assertEquals(200, r.getStatus());
        assertEquals("<h1>Hello</h1>", r.getContent());
        assertEquals("text/html; charset=utf-8", firstHeader(r, "Content-Type"));
    }

    @Test
    void testHtmlWithEmptyString() {
        Response r = ResponseBuilder.html("");

        assertEquals(200, r.getStatus());
        assertEquals("", r.getContent());
        assertEquals("text/html; charset=utf-8", firstHeader(r, "Content-Type"));
    }

    // ===== raw =====

    @Test
    void testRawDefaultStatus() {
        Response r = ResponseBuilder.raw()
                .body("hello");

        assertEquals(200, r.getStatus());
        assertEquals("hello", r.getContent());
    }

    @Test
    void testRawCustomStatus() {
        Response r = ResponseBuilder.raw()
                .status(201)
                .body("created");

        assertEquals(201, r.getStatus());
        assertEquals("created", r.getContent());
    }

    @Test
    void testRawCustomContentType() {
        Response r = ResponseBuilder.raw()
                .contentType("application/xml;charset=utf-8")
                .body("<xml>test</xml>");

        assertEquals("application/xml;charset=utf-8", firstHeader(r, "Content-Type"));
        assertEquals("<xml>test</xml>", r.getContent());
    }

    @Test
    void testRawCustomHeader() {
        Response r = ResponseBuilder.raw()
                .header("X-Custom", "hello")
                .body("test");

        assertEquals("hello", firstHeader(r, "X-Custom"));
    }

    @Test
    void testRawMultipleHeaders() {
        Response r = ResponseBuilder.raw()
                .header("X-Custom", "value1")
                .header("X-Custom", "value2")
                .body("test");

        List<String> values = r.getHeaders().get("X-Custom");
        assertEquals(2, values.size());
        assertEquals("value1", values.get(0));
        assertEquals("value2", values.get(1));
    }

    @Test
    void testRawDefaultContentTypeWhenNotSet() {
        Response r = ResponseBuilder.raw()
                .body("plain text");

        // 未设 Content-Type 时，getContentType() 返回默认值 text/plain;charset=utf-8
        assertEquals("text/plain;charset=utf-8", r.getContentType());
    }

    @Test
    void testRawBinaryBody() {
        byte[] data = {0x01, 0x02, 0x03};
        Response r = ResponseBuilder.raw()
                .contentType("application/octet-stream")
                .body(data);

        assertEquals(200, r.getStatus());
        assertArrayEquals(data, r.getBytes());
        assertNull(r.getContent());
    }

    @Test
    void testRawCookie() {
        Response r = ResponseBuilder.raw()
                .cookie("session", "abc123")
                .body("test");

        assertNotNull(r.getCookies());
        assertEquals(1, r.getCookies().length);
        assertEquals("session", r.getCookies()[0].getName());
        assertEquals("abc123", r.getCookies()[0].getValue());
    }

    @Test
    void testRawChainAllMethods() {
        Response r = ResponseBuilder.raw()
                .status(202)
                .contentType("text/csv;charset=utf-8")
                .header("X-Request-Id", "req-001")
                .cookie("token", "xyz")
                .body("a,b,c\n1,2,3");

        assertEquals(202, r.getStatus());
        assertEquals("text/csv;charset=utf-8", firstHeader(r, "Content-Type"));
        assertEquals("req-001", firstHeader(r, "X-Request-Id"));
        assertEquals("a,b,c\n1,2,3", r.getContent());
        assertEquals(1, r.getCookies().length);
    }

    // ===== error with redirect =====

    @Test
    void testErrorWithRedirect() {
        Response r = ResponseBuilder.error(401, "Unauthorized", "/login");

        assertEquals(401, r.getStatus());
        assertTrue(r.getContent().contains("Unauthorized"));
        assertTrue(r.getContent().contains("\"redirect\""));
        assertTrue(r.getContent().contains("/login"));
    }

    @Test
    void testErrorWithRedirect403() {
        Response r = ResponseBuilder.error(403, "Forbidden", "/login");

        assertEquals(403, r.getStatus());
        assertTrue(r.getContent().contains("/login"));
    }

    // ===== getContentType 默认值 =====

    @Test
    void testGetContentTypeDefaultWhenNotSet() {
        Response r = ResponseBuilder.content("hello");

        // content() 设置 text/plain，getContentType 返回它
        assertEquals("text/plain; charset=utf-8", r.getContentType());
    }

    /** 取响应头中某名称的第一个值 */
    private static String firstHeader(Response response, String name) {
        List<String> values = response.getHeaders().get(name);
        assertNotNull(values, "header " + name + " 不应缺失");
        assertFalse(values.isEmpty());
        return values.get(0);
    }
}
