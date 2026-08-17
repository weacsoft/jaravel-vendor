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
        assertEquals(null, req.input("missing"), "单参数重载无默认值时返回 null(而非空串)");
    }

    @Test
    void testInputNullValueReturnsDefault() {
        Request req = new Request();
        req.replaceInput("key", null);
        // 之前这里会 NPE：input.containsKey(key)=true 但 get(key)=null → null.toString()
        assertEquals("fallback", req.input("key", "fallback"));
        assertEquals(null, req.input("key"), "缺省默认值(单参数)应为 null,而非空串");
        // null 默认值不 NPE,返回 null
        assertEquals(null, req.input("missing", (String) null));
        assertEquals(null, req.query("missing", (Long) null), "泛型重载 null 默认值不 NPE");
    }

    @Test
    void testQuery() {
        Request req = new Request();
        req.addQuery("page", 1);

        assertEquals("1", req.query("page"));
        assertEquals("1", req.get("page", "fallback"));
        assertTrue(req.has("page"));
        assertEquals(1, req.queryNames().size());
        // 单参数/泛型 null 默认值:缺省返回 null 而非空串
        assertEquals(null, req.query("missing"));
        assertEquals("fallback", req.query("missing", "fallback"));
        assertEquals(null, req.query("missing", (Long) null), "泛型重载 null 默认值不 NPE");
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

    @Test
    void testGetTypedConversion() {
        // 回归:get(key, 非 String 默认值)必须能拿到真实值(类型转换),不能恒返回默认值。
        // 旧实现用 clazz.isInstance(value) 判断,查询参数是 String/Integer 时对 Long 恒不匹配 → 返回 null → 默认值。
        Request req = new Request();
        req.addQuery("page", "2");      // URL 查询参数:字符串
        req.addInput("size", 10);       // 表单值:Integer
        req.addQuery("ratio", "1.5");

        assertEquals(Long.valueOf(2L), req.get("page", 1L), "String 查询参数按 Long 默认值请求应转换");
        assertEquals(Long.valueOf(2L), req.get("page", Long.class));
        assertEquals(Integer.valueOf(2), req.get("page", Integer.class));
        assertEquals(Integer.valueOf(10), req.get("size", 1));
        assertEquals(11L, req.get("missing", 11L), "缺省键返回默认值");
        assertEquals(Double.valueOf(1.5), req.get("ratio", 0.0d));
        assertEquals("2", req.get("page", "default"), "String 默认值行为不变");
        // 转换失败(非数字)→ null → 默认值
        req.addQuery("bad", "abc");
        assertEquals(7L, req.get("bad", 7L));
    }
}
