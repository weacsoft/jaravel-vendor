package com.weacsoft.jaravel.vendor.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ArrayCacheDriver} 内存缓存驱动测试。
 * <p>
 * 验证底层 put / get / exists / remove / removeAll / allKeys 行为，
 * 以及 TTL 过期与惰性清理。
 */
class ArrayCacheDriverTest {

    @Test
    void testPutAndGet() {
        ArrayCacheDriver driver = new ArrayCacheDriver();
        driver.put("name", "jaravel", 0);
        assertEquals("jaravel", driver.get("name"));
    }

    @Test
    void testGetReturnsNullForMissingKey() {
        ArrayCacheDriver driver = new ArrayCacheDriver();
        assertNull(driver.get("missing"));
    }

    @Test
    void testExistsAndRemove() {
        ArrayCacheDriver driver = new ArrayCacheDriver();
        driver.put("k1", "v1", 0);

        assertTrue(driver.exists("k1"));
        assertFalse(driver.exists("missing"));

        assertTrue(driver.remove("k1"));
        assertFalse(driver.exists("k1"));
        // 再次移除不存在的键返回 false
        assertFalse(driver.remove("k1"));
    }

    @Test
    void testRemoveAllAndAllKeys() {
        ArrayCacheDriver driver = new ArrayCacheDriver();
        driver.put("a", 1, 0);
        driver.put("b", 2, 0);
        driver.put("c", 3, 0);

        assertEquals(3, driver.allKeys().size());
        assertTrue(driver.allKeys().contains("a"));

        driver.removeAll();
        assertTrue(driver.allKeys().isEmpty());
        assertNull(driver.get("a"));
    }

    @Test
    void testTtlExpiryLazyCleanup() throws InterruptedException {
        ArrayCacheDriver driver = new ArrayCacheDriver();
        // 1 秒过期
        driver.put("temp", "value", 1);

        // 写入后立即存在
        assertTrue(driver.exists("temp"));
        assertEquals("value", driver.get("temp"));

        // 等待超过 TTL
        Thread.sleep(1100);

        // get 惰性清理，返回 null
        assertNull(driver.get("temp"));
        assertFalse(driver.exists("temp"));
    }

    @Test
    void testNeverExpireWhenTtlZero() {
        ArrayCacheDriver driver = new ArrayCacheDriver();
        // ttlSeconds <= 0 表示永不过期
        driver.put("forever", "v", 0);
        assertTrue(driver.exists("forever"));
        assertEquals("v", driver.get("forever"));
    }
}
