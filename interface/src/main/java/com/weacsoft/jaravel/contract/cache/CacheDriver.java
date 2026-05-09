package com.weacsoft.jaravel.contract.cache;

/**
 * 缓存驱动接口，定义底层缓存存储的读写契约。
 *
 * <p>参考 Laravel {@code Illuminate\Contracts\Cache\Store}，
 * 本接口定义缓存最基础的 CRUD 操作，是 {@link CacheStore} 的底层依赖。</p>
 *
 * <h3>TTL 约定</h3>
 * <ul>
 *   <li>TTL 单位为毫秒</li>
 *   <li>TTL &lt;= 0 表示永不过期</li>
 * </ul>
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li>实现类必须保证线程安全</li>
 *   <li>{@link #get(String, Class)} 在键不存在时应返回 {@code null}</li>
 *   <li>类型转换失败时由实现决定行为（抛异常或返回 null）</li>
 * </ul>
 *
 * @see CacheStore
 */
public interface CacheDriver {

    /**
     * 存储键值对并指定 TTL。
     *
     * @param key   缓存键
     * @param value 缓存值
     * @param ttl   过期时间（毫秒），&lt;= 0 表示永不过期
     * @return 存储成功返回 {@code true}
     */
    boolean put(String key, Object value, long ttl);

    /**
     * 存储键值对（永不过期）。
     *
     * @param key   缓存键
     * @param value 缓存值
     * @return 存储成功返回 {@code true}
     */
    default boolean put(String key, Object value) {
        return put(key, value, -1);
    }

    /**
     * 获取缓存值（无类型转换）。
     *
     * @param key 缓存键
     * @return 缓存值，不存在时返回 {@code null}
     */
    default Object get(String key) {
        return get(key, Object.class);
    }

    /**
     * 获取缓存值并转换为指定类型。
     *
     * @param key  缓存键
     * @param type 目标类型
     * @param <T>  目标类型泛型
     * @return 类型转换后的缓存值，不存在时返回 {@code null}
     */
    <T> T get(String key, Class<T> type);

    /**
     * 检查缓存键是否存在。
     *
     * @param key 缓存键
     * @return 存在返回 {@code true}
     */
    boolean exist(String key);

    /**
     * 移除缓存键。
     *
     * @param key 缓存键
     * @return 移除成功返回 {@code true}
     */
    boolean remove(String key);

    /**
     * 清空所有缓存。
     */
    void removeAll();
}
