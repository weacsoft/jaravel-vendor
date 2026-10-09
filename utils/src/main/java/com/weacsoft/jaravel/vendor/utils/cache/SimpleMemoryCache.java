package com.weacsoft.jaravel.vendor.utils.cache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用内存缓存，基于 {@link ConcurrentHashMap} + TTL 过期机制。
 * <p>
 * 零外部依赖，仅依赖 JDK 标准库。适用于：
 * <ul>
 *   <li>各模块在 cache 模块不可用时的 fallback 存储</li>
 *   <li>单机场景下的轻量内存缓存</li>
 *   <li>单元测试中的 mock 缓存</li>
 * </ul>
 * <p>
 * 线程安全。读取 / 存在性判断时惰性清理过期条目；另提供 {@link #cleanup()} 主动清理。
 * <p>
 * cache 模块的 {@code ArrayCacheDriver} 内部基于此类实现；
 * captcha 模块的 {@code MemoryCaptchaStore} 也可基于此类实现；
 * wechat-sdk 等模块在 CacheManager 未注入时直接使用此类作为 fallback。
 *
 * <pre>
 * SimpleMemoryCache cache = new SimpleMemoryCache();
 * cache.put("key", value, 60);    // 60 秒后过期
 * cache.put("key2", value2);       // 永不过期
 * Object v = cache.get("key");
 * boolean exists = cache.exists("key");
 * cache.remove("key");
 * cache.cleanup();                 // 主动清理所有过期项
 * </pre>
 */
public class SimpleMemoryCache {

    /** 存储条目的并发 Map */
    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    /**
     * 写入缓存，指定过期秒数。
     *
     * @param key        缓存键
     * @param value      缓存值
     * @param ttlSeconds 过期秒数，{@code <= 0} 表示永不过期
     */
    public void put(String key, Object value, long ttlSeconds) {
        long expiryAt = ttlSeconds > 0 ? System.currentTimeMillis() + ttlSeconds * 1000L : 0L;
        store.put(key, new Entry(value, expiryAt));
    }

    /**
     * 永久写入缓存（永不过期）。
     *
     * @param key   缓存键
     * @param value 缓存值
     */
    public void put(String key, Object value) {
        store.put(key, new Entry(value, 0L));
    }

    /**
     * 查询键的剩余过期秒数（惰性清理过期条目）。
     * <p>
     * 用于「读改写」型操作（如 increment）保留原 TTL：不提供该能力时，
     * 自增会把键写成永不过期（限流计数永久生效、版本键无限堆积）。
     *
     * @param key 缓存键
     * @return 剩余秒数（{@code > 0}）；键不存在/已过期返回 {@code -2}；永不过期返回 {@code -1}
     */
    public long remainingTtlSeconds(String key) {
        Entry entry = store.get(key);
        if (entry == null) {
            return -2;
        }
        if (entry.isExpired()) {
            store.remove(key);
            return -2;
        }
        if (entry.expiryAt <= 0) {
            return -1;
        }
        long remainingMillis = entry.expiryAt - System.currentTimeMillis();
        if (remainingMillis <= 0) {
            store.remove(key);
            return -2;
        }
        // 向上取整：还剩 0.4 秒也要报 1 秒，避免写回时变成 0（= 永不过期）
        return Math.max(1, (remainingMillis + 999) / 1000);
    }

    /**
     * 读取缓存值，不存在或已过期返回 {@code null}。
     *
     * @param key 缓存键
     * @return 缓存值，或 {@code null}
     */
    public Object get(String key) {
        Entry entry = store.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            store.remove(key);
            return null;
        }
        return entry.value;
    }

    /**
     * 读取并按指定类型转换缓存值。
     *
     * @param key  缓存键
     * @param type 期望类型
     * @return 类型转换后的缓存值，不存在或类型不匹配返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * 判断缓存键是否存在（未过期）。
     *
     * @param key 缓存键
     * @return 是否存在
     */
    public boolean exists(String key) {
        Entry entry = store.get(key);
        if (entry == null) {
            return false;
        }
        if (entry.isExpired()) {
            store.remove(key);
            return false;
        }
        return true;
    }

    /**
     * 移除指定缓存键。
     *
     * @param key 缓存键
     * @return 是否确实移除了条目
     */
    public boolean remove(String key) {
        return store.remove(key) != null;
    }

    /**
     * 清空所有缓存条目。
     */
    public void removeAll() {
        store.clear();
    }

    /**
     * 返回所有未过期的缓存键。
     *
     * @return 未过期键集合
     */
    public Collection<String> allKeys() {
        store.entrySet().removeIf(e -> e.getValue().isExpired());
        return new ArrayList<>(store.keySet());
    }

    /**
     * 仅当键不存在时写入，返回是否实际写入。
     *
     * @param key        缓存键
     * @param value      缓存值
     * @param ttlSeconds 过期秒数
     * @return 是否写入成功
     */
    public boolean add(String key, Object value, long ttlSeconds) {
        long expiryAt = ttlSeconds > 0 ? System.currentTimeMillis() + ttlSeconds * 1000L : 0L;
        // compute 对同一 key 是原子的：并发 add 只有一个能成功。
        // 原实现是 exists→put 两步（先检查后写入），两个线程可能同时通过检查而都返回 true，
        // 「单次执行/互斥门闩」语义因此失效；已过期条目在此视为不存在。
        boolean[] written = {false};
        store.compute(key, (k, existing) -> {
            if (existing != null && !existing.isExpired()) {
                return existing;
            }
            written[0] = true;
            return new Entry(value, expiryAt);
        });
        return written[0];
    }

    /**
     * 读取后立即删除。
     *
     * @param key 缓存键
     * @return 缓存值，不存在或已过期返回 {@code null}
     */
    public Object pull(String key) {
        Entry entry = store.remove(key);
        if (entry == null || entry.isExpired()) {
            return null;
        }
        return entry.value;
    }

    /**
     * 主动清理所有已过期条目。
     *
     * @return 实际清理的条目数
     */
    public int cleanup() {
        int removed = 0;
        Iterator<Map.Entry<String, Entry>> it = store.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().isExpired()) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    /**
     * 返回当前条目数（含可能已过期但未触发清理的）。
     *
     * @return 条目数
     */
    public int size() {
        return store.size();
    }

    /**
     * 自增 1。键不存在或非数字时按 0 起算。
     *
     * @param key 缓存键
     * @return 自增后的值
     */
    public long increment(String key) {
        return increment(key, 1);
    }

    /**
     * 自增指定步长。键不存在或非数字时按 0 起算。
     *
     * @param key    缓存键
     * @param amount 步长
     * @return 自增后的值
     */
    public long increment(String key, long amount) {
        return increment(key, amount, 0L);
    }

    /**
     * 自增指定步长（原子），并可指定「键不存在时」的 TTL。
     *
     * @param key                缓存键
     * @param amount             步长
     * @param ttlIfAbsentSeconds 键不存在/已过期时的过期秒数，{@code <= 0} 表示永不过期
     * @return 自增后的值
     */
    public long increment(String key, long amount, long ttlIfAbsentSeconds) {
        // compute 对同一 key 原子：并发自增不再「丢失更新」（原实现 get→put 两步），
        // 且保留原 expiryAt（不把有期限的键变成永不过期）
        long[] result = {0L};
        store.compute(key, (k, existing) -> {
            long current = 0L;
            long expiryAt = 0L;
            if (existing != null && !existing.isExpired()) {
                if (existing.value instanceof Number) {
                    current = ((Number) existing.value).longValue();
                }
                expiryAt = existing.expiryAt;
            } else if (ttlIfAbsentSeconds > 0) {
                expiryAt = System.currentTimeMillis() + ttlIfAbsentSeconds * 1000L;
            }
            long newValue = current + amount;
            result[0] = newValue;
            return new Entry(newValue, expiryAt);
        });
        return result[0];
    }

    /**
     * 自减 1。
     *
     * @param key 缓存键
     * @return 自减后的值
     */
    public long decrement(String key) {
        return increment(key, -1);
    }

    /**
     * 自减指定步长。
     *
     * @param key    缓存键
     * @param amount 步长
     * @return 自减后的值
     */
    public long decrement(String key, long amount) {
        return increment(key, -amount);
    }

    /**
     * 缓存条目，记录值与过期时间戳。
     */
    static final class Entry {

        final Object value;

        final long expiryAt;

        Entry(Object value, long expiryAt) {
            this.value = value;
            this.expiryAt = expiryAt;
        }

        boolean isExpired() {
            return expiryAt > 0 && System.currentTimeMillis() >= expiryAt;
        }
    }
}
