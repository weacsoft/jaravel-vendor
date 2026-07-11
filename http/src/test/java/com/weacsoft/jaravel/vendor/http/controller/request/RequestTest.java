package com.weacsoft.jaravel.vendor.http.controller.request;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Request} 参数获取测试。
 * <p>
 * 覆盖 input / query / header（大小写不敏感）/ cookie / session /
 * has / all / get / routeParams / attributes 等核心访问器。
 */
class RequestTest {

    @Test
    void testInput() {
        Request req = new Request();
        req.addInput("name", "jaravel");

        assertEquals("jaravel", req.input("name"));
        // get(key, default) 同时检索 input 与 query
        assertEquals("jaravel", req.get("name", "fallback"));
        assertTrue(req.has("name"));

        // 不存在的键返回默认值
        assertEquals("default", req.input("missing", "default"));
        assertEquals("", req.input("missing"), "无默认值时返回空字符串");
    }

    @Test
    void testQuery() {
        Request req = new Request();
        req.addQuery("page", 1);

        assertEquals("1", req.query("page"));
        assertEquals("1", req.get("page", "fallback"));
        assertTrue(req.has("page"));
        assertEquals(1, req.queryNames().size());
    }

    @Test
    void testGetPrefersInputOverQuery() {
        Request req = new Request();
        req.addInput("k", "from-input");
        req.addQuery("k", "from-query");
        // input 优先（get(key, default) 先查 input）
        assertEquals("from-input", req.get("k", "fallback"));
    }

    @Test
    void testHeaderCaseInsensitive() {
        Request req = new Request();
        req.addHeader("Content-Type", "application/json");

        // header 内部使用大小写不敏感的 TreeMap
        assertEquals("application/json", req.header("content-type"));
        assertEquals("application/json", req.header("CONTENT-TYPE"));
        assertTrue(req.hasHeader("CONTENT-TYPE"));
    }

    @Test
    void testCookie() {
        Request req = new Request();
        req.addCookie("token", "abc123");

        assertEquals("abc123", req.cookie("token"));
        assertTrue(req.hasCookie("token"));
        assertFalse(req.hasCookie("missing"));
        assertEquals("fallback", req.cookie("missing", "fallback"));
    }

    @Test
    void testSession() {
        Request req = new Request();
        req.addSession("user_id", 42L);

        assertEquals("42", req.session("user_id"));
        assertTrue(req.hasSession("user_id"));
    }

    @Test
    void testHasChecksQueryAndInput() {
        Request req = new Request();
        req.addInput("a", 1);
        req.addQuery("b", 2);

        assertTrue(req.has("a"));
        assertTrue(req.has("b"));
        assertFalse(req.has("c"));
    }

    @Test
    void testAllMergesQueryAndInput() {
        Request req = new Request();
        req.addInput("name", "jaravel");
        req.addQuery("page", 1);

        Map<String, Object> all = req.all();
        assertEquals("jaravel", all.get("name"));
        assertEquals(1, all.get("page"));
        assertEquals(2, all.size());
    }

    @Test
    void testMultipleValuesForSameInputKey() {
        Request req = new Request();
        req.addInput("tags", "java");
        req.addInput("tags", "spring");
        req.addInput("tags", "boot");

        List<Object> values = req.inputs("tags");
        assertEquals(3, values.size());
        assertEquals("java", values.get(0));
        assertEquals("boot", values.get(2));
    }

    @Test
    void testRouteParams() {
        Request req = new Request();
        req.setRouteParams(Map.of("id", 123, "slug", "hello"));

        assertEquals("123", req.routeParam("id"));
        assertEquals(Integer.valueOf(123), req.routeParam("id", Integer.class));
        assertEquals("hello", req.routeParam("slug"));
        assertTrue(req.hasRouteParam("id"));
        assertFalse(req.hasRouteParam("missing"));
    }

    @Test
    void testAttributes() {
        Request req = new Request();
        req.setAttribute("user", "alice");

        assertEquals("alice", req.getAttribute("user"));
        assertTrue(req.hasAttribute("user"));
        assertEquals("alice", req.getAttribute("user", String.class));

        req.removeAttribute("user");
        assertFalse(req.hasAttribute("user"));
    }

    @Test
    void testGetNames() {
        Request req = new Request();
        req.addInput("a", 1);
        req.addQuery("b", 2);

        assertTrue(req.getNames().contains("a"));
        assertTrue(req.getNames().contains("b"));
        assertEquals(2, req.getNames().size());
    }
}
