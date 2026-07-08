package com.weacsoft.jaravel.vendor.wire;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link WireManager} 快照编解码与 HTML 注入测试。
 * <p>
 * 渲染相关方法依赖 BladeEngine，不在此测试。
 */
class WireManagerTest {

    @Test
    void testEncodeSnapshot() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", 5);
        data.put("name", "test");

        String snapshot = WireManager.encodeSnapshot(data);

        assertNotNull(snapshot);
        assertFalse(snapshot.isEmpty());
        // 应为合法 Base64
        assertDoesNotThrow(() -> java.util.Base64.getDecoder().decode(snapshot));
    }

    @Test
    void testEncodeSnapshotFiltersWireInternalKeys() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", 5);
        data.put("__wire_mode", true);
        data.put("__wire_update_url", "/api/wire/test");

        String snapshot = WireManager.encodeSnapshot(data);

        // __wire 开头的键应被过滤
        assertFalse(snapshot.contains("__wire"));
        // 正常键应保留
        Map<String, Object> decoded = WireManager.decodeSnapshot(snapshot);
        assertEquals(5, decoded.get("count"));
        assertFalse(decoded.containsKey("__wire_mode"));
        assertFalse(decoded.containsKey("__wire_update_url"));
    }

    @Test
    void testEncodeEmptyData() {
        String snapshot = WireManager.encodeSnapshot(new LinkedHashMap<>());
        assertNotNull(snapshot);
        Map<String, Object> decoded = WireManager.decodeSnapshot(snapshot);
        assertTrue(decoded.isEmpty());
    }

    @Test
    void testEncodeNullData() {
        String snapshot = WireManager.encodeSnapshot(null);
        assertNotNull(snapshot);
        Map<String, Object> decoded = WireManager.decodeSnapshot(snapshot);
        assertTrue(decoded.isEmpty());
    }

    @Test
    void testDecodeSnapshot() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("count", 42);
        original.put("message", "hello");
        original.put("items", java.util.Arrays.asList("a", "b", "c"));

        String snapshot = WireManager.encodeSnapshot(original);
        Map<String, Object> decoded = WireManager.decodeSnapshot(snapshot);

        assertEquals(42, decoded.get("count"));
        assertEquals("hello", decoded.get("message"));
        assertNotNull(decoded.get("items"));
    }

    @Test
    void testDecodeEmptySnapshot() {
        Map<String, Object> decoded = WireManager.decodeSnapshot("");
        assertNotNull(decoded);
        assertTrue(decoded.isEmpty());
    }

    @Test
    void testDecodeNullSnapshot() {
        Map<String, Object> decoded = WireManager.decodeSnapshot(null);
        assertNotNull(decoded);
        assertTrue(decoded.isEmpty());
    }

    @Test
    void testDecodeInvalidSnapshotThrows() {
        assertThrows(RuntimeException.class, () -> WireManager.decodeSnapshot("!!!invalid-base64!!!"));
    }

    @Test
    void testEncodeDecodeRoundTrip() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("count", 100);
        original.put("name", "jaravel");
        original.put("active", true);
        original.put("price", 99.5);

        String snapshot = WireManager.encodeSnapshot(original);
        Map<String, Object> decoded = WireManager.decodeSnapshot(snapshot);

        assertEquals(100, decoded.get("count"));
        assertEquals("jaravel", decoded.get("name"));
        assertEquals(true, decoded.get("active"));
        assertEquals(99.5, decoded.get("price"));
    }

    // ===== HTML 注入 =====

    @Test
    void testInjectWireAssetsBeforeBodyClose() {
        String html = "<html><body><h1>Hello</h1></body></html>";
        String result = WireManager.injectWireAssets(html, "/api/wire/test", "base64snapshot");

        // wire:config 应在 </body> 之前
        int configIndex = result.indexOf("wire:config");
        int bodyCloseIndex = result.toLowerCase().indexOf("</body>");
        assertTrue(configIndex >= 0 && configIndex < bodyCloseIndex,
                "wire:config 应注入到 </body> 之前");
        // wire.js 引入
        assertTrue(result.contains("/static/wire.js"));
        // updateUrl
        assertTrue(result.contains("data-wire-update=\"/api/wire/test\""));
        // snapshot
        assertTrue(result.contains("wire:snapshot=\"base64snapshot\""));
    }

    @Test
    void testInjectWireAssetsNoBodyTag() {
        String html = "<h1>No body tag</h1>";
        String result = WireManager.injectWireAssets(html, "/api/wire/update", "snap");

        // 没有 </body> 时追加到末尾
        assertTrue(result.contains("wire:config"));
        assertTrue(result.endsWith("<script src=\"/static/wire.js\"></script>") || result.trim().endsWith("<script src=\"/static/wire.js\"></script>"));
    }

    @Test
    void testInjectWireAssetsEscapesHtml() {
        String html = "<html><body></body></html>";
        // updateUrl 含特殊字符，应被转义
        String result = WireManager.injectWireAssets(html, "/api?x=1&y=2", "snap");

        assertFalse(result.contains("/api?x=1&y=2"), "原始 & 不应出现");
        assertTrue(result.contains("&amp;"), "应转义为 &amp;");
    }
}
