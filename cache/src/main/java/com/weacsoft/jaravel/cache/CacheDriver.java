package com.weacsoft.jaravel.cache;

public interface CacheDriver {

    boolean put(String key, Object value, long ttl);
    default boolean put(String key, Object value) {
        return put(key, value, -1);
    }
    default Object get(String key) {
        return get(key, Object.class);
    }
    <T> T get(String key, Class<T> type);

    boolean exist(String key);
    boolean remove(String key);
    void removeAll();
}
