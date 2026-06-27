package com.weacsoft.jaravel.vendor.core.support;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Arr} 数组/集合工具单元测试。
 */
class ArrTest {

    @Test
    void getSupportsDotNotation() {
        Map<String, Object> map = new LinkedHashMap<>();
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("name", "Alice");
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("profile", profile);
        map.put("user", user);

        assertEquals("Alice", Arr.get(map, "user.profile.name"));
        // 完整键命中
        assertEquals(user, Arr.get(map, "user"));
        // 中间层级也可命中
        assertEquals(profile, Arr.get(map, "user.profile"));
    }

    @Test
    void getReturnsDefaultWhenMissing() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("a", "b");

        assertEquals("fallback", Arr.get(map, "not.exist", "fallback"));
        assertNull(Arr.get(map, "not.exist"));
        // null map / null key 安全
        assertEquals("d", Arr.get(null, "x", "d"));
        assertEquals("d", Arr.get(map, null, "d"));
    }

    @Test
    void setCreatesNestedMaps() {
        Map<String, Object> map = new LinkedHashMap<>();
        Arr.set(map, "user.profile.name", "Bob");

        assertEquals("Bob", Arr.get(map, "user.profile.name"));
        assertTrue(Arr.has(map, "user.profile"));
        // 简单键
        Arr.set(map, "top", 1);
        assertEquals(Integer.valueOf(1), (Integer) Arr.get(map, "top"));
    }

    @Test
    void hasTraversesDotPath() {
        Map<String, Object> map = new LinkedHashMap<>();
        Arr.set(map, "a.b.c", 1);

        assertTrue(Arr.has(map, "a.b.c"));
        assertFalse(Arr.has(map, "a.b.d"));
        assertFalse(Arr.has(null, "x"));
    }

    @Test
    void onlyAndExceptFilterKeys() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", 1);
        map.put("name", "n");
        map.put("age", 9);

        assertEquals(Map.of("id", 1, "name", "n"), Arr.only(map, "id", "name"));
        assertEquals(Map.of("age", 9), Arr.except(map, "id", "name"));
    }

    @Test
    void pluckAndMapTransformCollections() {
        Map<String, Object> r1 = new LinkedHashMap<>();
        r1.put("k", "v1");
        Map<String, Object> r2 = new LinkedHashMap<>();
        r2.put("k", "v2");

        List<String> plucked = Arr.pluck(List.of(r1, r2), "k");
        assertEquals(List.of("v1", "v2"), plucked);

        List<Integer> mapped = Arr.map(List.of("a", "bb"), String::length);
        assertEquals(List.of(1, 2), mapped);
    }
}
