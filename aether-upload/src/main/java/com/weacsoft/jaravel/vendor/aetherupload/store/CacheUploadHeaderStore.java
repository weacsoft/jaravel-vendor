package com.weacsoft.jaravel.vendor.aetherupload.store;

import com.weacsoft.jaravel.vendor.cache.CacheStore;

/**
 * 基于 cache 模块 {@link CacheStore} 的记录头存储。
 * <p>
 * 组配置 {@code header-store: redis}（或任意 cache store 名）时使用，
 * 统一由 cache 模块管理底层驱动 —— 配置 redis store 即实现 redis 记录头，
 * 支持多实例共享上传进度（断线续传跨节点可用）。
 */
public class CacheUploadHeaderStore implements UploadHeaderStore {

    /** 键前缀，避免与业务缓存冲突 */
    private static final String PREFIX = "aetherupload:";

    private final CacheStore cacheStore;

    public CacheUploadHeaderStore(CacheStore cacheStore) {
        this.cacheStore = cacheStore;
    }

    @Override
    public void put(String key, String value, long ttlSeconds) {
        cacheStore.put(PREFIX + key, value, ttlSeconds);
    }

    @Override
    public String get(String key) {
        Object value = cacheStore.get(PREFIX + key);
        return value == null ? null : value.toString();
    }

    @Override
    public void remove(String key) {
        cacheStore.forget(PREFIX + key);
    }
}
