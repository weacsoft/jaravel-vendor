package com.weacsoft.jaravel.cache;

import com.weacsoft.jaravel.utils.ExpiryMap;

public class ArrayCacheDriver implements CacheDriver {
    private final ExpiryMap<String, Object> store;
    private final long defaultTtl;

    public ArrayCacheDriver() {
        this(3600);
    }

    public ArrayCacheDriver(long defaultTtl) {
        this.defaultTtl = defaultTtl * 1000;
        this.store = new ExpiryMap<>();
    }

    @Override
    public boolean put(String key, Object value, long ttl) {
        return store.put(key, value, ttl) != null;
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        return (T) store.get(key);
    }

    @Override
    public boolean exist(String key) {
        return store.containsKey(key);
    }

    @Override
    public boolean remove(String key) {
        return store.remove(key) != null;
    }

    @Override
    public void removeAll() {
        store.clear();
    }
}
