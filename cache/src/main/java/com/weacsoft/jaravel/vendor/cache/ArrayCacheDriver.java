package com.weacsoft.jaravel.vendor.cache;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存 {@link ConcurrentHashMap} 的缓存驱动，对齐 Laravel {@code "array"} 缓存驱动。
 * <p>
 * 线程安全，仅在当前 JVM 进程内有效，进程重启即失，常用于单元测试与本地开发。
 * TTL 单位为秒：{@code expiryAt = now + ttlSeconds * 1000}，{@code expiryAt == 0} 表示永不过期。
 * 读取 / 存在性判断时会惰性清理过期条目。
 */
public class ArrayCacheDriver implements CacheDriver {

    /** 真正存储条目的并发 Map */
    private final Map<String, CacheEntry> store = new ConcurrentHashMap<>();

    /** 默认构造器 */
    public ArrayCacheDriver() {
    }

    @Override
    public boolean put(String key, Object value, long ttlSeconds) {
        long expiryAt = ttlSeconds > 0 ? System.currentTimeMillis() + ttlSeconds * 1000L : 0L;
        store.put(key, new CacheEntry(value, expiryAt));
        return true;
    }

    @Override
    public Object get(String key) {
        CacheEntry entry = store.get(key);
        if (entry == null) {
            return null;
        }
        if (isExpired(entry)) {
            store.remove(key);
            return null;
        }
        return entry.value;
    }

    @Override
    public boolean exists(String key) {
        CacheEntry entry = store.get(key);
        if (entry == null) {
            return false;
        }
        if (isExpired(entry)) {
            store.remove(key);
            return false;
        }
        return true;
    }

    @Override
    public boolean remove(String key) {
        return store.remove(key) != null;
    }

    @Override
    public void removeAll() {
        store.clear();
    }

    @Override
    public Collection<String> allKeys() {
        // 顺带惰性清理过期条目，仅返回未过期键
        store.entrySet().removeIf(e -> isExpired(e.getValue()));
        return store.keySet();
    }

    /** 是否已过期 */
    private static boolean isExpired(CacheEntry entry) {
        return entry.expiryAt > 0 && System.currentTimeMillis() >= entry.expiryAt;
    }

    /**
     * 内存缓存条目。
     *
     * @param expiryAt 过期时间戳（毫秒），0 表示永不过期
     */
        private record CacheEntry(Object value, long expiryAt) {
    }
}
