package com.weacsoft.jaravel.vendor.cache;

import com.weacsoft.jaravel.vendor.cache.driver.ArrayCacheDriver;
import com.weacsoft.jaravel.vendor.cache.store.DefaultCacheStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DefaultCacheStore} 高级缓存仓库测试（底层使用 {@link ArrayCacheDriver}）。
 * <p>
 * 覆盖 put / get / has / forget / flush / increment / decrement / add / pull /
 * putMany / getMany / remember / rememberForever 等核心语义。
 */
class DefaultCacheStoreTest {

    private DefaultCacheStore store;

    @BeforeEach
    void setUp() {
        store = new DefaultCacheStore(new ArrayCacheDriver(), null);
    }

    @Test
    void testPutGetHas() {
        store.put("name", "jaravel");
        assertEquals("jaravel", store.get("name"));
        assertTrue(store.has("name"));
    }

    @Test
    void testGetWithDefaultType() {
        store.put("count", 42L);
        Long val = store.get("count", Long.class);
        assertEquals(42L, val);
    }

    @Test
    void testGetReturnsNullForMissing() {
        assertNull(store.get("nope"));
        assertFalse(store.has("nope"));
    }

    @Test
    void testForgetAndFlush() {
        store.put("a", 1);
        store.put("b", 2);

        assertTrue(store.forget("a"));
        assertFalse(store.has("a"));
        assertTrue(store.has("b"));

        store.flush();
        assertFalse(store.has("b"));
    }

    @Test
    void testIncrementAndDecrement() {
        // 键不存在时按 0 起算
        assertEquals(1L, store.increment("counter"));
        assertEquals(2L, store.increment("counter"));
        assertEquals(7L, store.increment("counter", 5));

        assertEquals(6L, store.decrement("counter"));
        assertEquals(0L, store.decrement("counter", 6));
    }

    @Test
    void testIncrementOnNonNumericResetsToZero() {
        store.put("mixed", "not-a-number");
        assertEquals(1L, store.increment("mixed"));
    }

    @Test
    void testAddOnlyWhenAbsent() {
        assertTrue(store.add("once", "first", 0));
        assertEquals("first", store.get("once"));

        // 已存在则不写入
        assertFalse(store.add("once", "second", 0));
        assertEquals("first", store.get("once"));
    }

    @Test
    void testPullReadsAndDeletes() {
        store.put("temp", "data");
        assertEquals("data", store.pull("temp"));
        assertFalse(store.has("temp"));
        // 再次 pull 返回 null
        assertNull(store.pull("temp"));
    }

    @Test
    void testPutManyAndGetMany() {
        store.putMany(Map.of("x", 1, "y", 2, "z", 3), 0);

        Map<String, Object> many = store.getMany(java.util.List.of("x", "y", "z", "missing"));
        assertEquals(1, many.get("x"));
        assertEquals(2, many.get("y"));
        assertEquals(3, many.get("z"));
        assertNull(many.get("missing"));
    }

    @Test
    void testRememberCachesLoaderResult() {
        int[] calls = {0};
        Object first = store.remember("computed", 60, () -> {
            calls[0]++;
            return "expensive-" + calls[0];
        });
        assertEquals("expensive-1", first);

        // 第二次命中缓存，loader 不应再次调用
        Object second = store.remember("computed", 60, () -> {
            calls[0]++;
            return "expensive-" + calls[0];
        });
        assertEquals("expensive-1", second);
        assertEquals(1, calls[0], "remember 命中缓存时不应调用 loader");
    }

    @Test
    void testRememberForever() {
        int[] calls = {0};
        Object value = store.rememberForever("forever", () -> {
            calls[0]++;
            return "cached-forever";
        });
        assertEquals("cached-forever", value);
        store.rememberForever("forever", () -> {
            calls[0]++;
            return "should-not-call";
        });
        assertEquals(1, calls[0], "rememberForever 命中缓存时不应调用 loader");
    }

    @Test
    void testPrefixIsolation() {
        // 不同前缀的 store 互不干扰
        DefaultCacheStore storeA = new DefaultCacheStore(new ArrayCacheDriver(), "appA");
        DefaultCacheStore storeB = new DefaultCacheStore(new ArrayCacheDriver(), "appB");

        storeA.put("key", "fromA");
        storeB.put("key", "fromB");

        assertEquals("fromA", storeA.get("key"));
        assertEquals("fromB", storeB.get("key"));
    }
}
