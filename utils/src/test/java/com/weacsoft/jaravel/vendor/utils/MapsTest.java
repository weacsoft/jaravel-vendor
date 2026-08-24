package com.weacsoft.jaravel.vendor.utils;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Maps} 不可变 Map 构建器测试。
 * 重点验证：相比 {@code Map.of}，本工具允许空键/空值而不抛异常，且空键跳过、空值保留。
 */
class MapsTest {

    @Test
    void testNormalPairs() {
        Map<String, Object> m = Maps.of("a", 1, "b", "x");
        assertEquals(2, m.size());
        assertEquals(1, m.get("a"));
        assertEquals("x", m.get("b"));
    }

    @Test
    void testNullKeySkipped() {
        Map<String, Object> m = Maps.of("a", 1, null, "ignored");
        assertEquals(1, m.size());
        assertNull(m.get("ignored"));
    }

    @Test
    void testEmptyKeySkipped() {
        Map<String, Object> m = Maps.of("", "ignored", "b", 2);
        assertEquals(1, m.size());
        assertEquals(2, m.get("b"));
    }

    @Test
    void testNullAndEmptyValueKept() {
        Map<String, Object> m = Maps.of("x", null, "y", "");
        assertEquals(2, m.size());
        assertNull(m.get("x"));
        assertEquals("", m.get("y"));
    }

    @Test
    void testPreservesInsertionOrder() {
        Map<String, Object> m = Maps.of("z", 1, "a", 2, "m", 3);
        assertTrue(m.keySet().toString().equals("[z, a, m]"));
    }

    @Test
    void testImmutable() {
        Map<String, Object> m = Maps.of("k", "v");
        assertThrows(UnsupportedOperationException.class, () -> m.put("z", 1));
        assertThrows(UnsupportedOperationException.class, () -> m.remove("k"));
    }

    @Test
    void testNullVarargs() {
        Map<String, Object> m = Maps.of((Object[]) null);
        assertTrue(m.isEmpty());
    }

    @Test
    void testOddLengthIgnoresTrailingKey() {
        Map<String, Object> m = Maps.of("a", 1, "orphan");
        assertEquals(1, m.size());
        assertEquals(1, m.get("a"));
        assertNull(m.get("orphan"));
    }
}
