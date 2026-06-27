package com.weacsoft.jaravel.vendor.modelcache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weacsoft.jaravel.vendor.cache.ArrayCacheDriver;
import com.weacsoft.jaravel.vendor.cache.CacheManager;
import com.weacsoft.jaravel.vendor.cache.CacheStore;
import com.weacsoft.jaravel.vendor.cache.DefaultCacheStore;

/**
 * {@link ModelCacheService} 模型缓存核心逻辑单元测试。
 */
class ModelCacheServiceTest {

    /** 自定义 TTL 与前缀的可缓存模型 */
    @CachableModel(prefix = "user", ttl = 120)
    static class CachedUser {
    }

    /** 使用哨兵 TTL（-1）回退到默认 TTL 的可缓存模型 */
    @CachableModel(ttl = -1)
    static class DefaultTtlModel {
    }

    /** 未标注注解的普通模型 */
    static class PlainModel {
    }

    private ModelCacheService service;
    private CacheStore store;

    @BeforeEach
    void setUp() {
        CacheManager manager = new CacheManager();
        store = new DefaultCacheStore(new ArrayCacheDriver(), "test");
        manager.addStore("array", store);
        manager.setDefaultStore("array");

        ModelCacheProperties properties = new ModelCacheProperties();
        properties.setEnabled(true);
        properties.setStore("array");
        properties.setDefaultTtl(3600);
        properties.setKeyPrefix("model-cache:");

        service = new ModelCacheService(manager, properties);
    }

    @Test
    void isCachableRequiresEnabledAndAnnotation() {
        assertTrue(service.isCachable(CachedUser.class));
        assertFalse(service.isCachable(PlainModel.class));

        // 关闭全局开关后全部不可缓存
        ModelCacheProperties disabled = new ModelCacheProperties();
        disabled.setEnabled(false);
        ModelCacheService off = new ModelCacheService(new CacheManager(), disabled);
        assertFalse(off.isCachable(CachedUser.class));
    }

    @Test
    void getTtlUsesAnnotationOrDefault() {
        // 注解显式指定 ttl=120
        assertEquals(120, service.getTtl(CachedUser.class));
        // 哨兵 -1 回退到默认 TTL
        assertEquals(3600, service.getTtl(DefaultTtlModel.class));
        // 未标注注解的类使用默认 TTL
        assertEquals(3600, service.getTtl(PlainModel.class));
    }

    @Test
    void findCachesNonNullResult() {
        AtomicInteger calls = new AtomicInteger();
        CachedUser instance = new CachedUser();
        Supplier<CachedUser> loader = () -> {
            calls.incrementAndGet();
            return instance;
        };

        CachedUser first = service.find(CachedUser.class, 1L, loader);
        CachedUser second = service.find(CachedUser.class, 1L, loader);

        // 第二次命中缓存，loader 仅调用一次
        assertEquals(1, calls.get());
        assertSame(instance, first);
        assertSame(first, second);
    }

    @Test
    void findDoesNotCacheNullResult() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<CachedUser> loader = () -> {
            calls.incrementAndGet();
            return null;
        };

        assertNull(service.find(CachedUser.class, 99L, loader));
        assertNull(service.find(CachedUser.class, 99L, loader));
        // null 不缓存，每次都回源
        assertEquals(2, calls.get());
    }

    @Test
    void invalidateByClassBumpsVersionAndBreaksCache() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<CachedUser> loader = () -> {
            calls.incrementAndGet();
            return new CachedUser();
        };

        // 初始版本号 1
        assertEquals(1, service.getVersion(CachedUser.class));

        service.find(CachedUser.class, 1L, loader);   // 回源 calls=1
        service.find(CachedUser.class, 1L, loader);   // 命中缓存 calls=1

        // 失效整个类 -> 版本号递增
        service.invalidate(CachedUser.class);
        assertEquals(2, service.getVersion(CachedUser.class));

        // 新版本下缓存未命中，重新回源
        service.find(CachedUser.class, 1L, loader);
        assertEquals(2, calls.get());
    }

    @Test
    void invalidateByIdForgetsSingleEntry() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<CachedUser> loader = () -> {
            calls.incrementAndGet();
            return new CachedUser();
        };

        service.find(CachedUser.class, 1L, loader);   // calls=1
        service.find(CachedUser.class, 1L, loader);   // calls=1（命中）
        service.find(CachedUser.class, 2L, loader);   // calls=2

        // 仅失效 id=1，不影响 id=2
        service.invalidate(CachedUser.class, 1L);
        service.find(CachedUser.class, 1L, loader);   // calls=3（未命中）
        service.find(CachedUser.class, 2L, loader);   // calls=3（仍命中）
        assertEquals(3, calls.get());
    }

    @Test
    void nonCachableClassAlwaysLoadsFromSource() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<PlainModel> loader = () -> {
            calls.incrementAndGet();
            return new PlainModel();
        };

        service.find(PlainModel.class, 1, loader);
        service.find(PlainModel.class, 1, loader);
        // 不可缓存 -> 每次都回源
        assertEquals(2, calls.get());
    }

    @Test
    void findAllAndQueryCacheResults() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<List<CachedUser>> loader = () -> {
            calls.incrementAndGet();
            return List.of(new CachedUser());
        };

        List<CachedUser> l1 = service.findAll(CachedUser.class, "all", loader);
        List<CachedUser> l2 = service.findAll(CachedUser.class, "all", loader);
        assertEquals(1, calls.get());
        assertSame(l1, l2);

        AtomicInteger qcalls = new AtomicInteger();
        Supplier<Object> qloader = () -> {
            qcalls.incrementAndGet();
            return 42L;
        };
        Object c1 = service.query(CachedUser.class, "count", qloader);
        Object c2 = service.query(CachedUser.class, "count", qloader);
        assertEquals(1, qcalls.get());
        assertEquals(42L, c1);
        assertSame(c1, c2);
    }
}
