package com.weacsoft.jaravel.vendor.aetherupload.store;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存记录头存储（默认实现）。
 * <p>
 * 基于 {@link ConcurrentHashMap}，读取时惰性清理过期项，并在写入时概率性全量清理，
 * 无后台线程，适合单实例部署；多实例部署请为组配置 cache/redis 记录头。
 */
public class MemoryUploadHeaderStore implements UploadHeaderStore {

    private static final class Entry {
        final String value;
        final long expireAt; // 0 表示永不过期

        Entry(String value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }

        boolean expired() {
            return expireAt > 0 && System.currentTimeMillis() > expireAt;
        }
    }

    private final Map<String, Entry> map = new ConcurrentHashMap<>();

    @Override
    public void put(String key, String value, long ttlSeconds) {
        long expireAt = ttlSeconds > 0 ? System.currentTimeMillis() + ttlSeconds * 1000 : 0;
        map.put(key, new Entry(value, expireAt));
        // 概率性清理过期项（约 1/100 次写入触发），避免长期运行内存膨胀
        if (map.size() > 64 && System.nanoTime() % 100 == 0) {
            cleanup();
        }
    }

    @Override
    public String get(String key) {
        Entry entry = map.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expired()) {
            map.remove(key);
            return null;
        }
        return entry.value;
    }

    @Override
    public void remove(String key) {
        map.remove(key);
    }

    private void cleanup() {
        Iterator<Map.Entry<String, Entry>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().expired()) {
                it.remove();
            }
        }
    }
}
