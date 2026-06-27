package com.weacsoft.jaravel.vendor.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CacheManager} 多 store 管理与切换测试。
 * <p>
 * 验证默认 store、按名称解析 store、store 注册与切换、
 * 未注册 store 抛异常等行为。
 */
class CacheManagerTest {

    @Test
    void testDefaultStoreName() {
        CacheManager manager = new CacheManager();
        assertEquals("array", manager.getDefaultStore());
    }

    @Test
    void testRegisterAndResolveStore() {
        CacheManager manager = new CacheManager();
        CacheStore arrayStore = new DefaultCacheStore(new ArrayCacheDriver(), null);
        manager.addStore("array", arrayStore);

        assertSame(arrayStore, manager.store("array"));
        // 默认 store 即 array
        assertSame(arrayStore, manager.store());
    }

    @Test
    void testSwitchDefaultStore() {
        CacheManager manager = new CacheManager();
        CacheStore arrayStore = new DefaultCacheStore(new ArrayCacheDriver(), null);
        CacheStore fileStore = new DefaultCacheStore(new ArrayCacheDriver(), "file");

        manager.addStore("array", arrayStore);
        manager.addStore("file", fileStore);

        // 默认指向 array
        assertSame(arrayStore, manager.store());

        // 切换默认 store 到 file
        manager.setDefaultStore("file");
        assertSame(fileStore, manager.store());
        assertEquals("file", manager.getDefaultStore());
    }

    @Test
    void testStoresAreIsolated() {
        CacheManager manager = new CacheManager();
        CacheStore mem1 = new DefaultCacheStore(new ArrayCacheDriver(), null);
        CacheStore mem2 = new DefaultCacheStore(new ArrayCacheDriver(), null);
        manager.addStore("s1", mem1);
        manager.addStore("s2", mem2);

        mem1.put("key", "from-s1");
        mem2.put("key", "from-s2");

        assertEquals("from-s1", manager.store("s1").get("key"));
        assertEquals("from-s2", manager.store("s2").get("key"));
    }

    @Test
    void testUnregisteredStoreThrows() {
        CacheManager manager = new CacheManager();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> manager.store("not-exists"));
        assertTrue(ex.getMessage().contains("not-exists"));
    }
}
