package com.weacsoft.jaravel.vendor.cache;

import java.util.Collection;

/**
 * 缓存驱动契约，对齐 Laravel 底层 {@code Illuminate\Contracts\Cache\Store}。
 * <p>
 * 这是与具体存储介质（内存 / 文件 / Redis 等）交互的最底层 CRUD 契约，
 * 由 {@link CacheStore} 在其上构建高级语义。TTL 单位统一为<b>秒</b>（对齐 Laravel），
 * {@code ttlSeconds <= 0} 表示永不过期。
 *
 * <pre>
 * CacheDriver driver = new ArrayCacheDriver();
 * driver.put("user:1", user, 60);   // 60 秒后过期
 * Object v = driver.get("user:1");
 * </pre>
 */
public interface CacheDriver {

    /**
     * 写入缓存。
     *
     * @param key        缓存键
     * @param value      缓存值
     * @param ttlSeconds 过期秒数，{@code <= 0} 表示永不过期
     * @return 是否写入成功
     */
    boolean put(String key, Object value, long ttlSeconds);

    /**
     * 读取缓存，不存在或已过期返回 {@code null}。
     */
    Object get(String key);

    /**
     * 判断缓存键是否存在（未过期）。
     */
    boolean exists(String key);

    /**
     * 移除指定缓存键，返回是否确实移除了条目。
     */
    boolean remove(String key);

    /**
     * 清空当前驱动下的所有缓存。
     */
    void removeAll();

    /**
     * 返回当前驱动下所有未过期的缓存键。
     */
    Collection<String> allKeys();
}
