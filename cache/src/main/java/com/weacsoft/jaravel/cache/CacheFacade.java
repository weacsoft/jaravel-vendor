package com.weacsoft.jaravel.cache;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class CacheFacade {

    private static CacheManager manager;

    public static void setManager(CacheManager manager) {
        CacheFacade.manager = manager;
    }

    public static CacheManager manager() {
        return manager;
    }

    public static Cache store() {
        return manager().store();
    }

    public static Cache store(String name) {
        return manager().store(name);
    }

    public static boolean put(String key, Object value) {
        return store().put(key, value);
    }

    public static boolean put(String key, Object value, long ttl) {
        return store().put(key, value, ttl);
    }

    public static boolean put(String key, Object value, long ttl, TimeUnit timeUnit) {
        return store().put(key, value, ttl, timeUnit);
    }

    public static Object get(String key) {
        return store().get(key);
    }

    public static <T> T get(String key, Class<T> type) {
        return store().get(key, type);
    }

    public static boolean has(String key) {
        return store().has(key);
    }

    public static boolean forget(String key) {
        return store().forget(key);
    }

    public static boolean flush() {
        return store().flush();
    }

    public static boolean putMany(Map<String, Object> values) {
        return store().putMany(values);
    }

    public static Map<String, Object> getMany(Collection<String> keys) {
        return store().getMany(keys);
    }

    public static boolean forgetMany(Collection<String> keys) {
        return store().forgetMany(keys);
    }

    public static boolean remember(String key, long ttl, Supplier<Object> callback) {
        return store().remember(key, ttl, callback);
    }

    public static boolean remember(String key, long ttl, TimeUnit timeUnit, Supplier<Object> callback) {
        return store().remember(key, ttl, timeUnit, callback);
    }

    public static boolean rememberForever(String key, Supplier<Object> callback) {
        return store().rememberForever(key, callback);
    }

    public static Object pull(String key) {
        return store().pull(key);
    }

    public static <T> T pull(String key, Class<T> type) {
        return store().pull(key, type);
    }

    public static boolean add(String key, Object value) {
        return store().add(key, value);
    }

    public static boolean add(String key, Object value, long ttl) {
        return store().add(key, value, ttl);
    }

    public static boolean add(String key, Object value, long ttl, TimeUnit timeUnit) {
        return store().add(key, value, ttl, timeUnit);
    }

    public static long increment(String key) {
        return store().increment(key);
    }

    public static long increment(String key, long value) {
        return store().increment(key, value);
    }

    public static long decrement(String key) {
        return store().decrement(key);
    }

    public static long decrement(String key, long value) {
        return store().decrement(key, value);
    }

    public static String getPrefix() {
        return store().getPrefix();
    }

    public static void setPrefix(String prefix) {
        store().setPrefix(prefix);
    }

    public static void extend(String name, java.util.function.Function<String, Cache> callback) {
        manager().extend(name, callback);
    }

    public static void setDefaultStore(String name) {
        manager().setDefaultStore(name);
    }

    public static String getDefaultStore() {
        return manager().getDefaultStore();
    }
}
