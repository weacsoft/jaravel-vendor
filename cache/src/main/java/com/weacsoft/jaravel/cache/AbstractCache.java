package com.weacsoft.jaravel.cache;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public abstract class AbstractCache implements Cache {

    protected String prefix = "";

    protected abstract CacheRepository getRepository();

    @Override
    public boolean put(String key, Object value) {
        return getRepository().put(getPrefixedKey(key), value);
    }

    @Override
    public boolean put(String key, Object value, long ttl) {
        return getRepository().put(getPrefixedKey(key), value, ttl);
    }

    @Override
    public boolean put(String key, Object value, long ttl, TimeUnit timeUnit) {
        return getRepository().put(getPrefixedKey(key), value, ttl, timeUnit);
    }

    @Override
    public Object get(String key) {
        return getRepository().get(getPrefixedKey(key));
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        return type.cast(value);
    }

    @Override
    public boolean has(String key) {
        return getRepository().has(getPrefixedKey(key));
    }

    @Override
    public boolean forget(String key) {
        return getRepository().forget(getPrefixedKey(key));
    }

    @Override
    public boolean flush() {
        return getRepository().flush();
    }

    @Override
    public boolean putMany(Map<String, Object> values) {
        Map<String, Object> prefixedValues = new java.util.HashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            prefixedValues.put(getPrefixedKey(entry.getKey()), entry.getValue());
        }
        return getRepository().putMany(prefixedValues);
    }

    @Override
    public Map<String, Object> getMany(Collection<String> keys) {
        Collection<String> prefixedKeys = new java.util.ArrayList<>();
        for (String key : keys) {
            prefixedKeys.add(getPrefixedKey(key));
        }
        Map<String, Object> result = getRepository().getMany(prefixedKeys);
        Map<String, Object> unprefixedResult = new java.util.HashMap<>();
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            unprefixedResult.put(removePrefix(entry.getKey()), entry.getValue());
        }
        return unprefixedResult;
    }

    @Override
    public boolean forgetMany(Collection<String> keys) {
        Collection<String> prefixedKeys = new java.util.ArrayList<>();
        for (String key : keys) {
            prefixedKeys.add(getPrefixedKey(key));
        }
        return getRepository().forgetMany(prefixedKeys);
    }

    @Override
    public boolean remember(String key, long ttl, Supplier<Object> callback) {
        return remember(key, ttl, TimeUnit.SECONDS, callback);
    }

    @Override
    public boolean remember(String key, long ttl, TimeUnit timeUnit, Supplier<Object> callback) {
        if (has(key)) {
            return true;
        }
        Object value = callback.get();
        return put(key, value, ttl, timeUnit);
    }

    @Override
    public boolean rememberForever(String key, Supplier<Object> callback) {
        if (has(key)) {
            return true;
        }
        Object value = callback.get();
        return put(key, value);
    }

    @Override
    public Object pull(String key) {
        Object value = get(key);
        forget(key);
        return value;
    }

    @Override
    public <T> T pull(String key, Class<T> type) {
        Object value = pull(key);
        if (value == null) {
            return null;
        }
        return type.cast(value);
    }

    @Override
    public boolean add(String key, Object value) {
        return getRepository().add(getPrefixedKey(key), value);
    }

    @Override
    public boolean add(String key, Object value, long ttl) {
        return getRepository().add(getPrefixedKey(key), value, ttl);
    }

    @Override
    public boolean add(String key, Object value, long ttl, TimeUnit timeUnit) {
        return getRepository().add(getPrefixedKey(key), value, ttl, timeUnit);
    }

    @Override
    public long increment(String key) {
        return increment(key, 1);
    }

    @Override
    public long increment(String key, long value) {
        return getRepository().increment(getPrefixedKey(key), value);
    }

    @Override
    public long decrement(String key) {
        return decrement(key, 1);
    }

    @Override
    public long decrement(String key, long value) {
        return getRepository().decrement(getPrefixedKey(key), value);
    }

    @Override
    public String getPrefix() {
        return prefix;
    }

    @Override
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    protected String getPrefixedKey(String key) {
        if (prefix == null || prefix.isEmpty()) {
            return key;
        }
        return prefix + key;
    }

    protected String removePrefix(String key) {
        if (prefix == null || prefix.isEmpty()) {
            return key;
        }
        if (key.startsWith(prefix)) {
            return key.substring(prefix.length());
        }
        return key;
    }
}
