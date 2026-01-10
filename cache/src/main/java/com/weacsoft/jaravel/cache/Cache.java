package com.weacsoft.jaravel.cache;

import java.util.concurrent.TimeUnit;

public interface Cache {

    boolean put(String key, Object value);

    boolean put(String key, Object value, long ttl);

    boolean put(String key, Object value, long ttl, TimeUnit timeUnit);

    Object get(String key);

    <T> T get(String key, Class<T> type);

    boolean has(String key);

    boolean forget(String key);

    boolean flush();

    boolean putMany(java.util.Map<String, Object> values);

    java.util.Map<String, Object> getMany(java.util.Collection<String> keys);

    boolean forgetMany(java.util.Collection<String> keys);

    boolean remember(String key, long ttl, java.util.function.Supplier<Object> callback);

    boolean remember(String key, long ttl, TimeUnit timeUnit, java.util.function.Supplier<Object> callback);

    boolean rememberForever(String key, java.util.function.Supplier<Object> callback);

    Object pull(String key);

    <T> T pull(String key, Class<T> type);

    boolean add(String key, Object value);

    boolean add(String key, Object value, long ttl);

    boolean add(String key, Object value, long ttl, TimeUnit timeUnit);

    long increment(String key);

    long increment(String key, long value);

    long decrement(String key);

    long decrement(String key, long value);

    String getPrefix();

    void setPrefix(String prefix);
}
