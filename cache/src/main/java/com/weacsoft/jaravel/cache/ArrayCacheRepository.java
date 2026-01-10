package com.weacsoft.jaravel.cache;

import com.weacsoft.jaravel.utils.ExpiryMap;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ArrayCacheRepository implements CacheRepository {

    private final ExpiryMap<String, Object> store;

    private final long defaultTtl;

    public ArrayCacheRepository() {
        this(3600);
    }

    public ArrayCacheRepository(long defaultTtl) {
        this.defaultTtl = defaultTtl * 1000;
        this.store = new ExpiryMap<>();
    }

    public ArrayCacheRepository(long defaultTtl, int initialCapacity) {
        this.defaultTtl = defaultTtl * 1000;
        this.store = new ExpiryMap<>(initialCapacity, defaultTtl * 1000);
    }

    @Override
    public boolean put(String key, Object value) {
        store.put(key, value);
        return true;
    }

    @Override
    public boolean put(String key, Object value, long ttl) {
        store.put(key, value, ttl);
        return true;
    }

    @Override
    public boolean put(String key, Object value, long ttl, TimeUnit timeUnit) {
        store.put(key, value, timeUnit.toMillis(ttl));
        return true;
    }

    @Override
    public Object get(String key) {
        return store.get(key);
    }

    @Override
    public boolean has(String key) {
        return store.containsKey(key);
    }

    @Override
    public boolean forget(String key) {
        store.remove(key);
        return true;
    }

    @Override
    public boolean flush() {
        store.clear();
        return true;
    }

    @Override
    public boolean putMany(Map<String, Object> values) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            store.put(entry.getKey(), entry.getValue(), defaultTtl);
        }
        return true;
    }

    @Override
    public Map<String, Object> getMany(Collection<String> keys) {
        Map<String, Object> result = new java.util.HashMap<>();
        for (String key : keys) {
            Object value = store.get(key);
            if (value != null) {
                result.put(key, value);
            }
        }
        return result;
    }

    @Override
    public boolean forgetMany(Collection<String> keys) {
        for (String key : keys) {
            store.remove(key);
        }
        return true;
    }

    @Override
    public boolean add(String key, Object value) {
        if (has(key)) {
            return false;
        }
        return put(key, value);
    }

    @Override
    public boolean add(String key, Object value, long ttl) {
        if (has(key)) {
            return false;
        }
        return put(key, value, ttl);
    }

    @Override
    public boolean add(String key, Object value, long ttl, TimeUnit timeUnit) {
        if (has(key)) {
            return false;
        }
        return put(key, value, ttl, timeUnit);
    }

    @Override
    public long increment(String key) {
        return increment(key, 1);
    }

    @Override
    public long increment(String key, long value) {
        Object current = store.get(key);
        long newValue;
        if (current == null) {
            newValue = value;
        } else if (current instanceof Number) {
            newValue = ((Number) current).longValue() + value;
        } else {
            throw new IllegalArgumentException("Cannot increment non-numeric value");
        }
        store.put(key, newValue, defaultTtl);
        return newValue;
    }

    @Override
    public long decrement(String key) {
        return decrement(key, 1);
    }

    @Override
    public long decrement(String key, long value) {
        return increment(key, -value);
    }
}
