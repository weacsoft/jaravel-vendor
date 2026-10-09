package com.weacsoft.jaravel.vendor.cache;

import com.weacsoft.jaravel.vendor.cache.driver.ArrayCacheDriver;
import com.weacsoft.jaravel.vendor.cache.store.DefaultCacheStore;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自增/自减的 <b>TTL 保留</b> 回归测试（审计 M9，已实测复现过）。
 * <p>
 * 缺陷形态：{@code DefaultCacheStore.increment/decrement} 写死 {@code put(key,next,0)}，
 * 于是「有期限」的键被写成永不过期 —— 限流计数永久生效、model-cache 版本键无限堆积。
 * <p>
 * 同时锁定两条设计决定：
 * <ul>
 *   <li>驱动不支持 TTL 能力时<b>沿用无 TTL 写入</b>并告警（而不是「退化成一个短 TTL」：
 *       那会让限流/版本键中途重置，把可用性问题变成正确性问题）；</li>
 *   <li>TTL 本身必须真实生效（过期后读不到），避免「保留了 TTL 但没人真的过期」的假修复。</li>
 * </ul>
 */
class CacheTtlPreservationTest {

    private static DefaultCacheStore storeOf(ArrayCacheDriver driver) {
        return new DefaultCacheStore(driver, "t");
    }

    @Test
    void incrementPreservesRemainingTtl() {
        ArrayCacheDriver driver = new ArrayCacheDriver();
        DefaultCacheStore store = storeOf(driver);
        store.put("throttle", 0L, 60);

        assertEquals(1L, store.increment("throttle"));

        OptionalLong ttl = driver.remainingTtlSeconds("t:throttle");
        assertTrue(ttl.isPresent(), "Array 驱动应报告剩余 TTL（TtlAwareCacheDriver）");
        assertTrue(ttl.getAsLong() > 0 && ttl.getAsLong() <= 60,
                "自增必须保留原 TTL，实际剩余=" + ttl.getAsLong() + " 秒");
    }

    @Test
    void repeatedIncrementDoesNotAccumulateTtl() {
        ArrayCacheDriver driver = new ArrayCacheDriver();
        DefaultCacheStore store = storeOf(driver);
        store.put("counter", 0L, 30);
        OptionalLong before = driver.remainingTtlSeconds("t:counter");

        for (int i = 0; i < 5; i++) {
            store.increment("counter");
        }

        OptionalLong after = driver.remainingTtlSeconds("t:counter");
        assertTrue(before.isPresent() && after.isPresent());
        assertTrue(after.getAsLong() <= before.getAsLong(),
                "反复自增不得刷新/延长 TTL（否则等于永不过期）");
    }

    @Test
    void incrementKeepsPermanentKeyPermanent() {
        ArrayCacheDriver driver = new ArrayCacheDriver();
        DefaultCacheStore store = storeOf(driver);
        store.put("forever", 1L, 0);

        store.increment("forever");

        OptionalLong ttl = driver.remainingTtlSeconds("t:forever");
        assertTrue(ttl.isPresent());
        assertEquals(0L, ttl.getAsLong(), "原本永不过期的键自增后仍应永不过期");
    }

    @Test
    void incrementOnMissingKeyCreatesCounterWithoutFailing() {
        ArrayCacheDriver driver = new ArrayCacheDriver();
        DefaultCacheStore store = storeOf(driver);

        assertEquals(1L, store.increment("brand-new"));
        assertEquals(3L, store.increment("brand-new", 2L));
    }

    @Test
    void decrementAlsoPreservesTtl() {
        ArrayCacheDriver driver = new ArrayCacheDriver();
        DefaultCacheStore store = storeOf(driver);
        store.put("remain", 10L, 45);

        assertEquals(9L, store.decrement("remain"));

        OptionalLong ttl = driver.remainingTtlSeconds("t:remain");
        assertTrue(ttl.isPresent() && ttl.getAsLong() > 0,
                "自减同样必须保留 TTL，实际=" + ttl.orElse(-99));
    }

    @Test
    void ttlActuallyExpires() throws InterruptedException {
        ArrayCacheDriver driver = new ArrayCacheDriver();
        DefaultCacheStore store = storeOf(driver);
        store.put("short", "v", 1);

        assertTrue(store.has("short"));
        Thread.sleep(1100);
        assertFalse(store.has("short"), "TTL 必须真实生效（过期后读不到）");
        assertFalse(driver.remainingTtlSeconds("t:short").isPresent(), "过期后应报告键不存在");
    }

    @Test
    void driverWithoutTtlCapabilityFallsBackInsteadOfThrowing() {
        // 最小实现：只满足 CacheDriver，不具备 TTL 能力
        CacheDriver minimal = new CacheDriver() {
            private final java.util.Map<String, Object> map = new java.util.concurrent.ConcurrentHashMap<>();

            @Override
            public boolean put(String key, Object value, long ttlSeconds) {
                map.put(key, value);
                return true;
            }

            @Override
            public Object get(String key) {
                return map.get(key);
            }

            @Override
            public boolean exists(String key) {
                return map.containsKey(key);
            }

            @Override
            public boolean remove(String key) {
                return map.remove(key) != null;
            }

            @Override
            public void removeAll() {
                map.clear();
            }

            @Override
            public Collection<String> allKeys() {
                return map.keySet();
            }
        };
        DefaultCacheStore store = new DefaultCacheStore(minimal, "");

        assertNotNull(store);
        assertEquals(1L, store.increment("k"), "非 TTL 能力驱动必须走回退路径而不是抛异常");
        assertEquals(3L, store.increment("k", 2L));
    }
}