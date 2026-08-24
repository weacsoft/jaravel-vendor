package com.weacsoft.jaravel.vendor.cache;

import com.weacsoft.jaravel.vendor.core.Facade;

import java.util.function.Supplier;

/**
 * Cache 门面，对齐 Laravel {@code Cache::}。
 * <p>
 * 静态代理 {@link CacheManager}（通过 {@link Facade#resolve(Class)} 从 Spring 容器解析），
 * 所有方法委托给默认 store，便于业务代码以 {@code Cache::get("key")} 风格调用。
 * <pre>
 * Cache::put("user:1", user, 60);          // 60 秒
 * User u = Cache::get("user:1", User.class);
 * long n = Cache::increment("hits");
 * Object v = Cache::remember("cfg", 300, () -> loadCfg());
 * Cache::store("file").put("k", "v", 0);   // 指定 store
 * </pre>
 */
public final class Cache {

    private Cache() {
    }

    /** 从容器解析 CacheManager */
    private static CacheManager mgr() {
        return Facade.resolve(CacheManager.class);
    }

    public static boolean put(String key, Object value, long ttl) {
        return mgr().store().put(key, value, ttl);
    }

    public static boolean put(String key, Object value) {
        return mgr().store().put(key, value);
    }

    public static Object get(String key) {
        return mgr().store().get(key);
    }

    public static <T> T get(String key, Class<T> type) {
        return mgr().store().get(key, type);
    }

    public static boolean has(String key) {
        return mgr().store().has(key);
    }

    public static boolean forget(String key) {
        return mgr().store().forget(key);
    }

    public static void flush() {
        mgr().store().flush();
    }

    public static Object pull(String key) {
        return mgr().store().pull(key);
    }

    public static boolean add(String key, Object value, long ttl) {
        return mgr().store().add(key, value, ttl);
    }

    public static long increment(String key) {
        return mgr().store().increment(key);
    }

    public static long increment(String key, long amount) {
        return mgr().store().increment(key, amount);
    }

    public static long decrement(String key) {
        return mgr().store().decrement(key);
    }

    public static long decrement(String key, long amount) {
        return mgr().store().decrement(key, amount);
    }

    public static Object remember(String key, long ttl, Supplier<Object> loader) {
        return mgr().store().remember(key, ttl, loader);
    }

    public static Object rememberForever(String key, Supplier<Object> loader) {
        return mgr().store().rememberForever(key, loader);
    }

    /** 返回默认 store */
    public static CacheStore store() {
        return mgr().store();
    }

    /** 按名称返回指定 store */
    public static CacheStore store(String name) {
        return mgr().store(name);
    }
}
