package com.weacsoft.jaravel.vendor.cache;

import com.weacsoft.jaravel.vendor.cache.autoconfigure.CacheAutoConfiguration;
import com.weacsoft.jaravel.vendor.cache.driver.ArrayCacheDriver;
import com.weacsoft.jaravel.vendor.cache.store.DefaultCacheStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存管理器，对齐 Laravel {@code Illuminate\Cache\CacheManager}。
 * <p>
 * 维护多个命名 {@link CacheStore}（如 {@code array}、{@code file}、{@code redis} 等），
 * 按名称解析 store 并提供默认 store。线程安全（基于 {@link ConcurrentHashMap}）。
 * <p>
 * 由 {@link CacheAutoConfiguration} 以 {@code @Bean @ConditionalOnMissingBean} 方式注册，
 * 构造时注入各 store。不使用 {@code @Component} 以避免组件扫描创建空实例。
 */
public class CacheManager {

    /** 命名 store 注册表：name -> CacheStore */
    private final Map<String, CacheStore> stores = new ConcurrentHashMap<>();

    /** 默认 store 名称 */
    private String defaultStore = "array";

    /** 无参构造器 */
    public CacheManager() {
    }

    /**
     * 返回默认 store。
     */
    public CacheStore store() {
        return store(defaultStore);
    }

    /**
     * 按名称返回指定 store，未注册则抛出异常。
     */
    public CacheStore store(String name) {
        CacheStore store = stores.get(name);
        if (store == null) {
            throw new IllegalStateException("未注册的缓存 store: " + name);
        }
        return store;
    }

    /**
     * 注册一个命名 store。
     */
    public void addStore(String name, CacheStore store) {
        stores.put(name, store);
    }

    /**
     * 设置默认 store 名称。
     */
    public void setDefaultStore(String name) {
        this.defaultStore = name;
    }

    /**
     * @return 默认 store 名称
     */
    public String getDefaultStore() {
        return defaultStore;
    }

    /**
     * 创建默认的内存缓存 store（基于 ArrayCacheDriver），供模块在 CacheManager 未注入时作为 fallback 使用。
     *
     * @return 内存缓存 store
     */
    public static CacheStore createDefaultStore() {
        return new DefaultCacheStore(new ArrayCacheDriver(), "");
    }
}
