package com.weacsoft.jaravel.cache;

import java.util.concurrent.TimeUnit;

public interface CacheRepository {

    boolean put(String key, Object value);

    boolean put(String key, Object value, long ttl);

    boolean put(String key, Object value, long ttl, TimeUnit timeUnit);

    Object get(String key);

    boolean has(String key);

    boolean forget(String key);

    boolean flush();

    boolean putMany(java.util.Map<String, Object> values);

    java.util.Map<String, Object> getMany(java.util.Collection<String> keys);

    boolean forgetMany(java.util.Collection<String> keys);

    boolean add(String key, Object value);

    boolean add(String key, Object value, long ttl);

    boolean add(String key, Object value, long ttl, TimeUnit timeUnit);

    long increment(String key);

    long increment(String key, long value);

    long decrement(String key);

    long decrement(String key, long value);
}
