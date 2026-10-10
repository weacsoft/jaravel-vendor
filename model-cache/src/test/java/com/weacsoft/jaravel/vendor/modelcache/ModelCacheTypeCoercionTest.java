package com.weacsoft.jaravel.vendor.modelcache;

import com.weacsoft.jaravel.vendor.cache.CacheManager;
import com.weacsoft.jaravel.vendor.cache.CacheStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 缓存命中值的类型还原（审计 M16）。
 * <p>
 * 非内存 store（redis/database/file）命中时取回的是 JSON 结构（{@code LinkedHashMap} /
 * {@code ArrayList}），调用方按实体类型使用会抛 {@code ClassCastException}。
 * 现在 {@code find}/{@code findAll} 做元素级还原，且<b>不可还原时驱逐该键并回源</b> ——
 * 绝不把错类型交给调用方（「返回原值 + WARN」会把类型契约变成「有时实体、有时 Map」）。
 * <p>
 * 另一条必须固定的语义：array（内存）store 命中的是 gaarason <b>托管实体</b>，
 * 必须<b>原样返回同一实例</b>（不能被 JSON 转成游离 POJO）。
 */
class ModelCacheTypeCoercionTest {

    /** 参与缓存的测试实体（需无参构造以支持 JSON 还原） */
    @CachableModel(ttl = 60)
    public static class CachedUser {
        public Long id;
        public String name;

        public CachedUser() {
        }

        public CachedUser(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private CacheStore store;
    private CacheManager cacheManager;
    private ModelCacheService service;

    @BeforeEach
    void setUp() {
        store = mock(CacheStore.class);
        cacheManager = mock(CacheManager.class);
        when(cacheManager.store()).thenReturn(store);
        service = new ModelCacheService(cacheManager, new ModelCacheProperties());
    }

    private static Map<String, Object> userMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", 7);
        map.put("name", "neo");
        return map;
    }

    @Test
    void findCoercesCachedMapIntoEntity() {
        when(store.get(anyString())).thenReturn(userMap());

        CachedUser user = service.find(CachedUser.class, 7L, () -> new CachedUser(7L, "loader"));

        assertNotNull(user);
        assertEquals(7L, user.id);
        assertEquals("neo", user.name);
    }

    @Test
    void findAllCoercesCachedMapListIntoEntities() {
        List<Object> cached = new ArrayList<>();
        cached.add(userMap());
        when(store.get(anyString())).thenReturn(cached);

        List<CachedUser> users = service.findAll(CachedUser.class, "all", () -> List.of(new CachedUser(1L, "loader")));

        assertEquals(1, users.size());
        assertInstanceOf(CachedUser.class, users.get(0), "列表元素必须是实体而不是 Map");
        assertEquals("neo", users.get(0).name);
    }

    @Test
    void unconvertibleCacheEntryIsEvictedAndReloaded() {
        // 含未知类型元素（String）→ 不可还原：必须驱逐 + 回源，绝不返回错类型
        List<Object> cached = new ArrayList<>();
        cached.add("not-a-map");
        when(store.get(anyString())).thenReturn(cached);

        List<CachedUser> users = service.findAll(CachedUser.class, "bad",
                () -> List.of(new CachedUser(2L, "from-loader")));

        assertEquals(1, users.size());
        assertEquals("from-loader", users.get(0).name, "驱逐后必须回源 loader");
        verify(store).forget(anyString());
    }

    @Test
    void arrayStoreManagedEntityIsReturnedAsSameInstance() {
        CachedUser managed = new CachedUser(9L, "managed");
        when(store.get(anyString())).thenReturn(managed);

        CachedUser user = service.find(CachedUser.class, 9L, () -> new CachedUser(9L, "loader"));

        assertSame(managed, user, "内存 store 命中的托管实体必须原样返回（不得 JSON 转换）");
        verify(store, never()).forget(anyString());
    }

    @Test
    void queryDoesNotCoerce() {
        Map<String, Object> scalar = new LinkedHashMap<>();
        scalar.put("count", 3);
        when(store.get(anyString())).thenReturn(scalar);

        Object result = service.query(CachedUser.class, "count", () -> 0);

        assertSame(scalar, result, "query 的契约是任意 Object，不做类型还原");
    }

    @Test
    void cacheMissReloadsAndBackfills() {
        when(store.get(anyString())).thenReturn(null);

        CachedUser user = service.find(CachedUser.class, 11L, () -> new CachedUser(11L, "fresh"));

        assertEquals("fresh", user.name);
        verify(store).put(anyString(), any(), anyLong());
        assertTrue(true);
    }
}