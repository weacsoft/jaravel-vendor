package com.weacsoft.jaravel.cache;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class DefaultCacheStore implements CacheStore {
    private final CacheDriver cacheDriver;
    private String prefix;

    public DefaultCacheStore(String prefix, CacheDriver cacheDriver) {
        this.prefix = prefix;
        this.cacheDriver = cacheDriver;
    }

    public DefaultCacheStore(String prefix) {
        this(prefix, new ArrayCacheDriver());
    }

    public DefaultCacheStore(CacheDriver cacheDriver) {
        this("", cacheDriver);
    }

    @Override
    public boolean put(String key, Object value) {
        return cacheDriver.put(key, value);
    }

    @Override
    public boolean put(String key, Object value, long ttl) {
        return put(key, value, ttl, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean put(String key, Object value, long ttl, TimeUnit timeUnit) {
        return cacheDriver.put(key, value, timeUnit.toMillis(ttl));
    }


    @Override
    public <T> T get(String key, Class<T> type) {
        return cacheDriver.get(key, type);
    }

    @Override
    public boolean has(String key) {
        return cacheDriver.exist(key);
    }

    @Override
    public boolean forget(String key) {
        return cacheDriver.remove(key);
    }

    @Override
    public boolean flush() {
        cacheDriver.removeAll();
        return true;
    }

    @Override
    public boolean putMany(Map<String, Object> values) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            this.put(entry.getKey(), entry.getValue());
        }
        return true;
    }

    @Override
    public Map<String, Object> getMany(Collection<String> keys) {
        Map<String, Object> result = new java.util.HashMap<>();
        for (String key : keys) {
            Object value = this.get(key);
            if (value != null) {
                result.put(key, value);
            }
        }
        return result;
    }

    @Override
    public boolean forgetMany(Collection<String> keys) {
        for (String key : keys) {
            this.forget(key);
        }
        return true;
    }

    @Override
    public boolean remember(String key, long ttl, Supplier<Object> callback) {
        return remember(key, ttl, TimeUnit.MILLISECONDS, callback);
    }

    @Override
    public boolean remember(String key, long ttl, TimeUnit timeUnit, Supplier<Object> callback) {
        if (has(key)) {
            return true;
        }
        return put(key, callback.get(), ttl, timeUnit);
    }

    @Override
    public boolean rememberForever(String key, Supplier<Object> callback) {
        if (has(key)) {
            return true;
        }
        return put(key, callback.get());
    }

    @Override
    public Object pull(String key) {
        return pull(key, Object.class);
    }

    @Override
    public <T> T pull(String key, Class<T> type) {
        T value = this.get(key, type);
        forget(key);
        return value;
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
        return add(key, value, ttl, TimeUnit.MILLISECONDS);
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
        Object current = get(key);
        long newValue;
        if (current == null) {
            newValue = value;
        } else if (current instanceof Number) {
            newValue = ((Number) current).longValue() + value;
        } else {
            throw new IllegalArgumentException("Cannot increment non-numeric value");
        }
        put(key, newValue);
        return newValue;
    }

    @Override
    public long decrement(String key) {
        return increment(key, 1);
    }

    @Override
    public long decrement(String key, long value) {
        return increment(key, -value);
    }

    @Override
    public String getPrefix() {
        return prefix;
    }

    @Override
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
}
